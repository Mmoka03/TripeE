package com.tripee.backend.controller.admin;

import com.tripee.backend.service.admin.AdminSession;
import com.tripee.backend.service.admin.PhotoFileService;
import com.tripee.backend.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
public class PhotoFileController {
  private final PhotoFileService photos;
  private final Clock clock;

  public PhotoFileController(PhotoFileService photos, Clock clock) {
    this.photos = photos;
    this.clock = clock;
  }

  @GetMapping("/api/v1/media/photo-assets/{assetId}")
  public ResponseEntity<byte[]> image(@PathVariable String assetId, HttpServletRequest request) {
    long id;
    try {
      if (!assetId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      id = Long.parseLong(assetId);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "사진 ID를 확인해 주세요.");
    }
    var session = request.getSession(false);
    boolean admin = false;
    if (session != null && session.getAttribute(AdminSession.ATTRIBUTE) instanceof AdminSession state
        && !state.expired(clock.instant())
        && session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) instanceof SecurityContext context
        && context.getAuthentication() != null && context.getAuthentication().isAuthenticated()) {
      admin = context.getAuthentication().getName().equals(state.view(clock.instant(), false).admin().id());
    }
    var image = photos.read(id, admin);
    return ResponseEntity.ok().contentType(image.mediaType()).body(image.bytes());
  }
}
