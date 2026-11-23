package com.tripee.backend.service.city;

import com.tripee.backend.repository.city.CityListRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("postgres")
public class CityListService {
  private final CityListRepository cities;

  public CityListService(CityListRepository cities) {
    this.cities = cities;
  }

  public List<CitySummary> list(String countryCode) {
    return cities.list(countryCode);
  }
}
