package producer

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
)

const publishFlushTimeout = 10 * time.Second

type RecordProducer interface {
	Produce(context.Context, ingestkafka.Record, func(error))
	Flush(context.Context) error
}

type RealtimeTradePublisher interface {
	Publish(context.Context, tradedomain.MarketTrade) error
}

// TradePublisher converts normalized trades and publishes them to Kafka.
type TradePublisher struct {
	producer          RecordProducer
	realtimePublisher RealtimeTradePublisher
}

func NewTradePublisher(
	producer RecordProducer,
	realtimePublisher RealtimeTradePublisher,
) *TradePublisher {
	return &TradePublisher{
		producer:          producer,
		realtimePublisher: realtimePublisher,
	}
}

func (p *TradePublisher) Run(ctx context.Context, trades <-chan tradedomain.MarketTrade) error {
	for {
		select {
		case trade, open := <-trades:
			if !open {
				return p.finish(nil)
			}

			record, err := newTradeRecord(trade)
			if err != nil {
				return p.finish(err)
			}
			p.producer.Produce(ctx, record, func(err error) {
				if err == nil {
					return
				}
				slog.Error(
					"failed to publish trade to Kafka",
					"error", err,
					"market_code", trade.MarketCode,
					"sequence_id", trade.SequenceID,
				)
			})
			if err := p.realtimePublisher.Publish(ctx, trade); err != nil {
				slog.Warn(
					"failed to publish realtime trade update",
					"error", err,
					"market_code", trade.MarketCode,
					"sequence_id", trade.SequenceID,
				)
			}
		case <-ctx.Done():
			for range trades {
			}
			return p.finish(nil)
		}
	}
}

func newTradeRecord(trade tradedomain.MarketTrade) (ingestkafka.Record, error) {
	value, err := json.Marshal(ingestkafka.TradeMessage{
		AssetID:           trade.AssetID,
		MarketCode:        trade.MarketCode,
		Price:             trade.Price,
		Volume:            trade.Volume,
		Side:              string(trade.Side),
		TradedAtUnixMilli: trade.TradedAt.UnixMilli(),
		SequenceID:        trade.SequenceID,
	})
	if err != nil {
		return ingestkafka.Record{}, exception.Wrap(exception.CodeKafkaInvalidMessage, err)
	}

	return ingestkafka.Record{
		Topic: ingestkafka.RawTradeTopic,
		Key:   []byte(trade.MarketCode),
		Value: value,
	}, nil
}

func (p *TradePublisher) finish(runErr error) error {
	flushContext, cancel := context.WithTimeout(context.Background(), publishFlushTimeout)
	defer cancel()
	flushErr := p.producer.Flush(flushContext)

	if runErr == nil {
		return flushErr
	}
	if flushErr != nil {
		return fmt.Errorf("%w; flush producer: %v", runErr, flushErr)
	}
	return runErr
}
