package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.PhotoRepository;
import com.tripee.backend.repository.admin.PhotoRepository.PhotoAsset;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import com.tripee.backend.common.exception.ApiException;
import tools.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Set;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

@Service
@Profile("postgres")
public class PhotoService {
  private final PhotoRepository photos;
  private final EditLockService locks;
  private final Path storage;

  public PhotoService(PhotoRepository photos, EditLockService locks,
                      @Value("${tripee.photos.directory:./data/photos}") String storage) {
    this.photos = photos;
    this.locks = locks;
    this.storage = Path.of(storage).toAbsolutePath().normalize();
  }

  public PhotoAsset upload(long cityId, long actorId, String token, HttpSession session,
                           MultipartFile file, JsonNode metadata) {
    validateMetadata(metadata);
    locks.withLease(cityId, actorId, token, session, () -> null);
    if (file.isEmpty() || file.getSize() > 10L * 1024 * 1024) {
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "사진 크기를 확인해 주세요.");
    }
    byte[] normalized;
    try {
      byte[] data = file.getBytes();
      try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw unsupported();
        var reader = readers.next();
        try {
          reader.setInput(input, true, true);
          String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
          if (!Set.of("jpeg", "jpg", "png", "webp").contains(format)) throw unsupported();
          String declared = file.getContentType();
          String expected = format.equals("jpg") || format.equals("jpeg") ? "image/jpeg" : "image/" + format;
          if (!expected.equalsIgnoreCase(declared)) throw unsupported();
          int width = reader.getWidth(0);
          int height = reader.getHeight(0);
          if (width <= 0 || height <= 0 || (long) width * height > 40_000_000L) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "사진 해상도를 확인해 주세요.");
          }
          var image = reader.read(0);
          if (image == null) throw unsupported();
          var output = new ByteArrayOutputStream();
          if (!ImageIO.write(image, "png", output)) throw unsupported();
          normalized = output.toByteArray();
          if (normalized.length > 10L * 1024 * 1024) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "저장할 사진 크기가 너무 큽니다.");
          }
        } finally {
          reader.dispose();
        }
      } catch (javax.imageio.IIOException invalidImage) {
        throw unsupported();
      }
      Files.createDirectories(storage);
      String key = UUID.randomUUID() + ".png";
      Path path = storage.resolve(key);
      Files.write(path, normalized, java.nio.file.StandardOpenOption.CREATE_NEW);
      try {
        return locks.withLease(cityId, actorId, token, session, () -> photos.insert(cityId, key,
            metadata.get("sourceUrl").asString(), nullable(metadata.get("creatorName")),
            nullable(metadata.get("licenseName")), nullable(metadata.get("licenseUrl")),
            nullable(metadata.get("usageNote")), metadata.get("rightsConfirmed").asBoolean()));
      } catch (RuntimeException failed) {
        try {
          Files.deleteIfExists(path);
        } catch (java.io.IOException cleanupFailure) {
          failed.addSuppressed(cleanupFailure);
        }
        throw failed;
      }
    } catch (java.io.IOException failure) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "사진을 저장할 수 없습니다.");
    }
  }

  private static void validateMetadata(JsonNode data) {
    var keys = Set.of("sourceUrl", "creatorName", "licenseName", "licenseUrl", "usageNote", "rightsConfirmed");
    if (!data.isObject() || data.size() != keys.size() || keys.stream().anyMatch(key -> !data.has(key))
        || !data.get("sourceUrl").isString() || !data.get("rightsConfirmed").isBoolean()) throw invalid();
    validateUrl(data.get("sourceUrl").asString());
    for (var key : List.of("creatorName", "licenseName", "licenseUrl", "usageNote")) {
      JsonNode value = data.get(key);
      int max = key.equals("licenseUrl") ? 4096 : key.equals("usageNote") ? 2000 : 200;
      if (!value.isNull() && (!value.isString() || value.asString().isBlank()
          || value.asString().codePointCount(0, value.asString().length()) > max)) throw invalid();
    }
    if (!data.get("licenseUrl").isNull()) validateUrl(data.get("licenseUrl").asString());
    if (data.get("rightsConfirmed").asBoolean() && data.get("licenseName").isNull()
        && data.get("usageNote").isNull()) throw invalid();
  }

  private static void validateUrl(String url) {
    try {
      var uri = java.net.URI.create(url);
      if (url.length() > 4096 || uri.getHost() == null || !Set.of("http", "https").contains(uri.getScheme())) {
        throw invalid();
      }
    } catch (IllegalArgumentException malformed) {
      throw invalid();
    }
  }

  private static String nullable(JsonNode value) {
    return value.isNull() ? null : value.asString();
  }

  private static ApiException invalid() {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "사진 정보를 확인해 주세요.");
  }

  private static ApiException unsupported() {
    return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 사진 형식입니다.");
  }

  public Items list(long cityId, long actorId, String token, HttpSession session) {
    return locks.withLease(cityId, actorId, token, session, () -> new Items(photos.list(cityId)));
  }

  public record Items(List<PhotoAsset> items) {}
}
