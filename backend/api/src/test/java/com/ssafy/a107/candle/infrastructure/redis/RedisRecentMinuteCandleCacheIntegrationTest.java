package com.ssafy.a107.candle.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.a107.candle.RecentMinuteCandleFixtures;
import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.stream.IntStream;

import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.ASSET_ID;
import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.GO_CACHE_VALUE;
import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.OPEN_TIME;
import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.candle;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "CANDLE_TEST_REDIS_PORT", matches = "[0-9]+")
class RedisRecentMinuteCandleCacheIntegrationTest {

    private static final String KEY = "candle:1m:recent:" + ASSET_ID;
    private final ObjectMapper mapper = new ObjectMapper();
    private LettuceConnectionFactory connection;
    private ValidatorFactory validators;
    private StringRedisTemplate redis;
    private RedisRecentMinuteCandleCache cache;

    @BeforeEach
    void setUp() {
        connection = new LettuceConnectionFactory("127.0.0.1",
                Integer.parseInt(System.getenv("CANDLE_TEST_REDIS_PORT")));
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        validators = Validation.buildDefaultValidatorFactory();
        cache = new RedisRecentMinuteCandleCache(redis, mapper, validators.getValidator());
        redis.delete(KEY);
    }

    @AfterEach
    void tearDown() {
        redis.delete(KEY);
        validators.close();
        connection.destroy();
    }

    @Test
    @DisplayName("Go Sorted Set JSON을 그대로 읽고 최신 봉을 오름차순으로 반환한다")
    void readGoCacheContract() {
        redis.opsForZSet().add(KEY, GO_CACHE_VALUE, OPEN_TIME);
        cache.fillMissing(ASSET_ID, 1, List.of(candle(1), candle(2)));

        assertThat(cache.findRecent(ASSET_ID, 1, 3)).containsExactly(candle(0), candle(1), candle(2));
        assertThat(cache.findRecent(ASSET_ID, 1, 2)).containsExactly(candle(1), candle(2));
    }

    @Test
    @DisplayName("종목이 같아도 간격별 캐시는 서로 섞이지 않는다")
    void isolateIntervals() {
        String derivedKey = "candle:5m:recent:" + ASSET_ID;
        try {
            redis.delete(derivedKey);
            cache.fillMissing(ASSET_ID, 1, List.of(candle(0)));
            MinuteCandleSnapshot derived = new MinuteCandleSnapshot(ASSET_ID, OPEN_TIME,
                    "200", "201", "199", "200", "5", "1000", OPEN_TIME + 299999);
            cache.fillMissing(ASSET_ID, 5, List.of(derived));

            assertThat(cache.findRecent(ASSET_ID, 1, 1)).containsExactly(candle(0));
            assertThat(cache.findRecent(ASSET_ID, 5, 1)).containsExactly(derived);
        } finally {
            redis.delete(derivedKey);
        }
    }

    @Test
    @DisplayName("DB 캐시 보충은 이미 보정된 봉과 새로 들어온 최신 봉을 보존한다")
    void preserveExistingCandles() {
        MinuteCandleSnapshot corrected = new MinuteCandleSnapshot(ASSET_ID, OPEN_TIME,
                "200", "201", "199", "200", "1", "200", OPEN_TIME + 59999);
        cache.fillMissing(ASSET_ID, 1, List.of(corrected, candle(2)));

        cache.fillMissing(ASSET_ID, 1, List.of(candle(0), candle(1)));
        cache.fillMissing(ASSET_ID, 1, List.of(candle(0), candle(1)));

        assertThat(cache.findRecent(ASSET_ID, 1, 3)).containsExactly(corrected, candle(1), candle(2));
        assertThat(redis.opsForZSet().size(KEY)).isEqualTo(3);
    }

    @Test
    @DisplayName("오래된 봉을 보충해도 최신 1000개만 남긴다")
    void keepLatestThousand() {
        cache.fillMissing(ASSET_ID, 1, IntStream.range(1, 1001)
                .mapToObj(RecentMinuteCandleFixtures::candle).toList());

        cache.fillMissing(ASSET_ID, 1, List.of(candle(0)));

        List<MinuteCandleSnapshot> result = cache.findRecent(ASSET_ID, 1, 1000);
        assertThat(result).hasSize(1000);
        assertThat(result.getFirst()).isEqualTo(candle(1));
        assertThat(result.getLast()).isEqualTo(candle(1000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"asset_id", "interval_minutes", "open_time_unix_milli", "open_price",
            "high_price", "low_price", "close_price", "trade_volume", "trade_amount", "last_event_time_unix_milli"})
    @DisplayName("필수 캐시 필드 누락을 미스로 처리한다")
    void rejectMissingField(String field) throws Exception {
        ObjectNode value = (ObjectNode) mapper.readTree(GO_CACHE_VALUE);
        value.remove(field);
        redis.opsForZSet().add(KEY, value.toString(), OPEN_TIME);

        assertThat(cache.findRecent(ASSET_ID, 1, 1)).isEmpty();
    }

    @Test
    @DisplayName("다른 종목의 데이터가 섞이면 캐시 미스로 처리한다")
    void rejectWrongAsset() {
        redis.opsForZSet().add(KEY, GO_CACHE_VALUE.replace(ASSET_ID.toString(),
                "22222222-2222-2222-2222-222222222222"), OPEN_TIME);

        assertThat(cache.findRecent(ASSET_ID, 1, 1)).isEmpty();
    }

    @Test
    @DisplayName("정렬 점수와 봉 시간이 다르면 캐시 미스로 처리한다")
    void rejectMismatchedScore() {
        redis.opsForZSet().add(KEY, GO_CACHE_VALUE, OPEN_TIME + 1);

        assertThat(cache.findRecent(ASSET_ID, 1, 1)).isEmpty();
    }

    @Test
    @DisplayName("캐시 JSON이 손상되어도 예외를 응답으로 전달하지 않는다")
    void rejectBrokenJson() {
        redis.opsForZSet().add(KEY, "{broken", OPEN_TIME);

        assertThat(cache.findRecent(ASSET_ID, 1, 1)).isEmpty();
    }
}
