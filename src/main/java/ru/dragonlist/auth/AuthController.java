package ru.dragonlist.auth;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @GetMapping("/login")
  public String login() {
    return "login";
  }

  @GetMapping("/register")
  public String registration(Model model) {
    model.addAttribute("form", new RegistrationForm());
    return "register";
  }

  @PostMapping("/register")
  public String registration(
      @Valid @ModelAttribute("form") RegistrationForm form, BindingResult errors) {
    if (errors.hasErrors()) return "register";
    try {
      service.register(form);
    } catch (DataIntegrityViolationException ex) {
      errors.rejectValue("login", "duplicate", "Этот логин уже занят");
      return "register";
    } catch (ResponseStatusException ex) {
      errors.rejectValue("login", "invalid", ex.getReason());
      return "register";
    }
    return "redirect:/login?registered";
  }
}
