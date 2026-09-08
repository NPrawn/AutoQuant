package candlerollup

import (
	"context"
	"log/slog"
	"time"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
)

type Job struct {
	AssetID         string
	IntervalMinutes int16
	OpenTime        time.Time
	Revision        int64
}

type MinuteReader interface {
	LoadMinutes(context.Context, string, time.Time, time.Time) ([]candledomain.MinuteCandle, error)
}

type Repository interface {
	MinuteReader
	Exclusive(context.Context, func() error) error
	Next(context.Context, time.Time) (*Job, error)
	Save(context.Context, candledomain.MinuteCandle) error
	Complete(context.Context, Job) error
}

type Cache interface {
	SaveBatch(context.Context, []candledomain.MinuteCandle) error
}

type Service struct {
	repository Repository
	cache      Cache
}

func NewService(repository Repository, cache Cache) *Service {
	return &Service{repository: repository, cache: cache}
}

// Run shares one bounded periodic job across consumer instances. Failed work
// remains durable; Redis failure cannot advance the rollup job checkpoint.
func (s *Service) Run(ctx context.Context) error {
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()
	for {
		runCtx, cancel := context.WithTimeout(ctx, 20*time.Second)
		err := s.RunOnce(runCtx, time.Now())
		cancel()
		if err != nil && ctx.Err() == nil {
			slog.Warn("candle rollup will retry", "error", err)
		}
		select {
		case <-ctx.Done():
			return nil
		case <-ticker.C:
		}
	}
}

func (s *Service) RunOnce(ctx context.Context, now time.Time) error {
	return s.repository.Exclusive(ctx, func() error {
		for range 100 {
			job, err := s.repository.Next(ctx, now.Add(-5*time.Second))
			if err != nil || job == nil {
				return err
			}
			minutes, err := s.repository.LoadMinutes(ctx, job.AssetID, job.OpenTime, job.OpenTime.Add(time.Duration(job.IntervalMinutes)*time.Minute))
			if err != nil {
				return err
			}
			result := Aggregate(job.AssetID, job.IntervalMinutes, job.OpenTime, minutes)
			if result != nil {
				if err := s.repository.Save(ctx, *result); err != nil {
					return err
				}
				if err := s.cache.SaveBatch(ctx, []candledomain.MinuteCandle{*result}); err != nil {
					return err
				}
			}
			// A concurrent source correction increments revision, retaining the job.
			if err := s.repository.Complete(ctx, *job); err != nil {
				return err
			}
		}
		return nil
	})
}
