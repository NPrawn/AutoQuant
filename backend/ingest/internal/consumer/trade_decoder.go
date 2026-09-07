package consumer

import (
	"encoding/json"
	"errors"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
)

func decodeTrade(record ingestkafka.Record) (tradedomain.MarketTrade, error) {
	var message ingestkafka.TradeMessage
	if err := json.Unmarshal(record.Value, &message); err != nil {
		return tradedomain.MarketTrade{}, exception.Wrap(exception.CodeKafkaInvalidMessage, err)
	}
	if message.AssetID == "" || message.MarketCode == "" || message.Price == "" ||
		message.Volume == "" || message.TradedAtUnixMilli <= 0 || message.SequenceID <= 0 {
		return tradedomain.MarketTrade{}, invalidTradeMessage("missing required trade field")
	}
	if message.Side != string(tradedomain.SideAsk) && message.Side != string(tradedomain.SideBid) {
		return tradedomain.MarketTrade{}, invalidTradeMessage("invalid trade side")
	}
	if string(record.Key) != message.MarketCode {
		return tradedomain.MarketTrade{}, invalidTradeMessage("Kafka key does not match market code")
	}

	return tradedomain.MarketTrade{
		AssetID:    message.AssetID,
		MarketCode: message.MarketCode,
		Price:      message.Price,
		Volume:     message.Volume,
		Side:       tradedomain.Side(message.Side),
		TradedAt:   time.UnixMilli(message.TradedAtUnixMilli).UTC(),
		SequenceID: message.SequenceID,
	}, nil
}

func invalidTradeMessage(message string) error {
	return exception.Wrap(exception.CodeKafkaInvalidMessage, errors.New(message))
}
