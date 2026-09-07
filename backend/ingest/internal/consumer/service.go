package consumer

import (
	"context"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

type RecordPoller interface {
	Poll(context.Context) ([]ingestkafka.Record, error)
}

// ResultHandler receives an aggregation result and its source record metadata.
type ResultHandler interface {
	Handle(context.Context, ingestkafka.Record, candle.Result) error
}

// Service polls and processes records synchronously without committing offsets.
type Service struct {
	poller    RecordPoller
	processor *Processor
	handler   ResultHandler
}

func NewService(poller RecordPoller, processor *Processor, handler ResultHandler) *Service {
	return &Service{
		poller:    poller,
		processor: processor,
		handler:   handler,
	}
}

func (s *Service) Run(ctx context.Context) error {
	for ctx.Err() == nil {
		records, err := s.poller.Poll(ctx)
		if err != nil {
			return err
		}
		if ctx.Err() != nil {
			return nil
		}

		for _, record := range records {
			if ctx.Err() != nil {
				return nil
			}
			result, err := s.processor.Process(record)
			if err != nil {
				return err
			}
			if err := s.handler.Handle(ctx, record, result); err != nil {
				return err
			}
		}
	}
	return nil
}
