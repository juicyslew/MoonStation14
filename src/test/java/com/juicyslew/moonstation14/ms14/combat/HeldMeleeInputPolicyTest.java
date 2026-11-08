package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.combat.client.HeldMeleeInputPolicy;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HeldMeleeInputPolicyTest {
    private static HeldMeleeInputPolicy.Context context(UUID body, long epoch, HeldMeleeInputPolicy.Source source) {
        return new HeldMeleeInputPolicy.Context(body, epoch, source);
    }

    private static MeleeWeaponResolver.Selection selection(long revision, MeleeWeaponResolver.Kind kind, String hand,
            java.util.Optional<String> token, MeleeWeaponProfile profile) {
        return new MeleeWeaponResolver.Selection(kind, profile, UUID.fromString("00000000-0000-0000-0000-000000000001"), revision, hand, token);
    }

    @Test void preferenceAndPrototypeAutoAttackIndependentlyEnableRepeat() {
        var policy = new HeldMeleeInputPolicy(); var body = UUID.randomUUID(); var ctx = context(body, 1, new HeldMeleeInputPolicy.Source(MeleeWeaponResolver.Kind.UNARMED, "main", null, null));
        assertFalse(policy.begin(false, false, true, ctx, 1));
        assertTrue(policy.begin(true, false, true, ctx, 2)); policy.cancel();
        assertTrue(policy.begin(false, true, true, ctx, 3));
    }

    @Test void physicalReleaseUiCaptureAndContextChangesRequireFreshPress() {
        var policy = new HeldMeleeInputPolicy(); var body = UUID.randomUUID();
        var source = new HeldMeleeInputPolicy.Source(MeleeWeaponResolver.Kind.HELD, "main", "token", null);
        var ctx = context(body, 4, source);
        assertTrue(policy.begin(true, false, true, ctx, 1));
        assertFalse(policy.eligible(false, true, true, true, ctx, 2)); // release
        assertFalse(policy.active());
        assertTrue(policy.begin(true, false, true, ctx, 3));
        assertFalse(policy.eligible(true, false, true, true, ctx, 4)); // UI/input capture
        assertFalse(policy.active());
        assertTrue(policy.begin(true, false, true, ctx, 5));
        assertFalse(policy.eligible(true, true, true, true, context(body, 5, source), 6)); // epoch changed
        assertTrue(policy.begin(true, false, true, ctx, 7));
        assertFalse(policy.eligible(true, true, true, true, context(body, 4, new HeldMeleeInputPolicy.Source(MeleeWeaponResolver.Kind.HELD, "offhand", "token", null)), 8)); // hand/source changed
    }

    @Test void sameTickPressDoesNotDuplicateAndCooldownAdmissionIsRequired() {
        var policy = new HeldMeleeInputPolicy(); var ctx = context(UUID.randomUUID(), 1, new HeldMeleeInputPolicy.Source(MeleeWeaponResolver.Kind.UNARMED, "main", null, null));
        assertTrue(policy.begin(true, false, true, ctx, 10));
        assertFalse(policy.eligible(true, true, true, true, ctx, 10));
        policy.dispatched(10);
        assertFalse(policy.eligible(true, true, false, true, ctx, 11)); // cooldown waits without ending hold
        assertTrue(policy.active());
        assertTrue(policy.eligible(true, true, true, true, ctx, 11)); // exact MeleeAttackClient admission now ready
    }

    @Test void combatModeLossCancelsWithoutMiningOrUseRepeat() {
        var policy = new HeldMeleeInputPolicy(); var ctx = context(UUID.randomUUID(), 1, new HeldMeleeInputPolicy.Source(MeleeWeaponResolver.Kind.UNARMED, "main", null, null));
        assertTrue(policy.begin(true, false, true, ctx, 1));
        assertFalse(policy.eligible(true, true, true, false, ctx, 2));
        assertFalse(policy.active());
    }

    @Test void revisionRefreshAndMeleeCommitDoNotCancelUnchangedHeldSource() {
        var body = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var profile = new MeleeWeaponProfile(new MeleeWeaponData(java.util.Map.of("blunt", 5f), 1f, 1.5f, false, true, 1f, false), java.util.Map.of(), java.util.Optional.empty());
        var before = selection(1, MeleeWeaponResolver.Kind.HELD, "main", java.util.Optional.of("token"), profile);
        var afterCommit = selection(3, MeleeWeaponResolver.Kind.HELD, "main", java.util.Optional.of("token"), profile);
        var sourceBefore = new HeldMeleeInputPolicy.Source(before.kind(), before.activeHand(), before.itemToken().orElse(null), before.profile());
        var sourceAfter = new HeldMeleeInputPolicy.Source(afterCommit.kind(), afterCommit.activeHand(), afterCommit.itemToken().orElse(null), afterCommit.profile());
        var ctx = context(body, 1, sourceBefore); var policy = new HeldMeleeInputPolicy();
        assertTrue(policy.begin(true, false, true, ctx, 1));
        policy.dispatched(1);
        var refreshed = context(body, 1, sourceAfter);
        assertFalse(policy.eligible(true, true, false, true, refreshed, 2));
        assertTrue(policy.active());
        assertTrue(policy.eligible(true, true, true, true, refreshed, 3));
        assertFalse(policy.eligible(true, true, true, true, context(body, 1,
                new HeldMeleeInputPolicy.Source(MeleeWeaponResolver.Kind.GLOVES, "main", "token", profile)), 4));
    }

    @Test void unarmedInventoryRevisionDoesNotCancelSource() {
        var body = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var profile = new MeleeWeaponProfile(new MeleeWeaponData(java.util.Map.of("blunt", 5f), 1f, 1.5f, false, true, 1f, false), java.util.Map.of(), java.util.Optional.empty());
        var a = selection(1, MeleeWeaponResolver.Kind.UNARMED, "main", java.util.Optional.empty(), profile);
        var b = selection(2, MeleeWeaponResolver.Kind.UNARMED, "main", java.util.Optional.empty(), profile);
        var source = new HeldMeleeInputPolicy.Source(a.kind(), a.activeHand(), null, a.profile());
        var updated = new HeldMeleeInputPolicy.Source(b.kind(), b.activeHand(), null, b.profile());
        var policy = new HeldMeleeInputPolicy(); policy.begin(true, false, true, context(body, 1, source), 1);
        assertTrue(policy.eligible(true, true, true, true, context(body, 1, updated), 2));
    }

    @Test void sharedRequestAdmissionRequiresSourceCooldownAndNoPendingRequest() {
        assertFalse(MeleeAttackClientPolicy.repeatAdmission(false, false, 100, 0));
        assertFalse(MeleeAttackClientPolicy.repeatAdmission(true, true, 100, 0));
        assertFalse(MeleeAttackClientPolicy.repeatAdmission(true, false, 99, 100));
        assertTrue(MeleeAttackClientPolicy.repeatAdmission(true, false, 100, 100));
    }

    @Test void heldUnarmedAltDisarmRepeatsOnlyAfterCooldownAdmissionAndUsesDisarmDispatchMode() {
        var policy = new HeldMeleeInputPolicy();
        var body = UUID.randomUUID();
        var ctx = context(body, 1, new HeldMeleeInputPolicy.Source(
                MeleeWeaponResolver.Kind.UNARMED, "main", null, null));
        assertTrue(policy.begin(true, false, true, ctx, 1));
        // The ordinary click remains the same primary dispatch route, while held attempts
        // select the secondary DISARM request whenever the current source permits it.
        assertEquals(MeleeAttackRequest.Mode.DISARM, MeleeSecondaryGesture.mode(
                MeleeWeaponResolver.Kind.UNARMED, true, true));
        assertFalse(policy.eligible(true, true, false, true, ctx, 2)); // cooldown not ready
        assertTrue(policy.eligible(true, true, true, true, ctx, 3));
        policy.dispatched(3);
        assertFalse(policy.eligible(true, true, true, true, ctx, 3)); // one dispatch per tick
        assertTrue(policy.eligible(true, true, true, true, ctx, 4)); // fresh attempt / target sample
        assertNull(MeleeSecondaryGesture.mode(MeleeWeaponResolver.Kind.UNARMED, true, false));
    }
}
