package com.juicyslew.moonstation14.ms14.medical_observer;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.mobs.MobStateData;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Trusted lifecycle-backend fixture; this exercises server APIs, not authenticated transport. */
@PrefixGameTestTemplate(false)
@GameTestTags({"medical-state", "observer", "lifecycle"})
public final class BoundObserverGameTests {
    private BoundObserverGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void boundGhostRequiresManualReturn(GameTestHelper helper) {
        FakePlayer owner = owner(helper);
        Villager body = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(4, 1, 2));
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(new MobStateData(MobStateComponent.State.DEAD, 1)));
        Backend backend = new Backend(body);
        require(ObserverSystem.beginBoundCorpse(owner, body, 7001, backend), "dead bound body enrolls under living owner");
        ObserverSession corpse = ObserverSystem.query(owner);
        require(corpse != null && corpse.corpseBodyId().equals(body.getUUID()) && !corpse.corpseBodyId().equals(owner.getUUID()),
                "corpse association points at actual body, not carrier");
        require(!ObserverSystem.requestGhost(owner, corpse.epoch() + 1), "mismatched epoch is denied");
        require(ObserverSystem.requestGhost(owner, corpse.epoch()), "trusted backend stages ghost for bound corpse");
        ObserverSession ghost = ObserverSystem.query(owner);
        require(ghost != null && ghost.mode() == ObserverSession.Mode.GHOST && ghost.authorityGeneration() > corpse.authorityGeneration(),
                "ghost authority generation advances");
        Entity entity = helper.getLevel().getEntity(ghost.ghostId());
         require(entity instanceof GhostMobHarnessEntity && entity.isNoGravity(),
                "backend ghost is registered actual ghost harness (id=" + ghost.ghostId() + ", entity=" + entity + ")");
        require(ObserverSystem.mayTrackMedicalGhost(owner, entity), "committed ghost remains visible to owner after staging mapping is removed");
        require(!CharacterControlSystem.canAct(owner), "healthy carrier cannot act while observer session is active");
        require(!ObserverSystem.requestReturn(owner, ghost.epoch()), "dead body cannot return through backend");
        require(ObserverSystem.query(owner) != null && backend.returnCalls == 1, "failed return retains observer claim");
        body.setData(ModDataAttachments.DAMAGE.get(), new com.juicyslew.moonstation14.component.codec.attachment.DamageData());
        require(MobStateSystem.explicitRevive(body), "body revives through mob-state system");
        require(ObserverSystem.query(owner) != null && backend.returnCalls == 1,
                "revival alone does not implicitly return a GHOST session");
        require(ObserverSystem.requestReturn(owner, ghost.epoch()), "manual return succeeds for revived bound body");
        require(ObserverSystem.query(owner) == null && backend.returnCalls == 2, "manual return clears claim exactly once");
        entity.discard(); body.discard(); owner.discard(); helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void boundCorpseRecoveryClearsClaim(GameTestHelper helper) {
        FakePlayer owner = owner(helper);
        Villager body = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(4, 1, 2));
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(new MobStateData(MobStateComponent.State.DEAD, 1)));
        Backend backend = new Backend(body);
        require(ObserverSystem.beginBoundCorpse(owner, body, 7002, backend), "dead bound body enrolls");
        require(MobStateSystem.policy(body) != null, "body consumes resolved medical policy");
        body.setData(ModDataAttachments.DAMAGE.get(), new com.juicyslew.moonstation14.component.codec.attachment.DamageData());
        require(MobStateSystem.explicitRevive(body), "body revives through mob-state system");
        require(ObserverSystem.query(owner) != null && backend.returnCalls == 0,
                "fixture carrier is not in the authenticated server player list");
        require(ObserverSystem.tryRecoverBoundCorpse(owner), "tick recovery helper recovers revived bound body");
        require(ObserverSystem.query(owner) == null && backend.returnCalls == 1, "recovery returns backend and clears claim");
        require(CharacterControlSystem.canAct(owner), "carrier is no longer locked after recovery");
        body.discard(); owner.discard(); helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void registeredReviveCommandEnforcesPermissionAndRevivesExactCorpse(GameTestHelper helper) {
        FakePlayer owner = owner(helper);
        Villager body = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(4, 1, 2));
        UUID bodyId = body.getUUID();
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(new MobStateData(MobStateComponent.State.DEAD, 1)));
        Backend authority = new Backend(body);
        require(ObserverSystem.beginBoundCorpse(owner, body, 7101, authority), "exact dead body receives linked corpse session");
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        boolean denied = false;
        try { dispatcher.execute("ms14 revive", owner.createCommandSourceStack().withPermission(0)); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
        require(denied && MobStateSystem.isDead(body), "registered command denies non-operator without mutating body");
        int result;
        try { result = dispatcher.execute("ms14 revive", owner.createCommandSourceStack().withPermission(2)); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("registered /ms14 revive failed: " + failure.getMessage());
        }
        require(result == 1 && body.getUUID().equals(bodyId) && helper.getLevel().getEntity(bodyId) == body,
                "registered command revives the exact same world body");
        require(MobStateSystem.isAlive(body), "registered command clears typed damage then explicitly revives");
        if (ObserverSystem.query(owner) != null)
            require(ObserverSystem.tryRecoverBoundCorpse(owner), "corpse-first recovery enters linked-body handshake");
        require(ObserverSystem.query(owner) == null && authority.returnCalls == 1,
                "corpse-first recovery completes existing linked-body handshake");
        body.discard(); owner.discard(); helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void registeredReviveCommandKeepsGhostSessionAndExactBodyForRReturn(GameTestHelper helper) {
        FakePlayer owner = owner(helper);
        Villager body = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(4, 1, 2));
        UUID bodyId = body.getUUID();
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(new MobStateData(MobStateComponent.State.DEAD, 1)));
        Backend authority = new Backend(body);
        require(ObserverSystem.beginBoundCorpse(owner, body, 7102, authority), "exact dead body receives linked corpse session");
        require(ObserverSystem.requestGhost(owner, 7102), "linked ghost established before operator revival");
        ObserverSession ghost = ObserverSystem.query(owner);
        require(ghost != null && ghost.mode() == ObserverSession.Mode.GHOST, "session is ghosted");
        int result;
        try { result = helper.getLevel().getServer().getCommands().getDispatcher()
                .execute("ms14 revive", owner.createCommandSourceStack().withPermission(2)); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("registered /ms14 revive failed: " + failure.getMessage());
        }
        require(result == 1 && body.getUUID().equals(bodyId) && helper.getLevel().getEntity(bodyId) == body,
                "ghost recovery revives same registered body");
        require(ObserverSystem.query(owner) == ghost && ghost.mode() == ObserverSession.Mode.GHOST,
                "operator revival does not force ghost return");
        require(ObserverSystem.requestReturn(owner, ghost.epoch()), "normal R return handshake remains available");
        require(ObserverSystem.query(owner) == null && authority.returnCalls == 1,
                "normal return clears observer claim after command revival");
        body.discard(); owner.discard(); helper.succeed();
    }

    private static FakePlayer owner(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bound-observer-owner"));
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(player), "living carrier joins level");
        return player;
    }

    private static final class Backend implements BoundObserverAuthority {
        private final LivingEntity body;
        private int returnCalls;
        private Backend(LivingEntity body) { this.body = body; }
        @Override public boolean valid(net.minecraft.server.level.ServerPlayer owner, LivingEntity target, ObserverSession session) {
            return target == body && session.ownerId().equals(owner.getUUID()) && session.corpseBodyId().equals(body.getUUID());
        }
        @Override public GhostStartResult startGhost(net.minecraft.server.level.ServerPlayer owner, LivingEntity target, long epoch,
                com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent observerMovement) {
            GhostMobHarnessEntity ghost = GhostMobHarnessRegistration.getEntityType().create(owner.level());
            if (ghost == null) return null;
            ghost.moveTo(owner.position());
            require(ObserverSystem.beforeBoundGhostAdmission(owner, ghost), "backend obtains staging admission");
            require(ObserverSystem.mayTrackMedicalGhost(owner, ghost), "staged ghost is trackable by trusted owner before world admission");
            FakePlayer outsider = new FakePlayer((net.minecraft.server.level.ServerLevel) owner.level(),
                    new GameProfile(UUID.randomUUID(), "bound-observer-outsider"));
            require(!ObserverSystem.mayTrackMedicalGhost(outsider, ghost), "staged ghost remains hidden from alive non-owner");
            require(owner.level().addFreshEntity(ghost), "backend admits staged ghost");
            return new GhostStartResult(ghost, epoch + 1);
        }
        @Override public boolean returnToBody(net.minecraft.server.level.ServerPlayer owner, LivingEntity target, ObserverSession session) {
            returnCalls++;
            return target == body && MobStateSystem.isAlive(body);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
