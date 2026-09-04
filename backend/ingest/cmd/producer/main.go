package main

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/config"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/producer"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/store"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/upbit"
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

	repository := store.NewCollectionTargetRepository(pool)
	watcher := producer.NewTargetWatcher(repository)
	client := upbit.NewClient(settings.UpbitWebSocketURL)
	service := producer.NewService(watcher, client, settings.RefreshInterval)

	trades := make(chan tradedomain.MarketTrade, 1024)
	errors := make(chan error, 1)
	go func() {
		errors <- service.Run(ctx, trades)
	}()

	slog.Info("ingest producer started")
	receivedTrades := uint64(0)
	for range trades {
		receivedTrades++
	}

	if err := <-errors; err != nil {
		return err
	}
	slog.Info("ingest producer stopped", "received_trades", receivedTrades)
	return nil
}
