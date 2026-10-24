package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay;
import com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MeleeWideNominationTest {
    private static final UUID A=id(1), B=id(2), C=id(3), D=id(4), E=id(5), F=id(6);
    private static UUID id(int n){return new UUID(0,n);}
    @Test void defaultSixtyDegreesSamplesThirtySixIncludingLowerButExcludingUpper() {
        double width=Math.toRadians(60), aim=1.25;
        var angles=ClientBodyEntityRay.fanAngles(aim,width);
        assertEquals(36,angles.size());
        assertEquals(aim-width/2,angles.get(0),1e-12);
        assertEquals(aim+width/2-width/36,angles.get(35),1e-12);
        assertTrue(angles.get(35)<aim+width/2);
    }
    @Test void zeroWidthHasOneCenterRay() { assertEquals(List.of(0.75),ClientBodyEntityRay.fanAngles(.75,0)); }
    @Test void mergesDeduplicatedHitsInRayOrderThenCaps() {
        assertEquals(List.of(A,B,C,D,E),ClientBodyEntityRay.mergeFanHits(List.of(List.of(A,B,C),List.of(B,D,E,F)),5));
    }
    @Test void eachRayUsesSharedBodyGeometryAndStopsAtBlockerWithoutNearestOnlyOcclusion() {
        Vec3 start=Vec3.ZERO, end=new Vec3(10,0,0);
        AABB blockerVictim=new AABB(1,-.4,-.4,2,.4,.4), behind=new AABB(6,-.4,-.4,7,.4,.4);
        assertTrue(BodyInteractionRay.hit(blockerVictim,start,end,10,3).isPresent());
        assertTrue(BodyInteractionRay.hit(behind,start,end,10,3).isEmpty());
        // Separate body hits on the same ray are both nominated; bodies do not occlude each other.
        assertTrue(BodyInteractionRay.hit(new AABB(3,-.4,-.4,4,.4,.4),start,end,10,9).isPresent());
        assertTrue(BodyInteractionRay.hit(new AABB(2,4,4,3,5,5),start,end,10,9).isEmpty());
        assertEquals(List.of(A,B),ClientBodyEntityRay.mergeFanHits(List.of(List.of(A,B)),5));
    }
}
