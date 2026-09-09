package com.ssafy.a107.candle.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Set;

import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.ASSET_ID;
import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.GO_CACHE_VALUE;
import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.OPEN_TIME;
import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.candle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RedisRecentMinuteCandleCacheTest {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ZSetOperations<String, String> sortedSet;
    private RedisRecentMinuteCandleCache cache;

    @BeforeEach
    void setUp() {
        cache = new RedisRecentMinuteCandleCache(redis, new ObjectMapper(), VALIDATORS.getValidator());
    }

    @AfterAll
    static void closeValidation() {
        VALIDATORS.close();
    }

    @Test
    @DisplayName("Redis 읽기 장애는 캐시 미스로 처리한다")
    void fallbackWhenRedisReadFails() {
        given(redis.opsForZSet()).willReturn(sortedSet);
        given(sortedSet.reverseRangeWithScores("candle:1m:recent:" + ASSET_ID, 0, 9))
                .willThrow(new DataAccessResourceFailureException("redis unavailable"));

        assertThat(cache.findRecent(ASSET_ID, 1, 10)).isEmpty();
    }

    @Test
    @DisplayName("Redis 쓰기 장애가 DB 조회 결과 반환을 막지 않는다")
    void ignoreCacheWriteFailure() {
        given(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(Object[].class)))
                .willThrow(new DataAccessResourceFailureException("redis unavailable"));

        assertThatCode(() -> cache.fillMissing(ASSET_ID, 1, List.of(candle(0)))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 봉 시작 시간에 여러 값이 있으면 캐시 미스로 처리한다")
    void rejectDuplicateOpenTime() {
        given(redis.opsForZSet()).willReturn(sortedSet);
        given(sortedSet.reverseRangeWithScores(eq("candle:1m:recent:" + ASSET_ID), eq(0L), eq(9L)))
                .willReturn(Set.of(
                        new DefaultTypedTuple<>(GO_CACHE_VALUE, (double) OPEN_TIME),
                        new DefaultTypedTuple<>(GO_CACHE_VALUE.replace("\"close_price\":\"100\"", "\"close_price\":\"101\""),
                                (double) OPEN_TIME)
                ));

        assertThat(cache.findRecent(ASSET_ID, 1, 10)).isEmpty();
    }
}
