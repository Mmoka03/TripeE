package com.tripee.backend.controller.admin;

import com.tripee.backend.service.admin.EditLockService;
import com.tripee.backend.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("postgres")
public class EditLockController {
  private final EditLockService locks;

  public EditLockController(EditLockService locks) {
    this.locks = locks;
  }

  @GetMapping("/api/v1/admin/cities/{cityId}/edit-lock")
  public EditLockService.View view(@PathVariable String cityId) {
    return locks.view(id(cityId));
  }

  @PostMapping("/api/v1/admin/cities/{cityId}/edit-lock")
  public ResponseEntity<EditLockService.Lease> acquire(@PathVariable String cityId, @RequestBody Acquire input,
                                                       Authentication auth, HttpServletRequest request) {
    long city = id(cityId);
    if (input.editorInstanceId() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "편집 창 ID를 확인해 주세요.");
    }
    var lease = locks.acquire(city, Long.parseLong(auth.getName()), input.editorInstanceId(), request.getSession(false));
    return ResponseEntity.created(URI.create("/api/v1/admin/cities/" + city + "/edit-lock")).body(lease);
  }

  @PostMapping("/api/v1/admin/cities/{cityId}/edit-lock/heartbeats")
  public EditLockService.Heartbeat heartbeat(@PathVariable String cityId,
      @RequestHeader("X-Edit-Lock-Token") String token, Authentication auth, HttpServletRequest request) {
    return locks.heartbeat(id(cityId), Long.parseLong(auth.getName()), token, request.getSession(false));
  }

  @DeleteMapping("/api/v1/admin/cities/{cityId}/edit-lock")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void release(@PathVariable String cityId, @RequestHeader("X-Edit-Lock-Token") String token,
                      Authentication auth, HttpServletRequest request) {
    locks.release(id(cityId), Long.parseLong(auth.getName()), token, request.getSession(false));
  }

  private static long id(String value) {
    try {
      if (!value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      return Long.parseLong(value);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 ID를 확인해 주세요.");
    }
  }

  public record Acquire(UUID editorInstanceId) {}
}
