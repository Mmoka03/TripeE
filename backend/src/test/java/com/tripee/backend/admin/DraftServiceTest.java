package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.DraftRepository;
import com.tripee.backend.repository.admin.SafetyRepository;
import com.tripee.backend.service.admin.AdminSession;
import com.tripee.backend.service.admin.DraftService;
import com.tripee.backend.service.admin.EditLockService;
import com.tripee.backend.service.admin.DraftWriteValidator;
import com.tripee.backend.common.exception.ApiException;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("service")
class DraftServiceTest {
  private final DraftRepository repository = mock(DraftRepository.class);
  private final EditLockService locks = mock(EditLockService.class);
  private final SafetyRepository safety = mock(SafetyRepository.class);
  private final DraftService drafts = new DraftService(repository, locks, new DraftWriteValidator(),
      safety,
      new com.tripee.backend.service.admin.PublicationValidator(), new JsonMapper(),
      mock(com.tripee.backend.repository.admin.AdminCityRepository.class));

  @SuppressWarnings("unchecked")
  @Test
  void returnsDraftValidatorFromRevisionAndEditVersion() {
    var session = new MockHttpSession();
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("3", "A", Instant.now()));
    when(locks.withLease(eq(1L), eq(3L), eq("token"), eq(session), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(4)).get());
    when(repository.find(1)).thenReturn(Optional.of(new JsonMapper().readTree(
        "{\"revisionId\":\"101\",\"editVersion\":\"7\"}")));
    var response = drafts.find(1, 3, "token", session);
    assertThat(response.version()).isEqualTo("\"draft-101-7\"");
  }

  @Test
  void rejectsRequestsWhenLeaseValidationFailsBeforeQueryingDraft() {
    var session = new MockHttpSession();
    when(locks.withLease(eq(1L), eq(3L), eq("wrong"), eq(session), any()))
        .thenThrow(new ApiException(org.springframework.http.HttpStatus.CONFLICT, "EDIT_LOCK_NOT_OWNED", "denied"));
    assertThatThrownBy(() -> drafts.find(1, 3, "wrong", session)).isInstanceOf(ApiException.class);
    verifyNoInteractions(repository);
  }

  @SuppressWarnings("unchecked")
  @Test
  void safetyReviewRejectsStaleDraftAndStalePublicSafetyBeforeWriting() {
    var session = new MockHttpSession();
    when(locks.withLease(eq(1L), eq(3L), eq("token"), eq(session), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(4)).get());
    var json = new JsonMapper();
    when(repository.find(1)).thenReturn(Optional.of(json.readTree("{\"revisionId\":\"101\",\"editVersion\":\"7\"}")));
    when(safety.current(1)).thenReturn(Optional.of(json.readTree("{\"revisionId\":\"5\"}")));
    var input = json.readTree("""
        {"expectedRevisionId":"101","expectedEditVersion":"6","reviewedPublicSafetyRevisionId":"5",
         "safety":{"reviewState":"UNVERIFIED","items":[]}}
        """);
    assertThatThrownBy(() -> drafts.reviewSafety(1, 3, "token", json.readTree("{}"), session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("MALFORMED_REQUEST"));
    assertThatThrownBy(() -> drafts.reviewSafety(1, 3, "token", input, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("DRAFT_VERSION_MISMATCH"));
    ((tools.jackson.databind.node.ObjectNode) input).put("expectedEditVersion", "7").put("reviewedPublicSafetyRevisionId", "4");
    assertThatThrownBy(() -> drafts.reviewSafety(1, 3, "token", input, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SAFETY_CHANGED"));
    ((tools.jackson.databind.node.ObjectNode) input).put("reviewedPublicSafetyRevisionId", "5").putNull("safety");
    assertThatThrownBy(() -> drafts.reviewSafety(1, 3, "token", input, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    ((tools.jackson.databind.node.ObjectNode) input).set("safety", json.readTree("{\"reviewState\":\"UNVERIFIED\",\"items\":[]}"));
    assertThat(drafts.reviewSafety(1, 3, "token", input, session).version()).isEqualTo("\"draft-101-7\"");
    verify(repository).reviewSafety(eq(1L), eq(3L), any(), any(), eq(5L));
  }

  @SuppressWarnings("unchecked")
  @Test
  void discardRequiresExistingDraftAndMatchingStrongEtag() {
    var session = new MockHttpSession();
    when(locks.withLease(eq(1L), eq(3L), eq("token"), eq(session), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(4)).get());
    var draft = new JsonMapper().readTree("{\"revisionId\":\"101\",\"editVersion\":\"7\"}");
    when(repository.find(1)).thenReturn(Optional.of(draft));
    assertThatThrownBy(() -> drafts.discard(1, 3, "token", null, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("PRECONDITION_REQUIRED"));
    assertThatThrownBy(() -> drafts.discard(1, 3, "token", "W/\"draft-101-7\"", session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("MALFORMED_REQUEST"));
    assertThatThrownBy(() -> drafts.discard(1, 3, "token", "\"draft-101-6\"", session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RESOURCE_VERSION_MISMATCH"));
    verify(repository, never()).discard(anyLong(), anyLong(), any());
    drafts.discard(1, 3, "token", "\"draft-101-7\"", session);
    verify(repository).discard(1, 3, draft);
    when(repository.find(1)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> drafts.find(1, 3, "token", session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("DRAFT_NOT_FOUND"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void safetyReviewAcceptsNullPublicRevisionWhenNothingHasBeenPublished() {
    var session = new MockHttpSession();
    when(locks.withLease(eq(1L), eq(3L), eq("token"), eq(session), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(4)).get());
    var json = new JsonMapper();
    when(repository.find(1)).thenReturn(Optional.of(json.readTree("{\"revisionId\":\"101\",\"editVersion\":\"7\"}")));
    when(safety.current(1)).thenReturn(Optional.of(json.readTree("{\"revisionId\":null}")));
    var input = json.readTree("""
        {"expectedRevisionId":"101","expectedEditVersion":"7","reviewedPublicSafetyRevisionId":null,
         "safety":{"reviewState":"UNVERIFIED","items":[]}}
        """);
    assertThat(drafts.reviewSafety(1, 3, "token", input, session).value()).isNotNull();
    verify(repository).reviewSafety(eq(1L), eq(3L), any(), any(), isNull());
  }

  @SuppressWarnings("unchecked")
  @Test
  void previewRejectsNullAndMissingExpectedVersionFields() {
    var session = new MockHttpSession();
    when(locks.withLease(eq(1L), eq(3L), eq("token"), eq(session), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(4)).get());
    var json = new JsonMapper();
    when(repository.find(1)).thenReturn(Optional.of(json.readTree("{\"revisionId\":\"101\",\"editVersion\":\"7\"}")));
    for (String request : new String[]{"null", "{}", "{\"expectedRevisionId\":null,\"expectedEditVersion\":\"7\"}",
        "{\"expectedRevisionId\":\"101\",\"expectedEditVersion\":null}"}) {
      var input = json.readTree(request);
      assertThatThrownBy(() -> drafts.preview(1, 3, "token", input, session))
          .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("MALFORMED_REQUEST"));
    }
    verifyNoInteractions(safety);
  }
}
