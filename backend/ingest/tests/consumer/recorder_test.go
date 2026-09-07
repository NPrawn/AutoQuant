package consumer_test

import (
	"context"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/consumer"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"

	"github.com/shopspring/decimal"
)

type fakeStore struct {
	saved []candledomain.MinuteCandle
	err   error
}

func (s *fakeStore) Upsert(_ context.Context, saved candledomain.MinuteCandle) error {
	if s.err != nil {
		return s.err
	}
	s.saved = append(s.saved, saved)

	return nil
}

type fakeCache struct {
	saved []candledomain.MinuteCandle
}

func (c *fakeCache) Save(_ context.Context, saved candledomain.MinuteCandle) error {
	c.saved = append(c.saved, saved)

	return nil
}

type committedOffset struct {
	partition int32
	offset    int64
}

type fakeCommitter struct {
	calls []committedOffset
}

func (c *fakeCommitter) Commit(_ context.Context, partition int32, offset int64) error {
	c.calls = append(c.calls, committedOffset{partition: partition, offset: offset})

	return nil
}

func minuteCandle(assetID string, minute int) candledomain.MinuteCandle {
	return candledomain.MinuteCandle{
		AssetID:         assetID,
		IntervalMinutes: 1,
		OpenTime:        time.Date(2026, 9, 7, 10, minute, 0, 0, time.UTC),
		OpenPrice:       decimal.RequireFromString("108800"),
		HighPrice:       decimal.RequireFromString("108900"),
		LowPrice:        decimal.RequireFromString("108700"),
		ClosePrice:      decimal.RequireFromString("108850"),
		TradeVolume:     decimal.RequireFromString("1.5"),
		TradeAmount:     decimal.RequireFromString("163205"),
	}
}

func offsetRecord(partition int32, offset int64) ingestkafka.Record {
	return ingestkafka.Record{
		Topic:     ingestkafka.RawTradeTopic,
		Partition: partition,
		Offset:    offset,
		Key:       []byte("KRW-BTC"),
	}
}

func TestRecorderCachesInProgressCandle(t *testing.T) {
	store, cache, committer := &fakeStore{}, &fakeCache{}, &fakeCommitter{}
	recorder := consumer.NewRecorder(store, cache, committer)

	result := candle.Result{InProgress: minuteCandle("asset-btc", 3)}
	if err := recorder.Handle(context.Background(), offsetRecord(0, 10), result); err != nil {
		t.Fatalf("Handle returned error: %v", err)
	}

	if len(cache.saved) != 1 {
		t.Fatalf("캐시 반영 횟수 = %d, want 1", len(cache.saved))
	}
}

func TestRecorderStoresFinalizedCandle(t *testing.T) {
	store, cache, committer := &fakeStore{}, &fakeCache{}, &fakeCommitter{}
	recorder := consumer.NewRecorder(store, cache, committer)

	closed := minuteCandle("asset-btc", 3)
	result := candle.Result{InProgress: minuteCandle("asset-btc", 4), Closed: &closed}
	if err := recorder.Handle(context.Background(), offsetRecord(0, 11), result); err != nil {
		t.Fatalf("Handle returned error: %v", err)
	}

	if len(store.saved) != 1 {
		t.Fatalf("저장된 봉 개수 = %d, want 1", len(store.saved))
	}
	if got := store.saved[0].OpenTime.Minute(); got != 3 {
		t.Fatalf("저장된 봉의 분 = %d, want 3", got)
	}
}

func TestRecorderHoldsCommitAtOpenCandle(t *testing.T) {
	store, cache, committer := &fakeStore{}, &fakeCache{}, &fakeCommitter{}
	recorder := consumer.NewRecorder(store, cache, committer)
	ctx := context.Background()

	first := candle.Result{InProgress: minuteCandle("asset-btc", 3)}
	if err := recorder.Handle(ctx, offsetRecord(0, 10), first); err != nil {
		t.Fatalf("Handle returned error: %v", err)
	}
	second := candle.Result{InProgress: minuteCandle("asset-btc", 3)}
	if err := recorder.Handle(ctx, offsetRecord(0, 11), second); err != nil {
		t.Fatalf("Handle returned error: %v", err)
	}
	if err := recorder.Commit(ctx); err != nil {
		t.Fatalf("Commit returned error: %v", err)
	}

	if len(committer.calls) != 1 {
		t.Fatalf("커밋 호출 수 = %d, want 1", len(committer.calls))
	}
	if got := committer.calls[0].offset; got != 10 {
		t.Fatalf("커밋 오프셋 = %d, want 10 (봉이 아직 열려 있다)", got)
	}
}

