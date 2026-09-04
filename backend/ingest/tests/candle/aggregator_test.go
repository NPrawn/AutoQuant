package candle_test

import (
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
)

func at(minute, second int) time.Time {
	return time.Date(2026, 9, 4, 10, minute, second, 0, time.UTC)
}

func trade(marketCode string, sequenceID int64, tradedAt time.Time, price, volume string) tradedomain.MarketTrade {
	return tradedomain.MarketTrade{
		AssetID:    "asset-" + marketCode,
		MarketCode: marketCode,
		Price:      price,
		Volume:     volume,
		Side:       tradedomain.SideBid,
		TradedAt:   tradedAt,
		SequenceID: sequenceID,
	}
}

func applyAll(t *testing.T, aggregator *candle.Aggregator, trades []tradedomain.MarketTrade) candle.Result {
	t.Helper()

	var result candle.Result
	for _, applied := range trades {
		var err error
		result, err = aggregator.Apply(applied)
		if err != nil {
			t.Fatalf("Apply(%d) returned error: %v", applied.SequenceID, err)
		}
	}

	return result
}

func TestApplyBuildsCandleFromTrades(t *testing.T) {
	aggregator := candle.New()
	result := applyAll(t, aggregator, []tradedomain.MarketTrade{
		trade("KRW-BTC", 1, at(3, 1), "108800", "0.5"),
		trade("KRW-BTC", 2, at(3, 20), "108850", "0.7"),
		trade("KRW-BTC", 3, at(3, 40), "108700", "0.3"),
	})

	tests := []struct {
		name string
		got  string
		want string
	}{
		{name: "시가는 첫 체결가다", got: result.InProgress.OpenPrice.String(), want: "108800"},
		{name: "고가는 가장 높은 체결가다", got: result.InProgress.HighPrice.String(), want: "108850"},
		{name: "저가는 가장 낮은 체결가다", got: result.InProgress.LowPrice.String(), want: "108700"},
		{name: "종가는 마지막 체결가다", got: result.InProgress.ClosePrice.String(), want: "108700"},
		{name: "거래량은 체결량의 합이다", got: result.InProgress.TradeVolume.String(), want: "1.5"},
		{name: "거래대금은 체결가와 체결량 곱의 합이다", got: result.InProgress.TradeAmount.String(), want: "163205"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if test.got != test.want {
				t.Fatalf("got %s, want %s", test.got, test.want)
			}
		})
	}
}

func TestApplyStartsCandleAtMinuteBoundary(t *testing.T) {
	aggregator := candle.New()
	result := applyAll(t, aggregator, []tradedomain.MarketTrade{
		trade("KRW-BTC", 1, at(3, 47), "108800", "0.5"),
	})

	if got := result.InProgress.OpenTime; !got.Equal(at(3, 0)) {
		t.Fatalf("OpenTime = %s, want %s", got, at(3, 0))
	}
}

func TestApplyClosesPreviousCandleOnNextMinute(t *testing.T) {
	aggregator := candle.New()
	result := applyAll(t, aggregator, []tradedomain.MarketTrade{
		trade("KRW-BTC", 1, at(3, 10), "108800", "0.5"),
		trade("KRW-BTC", 2, at(3, 50), "108900", "0.5"),
		trade("KRW-BTC", 3, at(4, 5), "109000", "0.2"),
	})

	if result.Closed == nil {
		t.Fatalf("Closed = nil, want the candle of minute %s", at(3, 0))
	}
	if got := result.Closed.OpenTime; !got.Equal(at(3, 0)) {
		t.Fatalf("Closed.OpenTime = %s, want %s", got, at(3, 0))
	}
	if got := result.Closed.ClosePrice.String(); got != "108900" {
		t.Fatalf("Closed.ClosePrice = %s, want 108900", got)
	}
	if got := result.InProgress.OpenTime; !got.Equal(at(4, 0)) {
		t.Fatalf("InProgress.OpenTime = %s, want %s", got, at(4, 0))
	}
}

func TestApplySkipsRepeatedSequence(t *testing.T) {
	tests := []struct {
		name       string
		sequenceID int64
	}{
		{name: "같은 시퀀스 체결은 건너뛴다", sequenceID: 10},
		{name: "더 이전 시퀀스 체결은 건너뛴다", sequenceID: 9},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			aggregator := candle.New()
			applyAll(t, aggregator, []tradedomain.MarketTrade{
				trade("KRW-BTC", 10, at(3, 10), "108800", "0.5"),
			})

			result, err := aggregator.Apply(trade("KRW-BTC", test.sequenceID, at(3, 20), "109000", "0.5"))
			if err != nil {
				t.Fatalf("Apply returned error: %v", err)
			}
			if !result.Duplicate {
				t.Fatalf("Duplicate = false, want true")
			}
		})
	}
}

