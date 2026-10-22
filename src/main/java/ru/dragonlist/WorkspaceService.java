package ru.dragonlist;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** All workspace operations run in transactions and scope lookups to the authenticated owner. */
@Service
@Transactional
public class WorkspaceService {
  private final JdbcTemplate db;

  public WorkspaceService(JdbcTemplate db) {
    this.db = db;
  }

  public long user(String login) {
    var ids = db.queryForList("SELECT id FROM users WHERE login=?", Long.class, login);
    if (ids.isEmpty())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Сессия завершилась");
    return ids.get(0);
  }

  public static void require(boolean valid, String message) {
    if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  public static String text(
      Map<String, Object> data, String key, String fallback, int max, boolean blank) {
    Object value = data.getOrDefault(key, fallback);
    require(value instanceof String, "Некорректное поле " + key);
    String result = (String) value;
    require(
        result.length() <= max && (blank || !result.isBlank()),
        "Проверьте поле " + key + " (до " + max + " символов)");
    return result;
  }

  public static long number(Object value) {
    try {
      long n = Long.parseLong(String.valueOf(value));
      require(n > 0, "Некорректный идентификатор");
      return n;
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный идентификатор");
    }
  }

  private static int position(Object value) {
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная позиция");
    }
  }

  private static boolean bool(Object v) {
    require(v instanceof Boolean, "Ожидалось логическое значение");
    return (Boolean) v;
  }

