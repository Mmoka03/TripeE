package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.PhotoRepository;
import com.tripee.backend.common.exception.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
@Profile("postgres")
public class PhotoFileService {
  private final PhotoRepository photos;
  private final Path storage;

  public PhotoFileService(PhotoRepository photos, @Value("${tripee.photos.directory:./data/photos}") String directory) {
    this.photos = photos;
    this.storage = Path.of(directory).toAbsolutePath().normalize();
  }

  public Image read(long id, boolean administrator) {
    String key = photos.storageKey(id, administrator).orElseThrow(PhotoFileService::notFound);
    try {
      Path root = storage.toRealPath();
      Path file = storage.resolve(key).normalize();
      if (!file.startsWith(storage)) throw notFound();
      file = file.toRealPath();
      if (!file.startsWith(root) || !Files.isRegularFile(file)) throw notFound();
      long length = Files.size(file);
      if (length == 0 || length > 10L * 1024 * 1024) throw notFound();
      byte[] bytes = Files.readAllBytes(file);
      MediaType media;
      if (bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff) {
        media = MediaType.IMAGE_JPEG;
      } else if (bytes.length >= 8 && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, 8),
          new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
        media = MediaType.IMAGE_PNG;
      } else if (bytes.length >= 12 && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
          && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) {
        media = MediaType.parseMediaType("image/webp");
      } else {
        throw notFound();
      }
      return new Image(bytes, media);
    } catch (IOException | SecurityException missing) {
      throw notFound();
    }
  }

  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "사진을 찾을 수 없어요.");
  }

  public record Image(byte[] bytes, MediaType mediaType) {}
}
