//go:build integration

// Dedicated PostgreSQL with Flyway migrations and Redis are required.
// TEST_DATABASE_URL / TEST_REDIS_ADDRESS; go test -tags=integration ./tests/store
package store_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlesync"
	ingestredis "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/redis"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/store"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/upbit"
)

func TestCandleSyncResumesBoundaryWithDelayedStoredTimestamp(t *testing.T) {
	address := os.Getenv("TEST_REDIS_ADDRESS")
	if address == "" {
		t.Skip("TEST_REDIS_ADDRESS must point to a dedicated Redis")
	}
	pool := newTestPool(t)
	prepareTestAsset(t, pool)
	ctx := context.Background()
	client := ingestredis.NewClient(address)
	key := ingestredis.RecentMinuteCandlesKey(testAssetID)
	t.Cleanup(func() {
		if err := client.Del(ctx, key).Err(); err != nil {
			t.Error(err)
		}
		if err := client.Close(); err != nil {
			t.Error(err)
		}
	})
	if _, err := pool.Exec(ctx, `INSERT INTO ops.market_data_collection_target(asset_id, collection_status)
VALUES ($1, 'ENABLED')`, testAssetID); err != nil {
		t.Fatal(err)
	}
	start := time.Date(2026, 9, 9, 0, 25, 0, 0, time.UTC)
	end := start.Add(time.Minute)
	storedAt := end.Add(39 * time.Millisecond)
	// A previously failed run left this boundary unchanged. No reset is needed.
	if _, err := store.NewCandleSyncRepository(pool).Initialize(ctx, testAssetID, testMarketCode, start, end); err != nil {
		t.Fatal(err)
	}
	payload, err := os.ReadFile("../upbit/testdata/minute_candle_delayed_timestamp.json")
	if err != nil {
		t.Fatal(err)
	}
	body := strings.ReplaceAll(string(payload), "KRW-ATH", testMarketCode)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if got := r.URL.Query().Get("to"); got != end.Format(time.RFC3339) {
			t.Errorf("got to=%s, want %s", got, end.Format(time.RFC3339))
		}
		w.Header().Set("Content-Type", "application/json")
		if _, err := w.Write([]byte(body)); err != nil {
			t.Error(err)
		}
	}))
	defer server.Close()
	source, err := upbit.NewCandleClient(server.URL, time.Second, 0)
	if err != nil {
		t.Fatal(err)
	}
	service := candlesync.NewService(store.NewCollectionTargetRepository(pool), source,
		store.NewCandleSyncRepository(pool), ingestredis.NewRecentMinuteCandleRepository(client), start, 1)
	if err := service.Run(ctx, end.Add(time.Minute)); err != nil {
		t.Fatal(err)
	}
	var gotOpen, gotStored, verifiedFrom, verifiedTo time.Time
	var verified bool
	if err := pool.QueryRow(ctx, `SELECT c.open_time, c.last_event_time, c.verified_at IS NOT NULL,
s.verified_from, s.verified_to FROM market_data.minute_candle c
JOIN ops.minute_candle_sync_state s USING(asset_id, interval_minutes)
WHERE c.asset_id=$1 AND c.interval_minutes=1`, testAssetID).
		Scan(&gotOpen, &gotStored, &verified, &verifiedFrom, &verifiedTo); err != nil {
		t.Fatal(err)
	}
	if !gotOpen.Equal(start) || !gotStored.Equal(storedAt) || !verified || !verifiedFrom.Equal(start) || !verifiedTo.Equal(end) {
		t.Fatalf("got open/stored/verified/range %v/%v/%v/[%v,%v), want %v/%v/true/[%v,%v)",
			gotOpen, gotStored, verified, verifiedFrom, verifiedTo, start, storedAt, start, end)
	}
	entries, err := client.ZRangeWithScores(ctx, key, 0, -1).Result()
	if err != nil || len(entries) != 1 {
		t.Fatalf("got %d cache entries / %v, want 1 / nil", len(entries), err)
	}
	var cached struct {
		OpenTime   int64 `json:"open_time_unix_milli"`
		StoredTime int64 `json:"last_event_time_unix_milli"`
	}
	if err := json.Unmarshal([]byte(entries[0].Member.(string)), &cached); err != nil {
		t.Fatal(err)
	}
	if cached.OpenTime != start.UnixMilli() || cached.StoredTime != storedAt.UnixMilli() || int64(entries[0].Score) != start.UnixMilli() {
		t.Fatalf("got cache %v / score %.0f, want %d/%d / score %d", cached, entries[0].Score, start.UnixMilli(), storedAt.UnixMilli(), start.UnixMilli())
	}
}
