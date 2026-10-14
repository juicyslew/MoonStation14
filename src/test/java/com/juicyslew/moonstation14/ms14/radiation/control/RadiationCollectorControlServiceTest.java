package com.juicyslew.moonstation14.ms14.radiation.control;

import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RadiationCollectorControlServiceTest {
    private static BodyBlockUseRequest proof(long sequence, BodyBlockUseRequest.Intent intent) {
        return new BodyBlockUseRequest(7, sequence, 4, "main", null, new BlockPos(1, 2, 3), Direction.UP, false, intent);
    }

    @Test void correlationRequiresMatchingSequenceAndUseIntent() {
        assertTrue(RadiationCollectorControlService.correlated(new RadiationCollectorControlRequest(3,
                RadiationCollectorControlRequest.Operation.QUERY, proof(3, BodyBlockUseRequest.Intent.USE), null)));
        assertFalse(RadiationCollectorControlService.correlated(new RadiationCollectorControlRequest(3,
                RadiationCollectorControlRequest.Operation.QUERY, proof(4, BodyBlockUseRequest.Intent.USE), null)));
        assertFalse(RadiationCollectorControlService.correlated(new RadiationCollectorControlRequest(3,
                RadiationCollectorControlRequest.Operation.QUERY, proof(3, BodyBlockUseRequest.Intent.ACTIVATE_WORLD), null)));
        assertFalse(RadiationCollectorControlService.correlated(null));
    }

    @Test void ejectExpectationRequiresPlacementProfileAndBothRevisions() {
        UUID placement = UUID.randomUUID();
        ResourceLocation profile = ResourceLocation.withDefaultNamespace("collector");
        var expected = new RadiationCollectorControlRequest.EjectExpectation(placement, profile, 8, 9);
        assertTrue(RadiationCollectorControlService.expectationMatches(expected, placement, profile, 8, 9));
        assertFalse(RadiationCollectorControlService.expectationMatches(expected, UUID.randomUUID(), profile, 8, 9));
        assertFalse(RadiationCollectorControlService.expectationMatches(expected, placement, ResourceLocation.withDefaultNamespace("other"), 8, 9));
        assertFalse(RadiationCollectorControlService.expectationMatches(expected, placement, profile, 7, 9));
        assertFalse(RadiationCollectorControlService.expectationMatches(expected, placement, profile, 8, 10));
        assertFalse(RadiationCollectorControlService.expectationMatches(null, placement, profile, 8, 9));
    }
}
