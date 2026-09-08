package candlerollup

import (
	"time"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
)

// Intervals returns the supported derived widths; one-minute input stays unchanged.
func Intervals() []int16 { return []int16{2, 5, 15, 60, 240, 1440} }

func Bucket(openTime time.Time, interval int16) time.Time {
	return openTime.UTC().Truncate(time.Duration(interval) * time.Minute)
}

// Aggregate replaces duplicate source minutes before summing. Missing minutes are
// not synthesized. Input order does not determine the open or close price.
func Aggregate(assetID string, interval int16, start time.Time, minutes []candledomain.MinuteCandle) *candledomain.MinuteCandle {
	end := start.Add(time.Duration(interval) * time.Minute)
	unique := make(map[int64]candledomain.MinuteCandle)
	for _, minute := range minutes {
		if minute.AssetID == assetID && minute.IntervalMinutes == 1 && !minute.OpenTime.Before(start) && minute.OpenTime.Before(end) {
			unique[minute.OpenTime.UnixMilli()] = minute
		}
	}
	var result *candledomain.MinuteCandle
	var first, last time.Time
	for _, minute := range unique {
		if result == nil {
			copy := minute
			copy.IntervalMinutes, copy.OpenTime = interval, start
			result, first, last = &copy, minute.OpenTime, minute.OpenTime
			continue
		}
		if minute.OpenTime.Before(first) {
			result.OpenPrice, first = minute.OpenPrice, minute.OpenTime
		}
		if minute.OpenTime.After(last) {
			result.ClosePrice, last = minute.ClosePrice, minute.OpenTime
		}
		if minute.HighPrice.GreaterThan(result.HighPrice) {
			result.HighPrice = minute.HighPrice
		}
		if minute.LowPrice.LessThan(result.LowPrice) {
			result.LowPrice = minute.LowPrice
		}
		if minute.LastEventTime.After(result.LastEventTime) {
			result.LastEventTime = minute.LastEventTime
		}
		result.TradeVolume = result.TradeVolume.Add(minute.TradeVolume)
		result.TradeAmount = result.TradeAmount.Add(minute.TradeAmount)
	}
	return result
}
