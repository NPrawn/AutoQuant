package producer

import (
	"context"
	"log/slog"
	"sort"
	"sync"

	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

// ShardCoordinator manages Kafka partition assignments as Collector shard ownership.
type ShardCoordinator struct {
	member  *ingestkafka.GroupMember
	mu      sync.RWMutex
	owned   map[int32]struct{}
	changes chan []int32
}

func NewShardCoordinator(brokers []string) (*ShardCoordinator, error) {
	coordinator := &ShardCoordinator{
		owned:   make(map[int32]struct{}),
		changes: make(chan []int32, 1),
	}

	member, err := ingestkafka.NewGroupMember(
		brokers,
		ingestkafka.CollectorShardTopic,
		ingestkafka.CollectorGroupID,
		ingestkafka.PartitionCallbacks{
			Assigned: coordinator.assign,
			Revoked:  coordinator.revoke,
			Lost:     coordinator.lose,
		},
	)
	if err != nil {
		return nil, err
	}
	coordinator.member = member
	return coordinator, nil
}

func (c *ShardCoordinator) Run(ctx context.Context) error {
	return c.member.Run(ctx)
}

// AssignmentChanges reports the latest complete shard ownership snapshot.
func (c *ShardCoordinator) AssignmentChanges() <-chan []int32 {
	return c.changes
}

// OwnedShards returns a sorted ownership snapshot.
func (c *ShardCoordinator) OwnedShards() []int32 {
	c.mu.RLock()
	defer c.mu.RUnlock()

	shards := make([]int32, 0, len(c.owned))
	for shard := range c.owned {
		shards = append(shards, shard)
	}
	sort.Slice(shards, func(left, right int) bool {
		return shards[left] < shards[right]
	})
	return shards
}

func (c *ShardCoordinator) Close() {
	c.member.Close()
}

func (c *ShardCoordinator) assign(shards []int32) {
	c.mu.Lock()
	for _, shard := range shards {
		c.owned[shard] = struct{}{}
	}
	c.mu.Unlock()
	c.reportAssignment("assigned", shards)
}

func (c *ShardCoordinator) revoke(shards []int32) {
	c.remove(shards)
	c.reportAssignment("revoked", shards)
}

func (c *ShardCoordinator) lose(shards []int32) {
	c.remove(shards)
	c.reportAssignment("lost", shards)
}

func (c *ShardCoordinator) remove(shards []int32) {
	c.mu.Lock()
	defer c.mu.Unlock()
	for _, shard := range shards {
		delete(c.owned, shard)
	}
}

func (c *ShardCoordinator) reportAssignment(event string, changed []int32) {
	owned := c.OwnedShards()
	changed = append([]int32(nil), changed...)
	sort.Slice(changed, func(left, right int) bool {
		return changed[left] < changed[right]
	})
	slog.Info(
		"collector shard assignment changed",
		"event", event,
		"topic", ingestkafka.CollectorShardTopic,
		"group_id", ingestkafka.CollectorGroupID,
		"changed_shards", changed,
		"owned_shards", owned,
	)

	select {
	case c.changes <- owned:
	default:
		select {
		case <-c.changes:
		default:
		}
		select {
		case c.changes <- owned:
		default:
		}
	}
}
