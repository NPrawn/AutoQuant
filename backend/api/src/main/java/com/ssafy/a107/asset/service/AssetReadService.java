package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssetReadService {

    private final CollectedAssetReadService collectedAssetReadService;

    public AssetReadService(CollectedAssetReadService collectedAssetReadService) {
        this.collectedAssetReadService = collectedAssetReadService;
    }

    /*
     * 화면에 보일 종목 목록. 후보군과 같은 기준이라 화면이 적는 종목 수와 엔진이 쓰는 수가 어긋나지 않는다.
     * 거래소에서 거래 중이어도 우리가 수집하지 않는 종목은 캔들이 없어 여기에서 뺀다.
     */
    public List<Asset> findChartAssets() {
        return collectedAssetReadService.findCollectedTradingAssets(Exchange.UPBIT, "KRW");
    }
}
