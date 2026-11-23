package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.AuditRepository;
import com.tripee.backend.service.admin.AuditService;
import com.tripee.backend.common.exception.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("service")
class AuditServiceTest {
  private final AuditRepository repository = mock(AuditRepository.class);
  private final JsonMapper json = new JsonMapper();
  private final AuditService service = new AuditService(repository, json);

  @ParameterizedTest
  @ValueSource(strings = {"0", "01", "-1", "9223372036854775808"})
  void rejectsInvalidIdsWithoutQueryingDatabase(String id) {
    assertThatThrownBy(() -> service.find(id)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.historicalPublication("1", id)).isInstanceOf(ApiException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void reportsMissingEventAndHistoricalPublication() {
    when(repository.find(4)).thenReturn(Optional.empty());
    when(repository.historicalPublication(1, 4)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.find("4")).isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo("RESOURCE_NOT_FOUND"));
    assertThatThrownBy(() -> service.historicalPublication("1", "4")).isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo("RESOURCE_NOT_FOUND"));
  }

  @Test
  void paginatesWithoutRepeatingItemsAndBindsCursorToCity() {
    var newest = json.readTree("{\"id\":\"3\"}");
    var older = json.readTree("{\"id\":\"2\"}");
    when(repository.list(1L, null, 2)).thenReturn(List.of(newest, older));
    when(repository.list(1L, 3L, 2)).thenReturn(List.of(older));
    var first = service.list("1", null, 1);
    assertThat(first.items()).containsExactly(newest);
    assertThat(first.nextCursor()).isNotBlank();
    var second = service.list("1", first.nextCursor(), 1);
    assertThat(second.items()).containsExactly(older);
    assertThat(second.nextCursor()).isNull();
    assertThatThrownBy(() -> service.list("2", first.nextCursor(), 1)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.list("1", "not-a-valid-cursor", 1)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.list("1", null, 0)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.list("1", null, 101)).isInstanceOf(ApiException.class);
    verify(repository).list(1L, null, 2);
    verify(repository).list(1L, 3L, 2);
    verifyNoMoreInteractions(repository);
  }
}
