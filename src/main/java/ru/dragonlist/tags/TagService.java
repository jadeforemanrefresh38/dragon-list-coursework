package ru.dragonlist.tags;

import static ru.dragonlist.common.InputValidation.*;

import java.sql.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TagService {
  private final JdbcTemplate db;
  private final ru.dragonlist.common.Database database;
  private final ru.dragonlist.auth.AccountInitializer accounts;

  public TagService(
      JdbcTemplate db,
      ru.dragonlist.common.Database database,
      ru.dragonlist.auth.AccountInitializer accounts) {
    this.db = db;
    this.database = database;
    this.accounts = accounts;
  }

  public void setTags(long id, long uid, Object raw) {
    require(raw instanceof List<?> && ((List<?>) raw).size() <= 50, "Не более 50 тегов");
    var names = new LinkedHashSet<String>();
    for (Object value : (List<?>) raw) {
      require(value instanceof String, "Некорректный тег");
      String name = ((String) value).strip();
      require(!name.isEmpty() && name.length() <= 50, "Тег: от 1 до 50 символов");
      names.add(name);
    }
    db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, uid);
    db.update("DELETE FROM note_tags WHERE note_id=?", id);
    for (String name : names) {
      require(
          db.queryForObject("SELECT COUNT(*) FROM user_tags WHERE user_id=?", Long.class, uid) < 200
              || !db.queryForList("SELECT id FROM user_tags WHERE user_id=? AND name=?", uid, name)
                  .isEmpty(),
          "Достигнут лимит тегов");
      var tids =
          db.queryForList(
              "SELECT id FROM user_tags WHERE user_id=? AND name=?", Long.class, uid, name);
      long tid =
          tids.isEmpty()
              ? database.insert("INSERT INTO user_tags(user_id,name) VALUES (?,?)", uid, name)
              : tids.get(0);
      db.update("INSERT INTO note_tags(note_id,tag_id) VALUES (?,?)", id, tid);
    }
  }

  public List<Map<String, Object>> tags(long uid) {
    accounts.initialize(uid);
    return db.queryForList(
        "SELECT t.id,t.name,t.color,COUNT(nt.note_id) AS note_count FROM user_tags t LEFT JOIN"
            + " note_tags nt ON nt.tag_id=t.id WHERE t.user_id=? GROUP BY t.id,t.name,t.color ORDER"
            + " BY note_count DESC,t.name",
        uid);
  }

  private Map<String, Object> tag(long id, long uid) {
    return db
        .queryForList("SELECT id,name,color FROM user_tags WHERE id=? AND user_id=?", id, uid)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  public Map<String, Object> saveTag(Long id, long uid, Map<String, Object> data) {
    accounts.initialize(uid);
    Map<String, Object> old = id == null ? Map.of("name", "", "color", "#9333ea") : tag(id, uid);
    String name = text(data, "name", (String) old.get("name"), 50, false).strip(),
        color = text(data, "color", (String) old.get("color"), 7, false);
    require(color.matches("#[0-9a-fA-F]{6}"), "Некорректный цвет");
    db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, uid);
    require(
        db.queryForObject(
                "SELECT COUNT(*) FROM user_tags WHERE user_id=? AND name=? AND id<>?",
                Long.class,
                uid,
                name,
                id == null ? 0 : id)
            == 0,
        "Такой тег уже существует");
    if (id == null)
      require(
          db.queryForObject("SELECT COUNT(*) FROM user_tags WHERE user_id=?", Long.class, uid)
              < 200,
          "Достигнут лимит тегов");
    if (id == null)
      id =
          database.insert(
              "INSERT INTO user_tags(user_id,name,color) VALUES (?,?,?)", uid, name, color);
    else
      db.update(
          "UPDATE user_tags SET name=?,color=? WHERE id=? AND user_id=?", name, color, id, uid);
    return tag(id, uid);
  }

  public void deleteTag(long id, long uid) {
    tag(id, uid);
    db.update("DELETE FROM user_tags WHERE id=? AND user_id=?", id, uid);
  }
}
