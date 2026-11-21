package com.tripee.backend.service.city;

import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


public enum CityVisibility {
  HIDDEN, PREPARING, PUBLIC;

  public boolean appearsOnPublicMap() {
    return this == PUBLIC || this == PREPARING;
  }
}
