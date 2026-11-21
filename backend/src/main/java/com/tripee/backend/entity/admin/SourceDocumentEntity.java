package com.tripee.backend.entity.admin;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "source_document")
public class SourceDocumentEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String url;
  private String title;
  private String publisher;
  @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
  protected SourceDocumentEntity() {}
  public SourceDocumentEntity(String url, String title, String publisher) {
    this.url = url;
    this.title = title;
    this.publisher = publisher;
  }
  public Long id() { return id; }
  public String url() { return url; }
  public String title() { return title; }
  public String publisher() { return publisher; }
  public Instant createdAt() { return createdAt; }
}
