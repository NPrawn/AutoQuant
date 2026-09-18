package com.ssafy.a107.asset.controller;

import com.ssafy.a107.asset.controller.response.ChartAssetResponse;
import com.ssafy.a107.asset.service.AssetReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/assets")
public class AssetController {
    private final AssetReadService assetReadService;

    public AssetController(AssetReadService assetReadService) {
        this.assetReadService = assetReadService;
    }

    @GetMapping
    public List<ChartAssetResponse> findChartAssets() {
        return assetReadService.findChartAssets().stream().map(ChartAssetResponse::from).toList();
    }
}
