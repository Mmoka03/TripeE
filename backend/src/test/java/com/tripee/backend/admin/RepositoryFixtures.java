package com.tripee.backend.admin;

import org.springframework.jdbc.core.simple.JdbcClient;

final class RepositoryFixtures {
  private RepositoryFixtures() {}

  static void publish(JdbcClient jdbc, long city, long revision) {
    long safety = jdbc.sql("""
        insert into safety_revision(city_id, created_by, published_at, published_by)
        values (:city, 1, current_timestamp, 1) returning id
        """).param("city", city).query(Long.class).single();
    jdbc.sql("""
        update city_revision set published_at=current_timestamp, published_by=1,
          safety_revision_at_publish_id=:safety where id=:revision
        """).param("safety", safety).param("revision", revision).update();
    jdbc.sql("""
        update city set visibility='PUBLIC', draft_revision_id=null, published_revision_id=:revision,
          published_safety_revision_id=:safety where id=:city
        """).param("revision", revision).param("safety", safety).param("city", city).update();
  }
}
