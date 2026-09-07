package upbit_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/upbit"

	"github.com/gorilla/websocket"
)

type subscriptionItem struct {
	Type         string   `json:"type"`
	Codes        []string `json:"codes"`
	OnlyRealtime bool     `json:"is_only_realtime"`
}

func TestDecodeTrade(t *testing.T) {
	validPayload := readTradeFixture(t)
	tests := []struct {
		name    string
		payload []byte
		wantErr bool
	}{
		{name: "실시간 체결을 파싱한다", payload: validPayload},
		{name: "손상된 JSON을 거부한다", payload: []byte(`{"type":`), wantErr: true},
		{name: "필수 필드가 없으면 거부한다", payload: []byte(`{"type":"trade","code":"KRW-BTC"}`), wantErr: true},
		{name: "잘못된 매수매도 구분을 거부한다", payload: []byte(`{"type":"trade","code":"KRW-BTC","trade_price":1,"trade_volume":1,"ask_bid":"UNKNOWN","trade_timestamp":1,"sequential_id":1}`), wantErr: true},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			message, err := upbit.DecodeTrade(test.payload)
			if test.wantErr {
				if !exception.HasCode(err, exception.CodeUpbitInvalidResponse) {
					t.Fatalf("error = %v, want code %s", err, exception.CodeUpbitInvalidResponse)
				}
				return
			}
			if err != nil {
				t.Fatalf("DecodeTrade() error = %v", err)
			}
			if message.Code != "KRW-BTC" {
				t.Fatalf("Code = %q, want %q", message.Code, "KRW-BTC")
			}
			if message.TradePrice.String() != "159324000.00000000" {
				t.Fatalf("TradePrice = %q, want %q", message.TradePrice, "159324000.00000000")
			}
		})
	}
}

func TestCandleClientLoadsMinuteCandles(t *testing.T) {
	payload, err := os.ReadFile("testdata/minute_candles.json")
	if err != nil {
		t.Fatal(err)
	}
	requests := make(chan *http.Request, 1)
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		requests <- request.Clone(request.Context())
		writer.Header().Set("Content-Type", "application/json")
		if _, err := writer.Write(payload); err != nil {
			t.Errorf("Write() error = %v", err)
		}
	}))
	defer server.Close()

	client, err := upbit.NewCandleClient(server.URL, time.Second, 0)
	if err != nil {
		t.Fatalf("NewCandleClient() error = %v", err)
	}
	to := time.Date(2026, 9, 7, 3, 9, 0, 0, time.UTC)
	candles, err := client.ListMinuteCandles(context.Background(), "KRW-BTC", to, 200)
	if err != nil {
		t.Fatalf("ListMinuteCandles() error = %v", err)
	}

	request := <-requests
	if request.URL.Path != "/v1/candles/minutes/1" {
		t.Fatalf("request path = %q, want /v1/candles/minutes/1", request.URL.Path)
	}
	if request.URL.Query().Get("market") != "KRW-BTC" || request.URL.Query().Get("count") != "200" {
		t.Fatalf("request query = %v, want market and count", request.URL.Query())
	}
	if len(candles) != 2 {
		t.Fatalf("candle count = %d, want 2", len(candles))
	}
	if candles[0].OpenPrice.String() != "150000000.12345678" {
		t.Fatalf("OpenPrice = %s, want exact decimal", candles[0].OpenPrice)
	}
	if !candles[0].OpenTime.Equal(time.Date(2026, 9, 7, 3, 8, 0, 0, time.UTC)) {
		t.Fatalf("OpenTime = %v, want UTC candle time", candles[0].OpenTime)
	}
}

func TestCandleClientRetriesTemporaryFailure(t *testing.T) {
	var requestCount atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		if requestCount.Add(1) == 1 {
			writer.WriteHeader(http.StatusInternalServerError)
			return
		}
		writer.Header().Set("Content-Type", "application/json")
		if _, err := writer.Write([]byte("[]")); err != nil {
			t.Errorf("Write() error = %v", err)
		}
	}))
	defer server.Close()

	client, err := upbit.NewCandleClient(server.URL, time.Second, 0)
	if err != nil {
		t.Fatalf("NewCandleClient() error = %v", err)
	}
	_, err = client.ListMinuteCandles(
		context.Background(),
		"KRW-BTC",
		time.Date(2026, 9, 7, 3, 9, 0, 0, time.UTC),
		200,
	)
	if err != nil {
		t.Fatalf("ListMinuteCandles() error = %v", err)
	}
	if got := requestCount.Load(); got != 2 {
		t.Fatalf("request count = %d, want 2", got)
	}
}

func TestCandleClientRejectsInvalidCandle(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.Header().Set("Content-Type", "application/json")
		if _, err := writer.Write([]byte(`[{"market":"KRW-ETH","unit":1}]`)); err != nil {
			t.Errorf("Write() error = %v", err)
		}
	}))
	defer server.Close()

	client, err := upbit.NewCandleClient(server.URL, time.Second, 0)
	if err != nil {
		t.Fatalf("NewCandleClient() error = %v", err)
	}
	_, err = client.ListMinuteCandles(
		context.Background(),
		"KRW-BTC",
		time.Date(2026, 9, 7, 3, 9, 0, 0, time.UTC),
		200,
	)
	if !exception.HasCode(err, exception.CodeUpbitInvalidResponse) {
		t.Fatalf("error = %v, want code %s", err, exception.CodeUpbitInvalidResponse)
	}
}

