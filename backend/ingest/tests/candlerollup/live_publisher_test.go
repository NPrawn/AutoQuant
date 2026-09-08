package candlerollup_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlerollup"
)

type readerFake struct {
	calls int
	err   error
}

func (r *readerFake) LoadMinutes(_ context.Context, asset string, from, to time.Time) ([]candledomain.MinuteCandle, error) {
	r.calls++
	return []candledomain.MinuteCandle{minute(asset, to.Add(-time.Minute), 100, 2)}, r.err
}

type publisherFake struct{ values []candle.Checkpoint }

func (p *publisherFake) PublishBatch(_ context.Context, values []candle.Checkpoint) error {
	p.values = values
	return nil
}

func TestLivePublisher(t *testing.T) {
	t.Run("반복_진행봉은_합산하지_않고_한번_조회한_종목별_상태에_교체한다", func(t *testing.T) {
		reader, sink := &readerFake{}, &publisherFake{}
		publisher := candlerollup.NewLivePublisher(reader, sink)
		start := time.Now().UTC().Truncate(5 * time.Minute).Add(time.Minute)
		input := []candle.Checkpoint{{MarketCode: "KRW-BTC", InProgress: minute("BTC", start, 120, 3), LastSequenceID: 1},
			{MarketCode: "KRW-ETH", InProgress: minute("ETH", start, 200, 7), LastSequenceID: 2}}
		for range 2 {
			if err := publisher.PublishBatch(context.Background(), input); err != nil {
				t.Fatal(err)
			}
		}
		if reader.calls != 2 {
			t.Fatalf("got %d reads, want 2", reader.calls)
		}
		for _, value := range sink.values {
			if value.InProgress.IntervalMinutes == 5 {
				want := "5"
				if value.MarketCode == "KRW-ETH" {
					want = "9"
				}
				if got := value.InProgress.TradeVolume.String(); got != want {
					t.Fatalf("got %s, want %s", got, want)
				}
			}
		}
	})
	t.Run("DB_복구_실패여도_기존_1분봉은_발행한다", func(t *testing.T) {
		unavailable := errors.New("unavailable")
		sink := &publisherFake{}
		publisher := candlerollup.NewLivePublisher(&readerFake{err: unavailable}, sink)
		err := publisher.PublishBatch(context.Background(), []candle.Checkpoint{{InProgress: minute("BTC", time.Now(), 100, 1)}})
		if !errors.Is(err, unavailable) || len(sink.values) != 1 {
			t.Fatalf("got %v / %d, want unavailable / 1", err, len(sink.values))
		}
	})
}
