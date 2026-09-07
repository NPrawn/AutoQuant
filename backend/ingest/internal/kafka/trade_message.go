package kafka

// TradeMessage is the Kafka contract for a normalized market trade.
type TradeMessage struct {
	AssetID           string `json:"asset_id"`
	MarketCode        string `json:"market_code"`
	Price             string `json:"price"`
	Volume            string `json:"volume"`
	Side              string `json:"side"`
	TradedAtUnixMilli int64  `json:"traded_at_unix_milli"`
	SequenceID        int64  `json:"sequence_id"`
}
