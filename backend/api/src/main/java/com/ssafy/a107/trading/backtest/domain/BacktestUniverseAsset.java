package com.ssafy.a107.trading.backtest.domain;

import com.ssafy.a107.asset.domain.Asset;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.util.Objects;

@Entity
@Table(name = "backtest_universe_asset", schema = "trading")
public class BacktestUniverseAsset implements Persistable<BacktestUniverseAssetId> {

    @EmbeddedId
    private BacktestUniverseAssetId id;

    @Transient
    private boolean newEntity = true;

    @MapsId("backtestId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "backtest_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_backtest_universe_asset_run")
    )
    private BacktestRun backtestRun;

    @MapsId("assetId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "asset_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_backtest_universe_asset_asset")
    )
    private Asset asset;

    @Column(name = "selection_rank")
    private Short selectionRank;

    @Column(name = "is_selected", nullable = false)
    private boolean selected;

    protected BacktestUniverseAsset() {
    }

    public BacktestUniverseAsset(
            BacktestUniverseAssetId id,
            BacktestRun backtestRun,
            Asset asset,
            Short selectionRank,
            boolean selected
    ) {
        this.id = Objects.requireNonNull(id);
        this.backtestRun = Objects.requireNonNull(backtestRun);
        this.asset = Objects.requireNonNull(asset);

        if (selectionRank != null && selectionRank <= 0) {
            throw new IllegalArgumentException("Selection rank must be greater than zero");
        }

        this.selectionRank = selectionRank;
        this.selected = selected;
    }

    @Override
    public BacktestUniverseAssetId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    private void markPersisted() {
        newEntity = false;
    }

    public BacktestRun getBacktestRun() {
        return backtestRun;
    }

    public Asset getAsset() {
        return asset;
    }

    public Short getSelectionRank() {
        return selectionRank;
    }

    public boolean isSelected() {
        return selected;
    }
}
