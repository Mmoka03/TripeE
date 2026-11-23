package com.tripee.backend.repository.admin.jpa;

import com.tripee.backend.repository.admin.SourceRepository;
import com.tripee.backend.entity.admin.SourceDocumentEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
public class JpaSourceRepository implements SourceRepository {
  private final EntityManager entities;
  public JpaSourceRepository(EntityManager entities) { this.entities = entities; }

  @Override @Transactional
  public Source create(String url, String title, String publisher) {
    var source = new SourceDocumentEntity(url, title, publisher);
    entities.persist(source);
    entities.flush();
    entities.refresh(source);
    return map(source);
  }

  @Override @Transactional(readOnly = true)
  public Optional<Source> find(long id) {
    return Optional.ofNullable(entities.find(SourceDocumentEntity.class, id)).map(JpaSourceRepository::map);
  }

  @Override @Transactional(readOnly = true)
  public List<Source> list(Long before, String query, int limit) {
    String condition = before == null ? "" : " and s.id < :before";
    var q = entities.createQuery("""
        select s from SourceDocumentEntity s
        where (:query = '' or locate(lower(:query), lower(s.title)) > 0
          or locate(lower(:query), lower(s.url)) > 0
          or locate(lower(:query), lower(coalesce(s.publisher, ''))) > 0)
        """ + condition + " order by s.id desc", SourceDocumentEntity.class)
        .setParameter("query", query).setMaxResults(limit);
    if (before != null) q.setParameter("before", before);
    return q.getResultStream().map(JpaSourceRepository::map).toList();
  }

  private static Source map(SourceDocumentEntity e) {
    return new Source(e.id().toString(), e.url(), e.title(), e.publisher(), e.createdAt());
  }
}
