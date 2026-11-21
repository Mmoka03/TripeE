package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.PhotoRepository;
import com.tripee.backend.service.admin.EditLockService;
import com.tripee.backend.service.admin.PhotoService;
import com.tripee.backend.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("service")
class PhotoServiceTest {
  @TempDir Path storage;
  private final PhotoRepository repository = mock(PhotoRepository.class);
  private final EditLockService locks = mock(EditLockService.class);

  @Test
  void rejectsUnverifiedRightsMetadataBeforeOpeningOrSavingFile() {
    var service = new PhotoService(repository, locks, storage.toString());
    var metadata = new JsonMapper().readTree("""
        {"sourceUrl":"https://example.org/a","creatorName":null,"licenseName":null,
         "licenseUrl":null,"usageNote":null,"rightsConfirmed":true}
        """);
    var file = new MockMultipartFile("file", "image.png", "image/png", "fake".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), file, metadata))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(locks, repository);
  }

  @Test
  void enforcesFileSizeBoundaryBeforeReadingOversizedData() throws Exception {
    var service = new PhotoService(repository, locks, storage.toString());
    var metadata = new JsonMapper().readTree("""
        {"sourceUrl":"https://example.org/a","creatorName":null,"licenseName":"CC0",
         "licenseUrl":null,"usageNote":null,"rightsConfirmed":true}
        """);
    var session = new MockHttpSession();
    MultipartFile tooLarge = mock(MultipartFile.class);
    when(tooLarge.getSize()).thenReturn(10L * 1024 * 1024 + 1);
    assertThatThrownBy(() -> service.upload(1, 1, "token", session, tooLarge, metadata))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    verify(tooLarge, never()).getBytes();

    var atLimit = new MockMultipartFile("file", "invalid.png", "image/png", new byte[10 * 1024 * 1024]);
    assertThatThrownBy(() -> service.upload(1, 1, "token", session, atLimit, metadata))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @ValueSource(strings = {"file:///secret", "https://", "https://example.org/"})
  void rejectsInvalidUrlOrMissingRightsEvidenceBeforeOpeningFile(String url) {
    var metadata = new JsonMapper().createObjectNode().put("sourceUrl", url).putNull("creatorName")
        .putNull("licenseName").putNull("licenseUrl").putNull("usageNote").put("rightsConfirmed", true);
    var file = mock(MultipartFile.class);
    var service = new PhotoService(repository, locks, storage.toString());
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), file, metadata))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    verifyNoInteractions(file, locks, repository);
  }

  @Test
  void rejectsMissingFieldsBlankLicenseAndUnsupportedLicenseUrl() {
    var json = new JsonMapper();
    var metadata = json.createObjectNode().put("sourceUrl", "https://example.org/a").putNull("creatorName")
        .put("licenseName", "CC0").putNull("licenseUrl").putNull("usageNote").put("rightsConfirmed", true);
    var service = new PhotoService(repository, locks, storage.toString());
    var file = mock(MultipartFile.class);
    metadata.put("licenseName", " ");
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), file, metadata)).isInstanceOf(ApiException.class);
    metadata.put("licenseName", "CC0").put("licenseUrl", "file:///secret");
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), file, metadata)).isInstanceOf(ApiException.class);
    metadata.remove("rightsConfirmed");
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), file, metadata)).isInstanceOf(ApiException.class);
    metadata.put("rightsConfirmed", "true").putNull("licenseUrl");
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), file, metadata)).isInstanceOf(ApiException.class);
    metadata.put("rightsConfirmed", true).put("sourceUrl", "https://example.org/a").put("creatorName", "x".repeat(201));
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), file, metadata)).isInstanceOf(ApiException.class);
    verifyNoInteractions(file, locks, repository);
  }

  @Test
  void removesWrittenFileWhenDatabaseRegistrationFails() throws Exception {
    var service = new PhotoService(repository, locks, storage.toString());
    var metadata = new JsonMapper().readTree("""
        {"sourceUrl":"https://example.org/a","creatorName":null,"licenseName":"CC0",
         "licenseUrl":null,"usageNote":null,"rightsConfirmed":true}
        """);
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
    var file = new MockMultipartFile("file", "image.png", "image/png", bytes.toByteArray());
    var failure = new IllegalStateException("DB registration failed");
    when(repository.insert(anyLong(), anyString(), anyString(), any(), any(), any(), any(), anyBoolean()))
        .thenThrow(failure);
    when(locks.withLease(anyLong(), anyLong(), anyString(), any(), any()))
        .thenReturn(null).thenAnswer(call -> ((java.util.function.Supplier<?>) call.getArgument(4)).get());
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), file, metadata)).isSameAs(failure);
    try (var files = java.nio.file.Files.list(storage)) {
      assertThat(files).isEmpty();
    }
    verify(repository).insert(eq(1L), anyString(), anyString(), any(), any(), any(), any(), eq(true));
  }

  @Test
  void rejectsEmptyFilesMismatchedMimeAndUnavailableStorage() throws Exception {
    var metadata = new JsonMapper().readTree("""
        {"sourceUrl":"https://example.org/a","creatorName":null,"licenseName":"CC0",
         "licenseUrl":null,"usageNote":null,"rightsConfirmed":true}
        """);
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
    var session = new MockHttpSession();
    var service = new PhotoService(repository, locks, storage.toString());
    var empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
    assertThatThrownBy(() -> service.upload(1, 1, "token", session, empty, metadata))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    var wrongType = new MockMultipartFile("file", "image.png", "image/jpeg", bytes.toByteArray());
    assertThatThrownBy(() -> service.upload(1, 1, "token", session, wrongType, metadata))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    var invalidRoot = java.nio.file.Files.writeString(storage.resolve("not-a-directory"), "file");
    var blocked = new PhotoService(repository, locks, invalidRoot.resolve("photos").toString());
    var valid = new MockMultipartFile("file", "image.png", "image/png", bytes.toByteArray());
    assertThatThrownBy(() -> blocked.upload(1, 1, "token", session, valid, metadata))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    verifyNoInteractions(repository);
  }

  @Test
  void rejectsNullMetadataAndValidatesEachOptionalFieldType() {
    var service = new PhotoService(repository, locks, storage.toString());
    var json = new JsonMapper();
    var file = mock(MultipartFile.class);
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), file, json.readTree("null")))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    for (String key : new String[]{"creatorName", "licenseName", "licenseUrl", "usageNote"}) {
      var metadata = json.createObjectNode().put("sourceUrl", "https://example.org/a").putNull("creatorName")
          .put("licenseName", "CC0").putNull("licenseUrl").putNull("usageNote").put("rightsConfirmed", true);
      metadata.put(key, 42);
      assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), file, metadata))
          .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    }
    verifyNoInteractions(file, locks, repository);
  }

  @Test
  void rejectsImageDimensionsAbovePixelLimitBeforeDecoding() throws Exception {
    var metadata = new JsonMapper().readTree("""
        {"sourceUrl":"https://example.org/a","creatorName":null,"licenseName":"CC0",
         "licenseUrl":null,"usageNote":null,"rightsConfirmed":true}
        """);
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
    byte[] png = bytes.toByteArray();
    java.nio.ByteBuffer.wrap(png).putInt(16, 8001).putInt(20, 5000);
    var crc = new java.util.zip.CRC32();
    crc.update(png, 12, 17);
    java.nio.ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
    var file = new MockMultipartFile("file", "oversized.png", "image/png", png);
    assertThatThrownBy(() -> new PhotoService(repository, locks, storage.toString())
        .upload(1, 1, "token", new MockHttpSession(), file, metadata))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    verifyNoInteractions(repository);
  }

  @Test
  void rejectsUnsupportedGifAndReportsReadFailureWithoutWriting() throws Exception {
    var metadata = new JsonMapper().readTree("""
        {"sourceUrl":"https://example.org/a","creatorName":null,"licenseName":"CC0",
         "licenseUrl":null,"usageNote":null,"rightsConfirmed":true}
        """);
    var output = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "gif", output);
    var gif = new MockMultipartFile("file", "a.gif", "image/gif", output.toByteArray());
    var service = new PhotoService(repository, locks, storage.toString());
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), gif, metadata))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE"));
    var broken = mock(MultipartFile.class);
    when(broken.getSize()).thenReturn(1L);
    when(broken.getBytes()).thenThrow(new java.io.IOException("read error"));
    assertThatThrownBy(() -> service.upload(1, 1, "token", new MockHttpSession(), broken, metadata))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INTERNAL_ERROR"));
    verifyNoInteractions(repository);
    try (var files = java.nio.file.Files.list(storage)) { assertThat(files).isEmpty(); }
  }
}
