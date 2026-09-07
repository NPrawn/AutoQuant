package kafka

const (
	CollectorShardTopic          = "collector-shards"
	CollectorGroupID             = "upbit-trade-collectors"
	CollectorShardPartitionCount = 6
	RawTradeTopic                = "tick-raw"
	MinuteCandleGroupID          = "minute-candle-aggregators"
)
