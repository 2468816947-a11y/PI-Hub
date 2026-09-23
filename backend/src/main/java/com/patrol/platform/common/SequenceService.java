package com.patrol.platform.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Mongo 原子序列服务(跨后端双实例的业务编号唯一性保障)。
 * <p>
 * 背景: 双实例各自维护内存自增计数器时, 同一天会生成相同业务编号
 * (如 T-20260923-00001 在两个实例各出现一次), Mongo @Id 后写覆盖先写, 数据丢失。
 * 本服务用 sequence 集合 + findAndModify($inc) 提供跨实例原子自增。
 * <p>
 * 播种策略: 首次使用时以 seedProvider(扫描存量数据最大序号)初始化,
 * 保证切到新方案后编号只增不减、不覆盖存量记录; 双实例并发播种靠
 * _id 唯一约束兜底(后插入方吃 DuplicateKeyException 后继续自增)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SequenceService {

    private static final String COLLECTION = "sequence";

    private final MongoOperations mongoOperations;

    /**
     * 取下一个序列值。
     *
     * @param key          sequence 文档 _id, 如 "task-20260923"
     * @param seedProvider 首次使用时的播种函数(返回存量数据最大序号, 无则 0)
     * @return 新序列值(从播种值 + 1 开始)
     */
    public long next(String key, LongSupplier seedProvider) {
        Query query = Query.query(Criteria.where("_id").is(key));
        Update inc = new Update().inc("value", 1);
        // 注意: options 不能传 null(MongoTemplate.findAndModify 有 Assert.notNull 校验)
        FindAndModifyOptions options = FindAndModifyOptions.options();
        Document doc = mongoOperations.findAndModify(query, inc, options, Document.class, COLLECTION);
        if (doc == null) {
            seed(key, seedProvider);
            doc = mongoOperations.findAndModify(query, inc, options, Document.class, COLLECTION);
        }
        if (doc == null) {
            throw new IllegalStateException("Mongo sequence unavailable: " + key);
        }
        return doc.getLong("value");
    }

    /**
     * 扫描 Mongo 集合中 _id 以 idPrefix 开头的文档, 返回尾部数字序号的最大值(无则 0)。
     */
    public long scanMaxSeq(String collection, String idPrefix) {
        // 注意: 不能用 Pattern.quote 包前缀——Mongo $regex(PCRE 方言)不识别 \Q...\E,
        // 会匹配不到任何文档(2026-09-23 实测)。此处只转义真正的正则元字符。
        Query query = Query.query(Criteria.where("_id").regex("^" + escapeRegex(idPrefix)));
        query.fields().include("_id");
        List<Document> docs = mongoOperations.find(query, Document.class, collection);
        return maxTailSeq(docs);
    }

    private static String escapeRegex(String s) {
        return s.replaceAll("([\\\\^$.|?*+()\\[\\]{}])", "\\\\$1");
    }

    /**
     * 从文档 _id 列表解析尾部数字序号的最大值。
     */
    public static long maxTailSeq(List<Document> docs) {
        long max = 0;
        for (Document d : docs) {
            String id = d.getString("_id");
            if (id == null) continue;
            int dash = id.lastIndexOf('-');
            if (dash <= 0 || dash >= id.length() - 1) continue;
            try {
                max = Math.max(max, Long.parseLong(id.substring(dash + 1)));
            } catch (NumberFormatException ignored) {
                // 非纯数字后缀的 _id 忽略
            }
        }
        return max;
    }

    private void seed(String key, LongSupplier seedProvider) {
        long maxExisting = seedProvider.getAsLong();
        try {
            mongoOperations.insert(new Document("_id", key).append("value", maxExisting), COLLECTION);
            log.info("Sequence seeded: key={}, start={}", key, maxExisting);
        } catch (DuplicateKeyException e) {
            // 双实例并发播种: 另一实例已成功, 直接用其种子继续自增
            log.info("Sequence seed race lost (already seeded by peer): key={}", key);
        }
    }
}
