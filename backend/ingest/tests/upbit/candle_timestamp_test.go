package upbit_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/upbit"
)

func TestCandleClientSeparatesBucketFromStoredTimestamp(t *testing.T) {
	payload, err := os.ReadFile("testdata/minute_candle_delayed_timestamp.json")
	if err != nil {
		t.Fatal(err)
	}
	start := time.Date(2026, 9, 9, 0, 25, 0, 0, time.UTC)
	end := start.Add(time.Minute)
	tests := []struct {
		name    string
		old     string
		value   string
		wantAt  time.Time
		wantErr bool
	}{
		{name: "실제_응답의_종료_39ms_후_저장_시각을_그대로_보존한다", wantAt: end.Add(39 * time.Millisecond)},
		{name: "종료_경계의_저장_시각을_허용한다", old: "1788913560039", value: strconv.FormatInt(end.UnixMilli(), 10), wantAt: end},
		{name: "분_내부의_저장_시각을_허용한다", old: "1788913560039", value: strconv.FormatInt(end.Add(-time.Millisecond).UnixMilli(), 10), wantAt: end.Add(-time.Millisecond)},
		{name: "임의의_밀리초_유예시간을_두지_않는다", old: "1788913560039", value: strconv.FormatInt(end.Add(2*time.Second).UnixMilli(), 10), wantAt: end.Add(2 * time.Second)},
		{name: "시작보다_이른_저장_시각은_거부한다", old: "1788913560039", value: strconv.FormatInt(start.Add(-time.Millisecond).UnixMilli(), 10), wantErr: true},
		{name: "0인_저장_시각은_거부한다", old: "1788913560039", value: "0", wantErr: true},
		{name: "누락된_저장_시각은_거부한다", old: `"timestamp": 1788913560039,`, value: "", wantErr: true},
		{name: "조회_종료와_같은_시작_시각은_거부한다", old: "2026-09-09T00:25:00", value: "2026-09-09T00:26:00", wantErr: true},
		{name: "분_경계가_아닌_시작_시각은_거부한다", old: "2026-09-09T00:25:00", value: "2026-09-09T00:25:01", wantErr: true},
		{name: "다른_종목은_거부한다", old: "KRW-ATH", value: "KRW-BTC", wantErr: true},
		{name: "다른_간격은_거부한다", old: `"unit": 1`, value: `"unit": 5`, wantErr: true},
		{name: "잘못된_가격_범위는_거부한다", old: `"high_price": 6.92`, value: `"high_price": 1`, wantErr: true},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			body := string(payload)
			if test.old != "" {
				body = strings.Replace(body, test.old, test.value, 1)
			}
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				w.Header().Set("Content-Type", "application/json")
				if _, err := w.Write([]byte(body)); err != nil {
					t.Errorf("Write() error = %v", err)
				}
			}))
			defer server.Close()
			client, err := upbit.NewCandleClient(server.URL, time.Second, 0)
			if err != nil {
				t.Fatal(err)
			}
			candles, err := client.ListMinuteCandles(context.Background(), "KRW-ATH", end, 200)
			if test.wantErr {
				if !exception.HasCode(err, exception.CodeUpbitInvalidResponse) {
					t.Fatalf("got %v, want %s", err, exception.CodeUpbitInvalidResponse)
				}
				return
			}
			if err != nil || len(candles) != 1 {
				t.Fatalf("got %d candles / %v, want 1 / nil", len(candles), err)
			}
			if !candles[0].OpenTime.Equal(start) || !candles[0].LastEventTime.Equal(test.wantAt) {
				t.Fatalf("got open/stored %v/%v, want %v/%v", candles[0].OpenTime, candles[0].LastEventTime, start, test.wantAt)
			}
		})
	}
}
