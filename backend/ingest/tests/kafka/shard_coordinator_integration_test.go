//go:build integration

// 실제 Kafka가 필요하다.
// 실행: docker compose -f infra/docker-compose.yml up -d kafka kafka-init
//
//	go test -tags=integration ./tests/kafka -v
package kafka_test

import (
	"context"
	"reflect"
	"sort"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/producer"
)

const (
	kafkaAddress = "localhost:9092"
	// franz-go 기본 세션 타임아웃 45초보다 오래 빈 토픽을 유지한다.
	heartbeatTestTime = 50 * time.Second
)

func TestShardCoordinatorMaintainsGroupMembershipWithoutRecords(t *testing.T) {
	first := newRunningCoordinator(t)
	waitForShardCount(t, first, 6)

	timer := time.NewTimer(heartbeatTestTime)
	defer timer.Stop()
	select {
	case <-timer.C:
	case err := <-first.done:
		t.Fatalf("첫 번째 Coordinator가 빈 토픽에서 중단됨: %v", err)
	}

	second := newRunningCoordinator(t)
	waitForShardCount(t, first, 3)
	waitForShardCount(t, second, 3)
	assertCompleteExclusiveAssignment(t, first.coordinator.OwnedShards(), second.coordinator.OwnedShards())
}

func TestShardCoordinatorReassignsShardsAfterMemberStops(t *testing.T) {
	first := newRunningCoordinator(t)
	second := newRunningCoordinator(t)
	waitForShardCount(t, first, 3)
	waitForShardCount(t, second, 3)

	second.stop(t)
	waitForShardCount(t, first, 6)
}

type runningCoordinator struct {
	coordinator *producer.ShardCoordinator
	cancel      context.CancelFunc
	done        chan error
	stopped     bool
}

func newRunningCoordinator(t *testing.T) *runningCoordinator {
	t.Helper()
	coordinator, err := producer.NewShardCoordinator([]string{kafkaAddress})
	if err != nil {
		t.Fatalf("NewShardCoordinator() error = %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	running := &runningCoordinator{
		coordinator: coordinator,
		cancel:      cancel,
		done:        make(chan error, 1),
	}
	go func() {
		running.done <- coordinator.Run(ctx)
	}()
	t.Cleanup(func() {
		if !running.stopped {
			running.stop(t)
		}
	})
	return running
}

func (r *runningCoordinator) stop(t *testing.T) {
	t.Helper()
	r.cancel()
	select {
	case err := <-r.done:
		if err != nil {
			t.Fatalf("ShardCoordinator.Run() error = %v", err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("ShardCoordinator.Run() did not stop")
	}
	r.coordinator.Close()
	r.stopped = true
}

func waitForShardCount(t *testing.T, coordinator *runningCoordinator, want int) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	if len(coordinator.coordinator.OwnedShards()) == want {
		return
	}
	for {
		select {
		case shards := <-coordinator.coordinator.AssignmentChanges():
			if len(shards) == want {
				return
			}
		case err := <-coordinator.done:
			t.Fatalf("ShardCoordinator가 할당 대기 중 중단됨: %v", err)
		case <-ctx.Done():
			t.Fatalf(
				"owned shard count = %d, want %d",
				len(coordinator.coordinator.OwnedShards()),
				want,
			)
		}
	}
}

func assertCompleteExclusiveAssignment(t *testing.T, first, second []int32) {
	t.Helper()
	all := append(append([]int32(nil), first...), second...)
	sort.Slice(all, func(left, right int) bool {
		return all[left] < all[right]
	})
	want := []int32{0, 1, 2, 3, 4, 5}
	if !reflect.DeepEqual(all, want) {
		t.Fatalf("assigned shards = %v, want %v", all, want)
	}
}
