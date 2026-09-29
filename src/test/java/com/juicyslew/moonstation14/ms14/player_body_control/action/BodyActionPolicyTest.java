package com.juicyslew.moonstation14.ms14.player_body_control.action;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyActionPolicyTest {
    private static CharacterData prototype(List<String> hands) {
        return new CharacterData(new CharacterData.SlipTargetData(false, true, false, false,
                List.of(), List.of()), Optional.empty(), List.of(), Optional.empty(), hands);
    }

    private static BodyActionPolicy.Identity identity(Object player, Object body, UUID uuid,
            BodyActionPolicy.Source source, MindId mind, long epoch, CharacterData prototype, boolean enabled) {
        return new BodyActionPolicy.Identity(player, body, uuid, source,
                mind, new MobHarnessId(uuid), epoch, prototype, enabled);
    }

    @Test
    void onlyOneCommittedCharacterAuthorityMayBeChosen() {
        assertTrue(BodyActionPolicy.selects(true, false, false));
        assertTrue(BodyActionPolicy.selects(false, true, true));
        assertFalse(BodyActionPolicy.selects(false, false, false));
        assertFalse(BodyActionPolicy.selects(true, true, true));
        assertFalse(BodyActionPolicy.selects(false, true, false)); // ghost
    }

    @Test
    void playerGateRequiresEveryServerEstablishedCondition() {
        assertTrue(BodyActionPolicy.eligiblePlayer(true, true, true, true, true, true));
        assertFalse(BodyActionPolicy.eligiblePlayer(false, true, true, true, true, true));
        assertFalse(BodyActionPolicy.eligiblePlayer(true, false, true, true, true, true));
        assertFalse(BodyActionPolicy.eligiblePlayer(true, true, false, true, true, true)); // fake
        assertFalse(BodyActionPolicy.eligiblePlayer(true, true, true, false, true, true)); // Creative
        assertFalse(BodyActionPolicy.eligiblePlayer(true, true, true, true, false, true));
        assertFalse(BodyActionPolicy.eligiblePlayer(true, true, true, true, true, false));
    }

    @Test
    void pigWithoutHandsStillHasAnActorIdentityIndependentOfComponent() {
        var pig = prototype(List.of());
        var body = new Object();
        var player = new Object();
        var id = identity(player, body, UUID.randomUUID(), BodyActionPolicy.Source.EXPERIMENTAL_HARNESS,
                new MindId(UUID.randomUUID()), 1, pig, false);
        assertTrue(BodyActionPolicy.same(id, id));
        assertFalse(BodyActionPolicy.eligibleForComplexInteraction(id, false));
        var enabled = identity(player, body, id.bodyUuid(), id.source(), id.mind(), 1, pig, true);
        assertTrue(BodyActionPolicy.eligibleForComplexInteraction(enabled, false));
        assertFalse(BodyActionPolicy.eligibleForComplexInteraction(enabled, true));
        assertFalse(BodyActionPolicy.eligibleForComplexInteraction(null, false));
        // Hands on a prototype cannot override an absent or revoked runtime component.
        var handsOnly = identity(player, body, id.bodyUuid(), id.source(), id.mind(), 1,
                prototype(List.of("left", "right")), false);
        assertFalse(BodyActionPolicy.eligibleForComplexInteraction(handsOnly, false));
    }

    @Test
    void prototypeDeclarationCannotRestoreRevokedRuntimeComponent() {
        var prototype = new CharacterData(new CharacterData.SlipTargetData(false, true, false, false,
                List.of(), List.of()), Optional.empty(), List.of(), Optional.empty(), List.of(),
                List.of(CharacterData.Capability.COMPLEX_INTERACTION));
        var body = new Object();
        var player = new Object();
        var uuid = UUID.randomUUID();
        var mind = new MindId(UUID.randomUUID());
        var enabled = identity(player, body, uuid, BodyActionPolicy.Source.LIFECYCLE, mind, 1, prototype, true);
        var revoked = identity(player, body, uuid, BodyActionPolicy.Source.LIFECYCLE, mind, 1, prototype, false);
        assertTrue(BodyActionPolicy.eligibleForComplexInteraction(enabled, false));
        assertFalse(BodyActionPolicy.eligibleForComplexInteraction(revoked, false));
        assertFalse(BodyActionPolicy.same(enabled, revoked));
    }

    @Test
    void replacementBodySessionEpochSourceOrPrototypeCannotRevalidate() {
        var player = new Object();
        var body = new Object();
        var uuid = UUID.randomUUID();
        var mind = new MindId(UUID.randomUUID());
        var pig = prototype(List.of());
        var original = identity(player, body, uuid, BodyActionPolicy.Source.LIFECYCLE, mind, 1, pig, true);
        assertTrue(BodyActionPolicy.same(original,
                identity(player, body, uuid, BodyActionPolicy.Source.LIFECYCLE, mind, 1, pig, true)));
        assertFalse(BodyActionPolicy.same(original, identity(new Object(), body, uuid,
                BodyActionPolicy.Source.LIFECYCLE, mind, 1, pig, true)));
        assertFalse(BodyActionPolicy.same(original, identity(player, new Object(), uuid,
                BodyActionPolicy.Source.LIFECYCLE, mind, 1, pig, true)));
        assertFalse(BodyActionPolicy.same(original, identity(player, body, UUID.randomUUID(),
                BodyActionPolicy.Source.LIFECYCLE, mind, 1, pig, true)));
        assertFalse(BodyActionPolicy.same(original, identity(player, body, uuid,
                BodyActionPolicy.Source.EXPERIMENTAL_HARNESS, mind, 1, pig, true)));
        assertFalse(BodyActionPolicy.same(original, identity(player, body, uuid,
                BodyActionPolicy.Source.LIFECYCLE, new MindId(UUID.randomUUID()), 1, pig, true)));
        assertFalse(BodyActionPolicy.same(original, identity(player, body, uuid,
                BodyActionPolicy.Source.LIFECYCLE, mind, 2, pig, true)));
        assertFalse(BodyActionPolicy.same(original, identity(player, body, uuid,
                BodyActionPolicy.Source.LIFECYCLE, mind, 1, prototype(List.of()), true)));
        // A live revoke invalidates the old observation despite unchanged session and epoch.
        assertFalse(BodyActionPolicy.same(original, identity(player, body, uuid,
                BodyActionPolicy.Source.LIFECYCLE, mind, 1, pig, false)));
        assertFalse(BodyActionPolicy.same(identity(player, body, uuid,
                BodyActionPolicy.Source.LIFECYCLE, mind, 1, pig, false), original));
        assertFalse(BodyActionPolicy.same(null, original));
    }
}
