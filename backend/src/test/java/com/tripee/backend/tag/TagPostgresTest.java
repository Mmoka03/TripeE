package com.tripee.backend.tag;

import com.tripee.backend.controller.tag.*;
import com.tripee.backend.repository.tag.*;


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
@Tag("repository")
class TagPostgresTest extends com.tripee.backend.PostgresIntegrationTestBase {
  @Autowired TagRepository tags;

  @Test
  @Sql(statements = {
      "CREATE TEMP TABLE tag AS SELECT id, code, label_ko, label_en, is_active FROM tripee.tag WITH NO DATA",
      """
      INSERT INTO pg_temp.tag VALUES
      (3, 'NATURE', '자연', 'Nature', true), (2, 'OLD', '폐기', 'Old', false), (1, 'ART', '예술', 'Art', true);
      """
  })
  void excludesInactiveTagsAndReturnsActiveTagsInStableOrder() {
    assertThat(tags.listActive()).extracting(TagRepository.PublicTag::code)
        .containsExactly("ART", "NATURE");
  }
}
