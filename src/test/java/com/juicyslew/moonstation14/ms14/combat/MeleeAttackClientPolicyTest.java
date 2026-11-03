package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MeleeAttackClientPolicyTest {
    @Test void correlatesExactContextAndSequenceAndPreservesConsumedCooldown() {
        var p=new MeleeAttackClientPolicy(); var id=UUID.randomUUID(); p.bind(id,3);
        var b=new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.HELD,4,"main","token");
        var request=p.submit(b,5,20,null); assertEquals(1,request.sequence()); assertTrue(p.pending());
        assertFalse(p.acknowledge(new MeleeAttackReply(id,3,2,MeleeAttackReply.Status.MISS,true,null,0,null,7),6));
        assertFalse(p.acknowledge(new MeleeAttackReply(UUID.randomUUID(),3,1,MeleeAttackReply.Status.MISS,true,null,0,null,7),6));
        assertTrue(p.acknowledge(new MeleeAttackReply(id,3,1,MeleeAttackReply.Status.MISS,true,null,0,null,7),6));
        assertEquals(13,p.projectedDeadline());
    }
    @Test void rejectedRequestResetsProjectionAndContextClears() {
        var p=new MeleeAttackClientPolicy(); var id=UUID.randomUUID(); p.bind(id,2);
        p.submit(new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.UNARMED,0,"body",""),10,20,null);
        assertTrue(p.acknowledge(new MeleeAttackReply(id,2,1,MeleeAttackReply.Status.REJECTED,false,null,0,null,7),11));
        assertEquals(18,p.projectedDeadline()); p.bind(id,3); assertFalse(p.pending()); assertNull(p.binding());
    }
    @Test void elapsedCooldownAndStaleAckDoNotRewindNewRequest() {
        var p=new MeleeAttackClientPolicy(); var id=UUID.randomUUID(); p.bind(id,2);
        var b=new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.UNARMED,0,"body","");
        p.submit(b,5,20,null); assertTrue(p.acknowledge(new MeleeAttackReply(id,2,1,MeleeAttackReply.Status.REJECTED,false,null,0,null,0),8));
        assertEquals(8,p.projectedDeadline()); p.submit(b,9,20,null);
        assertFalse(p.acknowledge(new MeleeAttackReply(id,2,1,MeleeAttackReply.Status.REJECTED,false,null,0,null,0),10));
        assertEquals(29,p.projectedDeadline()); assertTrue(p.pending());
    }

    @Test void singleTargetModesRetainNominationAndWideKeepsListOnly() {
        var p = new MeleeAttackClientPolicy(); p.bind(UUID.randomUUID(), 8);
        var binding = new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.UNARMED, 0, "body", "");
        var target = UUID.randomUUID();
        var light = p.submit(binding, 1, 10, MeleeAttackRequest.Mode.LIGHT, java.util.List.of(target));
        assertEquals(target, light.targetUUID()); assertTrue(light.wideTargets().isEmpty());
        assertTrue(p.acknowledge(new MeleeAttackReply(p.body(), 8, light.sequence(), MeleeAttackReply.Status.MISS, true, null, 0, null, 11), 2));
        var disarm = p.submit(binding, 20, 10, MeleeAttackRequest.Mode.DISARM, java.util.List.of(target));
        assertEquals(target, disarm.targetUUID()); assertTrue(disarm.wideTargets().isEmpty());
        assertTrue(p.acknowledge(new MeleeAttackReply(p.body(), 8, disarm.sequence(), MeleeAttackReply.Status.MISS, true, null, 0, null, 30), 21));
        var widePolicy = new MeleeAttackClientPolicy(); widePolicy.bind(UUID.randomUUID(), 8);
        var wide = widePolicy.submit(binding, 40, 10, MeleeAttackRequest.Mode.WIDE, java.util.List.of(target));
        assertNull(wide.targetUUID()); assertEquals(java.util.List.of(target), wide.wideTargets());
    }

    @Test void invalidNominationDoesNotConsumeSequenceOrCooldown() {
        var p = new MeleeAttackClientPolicy(); p.bind(UUID.randomUUID(), 2);
        var binding = new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.UNARMED, 0, "body", "");
        assertNull(p.submit(binding, 1, 10, MeleeAttackRequest.Mode.DISARM, java.util.List.of(UUID.randomUUID(), UUID.randomUUID())));
        var valid = p.submit(binding, 1, 10, MeleeAttackRequest.Mode.LIGHT, java.util.List.of());
        assertEquals(1, valid.sequence());
    }
}
