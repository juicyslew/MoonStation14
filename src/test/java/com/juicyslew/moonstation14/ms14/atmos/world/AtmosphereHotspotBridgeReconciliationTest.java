package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionStep;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotKernel;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotSpread.Neighbor;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereHotspotBridgeReconciliationTest {
    private static final BlockPos SOURCE = new BlockPos(0, 64, 0);
    private static final BlockPos RECEIVER = SOURCE.east();

    @Test
    void actualSpreadBridgeReconcilesAndConservesThermalEnergyAndRollsBackRejectedReceiver() {
        GasMixture sourceBefore = gas(800, 12);
        GasMixture reactedSource = gas(900, 12);
        GasMixture receiverBefore = gas(300, 1);
        var nextState = new HotspotKernel.HotspotState(.96, 900);
        double chemicalEnergy = reactedSource.thermalEnergy() - sourceBefore.thermalEnergy();
        var step = new GasReactionStep.Result(reactedSource, Map.of(), chemicalEnergy, List.of(),
                Optional.of(nextState), true, 0, true);
        var fire = new GasReactionData(Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01),
                373.149, 2000, 0, -1,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)));
        var catalog = new PrototypeCatalog<>(Map.of(ResourceLocation.parse("moonstation14:tritium_fire"), fire));
        assertTrue(AtmosphereService.potentiallyReactive(reactedSource, catalog.asMap().values()));

        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, sourceBefore, RECEIVER, receiverBefore));
        ReactionWorkQueue work = new ReactionWorkQueue();
        long currentDue = 8;
        AtomicReference<Optional<HotspotKernel.HotspotState>> published = new AtomicReference<>();
        Map<Direction, Double> faceHeat = new HashMap<>();
        var committed = AtmosphereService.commitHotspotSpreadWithTemperature(SOURCE, sourceBefore, null,
                ignored -> step, List.of(new Neighbor(RECEIVER, Direction.EAST, receiverBefore)), () -> true,
                (pos, mixture) -> { cells.put(pos, mixture); return true; }, changed -> { }, state -> {
                    published.set(state);
                    work.setHotspot(SOURCE, state);
                },
                (pos, state) -> { work.setHotspot(pos, Optional.of(state)); work.deferReceiver(currentDue, pos); },
                faceHeat::putAll).orElseThrow();

        GasMixture sourceAfter = cells.get(SOURCE);
        GasMixture receiverAfter = cells.get(RECEIVER);
        var state = published.get().orElseThrow();
        double debit = reactedSource.thermalEnergy() - sourceAfter.thermalEnergy();
        double credit = receiverAfter.thermalEnergy() - receiverBefore.thermalEnergy();
        assertTrue(debit > 0, "spread must debit source energy");
        assertTrue(sourceAfter.temperatureKelvin() < reactedSource.temperatureKelvin());
        assertTrue(faceHeat.get(Direction.EAST) > 0);
        assertTrue(state.fraction() * sourceAfter.heatCapacity() * state.temperatureKelvin()
                <= sourceAfter.thermalEnergy());
        double expectedHotEnergy = nextState.fraction() * reactedSource.heatCapacity()
                * nextState.temperatureKelvin() * sourceAfter.thermalEnergy() / reactedSource.thermalEnergy();
        assertEquals(expectedHotEnergy, state.fraction() * sourceAfter.heatCapacity() * state.temperatureKelvin(),
                Math.max(1e-6, expectedHotEnergy * 1e-12));
        assertEquals(debit, credit, Math.max(1e-6, debit * 1e-10));
        assertEquals(sourceBefore.thermalEnergy() + chemicalEnergy + receiverBefore.thermalEnergy(),
                sourceAfter.thermalEnergy() + receiverAfter.thermalEnergy(), 1e-5);
        for (GasType type : GasType.values())
            assertEquals(sourceBefore.moles(type) + receiverBefore.moles(type),
                    sourceAfter.moles(type) + receiverAfter.moles(type), 1e-10, type.toString());
        assertTrue(committed.result().mixture() == sourceAfter);
        assertTrue(AtmosphereService.schedulerReactionEligible(work, SOURCE, sourceAfter, catalog.asMap().values()),
                "reconciled cooled source must not be spuriously quenched");
        assertTrue(GasReactionStep.evaluate(catalog, sourceAfter, work.hotspot(SOURCE)).nextState().isPresent());
        assertNotNull(work.hotspot(RECEIVER));
        assertTrue(work.reacted(currentDue, RECEIVER));
        assertFalse(work.reacted(currentDue + 1, RECEIVER));

        AtomicReference<Optional<HotspotKernel.HotspotState>> rejectedPublication = new AtomicReference<>();
        var rollbackCells = new HashMap<BlockPos, GasMixture>(Map.of(SOURCE, sourceBefore, RECEIVER, receiverBefore));
        assertTrue(AtmosphereService.commitHotspotSpreadWithTemperature(SOURCE, sourceBefore, null,
                ignored -> step, List.of(new Neighbor(RECEIVER, Direction.EAST, receiverBefore)), () -> true,
                (pos, mixture) -> {
                    if (pos.equals(RECEIVER) && !mixture.equals(receiverBefore)) return false;
                    rollbackCells.put(pos, mixture);
                    return true;
                }, changed -> { }, rejectedPublication::set,
                (pos, receiverState) -> fail("rejected transaction published receiver"), heat -> fail()).isEmpty());
        assertNull(rejectedPublication.get(), "rejected receiver write must not publish source state");
        assertSame(sourceBefore, rollbackCells.get(SOURCE));
        assertSame(receiverBefore, rollbackCells.get(RECEIVER));
    }

    private static GasMixture gas(double temperature, double moles) {
        return new GasMixture(Map.of(GasType.TRITIUM, moles, GasType.OXYGEN, moles), temperature);
    }
}
