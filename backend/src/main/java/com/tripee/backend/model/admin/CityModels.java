package com.tripee.backend.model.admin;

public final class CityModels {
  private CityModels() {}
  public record Country(String id, String code, String nameKo, String nameEn) {}
  public record City(String id, Country country, String visibility, String nameKo, String nameEn,
      String publishedRevisionId, String draftRevisionId, String draftEditVersion,
      String publishedSafetyRevisionId) {}
}
