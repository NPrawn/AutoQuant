package candlesync

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"sort"
	"time"

	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
)

const (
	windowDuration     = 200 * time.Minute
	verificationLag    = time.Minute
	requestCandleCount = 200
)

type TargetLoader interface {
	LoadEnabledTargets(context.Context) ([]assetdomain.MarketDataCollectionTarget, error)
}

type CandleSource interface {
	ListMinuteCandles(context.Context, string, time.Time, int) ([]candledomain.MinuteCandle, error)
}

type SyncRepository interface {
	Initialize(context.Context, string, string, time.Time, time.Time) (State, error)
	ApplyWindow(context.Context, State, Direction, time.Time, time.Time, []candledomain.MinuteCandle) error
}

type RecentCandleCache interface {
	SaveBatch(context.Context, []candledomain.MinuteCandle) error
}

// Service verifies newly finalized candles first, then spends a bounded request
// budget extending each market's continuous history range backwards.
type Service struct {
	targets       TargetLoader
	source        CandleSource
	repository    SyncRepository
	recentCache   RecentCandleCache
	historyFrom   time.Time
	historyBudget int
}

func NewService(
	targets TargetLoader,
	source CandleSource,
	repository SyncRepository,
	recentCache RecentCandleCache,
	historyFrom time.Time,
	historyBudget int,
) *Service {
	return &Service{
		targets:       targets,
		source:        source,
		repository:    repository,
		recentCache:   recentCache,
		historyFrom:   historyFrom.UTC().Truncate(time.Minute),
		historyBudget: historyBudget,
	}
}

func (s *Service) Run(ctx context.Context, now time.Time) error {
	cutoff := now.UTC().Truncate(time.Minute).Add(-verificationLag)
	if s.historyFrom.After(cutoff) {
		return fmt.Errorf("candle history start %s is after verification cutoff %s", s.historyFrom, cutoff)
	}

	targets, err := s.targets.LoadEnabledTargets(ctx)
	if err != nil {
		return err
	}
	states, initializationErrors := s.initializeStates(ctx, targets, cutoff)
	errorsByMarket := append([]error(nil), initializationErrors...)

	for index := range states {
		if err := s.syncLatest(ctx, &states[index], cutoff); err != nil {
			slog.Warn(
				"failed to verify latest minute candles",
				"market", states[index].MarketCode,
				"verified_to", states[index].VerifiedTo,
				"error", err,
			)
			errorsByMarket = append(errorsByMarket, fmt.Errorf("latest %s: %w", states[index].MarketCode, err))
		}
	}

	historyErrors := s.syncHistory(ctx, states)
	errorsByMarket = append(errorsByMarket, historyErrors...)
	if len(errorsByMarket) > 0 {
		return errors.Join(errorsByMarket...)
	}

	slog.Info(
		"minute candle sync completed",
		"market_count", len(states),
		"history_request_budget", s.historyBudget,
		"cutoff", cutoff,
	)
	return nil
}

func (s *Service) initializeStates(
	ctx context.Context,
	targets []assetdomain.MarketDataCollectionTarget,
	anchor time.Time,
) ([]State, []error) {
	states := make([]State, 0, len(targets))
	initializationErrors := make([]error, 0)
	for _, target := range targets {
		historyTarget := s.historyFrom
		if target.TradingSupportStartedAt != nil {
			tradingStart := target.TradingSupportStartedAt.UTC().Truncate(time.Minute)
			if tradingStart.After(historyTarget) {
				historyTarget = tradingStart
			}
		}

		state, err := s.repository.Initialize(
			ctx,
			target.AssetID,
			target.MarketCode,
			historyTarget,
			anchor,
		)
		if err != nil {
			initializationErrors = append(
				initializationErrors,
				fmt.Errorf("initialize %s: %w", target.MarketCode, err),
			)
			continue
		}
		states = append(states, state)
	}

	return states, initializationErrors
}

func (s *Service) syncLatest(ctx context.Context, state *State, cutoff time.Time) error {
	for state.VerifiedTo.Before(cutoff) {
		from := state.VerifiedTo
		to := minimumTime(from.Add(windowDuration), cutoff)
		if err := s.syncWindow(ctx, *state, DirectionLatest, from, to); err != nil {
			return err
		}
		state.VerifiedTo = to
	}

	return nil
}

func (s *Service) syncHistory(ctx context.Context, states []State) []error {
	sort.SliceStable(states, func(left int, right int) bool {
		leftUpdated := states[left].HistoryUpdatedAt
		rightUpdated := states[right].HistoryUpdatedAt
		if leftUpdated == nil || rightUpdated == nil {
			return leftUpdated == nil && rightUpdated != nil
		}
		if leftUpdated.Equal(*rightUpdated) {
			return states[left].MarketCode < states[right].MarketCode
		}
		return leftUpdated.Before(*rightUpdated)
	})

	remaining := s.historyBudget
	failed := make(map[string]struct{})
	historyErrors := make([]error, 0)
	for remaining > 0 {
		processedInRound := false
		for index := range states {
			if remaining == 0 {
				break
			}
			state := &states[index]
			if !state.VerifiedFrom.After(state.HistoryTargetFrom) {
				continue
			}
			if _, exists := failed[state.AssetID]; exists {
				continue
			}

			processedInRound = true
			remaining--
			to := state.VerifiedFrom
			from := maximumTime(to.Add(-windowDuration), state.HistoryTargetFrom)
			if err := s.syncWindow(ctx, *state, DirectionHistory, from, to); err != nil {
				slog.Warn(
					"failed to backfill minute candles",
					"market", state.MarketCode,
					"from", from,
					"to", to,
					"error", err,
				)
				failed[state.AssetID] = struct{}{}
				historyErrors = append(
					historyErrors,
					fmt.Errorf("history %s: %w", state.MarketCode, err),
				)
				continue
			}
			state.VerifiedFrom = from
		}
		if !processedInRound {
			break
		}
	}

	return historyErrors
}

func (s *Service) syncWindow(
	ctx context.Context,
	state State,
	direction Direction,
	from time.Time,
	to time.Time,
) error {
	fetched, err := s.source.ListMinuteCandles(ctx, state.MarketCode, to, requestCandleCount)
	if err != nil {
		return err
	}

	candles := make([]candledomain.MinuteCandle, 0, len(fetched))
	for _, candle := range fetched {
		if candle.OpenTime.Before(from) {
			continue
		}
		if !candle.OpenTime.Before(to) {
			return fmt.Errorf("Upbit candle %s is outside [%s, %s)", candle.OpenTime, from, to)
		}
		candle.AssetID = state.AssetID
		candle.IntervalMinutes = state.IntervalMinutes
		candles = append(candles, candle)
	}
	sort.Slice(candles, func(left int, right int) bool {
		return candles[left].OpenTime.Before(candles[right].OpenTime)
	})

	if err := s.repository.ApplyWindow(ctx, state, direction, from, to, candles); err != nil {
		return err
	}
	if err := s.recentCache.SaveBatch(ctx, candles); err != nil {
		slog.Warn(
			"failed to synchronize recent minute candle cache",
			"market", state.MarketCode,
			"from", from,
			"to", to,
			"error", err,
		)
	}

	return nil
}

func minimumTime(left time.Time, right time.Time) time.Time {
	if left.Before(right) {
		return left
	}
	return right
}

func maximumTime(left time.Time, right time.Time) time.Time {
	if left.After(right) {
		return left
	}
	return right
}
