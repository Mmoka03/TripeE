package com.tripee.backend.city;

import org.junit.jupiter.api.Tag;

import com.tripee.backend.controller.city.*;
import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("service")
class CityVisibilityTest {
  @ParameterizedTest
  @CsvSource({"PUBLIC, true", "PREPARING, true", "HIDDEN, false"})
  void determinesWhetherACityAppearsOnThePublicMap(CityVisibility visibility, boolean expected) {
    assertEquals(expected, visibility.appearsOnPublicMap());
  }
}
