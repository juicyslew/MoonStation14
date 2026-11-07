package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.Map;

/** Test-only trusted finite-gas seed operation for GameTests in other feature packages. */
public final class PipeGasFixtureTestSupport {
    private PipeGasFixtureTestSupport() { }

    /** Rebuilds finite fixture gas at the current network's physical volume, without changing its inventory. */
    public static GasMixture atCurrentNetworkVolume(PipeGasInventory inventory, PipeTopology.NodeKey key,
                                                    Map<GasType, Double> moles, double temperatureKelvin) {
        double volume = inventory.network(key).orElseThrow().mixture().volumeCubicMeters();
        EnumMap<GasType, Double> copiedMoles = new EnumMap<>(GasType.class);
        copiedMoles.putAll(moles);
        return new GasMixture(copiedMoles, temperatureKelvin, volume);
    }

    public static boolean seedLane(ServerLevel level, PipeTopology.NodeKey key, java.util.UUID incarnation,
                                   long expectedVersion, GasMixture mixture) {
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return false;
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry entry = before.get(key).orElse(null);
        if (entry == null || !entry.active() || !entry.incarnation().equals(incarnation)
                || entry.version() != expectedVersion) return false;
        try {
            PipeGasInventory after = before.withMixture(key, incarnation, expectedVersion, mixture);
            return data.replace(before.revision(), before, after);
        } catch (RuntimeException invalid) {
            return false;
        }
    }
}
