package kafka

import (
	"context"
	"fmt"
	"sync"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/twmb/franz-go/pkg/kgo"
)

const maxPollRecords = 100

// Consumer polls raw trade records without committing offsets.
type Consumer struct {
	client  *kgo.Client
	revoked map[int32]struct{}
	mutex   sync.Mutex
}

func NewConsumer(brokers []string, groupID string) (*Consumer, error) {
	consumer := &Consumer{revoked: make(map[int32]struct{})}
	client, err := kgo.NewClient(
		kgo.SeedBrokers(brokers...),
		kgo.ConsumerGroup(groupID),
		kgo.ConsumeTopics(RawTradeTopic),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtStart()),
		kgo.Balancers(kgo.CooperativeStickyBalancer()),
		kgo.DisableAutoCommit(),
		kgo.BlockRebalanceOnPoll(),
		kgo.OnPartitionsRevoked(consumer.onPartitionsRevoked),
		kgo.OnPartitionsLost(consumer.onPartitionsRevoked),
	)
	if err != nil {
		return nil, exception.Wrap(exception.CodeKafkaConsumeFailed, err)
	}
	consumer.client = client
	return consumer, nil
}

// Poll returns the next fetched records while preserving order within each partition.
func (c *Consumer) Poll(ctx context.Context) ([]Record, error) {
	fetches := c.client.PollRecords(ctx, maxPollRecords)
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

// Commit synchronously commits the highest persisted record supplied for each
// partition. Callers must preserve monotonic partition offsets.
func (c *Consumer) Commit(ctx context.Context, records []Record) error {
	kafkaRecords := make([]*kgo.Record, 0, len(records))
	for _, record := range records {
		kafkaRecords = append(kafkaRecords, &kgo.Record{
			Topic:     record.Topic,
			Partition: record.Partition,
			Offset:    record.Offset,
		})
	}

	if err := c.client.CommitRecords(ctx, kafkaRecords...); err != nil {
		return exception.Wrap(exception.CodeKafkaCommitFailed, err)
	}

	return nil
}

func (c *Consumer) AllowRebalance() {
	c.client.AllowRebalance()
}

// TakeRevokedPartitions returns and clears partitions that lost ownership since
// the previous call.
func (c *Consumer) TakeRevokedPartitions() []int32 {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	partitions := make([]int32, 0, len(c.revoked))
	for partition := range c.revoked {
		partitions = append(partitions, partition)
	}
	clear(c.revoked)

	return partitions
}

func (c *Consumer) onPartitionsRevoked(
	_ context.Context,
	_ *kgo.Client,
	partitions map[string][]int32,
) {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	for _, topicPartitions := range partitions {
		for _, partition := range topicPartitions {
			c.revoked[partition] = struct{}{}
		}
	}
}

func (c *Consumer) Close() {
	c.client.CloseAllowingRebalance()
}
