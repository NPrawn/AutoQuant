package kafka

import (
	"context"
	"fmt"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/twmb/franz-go/pkg/kgo"
)

type PartitionCallbacks struct {
	Assigned func([]int32)
	Revoked  func([]int32)
	Lost     func([]int32)
}

// GroupMember maintains Kafka Consumer Group membership without processing records.
type GroupMember struct {
	client *kgo.Client
}

func NewGroupMember(
	brokers []string,
	topic string,
	groupID string,
	callbacks PartitionCallbacks,
) (*GroupMember, error) {
	client, err := kgo.NewClient(
		kgo.SeedBrokers(brokers...),
		kgo.ConsumerGroup(groupID),
		kgo.ConsumeTopics(topic),
		kgo.Balancers(kgo.CooperativeStickyBalancer()),
		kgo.DisableAutoCommit(),
		kgo.OnPartitionsAssigned(partitionCallback(topic, callbacks.Assigned)),
		kgo.OnPartitionsRevoked(partitionCallback(topic, callbacks.Revoked)),
		kgo.OnPartitionsLost(partitionCallback(topic, callbacks.Lost)),
	)
	if err != nil {
		return nil, exception.Wrap(exception.CodeKafkaConsumeFailed, err)
	}
	return &GroupMember{client: client}, nil
}

// Run polls the empty coordination topic so Kafka keeps the group membership alive.
func (m *GroupMember) Run(ctx context.Context) error {
	if err := m.client.Ping(ctx); err != nil {
		return exception.Wrap(exception.CodeKafkaConsumeFailed, fmt.Errorf("ping broker: %w", err))
	}

	for {
		fetches := m.client.PollFetches(ctx)
		if ctx.Err() != nil || fetches.IsClientClosed() {
			return nil
		}

		var fetchError error
		fetches.EachError(func(topic string, partition int32, err error) {
			if fetchError == nil {
				fetchError = fmt.Errorf("fetch topic %s partition %d: %w", topic, partition, err)
			}
		})
		if fetchError != nil {
			return exception.Wrap(exception.CodeKafkaConsumeFailed, fetchError)
		}
	}
}

// Close leaves the consumer group and closes Kafka connections.
func (m *GroupMember) Close() {
	m.client.Close()
}

func partitionCallback(
	topic string,
	callback func([]int32),
) func(context.Context, *kgo.Client, map[string][]int32) {
	return func(_ context.Context, _ *kgo.Client, partitions map[string][]int32) {
		assigned := partitions[topic]
		if callback == nil || len(assigned) == 0 {
			return
		}
		callback(assigned)
	}
}
