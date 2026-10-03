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
        return new HotspotSpread.Neighbor(CENTER.relative(face), face, gas);
    }

    private static HotspotSpread.Result plan(GasMixture source, boolean burning,
                                               HotspotSpread.Neighbor... neighbors) {
        return HotspotSpread.plan(CENTER, source, burning, List.of(neighbors));
    }

    private static void conserved(GasMixture source, List<HotspotSpread.Neighbor> neighbors,
                                  HotspotSpread.Result result) {
        double original = source.thermalEnergy();
        double after = result.source().thermalEnergy();
        double debited = original - after;
        double credited = 0;
        for (var neighbor : neighbors) {
            original += neighbor.mixture().thermalEnergy();
            var offer = result.offers().get(neighbor.face());
            if (offer == null) after += neighbor.mixture().thermalEnergy();
            else {
                after += offer.mixture().thermalEnergy();
                credited += offer.energyJoules();
                assertEquals(offer.energyJoules(), offer.mixture().thermalEnergy()
                        - neighbor.mixture().thermalEnergy(), 1e-8);
                assertEquals(neighbor.mixture().gasMoles(), offer.mixture().gasMoles());
                assertEquals(.25, offer.hotFraction());
                assertEquals(source.temperatureKelvin() * .85, offer.hotTemperatureKelvin());
                assertEquals(.25 * neighbor.mixture().heatCapacity()
                        * (offer.hotTemperatureKelvin() - neighbor.mixture().temperatureKelvin()),
                        offer.energyJoules(), 1e-8);
            }
        }
        assertEquals(original, after, Math.max(1e-8, original * 1e-12));
        assertEquals(debited, credited, Math.max(1e-8, credited * 1e-12));
        assertEquals(source.gasMoles(), result.source().gasMoles());
    }

    @Test void zeroAndOneNeighborDoNotCloneHeat() {
        var source = mix(500, 2, 2, 0);
        assertSame(source, plan(source, true).source());
        assertTrue(plan(source, true).offers().isEmpty());
        var cold = neighbor(Direction.NORTH, mix(300, 1, 1, 2));
        var result = plan(source, true, cold);
        assertEquals(1, result.offers().size());
        assertTrue(result.source().temperatureKelvin() >= 374.15);
        conserved(source, List.of(cold), result);
        assertThrows(UnsupportedOperationException.class,
                () -> result.offers().put(Direction.UP, result.offers().get(Direction.NORTH)));
    }

    @Test void sixFacesAndReversedInputGiveIdenticalOffers() {
        var source = mix(900, 6, 6, 0);
        List<HotspotSpread.Neighbor> faces = new ArrayList<>();
        for (Direction face : Direction.values()) faces.add(neighbor(face, mix(300, 1, 1, 1)));
        var forward = HotspotSpread.plan(CENTER, source, true, faces);
        Collections.reverse(faces);
        var reversed = HotspotSpread.plan(CENTER, source, true, faces);
        assertEquals(6, forward.offers().size());
        assertEquals(List.copyOf(forward.offers().keySet()), List.copyOf(reversed.offers().keySet()));
        assertEquals(forward.source().thermalEnergy(), reversed.source().thermalEnergy());
        for (Direction face : Direction.values())
            assertEquals(forward.offers().get(face).energyJoules(), reversed.offers().get(face).energyJoules());
        conserved(source, faces, forward);
    }

    @Test void shortageSkipsUnaffordableFaceAndNeverSpendsReservedHeat() {
        var source = mix(500, 1, 1, 0);
        var expensive = neighbor(Direction.DOWN, mix(300, 1, 1, 10));
        var cheap = neighbor(Direction.UP, mix(360, .04, .04, 0));
        var result = plan(source, true, expensive, cheap);
        assertEquals(1, result.offers().size());
        assertTrue(result.offers().containsKey(Direction.UP));
        assertTrue(result.source().temperatureKelvin() >= 374.15);
        conserved(source, List.of(expensive, cheap), result);
    }

    @Test void hotPortionCanIgniteWhileRecipientBulkRemainsCold() {
        var source = mix(500, 1, 1, 0);
        var cold = neighbor(Direction.NORTH, mix(300, 1, 1, 2));
        var offer = plan(source, true, cold).offers().get(Direction.NORTH);
        assertNotNull(offer);
        assertTrue(offer.hotTemperatureKelvin() > 373.15);
        assertTrue(offer.mixture().temperatureKelvin() < 373.15);
        assertEquals(300 + offer.energyJoules() / cold.mixture().heatCapacity(),
                offer.mixture().temperatureKelvin(), 1e-10);
    }

    @Test void exactGatesCapacityAndOxygenFuelRequirements() {
        var cold = neighbor(Direction.NORTH, mix(300, 1, 1, 0));
        var source = mix(500, 1, 1, 0);
        assertTrue(plan(mix(423.15, 1, 1, 0), true, cold).offers().isEmpty());
        assertEquals(1, plan(mix(500, 1, 1, 0), true,
                neighbor(Direction.NORTH, mix(300, .04, .04, 0))).offers().size());
        assertTrue(plan(source, false, cold).offers().isEmpty());
        assertTrue(plan(GasMixture.vacuum(), true, cold).offers().isEmpty());
        assertTrue(plan(source, true, neighbor(Direction.NORTH, GasMixture.vacuum())).offers().isEmpty());
        for (var gas : List.of(mix(300, 1, 0, 0), mix(300, 0, 1, 0),
                mix(300, .03999, 1, 0), mix(300, 1, .03999, 0),
                mix(373.15, 1, 1, 0))) {
            // Exactly 373.15 K is eligible, but warm recipients (>373.15 K) are not.
            if (gas.temperatureKelvin() == 373.15) assertEquals(1, plan(source, true, neighbor(Direction.NORTH, gas)).offers().size());
            else assertTrue(plan(source, true, neighbor(Direction.NORTH, gas)).offers().isEmpty());
        }
        assertEquals(1, plan(source, true, neighbor(Direction.NORTH, mix(373.150001, 1, 1, 0))).offers().size());
        assertTrue(plan(mix(500, 0, 1, 0), true, cold).offers().isEmpty());
        assertTrue(plan(mix(500, 1, 0, 0), true, cold).offers().isEmpty());
    }

    @Test void rejectsDuplicateAndNonadjacentNeighbors() {
        var gas = mix(500, 1, 1, 0);
        var face = neighbor(Direction.NORTH, gas);
        assertThrows(IllegalArgumentException.class, () -> plan(gas, true, face, face));
        assertThrows(IllegalArgumentException.class, () -> plan(gas, true,
                new HotspotSpread.Neighbor(CENTER, Direction.NORTH, gas)));
    }

    @Test void plasmaAndInertSpeciesStayWithTheirOriginalCells() {
        var source = new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0,
                GasType.WATER_VAPOR, .5), 700);
        var recipient = new GasMixture(Map.of(GasType.PLASMA, .04, GasType.OXYGEN, .04,
                GasType.NITROGEN, 1.0, GasType.CARBON_DIOXIDE, .3), 300);
        var neighbor = neighbor(Direction.WEST, recipient);
        var result = plan(source, true, neighbor);
        assertEquals(1, result.offers().size());
        conserved(source, List.of(neighbor), result);
        assertEquals(0, result.offers().get(Direction.WEST).mixture().moles(GasType.TRITIUM));
        assertEquals(0, result.source().moles(GasType.PLASMA));
    }
}
