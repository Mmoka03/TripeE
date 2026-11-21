package com.tripee.backend.entity.admin;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "photo_asset")
public class PhotoAssetEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "city_id") private Long cityId;
  @Column(name = "storage_key") private String storageKey;
  @Column(name = "source_url") private String sourceUrl;
  @Column(name = "creator_name") private String creatorName;
  @Column(name = "license_name") private String licenseName;
  @Column(name = "license_url") private String licenseUrl;
  @Column(name = "usage_note") private String usageNote;
  @Column(name = "rights_checked_at") private Instant rightsCheckedAt;

  protected PhotoAssetEntity() {}
  public PhotoAssetEntity(long cityId, String storageKey, String sourceUrl, String creatorName,
                          String licenseName, String licenseUrl, String usageNote, Instant rightsCheckedAt) {
    this.cityId = cityId;
    this.storageKey = storageKey;
    this.sourceUrl = sourceUrl;
    this.creatorName = creatorName;
    this.licenseName = licenseName;
    this.licenseUrl = licenseUrl;
    this.usageNote = usageNote;
    this.rightsCheckedAt = rightsCheckedAt;
  }
  public Long id() { return id; }
  public Long cityId() { return cityId; }
  public String sourceUrl() { return sourceUrl; }
  public String creatorName() { return creatorName; }
  public String licenseName() { return licenseName; }
  public String licenseUrl() { return licenseUrl; }
  public String usageNote() { return usageNote; }
  public Instant rightsCheckedAt() { return rightsCheckedAt; }
}
