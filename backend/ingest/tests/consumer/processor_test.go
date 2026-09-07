package consumer_test

import (
	"encoding/json"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/consumer"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"

	"github.com/shopspring/decimal"
)

func TestProcessorAppliesTradesInPartitionOrder(t *testing.T) {
	processor := consumer.NewProcessor()
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)

	if _, err := processor.Process(tradeRecord(t, 2, "KRW-BTC", "100", "2", "BID", tradedAt, 1)); err != nil {
		t.Fatalf("first Process() error = %v", err)
	}
	result, err := processor.Process(tradeRecord(t, 2, "KRW-BTC", "120", "1", "ASK", tradedAt.Add(time.Second), 2))
	if err != nil {
		t.Fatalf("second Process() error = %v", err)
	}

	if !result.InProgress.OpenPrice.Equal(decimal.RequireFromString("100")) ||
		!result.InProgress.HighPrice.Equal(decimal.RequireFromString("120")) ||
		!result.InProgress.ClosePrice.Equal(decimal.RequireFromString("120")) ||
		!result.InProgress.TradeVolume.Equal(decimal.RequireFromString("3")) ||
		!result.InProgress.TradeAmount.Equal(decimal.RequireFromString("320")) {
		t.Fatalf("InProgress = %#v, want ordered two-trade candle", result.InProgress)
	}
}

func TestProcessorKeepsPartitionStateIndependent(t *testing.T) {
	processor := consumer.NewProcessor()
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)

	first, err := processor.Process(tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 1))
	if err != nil {
		t.Fatalf("partition 0 Process() error = %v", err)
	}
	second, err := processor.Process(tradeRecord(t, 1, "KRW-BTC", "100", "1", "BID", tradedAt, 1))
	if err != nil {
		t.Fatalf("partition 1 Process() error = %v", err)
	}

	if first.Skipped() || second.Skipped() {
		t.Fatalf("partition results = %#v, %#v, want independent applied trades", first, second)
	}
}

func TestProcessorRejectsInvalidKafkaTrade(t *testing.T) {
	valid := ingestkafka.TradeMessage{
		AssetID:           "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
		MarketCode:        "KRW-BTC",
		Price:             "100",
		Volume:            "1",
		Side:              "BID",
		TradedAtUnixMilli: 1,
		SequenceID:        1,
	}
	tests := []struct {
		name    string
		key     string
		message ingestkafka.TradeMessage
		value   []byte
	}{
		{name: "손상된 JSON을 거부한다", key: valid.MarketCode, value: []byte(`{"asset_id":`)},
		{name: "필수 필드 누락을 거부한다", key: valid.MarketCode, message: withAssetID(valid, "")},
		{name: "잘못된 매수매도 구분을 거부한다", key: valid.MarketCode, message: withSide(valid, "UNKNOWN")},
		{name: "잘못된 체결 시각을 거부한다", key: valid.MarketCode, message: withTimestamp(valid, 0)},
		{name: "잘못된 체결 순번을 거부한다", key: valid.MarketCode, message: withSequenceID(valid, 0)},
		{name: "마켓 코드와 다른 key를 거부한다", key: "KRW-ETH", message: valid},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			value := test.value
			if value == nil {
				var err error
				value, err = json.Marshal(test.message)
				if err != nil {
					t.Fatalf("Marshal() error = %v", err)
				}
			}

			_, err := consumer.NewProcessor().Process(ingestkafka.Record{
				Topic:     ingestkafka.RawTradeTopic,
				Partition: 0,
				Key:       []byte(test.key),
				Value:     value,
			})
			if !exception.HasCode(err, exception.CodeKafkaInvalidMessage) {
				t.Fatalf("Process() error = %v, want code %s", err, exception.CodeKafkaInvalidMessage)
			}
		})
	}
}

func tradeRecord(
	t testing.TB,
	partition int32,
	marketCode string,
	price string,
	volume string,
	side string,
	tradedAt time.Time,
	sequenceID int64,
) ingestkafka.Record {
	t.Helper()
	message := ingestkafka.TradeMessage{
		AssetID:           "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
		MarketCode:        marketCode,
		Price:             price,
		Volume:            volume,
		Side:              side,
		TradedAtUnixMilli: tradedAt.UnixMilli(),
		SequenceID:        sequenceID,
	}
	value, err := json.Marshal(message)
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}
	return ingestkafka.Record{
		Topic:     ingestkafka.RawTradeTopic,
		Partition: partition,
		Key:       []byte(marketCode),
		Value:     value,
	}
}

func withAssetID(message ingestkafka.TradeMessage, assetID string) ingestkafka.TradeMessage {
	message.AssetID = assetID
	return message
}

func withSide(message ingestkafka.TradeMessage, side string) ingestkafka.TradeMessage {
	message.Side = side
	return message
}

func withTimestamp(message ingestkafka.TradeMessage, timestamp int64) ingestkafka.TradeMessage {
	message.TradedAtUnixMilli = timestamp
	return message
}

func withSequenceID(message ingestkafka.TradeMessage, sequenceID int64) ingestkafka.TradeMessage {
	message.SequenceID = sequenceID
	return message
}
