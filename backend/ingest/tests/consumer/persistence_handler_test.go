package consumer_test

import (
	"context"
	"errors"
	"reflect"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/consumer"

	"github.com/shopspring/decimal"
)

type progressWriter struct {
	events *[]string
	err    error
}

func (w *progressWriter) SaveBatch(
	_ context.Context,
	_ int32,
	_ []candle.Checkpoint,
) error {
	*w.events = append(*w.events, "progress")
	return w.err
}

func (w *progressWriter) DeleteBatch(
	_ context.Context,
	_ int32,
	_ []string,
) error {
	*w.events = append(*w.events, "delete-progress")
	return w.err
}

type finalCandleWriter struct {
	events *[]string
	err    error
	skip   bool
}

func (w *finalCandleWriter) Upsert(
	_ context.Context,
	candle candledomain.MinuteCandle,
) (*candledomain.MinuteCandle, error) {
	*w.events = append(*w.events, "final")
	if w.err != nil || w.skip {
		return nil, w.err
	}
	return &candle, nil
}

func TestPersistenceHandlerStoresEveryFinalCandleBeforeProgress(t *testing.T) {
	events := make([]string, 0, 3)
	handler := consumer.NewPersistenceHandler(
		&progressWriter{events: &events},
		&finalCandleWriter{events: &events},
	)
	checkpoint := testCheckpoint("KRW-BTC", 11)
	closed := checkpoint.InProgress
	secondClosed := closed
	secondClosed.OpenTime = secondClosed.OpenTime.Add(time.Minute)
	batch := consumer.ProcessedBatch{
		Partition:     2,
		ClosedCandles: []candledomain.MinuteCandle{closed, secondClosed},
		Checkpoints:   []candle.Checkpoint{checkpoint},
	}

	if err := handler.Handle(context.Background(), &batch); err != nil {
		t.Fatalf("Handle() error = %v", err)
	}
	want := []string{"final", "final", "progress"}
	for index := range want {
		if events[index] != want[index] {
			t.Fatalf("events = %v, want %v", events, want)
		}
	}
}

func TestPersistenceHandlerDoesNotAdvanceProgressWhenFinalWriteFails(t *testing.T) {
	wantErr := errors.New("database unavailable")
	events := make([]string, 0, 1)
	handler := consumer.NewPersistenceHandler(
		&progressWriter{events: &events},
		&finalCandleWriter{events: &events, err: wantErr},
	)
	checkpoint := testCheckpoint("KRW-BTC", 11)
	closed := checkpoint.InProgress

	batch := consumer.ProcessedBatch{
		Partition:          2,
		ClosedCandles:      []candledomain.MinuteCandle{closed},
		Checkpoints:        []candle.Checkpoint{checkpoint},
		RemovedMarketCodes: []string{checkpoint.MarketCode},
	}
	err := handler.Handle(context.Background(), &batch)
	if !errors.Is(err, wantErr) {
		t.Fatalf("Handle() error = %v, want %v", err, wantErr)
	}
	if len(events) != 1 || events[0] != "final" {
		t.Fatalf("events = %v, want [final]", events)
	}
}

func TestPersistenceHandlerDeletesFinalizedProgressAfterFinalWrite(t *testing.T) {
	events := make([]string, 0, 2)
	handler := consumer.NewPersistenceHandler(
		&progressWriter{events: &events},
		&finalCandleWriter{events: &events},
	)
	checkpoint := testCheckpoint("KRW-BTC", 11)

	batch := consumer.ProcessedBatch{
		Partition:          2,
		ClosedCandles:      []candledomain.MinuteCandle{checkpoint.InProgress},
		RemovedMarketCodes: []string{checkpoint.MarketCode},
	}
	if err := handler.Handle(context.Background(), &batch); err != nil {
		t.Fatalf("Handle() error = %v", err)
	}
	want := []string{"final", "delete-progress"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
}

func TestPersistenceHandlerExcludesVerifiedCandleFromCacheResult(t *testing.T) {
	events := make([]string, 0, 1)
	handler := consumer.NewPersistenceHandler(
		&progressWriter{events: &events},
		&finalCandleWriter{events: &events, skip: true},
	)
	batch := consumer.ProcessedBatch{
		Partition:     2,
		ClosedCandles: []candledomain.MinuteCandle{testCheckpoint("KRW-BTC", 11).InProgress},
	}

	if err := handler.Handle(context.Background(), &batch); err != nil {
		t.Fatalf("Handle() error = %v", err)
	}
	if len(batch.ClosedCandles) != 0 {
		t.Fatalf("ClosedCandles = %#v, want empty verified result", batch.ClosedCandles)
	}
}

func testCheckpoint(marketCode string, sequenceID int64) candle.Checkpoint {
	openTime := time.Date(2026, time.September, 7, 12, 34, 0, 0, time.UTC)
	return candle.Checkpoint{
		MarketCode:     marketCode,
		LastSequenceID: sequenceID,
		InProgress: candledomain.MinuteCandle{
			AssetID:         "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
			IntervalMinutes: 1,
			OpenTime:        openTime,
			OpenPrice:       decimal.RequireFromString("100"),
			HighPrice:       decimal.RequireFromString("120"),
			LowPrice:        decimal.RequireFromString("90"),
			ClosePrice:      decimal.RequireFromString("110"),
			TradeVolume:     decimal.RequireFromString("2"),
			TradeAmount:     decimal.RequireFromString("220"),
			LastEventTime:   openTime.Add(30 * time.Second),
		},
	}
}
