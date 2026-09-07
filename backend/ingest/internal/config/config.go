package config

import (
	"fmt"
	"net"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	defaultUpbitWebSocketURL = "wss://api.upbit.com/websocket/v1"
	defaultRefreshInterval   = 5 * time.Second
	defaultUpbitRESTBaseURL  = "https://api.upbit.com"
	defaultUpbitRESTTimeout  = 5 * time.Second
	defaultHistoryBudget     = 240
)

// Config contains the settings required by the ingest producer.
type Config struct {
	DatabaseURL           string
	KafkaBootstrapServers []string
	RedisAddress          string
	UpbitWebSocketURL     string
	RefreshInterval       time.Duration
}

// ConsumerConfig contains the settings required by the candle consumer.
type ConsumerConfig struct {
	DatabaseURL           string
	KafkaBootstrapServers []string
	RedisAddress          string
}

// BackfillConfig contains the settings required by the one-shot candle sync.
type BackfillConfig struct {
	DatabaseURL          string
	RedisAddress         string
	UpbitRESTBaseURL     string
	UpbitRESTTimeout     time.Duration
	HistoryFrom          time.Time
	HistoryRequestBudget int
}

// Load reads and validates producer settings from environment variables.
func Load() (Config, error) {
	databaseURL, err := loadDatabaseURL()
	if err != nil {
		return Config{}, err
	}
	kafkaBootstrapServers, err := requiredEnvironmentList("KAFKA_BOOTSTRAP_SERVERS")
	if err != nil {
		return Config{}, err
	}
	redisAddress, err := loadRedisAddress()
	if err != nil {
		return Config{}, err
	}

	refreshInterval := defaultRefreshInterval
	if value := os.Getenv("COLLECTION_TARGET_REFRESH_INTERVAL"); value != "" {
		refreshInterval, err = time.ParseDuration(value)
		if err != nil || refreshInterval <= 0 {
			return Config{}, fmt.Errorf("invalid COLLECTION_TARGET_REFRESH_INTERVAL")
		}
	}

	webSocketURL := os.Getenv("UPBIT_WEBSOCKET_URL")
	if webSocketURL == "" {
		webSocketURL = defaultUpbitWebSocketURL
	}
	parsedWebSocketURL, err := url.Parse(webSocketURL)
	if err != nil || parsedWebSocketURL.Scheme != "wss" || parsedWebSocketURL.Host == "" {
		return Config{}, fmt.Errorf("invalid UPBIT_WEBSOCKET_URL")
	}

	return Config{
		DatabaseURL:           databaseURL,
		KafkaBootstrapServers: kafkaBootstrapServers,
		RedisAddress:          redisAddress,
		UpbitWebSocketURL:     parsedWebSocketURL.String(),
		RefreshInterval:       refreshInterval,
	}, nil
}

// LoadConsumer reads and validates the database, Kafka, and Redis settings used
// by cmd/consumer.
func LoadConsumer() (ConsumerConfig, error) {
	databaseURL, err := loadDatabaseURL()
	if err != nil {
		return ConsumerConfig{}, err
	}
	kafkaBootstrapServers, err := requiredEnvironmentList("KAFKA_BOOTSTRAP_SERVERS")
	if err != nil {
		return ConsumerConfig{}, err
	}
	redisAddress, err := loadRedisAddress()
	if err != nil {
		return ConsumerConfig{}, err
	}

	return ConsumerConfig{
		DatabaseURL:           databaseURL,
		KafkaBootstrapServers: kafkaBootstrapServers,
		RedisAddress:          redisAddress,
	}, nil
}

