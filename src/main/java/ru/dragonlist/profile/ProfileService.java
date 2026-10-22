package ru.dragonlist.profile;

import static ru.dragonlist.common.InputValidation.*;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileService {
  private final JdbcTemplate db;
  private final ru.dragonlist.auth.AccountInitializer accounts;

  public ProfileService(JdbcTemplate db, ru.dragonlist.auth.AccountInitializer accounts) {
    this.db = db;
    this.accounts = accounts;
  }

  public Map<String, Object> profile(long uid) {
    accounts.initialize(uid);
    var r =
        db.queryForMap(
            "SELECT u.login,p.email,p.bio,p.avatar_id FROM users u JOIN profiles p ON"
                + " p.user_id=u.id WHERE u.id=?",
            uid);
    var out = new LinkedHashMap<String, Object>();
    out.put("username", r.get("login"));
    out.put("email", r.get("email"));
    out.put("bio", r.get("bio"));
    out.put("avatar", r.get("avatar_id") == null ? null : "/api/media/" + r.get("avatar_id"));
    return out;
  }

  public Map<String, Object> saveProfile(
      long uid, String username, String email, String bio, Long avatar) {
    accounts.initialize(uid);
    require(username.matches("[a-zA-Z0-9_]{3,32}"), "Логин: 3–32 латинские буквы, цифры или _");
    username = username.toLowerCase(Locale.ROOT);
    require(
        email.length() <= 254 && (email.isEmpty() || email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")),
        "Проверьте email");
    require(bio.length() <= 300, "О себе: до 300 символов");
    require(
        db.queryForObject(
                "SELECT COUNT(*) FROM users WHERE login=? AND id<>?", Long.class, username, uid)
            == 0,
        "Логин уже занят");
    db.update("UPDATE users SET login=? WHERE id=?", username, uid);
    db.update("UPDATE profiles SET email=?,bio=? WHERE user_id=?", email, bio, uid);
    if (avatar != null) db.update("UPDATE profiles SET avatar_id=? WHERE user_id=?", avatar, uid);
    return profile(uid);
  }
}
