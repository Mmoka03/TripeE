package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.SourceRepository;
import com.tripee.backend.repository.admin.SourceRepository.Source;
import com.tripee.backend.common.exception.ApiException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("postgres")
public class SourceServiceImpl implements SourceService {
  private final SourceRepository sources;
  private final ObjectMapper json;

  public SourceServiceImpl(SourceRepository sources, ObjectMapper json) {
    this.sources = sources;
    this.json = json;
  }

  public Source create(JsonNode input) {
    if (!input.isObject() || input.size() != 3 || !input.has("url") || !input.has("title")
        || !input.has("publisher") || !input.get("url").isString() || !input.get("title").isString()
        || !(input.get("publisher").isString() || input.get("publisher").isNull())) {
      throw error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    }
    String url = input.get("url").asString();
    String title = input.get("title").asString();
    String publisher = input.get("publisher").isNull() ? null : input.get("publisher").asString();
    try {
      URI uri = URI.create(url);
      if (url.length() > 4096 || uri.getHost() == null || !List.of("http", "https").contains(uri.getScheme())
          || title.isBlank() || title.codePointCount(0, title.length()) > 500
          || (publisher != null && (publisher.isBlank() || publisher.codePointCount(0, publisher.length()) > 200))) {
        throw new IllegalArgumentException();
      }
    } catch (IllegalArgumentException invalid) {
      throw error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED");
    }
    return sources.create(url, title, publisher);
  }

  public Source find(String id) {
    return sources.find(parseId(id)).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
  }

  public Page list(String cursor, int limit, String q) {
    if (limit < 1 || limit > 100 || (q != null && q.codePointCount(0, q.length()) > 100)) {
      throw error(HttpStatus.BAD_REQUEST, "INVALID_QUERY");
    }
    String query = q == null ? "" : q.strip();
    Long before = null;
    if (cursor != null) {
      try {
        var parsed = json.readValue(Base64.getUrlDecoder().decode(cursor), Cursor.class);
        if (!query.equals(parsed.query()) || parsed.before() <= 0) throw new IllegalArgumentException();
        before = parsed.before();
      } catch (RuntimeException invalid) {
        throw error(HttpStatus.BAD_REQUEST, "INVALID_QUERY");
      }
    }
    var results = sources.list(before, query, limit + 1);
    boolean more = results.size() > limit;
    var items = more ? results.subList(0, limit) : results;
    String next = more ? Base64.getUrlEncoder().withoutPadding()
        .encodeToString(json.writeValueAsString(new Cursor(Long.parseLong(items.getLast().id()), query))
            .getBytes(StandardCharsets.UTF_8)) : null;
    return new Page(items, next);
  }

  private static long parseId(String id) {
    try {
      if (!id.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      return Long.parseLong(id);
    } catch (NumberFormatException invalid) {
      throw error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    }
  }

  private static ApiException error(HttpStatus status, String code) {
    return new ApiException(status, code, "출처 요청을 확인해 주세요.");
  }

}
