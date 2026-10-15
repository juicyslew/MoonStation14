package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.PullableComponent;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the regular authenticated pull request against resolved mouse compositions in every medical state. */
@PrefixGameTestTemplate(false)
public final class PlayerPullsSupportedMouseGameTests {
    private PlayerPullsSupportedMouseGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 50)
    @GameTestTags({"pulling", "living-carry", "character-prototypes"})
    public static void normalHumanPullsAliveCriticalAndDeadMouseTargets(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "mousepuller");
        helper.runAtTickTime(2, () -> {
            try {
                var ready = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var actor = ready.body();
                BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
                for (int x = -2; x <= 5; x++) for (int z = -2; z <= 2; z++)
                    level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                actor.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
                actor.setYRot(-90); actor.setYHeadRot(-90); actor.setXRot(0);
                var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
                require(mouse != null, "canonical mouse constructed");
                mouse.setNoAi(true);
                // Establish a ray-visible client nomination, then move that exact entity before
                // the authenticated request is sent.
                mouse.setPos(origin.getX() + 2.5, actor.getEyeY() - .15, origin.getZ() + .5);
                require(level.addFreshEntity(mouse), "mouse target joined level");
                var originalMouseId = mouse.getUUID();
                com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse, level);
                var nominatedMouseId = mouse.getUUID();
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(level, actor,
                                nominatedMouseId, com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH) != null,
                        "client nomination established while mouse intersects look ray");
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.visibleCandidates(level, actor,
                                com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH).stream()
                                .noneMatch(candidate -> candidate.itemId().equals(nominatedMouseId)),
                        "automatic item selection excludes living foreground targets");
                mouse.setPos(origin.getX() + .5, actor.getEyeY() - .15, origin.getZ() + 1.5);
                require(BodyPullService.probeSelected(level, actor,
                                nominatedMouseId) == mouse, "same off-ray nomination remains eligible");
                mouse.setPos(origin.getX() + 7.5, actor.getEyeY() - .15, origin.getZ() + 1.5);
                require(BodyPullService.probeSelected(level, actor, mouse.getUUID()) == null,
                        "nominated mouse beyond pull reach denied");
                mouse.setPos(origin.getX() + .5, actor.getEyeY() - .15, origin.getZ() + 1.5);
                BlockPos blocker = new BlockPos(origin.getX(), origin.getY() + 1, origin.getZ() + 1);
                level.setBlock(blocker, Blocks.STONE.defaultBlockState(), 3);
                require(BodyPullService.probeSelected(level, actor, mouse.getUUID()) == null,
                        "blocker between actor and off-ray mouse denies pull");
                level.setBlock(blocker, Blocks.AIR.defaultBlockState(), 3);
                var unpullable = net.minecraft.world.entity.EntityType.COW.create(level);
                require(unpullable != null, "unpullable control entity constructed");
                unpullable.setPos(origin.getX() + .5, actor.getEyeY() - .15, origin.getZ() + 1.5);
                require(level.addFreshEntity(unpullable), "unpullable control entity joined");
                var unpullablePolicy = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem
                        .resolveForActor(unpullable);
                require(unpullablePolicy.flatMap(data -> data.component(PullableComponent.class)).isEmpty(),
                        "native cow control must resolve with no Pullable component before denial probe; resolved="
                                + unpullablePolicy.map(Object::toString).orElse("unbound"));
                require(BodyPullService.probeSelected(level, actor, unpullable.getUUID()) == null,
                        "in-reach, visible living nomination without resolved Pullable is denied");
                unpullable.discard();
                require(mouse.startRiding(actor, true), "mouse can enter contained control state");
                require(BodyPullService.probeSelected(level, actor, mouse.getUUID()) == null,
                        "contained mouse nomination denied");
                mouse.stopRiding();
                mouse.setPos(origin.getX() + .5, actor.getEyeY() - .15, origin.getZ() + 1.5);
                long sequence = 1;
                for (MobStateComponent.State state : MobStateComponent.State.values()) {
                    var hands = actor.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    if (state != MobStateComponent.State.ALIVE) {
                        mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get(),
                                new com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment(
                                        new com.juicyslew.moonstation14.ms14.mobs.MobStateData(state, state.ordinal())));
                        if (state == MobStateComponent.State.DEAD) mouse.setHealth(0);
                    }
                    var pull = BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                            BodyHandActionRequest.Action.TOGGLE_PULL, hands.activeHand(), ready.epoch(),
                            sequence++, hands.revision(), nominatedMouseId, null));
                    require(pull.reason() == BodyHandActionResult.Reason.OK,
                            "normal pull request admits " + state + " mouse: " + pull.reason());
                    require(BodyPullService.isPulling(actor) && level.getEntity(mouse.getUUID()) == mouse,
                            "pull retains exact world body identity in " + state);
                    var before = mouse.position();
                    actor.setPos(actor.getX() - .2, actor.getY(), actor.getZ());
                    BodyPullService.onPreServerTick(null);
                    require(BodyPullService.isPulling(actor), "tether follows same " + state + " body");
                    var stopHands = actor.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    var stop = BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                            BodyHandActionRequest.Action.STOP_PULL, stopHands.activeHand(), ready.epoch(),
                            sequence++, stopHands.revision(), null, null));
                    require(stop.reason() == BodyHandActionResult.Reason.OK && !BodyPullService.isPulling(actor),
                            "normal stop request releases " + state + " tether");
                    require(level.getEntity(originalMouseId) == mouse && !mouse.isRemoved()
                                    && mouse.getUUID().equals(originalMouseId),
                            "release conserves target entity and UUID in " + state + "; position=" + before);
                    mouse.setPos(origin.getX() + 2.5, actor.getEyeY() - .15, origin.getZ() + .5);
                }
                fixture.close(); helper.succeed();
            } catch (RuntimeException | Error failure) {
                fixture.close(); helper.fail("normal human-to-mouse pulling: " + failure);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 60)
    @GameTestTags({"pulling", "living-carry", "portable-throw"})
    public static void playerCanPullOriginalPortableBodyDuringFlight(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "throwpull");
        helper.runAtTickTime(2, () -> {
            try {
                var ready = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var actor = ready.body();
                BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
                for (int x = -2; x <= 6; x++) for (int z = -2; z <= 2; z++)
                    level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                actor.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
                actor.setYRot(-90); actor.setYHeadRot(-90); actor.setXRot(0);
                var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
                require(mouse != null, "portable mouse constructed");
                mouse.setNoAi(true);
                mouse.setPos(origin.getX() + 2.5, actor.getEyeY() - .15, origin.getZ() + .5);
                require(level.addFreshEntity(mouse), "portable mouse joined world");
                com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse, level);
                var hands = actor.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var pickup = BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.PICKUP_SELECTED, hands.activeHand(), ready.epoch(), 1,
                        hands.revision(), mouse.getUUID(), null));
                require(pickup.reason() == BodyHandActionResult.Reason.OK, "authenticated pickup creates portable key: " + pickup.reason());
                hands = actor.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var thrown = BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.THROW_ITEM, hands.activeHand(), ready.epoch(), 2,
                        hands.revision(), null, pickup.itemToken()));
                require(thrown.reason() == BodyHandActionResult.Reason.OK, "production authenticated throw releases portable body: " + thrown.reason());
                var originalId = mouse.getUUID();
                require(level.getEntity(originalId) == mouse && com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight
                        .tracked((net.minecraft.world.entity.Mob) mouse), "original mouse is the released flight body");
                hands = actor.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(hands.stackCopy(hands.activeHand()).isEmpty(), "thrown portable key reference cleared from active hand");
                mouse.setPos(origin.getX() + .5, actor.getEyeY() - .15, origin.getZ() + 1.5);
                var nomination = BodyItemTargetProbe.probeSelected(level, actor, originalId,
                        BodyItemTargetProbe.MAX_REACH);
                require(nomination != null && nomination.itemId().equals(originalId),
                        "flight body remains nominatable in reach off-ray");
                var pullHands = actor.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var pull = BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.TOGGLE_PULL, pullHands.activeHand(), ready.epoch(), 3,
                        pullHands.revision(), originalId, null));
                require(pull.reason() == BodyHandActionResult.Reason.OK && BodyPullService.isPulling(actor),
                        "authenticated pull accepts original released body during flight: " + pull.reason());
                require(level.getEntity(originalId) == mouse, "pull retains exact original body UUID");
                fixture.close(); helper.succeed();
            } catch (RuntimeException | Error failure) { fixture.close(); helper.fail("pull original portable flight body: " + failure); }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
