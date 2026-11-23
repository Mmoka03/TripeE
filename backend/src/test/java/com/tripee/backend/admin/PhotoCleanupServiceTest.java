package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.PhotoCleanupRepository;
import com.tripee.backend.service.admin.PhotoCleanupService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("service")
class PhotoCleanupServiceTest {
  @TempDir Path root;

  @Test
  void deletesQueuedFileAndKeepsInvalidPathForFurtherInspection() throws Exception {
    var jobs = mock(PhotoCleanupRepository.class);
    var photo = Files.writeString(root.resolve("photo.png"), "test");
    when(jobs.pending()).thenReturn(List.of("photo.png", "../outside.png"));

    new PhotoCleanupService(jobs, root.toString()).cleanup();

    assertThat(photo).doesNotExist();
    verify(jobs).completed("photo.png");
    verify(jobs, never()).completed("../outside.png");
  }

  @Test
  void retriesAfterFileDeletionFails() throws Exception {
    var jobs = mock(PhotoCleanupRepository.class);
    var directory = Files.createDirectory(root.resolve("pending"));
    var child = Files.writeString(directory.resolve("child"), "blocks deletion");
    when(jobs.pending()).thenReturn(List.of("pending"));
    var service = new PhotoCleanupService(jobs, root.toString());
    service.cleanup();
    assertThat(directory).exists();
    verify(jobs, never()).completed("pending");
    Files.delete(child);
    service.cleanup();
    assertThat(directory).doesNotExist();
    verify(jobs).completed("pending");
  }
}
