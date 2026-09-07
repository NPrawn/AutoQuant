//go:build integration

// 실제 Redis가 필요하다.
// 실행: docker compose -f infra/docker-compose.yml up -d redis
//
//	go test -tags=integration ./tests/redis -v
package redis_test

import (
	"context"
	"os"
	"reflect"
	"strings"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	ingestredis "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/redis"

	"github.com/shopspring/decimal"
)

const (
	testPartition        = int32(987654)
	testMarketCode       = "KRW-REDIS-TEST"
	secondTestMarketCode = "KRW-REDIS-TEST-2"
)

func redisAddress() string {
	if address := os.Getenv("TEST_REDIS_ADDRESS"); address != "" {
		return address
	}
	return "localhost:6379"
}

func TestProgressRepositoryStoresLoadsAndDeletesCheckpoint(t *testing.T) {
	ctx := context.Background()
	client := ingestredis.NewClient(redisAddress())
	if err := client.Ping(ctx).Err(); err != nil {
		t.Fatalf("Redis에 연결할 수 없다: %v", err)
	}
	t.Cleanup(func() {
		if err := client.Del(ctx, ingestredis.ProgressKey(testPartition)).Err(); err != nil {
			t.Fatalf("테스트 체크포인트 정리 실패: %v", err)
		}
		if err := client.Close(); err != nil {
			t.Fatalf("Redis 연결 종료 실패: %v", err)
		}
	})

	repository := ingestredis.NewProgressRepository(client)
	want := checkpoint()
	want.SourceOffset = 321
	secondWant := want
	secondWant.MarketCode = secondTestMarketCode
	secondWant.LastSequenceID = 124
	secondWant.SourceOffset = 322
	if err := repository.SaveBatch(
		ctx,
		testPartition,
		[]candle.Checkpoint{want, secondWant},
	); err != nil {
		t.Fatalf("SaveBatch() error = %v", err)
	}

	got, err := repository.Load(ctx, testPartition)
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	gotByMarket := make(map[string]candle.Checkpoint, len(got))
	for _, checkpoint := range got {
		gotByMarket[checkpoint.MarketCode] = checkpoint
	}
	if len(gotByMarket) != 2 || !reflect.DeepEqual(gotByMarket[testMarketCode], want) ||
		!reflect.DeepEqual(gotByMarket[secondTestMarketCode], secondWant) {
		t.Fatalf("Load() = %#v, want both batched checkpoints", got)
	}

	encoded, err := client.HGet(ctx, ingestredis.ProgressKey(testPartition), testMarketCode).Result()
	if err != nil {
		t.Fatalf("HGet() error = %v", err)
	}
	if !strings.Contains(encoded, `"source_offset":321`) {
		t.Fatalf("stored value = %s, want source_offset 321", encoded)
	}

	if err := repository.DeleteBatch(ctx, testPartition, []string{testMarketCode}); err != nil {
		t.Fatalf("DeleteBatch() error = %v", err)
	}
	remaining, err := repository.Load(ctx, testPartition)
	if err != nil {
		t.Fatalf("Load() after DeleteBatch error = %v", err)
	}
	if len(remaining) != 1 || !reflect.DeepEqual(remaining[0], secondWant) {
		t.Fatalf("remaining checkpoints = %#v, want second checkpoint only", remaining)
	}
}

func checkpoint() candle.Checkpoint {
	openTime := time.Date(2026, time.September, 7, 12, 34, 0, 0, time.UTC)
	return candle.Checkpoint{
		MarketCode:     testMarketCode,
		LastSequenceID: 123,
		InProgress: candledomain.MinuteCandle{
			AssetID:         "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
			IntervalMinutes: 1,
			OpenTime:        openTime,
			OpenPrice:       decimal.RequireFromString("100"),
			HighPrice:       decimal.RequireFromString("120"),
			LowPrice:        decimal.RequireFromString("90"),
			ClosePrice:      decimal.RequireFromString("110"),
			TradeVolume:     decimal.RequireFromString("2.5"),
			TradeAmount:     decimal.RequireFromString("275"),
			LastEventTime:   openTime.Add(30 * time.Second),
		},
	}
}
