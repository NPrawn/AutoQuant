package consumer

import (
	"context"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
)

type ProgressWriter interface {
	SaveBatch(context.Context, int32, []candle.Checkpoint) error
}

type FinalCandleWriter interface {
	Upsert(context.Context, candledomain.MinuteCandle) error
}

// PersistenceHandler stores the finalized candle before advancing the live
// checkpoint. A replay can therefore safely repeat either idempotent write.
type PersistenceHandler struct {
	progressWriter ProgressWriter
	finalWriter    FinalCandleWriter
}

func NewPersistenceHandler(
	progressWriter ProgressWriter,
	finalWriter FinalCandleWriter,
) *PersistenceHandler {
	return &PersistenceHandler{
		progressWriter: progressWriter,
		finalWriter:    finalWriter,
	}
}

func (h *PersistenceHandler) Handle(ctx context.Context, batch ProcessedBatch) error {
	for _, closed := range batch.ClosedCandles {
		if err := h.finalWriter.Upsert(ctx, closed); err != nil {
			return err
		}
	}
	if len(batch.Checkpoints) == 0 {
		return nil
	}

	return h.progressWriter.SaveBatch(
		ctx,
		batch.Partition,
		batch.Checkpoints,
	)
}
