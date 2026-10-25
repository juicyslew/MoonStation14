package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.CommittedMouseRoleSetup;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.entities.MouseMob;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.mobs.MobStateData;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"lifecycle", "character", "medical"})
public final class CharacterDirectionGameTests {
    private CharacterDirectionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"character_direction"})
    public static void acceptedVoluntaryLookUsesMedicalDirectionPermission(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setYRot(0);
        body.setXRot(0);
        require(CharacterControlSystem.applyVoluntaryLook(body, 90, 15), "healthy character look applies");
        require(body.getYRot() == 90 && body.getXRot() == 15 && body.getYHeadRot() == 90,
                "healthy accepted direction updates physical yaw and head yaw");

        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(
                new MobStateData(MobStateComponent.State.CRITICAL, 1)));
        require(!CharacterControlSystem.applyVoluntaryLook(body, 180, 30), "critical character look is denied");
        require(body.getYRot() == 90 && body.getXRot() == 15 && body.getYHeadRot() == 90,
                "critical voluntary look leaves physical facing unchanged");
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(
                new MobStateData(MobStateComponent.State.ALIVE, 2)));
        require(CharacterControlSystem.applyVoluntaryLook(body, 45, -10), "recovered character look applies");
        require(body.getYRot() == 45 && body.getYHeadRot() == 45, "recovery restores voluntary facing");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    @GameTestTags({"character_direction", "lifecycle", "medical"})
    public static void acceptedPlayerMouseIntentDoesNotTurnCriticalBody(GameTestHelper helper) {
        var level = helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "direction-mouse");
        MouseMob mouse = (MouseMob) com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "canonical mouse constructed");
        var spawn = helper.absolutePos(new BlockPos(2, 1, 2));
        mouse.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        require(level.addFreshEntity(mouse), "mouse joins ordinary level before canonical role adoption");
        CommittedMouseRoleSetup.start(helper, fixture, mouse, committed -> {
            long epoch = LifecycleCharacterSessionControl.activeCharacterBody(fixture.player()).orElseThrow().epoch();
            committed.setYRot(0);
            committed.setXRot(0);
            committed.setYHeadRot(0);
            while (fixture.pollOutbound() != null) { }
            RegisteredPlayerReadyDriver.submitAcceptedIntent(fixture, new GhostControlPayloads.Intent(epoch, 1,
                    (short) 0, (short) 0, (byte) 0, (byte) 0, 90, 0));
            helper.runAfterDelay(1, () -> {
                try {
                    require(committed.getYRot() == 90 && committed.getYHeadRot() == 90,
                            "healthy accepted PLAYER_MOUSE intent advances physical yaw");
                    require(acknowledged(fixture, epoch, committed.getId(), 1), "healthy intent acknowledged as applied");
                    double criticalThreshold = MobStateSystem.policy(committed).thresholds().thresholds().entrySet().stream()
                            .filter(entry -> entry.getValue() == MobStateComponent.State.CRITICAL)
                            .mapToDouble(java.util.Map.Entry::getKey).min().orElseThrow();
                    double deadThreshold = MobStateSystem.policy(committed).thresholds().thresholds().entrySet().stream()
                            .filter(entry -> entry.getValue() == MobStateComponent.State.DEAD)
                            .mapToDouble(java.util.Map.Entry::getKey).min().orElseThrow();
                    float criticalDamage = (float) Math.min(deadThreshold - 1, criticalThreshold);
                    require(criticalDamage >= criticalThreshold && criticalDamage < deadThreshold
                                    && DamageSystem.applyHealthChange(committed,
                                            java.util.Map.of(DamageKeys.BLUNT, criticalDamage), 1f, true)
                                            == DamageSystem.Result.APPLIED,
                            "resolved typed damage stages the mouse in critical below its dead threshold");
                    require(MobStateSystem.isCritical(committed)
                                    && !CharacterControlSystem.canChangeDirection(committed),
                            "critical committed body is medically denied direction before input");
                    float baseline = committed.getYRot();
                    float headBaseline = committed.getYHeadRot();
                    RegisteredPlayerReadyDriver.submitAcceptedIntent(fixture, new GhostControlPayloads.Intent(epoch, 2,
                            (short) 0, (short) 0, (byte) 0, (byte) 0, 0, 0));
                    helper.runAfterDelay(1, () -> {
                      try {
                    require(com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isCritical(committed)
                                    && committed.getYRot() == baseline && committed.getYHeadRot() == headBaseline,
                            "accepted critical PLAYER_MOUSE intent leaves physical yaw and head yaw unchanged");
                    require(acknowledged(fixture, epoch, committed.getId(), 2), "critical intent acknowledged as applied");
                    require(DamageSystem.applyHealthChange(committed,
                                    java.util.Map.of(DamageKeys.BLUNT, -criticalDamage), 1f, true)
                                    == DamageSystem.Result.APPLIED && MobStateSystem.isAlive(committed),
                            "typed healing restores the committed mouse to alive");
                    RegisteredPlayerReadyDriver.submitAcceptedIntent(fixture, new GhostControlPayloads.Intent(epoch, 3,
                            (short) 0, (short) 0, (byte) 0, (byte) 0, 45, -10));
                    helper.runAfterDelay(1, () -> {
                        try {
                            require(committed.getYRot() == 45 && committed.getYHeadRot() == 45,
                                    "recovered accepted PLAYER_MOUSE intent restores physical facing");
                            require(acknowledged(fixture, epoch, committed.getId(), 3), "recovery intent acknowledged as applied");
                            fixture.close();
                            helper.succeed();
                        } catch (RuntimeException | Error failure) {
                            fixture.close();
                            throw failure;
                        }
                    });
                      } catch (RuntimeException | Error failure) { fixture.close(); throw failure; }
                    });
                } catch (RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private static boolean acknowledged(RegisteredServerPlayerFixture fixture, long epoch, int bodyId, long sequence) {
        boolean found = false;
        Object outbound;
        while ((outbound = fixture.pollOutbound()) != null) {
            if (outbound instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof GhostControlPayloads.Snapshot snapshot
                    && snapshot.epoch() == epoch && snapshot.harnessEntityId() == bodyId
                    && snapshot.harnessKind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER
                    && snapshot.acknowledgedAppliedSequence() == sequence)
                found = true;
        }
        return found;
    }
}
