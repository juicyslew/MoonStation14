package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MeleeAttackClientWideRequestTest {
    @Test void typedWideRequestPreservesBlockAndMixedUnionCap() {
        var entity = new MeleeTargetNomination.Entity(UUID.randomUUID());
        var block = new MeleeTargetNomination.Block(BlockPos.ZERO, Direction.UP, Vec3.ZERO, 1);
        var mixed = List.<MeleeTargetNomination>of(entity, block,
                new MeleeTargetNomination.Entity(UUID.randomUUID()), new MeleeTargetNomination.Entity(UUID.randomUUID()),
                new MeleeTargetNomination.Entity(UUID.randomUUID()));
        var template = new MeleeAttackRequest(1, 2, MeleeWeaponResolver.Kind.UNARMED, 0, "", "", MeleeAttackRequest.Mode.WIDE, List.of(), false);
        var request = MeleeAttackClient.withWideNominations(template, mixed);
        assertEquals(5, request.nominations().size());
        assertEquals(block, request.nominations().get(1));
        assertEquals(MeleeAttackRequest.Mode.WIDE, request.attackMode());
    }
}
