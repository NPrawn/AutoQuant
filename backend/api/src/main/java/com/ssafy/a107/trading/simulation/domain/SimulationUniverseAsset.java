package com.ssafy.a107.trading.simulation.domain;

import com.ssafy.a107.asset.domain.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "simulation_universe_asset", schema = "trading")
public class SimulationUniverseAsset {

    @EmbeddedId
    private SimulationUniverseAssetId id;

    @MapsId("simulationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_simulation_universe_asset_run"))
    private SimulationRun simulationRun;

    @MapsId("assetId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_simulation_universe_asset_asset"))
    private Asset asset;

    @Column(name = "selection_rank")
    private Short selectionRank;

    @Column(name = "is_selected", nullable = false)
    private boolean selected;

    protected SimulationUniverseAsset() {
    }

    public SimulationUniverseAsset(SimulationUniverseAssetId id, SimulationRun simulationRun, Asset asset,
                                   Short selectionRank, boolean selected) {
        this.id = Objects.requireNonNull(id);
        this.simulationRun = Objects.requireNonNull(simulationRun);
        this.asset = Objects.requireNonNull(asset);
        if (selectionRank != null && selectionRank <= 0) {
            throw new IllegalArgumentException("Selection rank must be greater than zero");
        }
        this.selectionRank = selectionRank;
        this.selected = selected;
    }

    public SimulationUniverseAssetId getId() { return id; }
    public SimulationRun getSimulationRun() { return simulationRun; }
    public Asset getAsset() { return asset; }
    public Short getSelectionRank() { return selectionRank; }
    public boolean isSelected() { return selected; }
}
