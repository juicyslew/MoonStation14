package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GunShotClientPolicyTest {
    @Test void sourceGateAcknowledgementTimeoutAndEpochBinding() {
        UUID body=UUID.randomUUID(); var policy=new GunShotClientPolicy(); policy.bind(body,4);
        var first=policy.submit(new GunShotClientPolicy.Binding(2,"right","gun-A"),100,8,Vec3.ZERO);
        assertNotNull(first); assertTrue(policy.pending());
        assertNull(policy.submit(new GunShotClientPolicy.Binding(3,"left","gun-A"),101,8,Vec3.ZERO));
        assertFalse(policy.acknowledge(new GunShotReply(UUID.randomUUID(),4,first.sequence(),GunFireSystem.Status.REJECTED,false,2,"gun-A",3),102));
        assertTrue(policy.acknowledge(new GunShotReply(body,4,first.sequence(),GunFireSystem.Status.REJECTED,false,2,"gun-A",3),102));
        assertNotNull(policy.submit(new GunShotClientPolicy.Binding(3,"left","gun-B"),103,8,Vec3.ZERO));
        assertTrue(policy.timeout(112,9)); assertFalse(policy.pending());
        assertNotNull(policy.submit(new GunShotClientPolicy.Binding(3,"left","gun-A"),113,8,Vec3.ZERO));
        policy.bind(body,5); assertFalse(policy.pending()); assertEquals(0,policy.projectedDeadline());
    }
}
