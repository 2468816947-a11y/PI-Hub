package com.patrol.platform.elasticsearch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * patrol-event 索引初始化器(架构 §6.3)。
 * <p>
 * 启动后显式检查索引是否存在:
 * <ul>
 *   <li>不存在 → 创建索引 + 应用 @Document 注解推导的 mapping</li>
 *   <li>已存在 → 跳过(ES 中通过注解修改字段类型需重建索引, 由运维触发)</li>
 * </ul>
 * 字段类型约定:
 * <ul>
 *   <li>检索维度字段(设备/类型/区域/告警类型/级别)全部 keyword</li>
 *   <li>description 走 ik_max_word(ES 镜像已安装 analysis-ik 插件, 验证: docker exec elasticsearch bin/elasticsearch-plugin list)</li>
 *   <li>position 为 geo_point, 写入时由后端将 {lng, lat} 转为 {lat, lon}</li>
 *   <li>eventTime 按 ISO 8601 解析</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatrolEventIndexInitializer {

    private final ElasticsearchOperations elasticsearchOperations;

    @EventListener(ApplicationReadyEvent.class)
    public void initIndex() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(PatrolEventDocument.class);
        if (indexOps.exists()) {
            log.info("ES index already exists: {}", indexOps.getIndexCoordinates().getIndexName());
            return;
        }
        try {
            indexOps.create();
            indexOps.putMapping(indexOps.createMapping(PatrolEventDocument.class));
            log.info("ES index created with mapping: {}", indexOps.getIndexCoordinates().getIndexName());
        } catch (Exception e) {
            // 启动失败不致命: 阶段 5 检索前确保 ES 已就绪即可
            log.error("Failed to initialize patrol-event index", e);
        }
    }
}
