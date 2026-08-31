package exception

import (
	"errors"
	"testing"
)

func TestErrorRetryable(t *testing.T) {
	tests := []struct {
		name      string
		code      ErrorCode
		retryable bool
	}{
		{
			name:      "외부 시스템 오류는 재시도할 수 있다",
			code:      CodeKafkaPublishFailed,
			retryable: true,
		},
		{
			name:      "잘못된 캔들 데이터는 재시도하지 않는다",
			code:      CodeCandleInvalidPayload,
			retryable: false,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			err := New(test.code)

			if got := err.Retryable(); got != test.retryable {
				t.Fatalf("Retryable() = %v, want %v", got, test.retryable)
			}
		})
	}
}

func TestWrapPreservesCauseAndCode(t *testing.T) {
	cause := errors.New("connection refused")
	err := Wrap(CodeDatabaseUnavailable, cause)

	if !errors.Is(err, cause) {
		t.Fatalf("errors.Is(err, cause) = false, want true")
	}
	if !HasCode(err, CodeDatabaseUnavailable) {
		t.Fatalf("HasCode(err, %s) = false, want true", CodeDatabaseUnavailable)
	}
}

func TestUnknownCodeBecomesInternalError(t *testing.T) {
	err := New(ErrorCode("UNKNOWN"))

	if got := err.Code(); got != CodeInternal {
		t.Fatalf("Code() = %s, want %s", got, CodeInternal)
	}
}
