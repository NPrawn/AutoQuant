package domain

import (
	"time"

	commondomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/shopspring/decimal"
)

// MinuteCandle is a finalized minute candle persisted by the ingest pipeline.
type MinuteCandle struct {
	commondomain.BaseEntity

	// AssetID is the UUID assigned by reference.asset, encoded as a string.
	AssetID         string
	IntervalMinutes int16
	OpenTime        time.Time

	OpenPrice   decimal.Decimal
	HighPrice   decimal.Decimal
	LowPrice    decimal.Decimal
	ClosePrice  decimal.Decimal
	TradeVolume decimal.Decimal
	TradeAmount decimal.Decimal

	// LastEventTime preserves the source time: trade time for live aggregation,
	// last tick storage time for REST corrections (which may exceed the bucket end).
	// Bucket membership is determined only by OpenTime and IntervalMinutes.
	LastEventTime time.Time
}

// ParseAmount converts a contract string into a decimal amount.
// Contract boundaries such as Kafka messages and Redis values carry amounts as
// strings to avoid precision loss, so callers convert them at the domain edge.
func ParseAmount(value string) (decimal.Decimal, error) {
	parsed, err := decimal.NewFromString(value)
	if err != nil {
		return decimal.Decimal{}, exception.Wrap(exception.CodeCandleInvalidPayload, err)
	}

	return parsed, nil
}
