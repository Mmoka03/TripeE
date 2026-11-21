package com.tripee.backend.entity.tag;

import jakarta.persistence.*;

@Entity
@Table(name = "tag")
public class TagEntity {
  @Id private Long id;
  private String code;
  @Column(name = "label_ko") private String labelKo;
  @Column(name = "label_en") private String labelEn;
  @Column(name = "is_active") private boolean active;
  protected TagEntity() {}
  public Long id() { return id; }
  public String code() { return code; }
  public String labelKo() { return labelKo; }
  public String labelEn() { return labelEn; }
  public boolean active() { return active; }
}
