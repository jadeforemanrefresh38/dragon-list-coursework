package ru.dragonlist.tasks;

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
public class TaskService {
  private final JdbcTemplate db;
  private final ru.dragonlist.common.Database database;
  private final ru.dragonlist.notes.NoteService notes;

  public TaskService(
      JdbcTemplate db,
      ru.dragonlist.common.Database database,
      ru.dragonlist.notes.NoteService notes) {
    this.db = db;
    this.database = database;
    this.notes = notes;
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
    var n = notes.row(noteId, uid);
    db.queryForObject(
        "SELECT id FROM notes WHERE id=? AND user_id=? FOR UPDATE", Long.class, noteId, uid);
    if (id == null)
      require(
          db.queryForObject("SELECT COUNT(*) FROM todos WHERE note_id=?", Long.class, noteId) < 500,
          "Достигнут лимит задач");
    require("todo".equals(n.get("note_type")), "Это не список задач");
    if (id == null)
      id =
          database.insert(
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
}
