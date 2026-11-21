package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.PhotoCleanupRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Profile("postgres")
public class PhotoCleanupService {
  private static final Logger log = LoggerFactory.getLogger(PhotoCleanupService.class);
  private final PhotoCleanupRepository jobs;
  private final Path storage;

  public PhotoCleanupService(PhotoCleanupRepository jobs,
      @Value("${tripee.photos.directory:./data/photos}") String directory) {
    this.jobs = jobs;
    this.storage = Path.of(directory).toAbsolutePath().normalize();
  }

  @Scheduled(fixedDelayString = "${tripee.photos.cleanup-delay-ms:60000}", initialDelay = 60000)
  public void cleanup() {
    for (String key : jobs.pending()) {
      try {
        Path candidate = storage.resolve(key).normalize();
        if (!candidate.startsWith(storage) || candidate.equals(storage)) {
          log.warn("Photo cleanup skipped an invalid storage key");
          continue;
        }
        Path parent = candidate.getParent();
        if (Files.exists(parent) && (!parent.toRealPath().startsWith(storage.toRealPath()))) {
          log.warn("Photo cleanup skipped a path outside the storage directory");
          continue;
        }
        Files.deleteIfExists(candidate);
        jobs.completed(key);
      } catch (IOException | SecurityException failure) {
        log.warn("Photo cleanup failed; the job will be retried", failure);
      }
    }
  }
}
