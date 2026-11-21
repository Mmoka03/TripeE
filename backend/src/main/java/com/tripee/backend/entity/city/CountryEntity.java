package com.tripee.backend.entity.city;

import jakarta.persistence.*;

@Entity
@Table(name = "country")
public class CountryEntity {
  @Id private Long id;
  private String code;
  @Column(name = "name_ko") private String nameKo;
  @Column(name = "name_en") private String nameEn;
  protected CountryEntity() {}
  public Long id() { return id; }
  public String code() { return code; }
  public String nameKo() { return nameKo; }
  public String nameEn() { return nameEn; }
}
