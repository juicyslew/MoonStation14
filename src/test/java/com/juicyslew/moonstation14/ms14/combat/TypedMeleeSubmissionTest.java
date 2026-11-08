package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TypedMeleeSubmissionTest {
    private MeleeAttackClientPolicy policy() {
        var policy = new MeleeAttackClientPolicy();
        policy.bind(UUID.randomUUID(), 1);
        return policy;
    }
    private MeleeAttackClientPolicy.Binding binding() {
        return new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.UNARMED, 3, "main", "");
    }
    private MeleeTargetNomination.Block block() {
        return new MeleeTargetNomination.Block(new net.minecraft.core.BlockPos(1,2,3), net.minecraft.core.Direction.UP,
                new net.minecraft.world.phys.Vec3(1.5,2.5,3.5), 77);
    }

    @Test void typedWideKeepsEntityAndBlockTogether() {
        var p=policy(); var entity=UUID.randomUUID();
        var request=p.submitTyped(binding(),10,4,MeleeAttackRequest.Mode.WIDE,List.of(new MeleeTargetNomination.Entity(entity),block()));
        assertNotNull(request);
        assertEquals(2,request.nominations().size());
        assertEquals(entity,((MeleeTargetNomination.Entity)request.nominations().get(0)).entityUUID());
        assertEquals(77,((MeleeTargetNomination.Block)request.nominations().get(1)).expectedBlockStateID());
    }

    @Test void invalidDisarmDoesNotConsumePendingRequest() {
        var p=policy();
        assertNull(p.submitTyped(binding(),10,4,MeleeAttackRequest.Mode.DISARM,List.of(block())));
        assertFalse(p.pending()); assertEquals(0,p.sequence());
        assertNotNull(p.submitTyped(binding(),10,4,MeleeAttackRequest.Mode.DISARM,List.of()));
    }

    @Test void emptyWideIsAValidSilentMissRequest() {
        var request=policy().submitTyped(binding(),10,4,MeleeAttackRequest.Mode.WIDE,List.of());
        assertNotNull(request); assertTrue(request.nominations().isEmpty());
    }
}
