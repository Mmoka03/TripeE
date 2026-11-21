package com.tripee.backend.service.admin;

import com.tripee.backend.model.admin.CityModels.City;
import com.tripee.backend.model.admin.CityModels.Country;

import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import com.tripee.backend.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Base64;
import java.util.List;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import com.tripee.backend.model.admin.Versioned;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@org.springframework.stereotype.Service
@Profile("postgres")
public class AdminCityService {
  private final AdminCityRepository cities;
  private final ObjectMapper json;
  private final EditLockService locks;

  public AdminCityService(AdminCityRepository cities, ObjectMapper json, EditLockService locks) {
    this.cities = cities;
    this.json = json;
    this.locks = locks;
  }

  public Versioned<City> changeVisibility(String cityId, String visibility, String token, String ifMatch,
                                                long actorId, jakarta.servlet.http.HttpSession session) {
    if (visibility == null || !List.of("HIDDEN", "PREPARING").contains(visibility)) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "변경할 도시 상태를 확인해 주세요.");
    }
    long id;
    try {
      if (!cityId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      id = Long.parseLong(cityId);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 ID를 확인해 주세요.");
    }
    return locks.withLease(id, actorId, token, session, () -> {
      Versioned<City> current = find(cityId);
      if (ifMatch == null) {
        throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "If-Match가 필요합니다.");
      }
      if (!ifMatch.matches("\"[^\"\\r\\n]+\"")) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "If-Match 형식을 확인해 주세요.");
      }
      if (!ifMatch.equals(current.version())) {
        throw new ApiException(HttpStatus.PRECONDITION_FAILED, "RESOURCE_VERSION_MISMATCH", "도시 상태가 변경되었습니다.");
      }
      City before = current.value();
      if ("PREPARING".equals(visibility) && !cities.hasDisplayableRevision(id)) {
        throw new ApiException(HttpStatus.CONFLICT, "PREPARING_REQUIRES_REVISION", "이름과 좌표가 필요합니다.");
      }
      if (!visibility.equals(before.visibility())) cities.changeVisibility(id, actorId, visibility, before);
      return find(cityId);
    });
  }

  public void delete(String cityId, String token, String ifMatch, long actorId,
                     jakarta.servlet.http.HttpSession session) {
    long id;
    try {
      if (!cityId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      id = Long.parseLong(cityId);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 ID를 확인해 주세요.");
    }
    locks.withLease(id, actorId, token, session, () -> {
      Versioned<City> current = find(cityId);
      if (ifMatch == null) {
        throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "If-Match가 필요합니다.");
      }
      if (!ifMatch.matches("\"[^\"\\r\\n]+\"")) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "If-Match 형식을 확인해 주세요.");
      }
      if (!ifMatch.equals(current.version())) {
        throw new ApiException(HttpStatus.PRECONDITION_FAILED, "RESOURCE_VERSION_MISMATCH", "도시 상태가 변경되었습니다.");
      }
      cities.delete(id, actorId);
      return null;
    });
  }

  @Transactional
  public Created create(JsonNode input, long actorId) {
    var fields = Set.of("countryId", "nameKo", "nameEn", "coordinates", "tagline");
    if (!input.isObject() || input.size() != fields.size()
        || fields.stream().anyMatch(field -> !input.has(field))
        || !input.get("countryId").isString()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 입력을 확인해 주세요.");
    }
    long countryId;
    try {
      String value = input.get("countryId").asString();
      if (!value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      countryId = Long.parseLong(value);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "국가 ID를 확인해 주세요.");
    }
    String nameKo = optionalText(input.get("nameKo"), 150);
    String nameEn = optionalText(input.get("nameEn"), 150);
    String tagline = optionalText(input.get("tagline"), 200);
    BigDecimal latitude = null;
    BigDecimal longitude = null;
    var coordinates = input.get("coordinates");
    if (!coordinates.isNull()) {
      if (!coordinates.isObject() || coordinates.size() != 2 || !coordinates.has("latitude")
          || !coordinates.has("longitude") || !coordinates.get("latitude").isNumber()
          || !coordinates.get("longitude").isNumber()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "좌표를 확인해 주세요.");
      }
      latitude = coordinates.get("latitude").decimalValue();
      longitude = coordinates.get("longitude").decimalValue();
      if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0
          || longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "좌표 범위를 확인해 주세요.");
      }
    }
    long[] created = cities.create(countryId, actorId, nameKo, nameEn, tagline, latitude, longitude);
    long cityId = created[0];
    long revisionId = created[1];
    City city = find(Long.toString(cityId)).value();
    return new Created(city, Long.toString(revisionId));
  }

  private static String optionalText(JsonNode value, int maxLength) {
    if (value.isNull()) return null;
    if (!value.isString() || value.asString().isBlank()
        || value.asString().codePointCount(0, value.asString().length()) > maxLength) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "입력 값을 확인해 주세요.");
    }
    return value.asString();
  }

  public Page list(String cursor,
            int limit,
            String q,
            String visibility) {
    if (limit < 1 || limit > 100 || (q != null && q.codePointCount(0, q.length()) > 100)
        || (visibility != null && !List.of("HIDDEN", "PREPARING", "PUBLIC").contains(visibility))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "조회 조건을 확인해 주세요.");
    }
    String query = q == null ? "" : q.strip();
    String state = visibility == null ? "" : visibility;
    Long before = null;
    if (cursor != null) {
      try {
        Cursor decoded = json.readValue(Base64.getUrlDecoder().decode(cursor), Cursor.class);
        if (!decoded.query().equals(query) || !decoded.visibility().equals(state) || decoded.before() <= 0) {
          throw new IllegalArgumentException();
        }
        before = decoded.before();
      } catch (RuntimeException invalid) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "커서를 확인해 주세요.");
      }
    }
    var results = cities.list(before, state, query, limit);
    boolean hasNext = results.size() > limit;
    List<City> items = hasNext ? results.subList(0, limit) : results;
    String next = hasNext ? Base64.getUrlEncoder().withoutPadding().encodeToString(
        json.writeValueAsBytes(new Cursor(Long.parseLong(items.getLast().id()), query, state))) : null;
    return new Page(items, next);
  }

  public Versioned<City> find(String cityId) {
    long id;
    try {
      if (!cityId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      id = Long.parseLong(cityId);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 ID를 확인해 주세요.");
    }
    City city = cities.find(id);
    try {
      String validator = city.toString();
      String etag = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(validator.getBytes(StandardCharsets.UTF_8)));
      return new Versioned<>(city, "\"" + etag + "\"");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public record Page(List<City> items, String nextCursor) {}
  public record Cursor(long before, String query, String visibility) {}
  public record Created(City city, String draftRevisionId) {}
}
