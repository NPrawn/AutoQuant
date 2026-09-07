package kafka

import (
	"context"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/twmb/franz-go/pkg/kgo"
)

// Producer is a thin franz-go producer adapter.
type Producer struct {
	client *kgo.Client
}

func NewProducer(brokers []string) (*Producer, error) {
	client, err := kgo.NewClient(kgo.SeedBrokers(brokers...))
	if err != nil {
		return nil, exception.Wrap(exception.CodeKafkaPublishFailed, err)
	}
	return &Producer{client: client}, nil
}

// Produce publishes a record asynchronously and reports the broker result through complete.
func (p *Producer) Produce(ctx context.Context, record Record, complete func(error)) {
	p.client.Produce(ctx, &kgo.Record{
		Topic: record.Topic,
		Key:   record.Key,
		Value: record.Value,
	}, func(_ *kgo.Record, err error) {
		if err != nil {
			err = exception.Wrap(exception.CodeKafkaPublishFailed, err)
		}
		complete(err)
	})
}

func (p *Producer) Flush(ctx context.Context) error {
	if err := p.client.Flush(ctx); err != nil {
		return exception.Wrap(exception.CodeKafkaPublishFailed, err)
	}
	return nil
}

func (p *Producer) Close() {
	p.client.Close()
}
