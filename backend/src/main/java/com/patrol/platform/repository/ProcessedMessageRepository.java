package com.patrol.platform.repository;

import com.patrol.platform.entity.ProcessedMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 消息幂等去重 Repository。
 * msgId 唯一索引由 @Id 注解自动创建, 保证同一 msgId 只能插入一次。
 * TTL 索引: processedAt + 7 天后自动清理过期记录。
 */
@Repository
public interface ProcessedMessageRepository extends MongoRepository<ProcessedMessage, String> {

    /**
     * 检查消息是否已处理(幂等判断)。
     */
    boolean existsByMsgId(String msgId);

    /**
     * 按设备编号查询近期处理过的消息(问题排查用)。
     */
    List<ProcessedMessage> findByDeviceIdAndProcessedAtAfterOrderByProcessedAtDesc(
            String deviceId, OffsetDateTime since);

    /**
     * 按消息类型统计(运维统计用)。
     */
    long countByMsgType(String msgType);
}
