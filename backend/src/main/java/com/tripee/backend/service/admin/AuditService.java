package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.AuditRepository;
import com.tripee.backend.common.exception.ApiException;
import java.util.Base64;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("postgres")
public class AuditService {
  private final AuditRepository audit;
  private final ObjectMapper json;

  public AuditService(AuditRepository audit, ObjectMapper json) {
    this.audit = audit;
    this.json = json;
  }

  public JsonNode find(String id) {
    return audit.find(parseId(id)).orElseThrow(() ->
        new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "이력을 찾을 수 없어요."));
  }

  public JsonNode historicalPublication(String cityId, String revisionId) {
    return audit.historicalPublication(parseId(cityId), parseId(revisionId)).orElseThrow(() ->
        new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "게시 이력을 찾을 수 없어요."));
  }

  public Page list(String cityRefId, String cursor, int limit) {
    if (limit < 1 || limit > 100) throw invalid();
    Long city = cityRefId == null ? null : parseId(cityRefId);
    Long before = null;
    if (cursor != null) {
      try {
        Cursor c = json.readValue(Base64.getUrlDecoder().decode(cursor), Cursor.class);
        if (!java.util.Objects.equals(c.cityRefId(), city) || c.before() <= 0) throw new IllegalArgumentException();
        before = c.before();
      } catch (RuntimeException invalid) {
        throw invalid();
      }
    }
    var results = audit.list(city, before, limit + 1);
    boolean more = results.size() > limit;
    var items = more ? results.subList(0, limit) : results;
    String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(
        json.writeValueAsBytes(new Cursor(Long.parseLong(items.getLast().get("id").asString()), city))) : null;
    return new Page(items, next);
  }

  private static long parseId(String id) {
    try {
      if (!id.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      return Long.parseLong(id);
    } catch (NumberFormatException invalid) {
      throw invalid();
    }
  }

  private static ApiException invalid() {
    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "이력 조회 조건을 확인해 주세요.");
  }

  public record Cursor(long before, Long cityRefId) {}
  public record Page(List<JsonNode> items, String nextCursor) {}
}
