package upbit

import (
	"encoding/json"
	"fmt"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
)

// TradeMessage is the Upbit WebSocket trade response DTO.
type TradeMessage struct {
	Type           string      `json:"type"`
	Code           string      `json:"code"`
	TradePrice     json.Number `json:"trade_price"`
	TradeVolume    json.Number `json:"trade_volume"`
	AskBid         string      `json:"ask_bid"`
	TradeTimestamp int64       `json:"trade_timestamp"`
	SequentialID   int64       `json:"sequential_id"`
}

// DecodeTrade validates and decodes an Upbit trade response.
func DecodeTrade(payload []byte) (TradeMessage, error) {
	var message TradeMessage
	if err := json.Unmarshal(payload, &message); err != nil {
		return TradeMessage{}, exception.Wrap(exception.CodeUpbitInvalidResponse, err)
	}
	if message.Type != "trade" || message.Code == "" || message.TradePrice == "" ||
		message.TradeVolume == "" || message.TradeTimestamp <= 0 || message.SequentialID <= 0 {
		return TradeMessage{}, exception.Wrap(
			exception.CodeUpbitInvalidResponse,
			fmt.Errorf("missing required trade field"),
		)
	}
	if message.AskBid != string(tradedomain.SideAsk) && message.AskBid != string(tradedomain.SideBid) {
		return TradeMessage{}, exception.Wrap(
			exception.CodeUpbitInvalidResponse,
			fmt.Errorf("invalid ask_bid: %s", message.AskBid),
		)
	}

	return message, nil
}

func normalizeTrade(
	message TradeMessage,
	assetID string,
	marketCode string,
) tradedomain.MarketTrade {
	return tradedomain.MarketTrade{
		AssetID:    assetID,
		MarketCode: marketCode,
		Price:      message.TradePrice.String(),
		Volume:     message.TradeVolume.String(),
		Side:       tradedomain.Side(message.AskBid),
		TradedAt:   time.UnixMilli(message.TradeTimestamp).UTC(),
		SequenceID: message.SequentialID,
	}
}
