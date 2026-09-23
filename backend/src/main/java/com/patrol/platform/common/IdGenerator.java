package com.patrol.platform.common;

import com.patrol.platform.elasticsearch.PatrolEventDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务编号生成器。
 * <p>
 * 任务/告警/事件编号按天序号, 由 {@link SequenceService} 的 Mongo 原子序列提供:
 * 双实例部署时同一天不会重复, 且切换方案时以存量数据最大序号播种、不覆盖旧记录。
 * 设备编号(时间戳 + 实例内自增)与文件编号(随机)天然无跨实例碰撞, 保持本地生成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ES_EVENT_INDEX = "patrol-event";

    private final SequenceService sequenceService;
    private final ElasticsearchOperations elasticsearchOperations;

    /** 设备编号的实例内自增(与毫秒时间戳拼接, 跨实例亦无碰撞风险)。 */
    private final AtomicLong deviceCounter = new AtomicLong(0);

    /**
     * 生成设备编号: 后端未传入 deviceId 时使用, 形如 UAV-1234567890123-1。
     */
    public String nextDeviceId(String deviceType) {
        long seq = deviceCounter.incrementAndGet();
        String prefix = BusinessConstants.DEVICE_TYPE_ROBOT_DOG.equals(deviceType) ? "ROBOTDOG" : "UAV";
        return String.format("%s-%013d-%d", prefix, System.currentTimeMillis(), seq);
    }

    /**
     * 生成任务编号: 形如 T-yyyyMMdd-序号(序号为跨实例原子自增)。
     */
    public String nextTaskId() {
        String today = LocalDate.now().format(DATE);
        long seq = sequenceService.next("task-" + today,
                () -> sequenceService.scanMaxSeq("patrol_task", "T-" + today + "-"));
        return String.format("T-%s-%05d", today, seq);
    }

    /**
     * 生成告警编号: 形如 A-yyyyMMdd-序号(序号为跨实例原子自增)。
     */
    public String nextAlarmId() {
        String today = LocalDate.now().format(DATE);
        long seq = sequenceService.next("alarm-" + today,
                () -> sequenceService.scanMaxSeq("alarm", "A-" + today + "-"));
        return String.format("A-%s-%05d", today, seq);
    }

    /**
     * 生成事件编号: 形如 E-yyyyMMdd-序号(序号为跨实例原子自增, ES _id 唯一)。
     */
    public String nextEventId() {
        String today = LocalDate.now().format(DATE);
        long seq = sequenceService.next("event-" + today,
                () -> scanEsMaxSeq("E-" + today + "-"));
        return String.format("E-%s-%07d", today, seq);
    }

    /**
     * 生成文件编号: 形如 img-xxxxxxxx(8 位随机字符串)。
     */
    public String nextFileId() {
        return "img-" + randomHex(8);
    }

    /**
     * 扫描 ES patrol-event 索引中 _id 以 idPrefix 开头的文档, 返回最大序号(失败返回 0)。
     */
    private long scanEsMaxSeq(String idPrefix) {
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.wildcard(w -> w.field("_id").wildcard(idPrefix + "*")))
                    .withFields("_id")
                    .withPageable(Pageable.ofSize(10_000))
                    .build();
            SearchHits<PatrolEventDocument> hits = elasticsearchOperations.search(
                    query, PatrolEventDocument.class, IndexCoordinates.of(ES_EVENT_INDEX));
            List<Document> docs = new ArrayList<>();
            for (SearchHit<PatrolEventDocument> hit : hits) {
                docs.add(new Document("_id", hit.getId()));
            }
            return SequenceService.maxTailSeq(docs);
        } catch (Exception e) {
            // ES 未就绪时回退 0: 最坏情况仅是事件 _id 覆盖旧文档, 不阻断主流程
            log.warn("Failed to scan ES event max seq, fallback 0: {}", e.getMessage());
            return 0;
        }
    }

    private static String randomHex(int len) {
        StringBuilder sb = new StringBuilder(len);
        String hex = "0123456789abcdef";
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < len; i++) {
            sb.append(hex.charAt(rnd.nextInt(16)));
        }
        return sb.toString();
    }
}
