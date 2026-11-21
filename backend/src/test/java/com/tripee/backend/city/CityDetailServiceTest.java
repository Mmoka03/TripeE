package com.tripee.backend.city;

import com.tripee.backend.common.exception.ApiException;
import com.tripee.backend.repository.city.CityDetailRepository;
import com.tripee.backend.service.city.CityDetailService;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("service")
class CityDetailServiceTest {
  private final CityDetailRepository repository = mock(CityDetailRepository.class);
  private final CityDetailService service = new CityDetailService(repository);
  private final JsonMapper json = new JsonMapper();

  @Test
  void allowsPreparingCitiesAndPassesThroughMissingCities() {
    var preparing = json.readTree("{\"visibility\":\"PREPARING\",\"message\":\"준비 중\"}");
    when(repository.find(1)).thenReturn(Optional.of(preparing));
    when(repository.find(2)).thenReturn(Optional.empty());

    assertThat(service.find(1)).contains(preparing);
    assertThat(service.find(2)).isEmpty();
  }

  @Test
  void rejectsPublishedCityWithoutRequiredSections() {
    var incomplete = json.readTree("""
        {"visibility":"PUBLIC","photos":[],"experiences":[],"visitPeriods":[],
         "arrival":[],"localTransport":[],"considerations":[]}
        """);
    when(repository.find(1)).thenReturn(Optional.of(incomplete));

    assertThatThrownBy(() -> service.find(1)).isInstanceOf(ApiException.class);
  }
}
