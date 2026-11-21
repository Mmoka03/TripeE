package com.tripee.backend.controller.city;

import com.tripee.backend.controller.city.*;
import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
public class CityListController {
  private final CityListService cities;

  public CityListController(CityListService cities) {
    this.cities = cities;
  }

  @GetMapping("/api/v1/cities")
  public CityList list(@RequestParam(name = "countryCode", required = false) @Pattern(regexp = "[A-Z]{2}") String countryCode) {
    return new CityList(cities.list(countryCode));
  }

  public record CityList(List<CitySummary> items) {}
}
