package ru.dragonlist.auth;

import static ru.dragonlist.common.InputValidation.*;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dragonlist.tags.Tag;

@Service
@Transactional
public class AccountInitializer {
  private final JdbcTemplate db;
  private final ru.dragonlist.common.Database database;

  public AccountInitializer(JdbcTemplate db, ru.dragonlist.common.Database database) {
    this.db = db;
    this.database = database;
  }

  public void initialize(long uid) {
    db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, uid);
    if (db.queryForList("SELECT user_id FROM profiles WHERE user_id=?", uid).isEmpty())
      db.update("INSERT INTO profiles(user_id) VALUES (?)", uid);
    if (Boolean.TRUE.equals(
        db.queryForObject(
            "SELECT tags_initialized FROM profiles WHERE user_id=?", Boolean.class, uid))) return;
    for (Tag t : Tag.values()) {
      long tid =
          database.insert("INSERT INTO user_tags(user_id,name) VALUES (?,?)", uid, t.getLabel());
      db.update(
          "INSERT INTO note_tags(note_id,tag_id) SELECT id,? FROM notes WHERE user_id=? AND tag=?",
          tid,
          uid,
          t.name());
    }
    db.update("UPDATE profiles SET tags_initialized=TRUE WHERE user_id=?", uid);
  }
}
