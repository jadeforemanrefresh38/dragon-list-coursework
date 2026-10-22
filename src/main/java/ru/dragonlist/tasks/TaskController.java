package ru.dragonlist.tasks;

import static ru.dragonlist.common.InputValidation.*;

import java.io.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TaskController {
  private final TaskService service;
  private final ru.dragonlist.auth.CurrentAccount accounts;

  public TaskController(TaskService service, ru.dragonlist.auth.CurrentAccount accounts) {
    this.service = service;
    this.accounts = accounts;
  }

  private long uid(Principal p) {
    return accounts.id(p);
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
}
