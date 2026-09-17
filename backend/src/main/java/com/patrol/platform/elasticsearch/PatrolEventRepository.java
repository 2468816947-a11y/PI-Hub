package com.patrol.platform.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * 巡检事件 ES Repository。
 * Spring Data Elasticsearch 提供基础的 CRUD + 分页查询。
 * 复杂检索(geo 检索、聚合统计)由 PatrolEventService 使用 ElasticsearchOperations 实现。
 */
@Repository
public interface PatrolEventRepository extends ElasticsearchRepository<PatrolEventDocument, String> {
    // 基础 CRUD 已由父接口提供
    // 复杂查询逻辑在 Service 层使用 ElasticsearchOperations 实现
}
