package com.tripee.backend.repository.city;

import com.tripee.backend.service.city.CitySummary;
import java.util.List;

public interface CityListRepository {
  List<CitySummary> list(String countryCode);
}
