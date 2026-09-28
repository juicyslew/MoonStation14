package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftCharacterDeathEvidenceTest {
    private static final SavedLifecycleProfile PROFILE = new SavedLifecycleProfile(1, id(1), "main", id(2),
            id(3), "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0), Map.of(), id(4), 0,
            SavedLifecycleProfile.State.OFFLINE, 0, 10L);
    private static final MobHarness CHARACTER = new MobHarness(new MobHarnessId(id(3)), MobHarnessKind.CHARACTER);

    @Test
    void gatesOffAndMovementConflictReturnBeforeWorldLookup() {
        AtomicInteger lookups = new AtomicInteger();
        assertFalse(verify(false, false, lookups, UnaryOperator.identity()));
        assertFalse(verify(true, true, lookups, UnaryOperator.identity()));
        assertEquals(0, lookups.get());
    }

    @Test
    void aliveBodyIsNotDeathEvidence() {
        AtomicInteger lookups = new AtomicInteger();
        assertFalse(verify(true, false, lookups, observation -> new MinecraftCharacterDeathEvidence.Observation(
                observation.entityId(), observation.dimension(), observation.accountId(), observation.profileKey(),
                observation.mindId(), observation.registeredCustomEntity(), observation.validBinding(),
                observation.humanPrototype(), false)));
        assertEquals(1, lookups.get());
    }

    @Test
    void mismatchedUuidMindOrProfileAndWrongTypeFailClosed() {
        assertFalse(result(o -> change(o, id(9), o.dimension(), o.accountId(), o.profileKey(), o.mindId(), true)));
        assertFalse(result(o -> change(o, o.entityId(), o.dimension(), o.accountId(), o.profileKey(), id(9), true)));
        assertFalse(result(o -> change(o, o.entityId(), o.dimension(), o.accountId(), "other", o.mindId(), true)));
        assertFalse(result(o -> new MinecraftCharacterDeathEvidence.Observation(o.entityId(), o.dimension(),
                o.accountId(), o.profileKey(), o.mindId(), false, o.validBinding(), o.humanPrototype(), o.dead())));
    }

    @Test
    void exactDeadRegisteredHumanCharacterIsPositiveEvidence() {
        assertTrue(result(UnaryOperator.identity()));
    }

    private static boolean verify(boolean enabled, boolean conflict, AtomicInteger lookups,
                                  UnaryOperator<MinecraftCharacterDeathEvidence.Observation> transform) {
        return MinecraftCharacterDeathEvidence.verifyObserved(PROFILE, CHARACTER, enabled, conflict, true,
                () -> { lookups.incrementAndGet(); return transform.apply(good()); });
    }

    private static boolean result(UnaryOperator<MinecraftCharacterDeathEvidence.Observation> transform) {
        return verify(true, false, new AtomicInteger(), transform);
    }

    private static MinecraftCharacterDeathEvidence.Observation good() {
        return new MinecraftCharacterDeathEvidence.Observation(id(3), "minecraft:overworld", id(1), "main",
                id(2), true, true, true, true);
    }

    private static MinecraftCharacterDeathEvidence.Observation change(MinecraftCharacterDeathEvidence.Observation o,
            UUID entity, String dimension, UUID account, String profile, UUID mind, boolean dead) {
        return new MinecraftCharacterDeathEvidence.Observation(entity, dimension, account, profile, mind,
                o.registeredCustomEntity(), o.validBinding(), o.humanPrototype(), dead);
    }

    private static UUID id(long value) { return new UUID(0, value); }
}
