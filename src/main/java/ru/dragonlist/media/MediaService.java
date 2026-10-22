package ru.dragonlist.media;

import static ru.dragonlist.common.InputValidation.*;

import java.io.*;
import java.sql.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MediaService {
  private static final java.util.concurrent.Semaphore DECODERS =
      new java.util.concurrent.Semaphore(2);
  private final JdbcTemplate db;
  private final ru.dragonlist.common.Database database;
  private final ru.dragonlist.notes.NoteService notes;

  public MediaService(
      JdbcTemplate db,
      ru.dragonlist.common.Database database,
      ru.dragonlist.notes.NoteService notes) {
    this.db = db;
    this.database = database;
    this.notes = notes;
  }

  public long media(long uid, Long noteId, String type, byte[] data) {
    if (noteId != null) notes.row(noteId, uid);
    db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, uid);
    require(data.length <= 5 * 1024 * 1024, "Изображение после обработки превышает 5 МБ");
    require(
        db.queryForObject("SELECT COUNT(*) FROM media WHERE user_id=?", Long.class, uid) < 100,
        "Достигнут лимит изображений");
    require(
        db.queryForObject(
                    "SELECT COALESCE(SUM(OCTET_LENGTH(data)),0) FROM media WHERE user_id=?",
                    Long.class,
                    uid)
                + data.length
            <= 50 * 1024 * 1024,
        "Достигнут лимит 50 МБ изображений");
    return database.insert(
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

  public long upload(long user, Long note, MultipartFile file) throws IOException {
    if (note != null) notes.row(note, user);
    if (!DECODERS.tryAcquire())
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Повторите загрузку позже");
    try {
      require(
          file.getSize() > 0 && file.getSize() <= 5 * 1024 * 1024,
          "Изображение должно быть не больше 5 МБ");
      try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
        var readers = ImageIO.getImageReaders(input);
        require(readers.hasNext(), "Используйте PNG, JPEG или GIF");
        var reader = readers.next();
        try {
          reader.setInput(input);
          String format = reader.getFormatName().toLowerCase(Locale.ROOT);
          require(List.of("png", "jpeg", "gif").contains(format), "Используйте PNG, JPEG или GIF");
          require(
              reader.getWidth(0) <= 4096 && reader.getHeight(0) <= 4096,
              "Размер изображения: до 4096 × 4096");
          var image = reader.read(0);
          var output = new ByteArrayOutputStream();
          String safeFormat = format.equals("gif") ? "png" : format;
          ImageIO.write(image, safeFormat, output);
          return media(user, note, "image/" + safeFormat, output.toByteArray());
        } finally {
          reader.dispose();
        }
      }
    } finally {
      DECODERS.release();
    }
  }
}
