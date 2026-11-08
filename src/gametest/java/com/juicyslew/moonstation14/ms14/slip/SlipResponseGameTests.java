package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaState;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import com.juicyslew.moonstation14.ms14.standing.PostureData;
import com.juicyslew.moonstation14.ms14.standing.PostureAttachment;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"slip", "movement", "slip-response"})
public final class SlipResponseGameTests {
    private SlipResponseGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void genericSourceResponseAppliesConfiguredEffects(GameTestHelper helper) {
        Villager source = human(helper, new BlockPos(1, 1, 1));
        Villager target = human(helper, new BlockPos(3, 1, 1));
        var profile = SlipSystem.looseItemResponse(.5d, 1.5d, 1.5d, .5d, false);
        SlipSystem.respond(new SlipSystem.SlipResponse(helper.getLevel(), source, target, profile,
                new Vec3(2, 0, 0), false, false, true), (IStatusEffectTrait) target);

        var effects = target.getExistingDataOrNull(ModDataAttachments.STATUS_EFFECT.get());
        var stun = effects.get(ModStatusEffects.createKey("statuseffectstunned")).orElseThrow();
        var knockdown = effects.get(ModStatusEffects.createKey("knockdown")).orElseThrow();
        var friction = effects.get(ModStatusEffects.createKey("slip_friction")).orElseThrow();
        require(stun.remainingDurationTicks().orElseThrow() == 10, "new response applies exact 10-tick stun");
        require(knockdown.remainingDurationTicks().orElseThrow() == 30, "new response applies exact 30-tick knockdown");
        require(friction.remainingDurationTicks().orElseThrow() == 10, "new response applies exact 10-tick friction");

        require(Math.abs(target.getDeltaMovement().x - 3d) < 1.0e-6d, "launch velocity scaled by 1.5");
        require(!SlipSystem.isSliding(target), "generic-source response does not publish sliding");
        require(StaminaSystem.state(target).damage() == 25f, "configured 25 stamina damage applied");
        require(Math.abs(StatusEffectSystem.frictionFactor(target) - .5d) < 1.0e-6d,
                "configured timed friction factor is active");
        require(PostureSystem.isDownPosture(target), "knockdown posture is active");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void puddleAdapterDoesNotApplyStaminaOrTimedFrictionAndRestoresSliding(GameTestHelper helper) {
        Villager target = human(helper, new BlockPos(2, 1, 2));
        require(!SlipSystem.isSliding(target), "target starts not sliding");
        var profile = new SlipResponseData(.5d, 1.5d, 1.5d, .05d, 0d, 0d,
                true, true, false);
        SlipSystem.respond(new SlipSystem.SlipResponse(helper.getLevel(), null, target, profile,
                new Vec3(2, 0, 0), false, false, true), (IStatusEffectTrait) target);

        require(SlipSystem.isSliding(target), "puddle response restores sliding before down effects");
        require(StaminaSystem.state(target).equals(StaminaState.EMPTY), "puddle response adds no stamina damage");
        require(StatusEffectSystem.frictionFactor(target) == 1d, "puddle response applies no timed friction");
        require(Math.abs(target.getDeltaMovement().x - 3d) < 1.0e-6d, "puddle launch velocity scaled");
        target.setDeltaMovement(new Vec3(.25d, .5d, .75d));
        SlipSystem.respond(new SlipSystem.SlipResponse(helper.getLevel(), null, target, profile,
                new Vec3(2, 0, 0), true, false, true), (IStatusEffectTrait) target);
        require(target.getDeltaMovement().equals(new Vec3(.25d, .5d, .75d)), "already-sliding response retains existing velocity");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
    public static void sourceResponseDropsOnlySourcesOwnedHands(GameTestHelper helper) {
        RegisteredServerPlayerFixture sourceFixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "slip-source");
        RegisteredServerPlayerFixture victimFixture = null;
        try {
            victimFixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "slip-victim");
            RegisteredServerPlayerFixture finalVictimFixture = victimFixture;
            helper.runAtTickTime(2, () -> {
                try {
                    var sourceReady = RegisteredPlayerReadyDriver.readyAndQuery(sourceFixture);
                    var victimReady = RegisteredPlayerReadyDriver.readyAndQuery(finalVictimFixture);
                    var source = sourceReady.body();
                    var victim = victimReady.body();
                    ServerLevel level = (ServerLevel) helper.getLevel();
                    source.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter());
                    victim.setPos(helper.absolutePos(new BlockPos(3, 1, 1)).getCenter());
                    ItemStack sourceStack = new ItemStack(ModItems.STEEL.get(), 2);
                    ItemStack victimStack = new ItemStack(ModItems.STEEL.get(), 3);
                    var sourceHands = source.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    var victimHands = victim.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    String sourceHand = sourceHands.handIds().get(0), victimHand = victimHands.handIds().get(0);
                    var sourceItem = publish(level, source, sourceStack);
                    var victimItem = publish(level, victim, victimStack);
                    var entitiesBefore = level.getEntitiesOfClass(LooseItemEntity.class, source.getBoundingBox().inflate(8))
                            .stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
                    var sourceAuthority = HandActorAuthority.resolve(sourceFixture.player()).orElseThrow();
                    var victimAuthority = HandActorAuthority.resolve(finalVictimFixture.player()).orElseThrow();
                    require(BodyHandItemTransfer.pickupTrustedCreated(sourceAuthority, sourceItem, sourceStack.copy(), () -> true) == BodyHandItemTransfer.Result.SUCCESS, "source obtains owned stack");
                    require(BodyHandItemTransfer.pickupTrustedCreated(victimAuthority, victimItem, victimStack.copy(), () -> true) == BodyHandItemTransfer.Result.SUCCESS, "victim obtains owned stack");
                    sourceHands = source.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    victimHands = victim.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    String sourceToken = sourceHands.token(sourceHand).orElseThrow();
                    String victimToken = victimHands.token(victimHand).orElseThrow();
                    long sourceRevision = sourceHands.revision(), victimRevision = victimHands.revision();
                    var profile = SlipSystem.looseItemResponse(.5d, 1.5d, 1.5d, .5d, false);
                    SlipSystem.respond(new SlipSystem.SlipResponse(level, source, victim, profile,
                            new Vec3(1, 0, 0), false, false, true), (IStatusEffectTrait) victim);
                    var sourceAfter = source.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    var victimAfter = victim.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    require(sourceAfter.stackCopy(sourceHand).isEmpty(), "source hand emptied");
                    require(victimAfter.stackCopy(victimHand).filter(s -> ItemStack.isSameItemSameComponents(s, victimStack) && s.getCount() == victimStack.getCount()).isPresent(), "victim item/count/components retained");
                    require(victimAfter.token(victimHand).filter(victimToken::equals).isPresent() && victimAfter.revision() == victimRevision, "victim token and revision unchanged");
                    require(sourceAfter.revision() == sourceRevision + 1 && sourceAfter.token(sourceHand).isEmpty(), "source ownership revision advanced once and token cleared");
                    var dropped = level.getEntitiesOfClass(LooseItemEntity.class, source.getBoundingBox().inflate(8));
                    require(sourceItem.isRemoved() && level.getEntity(sourceItem.getUUID()) == null, "original pickup entity absent after pickup");
                    var newMatching = dropped.stream().filter(e -> !entitiesBefore.contains(e.getUUID()))
                            .filter(e -> !e.isRemoved() && e.level() == level && level.getEntity(e.getUUID()) == e)
                            .filter(e -> ItemStack.isSameItemSameComponents(e.getItem(), sourceStack) && e.getItem().getCount() == sourceStack.getCount()).toList();
                    require(newMatching.size() == 1, "exactly one newly created live loose entity matches source stack item/count/components");
                    helper.succeed();
                } finally { sourceFixture.close(); finalVictimFixture.close(); }
            });
        } catch (RuntimeException | Error failure) { sourceFixture.close(); if (victimFixture != null) victimFixture.close(); throw failure; }
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void alreadyDownResponsePreservesExistingEffectsAndPosturePolicy(GameTestHelper helper) {
        Villager target = human(helper, new BlockPos(2, 1, 2));
        Villager source = human(helper, new BlockPos(1, 1, 1));
        var attachment = ModDataAttachments.POSTURE.get();
        var level = (ServerLevel) helper.getLevel();
        long until = level.getServer().overworld().getGameTime() + 200;
        var beforePosture = new PostureData(true, false, until, 0, 2, 2, false, false, false);
        target.setData(attachment, new PostureAttachment(beforePosture));
        require(PostureSystem.isDownPosture(target), "target is actually down before response");
        StatusEffectSystem.apply(((IStatusEffectTrait) target).toHandleSelf(), level, ModStatusEffects.createKey("knockdown"),
                StatusEffectOperation.UPDATE, java.util.OptionalInt.of(200), 0);
        StatusEffectSystem.apply(((IStatusEffectTrait) target).toHandleSelf(), level, ModStatusEffects.createKey("statuseffectstunned"),
                StatusEffectOperation.UPDATE, java.util.OptionalInt.of(150), 0);
        StatusEffectSystem.apply(((IStatusEffectTrait) target).toHandleSelf(), level, ModStatusEffects.createKey("slip_friction"),
                StatusEffectOperation.UPDATE, java.util.OptionalInt.of(100), 0,
                new StatusEffectPayload.FrictionModifier(.7d));
        var statusAttachment = target.getExistingDataOrNull(ModDataAttachments.STATUS_EFFECT.get());
        var statuses = statusAttachment.snapshot();
        var stunKey = ModStatusEffects.createKey("statuseffectstunned");
        var knockdownKey = ModStatusEffects.createKey("knockdown");
        var frictionKey = ModStatusEffects.createKey("slip_friction");
        require(statusAttachment.get(stunKey).orElseThrow().remainingDurationTicks().orElseThrow() == 150
                        && statusAttachment.get(knockdownKey).orElseThrow().remainingDurationTicks().orElseThrow() == 200
                        && statusAttachment.get(frictionKey).orElseThrow().remainingDurationTicks().orElseThrow() == 100,
                "existing stun, knockdown, and friction durations are active at known values");
        var beforeStamina = StaminaSystem.state(target);
        var profile = SlipSystem.looseItemResponse(.5d, 1.5d, 2d, .5d, true);
        SlipSystem.respond(new SlipSystem.SlipResponse(level, source, target, profile,
                new Vec3(1, 0, 0), false, true, true), (IStatusEffectTrait) target);
        var afterPosture = target.getExistingDataOrNull(attachment).data();
        var afterStatuses = target.getExistingDataOrNull(ModDataAttachments.STATUS_EFFECT.get()).snapshot();
        require(StaminaSystem.state(target).equals(beforeStamina), "already-down response leaves stamina unchanged");
        require(afterStatuses.equals(statuses), "already-down response leaves stun/knockdown/friction instances and durations unchanged");
        var afterEffects = target.getExistingDataOrNull(ModDataAttachments.STATUS_EFFECT.get());
        require(afterEffects.get(stunKey).orElseThrow().remainingDurationTicks().orElseThrow() == 150
                        && afterEffects.get(knockdownKey).orElseThrow().remainingDurationTicks().orElseThrow() == 200
                        && afterEffects.get(frictionKey).orElseThrow().remainingDurationTicks().orElseThrow() == 100,
                "already-down response preserves exact existing active durations");
        require(afterPosture.voluntaryAutoStand() == beforePosture.voluntaryAutoStand()
                        && afterPosture.knockdownUntil() == beforePosture.knockdownUntil(),
                "already-down response preserves long posture deadline and auto-stand policy");
        require(Math.abs(target.getDeltaMovement().x - 2d) < 1.0e-6d, "super-slippery already-down response applies launch multiplier");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
    public static void registeredBodyAudienceReceivesOnlyNewDownSlipCue(GameTestHelper helper) {
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "slip-audio-victim");
        helper.runAtTickTime(2, () -> {
            try {
                var ready = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var target = ready.body();
                var level = (ServerLevel) helper.getLevel();
                BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
                target.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                var sounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
                var profile = SlipSystem.looseItemResponse(.5d, 1.5d, 1d, .5d, false);
                SlipSystem.respond(new SlipSystem.SlipResponse(level, null, target, profile,
                        Vec3.ZERO, false, false, false), (IStatusEffectTrait) target);
                drainSounds(fixture, sounds);
                var cues = sounds.stream().filter(p -> p.getSound().value() == com.juicyslew.moonstation14.sounds.ModSounds.SLIP.get()).toList();
                require(cues.size() == 1, "registered body-view recipient receives one slip cue");
                requireSlipSound(cues.get(0), pos, 1f);

                sounds.clear();
                SlipSystem.respond(new SlipSystem.SlipResponse(level, null, target, profile,
                        Vec3.ZERO, false, true, false), (IStatusEffectTrait) target);
                drainSounds(fixture, sounds);
                require(sounds.stream().noneMatch(p -> p.getSound().value() == com.juicyslew.moonstation14.sounds.ModSounds.SLIP.get()),
                        "already-down response emits no additional slip cue");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("registered slip audio: " + failure); }
            finally { fixture.close(); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
    public static void registeredSlipAudioUsesCameraAndConfiguredDecibelGain(GameTestHelper helper) {
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "slip-audio-camera");
        helper.runAtTickTime(2, () -> {
            try {
                var ready = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = ready.body();
                var level = (ServerLevel) helper.getLevel();
                BlockPos near = helper.absolutePos(new BlockPos(2, 1, 2));
                BlockPos far = helper.absolutePos(new BlockPos(40, 1, 40));
                body.setPos(near.getX() + .5, near.getY(), near.getZ() + .5);
                fixture.player().setPos(far.getX() + .5, far.getY(), far.getZ() + .5);
                fixture.player().setCamera(body);
                require(fixture.player().getCamera() == body, "committed camera identity is actual enrolled body");
                var packets = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
                var zero = new SlipResponseData(.5d, 1.5d, 1d, .5d, 0d, 0d,
                        false, true, false, SlipResponseData.DEFAULT_SOUND, -100d);
                // A living non-player is a real native carrier, deliberately distant from the near camera.
                // GameTestHelper.spawn expects template-relative coordinates; near/far above are absolute.
                var carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(40, 1, 40));
                SlipSystem.respond(new SlipSystem.SlipResponse(level, carrier, body, zero,
                        Vec3.ZERO, false, false, false), (IStatusEffectTrait) body);
                drainSounds(fixture, packets);
                var cue = packets.stream().filter(p -> p.getSound().value() == com.juicyslew.moonstation14.sounds.ModSounds.SLIP.get()).toList();
                require(cue.size() == 1, "near body camera hears cue although native carrier is far");
                requireSlipSound(cue.get(0), near, 1.0e-5f);
                require(carrier.blockPosition().distSqr(body.blockPosition()) > 1000, "native carrier is separately verified far from target");

                packets.clear();
                body.setPos(far.getX() + .5, far.getY(), far.getZ() + .5);
                fixture.player().setCamera(body);
                require(fixture.player().getCamera() == body, "camera remains committed to far body");
                var nearTarget = human(helper, new BlockPos(3, 1, 2));
                nearTarget.setPos(near.getX() + .5, near.getY(), near.getZ() + .5);
                var nearCarrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
                SlipSystem.respond(new SlipSystem.SlipResponse(level, nearCarrier, nearTarget, zero,
                        Vec3.ZERO, false, false, false), (IStatusEffectTrait) nearTarget);
                drainSounds(fixture, packets);
                require(packets.stream().noneMatch(p -> p.getSound().value() == com.juicyslew.moonstation14.sounds.ModSounds.SLIP.get()),
                        "far body camera does not hear cue even when native carrier is near");
                require(nearCarrier.blockPosition().distSqr(nearTarget.blockPosition()) < 2, "native carrier is separately verified near slip target");
                require(fixture.player().getCamera().blockPosition().distSqr(nearTarget.blockPosition()) > 1000,
                        "registered viewer camera remains far from near slip target");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("registered slip camera audio: " + failure); }
            finally { fixture.close(); }
        });
    }

    private static void drainSounds(RegisteredServerPlayerFixture fixture,
            java.util.List<net.minecraft.network.protocol.game.ClientboundSoundPacket> sounds) {
        Object packet;
        while ((packet = fixture.pollOutbound()) != null)
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound) sounds.add(sound);
    }

    private static void requireSlipSound(net.minecraft.network.protocol.game.ClientboundSoundPacket packet, BlockPos pos, float gain) {
        require(packet.getSound().value() == com.juicyslew.moonstation14.sounds.ModSounds.SLIP.get(), "slip sound event");
        require(packet.getSource() == net.minecraft.sounds.SoundSource.PLAYERS, "slip sound uses PLAYERS source");
        require(Math.abs(packet.getVolume() - gain) <= 1.0e-7f && packet.getPitch() == 1f, "slip sound gain/pitch");
        var expected = new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(com.juicyslew.moonstation14.sounds.ModSounds.SLIP.get()),
                net.minecraft.sounds.SoundSource.PLAYERS, pos.getX() + .5, pos.getY(), pos.getZ() + .5, gain, 1f, 0L);
        require(packet.getX() == expected.getX() && packet.getY() == expected.getY() && packet.getZ() == expected.getZ(),
                "slip cue packet position matches native coordinate encoding");
    }

    private static LooseItemEntity publish(ServerLevel level, LivingEntity body, ItemStack stack) {
        var entity = LooseItemEntity.create(level, body.position().add(0, 0, -1), stack.copy());
        require(level.addFreshEntity(entity), "loose item published");
        return entity;
    }

    private static Villager human(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        Villager target = EntityType.VILLAGER.create(helper.getLevel());
        require(target != null, "villager entity created");
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(target)
                        && CharacterIdentitySystem.enroll(target, helper.getLevel(), ModCharacters.HUMAN_ID),
                "server entity enrolled with resolved human composition");
        require(target instanceof IStatusEffectTrait, "enrolled human supports status effects");

        return target;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
