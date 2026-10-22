package ru.dragonlist.profile;

import static ru.dragonlist.common.InputValidation.*;

import java.io.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ProfileController {
  private final ProfileService service;
  private final ru.dragonlist.auth.CurrentAccount accounts;
  private final ru.dragonlist.media.MediaService media;
  private final ru.dragonlist.auth.PasswordService passwords;

  public ProfileController(
      ProfileService service,
      ru.dragonlist.auth.CurrentAccount accounts,
      ru.dragonlist.media.MediaService media,
      ru.dragonlist.auth.PasswordService passwords) {
    this.service = service;
    this.accounts = accounts;
    this.media = media;
    this.passwords = passwords;
  }

  private long uid(Principal p) {
    return accounts.id(p);
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
    Long image = avatar == null || avatar.isEmpty() ? null : media.upload(user, null, avatar);
    Object result = service.saveProfile(user, username, email, bio, image);
    var auth = SecurityContextHolder.getContext().getAuthentication();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                username.toLowerCase(Locale.ROOT), null, auth.getAuthorities()));
    return result;
  }

  @PostMapping("/profile/change_password/")
  public Object password(
      Principal principal,
      @RequestBody Map<String, Object> data,
      jakarta.servlet.http.HttpServletRequest request) {
    long version = passwords.change(uid(principal), data);
    var session = request.getSession(false);
    if (session != null) session.setAttribute("accountVersion", version);
    return Map.of("ok", true);
  }
}
