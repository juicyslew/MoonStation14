package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.TransientDisarmFlightPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisarmFlightVisualsTest {
    private static final ResourceLocation WORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static TransientDisarmFlightPayload payload(Vec3 velocity) {
        return new TransientDisarmFlightPayload(WORLD, UUID.randomUUID(), Vec3.ZERO, velocity, true);
    }
    @Test void duplicateDoesNotExtendDeadlineAndSameItemCanReleaseAgainAfterExpiry() {
        DisarmFlightVisuals.clear(); var p=payload(new Vec3(1,0,0));
        long start = 100L;
        DisarmFlightVisuals.receive(p,WORLD,start); DisarmFlightVisuals.receive(p,WORLD,start+100_000_000L);
        assertEquals(1,DisarmFlightVisuals.sample(start+DisarmFlightVisuals.CUE_LIFETIME_NANOS-1).size());
        assertTrue(DisarmFlightVisuals.sample(start+DisarmFlightVisuals.CUE_LIFETIME_NANOS).isEmpty());
        DisarmFlightVisuals.receive(p,WORLD,start+700_000_000L); assertEquals(1,DisarmFlightVisuals.sample(start+700_000_000L).size());
        DisarmFlightVisuals.clear();
    }
    @Test void invalidVelocityAndWrongWorldAreDiscardedAndWorldResetClears() {
        assertThrows(IllegalArgumentException.class, () -> payload(new Vec3(Double.NaN,0,0)));
        DisarmFlightVisuals.clear();
        var valid=payload(new Vec3(1,0,0)); DisarmFlightVisuals.receive(valid,WORLD,2);
        var wrongWorld = new TransientDisarmFlightPayload(ResourceLocation.withDefaultNamespace("the_nether"), UUID.randomUUID(), Vec3.ZERO, new Vec3(1,0,0), true);
        DisarmFlightVisuals.receive(wrongWorld,WORLD,3);
        assertEquals(1,DisarmFlightVisuals.sample(3).size());
        var zeroVelocity = payload(Vec3.ZERO);
        DisarmFlightVisuals.receive(zeroVelocity,WORLD,4);
        assertEquals(1,DisarmFlightVisuals.sample(4).size());
        DisarmFlightVisuals.reconcileWorld(ResourceLocation.withDefaultNamespace("the_nether"));
        assertTrue(DisarmFlightVisuals.sample(3).isEmpty());
        DisarmFlightVisuals.clear();
    }
}
