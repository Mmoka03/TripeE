package com.tripee.backend.admin;

import com.tripee.backend.controller.admin.*;
import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Sql(statements = {
    "CREATE TEMP TABLE admin_account AS SELECT id, login_id, display_name, password_hash, is_active FROM tripee.admin_account WITH NO DATA",
    "INSERT INTO pg_temp.admin_account VALUES (9007199254740993, 'admin', 'A', 'test-hash', true)",
    "CREATE TEMP TABLE tag AS SELECT id, code, label_ko, label_en, is_active FROM tripee.tag WITH NO DATA",
    "INSERT INTO pg_temp.tag VALUES (1, 'OLD', '이전', 'Old', false)"
})
@Tag("repository")
class AdminAccountsPostgresTest extends com.tripee.backend.PostgresIntegrationTestBase {
  @Autowired AdminAccounts accounts;
  @Autowired AdminDictionaryController dictionaries;

  @Test
  void readsLoginFieldsAndRetainsInactiveTagsForEditing() {
    var account = accounts.find("admin").orElseThrow();
    assertThat(account.id()).isEqualTo("9007199254740993");
    assertThat(account.displayName()).isEqualTo("A");
    assertThat(account.active()).isTrue();
    assertThat(accounts.find("' OR true --")).isEmpty();
    assertThat(dictionaries.tags().items()).hasSize(1);
    assertThat(dictionaries.tags().items().getFirst().isActive()).isFalse();
    assertThat(dictionaries.countries()).isNotNull();
  }
}
