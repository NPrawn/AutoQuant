package domain

import "time"

// Side identifies whether the market trade was initiated by an ask or a bid.
type Side string

const (
	SideAsk Side = "ASK"
	SideBid Side = "BID"
)

// MarketTrade is a normalized real-time Upbit trade event.
type MarketTrade struct {
	AssetID    string
	MarketCode string
	Price      string
	Volume     string
	Side       Side
	TradedAt   time.Time
	SequenceID int64
}
