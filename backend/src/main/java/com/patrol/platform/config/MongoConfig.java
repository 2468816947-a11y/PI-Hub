package com.patrol.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;

/**
 * MongoDB 自定义类型转换配置。
 * <p>
 * Spring Boot 自动配置默认注册了 {@link java.time.Instant} / {@link java.time.LocalDateTime}
 * 等常见时间类型的 codec, 但 **不包含** {@link OffsetDateTime} —— 本平台实体
 * (Device / Alarm / HdfsFile / ProcessedMessage) 大量使用 OffsetDateTime,
 * 因此需要显式注册双向 Converter:
 * <ul>
 *   <li>写库: OffsetDateTime → Date (UTC 瞬时点, 与 Mongo 原生 BSON Date 类型一致)</li>
 *   <li>读库: Date → OffsetDateTime (默认按 UTC 偏移还原, 与北京时区字符串经 mongosh 查时再转换)</li>
 * </ul>
 *
 * @see org.springframework.data.mongodb.core.convert.MongoCustomConversions
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(Arrays.asList(
                OffsetDateTimeToDateConverter.INSTANCE,
                DateToOffsetDateTimeConverter.INSTANCE
        ));
    }

    /**
     * 写库: OffsetDateTime → Date(UTC 毫秒)。
     */
    @WritingConverter
    enum OffsetDateTimeToDateConverter implements Converter<OffsetDateTime, Date> {
        INSTANCE;

        @Override
        public Date convert(OffsetDateTime source) {
            // 统一转 UTC 再存 Date, 避免时区漂移
            return Date.from(source.toInstant());
        }
    }

    /**
     * 读库: Date → OffsetDateTime(保留 UTC 偏移)。
     * <p>
     * 业务代码读出来后若需要本地时区字符串, 自己再做 {@code .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))}。
     */
    @ReadingConverter
    enum DateToOffsetDateTimeConverter implements Converter<Date, OffsetDateTime> {
        INSTANCE;

        @Override
        public OffsetDateTime convert(Date source) {
            return OffsetDateTime.ofInstant(source.toInstant(), ZoneOffset.UTC);
        }
    }
}
