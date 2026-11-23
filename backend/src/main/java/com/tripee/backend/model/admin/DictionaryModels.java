package com.tripee.backend.model.admin;

public final class DictionaryModels {
  private DictionaryModels() {}
  public record Country(String id, String code, String nameKo, String nameEn) {}
  public record Tag(String id, String code, String labelKo, String labelEn, boolean isActive) {}
}