func TestClientReceivesTradeAndUpdatesSubscription(t *testing.T) {
	payload := readTradeFixture(t)
	subscriptions := make(chan []subscriptionItem, 2)
	serverErrors := make(chan error, 1)
	server := newWebSocketServer(t, func(connection *websocket.Conn) {
		defer connection.Close()
		for index := 0; index < 2; index++ {
			var request []subscriptionItem
			if err := connection.ReadJSON(&request); err != nil {
				serverErrors <- err
				return
			}
			subscriptions <- request
			if index == 0 {
				if err := connection.WriteMessage(websocket.TextMessage, payload); err != nil {
					serverErrors <- err
					return
				}
			}
		}
		connection.ReadMessage()
	})
	defer server.Close()

	client := upbit.NewClient(webSocketURL(server.URL))
	updates := make(chan []assetdomain.MarketDataCollectionTarget, 2)
	trades := make(chan tradedomain.MarketTrade, 1)
	bitcoin := assetdomain.MarketDataCollectionTarget{AssetID: "asset-1", MarketCode: "KRW-BTC"}
	updates <- []assetdomain.MarketDataCollectionTarget{bitcoin}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	clientErrors := make(chan error, 1)
	go func() { clientErrors <- client.Run(ctx, updates, trades) }()

	assertSubscribedCodes(t, receiveSubscription(t, ctx, subscriptions, serverErrors), []string{"KRW-BTC"})
	select {
	case trade := <-trades:
		if trade.AssetID != "asset-1" || trade.Price != "159324000.00000000" {
			t.Fatalf("trade = %#v, want normalized KRW-BTC trade", trade)
		}
	case <-ctx.Done():
		t.Fatal("trade was not received")
	}

	updates <- []assetdomain.MarketDataCollectionTarget{
		bitcoin,
		{AssetID: "asset-2", MarketCode: "KRW-ETH"},
	}
	assertSubscribedCodes(t, receiveSubscription(t, ctx, subscriptions, serverErrors), []string{"KRW-BTC", "KRW-ETH"})

	cancel()
	if err := <-clientErrors; err != nil {
		t.Fatalf("Run() error = %v", err)
	}
}

func TestClientReconnectsWithCurrentSubscription(t *testing.T) {
	var connectionCount atomic.Int32
	reconnected := make(chan []subscriptionItem, 1)
	serverErrors := make(chan error, 1)
	server := newWebSocketServer(t, func(connection *websocket.Conn) {
		defer connection.Close()
		var request []subscriptionItem
		if err := connection.ReadJSON(&request); err != nil {
			serverErrors <- err
			return
		}
		if connectionCount.Add(1) == 1 {
			return
		}
		reconnected <- request
		connection.ReadMessage()
	})
	defer server.Close()

	client := upbit.NewClient(webSocketURL(server.URL))
	updates := make(chan []assetdomain.MarketDataCollectionTarget, 1)
	trades := make(chan tradedomain.MarketTrade, 1)
	updates <- []assetdomain.MarketDataCollectionTarget{{AssetID: "asset-1", MarketCode: "KRW-BTC"}}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	clientErrors := make(chan error, 1)
	go func() { clientErrors <- client.Run(ctx, updates, trades) }()

	select {
	case request := <-reconnected:
		assertSubscribedCodes(t, request, []string{"KRW-BTC"})
	case err := <-serverErrors:
		t.Fatalf("server error = %v", err)
	case <-ctx.Done():
		t.Fatal("client did not reconnect")
	}

	cancel()
	if err := <-clientErrors; err != nil {
		t.Fatalf("Run() error = %v", err)
	}
}

func BenchmarkDecodeTrade(b *testing.B) {
	payload, err := os.ReadFile("testdata/trade.json")
	if err != nil {
		b.Fatal(err)
	}
	b.ReportAllocs()
	b.ResetTimer()
	for range b.N {
		if _, err := upbit.DecodeTrade(payload); err != nil {
			b.Fatal(err)
		}
	}
}

func newWebSocketServer(t *testing.T, handle func(*websocket.Conn)) *httptest.Server {
	t.Helper()
	upgrader := websocket.Upgrader{CheckOrigin: func(*http.Request) bool { return true }}
	return httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		connection, err := upgrader.Upgrade(writer, request, nil)
		if err != nil {
			t.Errorf("Upgrade() error = %v", err)
			return
		}
		handle(connection)
	}))
}

func webSocketURL(serverURL string) string {
	return "ws" + strings.TrimPrefix(serverURL, "http")
}

func receiveSubscription(
	t *testing.T,
	ctx context.Context,
	subscriptions <-chan []subscriptionItem,
	serverErrors <-chan error,
) []subscriptionItem {
	t.Helper()
	select {
	case subscription := <-subscriptions:
		return subscription
	case err := <-serverErrors:
		t.Fatalf("server error = %v", err)
	case <-ctx.Done():
		t.Fatal("subscription was not received")
	}
	return nil
}

func assertSubscribedCodes(t *testing.T, request []subscriptionItem, want []string) {
	t.Helper()
	if len(request) != 3 {
		t.Fatalf("subscription length = %d, want 3", len(request))
	}
	if !reflect.DeepEqual(request[1].Codes, want) {
		t.Fatalf("codes = %v, want %v", request[1].Codes, want)
	}
	if request[1].Type != "trade" || !request[1].OnlyRealtime {
		t.Fatalf("trade request = %#v, want realtime trade", request[1])
	}
}

func readTradeFixture(t testing.TB) []byte {
	t.Helper()
	payload, err := os.ReadFile("testdata/trade.json")
	if err != nil {
		t.Fatal(err)
	}
	return payload
}
