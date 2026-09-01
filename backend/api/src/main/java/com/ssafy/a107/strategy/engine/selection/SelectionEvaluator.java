package com.ssafy.a107.strategy.engine.selection;

import com.ssafy.a107.strategy.domain.dsl.Selection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class SelectionEvaluator {

    public List<UUID> select(List<UUID> rankedAssetIds, Selection selection) {
        Objects.requireNonNull(rankedAssetIds);
        Objects.requireNonNull(selection);

        int selectedCount = Math.min(rankedAssetIds.size(), selection.topN());
        List<UUID> selectedAssetIds = new ArrayList<>(selectedCount);
        for (int index = 0; index < selectedCount; index++) {
            selectedAssetIds.add(rankedAssetIds.get(index));
        }
        return List.copyOf(selectedAssetIds);
    }
}
