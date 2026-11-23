package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.SourceRepository;
import com.tripee.backend.service.admin.SourceService;
import com.tripee.backend.service.admin.SourceServiceImpl;
import com.tripee.backend.common.exception.ApiException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("service")
class SourceServiceTest {
  private final SourceRepository repository = mock(SourceRepository.class);
  private final JsonMapper json = new JsonMapper();
  private final SourceService service = new SourceServiceImpl(repository, json);

  @ParameterizedTest
  @ValueSource(strings = {"0", "01", "-1", "9223372036854775808"})
  void rejectsMalformedAndOverflowingIds(String id) {
    assertThatThrownBy(() -> service.find(id)).isInstanceOf(ApiException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void rejectsNonHttpUrlsAndInvalidPagination() {
    var input = json.createObjectNode().put("url", "file:///tmp/data")
        .put("title", "source").putNull("publisher");
    assertThatThrownBy(() -> service.create(input)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.list(null, 0, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.list(null, 101, null)).isInstanceOf(ApiException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void validatesCreationFieldsAndLengthBoundaries() {
    var input = json.createObjectNode().put("url", "https://example.org").put("title", "가".repeat(500))
        .put("publisher", "가".repeat(200));
    var source = new SourceRepository.Source("1", "https://example.org", "title", null, Instant.EPOCH);
    when(repository.create(anyString(), anyString(), anyString())).thenReturn(source);
    assertThat(service.create(input)).isEqualTo(source);
    input.put("title", "가".repeat(501));
    assertThatThrownBy(() -> service.create(input)).isInstanceOf(ApiException.class);
    input.put("title", " ");
    assertThatThrownBy(() -> service.create(input)).isInstanceOf(ApiException.class);
    input.put("title", "제목").put("publisher", "가".repeat(201));
    assertThatThrownBy(() -> service.create(input)).isInstanceOf(ApiException.class);
    input.put("publisher", " ");
    assertThatThrownBy(() -> service.create(input)).isInstanceOf(ApiException.class);
    input.putNull("publisher").remove("url");
    assertThatThrownBy(() -> service.create(input)).isInstanceOf(ApiException.class);
    verify(repository, times(1)).create(anyString(), anyString(), anyString());
  }

  @Test
  void sourcePaginationPreservesSearchAndRejectsInvalidCursor() {
    var a = new SourceRepository.Source("3", "https://example.org/a", "자료", null, Instant.EPOCH);
    var b = new SourceRepository.Source("2", "https://example.org/b", "자료", null, Instant.EPOCH);
    when(repository.list(null, "자료", 2)).thenReturn(List.of(a, b));
    when(repository.list(3L, "자료", 2)).thenReturn(List.of(b));
    var first = service.list(null, 1, " 자료 ");
    assertThat(first.items()).containsExactly(a);
    assertThat(service.list(first.nextCursor(), 1, "자료").items()).containsExactly(b);
    assertThatThrownBy(() -> service.list(first.nextCursor(), 1, "다른 검색" )).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.list("invalid", 1, "자료")).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.list(null, 1, "가".repeat(101))).isInstanceOf(ApiException.class);
    when(repository.find(1)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.find("1")).isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo("RESOURCE_NOT_FOUND"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "null", "[]", "{}", "{\"url\":\"https://example.org\",\"title\":\"자료\"}",
      "{\"url\":null,\"title\":\"자료\",\"publisher\":null}",
      "{\"url\":\"https://example.org\",\"title\":null,\"publisher\":null}",
      "{\"url\":\"https://example.org\",\"title\":\"자료\",\"publisher\":42}",
      "{\"url\":\"https://example.org\",\"title\":\"자료\",\"extra\":null}"
  })
  void rejectsMalformedRequestShapesBeforeDatabaseAccess(String request) {
    assertThatThrownBy(() -> service.create(json.readTree(request))).isInstanceOfSatisfying(ApiException.class,
        error -> assertThat(error.code()).isEqualTo("MALFORMED_REQUEST"));
    verifyNoInteractions(repository);
  }

  @Test
  void acceptsNullPublisherAndRejectsUrlWithoutHostAndOversizedUrl() {
    var input = json.createObjectNode().put("url", "https://example.org").put("title", "자료").putNull("publisher");
    var expected = new SourceRepository.Source("1", "https://example.org", "자료", null, Instant.EPOCH);
    when(repository.create("https://example.org", "자료", null)).thenReturn(expected);
    assertThat(service.create(input)).isEqualTo(expected);
    input.put("url", "https:/missing-host");
    assertThatThrownBy(() -> service.create(input)).isInstanceOfSatisfying(ApiException.class,
        error -> assertThat(error.code()).isEqualTo("VALIDATION_FAILED"));
    input.put("url", "https://example.org/" + "a".repeat(4096));
    assertThatThrownBy(() -> service.create(input)).isInstanceOfSatisfying(ApiException.class,
        error -> assertThat(error.code()).isEqualTo("VALIDATION_FAILED"));
    verify(repository, times(1)).create(anyString(), anyString(), isNull());
  }
}
