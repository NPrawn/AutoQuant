package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.MarketDataset;
import com.ssafy.a107.contest.domain.MarketDatasetKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/*
 * 마감 뒤에만 공개한다. 뽑은 주 목록은 생성기가 만든 JSON 문자열 그대로 준다 (ADR-0010).
 */
public record ContestDatasetsResponse(
        List<ContestDatasetItem> datasets
) {

    public static ContestDatasetsResponse from(List<MarketDataset> datasets) {
        return new ContestDatasetsResponse(datasets.stream().map(ContestDatasetItem::from).toList());
    }

    public record ContestDatasetItem(
            MarketDatasetKind kind,
            Instant periodStart,
            Instant periodEnd,
            BigDecimal evaluationReturn,
            Instant sourceWindowStart,
            Instant sourceWindowEnd,
            long seed,
            String generatorVersion,
            int filledHours,
            String sourceWeeks
    ) {

        static ContestDatasetItem from(MarketDataset dataset) {
            return new ContestDatasetItem(
                    dataset.getKind(),
                    dataset.getPeriodStart(),
                    dataset.getPeriodEnd(),
                    dataset.getEvaluationReturn(),
                    dataset.getSourceWindowStart(),
                    dataset.getSourceWindowEnd(),
                    dataset.getSeed(),
                    dataset.getGeneratorVersion(),
                    dataset.getFilledHours(),
                    dataset.getSourceWeeks()
            );
        }
    }
}
