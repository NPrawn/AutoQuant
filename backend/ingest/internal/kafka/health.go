package kafka

import (
	"context"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/twmb/franz-go/pkg/kgo"
)

// Ping verifies that a Kafka broker is reachable.
func Ping(ctx context.Context, brokers []string) error {
	client, err := kgo.NewClient(kgo.SeedBrokers(brokers...))
	if err != nil {
		return exception.Wrap(exception.CodeKafkaConsumeFailed, err)
	}
	defer client.Close()

	if err := client.Ping(ctx); err != nil {
		return exception.Wrap(exception.CodeKafkaConsumeFailed, err)
	}

	return nil
}
