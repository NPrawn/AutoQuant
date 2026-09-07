package consumer_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/consumer"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

type batchPoller struct {
	batches [][]ingestkafka.Record
}

func (p *batchPoller) Poll(ctx context.Context) ([]ingestkafka.Record, error) {
	if len(p.batches) == 0 {
		<-ctx.Done()
		return nil, nil
	}
	records := p.batches[0]
	p.batches = p.batches[1:]
	return records, nil
}

type recordingResultHandler struct {
	records []ingestkafka.Record
	results []candle.Result
	err     error
	cancel  context.CancelFunc
}

func (h *recordingResultHandler) Handle(
	_ context.Context,
	record ingestkafka.Record,
	result candle.Result,
) error {
	h.records = append(h.records, record)
	h.results = append(h.results, result)
	if h.cancel != nil {
		h.cancel()
	}
	return h.err
}

func TestServicePassesProcessedResultToHandler(t *testing.T) {
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	record := tradeRecord(t, 2, "KRW-BTC", "100", "1", "BID", tradedAt, 1)
	poller := &batchPoller{batches: [][]ingestkafka.Record{{record}}}
	ctx, cancel := context.WithCancel(context.Background())
	handler := &recordingResultHandler{cancel: cancel}
	service := consumer.NewService(poller, consumer.NewProcessor(), handler)

	if err := service.Run(ctx); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if len(handler.records) != 1 || len(handler.results) != 1 {
		t.Fatalf("handled records/results = %d/%d, want 1/1", len(handler.records), len(handler.results))
	}
	if handler.records[0].Partition != record.Partition || handler.results[0].InProgress.AssetID == "" {
		t.Fatalf("handled record/result = %#v/%#v, want processed partition 2 trade", handler.records[0], handler.results[0])
	}
}

func TestServiceStopsWhenHandlerFails(t *testing.T) {
	wantErr := errors.New("store unavailable")
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	poller := &batchPoller{batches: [][]ingestkafka.Record{{
		tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 1),
		tradeRecord(t, 0, "KRW-BTC", "110", "1", "BID", tradedAt.Add(time.Second), 2),
	}}}
	handler := &recordingResultHandler{err: wantErr}
	service := consumer.NewService(poller, consumer.NewProcessor(), handler)

	err := service.Run(context.Background())
	if !errors.Is(err, wantErr) {
		t.Fatalf("Run() error = %v, want %v", err, wantErr)
	}
	if len(handler.records) != 1 {
		t.Fatalf("handled record count = %d, want 1", len(handler.records))
	}
}
