package com.tripee.backend.controller.admin;

import com.tripee.backend.service.admin.PhotoService;
import com.tripee.backend.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("postgres")
public class PhotoController {
  private final PhotoService photos;

  public PhotoController(PhotoService photos) {
    this.photos = photos;
  }

  @GetMapping("/api/v1/admin/cities/{cityId}/photo-assets")
  public PhotoService.Items list(@PathVariable String cityId, @RequestHeader("X-Edit-Lock-Token") String token,
                                 Authentication authentication, HttpServletRequest request) {
    try {
      if (!cityId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      return photos.list(Long.parseLong(cityId), Long.parseLong(authentication.getName()),
          token, request.getSession(false));
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 ID를 확인해 주세요.");
    }
  }

  @PostMapping(value = "/api/v1/admin/cities/{cityId}/photo-assets", consumes = "multipart/form-data")
  public ResponseEntity<com.tripee.backend.repository.admin.PhotoRepository.PhotoAsset> upload(
      @PathVariable String cityId, @RequestHeader("X-Edit-Lock-Token") String token,
      @RequestPart("file") MultipartFile file, @RequestPart("metadata") JsonNode metadata,
      Authentication authentication, HttpServletRequest request) {
    long id;
    try {
      if (!cityId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      id = Long.parseLong(cityId);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 ID를 확인해 주세요.");
    }
    var asset = photos.upload(id, Long.parseLong(authentication.getName()), token, request.getSession(false), file, metadata);
    return ResponseEntity.created(java.net.URI.create("/api/v1/media/photo-assets/" + asset.id())).body(asset);
  }
}
