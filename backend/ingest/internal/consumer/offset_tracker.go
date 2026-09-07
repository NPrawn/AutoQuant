package consumer

// OffsetTracker decides how far a partition may be committed.
//
// A candle is built from many records, and one partition carries several markets
// at once. Committing past the first record of a candle that is still open would
// drop the material needed to rebuild that candle after a crash, so the tracker
// remembers where each open candle started and reports the lowest of those
// offsets. Only when no candle is open may the partition commit everything it has
// processed.
type OffsetTracker struct {
	openedAt   map[string]int64
	nextOffset int64
	advanced   bool
}

func NewOffsetTracker() *OffsetTracker {
	return &OffsetTracker{openedAt: make(map[string]int64)}
}

// Observe records that the offset feeds the currently open candle of an asset.
// Later offsets of the same candle are ignored because only the first one limits
// the commit point.
func (t *OffsetTracker) Observe(assetID string, offset int64) {
	if _, exists := t.openedAt[assetID]; exists {
		return
	}

	t.openedAt[assetID] = offset
}

// Close reports that the candle of an asset was finalized, releasing the offset
// it was holding.
func (t *OffsetTracker) Close(assetID string) {
	delete(t.openedAt, assetID)
}

// Advance records that the record at this offset was fully processed.
func (t *OffsetTracker) Advance(offset int64) {
	if next := offset + 1; !t.advanced || next > t.nextOffset {
		t.nextOffset = next
		t.advanced = true
	}
}

// CommitOffset returns the offset to commit, meaning the offset the partition
// would resume from. It reports false while nothing has been processed yet.
func (t *OffsetTracker) CommitOffset() (int64, bool) {
	if !t.advanced {
		return 0, false
	}

	commit := t.nextOffset
	for _, opened := range t.openedAt {
		if opened < commit {
			commit = opened
		}
	}

	return commit, true
}