func TestApplyDoesNotInflateCandleOnRepeatedSequence(t *testing.T) {
	aggregator := candle.New()
	applyAll(t, aggregator, []tradedomain.MarketTrade{
		trade("KRW-BTC", 10, at(3, 10), "108800", "0.5"),
	})

	if _, err := aggregator.Apply(trade("KRW-BTC", 10, at(3, 20), "108800", "0.5")); err != nil {
		t.Fatalf("Apply returned error: %v", err)
	}

	closed := aggregator.Sweep(at(5, 0))
	if len(closed) != 1 {
		t.Fatalf("len(Sweep()) = %d, want 1", len(closed))
	}
	if got := closed[0].TradeVolume.String(); got != "0.5" {
		t.Fatalf("TradeVolume = %s, want 0.5", got)
	}
}

func TestApplySkipsTradeOfClosedMinute(t *testing.T) {
	aggregator := candle.New()
	applyAll(t, aggregator, []tradedomain.MarketTrade{
		trade("KRW-BTC", 1, at(3, 10), "108800", "0.5"),
	})

	result, err := aggregator.Apply(trade("KRW-BTC", 2, at(2, 50), "100000", "9"))
	if err != nil {
		t.Fatalf("Apply returned error: %v", err)
	}
	if !result.Stale {
		t.Fatalf("Stale = false, want true")
	}
}

func TestApplyKeepsMarketsIndependent(t *testing.T) {
	aggregator := candle.New()
	applyAll(t, aggregator, []tradedomain.MarketTrade{
		trade("KRW-BTC", 1, at(3, 10), "108800", "0.5"),
		trade("KRW-ETH", 1, at(3, 20), "4200", "2"),
	})

	closed := aggregator.Sweep(at(5, 0))
	if len(closed) != 2 {
		t.Fatalf("len(Sweep()) = %d, want 2", len(closed))
	}

	volumes := map[string]string{}
	for _, candle := range closed {
		volumes[candle.AssetID] = candle.TradeVolume.String()
	}
	if got := volumes["asset-KRW-BTC"]; got != "0.5" {
		t.Fatalf("BTC TradeVolume = %s, want 0.5", got)
	}
	if got := volumes["asset-KRW-ETH"]; got != "2" {
		t.Fatalf("ETH TradeVolume = %s, want 2", got)
	}
}

func TestApplyRejectsMalformedAmount(t *testing.T) {
	tests := []struct {
		name   string
		price  string
		volume string
	}{
		{name: "체결가가 숫자가 아니면 거부한다", price: "108O00", volume: "0.5"},
		{name: "체결량이 숫자가 아니면 거부한다", price: "108800", volume: ""},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			aggregator := candle.New()

			_, err := aggregator.Apply(trade("KRW-BTC", 1, at(3, 10), test.price, test.volume))
			if !exception.HasCode(err, exception.CodeCandleInvalidPayload) {
				t.Fatalf("HasCode(err, %s) = false, want true (err = %v)", exception.CodeCandleInvalidPayload, err)
			}
		})
	}
}

func TestSweepWaitsForCloseDelay(t *testing.T) {
	tests := []struct {
		name       string
		now        time.Time
		wantClosed int
	}{
		{name: "마감 유예 전에는 봉을 확정하지 않는다", now: at(4, 4), wantClosed: 0},
		{name: "마감 유예가 지나면 봉을 확정한다", now: at(4, 5), wantClosed: 1},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			aggregator := candle.New()
			applyAll(t, aggregator, []tradedomain.MarketTrade{
				trade("KRW-BTC", 1, at(3, 30), "108800", "0.5"),
			})

			if got := len(aggregator.Sweep(test.now)); got != test.wantClosed {
				t.Fatalf("len(Sweep(%s)) = %d, want %d", test.now, got, test.wantClosed)
			}
		})
	}
}

func TestSweepClosesCandleOnlyOnce(t *testing.T) {
	aggregator := candle.New()
	applyAll(t, aggregator, []tradedomain.MarketTrade{
		trade("KRW-BTC", 1, at(3, 30), "108800", "0.5"),
	})

	if got := len(aggregator.Sweep(at(5, 0))); got != 1 {
		t.Fatalf("첫 Sweep len = %d, want 1", got)
	}
	if got := len(aggregator.Sweep(at(6, 0))); got != 0 {
		t.Fatalf("두 번째 Sweep len = %d, want 0", got)
	}
}
