package consumer

import (
	"context"
	"log/slog"
	"sort"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

// ProcessedBatch contains every durable state change produced from one
// partition in a Kafka Poll. Checkpoints contains only the latest state for
// each changed market.
type ProcessedBatch struct {
	Partition          int32
	ClosedCandles      []candledomain.MinuteCandle
	Checkpoints        []candle.Checkpoint
	RemovedMarketCodes []string
}

func (w *partitionWorker) Sweep(
	ctx context.Context,
	now time.Time,
) ([]candledomain.MinuteCandle, error) {
	if err := w.restore(ctx); err != nil {
		return nil, err
	}

	finalized := w.processor.Sweep(now)
	if len(finalized) == 0 {
		return nil, nil
	}

	closedCandles := make([]candledomain.MinuteCandle, 0, len(finalized))
	removedMarketCodes := make([]string, 0, len(finalized))
	for _, closed := range finalized {
		closedCandles = append(closedCandles, closed.Candle)
		removedMarketCodes = append(removedMarketCodes, closed.MarketCode)
	}

	batch := ProcessedBatch{
		Partition:          w.partition,
		ClosedCandles:      closedCandles,
		RemovedMarketCodes: removedMarketCodes,
	}
	if err := w.handler.Handle(ctx, batch); err != nil {
		return nil, err
	}

	return closedCandles, nil
}

type partitionWorker struct {
	partition   int32
	checkpoints CheckpointStore
	handler     ResultHandler
	processor   *Processor
	restored    bool
}

func newPartitionWorker(
	partition int32,
	checkpoints CheckpointStore,
	handler ResultHandler,
) *partitionWorker {
	return &partitionWorker{
		partition:   partition,
		checkpoints: checkpoints,
		handler:     handler,
		processor:   NewProcessor(),
	}
}

func (w *partitionWorker) Process(
	ctx context.Context,
	records []ingestkafka.Record,
) (ProcessedBatch, ingestkafka.Record, error) {
	if err := w.restore(ctx); err != nil {
		return ProcessedBatch{}, ingestkafka.Record{}, err
	}

	var lastRecord ingestkafka.Record
	var closedCandles []candledomain.MinuteCandle
	latestCheckpoints := make(map[string]candle.Checkpoint)
	for _, record := range records {
		if err := ctx.Err(); err != nil {
			return ProcessedBatch{}, ingestkafka.Record{}, err
		}

		processed, err := w.processor.Process(record)
		if err != nil {
			if invalidRecord(err) {
				slog.Warn(
					"skip invalid Kafka record",
					"error", err,
					"topic", record.Topic,
					"partition", record.Partition,
					"offset", record.Offset,
					"key", string(record.Key),
					"payload", string(record.Value),
				)
				lastRecord = record
				continue
			}
			return ProcessedBatch{}, ingestkafka.Record{}, err
		}
		if processed.Result.Closed != nil {
			closedCandles = append(closedCandles, *processed.Result.Closed)
		}
		if processed.Checkpoint != nil {
			latestCheckpoints[processed.MarketCode] = *processed.Checkpoint
		}
		lastRecord = record
	}

	checkpoints := make([]candle.Checkpoint, 0, len(latestCheckpoints))
	marketCodes := make([]string, 0, len(latestCheckpoints))
	for marketCode := range latestCheckpoints {
		marketCodes = append(marketCodes, marketCode)
	}
	sort.Strings(marketCodes)
	for _, marketCode := range marketCodes {
		checkpoints = append(checkpoints, latestCheckpoints[marketCode])
	}

	batch := ProcessedBatch{
		Partition:     w.partition,
		ClosedCandles: closedCandles,
		Checkpoints:   checkpoints,
	}
	if len(closedCandles) > 0 || len(checkpoints) > 0 {
		if err := w.handler.Handle(ctx, batch); err != nil {
			return ProcessedBatch{}, ingestkafka.Record{}, err
		}
	}

	return batch, lastRecord, nil
}

func invalidRecord(err error) bool {
	return exception.HasCode(err, exception.CodeKafkaInvalidMessage) ||
		exception.HasCode(err, exception.CodeCandleInvalidPayload)
}

func (w *partitionWorker) restore(ctx context.Context) error {
	if w.restored {
		return nil
	}

	checkpoints, err := w.checkpoints.Load(ctx, w.partition)
	if err != nil {
		return err
	}
	if err := w.processor.Restore(checkpoints); err != nil {
		return err
	}
	w.restored = true

	return nil
}
