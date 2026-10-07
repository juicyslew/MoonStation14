package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExposureAirRoutingTest {
    @Test void absenceMeansWorldButContainedOwnerWithoutSampleIsKnownUnknown() {
        var world = ExposureAirRouting.fromOwner(Optional.empty());
        assertFalse(world.contained());
        var owner = new ContainedBodyOwner() {
            @Override public UUID identity() { return UUID.randomUUID(); }
            @Override public ServerLevel level() { return null; }
            @Override public Vec3 position() { return Vec3.ZERO; }
            @Override public Stage stage() { return Stage.TRANSIT; }
            @Override public boolean isValid() { return true; }
            @Override public boolean tryEscape(LivingEntity body) { return false; }
        };
        var containedUnknown = ExposureAirRouting.fromOwner(Optional.of(owner));
        assertTrue(containedUnknown.contained());
        assertTrue(containedUnknown.source().isEmpty());
    }
}
