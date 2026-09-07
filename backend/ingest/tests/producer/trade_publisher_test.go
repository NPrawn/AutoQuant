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

type recordingRealtimeTradeSink struct {
	trades []tradedomain.MarketTrade
	reject bool
	closed bool
}

func (p *recordingRealtimeTradeSink) TryPublish(trade tradedomain.MarketTrade) bool {
	p.trades = append(p.trades, trade)
	return !p.reject
}

func (p *recordingRealtimeTradeSink) Close() {
	p.closed = true
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
	realtimeSink := &recordingRealtimeTradeSink{}
	publisher := producer.NewTradePublisher(recordProducer, realtimeSink)
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
	if len(realtimeSink.trades) != 1 || !reflect.DeepEqual(realtimeSink.trades[0], trade) {
		t.Fatalf("realtime trades = %#v, want queued trade", realtimeSink.trades)
	}
	if !realtimeSink.closed {
		t.Fatal("realtime trade sink was not closed")
	}
}

func TestTradePublisherContinuesRealtimeTradesWhenKafkaFails(t *testing.T) {
	recordProducer := &recordingRecordProducer{
		produceErr: exception.Wrap(exception.CodeKafkaPublishFailed, errors.New("broker unavailable")),
	}
	realtimeSink := &recordingRealtimeTradeSink{}
	publisher := producer.NewTradePublisher(recordProducer, realtimeSink)
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
	if len(recordProducer.records) != 2 || len(realtimeSink.trades) != 2 {
		t.Fatalf(
			"Kafka/Redis publish counts = %d/%d, want 2/2",
			len(recordProducer.records),
			len(realtimeSink.trades),
		)
	}
}

func TestTradePublisherIgnoresFullRealtimeQueue(t *testing.T) {
	recordProducer := &recordingRecordProducer{}
	realtimeSink := &recordingRealtimeTradeSink{reject: true}
	publisher := producer.NewTradePublisher(recordProducer, realtimeSink)
	trades := make(chan tradedomain.MarketTrade, 1)
	trades <- tradedomain.MarketTrade{MarketCode: "KRW-BTC", SequenceID: 1}
	close(trades)

	if err := publisher.Run(context.Background(), trades); err != nil {
		t.Fatalf("Run() error = %v, want full realtime queue ignored", err)
	}
	if len(recordProducer.records) != 1 || len(realtimeSink.trades) != 1 {
		t.Fatalf(
			"Kafka/Redis publish counts = %d/%d, want 1/1",
			len(recordProducer.records),
			len(realtimeSink.trades),
		)
	}
}
