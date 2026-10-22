package ru.dragonlist.auth;

import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentAccount {
  private final JdbcTemplate db;

  public CurrentAccount(JdbcTemplate db) {
    this.db = db;
  }

  public long id(Principal principal) {
    return db
        .queryForList("SELECT id FROM users WHERE login=?", Long.class, principal.getName())
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Войдите снова"));
  }
}
