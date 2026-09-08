package candlerollup

import (
	"context"
	"errors"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
)

type Publisher interface {
	PublishBatch(context.Context, []candle.Checkpoint) error
}

type livePrefix struct {
	minute   time.Time
	values   map[int16]*candledomain.MinuteCandle
	lastUsed time.Time
}

// LivePublisher is called sequentially after the consumer commits a batch. It
// loads at most one UTC day's source data per changed market/minute, not per tick
// or subscriber. A new owner rebuilds its prefixes from PostgreSQL.
type LivePublisher struct {
	reader    MinuteReader
	publisher Publisher
	prefixes  map[string]livePrefix
}

func NewLivePublisher(reader MinuteReader, publisher Publisher) *LivePublisher {
	return &LivePublisher{reader: reader, publisher: publisher, prefixes: make(map[string]livePrefix)}
}

func (p *LivePublisher) PublishBatch(ctx context.Context, checkpoints []candle.Checkpoint) error {
	updates := append([]candle.Checkpoint(nil), checkpoints...)
	var loadError error
	for _, checkpoint := range checkpoints {
		current := checkpoint.InProgress
		prefix, exists := p.prefixes[current.AssetID]
		if !exists || !prefix.minute.Equal(current.OpenTime) {
			minutes, err := p.reader.LoadMinutes(ctx, current.AssetID, Bucket(current.OpenTime, 1440), current.OpenTime)
			if err != nil {
				loadError = errors.Join(loadError, err)
				continue
			}
			prefix = livePrefix{minute: current.OpenTime, values: make(map[int16]*candledomain.MinuteCandle)}
			for _, interval := range Intervals() {
				prefix.values[interval] = Aggregate(current.AssetID, interval, Bucket(current.OpenTime, interval), minutes)
			}
		}
		prefix.lastUsed = time.Now()
		p.prefixes[current.AssetID] = prefix
		for _, interval := range Intervals() {
			derived := checkpoint
			derived.InProgress = current
			derived.InProgress.IntervalMinutes = interval
			derived.InProgress.OpenTime = Bucket(current.OpenTime, interval)
			if base := prefix.values[interval]; base != nil {
				derived.InProgress.OpenPrice = base.OpenPrice
				if base.HighPrice.GreaterThan(current.HighPrice) {
					derived.InProgress.HighPrice = base.HighPrice
				}
				if base.LowPrice.LessThan(current.LowPrice) {
					derived.InProgress.LowPrice = base.LowPrice
				}
				derived.InProgress.TradeVolume = base.TradeVolume.Add(current.TradeVolume)
				derived.InProgress.TradeAmount = base.TradeAmount.Add(current.TradeAmount)
			}
			updates = append(updates, derived)
		}
	}
	// Remove idle/revoked markets; there is no per-subscriber or unbounded history.
	for assetID, prefix := range p.prefixes {
		if time.Since(prefix.lastUsed) > 2*time.Minute {
			delete(p.prefixes, assetID)
		}
	}
	return errors.Join(loadError, p.publisher.PublishBatch(ctx, updates))
}
