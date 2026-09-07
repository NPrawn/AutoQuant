package candlesync

import "time"

const IntervalMinutes = int16(1)

// State records one continuous Upbit-verified time range for an asset.
// The verified range uses [VerifiedFrom, VerifiedTo) semantics.
type State struct {
	AssetID           string
	MarketCode        string
	IntervalMinutes   int16
	HistoryTargetFrom time.Time
	VerifiedFrom      time.Time
	VerifiedTo        time.Time
	HistoryUpdatedAt  *time.Time
}

type Direction string

const (
	DirectionHistory Direction = "HISTORY"
	DirectionLatest  Direction = "LATEST"
)
