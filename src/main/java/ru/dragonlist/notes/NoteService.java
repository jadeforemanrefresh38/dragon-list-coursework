package ru.dragonlist.notes;

import static ru.dragonlist.common.InputValidation.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class NoteService {
  private final JdbcTemplate db;
  private final ru.dragonlist.common.Database database;
  private final ru.dragonlist.auth.AccountInitializer accounts;
  private final ru.dragonlist.tags.TagService tags;

  public NoteService(
      JdbcTemplate db,
      ru.dragonlist.common.Database database,
      ru.dragonlist.auth.AccountInitializer accounts,
      ru.dragonlist.tags.TagService tags) {
    this.db = db;
    this.database = database;
    this.accounts = accounts;
    this.tags = tags;
  }

  public Map<String, Object> row(long id, long uid) {
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

  public List<Map<String, Object>> notes(
      long uid, String status, String search, String tag, int limit, int offset) {
    accounts.initialize(uid);
    require(List.of("active", "draft", "trashed").contains(status), "Неизвестный статус");
    require(limit >= 1 && limit <= 100 && offset >= 0 && offset <= 10000, "Некорректная страница");
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
    args.add(limit);
    args.add(offset);
    return db
        .queryForList(
            sql
                + " ORDER BY n.is_pinned DESC,n.position,n.updated_at DESC,n.id DESC LIMIT ? OFFSET"
                + " ?",
            Long.class,
            args.toArray())
        .stream()
        .map(id -> note(id, uid))
        .toList();
  }

  public Map<String, Object> create(long uid, Map<String, Object> data) {
    accounts.initialize(uid);
    require(
        db.queryForObject("SELECT COUNT(*) FROM notes WHERE user_id=?", Long.class, uid) < 1000,
        "Достигнут лимит 1000 заметок");
    String title = text(data, "title", "Новая заметка", 200, true),
        content = text(data, "content", "", 20000, true);
    String type = text(data, "note_type", "note", 10, false);
    require(List.of("note", "todo").contains(type), "Неизвестный тип заметки");
    String status = text(data, "status", "active", 10, false);
    require(List.of("active", "draft", "trashed").contains(status), "Неизвестный статус");
    long id =
        database.insert(
            "INSERT INTO"
                + " notes(user_id,title,content,tag,created_at,updated_at,note_type,status,deleted_at)"
                + " VALUES (?,?,?,'IMPORTANT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?,?)",
            uid,
            title,
            content,
            type,
            status,
            "trashed".equals(status) ? Timestamp.from(Instant.now()) : null);
    if (data.containsKey("tag_names")) tags.setTags(id, uid, data.get("tag_names"));
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
    if (data.containsKey("tag_names")) tags.setTags(id, uid, data.get("tag_names"));
    return note(id, uid);
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
    var ids =
        ((List<?>) raw)
            .stream().map(ru.dragonlist.common.InputValidation::number).distinct().toList();
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
}
