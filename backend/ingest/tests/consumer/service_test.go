package consumer_test

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/consumer"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

type batchConsumer struct {
	batches     [][]ingestkafka.Record
	revocations [][]int32
	committed   [][]ingestkafka.Record
	allowCount  int
	events      *[]string
	commitErr   error
	cancel      context.CancelFunc
	cancelAfter int
	revoked     []int32
	mutex       sync.Mutex
}

func (c *batchConsumer) Poll(ctx context.Context) ([]ingestkafka.Record, error) {
	if len(c.batches) == 0 {
		<-ctx.Done()
		return nil, nil
	}
	records := c.batches[0]
	c.batches = c.batches[1:]
	if len(c.revocations) > 0 {
		c.mutex.Lock()
		c.revoked = c.revocations[0]
		c.mutex.Unlock()
		c.revocations = c.revocations[1:]
	}
	return records, nil
}

func (c *batchConsumer) Commit(_ context.Context, records []ingestkafka.Record) error {
	c.mutex.Lock()
	c.committed = append(c.committed, records)
	if c.events != nil {
		*c.events = append(*c.events, "commit")
	}
	shouldCancel := c.cancel != nil && len(c.committed) >= c.cancelAfter
	c.mutex.Unlock()
	if shouldCancel {
		c.cancel()
	}
	return c.commitErr
}

func (c *batchConsumer) AllowRebalance() {
	c.mutex.Lock()
	defer c.mutex.Unlock()
	c.allowCount++
	if c.events != nil {
		*c.events = append(*c.events, "allow")
	}
}

func (c *batchConsumer) TakeRevokedPartitions() []int32 {
	c.mutex.Lock()
	defer c.mutex.Unlock()
	partitions := c.revoked
	c.revoked = nil
	return partitions
}

type checkpointStore struct {
	checkpoints map[int32][]candle.Checkpoint
	loadCount   map[int32]int
	mutex       sync.Mutex
}

func (s *checkpointStore) Load(_ context.Context, partition int32) ([]candle.Checkpoint, error) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	s.loadCount[partition]++
	return s.checkpoints[partition], nil
}

type recordingResultHandler struct {
	batches []consumer.ProcessedBatch
	err     error
	events  *[]string
	mutex   sync.Mutex
}

type recordingLiveCandlePublisher struct {
	batches [][]candle.Checkpoint
	err     error
	events  *[]string
	mutex   sync.Mutex
}

func (p *recordingLiveCandlePublisher) PublishBatch(
	_ context.Context,
	checkpoints []candle.Checkpoint,
) error {
	p.mutex.Lock()
	defer p.mutex.Unlock()
	p.batches = append(p.batches, append([]candle.Checkpoint(nil), checkpoints...))
	if p.events != nil {
		*p.events = append(*p.events, "publish")
	}
	return p.err
}

type blockingResultHandler struct {
	started chan string
	release chan struct{}
}