func TestRecorderDoesNotCommitPastUnsavedCandle(t *testing.T) {
	store := &fakeStore{err: exception.New(exception.CodeDatabaseConstraintViolation)}
	cache, committer := &fakeCache{}, &fakeCommitter{}
	recorder := consumer.NewRecorder(store, cache, committer)
	ctx := context.Background()

	opening := candle.Result{InProgress: minuteCandle("asset-btc", 3)}
	if err := recorder.Handle(ctx, offsetRecord(0, 10), opening); err != nil {
		t.Fatalf("Handle returned error: %v", err)
	}

	closed := minuteCandle("asset-btc", 3)
	closing := candle.Result{InProgress: minuteCandle("asset-btc", 4), Closed: &closed}
	if err := recorder.Handle(ctx, offsetRecord(0, 11), closing); err == nil {
		t.Fatalf("Handle() error = nil, want error")
	}

	if err := recorder.Commit(ctx); err != nil {
		t.Fatalf("Commit returned error: %v", err)
	}
	if got := committer.calls[0].offset; got != 10 {
		t.Fatalf("커밋 오프셋 = %d, want 10 (저장되지 않은 봉의 재료를 지나칠 수 없다)", got)
	}
}

func TestRecorderAdvancesPastSkippedRecord(t *testing.T) {
	store, cache, committer := &fakeStore{}, &fakeCache{}, &fakeCommitter{}
	recorder := consumer.NewRecorder(store, cache, committer)
	ctx := context.Background()

	if err := recorder.Handle(ctx, offsetRecord(0, 10), candle.Result{Duplicate: true}); err != nil {
		t.Fatalf("Handle returned error: %v", err)
	}
	if err := recorder.Commit(ctx); err != nil {
		t.Fatalf("Commit returned error: %v", err)
	}

	if got := committer.calls[0].offset; got != 11 {
		t.Fatalf("커밋 오프셋 = %d, want 11", got)
	}
	if len(cache.saved) != 0 {
		t.Fatalf("캐시 반영 횟수 = %d, want 0", len(cache.saved))
	}
}

func TestRecorderAdvancesPastUnusableRecord(t *testing.T) {
	store, cache, committer := &fakeStore{}, &fakeCache{}, &fakeCommitter{}
	recorder := consumer.NewRecorder(store, cache, committer)
	ctx := context.Background()

	if err := recorder.Skip(ctx, offsetRecord(2, 40)); err != nil {
		t.Fatalf("Skip returned error: %v", err)
	}
	if err := recorder.Commit(ctx); err != nil {
		t.Fatalf("Commit returned error: %v", err)
	}

	if got := committer.calls[0].partition; got != 2 {
		t.Fatalf("커밋 파티션 = %d, want 2", got)
	}
	if got := committer.calls[0].offset; got != 41 {
		t.Fatalf("커밋 오프셋 = %d, want 41", got)
	}
}

func TestRecorderStoresSweptCandleAndReleasesHold(t *testing.T) {
	store, cache, committer := &fakeStore{}, &fakeCache{}, &fakeCommitter{}
	recorder := consumer.NewRecorder(store, cache, committer)
	ctx := context.Background()

	opening := candle.Result{InProgress: minuteCandle("asset-btc", 3)}
	if err := recorder.Handle(ctx, offsetRecord(0, 10), opening); err != nil {
		t.Fatalf("Handle returned error: %v", err)
	}

	swept := []candledomain.MinuteCandle{minuteCandle("asset-btc", 3)}
	if err := recorder.HandleSwept(ctx, 0, swept); err != nil {
		t.Fatalf("HandleSwept returned error: %v", err)
	}
	if err := recorder.Commit(ctx); err != nil {
		t.Fatalf("Commit returned error: %v", err)
	}

	if len(store.saved) != 1 {
		t.Fatalf("저장된 봉 개수 = %d, want 1", len(store.saved))
	}
	if got := committer.calls[0].offset; got != 11 {
		t.Fatalf("커밋 오프셋 = %d, want 11 (열린 봉이 없다)", got)
	}
}

func TestRecorderSkipsRedundantCommit(t *testing.T) {
	store, cache, committer := &fakeStore{}, &fakeCache{}, &fakeCommitter{}
	recorder := consumer.NewRecorder(store, cache, committer)
	ctx := context.Background()

	opening := candle.Result{InProgress: minuteCandle("asset-btc", 3)}
	if err := recorder.Handle(ctx, offsetRecord(0, 10), opening); err != nil {
		t.Fatalf("Handle returned error: %v", err)
	}
	if err := recorder.Commit(ctx); err != nil {
		t.Fatalf("첫 Commit returned error: %v", err)
	}
	if err := recorder.Commit(ctx); err != nil {
		t.Fatalf("두 번째 Commit returned error: %v", err)
	}

	if len(committer.calls) != 1 {
		t.Fatalf("커밋 호출 수 = %d, want 1 (지점이 그대로면 다시 커밋하지 않는다)", len(committer.calls))
	}
}
