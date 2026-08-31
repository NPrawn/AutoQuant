package upbit

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"time"

	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"

	"github.com/gorilla/websocket"
)

const (
	maximumReconnectAttempts = 5
	defaultReconnectDelay    = time.Second
)

var errNoTargets = errors.New("no collection targets")

type connection interface {
	ReadMessage() (messageType int, data []byte, err error)
	WriteJSON(value any) error
	Close() error
}

type dialer interface {
	Dial(ctx context.Context, url string) (connection, error)
}

type gorillaDialer struct{}

func (gorillaDialer) Dial(ctx context.Context, url string) (connection, error) {
	connection, _, err := websocket.DefaultDialer.DialContext(ctx, url, http.Header{})
	return connection, err
}

type subscriptionRequest struct {
	Ticket         string   `json:"ticket,omitempty"`
	Type           string   `json:"type,omitempty"`
	Codes          []string `json:"codes,omitempty"`
	OnlyRealtime   bool     `json:"is_only_realtime,omitempty"`
	ResponseFormat string   `json:"format,omitempty"`
}

// Client receives Upbit trade streams and replaces subscriptions when targets change.
type Client struct {
	url            string
	dialer         dialer
	reconnectDelay time.Duration
}

func NewClient(url string) *Client {
	return &Client{
		url:            url,
		dialer:         gorillaDialer{},
		reconnectDelay: defaultReconnectDelay,
	}
}

func (c *Client) Run(
	ctx context.Context,
	updates <-chan []assetdomain.MarketDataCollectionTarget,
	trades chan<- tradedomain.MarketTrade,
) error {
	defer close(trades)

	var targets []assetdomain.MarketDataCollectionTarget
	reconnectAttempts := 0

	for {
		if len(targets) == 0 {
			select {
			case <-ctx.Done():
				return nil
			case targets = <-updates:
				continue
			}
		}

		webSocketConnection, err := c.dialer.Dial(ctx, c.url)
		if err != nil {
			reconnectAttempts++
			if reconnectAttempts >= maximumReconnectAttempts {
				return exception.Wrap(exception.CodeUpbitUnavailable, err)
			}
			targets, err = c.waitBeforeReconnect(ctx, updates, targets)
			if err != nil {
				return nil
			}
			continue
		}

		if err := writeSubscription(webSocketConnection, targets); err != nil {
			webSocketConnection.Close()
			reconnectAttempts++
			if reconnectAttempts >= maximumReconnectAttempts {
				return exception.Wrap(exception.CodeUpbitUnavailable, err)
			}
			continue
		}

		healthy, err := runConnection(ctx, webSocketConnection, updates, &targets, trades)
		webSocketConnection.Close()
		if err == nil && ctx.Err() != nil {
			return nil
		}
		if errors.Is(err, errNoTargets) {
			reconnectAttempts = 0
			continue
		}
		if healthy {
			reconnectAttempts = 0
		}
		reconnectAttempts++
		if reconnectAttempts >= maximumReconnectAttempts {
			return err
		}
		targets, err = c.waitBeforeReconnect(ctx, updates, targets)
		if err != nil {
			return nil
		}
	}
}

func (c *Client) waitBeforeReconnect(
	ctx context.Context,
	updates <-chan []assetdomain.MarketDataCollectionTarget,
	targets []assetdomain.MarketDataCollectionTarget,
) ([]assetdomain.MarketDataCollectionTarget, error) {
	timer := time.NewTimer(c.reconnectDelay)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	case nextTargets := <-updates:
		return nextTargets, nil
	case <-timer.C:
		return targets, nil
	}
}

func runConnection(
	ctx context.Context,
	webSocketConnection connection,
	updates <-chan []assetdomain.MarketDataCollectionTarget,
	targets *[]assetdomain.MarketDataCollectionTarget,
	trades chan<- tradedomain.MarketTrade,
) (bool, error) {
	readContext, cancel := context.WithCancel(ctx)
	defer cancel()

	payloads := make(chan []byte)
	readErrors := make(chan error, 1)
	go readMessages(readContext, webSocketConnection, payloads, readErrors)

	targetsByCode := indexTargets(*targets)
	healthy := false
	for {
		select {
		case <-ctx.Done():
			return healthy, nil
		case nextTargets := <-updates:
			*targets = nextTargets
			if len(nextTargets) == 0 {
				return healthy, errNoTargets
			}
			if err := writeSubscription(webSocketConnection, nextTargets); err != nil {
				return healthy, exception.Wrap(exception.CodeUpbitUnavailable, err)
			}
			targetsByCode = indexTargets(nextTargets)
		case err := <-readErrors:
			return healthy, exception.Wrap(exception.CodeUpbitUnavailable, err)
		case payload := <-payloads:
			message, err := DecodeTrade(payload)
			if err != nil {
				return healthy, err
			}
			target, exists := targetsByCode[message.Code]
			if !exists {
				continue
			}
			healthy = true
			trade := normalizeTrade(message, target.AssetID, target.MarketCode)
			select {
			case trades <- trade:
			case <-ctx.Done():
				return healthy, nil
			}
		}
	}
}

func readMessages(ctx context.Context, webSocketConnection connection, payloads chan<- []byte, errors chan<- error) {
	for {
		_, payload, err := webSocketConnection.ReadMessage()
		if err != nil {
			select {
			case errors <- err:
			case <-ctx.Done():
			}
			return
		}

		select {
		case payloads <- payload:
		case <-ctx.Done():
			return
		}
	}
}

func writeSubscription(
	webSocketConnection connection,
	targets []assetdomain.MarketDataCollectionTarget,
) error {
	codes := make([]string, len(targets))
	for index, target := range targets {
		codes[index] = target.MarketCode
	}

	request := []subscriptionRequest{
		{Ticket: fmt.Sprintf("ingest-producer-%d", time.Now().UnixNano())},
		{Type: "trade", Codes: codes, OnlyRealtime: true},
		{ResponseFormat: "DEFAULT"},
	}
	return webSocketConnection.WriteJSON(request)
}

func indexTargets(
	targets []assetdomain.MarketDataCollectionTarget,
) map[string]assetdomain.MarketDataCollectionTarget {
	indexedTargets := make(map[string]assetdomain.MarketDataCollectionTarget, len(targets))
	for _, target := range targets {
		indexedTargets[target.MarketCode] = target
	}
	return indexedTargets
}
