package ru.dragonlist.notes;

import static ru.dragonlist.common.InputValidation.*;

import java.io.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class NoteController {
  private final NoteService service;
  private final ru.dragonlist.auth.CurrentAccount accounts;

  public NoteController(NoteService service, ru.dragonlist.auth.CurrentAccount accounts) {
    this.service = service;
    this.accounts = accounts;
  }

  private long uid(Principal p) {
    return accounts.id(p);
  }

  @GetMapping("/notes/")
  public Object notes(
      Principal p,
      @RequestParam(defaultValue = "active") String status,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String tag,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    var rows = service.notes(uid(p), status, search, tag, limit, offset);
    String next = null;
    if (rows.size() == limit) {
      var uri =
          org.springframework.web.util.UriComponentsBuilder.fromPath("/api/notes/")
              .queryParam("status", status)
              .queryParam("offset", offset + limit)
              .queryParam("limit", limit);
      if (search != null) uri.queryParam("search", search);
      if (tag != null) uri.queryParam("tag", tag);
      next = uri.build().encode().toUriString();
    }
    var page = new LinkedHashMap<String, Object>();
    page.put("results", rows);
    page.put("next", next);
    return page;
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
}
