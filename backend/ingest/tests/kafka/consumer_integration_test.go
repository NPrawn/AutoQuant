//go:build integration

// 실제 Kafka가 필요하다.
// 실행: docker compose -f infra/docker-compose.yml up -d kafka kafka-init
//
//	go test -tags=integration ./tests/kafka -run TestConsumerPollsRawTradeAndAggregates -v
package kafka_test

import (
	"context"
	"encoding/json"
	"fmt"
	"testing"
	"time"

	consumerapp "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/consumer"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

func TestConsumerPollsRawTradeAndAggregates(t *testing.T) {
	testContext, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	marketCode := fmt.Sprintf("KRW-TEST-%d", time.Now().UnixNano())
	message := ingestkafka.TradeMessage{
		AssetID:           "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
		MarketCode:        marketCode,
		Price:             "100",
		Volume:            "2",
		Side:              "BID",
		TradedAtUnixMilli: time.Now().UTC().UnixMilli(),
		SequenceID:        1,
	}
	value, err := json.Marshal(message)
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}

	producer, err := ingestkafka.NewProducer([]string{kafkaAddress})
	if err != nil {
		t.Fatalf("NewProducer() error = %v", err)
	}
	defer producer.Close()
	delivery := make(chan error, 1)
	producer.Produce(testContext, ingestkafka.Record{
		Topic: ingestkafka.RawTradeTopic,
		Key:   []byte(marketCode),
		Value: value,
	}, func(err error) {
		delivery <- err
	})
	select {
	case err := <-delivery:
		if err != nil {
			t.Fatalf("Produce() error = %v", err)
		}
	case <-testContext.Done():
		t.Fatalf("record was not delivered: %v", testContext.Err())
	}
	if err := producer.Flush(testContext); err != nil {
		t.Fatalf("Flush() error = %v", err)
	}

	groupID := fmt.Sprintf("%s-test-%d", ingestkafka.MinuteCandleGroupID, time.Now().UnixNano())
	tradeConsumer, err := ingestkafka.NewConsumer([]string{kafkaAddress}, groupID)
	if err != nil {
		t.Fatalf("NewConsumer() error = %v", err)
	}
	defer tradeConsumer.Close()
	processor := consumerapp.NewProcessor()

	for testContext.Err() == nil {
		records, err := tradeConsumer.Poll(testContext)
		if err != nil {
			t.Fatalf("Poll() error = %v", err)
		}
		for _, record := range records {
			if string(record.Key) != marketCode {
				continue
			}
			if record.Topic != ingestkafka.RawTradeTopic || record.Partition < 0 || record.Offset < 0 {
				t.Fatalf("record metadata = %#v, want tick-raw partition and offset", record)
			}

			processed, err := processor.Process(record)
			if err != nil {
				t.Fatalf("Process() error = %v", err)
			}
			if processed.Result.InProgress.AssetID != message.AssetID ||
				processed.Result.InProgress.ClosePrice.String() != message.Price {
				t.Fatalf("InProgress = %#v, want consumed trade candle", processed.Result.InProgress)
			}
			if !containsPartition(tradeConsumer.TakeAssignedPartitions(), record.Partition) {
				t.Fatalf("assigned partitions do not contain consumed partition %d", record.Partition)
			}
			if err := tradeConsumer.Commit(testContext, []ingestkafka.Record{record}); err != nil {
				t.Fatalf("Commit() error = %v", err)
			}
			tradeConsumer.AllowRebalance()
			return
		}
	}
	t.Fatalf("raw trade was not consumed: %v", testContext.Err())
}

func containsPartition(partitions []int32, want int32) bool {
	for _, partition := range partitions {
		if partition == want {
			return true
		}
	}
	return false
}