func (h *blockingResultHandler) Handle(ctx context.Context, batch consumer.ProcessedBatch) error {
	select {
	case h.started <- batch.Checkpoints[0].MarketCode:
	case <-ctx.Done():
		return ctx.Err()
	}

	select {
	case <-h.release:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func (h *recordingResultHandler) Handle(_ context.Context, batch consumer.ProcessedBatch) error {
	h.mutex.Lock()
	h.batches = append(h.batches, batch)
	if h.events != nil {
		*h.events = append(*h.events, "store")
	}
	h.mutex.Unlock()
	return h.err
}

func TestServiceCommitsAfterPersistingBatch(t *testing.T) {
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	record := tradeRecord(t, 2, "KRW-BTC", "100", "1", "BID", tradedAt, 1)
	events := make([]string, 0, 3)
	ctx, cancel := context.WithCancel(context.Background())
	kafkaConsumer := &batchConsumer{
		batches:     [][]ingestkafka.Record{{record}},
		events:      &events,
		cancel:      cancel,
		cancelAfter: 1,
	}
	checkpoints := &checkpointStore{
		checkpoints: make(map[int32][]candle.Checkpoint),
		loadCount:   make(map[int32]int),
	}
	handler := &recordingResultHandler{events: &events}
	publisher := &recordingLiveCandlePublisher{events: &events}
	service := consumer.NewService(kafkaConsumer, checkpoints, handler, publisher)

	if err := service.Run(ctx); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if len(kafkaConsumer.committed) != 1 || len(kafkaConsumer.committed[0]) != 1 {
		t.Fatalf("committed batches = %#v, want one record", kafkaConsumer.committed)
	}
	if got := kafkaConsumer.committed[0][0].Offset; got != record.Offset {
		t.Fatalf("committed offset = %d, want %d", got, record.Offset)
	}
	wantEvents := []string{"store", "commit", "publish", "allow"}
	if len(events) != len(wantEvents) {
		t.Fatalf("events = %v, want %v", events, wantEvents)
	}
	for index := range wantEvents {
		if events[index] != wantEvents[index] {
			t.Fatalf("events = %v, want %v", events, wantEvents)
		}
	}
}

func TestServiceCoalescesLatestCheckpointAndKeepsEveryClosedCandle(t *testing.T) {
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	records := []ingestkafka.Record{
		tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 1),
		tradeRecord(t, 0, "KRW-ETH", "200", "1", "BID", tradedAt, 1),
		tradeRecord(t, 0, "KRW-BTC", "110", "1", "BID", tradedAt.Add(time.Minute), 2),
		tradeRecord(t, 0, "KRW-BTC", "120", "1", "BID", tradedAt.Add(2*time.Minute), 3),
	}
	for index := range records {
		records[index].Offset = int64(10 + index)
	}

	ctx, cancel := context.WithCancel(context.Background())
	kafkaConsumer := &batchConsumer{
		batches:     [][]ingestkafka.Record{records},
		cancel:      cancel,
		cancelAfter: 1,
	}
	checkpoints := &checkpointStore{
		checkpoints: make(map[int32][]candle.Checkpoint),
		loadCount:   make(map[int32]int),
	}
	handler := &recordingResultHandler{}
	publisher := &recordingLiveCandlePublisher{}
	service := consumer.NewService(kafkaConsumer, checkpoints, handler, publisher)

	if err := service.Run(ctx); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if len(handler.batches) != 1 {
		t.Fatalf("handled batch count = %d, want 1", len(handler.batches))
	}

	batch := handler.batches[0]
	if len(batch.ClosedCandles) != 2 {
		t.Fatalf("closed candle count = %d, want 2", len(batch.ClosedCandles))
	}
	if len(batch.Checkpoints) != 2 {
		t.Fatalf("checkpoint count = %d, want 2 markets", len(batch.Checkpoints))
	}
	if got := batch.Checkpoints[0]; got.MarketCode != "KRW-BTC" ||
		got.LastSequenceID != 3 || got.SourceOffset != 13 {
		t.Fatalf("BTC checkpoint = %#v, want latest sequence 3 at offset 13", got)
	}
	if got := batch.Checkpoints[1]; got.MarketCode != "KRW-ETH" ||
		got.LastSequenceID != 1 || got.SourceOffset != 11 {
		t.Fatalf("ETH checkpoint = %#v, want sequence 1 at offset 11", got)
	}
	if len(publisher.batches) != 1 || len(publisher.batches[0]) != 2 {
		t.Fatalf("published checkpoints = %#v, want latest state for 2 markets", publisher.batches)
	}
}

func TestServiceDoesNotCommitWhenPersistenceFails(t *testing.T) {
	wantErr := errors.New("store unavailable")
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	kafkaConsumer := &batchConsumer{batches: [][]ingestkafka.Record{{
		tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 1),
		tradeRecord(t, 0, "KRW-BTC", "110", "1", "BID", tradedAt.Add(time.Second), 2),
	}}}
	checkpoints := &checkpointStore{
		checkpoints: make(map[int32][]candle.Checkpoint),
		loadCount:   make(map[int32]int),
	}
	handler := &recordingResultHandler{err: wantErr}
	publisher := &recordingLiveCandlePublisher{}
	service := consumer.NewService(kafkaConsumer, checkpoints, handler, publisher)

	err := service.Run(context.Background())
	if !errors.Is(err, wantErr) {
		t.Fatalf("Run() error = %v, want %v", err, wantErr)
	}
	if len(kafkaConsumer.committed) != 0 {
		t.Fatalf("committed batch count = %d, want 0", len(kafkaConsumer.committed))
	}
	if kafkaConsumer.allowCount != 1 {
		t.Fatalf("AllowRebalance count = %d, want 1", kafkaConsumer.allowCount)
	}
	if len(handler.batches) != 1 {
		t.Fatalf("handled batch count = %d, want 1", len(handler.batches))
	}
	if len(handler.batches[0].Checkpoints) != 1 ||
		handler.batches[0].Checkpoints[0].LastSequenceID != 2 {
		t.Fatalf("checkpoints = %#v, want latest sequence 2 only", handler.batches[0].Checkpoints)
	}
	if len(publisher.batches) != 0 {
		t.Fatalf("published batch count = %d, want 0", len(publisher.batches))
	}
}

