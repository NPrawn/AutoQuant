package com.ssafy.a107.asset.controller;

import com.ssafy.a107.asset.controller.request.CreateAssetRequest;
import com.ssafy.a107.asset.controller.response.AssetResponse;
import com.ssafy.a107.asset.service.AssetRegistrationFacade;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/assets")
public class AssetAdminController {

    private final AssetRegistrationFacade assetRegistrationFacade;

    public AssetAdminController(AssetRegistrationFacade assetRegistrationFacade) {
        this.assetRegistrationFacade = assetRegistrationFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse createAsset(@Valid @RequestBody CreateAssetRequest request) {
        return AssetResponse.from(assetRegistrationFacade.registerAsset(
                request.exchange(),
                request.exchangeMarketCode()
        ));
    }
}
