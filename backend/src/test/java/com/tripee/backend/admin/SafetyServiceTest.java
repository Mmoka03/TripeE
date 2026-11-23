package com.tripee.backend.admin;

import com.tripee.backend.common.exception.ApiException;
import com.tripee.backend.repository.admin.DraftRepository;
import com.tripee.backend.repository.admin.EditLockRepository;
import com.tripee.backend.repository.admin.SafetyRepository;
import com.tripee.backend.service.admin.AdminSession;
import com.tripee.backend.service.admin.DraftWriteValidator;
import com.tripee.backend.service.admin.PublicationValidator;
import com.tripee.backend.service.admin.SafetyService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("service")
class SafetyServiceTest {
  private final SafetyRepository safety = mock(SafetyRepository.class);
  private final DraftRepository drafts = mock(DraftRepository.class);
  private final EditLockRepository locks = mock(EditLockRepository.class);
  private final TransactionTemplate transactions = mock(TransactionTemplate.class);
  private final JsonMapper json = new JsonMapper();
  private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
  private final SafetyService service = new SafetyService(safety, drafts, locks, new DraftWriteValidator(),
      new PublicationValidator(), transactions, Clock.fixed(now, ZoneOffset.UTC));

  @SuppressWarnings("unchecked")
  @Test
  void sessionInvalidatedWhileWaitingForCityLockCannotPublishSafety() {
    when(transactions.execute(any())).thenAnswer(call ->
        ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
    var session = new MockHttpSession();
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("1", "A", now));
    doAnswer(call -> { session.invalidate(); return null; }).when(locks).lockCity(1);
    assertThatThrownBy(() -> service.publish(1, 1, "\"safety-1-empty\"",
        json.readTree("{\"reviewState\":\"UNVERIFIED\",\"items\":[]}"), session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SESSION_EXPIRED"));
    verifyNoInteractions(safety, drafts);
  }

  @SuppressWarnings("unchecked")
  @Test
  void rejectsExpiredSessionsAndInvalidVersionPreconditionsBeforeWriting() {
    when(transactions.execute(any())).thenAnswer(call -> ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
    when(safety.current(1)).thenReturn(Optional.of(json.readTree("{\"revisionId\":null,\"reviewState\":\"UNVERIFIED\",\"items\":[]}")));
    var input = json.readTree("{\"reviewState\":\"UNVERIFIED\",\"items\":[]}");
    var session = new MockHttpSession();
    assertThatThrownBy(() -> service.publish(1, 1, null, input, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SESSION_EXPIRED"));
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("1", "A", now));
    assertThatThrownBy(() -> service.publish(1, 1, null, input, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("PRECONDITION_REQUIRED"));
    assertThatThrownBy(() -> service.publish(1, 1, "bad", input, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("MALFORMED_REQUEST"));
    assertThatThrownBy(() -> service.publish(1, 1, "\"safety-1-old\"", input, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SAFETY_VERSION_MISMATCH"));
    assertThatThrownBy(() -> service.publish(1, 1, "\"safety-1-empty\"", null, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    verifyNoInteractions(drafts);
  }
}
