package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.domain.MarketDataCollectionStatus;
import com.ssafy.a107.asset.domain.MarketDataCollectionTarget;
import com.ssafy.a107.asset.repository.AssetRepository;
import com.ssafy.a107.asset.repository.MarketDataCollectionTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/*
 * 전략이 후보로 삼는 종목이다. 거래지원 기간만 보면 수집을 끊은 종목까지 후보에 들어오는데,
 * 그런 종목은 캔들이 그 시점에서 멈춰 있어 같은 기준으로 순위를 매길 수 없다.
 * 그래서 수집 중인 종목 중 그 시점에 거래지원 중이던 것으로 좁힌다.
 *
 * 수집 목록을 바꾸면 과거 구간 결과도 달라진다. 오늘 고른 종목으로 과거를 재는 셈이라
 * 생존 편향이 생기지만, 캔들이 수집 종목에만 있어 어차피 계산에서 빠지던 것을 규칙으로 드러낸 것이다.
 */
@Service
@Transactional(readOnly = true)
public class CollectedAssetReadService {

    private final AssetRepository assetRepository;
    private final MarketDataCollectionTargetRepository collectionTargetRepository;

    public CollectedAssetReadService(
            AssetRepository assetRepository,
            MarketDataCollectionTargetRepository collectionTargetRepository
    ) {
        this.assetRepository = assetRepository;
        this.collectionTargetRepository = collectionTargetRepository;
    }

    /*
     * 수집 중인 종목의 식별자만 필요한 곳에서 쓴다. 거래지원 기간으로 좁히지 않으므로
     * 거래지원이 끝난 종목도 들어온다. 이미 쌓인 봉은 상장 폐지 뒤에도 그대로 남기 때문이다.
     */
    public List<UUID> findCollectedAssetIds() {
        return collectionTargetRepository.findByCollectionStatus(MarketDataCollectionStatus.ENABLED).stream()
                .map(MarketDataCollectionTarget::getAssetId)
                .toList();
    }

    public List<Asset> findAvailableAt(Exchange exchange, String quoteCurrencyCode, Instant evaluationAt) {
        return keepCollected(assetRepository.findAllAvailableAt(exchange, quoteCurrencyCode, evaluationAt));
    }

    public List<Asset> findAvailableDuring(
            Exchange exchange,
            String quoteCurrencyCode,
            Instant periodStart,
            Instant periodEnd
    ) {
        return keepCollected(assetRepository.findAllAvailableDuring(exchange, quoteCurrencyCode, periodStart, periodEnd));
    }

    // 수집 대상은 서비스가 등록한 종목 수만큼이라 한 번에 읽어 걸러도 부담이 없다.
    private List<Asset> keepCollected(List<Asset> assets) {
        Set<UUID> collected = collectionTargetRepository
                .findByCollectionStatus(MarketDataCollectionStatus.ENABLED).stream()
                .map(MarketDataCollectionTarget::getAssetId)
                .collect(Collectors.toUnmodifiableSet());

        return assets.stream()
                .filter(asset -> collected.contains(asset.getAssetId()))
                .toList();
    }
}
