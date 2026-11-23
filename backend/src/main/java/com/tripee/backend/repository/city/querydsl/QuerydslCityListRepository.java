package com.tripee.backend.repository.city.querydsl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tripee.backend.entity.city.QCityEntity;
import com.tripee.backend.entity.city.QCityRevisionEntity;
import com.tripee.backend.entity.city.QCountryEntity;
import com.tripee.backend.repository.city.CityListRepository;
import com.tripee.backend.service.city.CitySummary;
import com.tripee.backend.service.city.CityVisibility;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
@Transactional(readOnly = true)
public class QuerydslCityListRepository implements CityListRepository {
  private final JPAQueryFactory queries;

  public QuerydslCityListRepository(JPAQueryFactory queries) { this.queries = queries; }

  @Override
  public List<CitySummary> list(String countryCode) {
    var c = QCityEntity.cityEntity;
    var k = QCountryEntity.countryEntity;
    var r = QCityRevisionEntity.cityRevisionEntity;
    // 스칼라 투영으로 JDBC 변경 이후에도 영속성 컨텍스트의 이전 엔티티를 재사용하지 않음.
    return queries.select(c.id, c.visibility, k.id, k.code, k.nameKo, k.nameEn,
            r.nameKo, r.nameEn, r.latitude, r.longitude)
        .from(c).join(k).on(k.id.eq(c.countryId))
        .join(r).on(r.id.eq(c.publishedRevisionId.coalesce(c.draftRevisionId)))
        .where(c.visibility.in("PUBLIC", "PREPARING"), countryCode == null ? null : k.code.eq(countryCode),
            r.nameKo.isNotNull(), r.nameEn.isNotNull(), r.latitude.isNotNull(), r.longitude.isNotNull())
        .orderBy(c.id.asc()).fetch().stream().map(row -> new CitySummary(row.get(c.id).toString(),
            new CitySummary.Country(row.get(k.id).toString(), row.get(k.code), row.get(k.nameKo), row.get(k.nameEn)),
            row.get(r.nameKo), row.get(r.nameEn), new CitySummary.Coordinates(row.get(r.latitude), row.get(r.longitude)),
            CityVisibility.valueOf(row.get(c.visibility)))).toList();
  }
}
