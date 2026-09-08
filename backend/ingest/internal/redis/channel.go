package redis

import "fmt"

const (
	// RealtimeTradeChannel carries normalized trades independently of Kafka delivery.
	RealtimeTradeChannel = "trade:realtime:updates"

	// LiveMinuteCandleChannel carries the latest durable one-minute candle state.
	LiveMinuteCandleChannel = "candle:1m:live:updates"
)

func LiveCandleChannel(intervalMinutes int16) string {
	if intervalMinutes == 1 {
		return LiveMinuteCandleChannel
	}
	return fmt.Sprintf("candle:%dm:live:updates", intervalMinutes)
}
