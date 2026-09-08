package com.ssafy.a107.candle.infrastructure.redis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;
import com.ssafy.a107.candle.service.port.RecentMinuteCandleCache;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class RedisRecentMinuteCandleCache implements RecentMinuteCandleCache {

    private static final Logger log = LoggerFactory.getLogger(RedisRecentMinuteCandleCache.class);
    private static final String KEY_PREFIX = "candle:1m:recent:";
    private static final DefaultRedisScript<Long> FILL_MISSING = new DefaultRedisScript<>("""
            for i = 2, #ARGV, 2 do
                local score = ARGV[i]
                if redis.call('ZCOUNT', KEYS[1], score, score) == 0 then
                    redis.call('ZADD', KEYS[1], score, ARGV[i + 1])
                end
            end
            redis.call('ZREMRANGEBYRANK', KEYS[1], 0, -(tonumber(ARGV[1]) + 1))
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ObjectReader reader;
    private final Validator validator;

    public RedisRecentMinuteCandleCache(StringRedisTemplate redis, ObjectMapper objectMapper, Validator validator) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.reader = objectMapper.readerFor(RecentMinuteCandleValue.class)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.validator = validator;
    }

    @Override
    public List<MinuteCandleSnapshot> findRecent(UUID assetId, int limit) {
        try {
            Set<TypedTuple<String>> entries = redis.opsForZSet().reverseRangeWithScores(KEY_PREFIX + assetId, 0, limit - 1L);
            if (entries == null || entries.isEmpty()) {
                return List.of();
            }
            List<MinuteCandleSnapshot> candles = new ArrayList<>(entries.size());
            Set<Long> openTimes = new HashSet<>();
            for (TypedTuple<String> entry : entries) {
                RecentMinuteCandleValue value = reader.readValue(entry.getValue());
                if (value == null || !validator.validate(value).isEmpty()
                        || !assetId.equals(value.assetId())
                        || entry.getScore() == null || entry.getScore() != value.openTimeUnixMilli().doubleValue()
                        || !openTimes.add(value.openTimeUnixMilli())) {
                    throw new IllegalArgumentException("Invalid recent candle cache");
                }
                candles.add(value.toSnapshot());
            }
            candles.sort(Comparator.comparingLong(MinuteCandleSnapshot::openTimeUnixMilli));
            return List.copyOf(candles);
        } catch (DataAccessException | IOException | IllegalArgumentException exception) {
            log.warn("Recent minute candle cache read failed; falling back to DB ({})",
                    exception.getClass().getSimpleName());
            return List.of();
        }
    }

    @Override
    public void fillMissing(UUID assetId, List<MinuteCandleSnapshot> candles) {
        if (candles.isEmpty()) {
            return;
        }
        try {
            List<String> arguments = new ArrayList<>(candles.size() * 2 + 1);
            arguments.add(Integer.toString(CAPACITY));
            for (MinuteCandleSnapshot candle : candles) {
                arguments.add(Long.toString(candle.openTimeUnixMilli()));
                arguments.add(objectMapper.writeValueAsString(RecentMinuteCandleValue.from(candle)));
            }
            // Go가 새로 넣거나 보정한 같은 시간의 봉을 오래된 DB 조회 결과로 덮어쓰지 않는다.
            redis.execute(FILL_MISSING, List.of(KEY_PREFIX + assetId), arguments.toArray());
        } catch (DataAccessException | IOException exception) {
            log.warn("Recent minute candle cache fill failed; serving DB result ({})",
                    exception.getClass().getSimpleName());
        }
    }
}
