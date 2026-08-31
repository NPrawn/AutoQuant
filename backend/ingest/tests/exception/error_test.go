package exception_test

import (
	"errors"
	"testing"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
)

func TestErrorRetryable(t *testing.T) {
	tests := []struct {
		name      string
		code      exception.ErrorCode
		retryable bool
	}{
		{name: "외부 시스템 오류는 재시도할 수 있다", code: exception.CodeKafkaPublishFailed, retryable: true},
		{name: "잘못된 캔들 데이터는 재시도하지 않는다", code: exception.CodeCandleInvalidPayload, retryable: false},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			err := exception.New(test.code)
			if got := err.Retryable(); got != test.retryable {
				t.Fatalf("Retryable() = %v, want %v", got, test.retryable)
			}
		})
	}
}

func TestWrapPreservesCauseAndCode(t *testing.T) {
	cause := errors.New("connection refused")
	err := exception.Wrap(exception.CodeDatabaseUnavailable, cause)

	if !errors.Is(err, cause) {
		t.Fatalf("errors.Is(err, cause) = false, want true")
	}
	if !exception.HasCode(err, exception.CodeDatabaseUnavailable) {
		t.Fatalf("HasCode(err, %s) = false, want true", exception.CodeDatabaseUnavailable)
	}
}

func TestUnknownCodeBecomesInternalError(t *testing.T) {
	err := exception.New(exception.ErrorCode("UNKNOWN"))
	if got := err.Code(); got != exception.CodeInternal {
		t.Fatalf("Code() = %s, want %s", got, exception.CodeInternal)
	}
}
