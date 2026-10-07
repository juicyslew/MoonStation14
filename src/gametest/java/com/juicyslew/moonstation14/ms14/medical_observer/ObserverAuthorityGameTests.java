package com.juicyslew.moonstation14.ms14.medical_observer;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.components.GhostOnMoveComponent;
import com.juicyslew.moonstation14.ms14.character.components.IncorporealComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Trusted server-API observer authority coverage; does not claim authenticated network coverage. */
@PrefixGameTestTemplate(false)
@GameTestTags({"medical-state", "observer"})
public final class ObserverAuthorityGameTests {
    private ObserverAuthorityGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 10)
    public static void nativeLivingViewYawUsesHeadYawForObserverCamera(GameTestHelper helper) {
        var body = com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration
                .getEntityType().create(helper.getLevel());
        require(body != null, "native living camera fixture created");
        for (float yaw : new float[]{179, 181, 540, -540}) {
            ObserverGhostLook.apply(body, yaw, 25);
            require(Math.abs(body.getViewYRot(0f) - yaw) < .001f
                            && Math.abs(body.getViewYRot(.5f) - yaw) < .001f
                            && Math.abs(body.getViewYRot(1f) - yaw) < .001f,
                    "native camera interpolation follows applied yaw " + yaw);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void nativeDeathGhostAndManualReturn(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "observer-authority"));
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(player), "FakePlayer joins server level");

        player.setHealth(0);
        MobStateSystem.NativeDeathEvents.onLivingDeath(
                new LivingDeathEvent(player, helper.getLevel().damageSources().generic()));
        ObserverSession corpse = ObserverSystem.query(player);
        require(corpse != null && corpse.mode() == ObserverSession.Mode.CORPSE,
                "native death event creates corpse observer record");
        require(corpse.corpseBodyId().equals(player.getUUID()), "corpse body uses native player UUID");
        long epoch = corpse.epoch();
        require(!ObserverSystem.submitIntent(player, epoch + 1, new ObserverIntent(0, 1000, 0, false, 0)),
                "stale intent epoch is denied");
        require(!ObserverSystem.requestReturn(player, epoch), "return from corpse mode is denied");
        require(ObserverSystem.requestGhost(player, epoch), "current corpse epoch creates ghost");

        ObserverSession ghostSession = ObserverSystem.query(player);
        require(ghostSession != null && ghostSession.mode() == ObserverSession.Mode.GHOST,
                "ghost request enters ghost mode");
        require(ghostSession.corpseBodyId().equals(player.getUUID()), "ghost session retains native corpse UUID");
        require(!ghostSession.ghostId().equals(player.getUUID()), "ghost has a distinct UUID");
        Entity ghost = ((ServerLevel) player.level()).getEntity(ghostSession.ghostId());
        require(ghost != null && ghost.position().distanceToSqr(player.position()) < 1.0e-8,
                "ghost begins at native corpse position");
        require(!ObserverSystem.requestGhost(player, epoch + 1), "stale ghost epoch is denied");
        require(!ObserverSystem.requestReturn(player, epoch), "return while dead is denied");

        var body = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy
                .resolveActor(player).orElseThrow(() -> new GameTestAssertException("native human profile resolves"));
        var ghostPolicy = body.component(GhostOnMoveComponent.class).orElse(null);
        require(ghostPolicy != null, "resolved human body has ghost-on-move policy");
        var observer = PrototypeRuntime.serverCharacters().get(
                ResourceLocation.fromNamespaceAndPath("moonstation14", "observer"));
        require(observer != null, "resolved observer prototype exists");
        var movement = observer.component(ObserverMovementComponent.class).orElse(null);
        require(movement != null && observer.component(IncorporealComponent.class).isPresent(),
                "resolved observer prototype has movement and incorporeal policy");
        require(movement.walkSpeed() == 8 && movement.sprintSpeed() == 12,
                "resolved observer movement speeds are 8/12");

        require(MobStateSystem.explicitRevive(player), "explicit revive restores native body");
        require(player.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()).data().state()
                        == MobStateComponent.State.ALIVE,
                "revived native body is alive");
        require(ObserverSystem.query(player) != null && ObserverSystem.query(player).mode() == ObserverSession.Mode.GHOST,
                "reviving body does not automatically end ghost session");
        require(ObserverSystem.requestReturn(player, epoch), "manual return succeeds after revive");
        require(ObserverSystem.query(player) == null && ghost.isRemoved(), "manual return removes session and ghost");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void criticalBodyCannotAdmitDeathGhost(GameTestHelper helper) {
        // NeoForge FakePlayer ignores native damage; typed medical damage is used here but the fixture
        // still overrides invulnerability so the damage adapter can project the expected native health.
        FakePlayer player = new DamageableFakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "critical-observer"));
        require(helper.getLevel().addFreshEntity(player), "test player joins server level");
        require(DamageSystem.applyHealthChange(player, java.util.Map.of(DamageKeys.BLUNT, 100f), 1f, false)
                        == DamageSystem.Result.APPLIED,
                "typed critical damage is applied");
        var criticalAttachment = player.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        var initialLedger = player.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        double initialTotal = initialLedger == null ? 0 : initialLedger.getMap().values().stream()
                .mapToDouble(Float::doubleValue).sum();
        ObserverSession afterCritical = ObserverSystem.query(player);
        require(MobStateSystem.isCritical(player) && afterCritical == null,
                "100 typed damage must leave body Critical without observer admission; state="
                        + (criticalAttachment == null ? "<missing>" : criticalAttachment.data().state())
                        + ", revision=" + (criticalAttachment == null ? "<missing>" : criticalAttachment.data().revision())
                        + ", ledger=" + initialTotal + ", nativeHealth=" + player.getHealth() + "/"
                        + player.getMaxHealth() + ", session=" + (afterCritical == null ? "<none>"
                        : afterCritical.mode() + "/" + afterCritical.epoch()));
        player.invulnerableTime = 0;
        require(DamageSystem.applyHealthChange(player, java.util.Map.of(DamageKeys.BLUNT, 100f), 1f, false)
                        == DamageSystem.Result.APPLIED,
                "second typed damage increment creates the initial Dead state");
        var ledger = player.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(ledger != null && ledger.getMap().values().stream().mapToDouble(Float::doubleValue).sum() == 200,
                "exactly 200 typed damage is present in the authoritative body ledger");
        require(MobStateSystem.isDead(player), "authoritative body state is Dead before observer admission");
        ObserverSession corpse = ObserverSystem.query(player);
        require(corpse != null && corpse.mode() == ObserverSession.Mode.CORPSE, "death establishes corpse session");
        require(DamageSystem.applyHealthChange(player, java.util.Map.of(DamageKeys.BLUNT, -100f), 1f, false)
                        == DamageSystem.Result.APPLIED,
                "heal typed ledger to 100 damage");
        require(MobStateSystem.explicitRevive(player) && MobStateSystem.isCritical(player),
                "explicit revive at 100 typed damage leaves Critical body");
        require(!ObserverSystem.requestGhost(player, corpse.epoch()),
                "Critical body cannot pass must-be-Dead ghost admission");
        player.discard();
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class DamageableFakePlayer extends FakePlayer {
        private DamageableFakePlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
            // ServerPlayer's spawn protection is decremented by ServerPlayer.tick(), which FakePlayer
            // intentionally replaces with an empty implementation. Clear only this fixture's timer.
            try {
                var spawnProtection = net.minecraft.server.level.ServerPlayer.class
                        .getDeclaredField("spawnInvulnerableTime");
                spawnProtection.setAccessible(true);
                spawnProtection.setInt(this, 0);
            } catch (ReflectiveOperationException failure) {
                throw new GameTestAssertException("Could not disable FakePlayer spawn protection for typed-damage fixture: "
                        + failure.getMessage());
            }
            invulnerableTime = 0;
        }

        @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
            return false;
        }
    }
}
