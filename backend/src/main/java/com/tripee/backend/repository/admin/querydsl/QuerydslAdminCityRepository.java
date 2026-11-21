package com.tripee.backend.repository.admin.querydsl;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tripee.backend.common.exception.ApiException;
import com.tripee.backend.entity.city.QCityEntity;
import com.tripee.backend.entity.city.QCityRevisionEntity;
import com.tripee.backend.entity.city.QCountryEntity;
import com.tripee.backend.repository.admin.AdminCityRepository;
import com.tripee.backend.repository.admin.jdbc.JdbcAdminCityCommands;
import com.tripee.backend.model.admin.CityModels.City;
import com.tripee.backend.model.admin.CityModels.Country;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
@Transactional(readOnly = true)
public class QuerydslAdminCityRepository implements AdminCityRepository {
  private static final QCityEntity C = QCityEntity.cityEntity;
  private static final QCountryEntity K = QCountryEntity.countryEntity;
  private static final QCityRevisionEntity P = new QCityRevisionEntity("published");
  private static final QCityRevisionEntity D = new QCityRevisionEntity("draft");
  private final JPAQueryFactory queries;
  private final JdbcAdminCityCommands commands;

  public QuerydslAdminCityRepository(JPAQueryFactory queries, JdbcAdminCityCommands commands) {
    this.queries = queries;
    this.commands = commands;
  }

  @Override @Transactional
  public long[] create(long countryId, long actorId, String nameKo, String nameEn, String tagline,
                       BigDecimal latitude, BigDecimal longitude) {
    return commands.create(countryId, actorId, nameKo, nameEn, tagline, latitude, longitude);
  }

  @Override
  public List<City> list(Long before, String state, String query, int limit) {
    return selectCity().where(before == null ? null : C.id.lt(before),
            state.isEmpty() ? null : C.visibility.eq(state), search(query))
        .orderBy(C.id.desc()).limit((long) limit + 1).fetch().stream().map(this::map).toList();
  }

  private Predicate search(String query) {
    if (query.isEmpty()) return null;
    // DB의 lower와 부분 문자열 검색을 사용하여 %, _, 역슬래시도 일반 문자로 취급함.
    var needle = Expressions.asString(query).lower();
    return P.nameKo.coalesce(D.nameKo).coalesce("").lower().indexOf(needle).goe(0)
        .or(P.nameEn.coalesce(D.nameEn).coalesce("").lower().indexOf(needle).goe(0))
        .or(K.nameKo.lower().indexOf(needle).goe(0))
        .or(K.nameEn.lower().indexOf(needle).goe(0));
  }

  @Override
  public City find(long id) {
    var row = selectCity().where(C.id.eq(id)).fetchOne();
    if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "CITY_NOT_FOUND", "도시를 찾을 수 없어요.");
    return map(row);
  }

  private JPAQuery<Tuple> selectCity() {
    // 엔티티 대신 값을 투영하므로 같은 트랜잭션에서 JDBC로 갱신한 값도 다시 조회됨.
    return queries.select(C.id, C.visibility, C.publishedRevisionId, C.draftRevisionId,
            C.publishedSafetyRevisionId, K.id, K.code, K.nameKo, K.nameEn,
            P.nameKo.coalesce(D.nameKo), P.nameEn.coalesce(D.nameEn), D.editVersion)
        .from(C).join(K).on(K.id.eq(C.countryId))
        .leftJoin(P).on(P.id.eq(C.publishedRevisionId))
        .leftJoin(D).on(D.id.eq(C.draftRevisionId));
  }

  private City map(Tuple row) {
    return new City(text(row.get(C.id)), new Country(text(row.get(K.id)), row.get(K.code), row.get(K.nameKo), row.get(K.nameEn)),
        row.get(C.visibility), row.get(P.nameKo.coalesce(D.nameKo)), row.get(P.nameEn.coalesce(D.nameEn)),
        text(row.get(C.publishedRevisionId)), text(row.get(C.draftRevisionId)), text(row.get(D.editVersion)),
        text(row.get(C.publishedSafetyRevisionId)));
  }

  private static String text(Long value) { return value == null ? null : value.toString(); }

  @Override
  public boolean hasDisplayableRevision(long cityId) {
    var r = QCityRevisionEntity.cityRevisionEntity;
    return queries.selectOne().from(C).join(r).on(r.id.eq(C.publishedRevisionId.coalesce(C.draftRevisionId)))
        .where(C.id.eq(cityId), r.nameKo.isNotNull(), r.nameEn.isNotNull(), r.latitude.isNotNull(), r.longitude.isNotNull())
        .fetchFirst() != null;
  }

  @Override @Transactional
  public void changeVisibility(long cityId, long actorId, String visibility, City before) {
    commands.setVisibility(cityId, visibility);
    commands.recordVisibility(cityId, actorId, before, find(cityId));
  }

  @Override @Transactional
  public void delete(long cityId, long actorId) { commands.delete(cityId, actorId); }
}
