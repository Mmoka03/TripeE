package com.tripee.backend.service.city;

import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import java.math.BigDecimal;

public record CitySummary(String id, Country country, String nameKo, String nameEn,
                   Coordinates coordinates, CityVisibility visibility) {
  public record Country(String id, String code, String nameKo, String nameEn) {}
  public record Coordinates(BigDecimal latitude, BigDecimal longitude) {}
}
