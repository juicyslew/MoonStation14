package com.juicyslew.moonstation14.ms14.stripping.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StripTargetSelectionTest {
    private static StripTargetSelection.Candidate candidate(UUID id,double root,double ray,boolean eligible,boolean excluded){
        return new StripTargetSelection.Candidate(id,new Vec3(root,0,0),ray,eligible,excluded);
    }
    @Test void rootReachAcceptsNearRootEvenWhenEyeIntersectionAndCenterAreBeyondOnePointFive(){
        UUID target=UUID.randomUUID();
        assertEquals(target,StripTargetSelection.select(List.of(candidate(target,1.49,2.1,true,false)),Vec3.ZERO,1.5,6));
    }
    @Test void closeBoundingBoxDoesNotMakeOutOfRangeRootEligible(){
        assertNull(StripTargetSelection.select(List.of(candidate(UUID.randomUUID(),1.51,.1,true,false)),Vec3.ZERO,1.5,6));
    }
    @Test void longProneBodyRayIntersectionIsAllowedWhenRootIsInReach(){
        UUID target=UUID.randomUUID();
        assertEquals(target,StripTargetSelection.select(List.of(candidate(target,1.4,2.7,true,false)),Vec3.ZERO,1.5,6));
    }
    @Test void nearerWallAndNearerIneligibleBodyBlockBehindTarget(){
        UUID behind=UUID.randomUUID();
        assertNull(StripTargetSelection.select(List.of(candidate(behind,1,2,true,false)),Vec3.ZERO,1.5,1.9));
        var nearer=candidate(UUID.randomUUID(),.7,.4,false,false);
        assertNull(StripTargetSelection.select(List.of(nearer,candidate(behind,1,1,true,false)),Vec3.ZERO,1.5,6));
    }
    @Test void carrierAndSelfCandidatesAreExcludedBeforeNearestVisibleBodySelection(){
        UUID target=UUID.randomUUID();
        assertEquals(target,StripTargetSelection.select(List.of(candidate(UUID.randomUUID(),0,.1,true,true),
                candidate(UUID.randomUUID(),.1,.2,true,true),candidate(target,1.2,.8,true,false)),Vec3.ZERO,1.5,6));
    }
}
