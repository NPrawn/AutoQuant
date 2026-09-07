package producer_test

import (
	"context"
	"encoding/json"
	"errors"
	"reflect"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/producer"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
)

type recordingRecordProducer struct {
	records    []ingestkafka.Record
	produceErr error
	flushErr   error
	flushed    bool
}

type recordingRealtimeTradePublisher struct {
	trades []tradedomain.MarketTrade
	err    error
}

func (p *recordingRealtimeTradePublisher) Publish(
	_ context.Context,
	trade tradedomain.MarketTrade,
) error {
	p.trades = append(p.trades, trade)
	return p.err
}

func (p *recordingRecordProducer) Produce(
	_ context.Context,
	record ingestkafka.Record,
	complete func(error),
) {
	p.records = append(p.records, ingestkafka.Record{
		Topic: record.Topic,
		Key:   append([]byte(nil), record.Key...),
		Value: append([]byte(nil), record.Value...),
	})
	complete(p.produceErr)
}

func (p *recordingRecordProducer) Flush(context.Context) error {
	p.flushed = true
	return p.flushErr
}

func TestTradePublisherPublishesMarketTrade(t *testing.T) {
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 56, 789000000, time.UTC)
	trade := tradedomain.MarketTrade{
		AssetID:    "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
		MarketCode: "KRW-BTC",
		Price:      "159324000.00000000",
		Volume:     "0.00123456",
		Side:       tradedomain.SideBid,
		TradedAt:   tradedAt,
		SequenceID: 123456789,
	}
	recordProducer := &recordingRecordProducer{}
	realtimePublisher := &recordingRealtimeTradePublisher{}
	publisher := producer.NewTradePublisher(recordProducer, realtimePublisher)
	trades := make(chan tradedomain.MarketTrade, 1)
	trades <- trade
	close(trades)

	if err := publisher.Run(context.Background(), trades); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if len(recordProducer.records) != 1 {
		t.Fatalf("published record count = %d, want 1", len(recordProducer.records))
	}

	record := recordProducer.records[0]
	if record.Topic != ingestkafka.RawTradeTopic || string(record.Key) != trade.MarketCode {
		t.Fatalf("record topic/key = %q/%q, want %q/%q", record.Topic, record.Key, ingestkafka.RawTradeTopic, trade.MarketCode)
	}
	var message ingestkafka.TradeMessage
	if err := json.Unmarshal(record.Value, &message); err != nil {
		t.Fatalf("Unmarshal() error = %v", err)
	}
	want := ingestkafka.TradeMessage{
		AssetID:           trade.AssetID,
		MarketCode:        trade.MarketCode,
		Price:             trade.Price,
		Volume:            trade.Volume,
		Side:              string(trade.Side),
		TradedAtUnixMilli: tradedAt.UnixMilli(),
		SequenceID:        trade.SequenceID,
	}
	if !reflect.DeepEqual(message, want) {
		t.Fatalf("message = %#v, want %#v", message, want)
	}
	if !recordProducer.flushed {
		t.Fatal("producer was not flushed")
	}
	if len(realtimePublisher.trades) != 1 ||
		!reflect.DeepEqual(realtimePublisher.trades[0], trade) {
		t.Fatalf("realtime trades = %#v, want published trade", realtimePublisher.trades)
	}
}

func TestTradePublisherContinuesRealtimeTradesWhenKafkaFails(t *testing.T) {
	recordProducer := &recordingRecordProducer{
		produceErr: exception.Wrap(exception.CodeKafkaPublishFailed, errors.New("broker unavailable")),
	}
	realtimePublisher := &recordingRealtimeTradePublisher{}
	publisher := producer.NewTradePublisher(recordProducer, realtimePublisher)
	trades := make(chan tradedomain.MarketTrade, 2)
	trades <- tradedomain.MarketTrade{MarketCode: "KRW-BTC", SequenceID: 1}
	trades <- tradedomain.MarketTrade{MarketCode: "KRW-ETH", SequenceID: 2}
	close(trades)

	if err := publisher.Run(context.Background(), trades); err != nil {
		t.Fatalf("Run() error = %v, want Kafka delivery errors isolated", err)
	}
	if !recordProducer.flushed {
		t.Fatal("producer was not flushed")
	}
	if len(recordProducer.records) != 2 || len(realtimePublisher.trades) != 2 {
		t.Fatalf(
			"Kafka/Redis publish counts = %d/%d, want 2/2",
			len(recordProducer.records),
			len(realtimePublisher.trades),
		)
	}
}

func TestTradePublisherIgnoresRealtimePublishError(t *testing.T) {
	recordProducer := &recordingRecordProducer{}
	realtimePublisher := &recordingRealtimeTradePublisher{err: errors.New("redis unavailable")}
	publisher := producer.NewTradePublisher(recordProducer, realtimePublisher)
	trades := make(chan tradedomain.MarketTrade, 1)
	trades <- tradedomain.MarketTrade{MarketCode: "KRW-BTC", SequenceID: 1}
	close(trades)

	if err := publisher.Run(context.Background(), trades); err != nil {
		t.Fatalf("Run() error = %v, want Redis publish error ignored", err)
	}
	if len(recordProducer.records) != 1 || len(realtimePublisher.trades) != 1 {
		t.Fatalf(
			"Kafka/Redis publish counts = %d/%d, want 1/1",
			len(recordProducer.records),
			len(realtimePublisher.trades),
		)
	}
}
