package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileImpactPolicyTest {
    @Test void missingProfileOnlyDiscardsOnServer() {
        assertFalse(ProjectileImpactPolicy.discardForMissingProfile(ProjectileImpactPolicy.TickSide.CLIENT, false));
        assertFalse(ProjectileImpactPolicy.discardForMissingProfile(ProjectileImpactPolicy.TickSide.CLIENT, true));
        assertTrue(ProjectileImpactPolicy.discardForMissingProfile(ProjectileImpactPolicy.TickSide.SERVER, false));
        assertFalse(ProjectileImpactPolicy.discardForMissingProfile(ProjectileImpactPolicy.TickSide.SERVER, true));
    }

    @Test void resolvedLifetimeIsCeiledAndNotCappedAtTenSeconds() {
        assertEquals(200, ProjectileImpactPolicy.lifetimeTicks(profile(10f, Map.of("piercing", 1f))));
        assertEquals(400, ProjectileImpactPolicy.lifetimeTicks(profile(20f, Map.of("piercing", 1f))));
        assertEquals(1, ProjectileImpactPolicy.lifetimeTicks(profile(.01f, Map.of())));
    }

    @Test void collisionIsConsumedEvenWhenDamageIsEmpty() {
        var zeroDamage = profile(10f, Map.of());
        assertTrue(ProjectileImpactPolicy.consumeCollision(zeroDamage));
    }
    @Test void zeroDamageBulletStillEmitsSingleImpact(){var cap=profile(10f,Map.of("piercing",0f));assertTrue(ProjectileImpactPolicy.emitImpact(cap,false));assertFalse(ProjectileImpactPolicy.emitImpact(cap,true));}

    private static ProjectileProfile profile(float lifetime, Map<String, Float> damage) {
        return new ProjectileProfile(damage, false, true, lifetime);
    }
}
