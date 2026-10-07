package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionStep;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotSpread.Neighbor;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    void cyclePassPublishesExposureMetadataOnlyAfterSourceCommit() {
        GasMixture sourceBefore = gas(800, 12);
        GasMixture reactedSource = gas(900, 12);
        GasMixture receiverBefore = new GasMixture(Map.of(GasType.TRITIUM, 1., GasType.OXYGEN, 1.), 300, .4);
        HotspotState nextState = new HotspotState(960, 900, true, false);
        var fire = new GasReactionData(Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01),
                373.149, 2000, 0, -1,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)));
        var catalog = new PrototypeCatalog<>(Map.of(net.minecraft.resources.ResourceLocation.parse(
                "moonstation14:tritium_fire"), fire));
        assertTrue(AtmosphereService.potentiallyReactive(reactedSource, catalog.asMap().values()));
        var step = new GasReactionStep.Result(reactedSource, Map.of(),
                reactedSource.thermalEnergy() - sourceBefore.thermalEnergy(), List.of(),
                Optional.of(nextState), true, 0, true);

        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, sourceBefore, RECEIVER, receiverBefore));
        AtomicReference<Optional<HotspotState>> published = new AtomicReference<>();
        AtomicReference<HotspotState> offered = new AtomicReference<>();
        var committed = AtmosphereService.commitHotspotCyclePass(SOURCE, sourceBefore, null,
                ignored -> step, List.of(new Neighbor(RECEIVER, Direction.EAST, receiverBefore, null)), () -> true,
                (pos, mixture) -> { cells.put(pos, mixture); return true; }, changed -> { },
                published::set, (pos, state) -> offered.set(state)).orElseThrow();

        assertSame(reactedSource, cells.get(SOURCE));
        assertSame(receiverBefore, cells.get(RECEIVER));
        assertSame(nextState, published.get().orElseThrow());
        assertEquals(receiverBefore.volumeCubicMeters() * 1000 * .25 * 25, offered.get().volumeLiters(), 1e-3);
        assertEquals(765, offered.get().temperatureKelvin());
        assertEquals(900, committed.preSpreadTemperature());
    }

    @Test
    void rejectedSourceWriteDoesNotPublishHotspotOrExposureMetadata() {
        GasMixture source = gas(800, 12);
        GasMixture receiver = gas(300, 1);
        HotspotState nextState = new HotspotState(960, 900, true, false);
        var step = new GasReactionStep.Result(gas(900, 12), Map.of(), 1, List.of(),
                Optional.of(nextState), true, 0, true);
        AtomicReference<Optional<HotspotState>> published = new AtomicReference<>();
        assertTrue(AtmosphereService.commitHotspotCyclePass(SOURCE, source, null, ignored -> step,
                List.of(new Neighbor(RECEIVER, Direction.EAST, receiver, null)), () -> true,
                new java.util.function.BiPredicate<BlockPos, GasMixture>() {
                    private boolean reject = true;
                    @Override public boolean test(BlockPos pos, GasMixture mixture) {
                        if (reject) { reject = false; return false; }
                        return true;
                    }
                }, changed -> fail("failed source write published gas"),
                published::set, (pos, state) -> fail("failed source write published receiver" )).isEmpty());
        assertNull(published.get());
    }

    private static GasMixture gas(double temperature, double moles) {
        return new GasMixture(Map.of(GasType.TRITIUM, moles, GasType.OXYGEN, moles), temperature);
    }
}
