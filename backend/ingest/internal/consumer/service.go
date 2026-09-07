package consumer

import (
	"context"
	"log/slog"
	"sync"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"

	"golang.org/x/sync/errgroup"
)

type RecordConsumer interface {
	Poll(context.Context) ([]ingestkafka.Record, error)
	Commit(context.Context, []ingestkafka.Record) error
	AllowRebalance()
	TakeAssignedPartitions() []int32
	TakeRevokedPartitions() []int32
}

type CheckpointStore interface {
	Load(context.Context, int32) ([]candle.Checkpoint, error)
}

// ResultHandler persists one partition batch before its offset is committed.
type ResultHandler interface {
	Handle(context.Context, ProcessedBatch) error
}

type LiveCandlePublisher interface {
	PublishBatch(context.Context, []candle.Checkpoint) error
}

type RecentCandleCache interface {
	SaveBatch(context.Context, []candledomain.MinuteCandle) error
}

// Service processes partitions concurrently while preserving record order
// within each partition.
type Service struct {
	consumer    RecordConsumer
	checkpoints CheckpointStore
	handler     ResultHandler
	recentCache RecentCandleCache
	publisher   LiveCandlePublisher
	workers     map[int32]*partitionWorker
}

const sweepInterval = time.Second

func NewService(
	consumer RecordConsumer,
	checkpoints CheckpointStore,
	handler ResultHandler,
	recentCache RecentCandleCache,
	publisher LiveCandlePublisher,
) *Service {
	return &Service{
		consumer:    consumer,
		checkpoints: checkpoints,
		handler:     handler,
		recentCache: recentCache,
		publisher:   publisher,
		workers:     make(map[int32]*partitionWorker),
	}
}

func (s *Service) Run(ctx context.Context) error {
	nextSweep := time.Now().Add(sweepInterval)
	for ctx.Err() == nil {
		pollContext, cancelPoll := context.WithDeadline(ctx, nextSweep)
		records, err := s.consumer.Poll(pollContext)
		cancelPoll()
		if err != nil {
			return err
		}
		if ctx.Err() != nil {
			s.consumer.AllowRebalance()
			return nil
		}

		if err := s.processPoll(ctx, records, &nextSweep); err != nil {
			return err
		}
	}
	return nil
}

func (s *Service) processPoll(
	ctx context.Context,
	records []ingestkafka.Record,
	nextSweep *time.Time,
) error {
	defer s.consumer.AllowRebalance()

	for _, partition := range s.consumer.TakeRevokedPartitions() {
		delete(s.workers, partition)
	}

	assignedWorkers := make([]*partitionWorker, 0)
	for _, partition := range s.consumer.TakeAssignedPartitions() {
		worker, exists := s.workers[partition]
		if !exists {
			worker = newPartitionWorker(partition, s.checkpoints, s.handler)
			s.workers[partition] = worker
		}
		if err := worker.restore(ctx); err != nil {
			return err
		}
		assignedWorkers = append(assignedWorkers, worker)
	}

	if len(records) > 0 {
		if err := s.processAndCommit(ctx, records); err != nil {
			return err
		}
	}
	if ctx.Err() != nil {
		return nil
	}

	now := time.Now()
	workers := assignedWorkers
	if !now.Before(*nextSweep) {
		workers = make([]*partitionWorker, 0, len(s.workers))
		for _, worker := range s.workers {
			workers = append(workers, worker)
		}
		*nextSweep = now.Add(sweepInterval)
	}
	return s.sweep(ctx, workers, now)
}

func (s *Service) processAndCommit(ctx context.Context, records []ingestkafka.Record) error {
	partitionRecords := make(map[int32][]ingestkafka.Record)
	for _, record := range records {
		partitionRecords[record.Partition] = append(partitionRecords[record.Partition], record)
	}

	lastRecords := make([]ingestkafka.Record, 0, len(partitionRecords))
	closedCandles := make([]candledomain.MinuteCandle, 0)
	checkpoints := make([]candle.Checkpoint, 0)
	var resultsMutex sync.Mutex
	group, groupContext := errgroup.WithContext(ctx)
	for partition, recordsForPartition := range partitionRecords {
		worker, exists := s.workers[partition]
		if !exists {
			worker = newPartitionWorker(partition, s.checkpoints, s.handler)
			s.workers[partition] = worker
		}

		group.Go(func() error {
			batch, lastRecord, err := worker.Process(groupContext, recordsForPartition)
			if err != nil {
				return err
			}

			resultsMutex.Lock()
			lastRecords = append(lastRecords, lastRecord)
			closedCandles = append(closedCandles, batch.ClosedCandles...)
			checkpoints = append(checkpoints, batch.Checkpoints...)
			resultsMutex.Unlock()
			return nil
		})
	}

	if err := group.Wait(); err != nil {
		return err
	}

	if err := s.consumer.Commit(ctx, lastRecords); err != nil {
		return err
	}
	if len(closedCandles) > 0 {
		if err := s.recentCache.SaveBatch(ctx, closedCandles); err != nil {
			slog.Warn(
				"failed to cache recent minute candles",
				"error", err,
				"candle_count", len(closedCandles),
			)
		}
	}
	if len(checkpoints) == 0 {
		return nil
	}
	if err := s.publisher.PublishBatch(ctx, checkpoints); err != nil {
		slog.Warn(
			"failed to publish live candle updates",
			"error", err,
			"market_count", len(checkpoints),
		)
	}

	return nil
}

func (s *Service) sweep(
	ctx context.Context,
	workers []*partitionWorker,
	now time.Time,
) error {
	if len(workers) == 0 {
		return nil
	}

	closedCandles := make([]candledomain.MinuteCandle, 0)
	var resultsMutex sync.Mutex
	group, groupContext := errgroup.WithContext(ctx)
	for _, worker := range workers {
		group.Go(func() error {
			closed, err := worker.Sweep(groupContext, now)
			if err != nil {
				return err
			}

			resultsMutex.Lock()
			closedCandles = append(closedCandles, closed...)
			resultsMutex.Unlock()
			return nil
		})
	}
	if err := group.Wait(); err != nil {
		return err
	}
	if len(closedCandles) == 0 {
		return nil
	}

	if err := s.recentCache.SaveBatch(ctx, closedCandles); err != nil {
		slog.Warn(
			"failed to cache swept minute candles",
			"error", err,
			"candle_count", len(closedCandles),
		)
	}
	return nil
}