  public long insert(String sql, Object... values) {
    var key = new GeneratedKeyHolder();
    db.update(
        c -> {
          var p = c.prepareStatement(sql, new String[] {"id"});
          for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
          return p;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  public void initialize(long uid) {
    db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, uid);
    if (db.queryForList("SELECT user_id FROM profiles WHERE user_id=?", uid).isEmpty())
      db.update("INSERT INTO profiles(user_id) VALUES (?)", uid);
    if (Boolean.TRUE.equals(
        db.queryForObject(
            "SELECT tags_initialized FROM profiles WHERE user_id=?", Boolean.class, uid))) return;
    for (Tag t : Tag.values()) {
      long tid = insert("INSERT INTO user_tags(user_id,name) VALUES (?,?)", uid, t.getLabel());
      db.update(
          "INSERT INTO note_tags(note_id,tag_id) SELECT id,? FROM notes WHERE user_id=? AND tag=?",
          tid,
          uid,
          t.name());
    }
    db.update("UPDATE profiles SET tags_initialized=TRUE WHERE user_id=?", uid);
  }

  private Map<String, Object> row(long id, long uid) {
    return db.queryForList("SELECT * FROM notes WHERE id=? AND user_id=?", id, uid).stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private String timestamp(Object v) {
    return v == null
        ? null
        : v instanceof java.time.OffsetDateTime t
            ? t.toInstant().toString()
            : v instanceof Timestamp t ? t.toInstant().toString() : v.toString();
  }

  public Map<String, Object> note(long id, long uid) {
    Map<String, Object> r = row(id, uid), out = new LinkedHashMap<>();
    for (String k :
        List.of("id", "title", "content", "status", "note_type", "position", "is_pinned"))
      out.put(k, r.get(k));
    for (String k : List.of("created_at", "updated_at", "deleted_at"))
      out.put(k, timestamp(r.get(k)));
    out.put(
        "tags",
        db.queryForList(
            "SELECT t.id,t.name,t.color FROM user_tags t JOIN note_tags nt ON nt.tag_id=t.id WHERE"
                + " nt.note_id=? AND t.user_id=? ORDER BY t.name",
            id,
            uid));
    out.put(
        "todos",
        db.queryForList(
            "SELECT id,text,done,position FROM todos WHERE note_id=? ORDER BY position,id", id));
    out.put(
        "images",
        db.queryForList("SELECT id FROM media WHERE note_id=? AND user_id=?", id, uid).stream()
            .map(m -> Map.of("id", m.get("id"), "url", "/api/media/" + m.get("id"), "alt", ""))
            .toList());
    return out;
  }

  public List<Map<String, Object>> notes(long uid, String status, String search, String tag) {
    initialize(uid);
    require(List.of("active", "draft", "trashed").contains(status), "Неизвестный статус");
    String sql = "SELECT n.id FROM notes n WHERE n.user_id=? AND n.status=?";
    var args = new ArrayList<Object>(List.of(uid, status));
    if (tag != null && !tag.isBlank()) {
      sql +=
          " AND EXISTS (SELECT 1 FROM note_tags nt JOIN user_tags t ON t.id=nt.tag_id WHERE"
              + " nt.note_id=n.id AND t.user_id=? AND t.name=?)";
      args.add(uid);
      args.add(tag);
    }
    if (search != null && !search.isBlank()) {
      sql += " AND (LOWER(n.title) LIKE ? ESCAPE '!' OR LOWER(n.content) LIKE ? ESCAPE '!')";
      String term =
          "%"
              + search
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%";
      args.add(term);
      args.add(term);
    }
    return db
        .queryForList(
            sql + " ORDER BY n.is_pinned DESC,n.position,n.updated_at DESC,n.id DESC",
            Long.class,
            args.toArray())
        .stream()
        .map(id -> note(id, uid))
        .toList();
  }

  public Map<String, Object> create(long uid, Map<String, Object> data) {
    initialize(uid);
    String title = text(data, "title", "Новая заметка", 200, true),
        content = text(data, "content", "", 20000, true);
    String type = text(data, "note_type", "note", 10, false);
    require(List.of("note", "todo").contains(type), "Неизвестный тип заметки");
    String status = text(data, "status", "active", 10, false);
    require(List.of("active", "draft", "trashed").contains(status), "Неизвестный статус");
    long id =
        insert(
            "INSERT INTO"
                + " notes(user_id,title,content,tag,created_at,updated_at,note_type,status,deleted_at)"
                + " VALUES (?,?,?,'IMPORTANT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?,?)",
            uid,
            title,
            content,
            type,
            status,
            "trashed".equals(status) ? Timestamp.from(Instant.now()) : null);
    if (data.containsKey("tag_names")) setTags(id, uid, data.get("tag_names"));
    return note(id, uid);
  }

  public Map<String, Object> update(long id, long uid, Map<String, Object> data) {
    var r = row(id, uid);
    String title = text(data, "title", (String) r.get("title"), 200, true),
        content = text(data, "content", (String) r.get("content"), 20000, true);
    String status = text(data, "status", (String) r.get("status"), 10, false);
    require(List.of("active", "draft", "trashed").contains(status), "Неизвестный статус");
    boolean pinned =
        data.containsKey("is_pinned") ? bool(data.get("is_pinned")) : (Boolean) r.get("is_pinned");
    db.update(
        "UPDATE notes SET"
            + " title=?,content=?,status=?,is_pinned=?,updated_at=CURRENT_TIMESTAMP,deleted_at=?"
            + " WHERE id=? AND user_id=?",
        title,
        content,
        status,
        pinned,
        "trashed".equals(status) ? Timestamp.from(Instant.now()) : null,
        id,
        uid);
    if (data.containsKey("tag_names")) setTags(id, uid, data.get("tag_names"));
    return note(id, uid);
  }

  private void setTags(long id, long uid, Object raw) {
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
      var tids =
          db.queryForList(
              "SELECT id FROM user_tags WHERE user_id=? AND name=?", Long.class, uid, name);
      long tid =
          tids.isEmpty()
              ? insert("INSERT INTO user_tags(user_id,name) VALUES (?,?)", uid, name)
              : tids.get(0);
      db.update("INSERT INTO note_tags(note_id,tag_id) VALUES (?,?)", id, tid);
    }
  }

  public void hardDelete(long id, long uid) {
    var r = row(id, uid);
    require("trashed".equals(r.get("status")), "Сначала переместите заметку в корзину");
    db.update("DELETE FROM notes WHERE id=? AND user_id=?", id, uid);
  }

  public void bulk(long uid, Map<String, Object> data, boolean reorder) {
    Object raw = data.get(reorder ? "order" : "ids");
    require(
        raw instanceof List<?> && ((List<?>) raw).size() <= 1000, "Некорректный список заметок");
    var ids = ((List<?>) raw).stream().map(WorkspaceService::number).distinct().toList();
    for (long id : ids) row(id, uid);
    String action = reorder ? "reorder" : text(data, "action", "", 20, false);
    require(
        List.of("reorder", "draft", "trash", "active", "restore").contains(action),
        "Неизвестное действие");
    for (int i = 0; i < ids.size(); i++) {
      long id = ids.get(i);
      if (reorder) db.update("UPDATE notes SET position=? WHERE id=? AND user_id=?", i, id, uid);
      else
        update(
            id,
            uid,
            Map.of(
                "status",
                action.equals("trash") ? "trashed" : action.equals("draft") ? "draft" : "active"));
    }
  }

  public List<Map<String, Object>> tags(long uid) {
    initialize(uid);
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
    initialize(uid);
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
      id = insert("INSERT INTO user_tags(user_id,name,color) VALUES (?,?,?)", uid, name, color);
    else
      db.update(
          "UPDATE user_tags SET name=?,color=? WHERE id=? AND user_id=?", name, color, id, uid);
    return tag(id, uid);
  }

  public void deleteTag(long id, long uid) {
    tag(id, uid);
    db.update("DELETE FROM user_tags WHERE id=? AND user_id=?", id, uid);
  }

  private Map<String, Object> todo(long id, long uid) {
    return db
        .queryForList(
            "SELECT t.* FROM todos t JOIN notes n ON n.id=t.note_id WHERE t.id=? AND n.user_id=?",
            id,
            uid)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  public Map<String, Object> saveTodo(Long id, long uid, Map<String, Object> data) {
    Map<String, Object> old =
        id == null ? Map.of("text", "", "done", false, "position", 0) : todo(id, uid);
    String text = text(data, "text", (String) old.get("text"), 500, false).strip();
    boolean done = data.containsKey("done") ? bool(data.get("done")) : (Boolean) old.get("done");
    int pos = position(data.getOrDefault("position", old.get("position")));
    long noteId = id == null ? number(data.get("note")) : ((Number) old.get("note_id")).longValue();
    var n = row(noteId, uid);
    require("todo".equals(n.get("note_type")), "Это не список задач");
    if (id == null)
      id =
          insert(
              "INSERT INTO todos(note_id,text,done,position) VALUES (?,?,?,?)",
              noteId,
              text,
              done,
              pos);
    else db.update("UPDATE todos SET text=?,done=?,position=? WHERE id=?", text, done, pos, id);
    db.update("UPDATE notes SET updated_at=CURRENT_TIMESTAMP WHERE id=?", noteId);
    return todo(id, uid);
  }

  public void deleteTodo(long id, long uid) {
    todo(id, uid);
    db.update("DELETE FROM todos WHERE id=?", id);
  }

  public Map<String, Object> profile(long uid) {
    initialize(uid);
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
    initialize(uid);
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

  public long media(long uid, Long noteId, String type, byte[] data) {
    if (noteId != null) row(noteId, uid);
    return insert(
        "INSERT INTO media(user_id,note_id,content_type,data) VALUES (?,?,?,?)",
        uid,
        noteId,
        type,
        data);
  }

  public Map<String, Object> media(long uid, long id) {
    return db
        .queryForList("SELECT content_type,data FROM media WHERE id=? AND user_id=?", id, uid)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
