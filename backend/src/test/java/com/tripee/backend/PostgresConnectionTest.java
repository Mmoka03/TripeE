package com.tripee.backend;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("postgres")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("repository")
class PostgresConnectionTest extends com.tripee.backend.PostgresIntegrationTestBase {
  @Autowired
  JdbcClient jdbc;

  @Test
  void readsTheCityTableInTheApplicationSchema() {
    assertEquals("tripee", jdbc.sql("select current_schema()").query(String.class).single());
    assertTrue(jdbc.sql("select id from city limit 0").query(Long.class).list().isEmpty());
  }

  @Test
  void connectsWithLimitedApplicationPrivileges() {
    assertFalse(jdbc.sql("select rolsuper from pg_roles where rolname = current_user")
        .query(Boolean.class).single());
    assertFalse(jdbc.sql("select has_schema_privilege(current_user, 'tripee', 'CREATE')")
        .query(Boolean.class).single());
    assertTrue(jdbc.sql("select has_table_privilege(current_user, 'city', 'INSERT')")
        .query(Boolean.class).single());
    assertFalse(jdbc.sql("select has_table_privilege(current_user, 'city', 'TRUNCATE')")
        .query(Boolean.class).single());
    assertFalse(jdbc.sql("select has_table_privilege(current_user, 'audit_event', 'DELETE')")
        .query(Boolean.class).single());
    assertTrue(jdbc.sql("select has_column_privilege(current_user, 'admin_account', 'password_hash', 'SELECT')")
        .query(Boolean.class).single());
    assertFalse(jdbc.sql("select has_table_privilege(current_user, 'admin_account', 'SELECT')")
        .query(Boolean.class).single());
  }
}
