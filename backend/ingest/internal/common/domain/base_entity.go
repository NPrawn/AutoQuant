package domain

import "time"

// BaseEntity contains timestamps shared by persisted domain entities.
type BaseEntity struct {
	CreatedAt time.Time
	UpdatedAt time.Time
}
