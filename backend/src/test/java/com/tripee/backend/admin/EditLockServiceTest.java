package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.EditLockRepository;
import com.tripee.backend.service.admin.EditLockService;
import com.tripee.backend.service.admin.AdminSession;
import com.tripee.backend.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("service")
class EditLockServiceTest {
  private final EditLockRepository repository = mock(EditLockRepository.class);
  private final TransactionTemplate transactions = mock(TransactionTemplate.class);
  private final Instant now = Instant.parse("2026-11-19T00:00:00Z");
  private final EditLockService service = new EditLockService(repository, transactions, Clock.fixed(now, ZoneOffset.UTC));

  @SuppressWarnings("unchecked")
  private void executeCallbacks() {
    when(transactions.execute(any())).thenAnswer(call ->
        ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
  }

  @Test
  void activeLeaseBlocksEvenSameAdministrator() {
    executeCallbacks();
    when(repository.find(1)).thenReturn(Optional.of(new EditLockRepository.Lock(
        7, "A", UUID.randomUUID(), "hash", now.plusSeconds(1))));
    assertThatThrownBy(() -> service.acquire(1, 7, UUID.randomUUID(), new MockHttpSession()))
        .isInstanceOf(ApiException.class);
    verify(repository, never()).save(anyLong(), anyLong(), any(), anyString(), any());
  }

  @Test
  void expiredLeaseAppearsUnlockedWithoutWriting() {
    executeCallbacks();
    when(repository.find(1)).thenReturn(Optional.of(new EditLockRepository.Lock(
        7, "A", UUID.randomUUID(), "hash", now)));
    var view = service.view(1);
    assertThat(view.isLocked()).isFalse();
    assertThat(view.owner()).isNull();
    verify(repository, never()).delete(anyLong());
  }

  @Test
  void tokenCannotBeUsedFromAnotherSessionAndExpiredTokenCannotBeRenewed() throws Exception {
    executeCallbacks();
    var originalSession = new MockHttpSession();
    var otherSession = new MockHttpSession();
    var editor = UUID.randomUUID();
    String[] savedHash = new String[1];
    doAnswer(call -> {
      savedHash[0] = call.getArgument(3);
      return null;
    }).when(repository).save(eq(1L), eq(7L), eq(editor), anyString(), eq(now));
    when(repository.find(1)).thenAnswer(call -> savedHash[0] == null ? Optional.empty()
        : Optional.of(new EditLockRepository.Lock(7, "A", editor, savedHash[0], now.plusSeconds(300))));

    var lease = service.acquire(1, 7, editor, originalSession);
    String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(lease.leaseToken().getBytes(StandardCharsets.UTF_8)));
    assertThat(savedHash[0]).isEqualTo(expectedHash).isNotEqualTo(lease.leaseToken());
    assertThatThrownBy(() -> service.heartbeat(1, 7, lease.leaseToken(), otherSession))
        .isInstanceOf(ApiException.class);
    when(repository.find(1)).thenReturn(Optional.of(new EditLockRepository.Lock(
        7, "A", editor, savedHash[0], now)));
    assertThatThrownBy(() -> service.heartbeat(1, 7, lease.leaseToken(), originalSession))
        .isInstanceOf(ApiException.class);
    verify(repository, never()).renew(anyLong(), any());
  }

  @Test
  void validOwnerCanRenewAndUseLeaseWhileOtherActorsAndTokensCannot() {
    executeCallbacks();
    var session = new MockHttpSession();
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("7", "A", now));
    var editor = UUID.randomUUID();
    String[] savedHash = new String[1];
    doAnswer(call -> { savedHash[0] = call.getArgument(3); return null; })
        .when(repository).save(eq(1L), eq(7L), eq(editor), anyString(), eq(now));
    when(repository.find(1)).thenAnswer(call -> savedHash[0] == null ? Optional.empty()
        : Optional.of(new EditLockRepository.Lock(7, "A", editor, savedHash[0], now.plusSeconds(300))));
    var lease = service.acquire(1, 7, editor, session);
    assertThat(service.withLease(1, 7, lease.leaseToken(), session, () -> "saved")).isEqualTo("saved");
    assertThat(service.heartbeat(1, 7, lease.leaseToken(), session).expiresAt()).isEqualTo(now.plusSeconds(300));
    verify(repository).renew(1, now);
    assertThatThrownBy(() -> service.withLease(1, 8, lease.leaseToken(), session, () -> "bad"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("EDIT_LOCK_NOT_OWNED"));
    assertThatThrownBy(() -> service.withLease(1, 7, "wrong", session, () -> "bad"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("EDIT_LOCK_NOT_OWNED"));
    session.removeAttribute(AdminSession.ATTRIBUTE);
    assertThatThrownBy(() -> service.withLease(1, 7, lease.leaseToken(), session, () -> "bad"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SESSION_EXPIRED"));
  }
}
