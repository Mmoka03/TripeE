package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.DraftRepository;
import com.tripee.backend.repository.admin.SourceRepository;
import com.tripee.backend.service.admin.EditLockService;
import com.tripee.backend.service.admin.ExperienceSuggestionProvider;
import com.tripee.backend.service.admin.ExperienceSuggestionService;
import com.tripee.backend.common.exception.ApiException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("service")
class ExperienceSuggestionServiceTest {
  private final DraftRepository drafts = mock(DraftRepository.class);
  private final SourceRepository sources = mock(SourceRepository.class);
  private final EditLockService locks = mock(EditLockService.class);
  private final ExperienceSuggestionProvider provider = mock(ExperienceSuggestionProvider.class);
  private final ExperienceSuggestionService service = new ExperienceSuggestionService(drafts, sources, locks, provider);
  private final MockHttpSession session = new MockHttpSession();
  private final JsonMapper json = new JsonMapper();
  private final AtomicBoolean insideLease = new AtomicBoolean();

  @SuppressWarnings("unchecked")
  @BeforeEach void setUp() {
    when(locks.withLease(eq(1L), eq(2L), eq("token"), eq(session), any())).thenAnswer(call -> {
      insideLease.set(true);
      try { return ((Supplier<Object>) call.getArgument(4)).get(); }
      finally { insideLease.set(false); }
    });
    when(drafts.find(1)).thenReturn(Optional.of(json.readTree("{\"revisionId\":\"3\",\"editVersion\":\"0\"}")));
    when(sources.find(4)).thenReturn(Optional.of(new SourceRepository.Source("4", "https://example.org", "출처", null, Instant.EPOCH)));
  }
  @AfterEach void stop() { service.shutdown(); }
  private tools.jackson.databind.JsonNode input() {
    return json.readTree("{\"expectedRevisionId\":\"3\",\"expectedEditVersion\":\"0\",\"sources\":[{\"sourceDocumentId\":\"4\",\"text\":\"자료 본문\"}]}");
  }

  @Test void returnsUnpersistedSuggestionsAndCallsProviderOutsideLease() {
    when(provider.generate(anyList())).thenAnswer(call -> {
      assertThat(insideLease).isFalse();
      return json.readTree("{\"experiences\":[{\"title\":\"경험\",\"body\":\"내용\",\"sourceDocumentIds\":[\"4\"]}],\"warnings\":[]}");
    });
    var result = service.suggest(1, 2, "token", session, input());
    assertThat(result.basedOnRevisionId()).isEqualTo("3");
    assertThat(result.basedOnEditVersion()).isEqualTo("0");
    assertThat(result.persisted()).isFalse();
    assertThat(result.experiences()).singleElement().satisfies(e -> assertThat(e.sourceDocumentIds()).containsExactly("4"));
    verify(drafts, never()).replace(anyLong(), anyLong(), any(), any());
  }

