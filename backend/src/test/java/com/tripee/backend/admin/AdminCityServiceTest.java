package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.AdminCityRepository;
import com.tripee.backend.service.admin.AdminCityService;
import com.tripee.backend.common.exception.ApiException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("service")
class AdminCityServiceTest {
  private final AdminCityRepository repository = mock(AdminCityRepository.class);
  private final com.tripee.backend.service.admin.EditLockService locks = mock(com.tripee.backend.service.admin.EditLockService.class);
  private final JsonMapper json = new JsonMapper();
  private final AdminCityService cities = new AdminCityService(repository, json, locks);

  private com.tripee.backend.model.admin.CityModels.City city(String id, String visibility) {
    return new com.tripee.backend.model.admin.CityModels.City(id, new com.tripee.backend.model.admin.CityModels.Country("1", "JP", "일본", "Japan"),
        visibility, "교토", "Kyoto", null, "1", "0", null);
  }

  @Test
  void rejectsInvalidFiltersBeforeQueryingDatabase() {
    assertThatThrownBy(() -> cities.list(null, 101, null, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> cities.list(null, 20, null, "INVALID")).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> cities.list("invalid-cursor", 20, null, null)).isInstanceOf(ApiException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void rejectsIdOutsidePostgresBigintRangeBeforeQueryingDatabase() {
    assertThatThrownBy(() -> cities.find("9223372036854775808")).isInstanceOf(ApiException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void creationValidatesInputBoundariesBeforeWriting() {
    var auth = UsernamePasswordAuthenticationToken.authenticated("1", null, List.of());
    var base = json.readTree("""
        {"countryId":"1","nameKo":"교토","nameEn":"Kyoto","coordinates":{"latitude":90,"longitude":-180},"tagline":"여행"}
        """);
    when(repository.create(anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(new long[] {2, 3});
    when(repository.find(2)).thenReturn(city("2", "HIDDEN"));
    assertThat(cities.create(base, 1).draftRevisionId()).isEqualTo("3");
    var bad = ((tools.jackson.databind.node.ObjectNode) base).deepCopy();
    bad.putObject("coordinates").put("latitude", 90.0001).put("longitude", 0);
    assertThatThrownBy(() -> cities.create(bad, 1)).isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    bad.put("countryId", "9223372036854775808");
    assertThatThrownBy(() -> cities.create(bad, 1)).isInstanceOf(ApiException.class);
    bad.put("countryId", "1");
    bad.put("nameKo", " ");
    assertThatThrownBy(() -> cities.create(bad, 1)).isInstanceOf(ApiException.class);
    verify(repository, times(1)).create(anyLong(), anyLong(), any(), any(), any(), any(), any());
  }

  @SuppressWarnings("unchecked")
  @Test
  void visibilityAndDeletionRequireMatchingVersionAndDisplayableRevision() {
    var session = new MockHttpSession();
    when(locks.withLease(eq(1L), eq(1L), eq("token"), eq(session), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(4)).get());
    when(repository.find(1)).thenReturn(city("1", "HIDDEN"));
    String etag = cities.find("1").version();
    assertThatThrownBy(() -> cities.changeVisibility("1", "PUBLIC", "token", etag, 1, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    assertThatThrownBy(() -> cities.changeVisibility("1", "PREPARING", "token", null, 1, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("PRECONDITION_REQUIRED"));
    assertThatThrownBy(() -> cities.changeVisibility("1", "PREPARING", "token", "bad", 1, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("MALFORMED_REQUEST"));
    assertThatThrownBy(() -> cities.changeVisibility("1", "PREPARING", "token", "\"stale\"", 1, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RESOURCE_VERSION_MISMATCH"));
    assertThatThrownBy(() -> cities.changeVisibility("1", "PREPARING", "token", etag, 1, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("PREPARING_REQUIRES_REVISION"));
    when(repository.hasDisplayableRevision(1)).thenReturn(true);
    cities.changeVisibility("1", "PREPARING", "token", etag, 1, session);
    verify(repository).changeVisibility(eq(1L), eq(1L), eq("PREPARING"), any());
    assertThatThrownBy(() -> cities.delete("1", "token", "\"stale\"", 1, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RESOURCE_VERSION_MISMATCH"));
    assertThatThrownBy(() -> cities.delete("1", "token", null, 1, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("PRECONDITION_REQUIRED"));
    assertThatThrownBy(() -> cities.delete("1", "token", "invalid", 1, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("MALFORMED_REQUEST"));
    cities.changeVisibility("1", "HIDDEN", "token", etag, 1, session);
    verify(repository, never()).changeVisibility(eq(1L), eq(1L), eq("HIDDEN"), any());
    cities.delete("1", "token", etag, 1, session);
    verify(repository).delete(1, 1);
  }

  @Test
  void cursorCannotBeReusedWithDifferentSearchConditions() {
    when(repository.list(null, "HIDDEN", "교토", 1)).thenReturn(List.of(city("3", "HIDDEN"), city("2", "HIDDEN")));
    var page = cities.list(null, 1, " 교토 ", "HIDDEN");
    assertThat(page.items()).hasSize(1);
    assertThat(page.nextCursor()).isNotBlank();
    assertThatThrownBy(() -> cities.list(page.nextCursor(), 1, "오사카", "HIDDEN"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_QUERY"));
    assertThatThrownBy(() -> cities.list(page.nextCursor(), 1, "교토", "PUBLIC"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_QUERY"));
    assertThatThrownBy(() -> cities.list(null, 0, null, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> cities.list(null, 1, "가".repeat(101), null)).isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsMalformedCityCreationAndInvalidCoordinateEdges() {
    var auth = UsernamePasswordAuthenticationToken.authenticated("1", null, List.of());
    var input = json.createObjectNode().put("countryId", "1").putNull("nameKo").putNull("nameEn")
        .putNull("coordinates").putNull("tagline");
    input.put("countryId", "0");
    assertThatThrownBy(() -> cities.create(input, 1)).isInstanceOf(ApiException.class);
    input.put("countryId", "1").putObject("coordinates").put("latitude", 0);
    assertThatThrownBy(() -> cities.create(input, 1)).isInstanceOf(ApiException.class);
    input.putObject("coordinates").put("latitude", -90.001).put("longitude", 0);
    assertThatThrownBy(() -> cities.create(input, 1)).isInstanceOf(ApiException.class);
    input.putObject("coordinates").put("latitude", 0).put("longitude", 180.001);
    assertThatThrownBy(() -> cities.create(input, 1)).isInstanceOf(ApiException.class);
    input.putObject("coordinates").put("latitude", 0).put("longitude", -180.001);
    assertThatThrownBy(() -> cities.create(input, 1)).isInstanceOf(ApiException.class);
    input.putNull("coordinates").put("nameEn", "x".repeat(151));
    assertThatThrownBy(() -> cities.create(input, 1)).isInstanceOf(ApiException.class);
    input.putNull("nameEn").remove("tagline");
    assertThatThrownBy(() -> cities.create(input, 1)).isInstanceOf(ApiException.class);
    verifyNoInteractions(repository);
  }
}
