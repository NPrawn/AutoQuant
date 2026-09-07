package consumer_test

import (
	"testing"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/consumer"
)

func TestCommitOffsetReportsNothingBeforeProcessing(t *testing.T) {
	tracker := consumer.NewOffsetTracker()

	if _, ok := tracker.CommitOffset(); ok {
		t.Fatalf("CommitOffset() ok = true, want false")
	}
}

func TestCommitOffsetAdvancesWhenNoCandleIsOpen(t *testing.T) {
	tracker := consumer.NewOffsetTracker()
	tracker.Observe("KRW-BTC", 100)
	tracker.Advance(100)
	tracker.Close("KRW-BTC")

	offset, ok := tracker.CommitOffset()
	if !ok {
		t.Fatalf("CommitOffset() ok = false, want true")
	}
	if offset != 101 {
		t.Fatalf("CommitOffset() = %d, want 101", offset)
	}
}

func TestCommitOffsetHoldsAtOldestOpenCandle(t *testing.T) {
	tracker := consumer.NewOffsetTracker()
	tracker.Observe("KRW-BTC", 100)
	tracker.Advance(100)
	tracker.Observe("KRW-ETH", 101)
	tracker.Advance(101)
	tracker.Observe("KRW-BTC", 102)
	tracker.Advance(102)

	offset, _ := tracker.CommitOffset()
	if offset != 100 {
		t.Fatalf("CommitOffset() = %d, want 100", offset)
	}
}

func TestCommitOffsetHoldsForOtherMarketAfterOneCloses(t *testing.T) {
	tracker := consumer.NewOffsetTracker()
	tracker.Observe("KRW-BTC", 100)
	tracker.Advance(100)
	tracker.Observe("KRW-ETH", 101)
	tracker.Advance(101)

	tracker.Close("KRW-BTC")

	offset, _ := tracker.CommitOffset()
	if offset != 101 {
		t.Fatalf("CommitOffset() = %d, want 101 (ETH 봉이 아직 열려 있다)", offset)
	}
}

func TestObserveKeepsFirstOffsetOfOpenCandle(t *testing.T) {
	tracker := consumer.NewOffsetTracker()
	tracker.Observe("KRW-BTC", 100)
	tracker.Observe("KRW-BTC", 101)
	tracker.Observe("KRW-BTC", 102)
	tracker.Advance(102)

	offset, _ := tracker.CommitOffset()
	if offset != 100 {
		t.Fatalf("CommitOffset() = %d, want 100", offset)
	}
}

func TestObserveAfterCloseStartsNewCandle(t *testing.T) {
	tracker := consumer.NewOffsetTracker()
	tracker.Observe("KRW-BTC", 100)
	tracker.Advance(100)

	tracker.Close("KRW-BTC")
	tracker.Observe("KRW-BTC", 101)
	tracker.Advance(101)

	offset, _ := tracker.CommitOffset()
	if offset != 101 {
		t.Fatalf("CommitOffset() = %d, want 101", offset)
	}
}

func TestAdvanceIgnoresOlderOffset(t *testing.T) {
	tracker := consumer.NewOffsetTracker()
	tracker.Advance(200)
	tracker.Advance(150)

	offset, _ := tracker.CommitOffset()
	if offset != 201 {
		t.Fatalf("CommitOffset() = %d, want 201", offset)
	}
}
