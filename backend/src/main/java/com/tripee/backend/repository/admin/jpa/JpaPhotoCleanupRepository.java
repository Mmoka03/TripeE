package com.tripee.backend.repository.admin.jpa;

import com.tripee.backend.repository.admin.PhotoCleanupRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
@Transactional(readOnly = true)
public class JpaPhotoCleanupRepository implements PhotoCleanupRepository {
  private final PhotoCleanupJobJpaRepository jobs;

  public JpaPhotoCleanupRepository(PhotoCleanupJobJpaRepository jobs) { this.jobs = jobs; }

  @Override
  public List<String> pending() { return jobs.pending(Pageable.ofSize(100)); }

  @Override @Transactional
  public void completed(String key) { jobs.deleteCompleted(key); }
}
