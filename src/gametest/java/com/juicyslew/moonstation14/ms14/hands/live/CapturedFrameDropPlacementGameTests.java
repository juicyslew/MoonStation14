package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Authenticated DROP placement against a captured local frame target after native frame motion. */
@PrefixGameTestTemplate(false)
public final class CapturedFrameDropPlacementGameTests {
    private CapturedFrameDropPlacementGameTests() { }

    @GameTestTags({"hands-captured-drop-placement"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 320)
    public static void capturedFloorDropUsesOriginalLocalPointAfterNativeYaw(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2620, 300, 940), "captured-floor-drop",
                2.5, 1, 2.5, (fixture, body) -> {
            var level = fixture.level();
            var owner = fixture.owner();
            var actor = owner.actor();
            var hands = LiveHands.initialize(body).orElseThrow(() ->
                    new GameTestAssertException("controlled human lacks resolved live hands"));
            var jug = new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
            var water = net.minecraft.resources.ResourceKey.create(
                    com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
            jug.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                    new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(java.util.Map.of(water, 10f)));
            String token = "captured-drop-" + UUID.randomUUID();
            var stocked = hands.putWhole(hands.revision(), "left", new ItemToken(token), jug);
            if (!stocked.succeeded() || !com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway
                    .commit(actor, owner.authority(), stocked.state()))
                throw new GameTestAssertException("could not stock authenticated fixture hand");

            var storage = new Vec3(fixture.cabin().origin().getX() + 3.5,
                    fixture.cabin().origin().getY() + 1.0, fixture.cabin().origin().getZ() + 1.5);
            BlockPos cell = BlockPos.containing(storage.x, storage.y - .001, storage.z);
            var captured = CapturedHandTarget.frame(storage, fixture.plot().engineId(), cell);
            var pose0 = fixture.plot().subLevel().logicalPose();
            var initialWorld = pose0.transformPosition(new org.joml.Vector3d(storage.x, storage.y, storage.z));
            Vec3 world0 = new Vec3(initialWorld.x, initialWorld.y, initialWorld.z);
            body.setXRot(45); // stale body look intentionally differs from the captured floor point

            MovingCabinAuthorityFixture.awaitNativeYaw(helper, fixture, body, () -> BoundedGameTestWait.await(
                    helper, 40, () -> "native yaw failed to move the captured target at least .05 blocks while actor remained reachable",
                    () -> {
                        var livePose = fixture.plot().subLevel().logicalPose();
                        var world = livePose.transformPosition(new org.joml.Vector3d(storage.x, storage.y, storage.z));
                        Vec3 point = new Vec3(world.x, world.y, world.z);
                        return body.isAlive() && body.onGround() && body.getEyePosition().distanceTo(point) <= 3.5
                                && point.distanceTo(world0) >= .05;
                    }, () -> commitMovingCapturedDrop(helper, fixture, body, captured, storage, world0, jug,
                            stocked.state().revision(), token)));
        });
    }

    private static void commitMovingCapturedDrop(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            net.minecraft.world.entity.LivingEntity body, CapturedHandTarget captured, Vec3 storage,
            Vec3 world0, ItemStack jug, long stockedRevision, String token) {
                var level = fixture.level();
                var owner = fixture.owner();
                var actor = owner.actor();
                var pose1 = fixture.plot().subLevel().logicalPose();
                var currentWorld = pose1.transformPosition(new org.joml.Vector3d(storage.x, storage.y, storage.z));
                Vec3 world1 = new Vec3(currentWorld.x, currentWorld.y, currentWorld.z);
                var actorPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
                if (actorPoint == null || !fixture.plot().engineId().equals(actorPoint.frameId())
                        || !body.onGround() || !body.isAlive())
                    throw new GameTestAssertException("moving controlled HUMAN lost live frame/floor premise: " + actorPoint);
                if (world1.distanceTo(world0) < .05)
                    throw new GameTestAssertException("frame target world point did not measurably move: " + world0 + " -> " + world1);
                if (body.getEyePosition().distanceTo(world1) > 3.5)
                    throw new GameTestAssertException("captured floor nomination is outside current eye reach: eye="
                            + body.getEyePosition() + " target=" + world1);

                var beforeIds = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                        body.getBoundingBox().inflate(12), e -> e.getItem().is(jug.getItem()))
                        .stream().map(e -> e.getUUID()).collect(java.util.stream.Collectors.toSet());
                var request = dropRequest(owner.epoch(), stockedRevision, token, captured);
                var result = BodyHandRequestService.apply(actor, request);
                if (!result.accepted() || result.reason() != BodyHandActionResult.Reason.OK
                        || result.hostTransition() == null || result.hostTransition().worldEntity() == null)
                    throw new GameTestAssertException("authenticated captured frame DROP rejected: " + result);
                var spawned = level.getEntity(result.hostTransition().worldEntity());
                if (!(spawned instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose))
                    throw new GameTestAssertException("captured DROP did not create loose jug: " + spawned);
                if (!ItemStack.isSameItemSameComponents(jug, loose.getItem()) || loose.getItem().getCount() != jug.getCount())
                    throw new GameTestAssertException("released jug stack/reagent components changed");
                var afterHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                if (afterHands == null || afterHands.revision() != stockedRevision + 1
                        || afterHands.token("left").isPresent() || afterHands.stackCopy("left").isPresent())
                    throw new GameTestAssertException("successful transfer did not conserve exact hand/token ownership");
                var anchor = LoadedFrameRayQuery.currentActorPoint(level, loose);
                var expectedStorage = storage.add(0, .001, 0);
                var projectedExpected = pose1.transformPosition(new org.joml.Vector3d(
                        expectedStorage.x, expectedStorage.y, expectedStorage.z));
                Vec3 expectedWorld = new Vec3(projectedExpected.x, projectedExpected.y, projectedExpected.z);
                if (anchor == null || !fixture.plot().engineId().equals(anchor.frameId())
                        || anchor.storagePoint().distanceTo(expectedStorage) > 1.0e-4
                        || loose.position().distanceTo(expectedWorld) > 1.0e-4
                        || loose.position().distanceTo(world0) < .05 || loose.getDeltaMovement().lengthSqr() != 0)
                    throw new GameTestAssertException("release missed captured local surface / rest state: anchor=" + anchor
                            + " expectedLocal=" + expectedStorage + " expectedWorld=" + expectedWorld
                            + " oldWorld=" + world0 + " newSurfaceWorld=" + world1
                            + " actual=" + loose.position() + " velocity=" + loose.getDeltaMovement());
                var duplicates = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                        body.getBoundingBox().inflate(12), e -> e.getItem().is(jug.getItem()));
                if (duplicates.size() != beforeIds.size() + 1 || duplicates.stream()
                        .filter(e -> !beforeIds.contains(e.getUUID())).count() != 1)
                    throw new GameTestAssertException("drop created duplicate/orphan cargo owners: " + duplicates.size());
                helper.succeed();
    }

    @GameTestTags({"hands-captured-drop-placement"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 260)
    public static void invalidFrameUuidDropRetainsExactHandOwner(GameTestHelper helper) {
        runDenied(helper, new BlockPos(2720, 300, 940), "captured-invalid-frame", Denial.INVALID_FRAME);
    }

    @GameTestTags({"hands-captured-drop-placement"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 260)
    public static void removedCapturedFrameDropRetainsExactHandOwner(GameTestHelper helper) {
        runDenied(helper, new BlockPos(2770, 300, 940), "captured-removed-frame", Denial.REMOVED_FRAME);
    }

    @GameTestTags({"hands-captured-drop-placement"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 260)
    public static void deletedCapturedSurfaceDropRetainsExactHandOwner(GameTestHelper helper) {
        runDenied(helper, new BlockPos(2820, 300, 940), "captured-deleted-surface", Denial.DELETED_SURFACE);
    }

    @GameTestTags({"hands-captured-drop-placement"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 260)
    public static void blockedCapturedFloorDropRetainsExactHandOwner(GameTestHelper helper) {
        runDenied(helper, new BlockPos(2920, 300, 940), "captured-blocked-floor", Denial.BLOCKED);
    }

    @GameTestTags({"hands-captured-drop-placement"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 260)
    public static void unloadedWorldDropNominationDoesNotLoadTargetChunk(GameTestHelper helper) {
        runDenied(helper, new BlockPos(3020, 300, 940), "captured-unloaded-world", Denial.UNLOADED_WORLD);
    }

    private enum Denial { INVALID_FRAME, REMOVED_FRAME, DELETED_SURFACE, BLOCKED, UNLOADED_WORLD }

    private static void runDenied(GameTestHelper helper, BlockPos origin, String name, Denial denial) {
        MovingCabinAuthorityFixture.start(helper, origin, name, 2.5, 1, 2.5, (fixture, body) -> {
            var level = fixture.level();
            var owner = fixture.owner();
            var actor = owner.actor();
            var hands = LiveHands.initialize(body).orElseThrow(() ->
                    new GameTestAssertException("controlled human lacks resolved live hands"));
            var jug = new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
            var water = net.minecraft.resources.ResourceKey.create(
                    com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
            jug.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                    new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(java.util.Map.of(water, 10f)));
            String token = "captured-denial-" + UUID.randomUUID();
            var stocked = hands.putWhole(hands.revision(), "left", new ItemToken(token), jug);
            if (!stocked.succeeded() || !com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway
                    .commit(actor, owner.authority(), stocked.state()))
                throw new GameTestAssertException("could not stock authenticated denial hand");
            var storage = new Vec3(fixture.cabin().origin().getX() + 3.5,
                    fixture.cabin().origin().getY() + 1.0, fixture.cabin().origin().getZ() + 1.5);
            BlockPos cell = BlockPos.containing(storage.x, storage.y - .001, storage.z);
            CapturedHandTarget target = CapturedHandTarget.frame(storage, fixture.plot().engineId(), cell);
            BlockPos expectedUnloaded = null;
            switch (denial) {
                case INVALID_FRAME -> target = CapturedHandTarget.frame(storage, UUID.randomUUID(), cell);
                case REMOVED_FRAME -> {
                    UUID frame = fixture.plot().engineId();
                    fixture.plot().close();
                    if (dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(frame) != null
                            || LoadedFrameRayQuery.resolveCapturedTarget(level, target) != null)
                        throw new GameTestAssertException("closed native frame still resolves captured surface: " + frame);
                    if (!body.isAlive() || fixture.owner().authority().body() != body)
                        throw new GameTestAssertException("frame removal invalidated the authenticated test actor before request");
                }
                case DELETED_SURFACE -> fixture.plot().setStorageBlock(cell,
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                case BLOCKED -> fixture.plot().setStorageBlock(fixture.cabin().origin().offset(3, 1, 2),
                        net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());
                case UNLOADED_WORLD -> {
                    expectedUnloaded = findUnloadedWorldCell(level, origin);
                    target = CapturedHandTarget.world(Vec3.atCenterOf(expectedUnloaded));
                }
            }
            var before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var nearbyBefore = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                    body.getBoundingBox().inflate(12), entity -> entity.getItem().is(jug.getItem())).stream()
                    .map(entity -> entity.getUUID()).collect(java.util.stream.Collectors.toSet());
            var result = BodyHandRequestService.apply(actor,
                    dropRequest(owner.epoch(), stocked.state().revision(), token, target));
            var after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (result.accepted() || result.reason() == BodyHandActionResult.Reason.OK
                    || after == null || before == null || after.revision() != before.revision()
                    || !after.token("left").filter(token::equals).isPresent()
                    || after.stackCopy("left").filter(stack -> ItemStack.isSameItemSameComponents(jug, stack)
                            && stack.getCount() == jug.getCount()).isEmpty())
                throw new GameTestAssertException("denied nomination changed hand ownership: reason=" + result
                        + " before=" + before + " after=" + after);
            var nearbyAfter = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                    body.getBoundingBox().inflate(12), entity -> entity.getItem().is(jug.getItem()));
            if (nearbyAfter.size() != nearbyBefore.size() || nearbyAfter.stream()
                    .anyMatch(entity -> !nearbyBefore.contains(entity.getUUID())))
                throw new GameTestAssertException("denied nomination staged/spawned loose cargo: " + nearbyAfter);
            if (expectedUnloaded != null && level.getChunkSource().getChunkNow(
                    expectedUnloaded.getX() >> 4, expectedUnloaded.getZ() >> 4) != null)
                throw new GameTestAssertException("denied world nomination force-loaded target chunk: " + expectedUnloaded);
            helper.succeed();
        });
    }

    /** Pick an already-unloaded, in-border world cell far beyond ordinary fixture/view neighborhoods. */
    private static BlockPos findUnloadedWorldCell(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        for (int i = 0; i < 32; i++) {
            BlockPos candidate = new BlockPos(origin.getX() + 1_000_000 + i * 4096,
                    origin.getY() + 1, origin.getZ() + 1_000_000);
            if (!level.getWorldBorder().isWithinBounds(candidate)
                    || level.isOutsideBuildHeight(candidate)) continue;
            if (level.getChunkSource().getChunkNow(candidate.getX() >> 4, candidate.getZ() >> 4) == null)
                return candidate;
        }
        throw new GameTestAssertException("could not find bounded in-border unloaded world target near " + origin);
    }

    private static BodyHandActionRequest dropRequest(long epoch, long revision, String token, CapturedHandTarget target) {
        return new BodyHandActionRequest(BodyHandActionRequest.Action.DROP, "left", epoch, 2, revision,
                null, token, null, -1, -1, -1, -1, -1, -1, null, null, null, null, target);
    }
}
