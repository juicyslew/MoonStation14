package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native request-path regression for throwing into nearby floor from a near-vertical aim. */
@PrefixGameTestTemplate(false)
@GameTestTags({"thrown-combat", "portable-throw", "thrown-close-ground"})
public final class NativeCloseGroundThrowGameTests {
    private NativeCloseGroundThrowGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void ordinaryStackThrowsDownwardToNearbyFloor(GameTestHelper helper) {
        exercise(helper, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 3), "close-floor-stone");
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void hypodartThrowsDownwardToNearbyFloor(GameTestHelper helper) {
        exercise(helper, new ItemStack(com.juicyslew.moonstation14.item.ModItems.HYPO_DART.get()), "close-floor-hypodart");
    }

    private static void exercise(GameTestHelper helper, ItemStack source, String label) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "NativeCloseGroundThrow");
        var bodyRef = new net.minecraft.world.entity.LivingEntity[1];
        var sourceRef = new LooseItemEntity[1];
        var outputRef = new LooseItemEntity[1];
        int[] setupCalls = {0};
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                Throwable failure = null;
                for (var entity : new net.minecraft.world.entity.Entity[]{outputRef[0], sourceRef[0], bodyRef[0]}) {
                    try { if (entity != null && !entity.isRemoved()) entity.discard(); }
                    catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
                }
                try { fixture.close(); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
                if (failure != null) { if (info.getError() != null) info.getError().addSuppressed(failure); else info.fail(failure); }
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(info); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(info); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestInfo next, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(info); }
        });
        RegisteredPlayerReadyDriver.awaitCharacterReady(helper, fixture, 40, human -> {
            if (++setupCalls[0] != 1) throw new GameTestAssertException(label + " ready setup callback repeated");
            var body = human.body();
            bodyRef[0] = body;
            if (body instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            body.setDeltaMovement(Vec3.ZERO);
            var origin = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
            for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++)
                level.setBlock(origin.offset(x - 3, -1, z - 3), Blocks.STONE.defaultBlockState(), 3);
            body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
            source.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(label));
            sourceRef[0] = LooseItemEntity.create(level, body.position().add(0, .3, 1), source.copy());
            sourceRef[0].setNoGravity(true);
            if (!level.addFreshEntity(sourceRef[0])) throw new GameTestAssertException("close-ground source spawn failed");
            var active = LifecycleCharacterSessionControl.activeCharacterBody(fixture.player()).orElseThrow();
            var initial = fixture.queryHands(active.epoch());
            String hand = initial.activeHand();
            var pickup = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),
                    new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                            com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.PICKUP_SELECTED,
                            hand, active.epoch(), 1, initial.revision(), sourceRef[0].getUUID(), null));
            if (pickup.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK)
                throw new GameTestAssertException(label + " pickup denied: " + pickup.reason());
            var heldHands = fixture.queryHands(active.epoch());
            var held = heldHands.hands().stream().filter(item -> item.id().equals(hand)).findFirst().orElseThrow();
            var heldStack = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                    .stackCopy(hand).orElseThrow();
            if (heldStack.getCount() != source.getCount() || !label.equals(heldStack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString()))
                throw new GameTestAssertException(label + " pickup changed source stack");
            body.setYRot(-90); body.setYHeadRot(-90); body.setXRot(89);
            var thrown = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),
                    com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.throwItem(
                            hand, active.epoch(), 2, heldHands.revision(), held.token(),
                            com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget.world(
                                    body.getEyePosition().add(body.getLookAngle().scale(4)))));
            if (thrown.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK || thrown.hostTransition() == null)
                throw new GameTestAssertException(label + " close-ground throw denied: " + thrown.reason());
            var output = level.getEntity(thrown.hostTransition().worldEntity());
            if (!(output instanceof LooseItemEntity loose)) throw new GameTestAssertException("throw output missing");
            outputRef[0] = loose;
            if (!loose.activeThrownCombat() || !loose.thrownFlightPresentation())
                throw new GameTestAssertException(label + " throw did not enter active flight");
            if (!loose.getUUID().equals(thrown.hostTransition().worldEntity()))
                throw new GameTestAssertException(label + " throw output UUID mismatch");
            if (!ItemStack.isSameItemSameComponents(heldStack, loose.getItem()) || loose.getItem().getCount() != heldStack.getCount())
                throw new GameTestAssertException(label + " output stack/components changed");
            var sourceId = sourceRef[0].getUUID();
            var outputId = loose.getUUID();
            double floorTop = origin.getY();
            int[] contactTick = {-1};
            Vec3[] contactPosition = {null};
            int[] elapsed = {0};
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeOnEachTick(helper, () -> {
                elapsed[0]++;
                if (contactTick[0] < 0 && level.getEntity(outputId) == loose && loose.onGround()
                        && Math.abs(loose.getBoundingBox().minY - floorTop) < 1.0e-4
                        && !loose.activeThrownCombat()) {
                    contactTick[0] = elapsed[0];
                    contactPosition[0] = loose.position();
                }
                if (elapsed[0] < 12) return;
                var matches = level.getEntitiesOfClass(LooseItemEntity.class, body.getBoundingBox().inflate(3),
                        item -> item.getItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                                && label.equals(item.getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString()));
                var remainingStack = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                        .stackCopy(hand);
                if (setupCalls[0] != 1 || contactTick[0] < 0 || level.getEntity(outputId) != loose || loose.isRemoved()
                        || loose.position().distanceToSqr(body.position()) > 2.5 * 2.5
                        || loose.activeThrownCombat() || loose.thrownFlightPresentation() || level.getEntity(sourceId) != null
                        || remainingStack.isPresent() || matches.size() != 1 || matches.getFirst() != loose)
                    throw new GameTestAssertException(label + " failed close-floor landing/conservation: pos=" + loose.position()
                            + " body=" + body.position() + " contactTick=" + contactTick[0] + " contactPos=" + contactPosition[0]
                            + " onGround=" + loose.onGround() + " active=" + loose.activeThrownCombat()
                            + " setupCalls=" + setupCalls[0]);
                helper.succeed();
            });
        });
    }
}
