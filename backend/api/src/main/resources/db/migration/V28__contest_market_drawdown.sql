-- 국면 점수를 그 장세의 시장 낙폭 대비로 잰다 (ADR-0003 개정, S15P21A107-240).
-- 시장 낙폭은 평가 구간 균등 보유 포트폴리오의 최대 낙폭이다. 엔진이 전략 봉이 닫힐 때마다 자산을 기록해
-- 봉 간격이 촘촘할수록 낙폭이 깊게 잡히므로 1시간봉·4시간봉·일봉 값을 따로 둔다.
-- 1회차 값은 ai/scripts/contest_sim/scoring.py 의 market_drawdowns 가 V26 을 만든 생성기 출력에서 잰 값이다.
-- 다음 회차부터는 export_seed_sql.py 가 데이터셋 행에 함께 쓴다.

ALTER TABLE contest.market_dataset
    ADD COLUMN market_mdd_1h NUMERIC(12,8) NULL,
    ADD COLUMN market_mdd_4h NUMERIC(12,8) NULL,
    ADD COLUMN market_mdd_1d NUMERIC(12,8) NULL;

UPDATE contest.market_dataset AS dataset
SET market_mdd_1h = market.mdd_1h,
    market_mdd_4h = market.mdd_4h,
    market_mdd_1d = market.mdd_1d
FROM (VALUES
    ('GENERAL',  0.38448381, 0.38448381, 0.32298406),
    ('BULL',     0.36501658, 0.35704722, 0.35360052),
    ('BEAR',     0.51515961, 0.50414435, 0.49958768),
    ('SIDEWAYS', 0.39248419, 0.39028694, 0.35546703)
) AS market(kind, mdd_1h, mdd_4h, mdd_1d)
WHERE dataset.round_id = '0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10'
  AND dataset.kind = market.kind;

ALTER TABLE contest.market_dataset
    ALTER COLUMN market_mdd_1h SET NOT NULL,
    ALTER COLUMN market_mdd_4h SET NOT NULL,
    ALTER COLUMN market_mdd_1d SET NOT NULL,
    ADD CONSTRAINT ck_market_dataset_market_mdd CHECK (
        market_mdd_1h BETWEEN 0 AND 1
        AND market_mdd_4h BETWEEN 0 AND 1
        AND market_mdd_1d BETWEEN 0 AND 1
    );

-- 산식이 바뀌어 저장된 점수를 지운다. 1회차는 제출을 받는 중이라 스케줄러가 끝난 백테스트로 다시 채점한다.
DELETE FROM contest.contest_result;
