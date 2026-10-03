package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotKernel;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereHotspotReconciliationTest {
    @Test void committedThermalMutationReconcilesHotspotAgainstUpdatedMixtureEnergy() {
        GasMixture before = new GasMixture(Map.of(GasType.TRITIUM, 2.0, GasType.OXYGEN, 2.0), 1800);
        var hotspot = new HotspotKernel.HotspotState(.8, 1700);
        GasMixture after = before.withEnergyDelta(-before.thermalEnergy() * .35);
        var next = AtmosphereService.reconcileHotspotState(before, after, hotspot).orElseThrow();
        double partitionedEnergy = next.fraction() * after.heatCapacity() * next.temperatureKelvin();
        assertTrue(partitionedEnergy <= after.thermalEnergy() + 1e-8);
        assertTrue(next.temperatureKelvin() < hotspot.temperatureKelvin());
        // A later chemistry pass cannot spend more than the refreshed hot partition.
        assertTrue(next.fraction() * after.heatCapacity() * next.temperatureKelvin() <= after.thermalEnergy());
    }

    @Test void failedAtomicPatchDoesNotReconcileOrPublishHotspotMetadata() {
        var first = new net.minecraft.core.BlockPos(0, 1, 0);
        var second = first.east();
        GasMixture before = new GasMixture(Map.of(GasType.TRITIUM, 2.0, GasType.OXYGEN, 2.0), 1800);
        GasMixture after = before.withEnergyDelta(-before.thermalEnergy() * .35);
        var initial = new HotspotKernel.HotspotState(.8, 1700);
        Map<net.minecraft.core.BlockPos, GasMixture> world = new java.util.HashMap<>(Map.of(first, before, second, before));
        Map<net.minecraft.core.BlockPos, HotspotKernel.HotspotState> metadata = new java.util.HashMap<>(Map.of(first, initial, second, initial));
        Map<net.minecraft.core.BlockPos, GasMixture> beforePatch = Map.copyOf(world);
        Map<net.minecraft.core.BlockPos, GasMixture> afterPatch = Map.of(first, after, second, after);
        boolean committed = AtmosphereService.applyPatchTransaction(List.of(first, second), beforePatch, afterPatch,
                (pos, mixture) -> {
                    if (pos.equals(second) && mixture == after) return false;
                    world.put(pos, mixture);
                    return true;
                }, () -> afterPatch.forEach((pos, mixture) -> metadata.put(pos,
                        AtmosphereService.reconcileHotspotState(beforePatch.get(pos), mixture, metadata.get(pos)).orElse(null))));
        assertFalse(committed);
        assertEquals(beforePatch, world, "failed patch restores all gas before publication");
        assertEquals(Map.of(first, initial, second, initial), metadata,
                "hotspot metadata must remain untouched when the transaction is rejected");
    }

    @Test void reconcilesCommittedSourceDebitWithoutCreatingHotEnergy() {
        GasMixture pre = new GasMixture(Map.of(GasType.TRITIUM, 2.0, GasType.OXYGEN, 2.0), 1800);
        var state = new HotspotKernel.HotspotState(.96, 1700);
        GasMixture post = pre.withEnergyDelta(-pre.thermalEnergy() * .10);
        var reconciled = HotspotKernel.reconcileAfterHeatTransfer(pre, post, state).orElseThrow();
        double hotEnergy = reconciled.fraction() * post.heatCapacity() * reconciled.temperatureKelvin();
        assertTrue(hotEnergy <= post.thermalEnergy());
        assertNotEquals(state.temperatureKelvin(), reconciled.temperatureKelvin());
        // Reconciled state remains a valid next-step source partition; receiver deferral is unaffected.
        assertTrue(reconciled.fraction() > 0 && reconciled.fraction() < 1);
    }

}
