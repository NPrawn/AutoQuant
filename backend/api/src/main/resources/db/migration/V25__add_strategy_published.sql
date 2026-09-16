-- 공개 여부. 기본은 비공개다. 이미 있는 전략을 주인 모르게 남에게 보이지 않는다.
ALTER TABLE strategy.strategy
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE;

-- 공개 전략 목록과 복제 대상 조회가 쓰는 경로다.
CREATE INDEX idx_strategy_published
    ON strategy.strategy (strategy_id)
    WHERE published AND deleted_at IS NULL;
