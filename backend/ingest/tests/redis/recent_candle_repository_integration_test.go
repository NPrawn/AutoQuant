//go:build integration

// 실제 Redis가 필요하다.
// 실행: docker compose -f infra/docker-compose.yml up -d redis
//
//	go test -tags=integration ./tests/redis -v
package redis_test

import (
	"context"
	"encoding/json"
	"strconv"
	"testing"
	"time"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	ingestredis "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/redis"

	goredis "github.com/redis/go-redis/v9"
	"github.com/shopspring/decimal"
)

const recentCandleTestAssetID = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22"

func TestRecentMinuteCandleRepositoryReplacesAndKeepsLatestThousand(t *testing.T) {
	ctx := context.Background()
	client := ingestredis.NewClient(redisAddress())
	if err := client.Ping(ctx).Err(); err != nil {
		t.Fatalf("Redis에 연결할 수 없다: %v", err)
	}
	key := ingestredis.RecentMinuteCandlesKey(recentCandleTestAssetID)
	t.Cleanup(func() {
		if err := client.Del(ctx, key).Err(); err != nil {
			t.Fatalf("최근 분봉 캐시 정리 실패: %v", err)
		}
		if err := client.Close(); err != nil {
			t.Fatalf("Redis 연결 종료 실패: %v", err)
		}
	})

	start := time.Date(2026, time.September, 1, 0, 0, 0, 0, time.UTC)
	candles := make([]candledomain.MinuteCandle, 1001)
	for index := range candles {
		candles[index] = recentMinuteCandle(start.Add(time.Duration(index) * time.Minute))
	}
	repository := ingestredis.NewRecentMinuteCandleRepository(client)
	if err := repository.SaveBatch(ctx, candles); err != nil {
		t.Fatalf("SaveBatch() error = %v", err)
	}

	count, err := client.ZCard(ctx, key).Result()
	if err != nil {
		t.Fatalf("ZCard() error = %v", err)
	}
	if count != 1000 {
		t.Fatalf("cached candle count = %d, want 1000", count)
	}
	entries, err := client.ZRangeWithScores(ctx, key, 0, -1).Result()
	if err != nil {
		t.Fatalf("ZRangeWithScores() error = %v", err)
	}
	if int64(entries[0].Score) != start.Add(time.Minute).UnixMilli() ||
		int64(entries[len(entries)-1].Score) != start.Add(1000*time.Minute).UnixMilli() {
		t.Fatalf("cached score range = %.0f..%.0f, want latest 1000 candles", entries[0].Score, entries[len(entries)-1].Score)
	}

	corrected := candles[len(candles)-1]
	corrected.ClosePrice = decimal.RequireFromString("999")
	if err := repository.SaveBatch(ctx, []candledomain.MinuteCandle{corrected}); err != nil {
		t.Fatalf("SaveBatch(corrected) error = %v", err)
	}
	count, err = client.ZCard(ctx, key).Result()
	if err != nil {
		t.Fatalf("ZCard() after correction error = %v", err)
	}
	if count != 1000 {
		t.Fatalf("cached candle count after correction = %d, want 1000", count)
	}
	correctedValues, err := client.ZRangeByScore(ctx, key, &goredis.ZRangeBy{
		Min: strconv.FormatInt(corrected.OpenTime.UnixMilli(), 10),
		Max: strconv.FormatInt(corrected.OpenTime.UnixMilli(), 10),
	}).Result()
	if err != nil {
		t.Fatalf("ZRangeByScore() error = %v", err)
	}
	if len(correctedValues) != 1 {
		t.Fatalf("corrected candle count = %d, want 1", len(correctedValues))
	}
	var payload map[string]any
	if err := json.Unmarshal([]byte(correctedValues[0]), &payload); err != nil {
		t.Fatalf("cached JSON parse error = %v", err)
	}
	if payload["close_price"] != "999" {
		t.Fatalf("corrected payload = %#v, want close_price 999", payload)
	}
}

func recentMinuteCandle(openTime time.Time) candledomain.MinuteCandle {
	return candledomain.MinuteCandle{
		AssetID:         recentCandleTestAssetID,
		IntervalMinutes: 1,
		OpenTime:        openTime,
		OpenPrice:       decimal.RequireFromString("100"),
		HighPrice:       decimal.RequireFromString("120"),
		LowPrice:        decimal.RequireFromString("90"),
		ClosePrice:      decimal.RequireFromString("110"),
		TradeVolume:     decimal.RequireFromString("2.5"),
		TradeAmount:     decimal.RequireFromString("275"),
		LastEventTime:   openTime.Add(30 * time.Second),
	}
}
