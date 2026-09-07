package config_test

import (
	"reflect"
	"strings"
	"testing"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/config"
)

func TestLoad(t *testing.T) {
	t.Run("필수 설정으로 구성을 생성한다", func(t *testing.T) {
		setRequiredEnvironment(t)
		t.Setenv("COLLECTION_TARGET_REFRESH_INTERVAL", "2s")
		t.Setenv("UPBIT_WEBSOCKET_URL", "wss://example.com/websocket")
		t.Setenv("KAFKA_BOOTSTRAP_SERVERS", "kafka-1:9092, kafka-2:9092")

		got, err := config.Load()
		if err != nil {
			t.Fatalf("Load() error = %v", err)
		}
		if got.RefreshInterval != 2*time.Second {
			t.Fatalf("RefreshInterval = %v, want %v", got.RefreshInterval, 2*time.Second)
		}
		if got.UpbitWebSocketURL != "wss://example.com/websocket" {
			t.Fatalf("UpbitWebSocketURL = %q, want %q", got.UpbitWebSocketURL, "wss://example.com/websocket")
		}
		if !strings.Contains(got.DatabaseURL, "postgres://a107:password@localhost:5432/a107") {
			t.Fatalf("DatabaseURL = %q, want configured database address", got.DatabaseURL)
		}
		wantKafkaBootstrapServers := []string{"kafka-1:9092", "kafka-2:9092"}
		if !reflect.DeepEqual(got.KafkaBootstrapServers, wantKafkaBootstrapServers) {
			t.Fatalf(
				"KafkaBootstrapServers = %v, want %v",
				got.KafkaBootstrapServers,
				wantKafkaBootstrapServers,
			)
		}
	})

	t.Run("필수 설정이 없으면 오류를 반환한다", func(t *testing.T) {
		setRequiredEnvironment(t)
		t.Setenv("DB_HOST", "")
		if _, err := config.Load(); err == nil {
			t.Fatal("Load() error = nil, want error")
		}
	})

	t.Run("잘못된 조회 주기를 거부한다", func(t *testing.T) {
		setRequiredEnvironment(t)
		t.Setenv("COLLECTION_TARGET_REFRESH_INTERVAL", "0s")
		if _, err := config.Load(); err == nil {
			t.Fatal("Load() error = nil, want error")
		}
	})

	t.Run("카프카 접속 주소가 없으면 오류를 반환한다", func(t *testing.T) {
		setRequiredEnvironment(t)
		t.Setenv("KAFKA_BOOTSTRAP_SERVERS", "")
		if _, err := config.Load(); err == nil {
			t.Fatal("Load() error = nil, want error")
		}
	})

	t.Run("빈 카프카 접속 주소를 거부한다", func(t *testing.T) {
		setRequiredEnvironment(t)
		t.Setenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092,")
		if _, err := config.Load(); err == nil {
			t.Fatal("Load() error = nil, want error")
		}
	})
}

func TestLoadConsumer(t *testing.T) {
	t.Run("Consumer 저장소 구성을 생성한다", func(t *testing.T) {
		setRequiredEnvironment(t)
		t.Setenv("REDIS_HOST", "redis")
		t.Setenv("REDIS_PORT", "6379")

		got, err := config.LoadConsumer()
		if err != nil {
			t.Fatalf("LoadConsumer() error = %v", err)
		}
		if got.RedisAddress != "redis:6379" {
			t.Fatalf("RedisAddress = %q, want %q", got.RedisAddress, "redis:6379")
		}
	})

	t.Run("Redis 접속 주소가 없으면 오류를 반환한다", func(t *testing.T) {
		setRequiredEnvironment(t)
		t.Setenv("REDIS_HOST", "")
		t.Setenv("REDIS_PORT", "6379")

		if _, err := config.LoadConsumer(); err == nil {
			t.Fatal("LoadConsumer() error = nil, want error")
		}
	})
}

func setRequiredEnvironment(t *testing.T) {
	t.Helper()
	t.Setenv("DB_HOST", "localhost")
	t.Setenv("DB_PORT", "5432")
	t.Setenv("DB_NAME", "a107")
	t.Setenv("DB_USER", "a107")
	t.Setenv("DB_PASSWORD", "password")
	t.Setenv("COLLECTION_TARGET_REFRESH_INTERVAL", "")
	t.Setenv("UPBIT_WEBSOCKET_URL", "")
	t.Setenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
}
