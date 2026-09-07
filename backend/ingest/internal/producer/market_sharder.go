package producer

import (
	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
)

const (
	fnvOffset32 = uint32(2166136261)
	fnvPrime32  = uint32(16777619)
)

// ShardForMarket returns the stable Collector shard for a market code.
func ShardForMarket(marketCode string) int32 {
	hash := fnvOffset32
	for index := 0; index < len(marketCode); index++ {
		hash ^= uint32(marketCode[index])
		hash *= fnvPrime32
	}
	return int32(hash % ingestkafka.CollectorShardPartitionCount)
}

// TargetsForShards filters collection targets assigned to the owned shards.
func TargetsForShards(
	targets []assetdomain.MarketDataCollectionTarget,
	ownedShards []int32,
) []assetdomain.MarketDataCollectionTarget {
	owned := make(map[int32]struct{}, len(ownedShards))
	for _, shard := range ownedShards {
		owned[shard] = struct{}{}
	}

	assigned := make([]assetdomain.MarketDataCollectionTarget, 0, len(targets))
	for _, target := range targets {
		if _, exists := owned[ShardForMarket(target.MarketCode)]; exists {
			assigned = append(assigned, target)
		}
	}
	return assigned
}
