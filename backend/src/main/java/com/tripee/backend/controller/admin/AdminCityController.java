package com.tripee.backend.controller.admin;

import com.tripee.backend.controller.admin.*;
import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("postgres")
public class AdminCityController {
  private final AdminCityService cities;

  public AdminCityController(AdminCityService cities) {
    this.cities = cities;
  }

  @PostMapping("/api/v1/admin/cities")
  public ResponseEntity<AdminCityService.Created> create(@RequestBody JsonNode input, Authentication authentication) {
    var created = cities.create(input, Long.parseLong(authentication.getName()));
    return ResponseEntity.created(java.net.URI.create("/api/v1/admin/cities/" + created.city().id())).body(created);
  }

  @GetMapping("/api/v1/admin/cities")
  AdminCityService.Page list(@RequestParam(required = false) String cursor,
                             @RequestParam(required = false, defaultValue = "20") int limit,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) String visibility) {
    return cities.list(cursor, limit, q, visibility);
  }

  @GetMapping("/api/v1/admin/cities/{cityId}")
  public ResponseEntity<com.tripee.backend.model.admin.CityModels.City> find(@PathVariable String cityId) {
    return response(cities.find(cityId));
  }

  @PatchMapping("/api/v1/admin/cities/{cityId}")
  public ResponseEntity<com.tripee.backend.model.admin.CityModels.City> changeVisibility(@PathVariable String cityId,
      @RequestBody Visibility input, @RequestHeader("X-Edit-Lock-Token") String token,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      Authentication authentication, HttpServletRequest request) {
    return response(cities.changeVisibility(cityId, input.visibility(), token, ifMatch,
        Long.parseLong(authentication.getName()), request.getSession(false)));
  }

  private static ResponseEntity<com.tripee.backend.model.admin.CityModels.City> response(
      com.tripee.backend.model.admin.Versioned<com.tripee.backend.model.admin.CityModels.City> value) {
    return ResponseEntity.ok().eTag(value.version()).body(value.value());
  }

  public record Visibility(String visibility) {}

  @DeleteMapping("/api/v1/admin/cities/{cityId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String cityId, @RequestHeader("X-Edit-Lock-Token") String token,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      Authentication authentication, HttpServletRequest request) {
    cities.delete(cityId, token, ifMatch, Long.parseLong(authentication.getName()), request.getSession(false));
  }
}
