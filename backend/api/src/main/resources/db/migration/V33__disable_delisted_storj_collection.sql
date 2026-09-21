-- KRW-STORJ 는 업비트 상장폐지로 캔들 API 가 404 를 준다. 수집 대상으로 두면 5분마다 재시도하고 종목 목록에도 남는다.
UPDATE reference.asset AS a
   SET trading_status           = 'DELISTED',
       trading_support_ended_at = COALESCE(
           a.trading_support_ended_at,
           (
               SELECT MAX(mc.open_time) + INTERVAL '1 minute'
                 FROM market_data.minute_candle mc
                WHERE mc.asset_id = a.asset_id
                  AND mc.interval_minutes = 1
           ),
           CURRENT_TIMESTAMP
       ),
       updated_at               = CURRENT_TIMESTAMP
 WHERE a.exchange = 'UPBIT'
   AND a.exchange_market_code = 'KRW-STORJ'
   AND a.trading_status <> 'DELISTED';

UPDATE ops.market_data_collection_target AS t
   SET collection_status = 'DISABLED',
       updated_at        = CURRENT_TIMESTAMP
 WHERE t.collection_status = 'ENABLED'
   AND t.asset_id IN (
       SELECT a.asset_id
         FROM reference.asset a
        WHERE a.exchange = 'UPBIT'
          AND a.exchange_market_code = 'KRW-STORJ'
   );
