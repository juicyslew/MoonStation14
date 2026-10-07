package com.juicyslew.moonstation14.ms14.disposal.transport;

import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import java.util.Map;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

class DisposalTransitHolderTest {
    @Test void releaseAirTargetsRoomAboveReturnedUnitWhenUnitOccupiesEndCell() {
        var end = new net.minecraft.core.BlockPos(4, 10, 7);
        assertEquals(end.above(2), LoadedWorldDisposalRouting.releaseEnvironmentCell(end, true));
        assertEquals(end.above(), LoadedWorldDisposalRouting.releaseEnvironmentCell(end, false));
    }
    @Test void segmentInterpolationMovesFeetAcrossTwoTwentyTpsTicks() {
        Vec3 from = new Vec3(.5, 65, .5), to = new Vec3(1.5, 65, .5);
        assertEquals(from, DisposalTransitHolder.interpolateSegment(from, to, 0, 2));
        assertEquals(new Vec3(1, 65, .5), DisposalTransitHolder.interpolateSegment(from, to, 1, 2));
        assertEquals(to, DisposalTransitHolder.interpolateSegment(from, to, 2, 2));
    }
    @Test void segmentArrivalTicksAreContiguousAtTwoTicksPerCell() {
        Vec3 from = new Vec3(.5, 65, .5), first = new Vec3(1.5, 65, .5), second = new Vec3(2.5, 65, .5);
        // Runtime clears a completed segment and makes its next routing decision on that
        // completion tick; only the following two ticks interpolate the next segment.
        assertEquals(new Vec3(1, 65, .5), DisposalTransitHolder.interpolateSegment(from, first, 1, 2));
        assertEquals(first, DisposalTransitHolder.interpolateSegment(from, first, 2, 2));
        assertEquals(new Vec3(2, 65, .5), DisposalTransitHolder.interpolateSegment(first, second, 1, 2));
        assertEquals(second, DisposalTransitHolder.interpolateSegment(first, second, 2, 2));
    }
    @Test void originalBodyFlagsRoundTripIndependentOfSuppressedRuntimeFlags() {
        UUID body = UUID.randomUUID();
        var expected = new BodyContainmentService.ContainedBodyFlags(false, true, false);
        var decoded = DisposalTransitHolder.decodeContainedBodyFlags(DisposalTransitHolder.encodeContainedBodyFlags(body, expected));
        assertNotNull(decoded); assertEquals(body, decoded.getKey()); assertEquals(expected, decoded.getValue());
    }
    @Test void finiteAirNbtRetainsExactVolumeSpeciesAndTemperature() {
        GasMixture mixture = new GasMixture(Map.of(GasType.OXYGEN, 2.5, GasType.NITROGEN, 4.0), 287.25);
        var decoded = DisposalTransitHolder.decodeAir(DisposalTransitHolder.encodeAir(mixture, .070)).sample().orElseThrow();
        assertEquals(.070, decoded.volumeCubicMeters());
        assertEquals(287.25, decoded.mixture().temperatureKelvin());
        assertEquals(2.5, decoded.mixture().moles(GasType.OXYGEN));
        assertEquals(4.0, decoded.mixture().moles(GasType.NITROGEN));
    }
}
