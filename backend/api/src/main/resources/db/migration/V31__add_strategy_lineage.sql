-- 전략 행은 만들어진 뒤 바뀌지 않고 수정은 새 행을 만든다. 그 새 행이 어느 전략의 몇 번째 판인지 묶는 연결고리다.
-- root_strategy_id 가 NULL 이면 그 행이 뿌리(1판)다. 뿌리 행은 삽입 시점에 자기 번호를 모르므로 비워 두고 조회 때 자기 번호로 읽는다.
-- 기존 행은 모두 뿌리 1판이라 채울 값이 없다.
ALTER TABLE strategy.strategy
    ADD COLUMN root_strategy_id BIGINT  NULL,
    ADD COLUMN version_no       INTEGER NOT NULL DEFAULT 1;

ALTER TABLE strategy.strategy
    ADD CONSTRAINT fk_strategy_root
        FOREIGN KEY (root_strategy_id)
        REFERENCES strategy.strategy (strategy_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT ck_strategy_version_no_positive
        CHECK (version_no >= 1),
    ADD CONSTRAINT ck_strategy_root_is_first_version
        CHECK ((root_strategy_id IS NULL) = (version_no = 1)),
    ADD CONSTRAINT uk_strategy_root_version
        UNIQUE (root_strategy_id, version_no);

CREATE INDEX idx_strategy_root_strategy_id
    ON strategy.strategy (root_strategy_id)
    WHERE root_strategy_id IS NOT NULL;
