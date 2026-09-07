package config

import (
	"fmt"
	"net"
	"net/url"
	"os"
	"strings"
	"time"
)

const (
	defaultUpbitWebSocketURL = "wss://api.upbit.com/websocket/v1"
	defaultRefreshInterval   = 5 * time.Second
)

// Config contains the settings required by the ingest producer.
type Config struct {
	DatabaseURL           string
	KafkaBootstrapServers []string
	UpbitWebSocketURL     string
	RefreshInterval       time.Duration
}

// Load reads and validates producer settings from environment variables.
func Load() (Config, error) {
	host, err := requiredEnvironment("DB_HOST")
	if err != nil {
		return Config{}, err
	}
	port, err := requiredEnvironment("DB_PORT")
	if err != nil {
		return Config{}, err
	}
	databaseName, err := requiredEnvironment("DB_NAME")
	if err != nil {
		return Config{}, err
	}
	user, err := requiredEnvironment("DB_USER")
	if err != nil {
		return Config{}, err
	}
	password, err := requiredEnvironment("DB_PASSWORD")
	if err != nil {
		return Config{}, err
	}
	kafkaBootstrapServers, err := requiredEnvironmentList("KAFKA_BOOTSTRAP_SERVERS")
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

	databaseURL := &url.URL{
		Scheme: "postgres",
		User:   url.UserPassword(user, password),
		Host:   net.JoinHostPort(host, port),
		Path:   databaseName,
	}
	databaseQuery := databaseURL.Query()
	databaseQuery.Set("sslmode", "disable")
	databaseURL.RawQuery = databaseQuery.Encode()

	return Config{
		DatabaseURL:           databaseURL.String(),
		KafkaBootstrapServers: kafkaBootstrapServers,
		UpbitWebSocketURL:     parsedWebSocketURL.String(),
		RefreshInterval:       refreshInterval,
	}, nil
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
