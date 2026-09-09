package upbit

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"sync"
	"time"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/shopspring/decimal"
)

const (
	maximumCandleCount     = 200
	maximumResponseBytes   = 2 << 20
	maximumRequestAttempts = 3
)

type minuteCandleResponse struct {
	Market               string      `json:"market"`
	CandleDateTimeUTC    string      `json:"candle_date_time_utc"`
	OpeningPrice         json.Number `json:"opening_price"`
	HighPrice            json.Number `json:"high_price"`
	LowPrice             json.Number `json:"low_price"`
	TradePrice           json.Number `json:"trade_price"`
	Timestamp            int64       `json:"timestamp"`
	CandleAccTradePrice  json.Number `json:"candle_acc_trade_price"`
	CandleAccTradeVolume json.Number `json:"candle_acc_trade_volume"`
	Unit                 int16       `json:"unit"`
}

// CandleClient loads finalized one-minute candles from the Upbit REST API.
type CandleClient struct {
	baseURL         *url.URL
	httpClient      *http.Client
	requestInterval time.Duration
	requestMutex    sync.Mutex
	lastRequestAt   time.Time
}

func NewCandleClient(
	baseURL string,
	timeout time.Duration,
	requestInterval time.Duration,
) (*CandleClient, error) {
	parsedURL, err := url.Parse(baseURL)
	if err != nil || parsedURL.Scheme == "" || parsedURL.Host == "" {
		return nil, fmt.Errorf("invalid Upbit REST base URL")
	}
	if timeout <= 0 || requestInterval < 0 {
		return nil, fmt.Errorf("invalid Upbit REST timing configuration")
	}

	return &CandleClient{
		baseURL:         parsedURL,
		httpClient:      &http.Client{Timeout: timeout},
		requestInterval: requestInterval,
	}, nil
}

func (c *CandleClient) ListMinuteCandles(
	ctx context.Context,
	marketCode string,
	to time.Time,
	count int,
) ([]candledomain.MinuteCandle, error) {
	if marketCode == "" || to.IsZero() || count <= 0 || count > maximumCandleCount {
		return nil, exception.New(exception.CodeCandleInvalidPayload)
	}

	var lastError error
	for attempt := 0; attempt < maximumRequestAttempts; attempt++ {
		if err := c.waitForRequestSlot(ctx); err != nil {
			return nil, err
		}

		candles, retryAfter, err := c.fetchMinuteCandles(ctx, marketCode, to, count)
		if err == nil {
			return candles, nil
		}
		lastError = err
		if !exception.Retryable(err) || attempt == maximumRequestAttempts-1 {
			return nil, err
		}

		if retryAfter <= 0 {
			retryAfter = time.Duration(1<<attempt) * 250 * time.Millisecond
		}
		if err := waitContext(ctx, retryAfter); err != nil {
			return nil, err
		}
	}

	return nil, lastError
}

func (c *CandleClient) fetchMinuteCandles(
	ctx context.Context,
	marketCode string,
	to time.Time,
	count int,
) ([]candledomain.MinuteCandle, time.Duration, error) {
	requestURL := *c.baseURL
	requestURL.Path = "/v1/candles/minutes/1"
	query := requestURL.Query()
	query.Set("market", marketCode)
	query.Set("to", to.UTC().Format(time.RFC3339))
	query.Set("count", strconv.Itoa(count))
	requestURL.RawQuery = query.Encode()

	request, err := http.NewRequestWithContext(ctx, http.MethodGet, requestURL.String(), nil)
	if err != nil {
		return nil, 0, exception.Wrap(exception.CodeUpbitInvalidResponse, err)
	}
	response, err := c.httpClient.Do(request)
	if err != nil {
		return nil, 0, exception.Wrap(exception.CodeUpbitUnavailable, err)
	}
	defer response.Body.Close()

	if response.StatusCode == http.StatusTooManyRequests || response.StatusCode >= 500 {
		return nil, parseRetryAfter(response.Header.Get("Retry-After")), exception.Wrap(
			exception.CodeUpbitUnavailable,
			fmt.Errorf("Upbit candle response status %d", response.StatusCode),
		)
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, 0, exception.Wrap(
			exception.CodeUpbitInvalidResponse,
			fmt.Errorf("Upbit candle response status %d", response.StatusCode),
		)
	}

	decoder := json.NewDecoder(io.LimitReader(response.Body, maximumResponseBytes))
	decoder.UseNumber()
	var payloads []minuteCandleResponse
	if err := decoder.Decode(&payloads); err != nil {
		return nil, 0, exception.Wrap(exception.CodeUpbitInvalidResponse, err)
	}
	if len(payloads) > count {
		return nil, 0, exception.New(exception.CodeUpbitInvalidResponse)
	}

	candles := make([]candledomain.MinuteCandle, 0, len(payloads))
	openTimes := make(map[int64]struct{}, len(payloads))
	for _, payload := range payloads {
		candle, err := decodeMinuteCandle(payload, marketCode, to)
		if err != nil {
			return nil, 0, err
		}
		if _, exists := openTimes[candle.OpenTime.UnixNano()]; exists {
			return nil, 0, exception.Wrap(
				exception.CodeUpbitInvalidResponse,
				fmt.Errorf("duplicate candle open time %s", candle.OpenTime),
			)
		}
		openTimes[candle.OpenTime.UnixNano()] = struct{}{}
		candles = append(candles, candle)
	}

	return candles, 0, nil
}