func TestServiceDoesNotPublishWhenCommitFails(t *testing.T) {
	wantErr := errors.New("commit unavailable")
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	kafkaConsumer := &batchConsumer{
		batches: [][]ingestkafka.Record{{
			tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 1),
		}},
		commitErr: wantErr,
	}
	checkpoints := &checkpointStore{
		checkpoints: make(map[int32][]candle.Checkpoint),
		loadCount:   make(map[int32]int),
	}
	publisher := &recordingLiveCandlePublisher{}
	service := consumer.NewService(
		kafkaConsumer,
		checkpoints,
		&recordingResultHandler{},
		publisher,
	)

	err := service.Run(context.Background())
	if !errors.Is(err, wantErr) {
		t.Fatalf("Run() error = %v, want %v", err, wantErr)
	}
	if len(publisher.batches) != 0 {
		t.Fatalf("published batch count = %d, want 0", len(publisher.batches))
	}
}

func TestServiceIgnoresLiveCandlePublishError(t *testing.T) {
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	ctx, cancel := context.WithCancel(context.Background())
	kafkaConsumer := &batchConsumer{
		batches: [][]ingestkafka.Record{{
			tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 1),
		}},
		cancel:      cancel,
		cancelAfter: 1,
	}
	checkpoints := &checkpointStore{
		checkpoints: make(map[int32][]candle.Checkpoint),
		loadCount:   make(map[int32]int),
	}
	publisher := &recordingLiveCandlePublisher{err: errors.New("redis unavailable")}
	service := consumer.NewService(
		kafkaConsumer,
		checkpoints,
		&recordingResultHandler{},
		publisher,
	)

	if err := service.Run(ctx); err != nil {
		t.Fatalf("Run() error = %v, want Redis publish error ignored", err)
	}
	if len(kafkaConsumer.committed) != 1 || len(publisher.batches) != 1 {
		t.Fatalf(
			"committed/published batch counts = %d/%d, want 1/1",
			len(kafkaConsumer.committed),
			len(publisher.batches),
		)
	}
}

func TestServiceCommitsInvalidRecordWithoutPersistence(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	record := ingestkafka.Record{
		Topic:     ingestkafka.RawTradeTopic,
		Partition: 0,
		Offset:    5,
		Key:       []byte("KRW-BTC"),
		Value:     []byte(`{"asset_id":`),
	}
	kafkaConsumer := &batchConsumer{
		batches:     [][]ingestkafka.Record{{record}},
		cancel:      cancel,
		cancelAfter: 1,
	}
	checkpoints := &checkpointStore{
		checkpoints: make(map[int32][]candle.Checkpoint),
		loadCount:   make(map[int32]int),
	}
	handler := &recordingResultHandler{}
	service := consumer.NewService(
		kafkaConsumer,
		checkpoints,
		handler,
		&recordingLiveCandlePublisher{},
	)

	if err := service.Run(ctx); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if len(handler.batches) != 0 {
		t.Fatalf("handled batch count = %d, want 0", len(handler.batches))
	}
	if len(kafkaConsumer.committed) != 1 || kafkaConsumer.committed[0][0].Offset != record.Offset {
		t.Fatalf("committed batches = %#v, want invalid record offset", kafkaConsumer.committed)
	}
}

