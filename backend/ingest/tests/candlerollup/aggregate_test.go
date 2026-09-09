package candlerollup_test

import (
	"testing"
	"time"

	"github.com/shopspring/decimal"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlerollup"
)

func minute(asset string, start time.Time, price, volume int64) candledomain.MinuteCandle {
	return candledomain.MinuteCandle{AssetID: asset, IntervalMinutes: 1, OpenTime: start,
		OpenPrice: decimal.NewFromInt(price), HighPrice: decimal.NewFromInt(price), LowPrice: decimal.NewFromInt(price),
		ClosePrice: decimal.NewFromInt(price), TradeVolume: decimal.NewFromInt(volume),
		TradeAmount: decimal.NewFromInt(price * volume), LastEventTime: start.Add(time.Second)}
}

func TestAggregate(t *testing.T) {
	start := time.Date(2026, 9, 8, 0, 0, 0, 0, time.UTC)
	for _, interval := range candlerollup.Intervals() {
		t.Run("간격_"+(time.Duration(interval)*time.Minute).String(), func(t *testing.T) {
			first := minute("BTC", start, 100, 1)
			last := minute("BTC", start.Add(time.Duration(interval-1)*time.Minute), 120, 2)
			corrected := first
			corrected.TradeVolume = decimal.NewFromInt(3)
			corrected.TradeAmount = decimal.NewFromInt(300)
			result := candlerollup.Aggregate("BTC", interval, start, []candledomain.MinuteCandle{
				last, first, minute("ETH", start, 1000, 100), corrected,
				minute("BTC", start.Add(time.Duration(interval)*time.Minute), 1000, 100),
			})
			if result == nil || result.IntervalMinutes != interval || !result.OpenTime.Equal(start) ||
				result.OpenPrice.String() != "100" || result.ClosePrice.String() != "120" || result.HighPrice.String() != "120" ||
				result.LowPrice.String() != "100" || result.TradeVolume.String() != "5" || result.TradeAmount.String() != "540" {
				t.Fatalf("got %+v, want isolated ordered OHLC, replaced volume=5 amount=540", result)
			}
		})
	}
}

func TestBucketAndEmptyMinutes(t *testing.T) {
	t.Run("일봉은_KST09시_UTC자정부터_시작한다", func(t *testing.T) {
		local := time.Date(2026, 9, 8, 8, 59, 0, 0, time.FixedZone("KST", 9*3600))
		want := time.Date(2026, 9, 7, 0, 0, 0, 0, time.UTC)
		if got := candlerollup.Bucket(local, 1440); !got.Equal(want) {
			t.Fatalf("got %v, want %v", got, want)
		}
	})
	t.Run("무거래_구간은_봉을_만들지_않는다", func(t *testing.T) {
		if got := candlerollup.Aggregate("BTC", 5, time.Now(), nil); got != nil {
			t.Fatalf("got %v, want nil", got)
		}
	})
}
