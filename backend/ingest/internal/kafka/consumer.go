package kafka

import (
	"context"
	"fmt"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/twmb/franz-go/pkg/kgo"
)

// Consumer polls raw trade records without committing offsets.
type Consumer struct {
	client *kgo.Client
}

func NewConsumer(brokers []string) (*Consumer, error) {
	client, err := kgo.NewClient(
		kgo.SeedBrokers(brokers...),
		kgo.ConsumerGroup(MinuteCandleGroupID),
		kgo.ConsumeTopics(RawTradeTopic),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtStart()),
		kgo.Balancers(kgo.CooperativeStickyBalancer()),
		kgo.DisableAutoCommit(),
	)
	if err != nil {
		return nil, exception.Wrap(exception.CodeKafkaConsumeFailed, err)
	}
	return &Consumer{client: client}, nil
}

// Poll returns the next fetched records while preserving order within each partition.
func (c *Consumer) Poll(ctx context.Context) ([]Record, error) {
	fetches := c.client.PollFetches(ctx)
	if ctx.Err() != nil || fetches.IsClientClosed() {
		return nil, nil
	}

	var fetchError error
	fetches.EachError(func(topic string, partition int32, err error) {
		if fetchError == nil {
			fetchError = fmt.Errorf("fetch topic %s partition %d: %w", topic, partition, err)
		}
	})
	if fetchError != nil {
		return nil, exception.Wrap(exception.CodeKafkaConsumeFailed, fetchError)
	}

	records := make([]Record, 0, fetches.NumRecords())
	for _, record := range fetches.Records() {
		records = append(records, Record{
			Topic:     record.Topic,
			Partition: record.Partition,
			Offset:    record.Offset,
			Key:       record.Key,
			Value:     record.Value,
		})
	}
	return records, nil
}

func (c *Consumer) Close() {
	c.client.Close()
}
