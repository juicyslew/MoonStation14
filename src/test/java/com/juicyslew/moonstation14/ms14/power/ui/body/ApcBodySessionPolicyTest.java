package com.juicyslew.moonstation14.ms14.power.ui.body;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.juicyslew.moonstation14.ms14.power.ui.body.ApcBodySessionPolicy.Decision.DENY;
import static com.juicyslew.moonstation14.ms14.power.ui.body.ApcBodySessionPolicy.Decision.MATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ApcBodySessionPolicyTest {
    private static final class EqualReference {
        @Override public boolean equals(Object other) { return other instanceof EqualReference; }
        @Override public int hashCode() { return 1; }
    }

    private static final class Fixture {
        Object player = new EqualReference();
        Object bodyObject = new EqualReference();
        Object level = new EqualReference();
        Object apc = new EqualReference();
        ResourceKey<Level> dimension = Level.OVERWORLD;
        BlockPos target = new BlockPos(5, 70, 9);
        UUID bodyUuid = UUID.randomUUID();
        MindId mind = new MindId(UUID.randomUUID());
        CharacterData prototype = new CharacterData(new CharacterData.SlipTargetData(false, true,
                false, false, List.of(), List.of()), Optional.empty(), List.of(), Optional.empty(), List.of());
        BodyActionPolicy.Identity body = identity(true);
        UUID token = UUID.randomUUID();
        long opened = 100;
        long expiry = 200;
        long now = 150;
        long epoch = 1;
        boolean connected = true;
        boolean loaded = true;
        boolean notRemoved = true;
        boolean isApc = true;
        boolean usable = true;
        boolean stunned = false;
        boolean geometry = true;

        BodyActionPolicy.Identity identity(boolean complex) {
            return new BodyActionPolicy.Identity(player, bodyObject, bodyUuid,
                    BodyActionPolicy.Source.LIFECYCLE, mind, new MobHarnessId(bodyUuid), 1,
                    prototype, complex);
        }

        ApcBodySessionPolicy.Session session() {
            return new ApcBodySessionPolicy.Session(player, identity(true), level, Level.OVERWORLD,
                    new BlockPos(5, 70, 9), apc, token, opened, expiry);
        }

        ApcBodySessionPolicy.Facts facts() {
            return new ApcBodySessionPolicy.Facts(player, connected, body, level, dimension, target,
                    apc, loaded, notRemoved, isApc, usable, stunned, geometry, token, epoch, now);
        }
    }

    @Test
    void liveExactSessionMatchesWithoutAnyHandRequirement() {
        var f = new Fixture();
        assertEquals(MATCH, ApcBodySessionPolicy.decide(f.session(), f.facts()));
        f.now = f.opened;
        assertEquals(MATCH, ApcBodySessionPolicy.decide(f.session(), f.facts()));
        f.now = f.expiry;
        assertEquals(MATCH, ApcBodySessionPolicy.decide(f.session(), f.facts()));
    }

    @Test
    void exactReferencesAndLeaseAreRequiredEvenWhenObjectsCompareEqual() {
        var f = new Fixture();
        var session = f.session();
        assertNotSame(f.body, session.body()); // separately resolved snapshot, not record equality
        f.player = new EqualReference();
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.connected = false;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.bodyObject = new EqualReference();
        f.body = f.identity(true);
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.body = new BodyActionPolicy.Identity(f.player, f.bodyObject, f.bodyUuid,
                BodyActionPolicy.Source.EXPERIMENTAL_HARNESS, f.mind, new MobHarnessId(f.bodyUuid),
                1, f.prototype, true);
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.body = new BodyActionPolicy.Identity(f.player, f.bodyObject, f.bodyUuid,
                BodyActionPolicy.Source.LIFECYCLE, f.mind, new MobHarnessId(f.bodyUuid),
                2, f.prototype, true);
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.bodyUuid = UUID.randomUUID();
        f.body = f.identity(true);
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.mind = new MindId(UUID.randomUUID());
        f.body = f.identity(true);
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.prototype = new CharacterData(new CharacterData.SlipTargetData(false, true,
                false, false, List.of(), List.of()), Optional.empty(), List.of(), Optional.empty(), List.of());
        f.body = f.identity(true);
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
    }

    @Test
    void tokenAndEpochMustMatch() {
        var f = new Fixture(); var session = f.session();
        f.token = UUID.randomUUID();
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.epoch = 2;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
    }

    @Test
    void levelTargetAndDeviceMustRemainExactAndLoaded() {
        var f = new Fixture(); var session = f.session();
        f.level = new EqualReference();
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.dimension = Level.NETHER;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.target = f.target.above();
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.apc = new EqualReference();
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.loaded = false;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.notRemoved = false;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.isApc = false;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
    }

    @Test
    void revokedComplexStatusAndGeometryDeny() {
        var f = new Fixture(); var session = f.session();
        f.body = f.identity(false);
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.usable = false;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.stunned = true;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); session = f.session();
        f.geometry = false;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
    }

    @Test
    void windowRejectsEarlyExpiredOversizedAndOverflowingTimes() {
        var f = new Fixture(); var session = f.session();
        f.now = 99;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f.now = 201;
        assertEquals(DENY, ApcBodySessionPolicy.decide(session, f.facts()));
        f = new Fixture(); f.expiry = f.opened + ApcBodySessionPolicy.MAX_LIFETIME_TICKS + 1;
        assertEquals(DENY, ApcBodySessionPolicy.decide(f.session(), f.facts()));
        f = new Fixture(); f.opened = Long.MAX_VALUE - 1; f.expiry = Long.MIN_VALUE;
        assertEquals(DENY, ApcBodySessionPolicy.decide(f.session(), f.facts()));
        f = new Fixture(); f.opened = -1;
        assertEquals(DENY, ApcBodySessionPolicy.decide(f.session(), f.facts()));
        assertEquals(DENY, ApcBodySessionPolicy.decide(null, f.facts()));
        assertEquals(DENY, ApcBodySessionPolicy.decide(f.session(), null));
    }

    @Test
    void mutablePositionIsCopiedAtPinTime() {
        var f = new Fixture();
        var mutable = new BlockPos.MutableBlockPos(5, 70, 9);
        var pinned = new ApcBodySessionPolicy.Session(f.player, f.body, f.level, f.dimension,
                mutable, f.apc, f.token, f.opened, f.expiry);
        mutable.set(10, 70, 9);
        assertEquals(MATCH, ApcBodySessionPolicy.decide(pinned, f.facts()));
    }
}
