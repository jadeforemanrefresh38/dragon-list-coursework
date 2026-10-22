package ru.dragonlist;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebController {
  private final UserRepository users;
  private final NoteRepository notes;
  private final NoteService service;
  private final PasswordEncoder encoder;

  @Value("${app.donation-url:https://pay.cloudtips.ru/p/5a3bdc84}")
  private String donationUrl;

  public WebController(
      UserRepository users, NoteRepository notes, NoteService service, PasswordEncoder encoder) {
    this.users = users;
    this.notes = notes;
    this.service = service;
    this.encoder = encoder;
  }

  @ModelAttribute
  public void common(Model m, Principal p) {
    m.addAttribute("tags", Tag.values());
    m.addAttribute("login", p == null ? null : p.getName());
    m.addAttribute("donationUrl", donationUrl.startsWith("https://") ? donationUrl : "");
  }

  @GetMapping("/")
  public String home() {
    return "redirect:/notes";
  }

  @GetMapping("/login")
  public String login() {
    return "login";
  }

  @GetMapping("/register")
  public String register(Model m) {
    m.addAttribute("form", new RegistrationForm());
    return "register";
  }

  @PostMapping("/register")
  public String register(
      @Valid @ModelAttribute("form") RegistrationForm form, BindingResult errors) {
    if (errors.hasErrors()) return "register";
    String login = form.getLogin().toLowerCase(Locale.ROOT);
    if (users.existsByLogin(login)) {
      errors.rejectValue("login", "duplicate", "Этот логин уже занят");
      return "register";
    }
    try {
      users.saveAndFlush(new User(login, encoder.encode(form.getPassword())));
    } catch (DataIntegrityViolationException e) {
      errors.rejectValue("login", "duplicate", "Этот логин уже занят");
      return "register";
    }
    return "redirect:/login?registered";
  }

  private void workspace(Model m, Principal p, Tag tag, String query) {
    String search = query == null ? "" : query.strip();
    var rows =
        tag == null
            ? notes.findByUserLoginOrderByCreatedAtDescIdDesc(p.getName())
            : notes.findByUserLoginAndTagOrderByCreatedAtDescIdDesc(p.getName(), tag);
    if (!search.isEmpty()) {
      String term = search.toLowerCase(Locale.ROOT);
      rows =
          rows.stream()
              .filter(
                  n ->
                      n.getTitle().toLowerCase(Locale.ROOT).contains(term)
                          || n.getContent().toLowerCase(Locale.ROOT).contains(term))
              .toList();
    }
    m.addAttribute("selectedTag", tag);
    m.addAttribute("notes", rows);
    m.addAttribute("search", search);
  }

  @GetMapping("/notes")
  public String app() {
    return "app";
  }

  @GetMapping("/notes/table")
  public String list(
      @RequestParam(required = false) Tag tag,
      @RequestParam(required = false) String q,
      Principal p,
      Model m) {
    workspace(m, p, tag, q);
    return "notes";
  }

  @GetMapping("/notes/new")
  public String add(Model m, Principal p) {
    workspace(m, p, null, null);
    m.addAttribute("form", new NoteForm());
    m.addAttribute("editing", false);
    return "form";
  }

  @PostMapping("/notes")
  public String create(
      @Valid @ModelAttribute("form") NoteForm form, BindingResult errors, Principal p, Model m) {
    m.addAttribute("editing", false);
    if (errors.hasErrors()) {
      workspace(m, p, null, null);
      return "form";
    }
    return "redirect:/notes/" + service.create(form, p.getName());
  }

  @GetMapping("/notes/{id}")
  public String detail(@PathVariable Long id, Principal p, Model m) {
    workspace(m, p, null, null);
    m.addAttribute("note", service.owned(id, p.getName()));
    return "note";
  }

  @GetMapping("/notes/{id}/edit")
  public String edit(@PathVariable Long id, Principal p, Model m) {
    workspace(m, p, null, null);
    m.addAttribute("note", service.owned(id, p.getName()));
    m.addAttribute("form", new NoteForm(service.owned(id, p.getName())));
    m.addAttribute("editing", true);
    m.addAttribute("id", id);
    return "form";
  }

  @PostMapping("/notes/{id}/edit")
  public String update(
      @PathVariable Long id,
      @Valid @ModelAttribute("form") NoteForm form,
      BindingResult errors,
      Principal p,
      Model m) {
    service.owned(id, p.getName());
    m.addAttribute("editing", true);
    m.addAttribute("id", id);
    if (errors.hasErrors()) {
      workspace(m, p, null, null);
      m.addAttribute("note", service.owned(id, p.getName()));
      return "form";
    }
    service.update(id, form, p.getName());
    return "redirect:/notes/" + id;
  }

  @PostMapping("/notes/{id}/autosave")
  @ResponseBody
  public org.springframework.http.ResponseEntity<?> autosave(
      @PathVariable Long id,
      @Valid @ModelAttribute NoteForm form,
      BindingResult errors,
      Principal p) {
    service.owned(id, p.getName());
    if (errors.hasErrors())
      return org.springframework.http.ResponseEntity.badRequest()
          .body(java.util.Map.of("error", "Проверьте заголовок, текст и метку"));
    service.update(id, form, p.getName());
    return org.springframework.http.ResponseEntity.ok(java.util.Map.of("saved", true));
  }

  @PostMapping("/notes/{id}/delete")
  public String delete(@PathVariable Long id, Principal p) {
    service.delete(id, p.getName());
    return "redirect:/notes";
  }
}
