package main

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/config"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/consumer"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
	ingestredis "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/redis"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/store"
)

func main() {
	if err := run(); err != nil {
		slog.Error("ingest consumer stopped", "error", err)
		os.Exit(1)
	}
}

func run() error {
	settings, err := config.LoadConsumer()
	if err != nil {
		return fmt.Errorf("load consumer config: %w", err)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

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

	kafkaConsumer, err := ingestkafka.NewConsumer(
		settings.KafkaBootstrapServers,
		ingestkafka.MinuteCandleGroupID,
	)
	if err != nil {
		return fmt.Errorf("create Kafka consumer: %w", err)
	}
	defer kafkaConsumer.Close()

	progressRepository := ingestredis.NewProgressRepository(redisClient)
	minuteCandleRepository := store.NewMinuteCandleRepository(pool)
	handler := consumer.NewPersistenceHandler(progressRepository, minuteCandleRepository)
	liveCandlePublisher := ingestredis.NewLiveMinuteCandlePublisher(redisClient)
	service := consumer.NewService(
		kafkaConsumer,
		progressRepository,
		handler,
		liveCandlePublisher,
	)

	slog.Info("ingest consumer started")
	if err := service.Run(ctx); err != nil {
		return err
	}
	slog.Info("ingest consumer stopped")

	return nil
}
