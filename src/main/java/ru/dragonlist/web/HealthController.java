package ru.dragonlist.web;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
  private final JdbcTemplate db;

  public HealthController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping("/healthz")
  public Map<String, String> health() {
    db.queryForObject("SELECT 1", Integer.class);
    return Map.of("status", "UP");
  }
}
