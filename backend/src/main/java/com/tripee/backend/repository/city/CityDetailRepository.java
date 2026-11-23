package com.tripee.backend.repository.city;

import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import com.tripee.backend.common.exception.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
@Profile("postgres")
public class CityDetailRepository {
  private final JdbcClient jdbc;
  private final ObjectMapper mapper;
  private final String query;

  public CityDetailRepository(JdbcClient jdbc, ObjectMapper mapper,
                       @Value("classpath:sql/city-detail.sql") Resource sql) throws IOException {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.query = sql.getContentAsString(StandardCharsets.UTF_8);
  }

  public Optional<JsonNode> find(long cityId) {
    return jdbc.sql(query).param("cityId", cityId)
        .query((row, n) -> mapper.readTree(row.getString("detail"))).optional();
  }


}
