package domain

import (
	"time"

	commondomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/domain"
)

// MinuteCandle is a finalized minute candle persisted by the ingest pipeline.
type MinuteCandle struct {
	commondomain.BaseEntity

	// AssetID is the UUID assigned by reference.asset, encoded as a string.
	AssetID         string
	IntervalMinutes int16
	OpenTime        time.Time

	OpenPrice   string
	HighPrice   string
	LowPrice    string
	ClosePrice  string
	TradeVolume string
	TradeAmount string

	LastEventTime time.Time
}
