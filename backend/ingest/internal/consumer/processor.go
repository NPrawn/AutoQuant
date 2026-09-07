package consumer

import (
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

// Processor owns one candle Aggregator for each Kafka partition.
// It is used synchronously by Service and is not safe for concurrent use.
type Processor struct {
	aggregators map[int32]*candle.Aggregator
}

func NewProcessor() *Processor {
	return &Processor{aggregators: make(map[int32]*candle.Aggregator)}
}

// Process validates one record and applies it to the owning partition's Aggregator.
func (p *Processor) Process(record ingestkafka.Record) (candle.Result, error) {
	trade, err := decodeTrade(record)
	if err != nil {
		return candle.Result{}, err
	}

	aggregator, exists := p.aggregators[record.Partition]
	if !exists {
		aggregator = candle.New()
		p.aggregators[record.Partition] = aggregator
	}
	return aggregator.Apply(trade)
}

// Sweep finalizes the candles whose minute has passed in every partition this
// processor owns, and reports them per partition.
//
// Only a later record closes a candle in Process, so a market that stopped
// trading would hold its last candle open forever without this.
func (p *Processor) Sweep(now time.Time) map[int32][]candledomain.MinuteCandle {
	swept := make(map[int32][]candledomain.MinuteCandle)
	for partition, aggregator := range p.aggregators {
		closed := aggregator.Sweep(now)
		if len(closed) > 0 {
			swept[partition] = closed
		}
	}

	return swept
}
