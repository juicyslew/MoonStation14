package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HotspotSpreadTest {
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);

    private static GasMixture mix(double temp, double fuel, double oxygen, double nitrogen) {
        return new GasMixture(Map.of(GasType.TRITIUM, fuel, GasType.OXYGEN, oxygen,
                GasType.NITROGEN, nitrogen), temp);
    }

    private static HotspotSpread.Neighbor neighbor(Direction face, GasMixture gas) {
        return new HotspotSpread.Neighbor(CENTER.relative(face), face, gas, null);
    }

    private static HotspotState burning() { return new HotspotState(1000, 500, true, true); }

    private static HotspotSpread.Result plan(GasMixture source, HotspotState state,
                                               HotspotSpread.Neighbor... neighbors) {
        return HotspotSpread.plan(CENTER, source, state, List.of(neighbors));
    }

    @Test void plansQuarterCellExposureWithoutMutatingAnyGas() {
        var source = mix(900, 2, 2, 0);
        var cold = neighbor(Direction.NORTH, mix(300, 1, 1, 2));
        var originalSource = source.thermalEnergy();
        var originalRecipient = cold.mixture().thermalEnergy();
        var result = plan(source, burning(), cold);
        assertEquals(1, result.offers().size());
        var offer = result.offers().get(cold.position());
        assertEquals(900 * .85, offer.temperatureKelvin());
        assertEquals(250, offer.inputVolumeLiters());
        assertEquals(originalSource, source.thermalEnergy());
        assertEquals(originalRecipient, cold.mixture().thermalEnergy());
        assertEquals(cold.mixture().gasMoles(), mix(300, 1, 1, 2).gasMoles());
        assertTrue(HotspotKernel.expose(cold.mixture(), null, offer.temperatureKelvin(),
                offer.inputVolumeLiters(), false, false).orElseThrow().volumeLiters() == 6250);
        assertThrows(UnsupportedOperationException.class, () -> result.offers().clear());
    }

    @Test void sourceRequiresHotEligibleCommittedHotspot() {
        var neighbor = neighbor(Direction.NORTH, mix(300, 1, 1, 0));
        assertTrue(plan(mix(423.15, 1, 1, 0), burning(), neighbor).offers().isEmpty());
        assertTrue(plan(mix(500, 1, 1, 0), new HotspotState(1000, 500, false, true), neighbor).offers().isEmpty());
        assertEquals(1, plan(mix(300, 1, 1, 0), new HotspotState(1000, 500, true, false), neighbor)
                .offers().size(), "partial source uses local flame temperature despite a cold bulk mixture");
        assertTrue(plan(mix(500, 1, 1, 0), null, neighbor).offers().isEmpty());
    }

    @Test void skipsExistingHotspotsAndNonIgnitableNeighbors() {
        var existing = new HotspotSpread.Neighbor(CENTER.relative(Direction.NORTH), Direction.NORTH,
                mix(300, 1, 1, 0), new HotspotState(100, 400, true, false));
        var bad = neighbor(Direction.SOUTH, mix(300, 0, 1, 0));
        var valid = neighbor(Direction.WEST, mix(300, 1, 1, 0));
        var result = plan(mix(600, 1, 1, 0), burning(), existing, bad, valid);
        assertEquals(List.of(valid.position()), List.copyOf(result.offers().keySet()));
    }

    @Test void acceptsAtMostOneAdjacentNeighborPerFaceIndependentOfInputOrder() {
        List<HotspotSpread.Neighbor> faces = new ArrayList<>();
        for (Direction face : Direction.values()) faces.add(neighbor(face, mix(300, 1, 1, 1)));
        var forward = HotspotSpread.plan(CENTER, mix(900, 6, 6, 0), burning(), faces);
        Collections.reverse(faces);
        var reverse = HotspotSpread.plan(CENTER, mix(900, 6, 6, 0), burning(), faces);
        assertEquals(6, forward.offers().size());
        assertEquals(List.copyOf(forward.offers().keySet()), List.copyOf(reverse.offers().keySet()));
        assertThrows(IllegalArgumentException.class, () -> plan(mix(900, 1, 1, 0), burning(),
                neighbor(Direction.NORTH, mix(300, 1, 1, 0)), neighbor(Direction.NORTH, mix(300, 1, 1, 0))));
    }
}
