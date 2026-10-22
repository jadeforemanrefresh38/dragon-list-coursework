package ru.dragonlist.auth;

import static ru.dragonlist.common.InputValidation.*;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordService {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;

  public PasswordService(JdbcTemplate db, PasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  @Transactional
  public long change(long user, Map<String, Object> data) {
    String old = text(data, "old_password", "", 64, false),
        next = text(data, "new_password1", "", 64, false),
        confirmation = text(data, "new_password2", "", 64, false);
    require(
        encoder.matches(
            old, db.queryForObject("SELECT password FROM users WHERE id=?", String.class, user)),
        "Неверный текущий пароль");
    require(next.matches("[\\x20-\\x7E]{8,64}"), "Пароль: 8–64 печатных ASCII-символа");
    require(next.equals(confirmation), "Пароли не совпадают");
    db.update(
        "UPDATE users SET password=?,session_version=session_version+1 WHERE id=?",
        encoder.encode(next),
        user);
    return db.queryForObject("SELECT session_version FROM users WHERE id=?", Long.class, user);
  }
}
