package main

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlesync"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/config"
	ingestredis "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/redis"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/store"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/upbit"
)

const upbitRequestInterval = 125 * time.Millisecond

func main() {
	if err := run(); err != nil {
		slog.Error("minute candle sync failed", "error", err)
		os.Exit(1)
	}
}

func run() error {
	settings, err := config.LoadBackfill()
	if err != nil {
		return fmt.Errorf("load backfill config: %w", err)
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

	candleClient, err := upbit.NewCandleClient(
		settings.UpbitRESTBaseURL,
		settings.UpbitRESTTimeout,
		upbitRequestInterval,
	)
	if err != nil {
		return fmt.Errorf("create Upbit candle client: %w", err)
	}

	service := candlesync.NewService(
		store.NewCollectionTargetRepository(pool),
		candleClient,
		store.NewCandleSyncRepository(pool),
		ingestredis.NewRecentMinuteCandleRepository(redisClient),
		settings.HistoryFrom,
		settings.HistoryRequestBudget,
	)

	synchronize := func() error {
		lock, acquired, err := store.TryAcquireCandleSyncLock(ctx, pool)
		if err != nil {
			return fmt.Errorf("acquire candle sync lock: %w", err)
		}
		if !acquired {
			slog.Info("minute candle sync skipped because another run is active")
			return nil
		}
		defer func() {
			releaseContext, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			if releaseErr := lock.Release(releaseContext); releaseErr != nil {
				slog.Error("release candle sync lock", "error", releaseErr)
			}
		}()

		slog.Info("minute candle sync started")
		return service.Run(ctx, time.Now())
	}

	slog.Info("minute candle sync scheduler started", "interval", settings.SyncInterval)
	ticker := time.NewTicker(settings.SyncInterval)
	defer ticker.Stop()
	for {
		if err := synchronize(); err != nil {
			if ctx.Err() != nil {
				return nil
			}
			slog.Error("minute candle sync run failed", "error", err)
		}

		select {
		case <-ctx.Done():
			return nil
		case <-ticker.C:
		}
	}
}
