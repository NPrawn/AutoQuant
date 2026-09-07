package redis

const (
	// RealtimeTradeChannel carries normalized trades independently of Kafka delivery.
	RealtimeTradeChannel = "trade:realtime:updates"

	// LiveMinuteCandleChannel carries the latest durable one-minute candle state.
	LiveMinuteCandleChannel = "candle:1m:live:updates"
)
