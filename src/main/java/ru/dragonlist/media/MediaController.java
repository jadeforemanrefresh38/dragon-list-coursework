package ru.dragonlist.media;

import static ru.dragonlist.common.InputValidation.*;

import java.io.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class MediaController {
  private final MediaService service;
  private final ru.dragonlist.auth.CurrentAccount accounts;

  public MediaController(MediaService service, ru.dragonlist.auth.CurrentAccount accounts) {
    this.service = service;
    this.accounts = accounts;
  }

  private long uid(Principal p) {
    return accounts.id(p);
  }

  @PostMapping("/images/")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Object image(Principal p, @RequestParam long note, @RequestParam MultipartFile image)
      throws IOException {
    long user = uid(p);

    long id = service.upload(user, note, image);
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
