package com.tripee.backend.repository.admin;

import com.tripee.backend.model.admin.CityModels.City;
import java.math.BigDecimal;
import java.util.List;

public interface AdminCityRepository {
  long[] create(long countryId, long actorId, String nameKo, String nameEn, String tagline,
                BigDecimal latitude, BigDecimal longitude);
  List<City> list(Long before, String state, String query, int limit);
  City find(long id);
  boolean hasDisplayableRevision(long cityId);
  void changeVisibility(long cityId, long actorId, String visibility, City before);
  void delete(long cityId, long actorId);
}
