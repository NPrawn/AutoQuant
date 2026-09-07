package consumer_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
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
	skipped []ingestkafka.Record
	swept   map[int32][]candledomain.MinuteCandle
	commits int
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

func (h *recordingResultHandler) Skip(_ context.Context, record ingestkafka.Record) error {
	h.skipped = append(h.skipped, record)
	if h.cancel != nil {
		h.cancel()
	}

	return h.err
}

func (h *recordingResultHandler) HandleSwept(
	_ context.Context,
	partition int32,
	candles []candledomain.MinuteCandle,
) error {
	if h.swept == nil {
		h.swept = make(map[int32][]candledomain.MinuteCandle)
	}
	h.swept[partition] = append(h.swept[partition], candles...)

	return h.err
}

func (h *recordingResultHandler) Commit(context.Context) error {
	h.commits++

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

func TestServiceSkipsUnusableRecordInsteadOfStopping(t *testing.T) {
	unusable := ingestkafka.Record{
		Topic:     ingestkafka.RawTradeTopic,
		Partition: 3,
		Offset:    77,
		Key:       []byte("KRW-BTC"),
		Value:     []byte("{not json"),
	}
	poller := &batchPoller{batches: [][]ingestkafka.Record{{unusable}}}
	ctx, cancel := context.WithCancel(context.Background())
	handler := &recordingResultHandler{cancel: cancel}
	service := consumer.NewService(poller, consumer.NewProcessor(), handler)

	if err := service.Run(ctx); err != nil {
		t.Fatalf("Run() error = %v, want nil (잘못된 레코드는 소비를 멈추지 않는다)", err)
	}
	if len(handler.skipped) != 1 {
		t.Fatalf("건너뛴 레코드 수 = %d, want 1", len(handler.skipped))
	}
	if handler.skipped[0].Offset != 77 {
		t.Fatalf("건너뛴 오프셋 = %d, want 77", handler.skipped[0].Offset)
	}
	if len(handler.records) != 0 {
		t.Fatalf("처리된 레코드 수 = %d, want 0", len(handler.records))
	}
}

func TestServiceCommitsAfterBatch(t *testing.T) {
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	poller := &batchPoller{batches: [][]ingestkafka.Record{{
		tradeRecord(t, 1, "KRW-BTC", "100", "1", "BID", tradedAt, 1),
	}}}
	handler := &recordingResultHandler{}
	service := consumer.NewService(poller, consumer.NewProcessor(), handler)

	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()

	if err := service.Run(ctx); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if handler.commits == 0 {
		t.Fatalf("커밋 호출 수 = 0, want 1 이상")
	}
}
