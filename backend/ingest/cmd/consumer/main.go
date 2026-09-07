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
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/store"
)

func main() {
	if err := run(); err != nil {
		slog.Error("ingest consumer stopped", "error", err)
		os.Exit(1)
	}
}

func run() error {
	settings, err := config.Load()
	if err != nil {
		return fmt.Errorf("load config: %w", err)
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

	tradeConsumer, err := ingestkafka.NewConsumer(settings.KafkaBootstrapServers)
	if err != nil {
		return fmt.Errorf("create kafka consumer: %w", err)
	}
	defer tradeConsumer.Close()

	recorder := consumer.NewRecorder(
		store.NewMinuteCandleRepository(pool),
		consumer.DiscardingCache{},
		tradeConsumer,
	)
	service := consumer.NewService(tradeConsumer, consumer.NewProcessor(), recorder)

	slog.Info(
		"ingest consumer started",
		"topic", ingestkafka.RawTradeTopic,
		"group", ingestkafka.MinuteCandleGroupID,
	)

	if err := service.Run(ctx); err != nil {
		return err
	}

	slog.Info("ingest consumer stopped")

	return nil
}
