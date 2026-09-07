package consumer

import (
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
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
