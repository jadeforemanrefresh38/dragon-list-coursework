package ru.dragonlist.common;

import static ru.dragonlist.common.InputValidation.*;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class Database {
  private final JdbcTemplate db;

  public Database(JdbcTemplate db) {
    this.db = db;
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
}
