package domain

import "time"

// MarketDataCollectionTarget identifies an enabled Upbit market stream.
type MarketDataCollectionTarget struct {
	AssetID                 string
	MarketCode              string
	TradingSupportStartedAt *time.Time
}
