package candle

import (
	"sort"
	"time"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"

	"github.com/shopspring/decimal"
)

const (
	// intervalMinutes is the candle width this aggregator produces.
	intervalMinutes = 1

	// closeDelay keeps a candle open past the end of its minute so that trades
	// arriving slightly late are still counted. A sweep finalizes the candle once
	// the delay has passed, which is the only way a market that stopped trading
	// ever closes its last candle.
	closeDelay = 5 * time.Second
)

// Result reports what a single applied trade changed.
type Result struct {
	// InProgress is the candle the trade was folded into. It is meaningless when
	// the trade was skipped.
	InProgress candledomain.MinuteCandle

	// Closed is the earlier candle finalized by this trade, present only when the
	// trade opened a later minute.
	Closed *candledomain.MinuteCandle

	// Duplicate reports that the trade repeated an already applied sequence.
	Duplicate bool

	// Stale reports that the trade belonged to a minute that is already closed.
	Stale bool
}

// Checkpoint is the mutable aggregation state persisted for one market.
// LastSequenceID prevents a record replayed after a failed offset commit from
// being folded into the candle twice.
type Checkpoint struct {
	MarketCode     string
	InProgress     candledomain.MinuteCandle
	LastSequenceID int64
	SourceOffset   int64
}

// Skipped reports whether the trade left the aggregator unchanged.
func (r Result) Skipped() bool {
	return r.Duplicate || r.Stale
}

type openCandle struct {
	assetID       string
	openTime      time.Time
	openPrice     decimal.Decimal
	highPrice     decimal.Decimal
	lowPrice      decimal.Decimal
	closePrice    decimal.Decimal
	tradeVolume   decimal.Decimal
	tradeAmount   decimal.Decimal
	lastEventTime time.Time
}

func (c *openCandle) snapshot() candledomain.MinuteCandle {
	return candledomain.MinuteCandle{
		AssetID:         c.assetID,
		IntervalMinutes: intervalMinutes,
		OpenTime:        c.openTime,
		OpenPrice:       c.openPrice,
		HighPrice:       c.highPrice,
		LowPrice:        c.lowPrice,
		ClosePrice:      c.closePrice,
		TradeVolume:     c.tradeVolume,
		TradeAmount:     c.tradeAmount,
		LastEventTime:   c.lastEventTime,
	}
}

func (c *openCandle) endTime() time.Time {
	return c.openTime.Add(intervalMinutes * time.Minute)
}

// Aggregator folds trades into one-minute candles for the markets of a single
// Kafka partition. It owns mutable candle state and is not safe for concurrent
// use: one aggregator belongs to one partition worker.
type Aggregator struct {
	openCandles  map[string]*openCandle
	lastSequence map[string]int64
}

// New creates an aggregator holding no open candles.
func New() *Aggregator {
	return &Aggregator{
		openCandles:  make(map[string]*openCandle),
		lastSequence: make(map[string]int64),
	}
}

// Restore replaces the in-memory state for one market with a persisted checkpoint.
func (a *Aggregator) Restore(checkpoint Checkpoint) error {
	if checkpoint.MarketCode == "" || checkpoint.InProgress.AssetID == "" ||
		checkpoint.InProgress.IntervalMinutes != intervalMinutes || checkpoint.LastSequenceID <= 0 {
		return exception.New(exception.CodeCandleInvalidPayload)
	}

	candle := checkpoint.InProgress
	a.openCandles[checkpoint.MarketCode] = &openCandle{
		assetID:       candle.AssetID,
		openTime:      candle.OpenTime.UTC(),
		openPrice:     candle.OpenPrice,
		highPrice:     candle.HighPrice,
		lowPrice:      candle.LowPrice,
		closePrice:    candle.ClosePrice,
		tradeVolume:   candle.TradeVolume,
		tradeAmount:   candle.TradeAmount,
		lastEventTime: candle.LastEventTime.UTC(),
	}
	a.lastSequence[checkpoint.MarketCode] = checkpoint.LastSequenceID

	return nil
}

// Checkpoint returns the current mutable state for one market.
func (a *Aggregator) Checkpoint(marketCode string) (Checkpoint, bool) {
	current, exists := a.openCandles[marketCode]
	if !exists {
		return Checkpoint{}, false
	}

	return Checkpoint{
		MarketCode:     marketCode,
		InProgress:     current.snapshot(),
		LastSequenceID: a.lastSequence[marketCode],
	}, true
}

// Apply folds a trade into the candle of its market and reports the resulting
// state. A trade that belongs to a later minute finalizes the open candle first.
// Repeated and late trades are skipped so that redelivery cannot inflate a candle.
func (a *Aggregator) Apply(trade tradedomain.MarketTrade) (Result, error) {
	if last, seen := a.lastSequence[trade.MarketCode]; seen && trade.SequenceID <= last {
		return Result{Duplicate: true}, nil
	}

	price, err := candledomain.ParseAmount(trade.Price)
	if err != nil {
		return Result{}, err
	}

	volume, err := candledomain.ParseAmount(trade.Volume)
	if err != nil {
		return Result{}, err
	}

	tradedAt := trade.TradedAt.UTC()
	openTime := tradedAt.Truncate(time.Minute)

	var result Result
	current, isOpen := a.openCandles[trade.MarketCode]
	switch {
	case isOpen && openTime.Before(current.openTime):
		a.lastSequence[trade.MarketCode] = trade.SequenceID

		return Result{Stale: true}, nil
	case isOpen && openTime.After(current.openTime):
		closed := current.snapshot()
		result.Closed = &closed
		isOpen = false
	}

	if !isOpen {
		current = &openCandle{
			assetID:   trade.AssetID,
			openTime:  openTime,
			openPrice: price,
			highPrice: price,
			lowPrice:  price,
		}
		a.openCandles[trade.MarketCode] = current
	}

	if price.GreaterThan(current.highPrice) {
		current.highPrice = price
	}
	if price.LessThan(current.lowPrice) {
		current.lowPrice = price
	}
	current.closePrice = price
	current.tradeVolume = current.tradeVolume.Add(volume)
	current.tradeAmount = current.tradeAmount.Add(price.Mul(volume))
	current.lastEventTime = tradedAt

	a.lastSequence[trade.MarketCode] = trade.SequenceID
	result.InProgress = current.snapshot()

	return result, nil
}

// Sweep finalizes every candle whose minute ended before the close delay elapsed.
// Without it a market that stops trading would hold its last candle open forever,
// because only a later trade closes a candle in Apply.
func (a *Aggregator) Sweep(now time.Time) []candledomain.MinuteCandle {
	deadline := now.UTC().Add(-closeDelay)

	var closed []candledomain.MinuteCandle
	for marketCode, current := range a.openCandles {
		if current.endTime().After(deadline) {
			continue
		}

		closed = append(closed, current.snapshot())
		delete(a.openCandles, marketCode)
	}

	sort.Slice(closed, func(i, j int) bool {
		if !closed[i].OpenTime.Equal(closed[j].OpenTime) {
			return closed[i].OpenTime.Before(closed[j].OpenTime)
		}

		return closed[i].AssetID < closed[j].AssetID
	})

	return closed
}
