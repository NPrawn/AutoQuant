package consumer

import (
	"context"
	"sync"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"

	"golang.org/x/sync/errgroup"
)

type RecordConsumer interface {
	Poll(context.Context) ([]ingestkafka.Record, error)
	Commit(context.Context, []ingestkafka.Record) error
	AllowRebalance()
	TakeRevokedPartitions() []int32
}

type CheckpointStore interface {
	Load(context.Context, int32) ([]candle.Checkpoint, error)
}

// ResultHandler persists one partition batch before its offset is committed.
type ResultHandler interface {
	Handle(context.Context, ProcessedBatch) error
}

// Service processes partitions concurrently while preserving record order
// within each partition.
type Service struct {
	consumer    RecordConsumer
	checkpoints CheckpointStore
	handler     ResultHandler
	workers     map[int32]*partitionWorker
}

func NewService(consumer RecordConsumer, checkpoints CheckpointStore, handler ResultHandler) *Service {
	return &Service{
		consumer:    consumer,
		checkpoints: checkpoints,
		handler:     handler,
		workers:     make(map[int32]*partitionWorker),
	}
}

func (s *Service) Run(ctx context.Context) error {
	for ctx.Err() == nil {
		records, err := s.consumer.Poll(ctx)
		if err != nil {
			return err
		}
		if ctx.Err() != nil || len(records) == 0 {
			return nil
		}
		for _, partition := range s.consumer.TakeRevokedPartitions() {
			delete(s.workers, partition)
		}

		if err := s.processAndCommit(ctx, records); err != nil {
			return err
		}
	}
	return nil
}

func (s *Service) processAndCommit(ctx context.Context, records []ingestkafka.Record) error {
	defer s.consumer.AllowRebalance()

	partitionRecords := make(map[int32][]ingestkafka.Record)
	for _, record := range records {
		partitionRecords[record.Partition] = append(partitionRecords[record.Partition], record)
	}

	lastRecords := make([]ingestkafka.Record, 0, len(partitionRecords))
	var lastRecordsMutex sync.Mutex
	group, groupContext := errgroup.WithContext(ctx)
	for partition, recordsForPartition := range partitionRecords {
		worker, exists := s.workers[partition]
		if !exists {
			worker = newPartitionWorker(partition, s.checkpoints, s.handler)
			s.workers[partition] = worker
		}

		group.Go(func() error {
			lastRecord, err := worker.Process(groupContext, recordsForPartition)
			if err != nil {
				return err
			}

			lastRecordsMutex.Lock()
			lastRecords = append(lastRecords, lastRecord)
			lastRecordsMutex.Unlock()
			return nil
		})
	}

	if err := group.Wait(); err != nil {
		return err
	}

	return s.consumer.Commit(ctx, lastRecords)
}
