package consumer

import (
	"context"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

const (
	// storeAttempts bounds how often one candle retries a failing dependency. Past
	// that the consumer gives up and exits; offsets stay uncommitted, so a restart
	// replays the records instead of losing candles.
	storeAttempts = 5

	// storeRetryDelay spaces those attempts out.
	storeRetryDelay = time.Second
)

// CandleStore persists finalized candles.
type CandleStore interface {
	Upsert(ctx context.Context, candle candledomain.MinuteCandle) error
}

// CandleCache holds the candle still being built so readers can render it live.
type CandleCache interface {
	Save(ctx context.Context, candle candledomain.MinuteCandle) error
}

// OffsetCommitter stores how far a partition has been durably processed.
type OffsetCommitter interface {
	Commit(ctx context.Context, partition int32, offset int64) error
}

// Recorder turns aggregation results into durable state. It caches the candle
// still being built, stores the finalized one, and lets a partition commit past a
// record only once the candle that record produced is safe.
type Recorder struct {
	store     CandleStore
	cache     CandleCache
	committer OffsetCommitter
	trackers  map[int32]*OffsetTracker
	committed map[int32]int64
}

func NewRecorder(store CandleStore, cache CandleCache, committer OffsetCommitter) *Recorder {
	return &Recorder{
		store:     store,
		cache:     cache,
		committer: committer,
		trackers:  make(map[int32]*OffsetTracker),
		committed: make(map[int32]int64),
	}
}

// Handle records the result of one applied record.
func (r *Recorder) Handle(ctx context.Context, record ingestkafka.Record, result candle.Result) error {
	tracker := r.trackerFor(record.Partition)

	if result.Skipped() {
		tracker.Advance(record.Offset)

		return nil
	}

	if result.Closed != nil {
		if err := r.storeCandle(ctx, *result.Closed); err != nil {
			return err
		}

		// The finalized candle is durable now, so the hold may move forward to the
		// candle this record opened.
		tracker.Close(result.Closed.AssetID)
	}

	tracker.Observe(result.InProgress.AssetID, record.Offset)

	if err := r.cache.Save(ctx, result.InProgress); err != nil {
		return err
	}

	tracker.Advance(record.Offset)

	return nil
}

// HandleSwept records candles finalized by a sweep rather than by a later record.
func (r *Recorder) HandleSwept(
	ctx context.Context,
	partition int32,
	candles []candledomain.MinuteCandle,
) error {
	tracker := r.trackerFor(partition)
	for _, finalized := range candles {
		if err := r.storeCandle(ctx, finalized); err != nil {
			return err
		}

		tracker.Close(finalized.AssetID)
	}

	return nil
}

// Commit stores the progress each partition has made since the last call. It runs
// once per batch rather than once per record, because a commit is a round trip to
// the broker and the commit point rarely moves within a batch.
func (r *Recorder) Commit(ctx context.Context) error {
	for partition, tracker := range r.trackers {
		offset, ok := tracker.CommitOffset()
		if !ok {
			continue
		}
		if previous, committed := r.committed[partition]; committed && previous == offset {
			continue
		}

		if err := r.committer.Commit(ctx, partition, offset); err != nil {
			return err
		}

		r.committed[partition] = offset
	}

	return nil
}

func (r *Recorder) trackerFor(partition int32) *OffsetTracker {
	tracker, exists := r.trackers[partition]
	if !exists {
		tracker = NewOffsetTracker()
		r.trackers[partition] = tracker
	}

	return tracker
}

// storeCandle retries while the failure looks temporary, so a brief database
// outage does not end the consumer.
func (r *Recorder) storeCandle(ctx context.Context, finalized candledomain.MinuteCandle) error {
	for attempt := 1; ; attempt++ {
		err := r.store.Upsert(ctx, finalized)
		if err == nil || !exception.Retryable(err) || attempt >= storeAttempts {
			return err
		}
		if !sleep(ctx, storeRetryDelay) {
			return err
		}
	}
}

// sleep waits out the delay and reports whether the wait finished rather than
// being cut short by a cancelled context.
func sleep(ctx context.Context, duration time.Duration) bool {
	timer := time.NewTimer(duration)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}
