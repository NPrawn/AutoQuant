package exception

// ErrorCode identifies a pipeline failure independently of its wrapped cause.
type ErrorCode string

const (
	// 공통
	CodeInternal ErrorCode = "COMMON-500-001"

	// 캔들
	CodeCandleInvalidInterval ErrorCode = "CANDLE-400-001"
	CodeCandleInvalidPayload  ErrorCode = "CANDLE-400-002"
	CodeCandleSyncMismatch    ErrorCode = "CANDLE-409-001"

	// 업비트
	CodeUpbitUnavailable     ErrorCode = "UPBIT-503-001"
	CodeUpbitInvalidResponse ErrorCode = "UPBIT-502-001"

	// 카프카
	CodeKafkaPublishFailed  ErrorCode = "KAFKA-503-001"
	CodeKafkaConsumeFailed  ErrorCode = "KAFKA-503-002"
	CodeKafkaCommitFailed   ErrorCode = "KAFKA-503-003"
	CodeKafkaInvalidMessage ErrorCode = "KAFKA-400-001"

	// 레디스
	CodeRedisWriteFailed   ErrorCode = "REDIS-503-001"
	CodeRedisReadFailed    ErrorCode = "REDIS-503-002"
	CodeRedisInvalidValue  ErrorCode = "REDIS-500-001"
	CodeRedisPublishFailed ErrorCode = "REDIS-503-003"

	// 데이터베이스
	CodeDatabaseUnavailable         ErrorCode = "DATABASE-503-001"
	CodeDatabaseConstraintViolation ErrorCode = "DATABASE-409-001"
)

type errorDefinition struct {
	message   string
	retryable bool
}

var errorDefinitions = map[ErrorCode]errorDefinition{
	CodeInternal:                    {message: "internal pipeline error", retryable: false},
	CodeCandleInvalidInterval:       {message: "invalid candle interval", retryable: false},
	CodeCandleInvalidPayload:        {message: "invalid candle payload", retryable: false},
	CodeCandleSyncMismatch:          {message: "minute candle differs from Upbit availability", retryable: false},
	CodeUpbitUnavailable:            {message: "Upbit is unavailable", retryable: true},
	CodeUpbitInvalidResponse:        {message: "Upbit returned an invalid response", retryable: false},
	CodeKafkaPublishFailed:          {message: "failed to publish Kafka message", retryable: true},
	CodeKafkaConsumeFailed:          {message: "failed to consume Kafka message", retryable: true},
	CodeKafkaCommitFailed:           {message: "failed to commit Kafka offset", retryable: true},
	CodeKafkaInvalidMessage:         {message: "invalid Kafka message", retryable: false},
	CodeRedisWriteFailed:            {message: "failed to write Redis value", retryable: true},
	CodeRedisReadFailed:             {message: "failed to read Redis value", retryable: true},
	CodeRedisInvalidValue:           {message: "invalid Redis value", retryable: false},
	CodeRedisPublishFailed:          {message: "failed to publish Redis message", retryable: true},
	CodeDatabaseUnavailable:         {message: "database is unavailable", retryable: true},
	CodeDatabaseConstraintViolation: {message: "database constraint violation", retryable: false},
}

func (c ErrorCode) message() string {
	definition, ok := errorDefinitions[c]
	if !ok {
		return errorDefinitions[CodeInternal].message
	}

	return definition.message
}

func (c ErrorCode) retryable() bool {
	definition, ok := errorDefinitions[c]
	if !ok {
		return false
	}

	return definition.retryable
}
