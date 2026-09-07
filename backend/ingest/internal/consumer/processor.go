package consumer

import (
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

// Processor owns the candle Aggregator for one Kafka partition. A partition
// worker uses it synchronously, so no shared concurrent map is required.
type Processor struct {
	aggregator *candle.Aggregator
}

func NewProcessor() *Processor {
	return &Processor{aggregator: candle.New()}
}

// ProcessedRecord contains the source metadata, aggregation result, and state
// that must be persisted before the source offset is committed.
type ProcessedRecord struct {
	Record     ingestkafka.Record
	MarketCode string
	Result     candle.Result
	Checkpoint *candle.Checkpoint
}

// Restore loads persisted market state before this partition processes records.
func (p *Processor) Restore(checkpoints []candle.Checkpoint) error {
	for _, checkpoint := range checkpoints {
		if err := p.aggregator.Restore(checkpoint); err != nil {
			return err
		}
	}

	return nil
}

// Process validates one record and applies it to this partition's Aggregator.
func (p *Processor) Process(record ingestkafka.Record) (ProcessedRecord, error) {
	trade, err := decodeTrade(record)
	if err != nil {
		return ProcessedRecord{}, err
	}

	result, err := p.aggregator.Apply(trade)
	if err != nil {
		return ProcessedRecord{}, err
	}

	processed := ProcessedRecord{
		Record:     record,
		MarketCode: trade.MarketCode,
		Result:     result,
	}
	if !result.Duplicate {
		checkpoint, exists := p.aggregator.Checkpoint(trade.MarketCode)
		if exists {
			checkpoint.SourceOffset = record.Offset
			processed.Checkpoint = &checkpoint
		}
	}

	return processed, nil
}
