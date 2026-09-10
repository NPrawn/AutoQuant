package main

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/config"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
	ingestredis "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/redis"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/store"
)

const dependencyTimeout = 5 * time.Second

func main() {
	if err := run(); err != nil {
		slog.Error("ingest health check failed", "error", err)
		os.Exit(1)
	}
}

func run() error {
	settings, err := config.LoadHealthcheck()
	if err != nil {
		return fmt.Errorf("load health check config: %w", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), dependencyTimeout)
	defer cancel()

	pool, err := store.NewPool(ctx, settings.DatabaseURL)
	if err != nil {
		return fmt.Errorf("create database pool: %w", err)
	}
	defer pool.Close()
	if err := pool.Ping(ctx); err != nil {
		return fmt.Errorf("connect database: %w", err)
	}

	redisClient := ingestredis.NewClient(settings.RedisAddress)
	defer func() {
		if closeErr := redisClient.Close(); closeErr != nil {
			slog.Error("close Redis client", "error", closeErr)
		}
	}()
	if err := redisClient.Ping(ctx).Err(); err != nil {
		return fmt.Errorf("connect Redis: %w", err)
	}

	if len(settings.KafkaBootstrapServers) > 0 {
		if err := ingestkafka.Ping(ctx, settings.KafkaBootstrapServers); err != nil {
			return fmt.Errorf("connect Kafka: %w", err)
		}
	}

	return nil
}
