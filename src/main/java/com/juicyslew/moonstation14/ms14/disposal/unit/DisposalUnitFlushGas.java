package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.exposure.FiniteExposureAir;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransaction;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.EnumSet;
import java.util.Map;

/**
 * Finite SS14 unit-reservoir recharge at the flush boundary. If the room mixture is unavailable, no gas is sampled;
 * the flush can still carry the unit's explicitly owned empty default inventory. That empty holder inventory is known
 * local state, not an inference that the room was vacuum.
 */
public final class DisposalUnitFlushGas {
    private static final double ATM_KPA = 101.325;
    private static final double R = 8.31446261815324;
    private static final double TARGET_KPA = .25 * ATM_KPA * 1.01;
    private DisposalUnitFlushGas() { }

    public static GasMixture prepare(ServerLevel level, DisposalUnitBlockEntity unit) {
        FiniteExposureAir reservoir = unit.reservoirAir();
        var before = reservoir.sample().orElseThrow();
        var source = AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, unit.getBlockPos().above()).orElse(null);
        if (source == null) return before.mixture(); // Unknown is not inferred vacuum; carry the explicit owned inventory.
        double deficitKpa = Math.max(0, TARGET_KPA - before.pressureKpa());
        double maxMoles = .1 * deficitKpa * unit.prototype().airLiters() / (before.temperatureKelvin() * R);
        if (!(maxMoles > 0) || !Double.isFinite(maxMoles)) return before.mixture();
        PipeGasTransaction.Endpoint destination = endpoint(reservoir);
        var plan = PipeGasTransaction.plan(source, destination, 1.0, unit.prototype().airLiters() / 1000.0,
                maxMoles, TARGET_KPA, EnumSet.allOf(GasType.class));
        if (plan.isEmpty()) return before.mixture();
        try { PipeGasTransaction.commit(source, destination, plan.get()); }
        catch (RuntimeException rolledBackOrRecoveryRequired) {
            // Keep the endpoint-owned inventory either as rolled back or as whatever confirmed raw write remains.
        }
        return reservoir.sample().orElseThrow().mixture();
    }

    private static PipeGasTransaction.Endpoint endpoint(FiniteExposureAir air) {
        return new PipeGasTransaction.Endpoint() {
            private GasMixture current = air.sample().orElseThrow().mixture();
            private long version;
            @Override public Object identity() { return air; }
            @Override public long version() { return version; }
            @Override public GasMixture snapshot() { return current; }
            @Override public boolean eligible() { return true; }
            @Override public boolean writeRaw(GasMixture expected, GasMixture next) {
                if (expected != null && current != expected) return false;
                GasMixture actual = air.sample().orElseThrow().mixture();
                if (!air.replaceInventory(actual, next)) return false;
                current = next; return true;
            }
            @Override public void publish() { version++; }
        };
    }
}
