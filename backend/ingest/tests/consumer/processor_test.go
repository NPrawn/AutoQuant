package consumer_test

import (
	"encoding/json"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
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

	if !result.Result.InProgress.OpenPrice.Equal(decimal.RequireFromString("100")) ||
		!result.Result.InProgress.HighPrice.Equal(decimal.RequireFromString("120")) ||
		!result.Result.InProgress.ClosePrice.Equal(decimal.RequireFromString("120")) ||
		!result.Result.InProgress.TradeVolume.Equal(decimal.RequireFromString("3")) ||
		!result.Result.InProgress.TradeAmount.Equal(decimal.RequireFromString("320")) {
		t.Fatalf("InProgress = %#v, want ordered two-trade candle", result.Result.InProgress)
	}
}

func TestProcessorKeepsMarketStateIndependent(t *testing.T) {
	processor := consumer.NewProcessor()
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)

	first, err := processor.Process(tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 1))
	if err != nil {
		t.Fatalf("partition 0 Process() error = %v", err)
	}
	second, err := processor.Process(tradeRecord(t, 0, "KRW-ETH", "200", "2", "BID", tradedAt, 1))
	if err != nil {
		t.Fatalf("second market Process() error = %v", err)
	}

	if first.Result.Skipped() || second.Result.Skipped() ||
		first.Checkpoint.MarketCode == second.Checkpoint.MarketCode {
		t.Fatalf("market results = %#v, %#v, want independent applied trades", first, second)
	}
}

func TestProcessorRestoresCheckpointBeforeReplay(t *testing.T) {
	tradedAt := time.Date(2026, time.September, 7, 12, 34, 10, 0, time.UTC)
	firstProcessor := consumer.NewProcessor()
	first, err := firstProcessor.Process(tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 10))
	if err != nil {
		t.Fatalf("first Process() error = %v", err)
	}

	restoredProcessor := consumer.NewProcessor()
	if err := restoredProcessor.Restore([]candle.Checkpoint{*first.Checkpoint}); err != nil {
		t.Fatalf("Restore() error = %v", err)
	}
	replayed, err := restoredProcessor.Process(tradeRecord(t, 0, "KRW-BTC", "100", "1", "BID", tradedAt, 10))
	if err != nil {
		t.Fatalf("replayed Process() error = %v", err)
	}
	if !replayed.Result.Duplicate {
		t.Fatalf("Duplicate = false, want true")
	}

	next, err := restoredProcessor.Process(tradeRecord(t, 0, "KRW-BTC", "120", "1", "BID", tradedAt.Add(time.Second), 11))
	if err != nil {
		t.Fatalf("next Process() error = %v", err)
	}
	if got := next.Result.InProgress.TradeVolume.String(); got != "2" {
		t.Fatalf("TradeVolume = %s, want 2", got)
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
