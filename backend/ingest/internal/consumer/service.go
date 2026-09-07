package consumer

import (
	"context"
	"log/slog"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

// defaultPollTimeout bounds how long one fetch waits. The loop has to come back
// even while no records arrive, because a market that stopped trading has no later
// record to close its candle and only a sweep can finalize it.
const defaultPollTimeout = 5 * time.Second

type RecordPoller interface {
	Poll(context.Context) ([]ingestkafka.Record, error)
}

// ResultHandler receives every outcome the consume loop can produce, so that the
// offsets it commits reflect exactly what was durably handled.
type ResultHandler interface {
	// Handle records the result of an applied record.
	Handle(context.Context, ingestkafka.Record, candle.Result) error

	// Skip records that a record can never be applied, letting the offsets move
	// past it.
	Skip(context.Context, ingestkafka.Record) error

	// HandleSwept records candles finalized by a sweep rather than by a record.
	HandleSwept(context.Context, int32, []candledomain.MinuteCandle) error

	// Commit stores the progress made since the last call.
	Commit(context.Context) error
}

// Service polls and processes records synchronously. Every partition keeps its own
// aggregator inside Processor, so candle state is never shared even though one
// goroutine drives them all.
type Service struct {
	poller      RecordPoller
	processor   *Processor
	handler     ResultHandler
	pollTimeout time.Duration
}

func NewService(poller RecordPoller, processor *Processor, handler ResultHandler) *Service {
	return &Service{
		poller:      poller,
		processor:   processor,
		handler:     handler,
		pollTimeout: defaultPollTimeout,
	}
}

func (s *Service) Run(ctx context.Context) error {
	for ctx.Err() == nil {
		records, err := s.poll(ctx)
		if err != nil {
			return err
		}
		if ctx.Err() != nil {
			return nil
		}

		if err := s.consume(ctx, records); err != nil {
			return err
		}
		if ctx.Err() != nil {
			return nil
		}

		if err := s.sweep(ctx); err != nil {
			return err
		}
		if err := s.handler.Commit(ctx); err != nil {
			return err
		}
	}

	return nil
}

func (s *Service) poll(ctx context.Context) ([]ingestkafka.Record, error) {
	pollContext, cancel := context.WithTimeout(ctx, s.pollTimeout)
	defer cancel()

	return s.poller.Poll(pollContext)
}

func (s *Service) consume(ctx context.Context, records []ingestkafka.Record) error {
	for _, record := range records {
		if ctx.Err() != nil {
			return nil
		}

		result, err := s.processor.Process(record)
		if err != nil {
			// Bad data is skipped rather than retried: the record can never be
			// applied, so retrying it would stall the partition forever.
			slog.Warn(
				"skipped unusable trade record",
				"topic", record.Topic,
				"partition", record.Partition,
				"offset", record.Offset,
				"error", err,
			)

			if err := s.handler.Skip(ctx, record); err != nil {
				return err
			}

			continue
		}

		if err := s.handler.Handle(ctx, record, result); err != nil {
			return err
		}
	}

	return nil
}

func (s *Service) sweep(ctx context.Context) error {
	for partition, candles := range s.processor.Sweep(time.Now()) {
		if err := s.handler.HandleSwept(ctx, partition, candles); err != nil {
			return err
		}
	}

	return nil
}
