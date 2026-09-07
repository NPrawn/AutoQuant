//go:build integration

// 실제 Redis가 필요하다.
// 실행: docker compose -f infra/docker-compose.yml up -d redis
//
//	go test -tags=integration ./tests/redis -v
package redis_test

import (
	"context"
	"encoding/json"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	ingestredis "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/redis"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"

	"github.com/shopspring/decimal"
)

func TestRealtimeTradePublisherPublishesJSON(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	client := ingestredis.NewClient(redisAddress())
	t.Cleanup(func() {
		if err := client.Close(); err != nil {
			t.Fatalf("Redis 연결 종료 실패: %v", err)
		}
	})

	subscription := client.Subscribe(ctx, ingestredis.RealtimeTradeChannel)
	t.Cleanup(func() {
		if err := subscription.Close(); err != nil {
			t.Fatalf("Redis 구독 종료 실패: %v", err)
		}
	})
	if _, err := subscription.Receive(ctx); err != nil {
		t.Fatalf("Redis 채널 구독 실패: %v", err)
	}

	tradedAt := time.Date(2026, time.September, 7, 12, 34, 56, 789000000, time.UTC)
	publisher := ingestredis.NewRealtimeTradePublisher(client)
	if err := publisher.Publish(ctx, tradedomain.MarketTrade{
		AssetID:    "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
		MarketCode: "KRW-BTC",
		Price:      "100",
		Volume:     "0.5",
		Side:       tradedomain.SideBid,
		TradedAt:   tradedAt,
		SequenceID: 123,
	}); err != nil {
		t.Fatalf("Publish() error = %v", err)
	}

	message, err := subscription.ReceiveMessage(ctx)
	if err != nil {
		t.Fatalf("Redis 메시지 수신 실패: %v", err)
	}
	var payload map[string]any
	if err := json.Unmarshal([]byte(message.Payload), &payload); err != nil {
		t.Fatalf("JSON 파싱 실패: %v", err)
	}
	if payload["market_code"] != "KRW-BTC" || payload["price"] != "100" ||
		payload["traded_at_unix_milli"] != float64(tradedAt.UnixMilli()) {
		t.Fatalf("payload = %#v, want normalized realtime trade", payload)
	}
}

func TestLiveMinuteCandlePublisherPublishesCheckpointWithoutSourceOffset(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	client := ingestredis.NewClient(redisAddress())
	t.Cleanup(func() {
		if err := client.Close(); err != nil {
			t.Fatalf("Redis 연결 종료 실패: %v", err)
		}
	})

	subscription := client.Subscribe(ctx, ingestredis.LiveMinuteCandleChannel)
	t.Cleanup(func() {
		if err := subscription.Close(); err != nil {
			t.Fatalf("Redis 구독 종료 실패: %v", err)
		}
	})
	if _, err := subscription.Receive(ctx); err != nil {
		t.Fatalf("Redis 채널 구독 실패: %v", err)
	}

	openTime := time.Date(2026, time.September, 7, 12, 34, 0, 0, time.UTC)
	publisher := ingestredis.NewLiveMinuteCandlePublisher(client)
	if err := publisher.PublishBatch(ctx, []candle.Checkpoint{{
		MarketCode:     "KRW-BTC",
		LastSequenceID: 123,
		SourceOffset:   456,
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
	}}); err != nil {
		t.Fatalf("PublishBatch() error = %v", err)
	}

	message, err := subscription.ReceiveMessage(ctx)
	if err != nil {
		t.Fatalf("Redis 메시지 수신 실패: %v", err)
	}
	var payload map[string]any
	if err := json.Unmarshal([]byte(message.Payload), &payload); err != nil {
		t.Fatalf("JSON 파싱 실패: %v", err)
	}
	if payload["market_code"] != "KRW-BTC" || payload["close_price"] != "110" ||
		payload["last_sequence_id"] != float64(123) {
		t.Fatalf("payload = %#v, want live minute candle", payload)
	}
	if _, exists := payload["source_offset"]; exists {
		t.Fatalf("payload = %#v, source_offset must remain internal", payload)
	}
}