// LoadBackfill reads and validates the database, Redis, and Upbit REST settings.
func LoadBackfill() (BackfillConfig, error) {
	databaseURL, err := loadDatabaseURL()
	if err != nil {
		return BackfillConfig{}, err
	}
	redisAddress, err := loadRedisAddress()
	if err != nil {
		return BackfillConfig{}, err
	}

	historyFromValue, err := requiredEnvironment("CANDLE_HISTORY_FROM")
	if err != nil {
		return BackfillConfig{}, err
	}
	historyFrom, err := time.Parse(time.RFC3339, historyFromValue)
	if err != nil {
		return BackfillConfig{}, fmt.Errorf("invalid CANDLE_HISTORY_FROM")
	}
	historyFrom = historyFrom.UTC().Truncate(time.Minute)

	historyBudget := defaultHistoryBudget
	if value := os.Getenv("CANDLE_HISTORY_REQUEST_BUDGET"); value != "" {
		historyBudget, err = strconv.Atoi(value)
		if err != nil || historyBudget <= 0 {
			return BackfillConfig{}, fmt.Errorf("invalid CANDLE_HISTORY_REQUEST_BUDGET")
		}
	}

	restTimeout := defaultUpbitRESTTimeout
	if value := os.Getenv("UPBIT_REST_TIMEOUT"); value != "" {
		restTimeout, err = time.ParseDuration(value)
		if err != nil || restTimeout <= 0 {
			return BackfillConfig{}, fmt.Errorf("invalid UPBIT_REST_TIMEOUT")
		}
	}

	restBaseURL := os.Getenv("UPBIT_REST_BASE_URL")
	if restBaseURL == "" {
		restBaseURL = defaultUpbitRESTBaseURL
	}
	parsedRESTBaseURL, err := url.Parse(restBaseURL)
	if err != nil ||
		(parsedRESTBaseURL.Scheme != "http" && parsedRESTBaseURL.Scheme != "https") ||
		parsedRESTBaseURL.Host == "" {
		return BackfillConfig{}, fmt.Errorf("invalid UPBIT_REST_BASE_URL")
	}

	return BackfillConfig{
		DatabaseURL:          databaseURL,
		RedisAddress:         redisAddress,
		UpbitRESTBaseURL:     parsedRESTBaseURL.String(),
		UpbitRESTTimeout:     restTimeout,
		HistoryFrom:          historyFrom,
		HistoryRequestBudget: historyBudget,
	}, nil
}

func loadRedisAddress() (string, error) {
	host, err := requiredEnvironment("REDIS_HOST")
	if err != nil {
		return "", err
	}
	port, err := requiredEnvironment("REDIS_PORT")
	if err != nil {
		return "", err
	}

	return net.JoinHostPort(host, port), nil
}

func loadDatabaseURL() (string, error) {
	host, err := requiredEnvironment("DB_HOST")
	if err != nil {
		return "", err
	}
	port, err := requiredEnvironment("DB_PORT")
	if err != nil {
		return "", err
	}
	databaseName, err := requiredEnvironment("DB_NAME")
	if err != nil {
		return "", err
	}
	user, err := requiredEnvironment("DB_USER")
	if err != nil {
		return "", err
	}
	password, err := requiredEnvironment("DB_PASSWORD")
	if err != nil {
		return "", err
	}

	databaseURL := &url.URL{
		Scheme: "postgres",
		User:   url.UserPassword(user, password),
		Host:   net.JoinHostPort(host, port),
		Path:   databaseName,
	}
	databaseQuery := databaseURL.Query()
	databaseQuery.Set("sslmode", "disable")
	databaseURL.RawQuery = databaseQuery.Encode()

	return databaseURL.String(), nil
}

func requiredEnvironment(name string) (string, error) {
	value := os.Getenv(name)
	if value == "" {
		return "", fmt.Errorf("%s is required", name)
	}
	return value, nil
}

func requiredEnvironmentList(name string) ([]string, error) {
	value, err := requiredEnvironment(name)
	if err != nil {
		return nil, err
	}

	values := strings.Split(value, ",")
	for index := range values {
		values[index] = strings.TrimSpace(values[index])
		if values[index] == "" {
			return nil, fmt.Errorf("%s contains an empty value", name)
		}
	}
	return values, nil
}
