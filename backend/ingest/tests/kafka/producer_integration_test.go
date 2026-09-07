//go:build integration

// 실제 Kafka가 필요하다.
// 실행: docker compose -f infra/docker-compose.yml up -d kafka kafka-init
//
//	go test -tags=integration ./tests/kafka -run TestProducerPublishesRecordsWithSameKeyInOrder -v
package kafka_test

import (
	"context"
	"fmt"
	"testing"
	"time"

	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"

	"github.com/twmb/franz-go/pkg/kgo"
)

func TestProducerPublishesRecordsWithSameKeyInOrder(t *testing.T) {
	testContext, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	consumer, err := kgo.NewClient(
		kgo.SeedBrokers(kafkaAddress),
		kgo.ConsumerGroup(fmt.Sprintf("trade-publisher-test-%d", time.Now().UnixNano())),
		kgo.ConsumeTopics(ingestkafka.RawTradeTopic),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtEnd()),
	)
	if err != nil {
		t.Fatalf("create consumer: %v", err)
	}
	defer consumer.Close()
	if err := consumer.Ping(testContext); err != nil {
		t.Fatalf("ping consumer: %v", err)
	}

	assignmentContext, stopAssignment := context.WithTimeout(testContext, 2*time.Second)
	consumer.PollFetches(assignmentContext)
	stopAssignment()

	producer, err := ingestkafka.NewProducer([]string{kafkaAddress})
	if err != nil {
		t.Fatalf("NewProducer() error = %v", err)
	}
	defer producer.Close()

	key := fmt.Sprintf("KRW-TEST-%d", time.Now().UnixNano())
	wantValues := []string{`{"sequence_id":1}`, `{"sequence_id":2}`}
	delivery := make(chan error, len(wantValues))
	for _, value := range wantValues {
		producer.Produce(testContext, ingestkafka.Record{
			Topic: ingestkafka.RawTradeTopic,
			Key:   []byte(key),
			Value: []byte(value),
		}, func(err error) {
			delivery <- err
		})
	}

	for range wantValues {
		select {
		case err := <-delivery:
			if err != nil {
				t.Fatalf("Produce() error = %v", err)
			}
		case <-testContext.Done():
			t.Fatalf("record was not delivered: %v", testContext.Err())
		}
	}
	if err := producer.Flush(testContext); err != nil {
		t.Fatalf("Flush() error = %v", err)
	}

	partition := int32(-1)
	var gotValues []string
	for testContext.Err() == nil {
		fetches := consumer.PollFetches(testContext)
		if err := fetches.Err(); err != nil {
			t.Fatalf("PollFetches() error = %v", err)
		}
		for _, record := range fetches.Records() {
			if string(record.Key) == key {
				if partition == -1 {
					partition = record.Partition
				}
				if record.Partition != partition {
					t.Fatalf("record partition = %d, want %d", record.Partition, partition)
				}
				gotValues = append(gotValues, string(record.Value))
				if len(gotValues) == len(wantValues) {
					for index := range wantValues {
						if gotValues[index] != wantValues[index] {
							t.Fatalf("record values = %v, want %v", gotValues, wantValues)
						}
					}
					return
				}
			}
		}
	}
	t.Fatalf("record was not consumed: %v", testContext.Err())
}
