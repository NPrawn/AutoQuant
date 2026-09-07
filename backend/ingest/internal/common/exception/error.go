package exception

import (
	"errors"
	"fmt"
)

// Error contains a stable code and preserves the original failure for errors.Is and errors.As.
type Error struct {
	code  ErrorCode
	cause error
}

// New creates an error without an underlying cause.
func New(code ErrorCode) *Error {
	return &Error{code: normalizeCode(code)}
}

// Wrap adds a stable pipeline code while preserving the original cause.
func Wrap(code ErrorCode, cause error) *Error {
	if cause == nil {
		return New(code)
	}

	return &Error{
		code:  normalizeCode(code),
		cause: cause,
	}
}

func (e *Error) Error() string {
	if e.cause == nil {
		return fmt.Sprintf("%s: %s", e.code, e.code.message())
	}

	return fmt.Sprintf("%s: %s: %v", e.code, e.code.message(), e.cause)
}

// Unwrap exposes the original cause.
func (e *Error) Unwrap() error {
	return e.cause
}

// Code returns the stable machine-readable error code.
func (e *Error) Code() ErrorCode {
	return e.code
}

// Retryable reports whether the caller may retry the failed operation.
func (e *Error) Retryable() bool {
	return e.code.retryable()
}

// Retryable checks an error chain for a pipeline error the caller may retry.
// An error carrying no pipeline code is treated as permanent, because a failure of
// unknown origin must not be retried blindly.
func Retryable(err error) bool {
	for current := err; current != nil; current = errors.Unwrap(current) {
		pipelineError, ok := current.(*Error)
		if ok {
			return pipelineError.Retryable()
		}
	}

	return false
}

// HasCode checks an error chain for a pipeline error with the requested code.
func HasCode(err error, code ErrorCode) bool {
	for current := err; current != nil; current = errors.Unwrap(current) {
		pipelineError, ok := current.(*Error)
		if ok && pipelineError.code == code {
			return true
		}
	}

	return false
}

func normalizeCode(code ErrorCode) ErrorCode {
	if _, ok := errorDefinitions[code]; !ok {
		return CodeInternal
	}

	return code
}
