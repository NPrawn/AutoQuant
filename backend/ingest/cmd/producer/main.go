package main

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/config"
	ingestkafka "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/kafka"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/producer"
	ingestredis "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/redis"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/store"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/upbit"

	"golang.org/x/sync/errgroup"
)

func main() {
	if err := run(); err != nil {
		slog.Error("ingest producer stopped", "error", err)
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
	redisClient := ingestredis.NewClient(settings.RedisAddress)
	defer func() {
		if closeErr := redisClient.Close(); closeErr != nil {
			slog.Error("close Redis client", "error", closeErr)
		}
	}()

	repository := store.NewCollectionTargetRepository(pool)
	watcher := producer.NewTargetWatcher(repository)
	client := upbit.NewClient(settings.UpbitWebSocketURL)
	service := producer.NewService(watcher, client, settings.RefreshInterval)
	coordinator, err := producer.NewShardCoordinator(settings.KafkaBootstrapServers)
	if err != nil {
		return fmt.Errorf("create shard coordinator: %w", err)
	}
	defer coordinator.Close()
	kafkaProducer, err := ingestkafka.NewProducer(settings.KafkaBootstrapServers)
	if err != nil {
		return fmt.Errorf("create Kafka producer: %w", err)
	}
	defer kafkaProducer.Close()
	realtimePublisher := ingestredis.NewRealtimeTradePublisher(redisClient)
	tradePublisher := producer.NewTradePublisher(kafkaProducer, realtimePublisher)

	trades := make(chan tradedomain.MarketTrade, 1024)
	slog.Info("ingest producer started")
	group, groupContext := errgroup.WithContext(ctx)
	group.Go(func() error {
		return coordinator.Run(groupContext)
	})
	group.Go(func() error {
		return service.Run(groupContext, coordinator.AssignmentChanges(), trades)
	})
	group.Go(func() error {
		return tradePublisher.Run(groupContext, trades)
	})

	if err := group.Wait(); err != nil {
		return err
	}
	slog.Info("ingest producer stopped")
	return nil
}
