package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.PhotoRepository;
import com.tripee.backend.service.admin.PhotoFileService;
import com.tripee.backend.common.exception.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("service")
class PhotoFileServiceTest {
  @TempDir Path temporary;
  private final PhotoRepository repository = mock(PhotoRepository.class);

  @Test
  void servesOnlyImagesInsideStorageAndUsesFileSignature() throws Exception {
    Path storage = Files.createDirectory(temporary.resolve("photos"));
    Path outside = Files.write(temporary.resolve("outside.png"), new byte[] {(byte) 137,80,78,71,13,10,26,10});
    Files.write(storage.resolve("image"), Files.readAllBytes(outside));
    Files.writeString(storage.resolve("invalid"), "not an image");
    Files.createSymbolicLink(storage.resolve("link"), outside);
    when(repository.storageKey(1, false)).thenReturn(Optional.of("image"));
    when(repository.storageKey(2, false)).thenReturn(Optional.of("../outside.png"));
    when(repository.storageKey(3, false)).thenReturn(Optional.of("link"));
    when(repository.storageKey(4, false)).thenReturn(Optional.of("invalid"));
    when(repository.storageKey(5, false)).thenReturn(Optional.empty());
    var service = new PhotoFileService(repository, storage.toString());

    assertThat(service.read(1, false).mediaType()).isEqualTo(MediaType.IMAGE_PNG);
    for (long id : new long[] {2, 3, 4, 5}) {
      assertThatThrownBy(() -> service.read(id, false)).isInstanceOfSatisfying(ApiException.class,
          e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }
  }

  @Test
  void recognizesJpegAndWebpAndRejectsEmptyOversizedAndMissingFiles() throws Exception {
    Path storage = Files.createDirectory(temporary.resolve("media"));
    Files.write(storage.resolve("jpeg"), new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    Files.write(storage.resolve("webp"), new byte[] {'R','I','F','F',0,0,0,0,'W','E','B','P'});
    Files.write(storage.resolve("empty"), new byte[0]);
    try (var file = new java.io.RandomAccessFile(storage.resolve("oversized").toFile(), "rw")) {
      file.setLength(10L * 1024 * 1024 + 1);
    }
    when(repository.storageKey(1, true)).thenReturn(Optional.of("jpeg"));
    when(repository.storageKey(2, true)).thenReturn(Optional.of("webp"));
    when(repository.storageKey(3, true)).thenReturn(Optional.of("empty"));
    when(repository.storageKey(4, true)).thenReturn(Optional.of("oversized"));
    when(repository.storageKey(5, true)).thenReturn(Optional.of("missing"));
    var service = new PhotoFileService(repository, storage.toString());
    assertThat(service.read(1, true).mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
    assertThat(service.read(2, true).mediaType()).isEqualTo(MediaType.parseMediaType("image/webp"));
    for (long id : new long[] {3, 4, 5}) {
      assertThatThrownBy(() -> service.read(id, true)).isInstanceOfSatisfying(ApiException.class,
          e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }
  }

  @Test
  void permitsFileAtSizeLimitButRejectsDirectoryAndMissingRoot() throws Exception {
    Path storage = Files.createDirectory(temporary.resolve("limits"));
    Path atLimit = storage.resolve("limit.png");
    try (var file = new java.io.RandomAccessFile(atLimit.toFile(), "rw")) {
      file.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
      file.setLength(10L * 1024 * 1024);
    }
    Files.createDirectory(storage.resolve("directory"));
    when(repository.storageKey(1, false)).thenReturn(Optional.of("limit.png"));
    when(repository.storageKey(2, false)).thenReturn(Optional.of("directory"));
    var service = new PhotoFileService(repository, storage.toString());
    assertThat(service.read(1, false).bytes()).hasSize(10 * 1024 * 1024);
    assertThatThrownBy(() -> service.read(2, false)).isInstanceOfSatisfying(ApiException.class,
        error -> assertThat(error.code()).isEqualTo("RESOURCE_NOT_FOUND"));
    var missingRoot = new PhotoFileService(repository, temporary.resolve("missing-root").toString());
    assertThatThrownBy(() -> missingRoot.read(1, false)).isInstanceOfSatisfying(ApiException.class,
        error -> assertThat(error.code()).isEqualTo("RESOURCE_NOT_FOUND"));
  }
}
