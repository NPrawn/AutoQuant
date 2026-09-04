package candle_test

import (
	"testing"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
)

func TestParseAmount(t *testing.T) {
	tests := []struct {
		name  string
		value string
		want  string
	}{
		{name: "정수 가격을 그대로 파싱한다", value: "108831000", want: "108831000"},
		{name: "소수점 거래량을 정밀도 손실 없이 파싱한다", value: "0.01234567", want: "0.01234567"},
		{name: "소수 18자리를 정밀도 손실 없이 파싱한다", value: "0.123456789012345678", want: "0.123456789012345678"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			parsed, err := candledomain.ParseAmount(test.value)
			if err != nil {
				t.Fatalf("ParseAmount(%q) returned error: %v", test.value, err)
			}
			if got := parsed.String(); got != test.want {
				t.Fatalf("ParseAmount(%q) = %s, want %s", test.value, got, test.want)
			}
		})
	}
}

func TestParseAmountRejectsMalformedValue(t *testing.T) {
	tests := []struct {
		name  string
		value string
	}{
		{name: "빈 문자열은 잘못된 페이로드로 처리한다", value: ""},
		{name: "숫자가 아닌 문자열은 잘못된 페이로드로 처리한다", value: "108O31000"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			_, err := candledomain.ParseAmount(test.value)
			if !exception.HasCode(err, exception.CodeCandleInvalidPayload) {
				t.Fatalf("HasCode(err, %s) = false, want true (err = %v)", exception.CodeCandleInvalidPayload, err)
			}
		})
	}
}
