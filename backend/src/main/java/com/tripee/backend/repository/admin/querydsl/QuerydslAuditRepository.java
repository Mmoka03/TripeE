package com.tripee.backend.repository.admin.querydsl;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tripee.backend.entity.admin.QAdminAccountEntity;
import com.tripee.backend.entity.admin.QAuditEventEntity;
import com.tripee.backend.entity.city.QCityEntity;
import com.tripee.backend.entity.city.QCityRevisionEntity;
import com.tripee.backend.repository.admin.AuditRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
@Profile("postgres")
@Transactional(readOnly = true)
public class QuerydslAuditRepository implements AuditRepository {
  private static final QAuditEventEntity E = QAuditEventEntity.auditEventEntity;
  private static final QAdminAccountEntity A = QAdminAccountEntity.adminAccountEntity;
  private final JPAQueryFactory queries;
  private final ObjectMapper json;

  public QuerydslAuditRepository(JPAQueryFactory queries, ObjectMapper json) {
    this.queries = queries;
    this.json = json;
  }

  @Override
  public Optional<JsonNode> find(long id) {
    var row = queries.select(E.id, E.cityRefId, E.revisionRefId, E.safetyRevisionRefId, A.id, A.displayName,
            E.action, E.occurredAt, E.snapshotSchemaVersion, E.beforeData, E.afterData)
        .from(E).join(A).on(A.id.eq(E.actorId)).where(E.id.eq(id)).fetchOne();
    if (row == null) return Optional.empty();
    var node = summary(row);
    node.put("snapshotSchemaVersion", row.get(E.snapshotSchemaVersion));
    node.set("beforeData", snapshot(row.get(E.beforeData)));
    node.set("afterData", snapshot(row.get(E.afterData)));
    return Optional.of(node);
  }

  @Override
  public List<JsonNode> list(Long cityId, Long before, int limit) {
    // 목록에서는 큰 JSON 스냅샷을 읽지 않음.
    return queries.select(E.id, E.cityRefId, E.revisionRefId, E.safetyRevisionRefId, A.id, A.displayName,
            E.action, E.occurredAt)
        .from(E).join(A).on(A.id.eq(E.actorId))
        .where(cityId == null ? null : E.cityRefId.eq(cityId), before == null ? null : E.id.lt(before))
        .orderBy(E.id.desc()).limit(limit).fetch().stream().<JsonNode>map(this::summary).toList();
  }

  private ObjectNode summary(Tuple row) {
    var node = json.createObjectNode();
    node.put("id", text(row.get(E.id)));
    node.put("cityRefId", text(row.get(E.cityRefId)));
    node.put("revisionRefId", text(row.get(E.revisionRefId)));
    node.put("safetyRevisionRefId", text(row.get(E.safetyRevisionRefId)));
    node.set("actor", actor(row));
    node.put("action", row.get(E.action));
    node.put("occurredAt", row.get(E.occurredAt).toString());
    return node;
  }

  private ObjectNode actor(Tuple row) {
    return json.createObjectNode().put("id", text(row.get(A.id))).put("displayName", row.get(A.displayName));
  }

  @Override
  public Optional<JsonNode> historicalPublication(long cityId, long revisionId) {
    var c = QCityEntity.cityEntity;
    var r = QCityRevisionEntity.cityRevisionEntity;
    var latest = new QAuditEventEntity("latestPublication");
    var row = queries.select(c.id, r.id, r.revisionNo, r.publishedAt, A.id, A.displayName, E.afterData)
        .from(c).join(r).on(r.cityId.eq(c.id).and(r.id.eq(revisionId)))
        .join(A).on(A.id.eq(r.publishedBy))
        .join(E).on(E.id.eq(JPAExpressions.select(latest.id.max()).from(latest)
            .where(latest.cityRefId.eq(c.id), latest.revisionRefId.eq(r.id), latest.action.eq("PUBLISH"))))
        .where(c.id.eq(cityId), r.publishedAt.isNotNull()).fetchOne();
    if (row == null) return Optional.empty();
    var node = json.createObjectNode().put("cityId", text(row.get(c.id))).put("revisionId", text(row.get(r.id)))
        .put("revisionNo", row.get(r.revisionNo)).put("publishedAt", row.get(r.publishedAt).toString());
    node.set("publishedBy", actor(row));
    node.set("snapshot", snapshot(row.get(E.afterData)));
    return Optional.of(node);
  }

  private JsonNode snapshot(String value) { return value == null ? json.nullNode() : json.readTree(value); }
  private static String text(Long value) { return value == null ? null : value.toString(); }
}
