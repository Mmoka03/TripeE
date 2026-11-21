package com.tripee.backend;

import java.nio.file.Path;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Shared isolated DB for the postgresContainerTest task; external postgresTest remains opt-in. */
public abstract class PostgresIntegrationTestBase {
  private static final String APP_PASSWORD = UUID.randomUUID().toString();
  private static PostgreSQLContainer database;

  @DynamicPropertySource
  static void isolatedDatabase(DynamicPropertyRegistry properties) {
    if (!Boolean.getBoolean("tripee.testcontainers")) return;
    PostgreSQLContainer container = database();
    properties.add("spring.datasource.url", container::getJdbcUrl);
    properties.add("spring.datasource.username", () -> "tripee_backend");
    properties.add("spring.datasource.password", () -> APP_PASSWORD);
  }

  private static synchronized PostgreSQLContainer database() {
    if (database != null) return database;
    var container = new PostgreSQLContainer("postgres:18.6-trixie")
        .withDatabaseName("tripee").withUsername("postgres").withPassword(UUID.randomUUID().toString());
    try {
      container.start();
      container.copyFileToContainer(MountableFile.forHostPath(Path.of("db/schema.sql").toAbsolutePath()), "/tmp/schema.sql");
      container.copyFileToContainer(MountableFile.forHostPath(Path.of("../infra/provision-backend.sql").toAbsolutePath()), "/tmp/provision.sql");
      run(container, "psql", "-X", "-U", "postgres", "-d", "tripee", "-v", "ON_ERROR_STOP=1", "-f", "/tmp/schema.sql");
      run(container, "env", "TRIPEE_DB_PASSWORD=" + APP_PASSWORD, "psql", "-X", "-U", "postgres", "-d", "tripee",
          "-v", "ON_ERROR_STOP=1", "--single-transaction", "-f", "/tmp/provision.sql");
      run(container, "psql", "-X", "-U", "postgres", "-d", "tripee", "-v", "ON_ERROR_STOP=1", "-c", """
          INSERT INTO tripee.admin_account(id,login_id,password_hash,display_name) OVERRIDING SYSTEM VALUE
            VALUES (1,'test-a','test-placeholder','A'),(2,'test-b','test-placeholder','B');
          INSERT INTO tripee.country(id,code,name_ko,name_en) OVERRIDING SYSTEM VALUE VALUES (1,'JP','일본','Japan');
          INSERT INTO tripee.city(id,country_id) OVERRIDING SYSTEM VALUE VALUES (1,1);
          SELECT setval(pg_get_serial_sequence('tripee.city','id'), 1, true);
          """);
      database = container;
      return container;
    } catch (Exception failure) {
      container.stop();
      if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new IllegalStateException("Isolated PostgreSQL setup failed", failure);
    }
  }

  private static void run(PostgreSQLContainer container, String... command) throws Exception {
    var result = container.execInContainer(command);
    if (result.getExitCode() != 0) throw new IllegalStateException("PostgreSQL initialization failed: " + result.getStderr());
  }
}
