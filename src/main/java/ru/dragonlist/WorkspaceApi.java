package ru.dragonlist;

import static ru.dragonlist.WorkspaceService.*;

import java.io.*;
import java.security.Principal;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class WorkspaceApi {
  private final WorkspaceService service;
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;

  public WorkspaceApi(WorkspaceService service, JdbcTemplate db, PasswordEncoder encoder) {
    this.service = service;
    this.db = db;
    this.encoder = encoder;
  }

  private long uid(Principal p) {
    return service.user(p.getName());
  }

  @GetMapping("/notes/")
  public Object notes(
      Principal p,
      @RequestParam(defaultValue = "active") String status,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String tag) {
    return service.notes(uid(p), status, search, tag);
  }

  @PostMapping("/notes/")
  @ResponseStatus(HttpStatus.CREATED)
  public Object create(Principal p, @RequestBody Map<String, Object> data) {
    return service.create(uid(p), data);
  }

  @GetMapping("/notes/{id}/")
  public Object note(Principal p, @PathVariable long id) {
    return service.note(id, uid(p));
  }

  @PatchMapping("/notes/{id}/")
  public Object update(Principal p, @PathVariable long id, @RequestBody Map<String, Object> data) {
    return service.update(id, uid(p), data);
  }

  @PostMapping("/notes/{id}/restore/")
  public Object restore(Principal p, @PathVariable long id) {
    return service.update(id, uid(p), Map.of("status", "active"));
  }

  @PostMapping("/notes/{id}/hard_delete/")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(Principal p, @PathVariable long id) {
    service.hardDelete(id, uid(p));
  }

  @DeleteMapping("/notes/{id}/")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void trash(Principal p, @PathVariable long id) {
    service.update(id, uid(p), Map.of("status", "trashed"));
  }

  @PostMapping("/notes/reorder/")
  public Object reorder(Principal p, @RequestBody Map<String, Object> data) {
    service.bulk(uid(p), data, true);
    return Map.of("ok", true);
  }

  @PostMapping("/notes/bulk/")
  public Object bulk(Principal p, @RequestBody Map<String, Object> data) {
    service.bulk(uid(p), data, false);
    return Map.of("ok", true);
  }

  @GetMapping({"/tags/", "/tags/dashboard/"})
  public Object tags(Principal p) {
    return service.tags(uid(p));
  }

  @PostMapping("/tags/")
  @ResponseStatus(HttpStatus.CREATED)
  public Object tag(Principal p, @RequestBody Map<String, Object> data) {
    return service.saveTag(null, uid(p), data);
  }

  @PatchMapping("/tags/{id}/")
  public Object tag(Principal p, @PathVariable long id, @RequestBody Map<String, Object> data) {
    return service.saveTag(id, uid(p), data);
  }

  @DeleteMapping("/tags/{id}/")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void tag(Principal p, @PathVariable long id) {
    service.deleteTag(id, uid(p));
  }

  @PostMapping("/todos/")
  @ResponseStatus(HttpStatus.CREATED)
  public Object todo(Principal p, @RequestBody Map<String, Object> data) {
    return service.saveTodo(null, uid(p), data);
  }

  @PatchMapping("/todos/{id}/")
  public Object todo(Principal p, @PathVariable long id, @RequestBody Map<String, Object> data) {
    return service.saveTodo(id, uid(p), data);
  }

  @DeleteMapping("/todos/{id}/")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void todo(Principal p, @PathVariable long id) {
    service.deleteTodo(id, uid(p));
  }

  @GetMapping("/profile/")
  public Object profile(Principal p) {
    return service.profile(uid(p));
  }

  @PostMapping("/profile/save/")
  @Transactional
  public Object profile(
      Principal p,
      @RequestParam String username,
      @RequestParam(defaultValue = "") String email,
      @RequestParam(defaultValue = "") String bio,
      @RequestParam(required = false) MultipartFile avatar)
      throws IOException {
    long user = uid(p);
    Long image = avatar == null || avatar.isEmpty() ? null : upload(user, null, avatar);
    Object result = service.saveProfile(user, username, email, bio, image);
    var auth = SecurityContextHolder.getContext().getAuthentication();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                username.toLowerCase(Locale.ROOT), null, auth.getAuthorities()));
    return result;
  }

  @PostMapping("/profile/change_password/")
  public Object password(Principal p, @RequestBody Map<String, Object> data) {
    long user = uid(p);
    String old = text(data, "old_password", "", 64, false),
        password = text(data, "new_password1", "", 64, false),
        confirm = text(data, "new_password2", "", 64, false);
    require(
        encoder.matches(
            old, db.queryForObject("SELECT password FROM users WHERE id=?", String.class, user)),
        "Неверный текущий пароль");
    require(
        password.matches("[\\x20-\\x7E]{8,64}"),
        "Пароль: 8–64 символа, латинские буквы, цифры и знаки");
    require(password.equals(confirm), "Пароли не совпадают");
    db.update("UPDATE users SET password=? WHERE id=?", encoder.encode(password), user);
    return Map.of("ok", true);
  }

  private long upload(long user, Long note, MultipartFile file) throws IOException {
    require(
        file.getSize() > 0 && file.getSize() <= 5 * 1024 * 1024,
        "Изображение должно быть не больше 5 МБ");
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
      var readers = ImageIO.getImageReaders(input);
      require(readers.hasNext(), "Используйте PNG, JPEG или GIF");
      var reader = readers.next();
      try {
        reader.setInput(input);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        require(List.of("png", "jpeg", "gif").contains(format), "Используйте PNG, JPEG или GIF");
        require(
            reader.getWidth(0) <= 4096 && reader.getHeight(0) <= 4096,
            "Размер изображения: до 4096 × 4096");
        var image = reader.read(0);
        var output = new ByteArrayOutputStream();
        String safeFormat = format.equals("gif") ? "png" : format;
        ImageIO.write(image, safeFormat, output);
        return service.media(user, note, "image/" + safeFormat, output.toByteArray());
      } finally {
        reader.dispose();
      }
    }
  }

  @PostMapping("/images/")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Object image(Principal p, @RequestParam long note, @RequestParam MultipartFile image)
      throws IOException {
    long user = uid(p);
    service.note(note, user);
    long id = upload(user, note, image);
    return Map.of("id", id, "url", "/api/media/" + id, "alt", "");
  }

  @GetMapping("/media/{id}")
  public ResponseEntity<byte[]> media(Principal p, @PathVariable long id) {
    var r = service.media(uid(p), id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) r.get("content_type")))
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body((byte[]) r.get("data"));
  }
}
