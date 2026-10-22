package ru.dragonlist.config;

import org.flywaydb.core.Flyway;

/** Separate one-shot process. Runtime application credentials cannot migrate the schema. */
public final class SchemaMigrator {
  private SchemaMigrator() {}

  public static void main(String[] args) throws Exception {
    Flyway.configure()
        .dataSource(System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
        .load()
        .migrate();
    try (var connection =
            java.sql.DriverManager.getConnection(
                System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
        var statement = connection.createStatement()) {
      statement.execute("REVOKE ALL ON TABLE public.flyway_schema_history FROM dragon_app");
    }
    System.out.println("Schema migration complete");
  }
}
