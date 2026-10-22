package ru.dragonlist.tags;

import static ru.dragonlist.common.InputValidation.*;

import java.io.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TagController {
  private final TagService service;
  private final ru.dragonlist.auth.CurrentAccount accounts;

  public TagController(TagService service, ru.dragonlist.auth.CurrentAccount accounts) {
    this.service = service;
    this.accounts = accounts;
  }

  private long uid(Principal p) {
    return accounts.id(p);
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
}