func decodeMinuteCandle(
	payload minuteCandleResponse,
	marketCode string,
	to time.Time,
) (candledomain.MinuteCandle, error) {
	if payload.Market != marketCode || payload.Unit != 1 || payload.Timestamp <= 0 {
		return candledomain.MinuteCandle{}, exception.New(exception.CodeUpbitInvalidResponse)
	}

	openTime, err := time.Parse("2006-01-02T15:04:05", payload.CandleDateTimeUTC)
	if err != nil || !openTime.Before(to.UTC()) || openTime.Second() != 0 {
		return candledomain.MinuteCandle{}, exception.Wrap(exception.CodeUpbitInvalidResponse, err)
	}
	openTime = openTime.UTC()
	// REST timestamp is the last tick's storage time, not its trade time.
	// It may cross the candle boundary; only candle_date_time_utc selects the bucket.
	lastEventTime := time.UnixMilli(payload.Timestamp).UTC()
	if lastEventTime.Before(openTime) {
		return candledomain.MinuteCandle{}, exception.New(exception.CodeUpbitInvalidResponse)
	}

	amounts := make([]decimal.Decimal, 0, 6)
	for _, value := range []json.Number{
		payload.OpeningPrice,
		payload.HighPrice,
		payload.LowPrice,
		payload.TradePrice,
		payload.CandleAccTradeVolume,
		payload.CandleAccTradePrice,
	} {
		amount, parseErr := decimal.NewFromString(value.String())
		if parseErr != nil || amount.IsNegative() {
			return candledomain.MinuteCandle{}, exception.Wrap(
				exception.CodeUpbitInvalidResponse,
				parseErr,
			)
		}
		amounts = append(amounts, amount)
	}
	if amounts[1].LessThan(amounts[2]) ||
		amounts[0].LessThan(amounts[2]) || amounts[0].GreaterThan(amounts[1]) ||
		amounts[3].LessThan(amounts[2]) || amounts[3].GreaterThan(amounts[1]) {
		return candledomain.MinuteCandle{}, exception.New(exception.CodeUpbitInvalidResponse)
	}

	return candledomain.MinuteCandle{
		IntervalMinutes: 1,
		OpenTime:        openTime,
		OpenPrice:       amounts[0],
		HighPrice:       amounts[1],
		LowPrice:        amounts[2],
		ClosePrice:      amounts[3],
		TradeVolume:     amounts[4],
		TradeAmount:     amounts[5],
		LastEventTime:   lastEventTime,
	}, nil
}

func (c *CandleClient) waitForRequestSlot(ctx context.Context) error {
	c.requestMutex.Lock()
	defer c.requestMutex.Unlock()

	wait := time.Until(c.lastRequestAt.Add(c.requestInterval))
	if wait > 0 {
		if err := waitContext(ctx, wait); err != nil {
			return err
		}
	}
	c.lastRequestAt = time.Now()

	return nil
}

func waitContext(ctx context.Context, duration time.Duration) error {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

func parseRetryAfter(value string) time.Duration {
	seconds, err := strconv.Atoi(value)
	if err != nil || seconds <= 0 {
		return 0
	}
	return time.Duration(seconds) * time.Second
}