  @Test void rejectsStaleVersionAndUnregisteredSourceBeforeCallingProvider() {
    var stale = (tools.jackson.databind.node.ObjectNode) input();
    stale.put("expectedEditVersion", "1");
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, stale)).isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo("DRAFT_VERSION_MISMATCH"));
    when(sources.find(4)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, input())).isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    verifyNoInteractions(provider);
  }

  @Test void rejectsProviderInventedOrRepeatedSourceIds() {
    when(provider.generate(anyList())).thenReturn(json.readTree("{\"experiences\":[{\"title\":\"경험\",\"body\":\"내용\",\"sourceDocumentIds\":[\"5\"]}],\"warnings\":[]}"),
        json.readTree("{\"experiences\":[{\"title\":\"경험\",\"body\":\"내용\",\"sourceDocumentIds\":[\"4\",\"4\"]}],\"warnings\":[]}"));
    for (int i = 0; i < 2; i++) assertThatThrownBy(() -> service.suggest(1, 2, "token", session, input()))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("AI_INVALID_RESPONSE"));
  }

  @Test void rejectsInputLengthAndProviderFailureWithoutSaving() {
    var oversized = (tools.jackson.databind.node.ObjectNode) input();
    ((tools.jackson.databind.node.ObjectNode) oversized.get("sources").get(0)).put("text", "가".repeat(10001));
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, oversized)).isInstanceOf(ApiException.class);
    when(provider.generate(anyList())).thenThrow(new IllegalStateException("private provider error"));
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, input())).isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo("AI_UPSTREAM_ERROR"));
    verify(drafts, never()).replace(anyLong(), anyLong(), any(), any());
  }

  @Test void abortsSlowProviderWithoutSavingDraft() {
    var shortTimeout = new ExperienceSuggestionService(drafts, sources, locks, provider, java.time.Duration.ofMillis(50));
    try {
      when(provider.generate(anyList())).thenAnswer(call -> { Thread.sleep(10_000); return null; });
      assertThatThrownBy(() -> shortTimeout.suggest(1, 2, "token", session, input()))
          .isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status().value()).isEqualTo(504);
            assertThat(e.code()).isEqualTo("AI_TIMEOUT");
          });
      verify(drafts, never()).replace(anyLong(), anyLong(), any(), any());
    } finally { shortTimeout.shutdown(); }
  }

  @Test void rejectsMalformedAndOutOfRangeRequests() {
    var bad = (tools.jackson.databind.node.ObjectNode) input();
    bad.put("expectedRevisionId", "0");
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, bad)).isInstanceOf(ApiException.class);
    bad.put("expectedRevisionId", "9223372036854775808");
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, bad)).isInstanceOf(ApiException.class);
    bad.put("expectedRevisionId", "3").put("expectedEditVersion", "-1");
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, bad)).isInstanceOf(ApiException.class);
    bad.put("expectedEditVersion", "0").putArray("sources");
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, bad)).isInstanceOf(ApiException.class);
    var blank = (tools.jackson.databind.node.ObjectNode) input();
    ((tools.jackson.databind.node.ObjectNode) blank.get("sources").get(0)).put("text", " ");
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, blank)).isInstanceOf(ApiException.class);
    verifyNoInteractions(provider);
  }

  @Test void rejectsInvalidProviderShapeAndBlankContent() {
    when(provider.generate(anyList())).thenReturn(json.readTree("{}"),
        json.readTree("{\"experiences\":[{\"title\":\" \",\"body\":\"본문\",\"sourceDocumentIds\":[\"4\"]}],\"warnings\":[]}"),
        json.readTree("{\"experiences\":[],\"warnings\":[1]}"));
    for (int i = 0; i < 3; i++) assertThatThrownBy(() -> service.suggest(1, 2, "token", session, input()))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("AI_INVALID_RESPONSE"));
  }

  @Test void acceptsThirtyThousandCharactersAndRejectsOneMoreBeforeCallingProvider() {
    var input = (tools.jackson.databind.node.ObjectNode) input();
    var entries = input.putArray("sources");
    for (int i = 0; i < 3; i++) entries.addObject().put("sourceDocumentId", "4").put("text", "가".repeat(10_000));
    when(provider.generate(anyList())).thenReturn(json.readTree("{\"experiences\":[],\"warnings\":[]}"));
    assertThat(service.suggest(1, 2, "token", session, input).experiences()).isEmpty();
    entries.addObject().put("sourceDocumentId", "4").put("text", "가");
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, input))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.status().value()).isEqualTo(413);
          assertThat(e.code()).isEqualTo("PAYLOAD_TOO_LARGE");
        });
    verify(provider, times(1)).generate(anyList());
  }

  @Test void enforcesCandidateCountAndOutputLength() {
    var output = json.createObjectNode();
    var experiences = output.putArray("experiences");
    output.putArray("warnings").add("확인 필요");
    var item = experiences.addObject().put("title", "가".repeat(200)).put("body", "나".repeat(3000));
    item.putArray("sourceDocumentIds").add("4");
    when(provider.generate(anyList())).thenReturn(output);
    assertThat(service.suggest(1, 2, "token", session, input()).warnings()).containsExactly("확인 필요");
    item.put("body", "나".repeat(3001));
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, input()))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("AI_INVALID_RESPONSE"));
    item.put("body", "본문");
    for (int i = 0; i < 5; i++) experiences.add(item.deepCopy());
    assertThatThrownBy(() -> service.suggest(1, 2, "token", session, input()))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("AI_INVALID_RESPONSE"));
  }
}
