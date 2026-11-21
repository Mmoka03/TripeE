package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.SourceRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@Tag("repository")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TRIPEE_TEST_ISOLATED_DB", matches = "true")
@Transactional
class JpaSourcePostgresTest extends com.tripee.backend.PostgresIntegrationTestBase {
  @Autowired SourceRepository sources;

  @Test
  void savesAndSearchesLiteralTextInDescendingCursorOrder() {
    var first = sources.create("https://example.org/source-one", "자료 100%", null);
    var second = sources.create("https://example.org/source-two", "자료 100% 둘", "출판사");
    assertThat(first.createdAt()).isNotNull();
    assertThat(sources.find(Long.parseLong(first.id()))).contains(first);
    assertThat(sources.list(null, "자료 100%", 10)).extracting(SourceRepository.Source::id)
        .containsExactly(second.id(), first.id());
    assertThat(sources.list(Long.parseLong(second.id()), "자료 100%", 10))
        .extracting(SourceRepository.Source::id).containsExactly(first.id());
    assertThat(sources.list(null, "자료 100_", 10)).isEmpty();
  }
}
