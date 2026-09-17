package com.patrol.platform.service;

import co.elastic.clients.elasticsearch._types.GeoDistanceType;
import co.elastic.clients.elasticsearch._types.LatLonGeoLocation;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.patrol.platform.dto.SearchDto.Bbox;
import com.patrol.platform.dto.SearchDto.EventSearchRequest;
import com.patrol.platform.dto.SearchDto.Geo;
import com.patrol.platform.dto.SearchDto.GeoPoint;
import com.patrol.platform.elasticsearch.PatrolEventDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 巡检事件检索与聚合 Service(接口文档 §2.5)。
 * <p>
 * 实现要点:
 * <ul>
 *   <li>事件检索: bool query + 多维度条件, 分页按 eventTime desc</li>
 *   <li>geo 检索: geo_distance(圆形) / geo_bounding_box(矩形), 两者互斥</li>
 *   <li>keyword 全文检索: description match 走 ik_max_word</li>
 *   <li>timeTrend: 1h 直方图 + 空桶补零(对齐接口文档 §2.5.2 期望 ECharts 折线图)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatrolEventSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    // ==================== §2.5.1 POST /api/search/events ====================

    /**
     * 检索巡检事件(分页)。
     */
    public org.springframework.data.domain.Page<PatrolEventDocument> searchEvents(EventSearchRequest req) {
        int page = req.page() == null || req.page() < 1 ? 0 : req.page() - 1;
        int size = req.size() == null || req.size() < 1 ? 20 : Math.min(req.size(), 100);

        NativeQueryBuilder qb = NativeQuery.builder()
                .withQuery(buildBoolQuery(req))
                .withSort(s -> s.field(f -> f.field("eventTime").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(page, size));

        SearchHits<PatrolEventDocument> hits = elasticsearchOperations.search(qb.build(), PatrolEventDocument.class);

        List<PatrolEventDocument> content = new ArrayList<>();
        hits.forEach(h -> content.add(h.getContent()));
        long total = hits.getTotalHits();

        return new org.springframework.data.domain.PageImpl<>(content, PageRequest.of(page, size), total);
    }

    /**
     * 构建 bool query: must(filter 条件) + must(keyword 检索) + filter(geo/time)
     */
    private Query buildBoolQuery(EventSearchRequest req) {
        return Query.of(q -> q.bool(b -> {
            // 关键词全文检索
            if (req.keyword() != null && !req.keyword().isEmpty()) {
                b.must(m -> m.match(mq -> mq.field("description").query(req.keyword())));
            }
            // term filter: 设备精确匹配
            if (req.deviceId() != null && !req.deviceId().isEmpty()) {
                b.filter(f -> f.term(t -> t.field("deviceId").value(req.deviceId())));
            }
            // terms filter: 事件类型多选
            if (req.eventTypes() != null && !req.eventTypes().isEmpty()) {
                b.filter(f -> f.terms(t -> t
                        .field("eventType")
                        .terms(tt -> tt.value(req.eventTypes().stream()
                                .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                .toList()))));
            }
            // term filter: 告警类型
            if (req.alarmType() != null && !req.alarmType().isEmpty()) {
                b.filter(f -> f.term(t -> t.field("alarmType").value(req.alarmType())));
            }
            // term filter: 告警等级
            if (req.alarmLevel() != null && !req.alarmLevel().isEmpty()) {
                b.filter(f -> f.term(t -> t.field("alarmLevel").value(req.alarmLevel())));
            }
            // term filter: 区域(keyword 精确)
            if (req.area() != null && !req.area().isEmpty()) {
                b.filter(f -> f.term(t -> t.field("area").value(req.area())));
            }
            // 时间范围
            if (req.from() != null || req.to() != null) {
                b.filter(f -> f.range(r -> {
                    r.field("eventTime");
                    if (req.from() != null) r.gte(co.elastic.clients.json.JsonData.of(req.from()));
                    if (req.to() != null) r.lte(co.elastic.clients.json.JsonData.of(req.to()));
                    return r;
                }));
            }
            // 地理范围(geo / bbox 二选一)
            Geo geo = req.geo();
            Bbox bbox = req.bbox();
            if (geo != null && bbox != null) {
                throw new com.patrol.platform.common.BusinessException(
                        com.patrol.platform.common.ErrorCode.VALIDATION_FAILED,
                        "geo 与 bbox 不能同时使用");
            }
            if (geo != null && geo.center() != null && geo.radiusKm() != null) {
                GeoPoint c = geo.center();
                if (c.lng() != null && c.lat() != null) {
                    b.filter(f -> f.geoDistance(g -> g
                            .field("position")
                            .distance(geo.radiusKm() + "km")
                            .location(loc -> loc.latlon(ll -> ll.lat(c.lat()).lon(c.lng())))
                            .distanceType(GeoDistanceType.Arc)));
                }
            } else if (bbox != null
                    && bbox.topLeft() != null && bbox.bottomRight() != null
                    && bbox.topLeft().lng() != null && bbox.topLeft().lat() != null
                    && bbox.bottomRight().lng() != null && bbox.bottomRight().lat() != null) {
                GeoPoint tl = bbox.topLeft();
                GeoPoint br = bbox.bottomRight();
                b.filter(f -> f.geoBoundingBox(gb -> gb
                        .field("position")
                        .boundingBox(bb2 -> bb2
                                .topLeft(tl2 -> tl2.latlon(ll -> ll.lat(tl.lat()).lon(tl.lng())))
                                .bottomRight(br2 -> br2.latlon(ll -> ll.lat(br.lat()).lon(br.lng()))))));
            }
            return b;
        }));
    }

    // ==================== §2.5.2 GET /api/search/stats ====================

    /**
     * 聚合统计(接口文档 §2.5.2)。
     * <p>
     * 数据获取策略: timeTrend 从 ES date_histogram 拿; alarmTypeDist / deviceRank / areaDist 从 MongoDB 聚合
     * (ES 文档可能未覆盖所有维度, MongoDB 是统计的权威来源)。
     */
    public Map<String, Object> aggregateStats(String from, String to) {
        Map<String, Object> data = new HashMap<>();

        // 1) totals: 从 MongoDB 实时统计
        Map<String, Object> totals = new HashMap<>();
        totals.put("events", elasticsearchOperations.count(
                NativeQuery.builder().withQuery(Query.of(q -> q.matchAll(m -> m))).build(),
                PatrolEventDocument.class));
        // alarms / devices 总数在 ReportService 中已统计; 这里通过外部注入更简洁——独立查询
        // 为避免循环依赖, 这里只算 events; alarms / devices 由 SearchController 注入 MongoDB repository
        data.put("totals", totals);

        // 2) alarmTypeDist: 按 alarmType terms
        data.put("alarmTypeDist", termsAgg("alarmType", 100));

        // 3) deviceRank: 按 deviceId terms, top 10
        data.put("deviceRank", termsAgg("deviceId", 10));

        // 4) areaDist: 按 area terms
        data.put("areaDist", termsAgg("area", 100));

        // 5) timeTrend: 每小时聚合, 空桶补零
        data.put("timeTrend", timeTrend(from, to));

        return data;
    }

    /**
     * terms 聚合通用实现。
     */
    private List<Map<String, Object>> termsAgg(String field, int topN) {
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(Query.of(q -> q.bool(b -> b.filter(f -> f.exists(e -> e.field(field))))))
                    .withAggregation(field, Aggregation.of(a -> a.terms(t -> t.field(field).size(topN))))
                    .withMaxResults(0)
                    .build();
            SearchHits<PatrolEventDocument> hits = elasticsearchOperations.search(query, PatrolEventDocument.class);
            AggregationsContainer<?> agg = hits.getAggregations();
            if (agg == null) return Collections.emptyList();
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) agg;
            ElasticsearchAggregation ea = aggregations.get(field);
            if (ea == null) return Collections.emptyList();
            @SuppressWarnings("unchecked")
            List<co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket> buckets =
                    (List<co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket>) ea.aggregation().getAggregate().sterms().buckets().array();
            List<Map<String, Object>> result = new ArrayList<>();
            for (var bucket : buckets) {
                Map<String, Object> item = new HashMap<>();
                item.put("key", bucket.key().stringValue());
                item.put("count", bucket.docCount());
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("terms aggregation failed: field={}", field, e);
            return Collections.emptyList();
        }
    }

    /**
     * timeTrend 按小时聚合 + 空桶补零。
     * <p>
     * 区间约定: from 缺省取 now - 24h; to 缺省 now; 补零按 from ~ to 每小时 1 桶。
     */
    private List<Map<String, Object>> timeTrend(String from, String to) {
        OffsetDateTime fromTs, toTs;
        try {
            fromTs = from != null ? OffsetDateTime.parse(from) : OffsetDateTime.now().minusHours(24);
            toTs = to != null ? OffsetDateTime.parse(to) : OffsetDateTime.now();
        } catch (Exception e) {
            log.warn("Invalid time range, fallback to last 24h", e);
            fromTs = OffsetDateTime.now().minusHours(24);
            toTs = OffsetDateTime.now();
        }
        if (fromTs.isAfter(toTs)) {
            return Collections.emptyList();
        }
        // 区间过大时强制截取为前 7 天, 避免桶数爆炸
        long totalHours = ChronoUnit.HOURS.between(fromTs, toTs);
        if (totalHours > 7L * 24) {
            fromTs = toTs.minusHours(7L * 24);
            totalHours = 7L * 24;
        }
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(Query.of(q -> q.range(r -> r
                            .field("eventTime")
                            .gte(co.elastic.clients.json.JsonData.of(fromTs.toString()))
                            .lte(co.elastic.clients.json.JsonData.of(toTs.toString())))))
                    .withAggregation("by_hour", Aggregation.of(a -> a
                            .dateHistogram(dh -> dh
                                    .field("eventTime")
                                    .calendarInterval(CalendarInterval.Hour)
                                    .minDocCount(0)
                                    .extendedBounds(b -> b
                                            .minField(co.elastic.clients.json.JsonData.of(fromTs.toString()))
                                            .maxField(co.elastic.clients.json.JsonData.of(toTs.toString()))))))
                    .withMaxResults(0)
                    .build();
            SearchHits<PatrolEventDocument> hits = elasticsearchOperations.search(query, PatrolEventDocument.class);
            // 解析 + 补零
            Map<LocalDateTime, Long> bucket = new HashMap<>();
            ZoneId zone = ZoneId.of("UTC"); // ES 默认 UTC 桶
            AggregationsContainer<?> agg = hits.getAggregations();
            if (agg != null) {
                ElasticsearchAggregations aggregations = (ElasticsearchAggregations) agg;
                ElasticsearchAggregation ea = aggregations.get("by_hour");
                if (ea != null) {
                    @SuppressWarnings("unchecked")
                    List<co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket> buckets =
                            (List<co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket>) ea.aggregation().getAggregate().dateHistogram().buckets().array();
                    for (var b : buckets) {
                        // key 是 epoch ms (long)
                        LocalDateTime hour = LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(b.key()), zone);
                        bucket.put(hour, b.docCount());
                    }
                }
            }
            // 补零
            List<Map<String, Object>> result = new ArrayList<>();
            LocalDateTime cursor = fromTs.atZoneSameInstant(zone).toLocalDateTime()
                    .truncatedTo(ChronoUnit.HOURS);
            LocalDateTime end = toTs.atZoneSameInstant(zone).toLocalDateTime();
            while (!cursor.isAfter(end)) {
                Map<String, Object> item = new HashMap<>();
                item.put("bucket", cursor.atZone(zone).toOffsetDateTime().toString());
                item.put("count", bucket.getOrDefault(cursor, 0L));
                result.add(item);
                cursor = cursor.plusHours(1);
            }
            return result;
        } catch (Exception e) {
            log.warn("timeTrend aggregation failed", e);
            return Collections.emptyList();
        }
    }
}