func TestServiceRestoresPartitionOnlyOnce(t *testing.T) {
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	ctx, cancel := context.WithCancel(context.Background())
	kafkaConsumer := &batchConsumer{batches: [][]ingestkafka.Record{
		{tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 1)},
		{tradeRecord(t, 0, "KRW-BTC", "110", "1", "BID", tradedAt.Add(time.Second), 2)},
	}}
	kafkaConsumer.cancel = cancel
	kafkaConsumer.cancelAfter = 2
	checkpoints := &checkpointStore{
		checkpoints: make(map[int32][]candle.Checkpoint),
		loadCount:   make(map[int32]int),
	}
	handler := &recordingResultHandler{}
	service := consumer.NewService(
		kafkaConsumer,
		checkpoints,
		handler,
		&recordingLiveCandlePublisher{},
	)

	if err := service.Run(ctx); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if got := checkpoints.loadCount[0]; got != 1 {
		t.Fatalf("checkpoint Load count = %d, want 1", got)
	}
}

func TestServiceRestoresRevokedPartitionAgain(t *testing.T) {
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	ctx, cancel := context.WithCancel(context.Background())
	kafkaConsumer := &batchConsumer{
		batches: [][]ingestkafka.Record{
			{tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 1)},
			{tradeRecord(t, 0, "KRW-BTC", "110", "1", "BID", tradedAt.Add(time.Second), 2)},
		},
		revocations: [][]int32{nil, {0}},
		cancel:      cancel,
		cancelAfter: 2,
	}
	checkpoints := &checkpointStore{
		checkpoints: make(map[int32][]candle.Checkpoint),
		loadCount:   make(map[int32]int),
	}
	service := consumer.NewService(
		kafkaConsumer,
		checkpoints,
		&recordingResultHandler{},
		&recordingLiveCandlePublisher{},
	)

	if err := service.Run(ctx); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if got := checkpoints.loadCount[0]; got != 2 {
		t.Fatalf("checkpoint Load count = %d, want 2", got)
	}
}

func TestServiceProcessesDifferentPartitionsConcurrently(t *testing.T) {
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	kafkaConsumer := &batchConsumer{
		batches: [][]ingestkafka.Record{{
			tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 1),
			tradeRecord(t, 1, "KRW-ETH", "200", "1", "BID", tradedAt, 1),
		}},
		cancel:      cancel,
		cancelAfter: 1,
	}
	checkpoints := &checkpointStore{
		checkpoints: make(map[int32][]candle.Checkpoint),
		loadCount:   make(map[int32]int),
	}
	handler := &blockingResultHandler{
		started: make(chan string, 2),
		release: make(chan struct{}),
	}
	service := consumer.NewService(
		kafkaConsumer,
		checkpoints,
		handler,
		&recordingLiveCandlePublisher{},
	)
	errChannel := make(chan error, 1)
	go func() {
		errChannel <- service.Run(ctx)
	}()

	startedMarkets := make(map[string]struct{})
	for len(startedMarkets) < 2 {
		select {
		case marketCode := <-handler.started:
			startedMarkets[marketCode] = struct{}{}
		case <-ctx.Done():
			t.Fatalf("started markets = %v, want both partitions", startedMarkets)
		}
	}
	close(handler.release)

	if err := <-errChannel; err != nil {
		t.Fatalf("Run() error = %v", err)
	}
}
