package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModEquipmentItems;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.live.CrossPublicationFixture;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestTags({"equipment-drag-world", "hands", "storage"})
@PrefixGameTestTemplate(false)
public final class WorldEquipmentTransferGameTests {
    private WorldEquipmentTransferGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"equipment-drag-world", "hands", "storage"})
    public static void transfersWorldGridChildToEmptyEquipmentSlot(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        body.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            CrossPublicationFixture.initialize(body);
            LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var equipmentBefore = LiveHands.equipmentSnapshot(body).orElseThrow();
            require(equipmentBefore.persisted() && equipmentBefore.slots().size() == 15,
                    "resolved human equipment capability has all 15 slots");
            var head = equipmentBefore.slots().stream().filter(slot -> slot.id().equals("head")).findFirst().orElseThrow();
            require(head.stack().isEmpty() && head.token().isEmpty(), "resolved head slot starts empty");
            require(before.token("left").filter("belt"::equals).isPresent()
                    && before.token("right").filter("stick"::equals).isPresent(), "both hands are occupied");

            ItemStack worldStack = new ItemStack(ModItems.BAG.get());
            worldStack.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                    new GridContents.Entry("cap-child", new ItemStack(ModEquipmentItems.GREY_CAP.get()), 0, 0,
                            GridGeometry.Rotation.DEG_0))));
            // Match the elevated, unobstructed host geometry used by the passing cross-publication fixture.
            ItemEntity world = new ItemEntity(helper.getLevel(), body.getX() + 2.5, body.getY() + 1.5,
                    body.getZ() + .5, worldStack);
            world.setNoPickUpDelay();
            world.setTarget(null);
            require(helper.getLevel().addFreshEntity(world), "world grid bag spawned");

            ItemStack reference = world.getItem();
            ItemStack copy = reference.copy();
            BodyWorldCrossAction action = new BodyWorldCrossAction(
                    BodyWorldCrossAction.Direction.WORLD_TO_EQUIPMENT, 1, 1, world.getUUID(), UUID.randomUUID(), 0,
                    before.revision(), true, "head", null, "cap-child", 0, 0, 0, 0, 0, 0);
            var candidate = before.insertEquipment(before.revision(), equipmentBefore.slots().stream()
                    .map(LiveHands.EquipmentSlot::id).toList(), "head", "cap-child",
                    GridContents.read(copy).orElseThrow().entries().getFirst().stack());
            require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body), "body can act");
            require(before.compatible(body) && BodyHandStateService.representable(before, body),
                    "current body state is compatible and representable");
            require(candidate.isPresent(), "equipment insertion candidate exists; head=" + head
                    + ", worldGrid=" + GridContents.read(copy).isPresent());
            require(candidate.filter(value -> value.compatible(body) && BodyHandStateService.representable(value, body)).isPresent(),
                    "equipment insertion candidate is compatible and representable");
            require(world.getTarget() == null && !world.hasPickUpDelay()
                    && helper.getLevel().getEntity(world.getUUID()) == world, "world owner is safe and registered");
            require(body.level().getServer().isSameThread() && body.isAlive() && body.isAddedToLevel()
                    && helper.getLevel().getEntity(body.getUUID()) == body, "body owner is safe and registered");
            String reachDiagnostic = loadReachabilityRing(helper, body, world);
            boolean reachable = BodyItemTargetProbe.reachableStorageHost(helper.getLevel(), body, world,
                    BodyItemTargetProbe.MAX_REACH);
            BlockHitResult diagnosticRay = helper.getLevel().clip(new ClipContext(body.getEyePosition(),
                    closestPoint(body.getEyePosition(), world.getBoundingBox()), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, body));
            require(reachable, "world host reachable; " + reachDiagnostic + ", loadedClip=" + diagnosticRay.getType()
                    + "/" + (diagnosticRay.getType() == HitResult.Type.BLOCK ? diagnosticRay.getBlockPos() : "none")
                    + ", body=" + body.position() + ", eye=" + body.getEyePosition()
                    + ", world=" + world.position() + ", bounds=" + world.getBoundingBox()
                    + ", bodyAdded=" + body.isAddedToLevel() + ", worldAdded=" + world.isAddedToLevel()
                    + ", bodyIdentity=" + (helper.getLevel().getEntity(body.getUUID()) == body)
                    + ", worldIdentity=" + (helper.getLevel().getEntity(world.getUUID()) == world)
                    + ", target=" + world.getTarget() + ", pickupDelay=" + world.hasPickUpDelay());
            boolean[] worldPublished = {false};
            boolean[] bodyPublished = {false};
            String[] publicationFailure = {null};
            var outcome = LiveHandsCommitGateway.batch(() -> BodyWorldCrossTransfer.applyTrusted(body, world,
                    reference, copy, action, (target, next) -> {
                        bodyPublished[0] = true;
                        if (!LiveHandsCommitGateway.commitTrusted(target, before, next)) {
                            publicationFailure[0] = "body gateway rejected candidate";
                            throw new IllegalStateException(publicationFailure[0]);
                        }
                    }, (entity, stack) -> { worldPublished[0] = true; entity.setItem(stack); }, () -> true));

            require(outcome == BodyWorldCrossTransfer.Outcome.SUCCESS, "world item transfers to head equipment; outcome="
                    + outcome + ", worldPublished=" + worldPublished[0] + ", bodyPublished=" + bodyPublished[0]
                    + ", failure=" + publicationFailure[0] + ", bodyCurrent="
                    + (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before)
                    + ", worldSame=" + (world.getItem() == reference));
            LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(after.revision() == before.revision() + 1, "body revision advances once");
            require(after.token("left").equals(before.token("left")) && after.token("right").equals(before.token("right"))
                    && ItemStack.matches(after.stackCopy("left").orElseThrow(), before.stackCopy("left").orElseThrow())
                    && ItemStack.matches(after.stackCopy("right").orElseThrow(), before.stackCopy("right").orElseThrow()),
                    "both hand items remain unchanged");
            var equipmentAfter = LiveHands.equipmentSnapshot(body).orElseThrow();
            var wornCap = equipmentAfter.slots().stream().filter(slot -> slot.id().equals("head")).findFirst().orElseThrow();
            require(wornCap.token().filter("cap-child"::equals).isPresent()
                    && wornCap.stack().filter(stack -> stack.is(ModEquipmentItems.GREY_CAP.get())).isPresent(),
                    "native grey cap is worn in head slot");
            require(GridContents.read(world.getItem()).orElseThrow().entries().stream()
                    .noneMatch(entry -> entry.token().equals("cap-child")), "transferred child removed from world grid");

            LiveHands equipped = after;
            ItemStack emptyWorldReference = world.getItem();
            ItemStack emptyWorldCopy = emptyWorldReference.copy();
            BodyWorldCrossAction toWorld = new BodyWorldCrossAction(
                    BodyWorldCrossAction.Direction.EQUIPMENT_TO_WORLD, 1, 2, world.getUUID(), action.session(), 0,
                    equipped.revision(), true, "head", "cap-child", "cap-child", 0, 0, 0, 0, 0, 0);
            outcome = LiveHandsCommitGateway.batch(() -> BodyWorldCrossTransfer.applyTrusted(body, world,
                    emptyWorldReference, emptyWorldCopy, toWorld, (target, next) -> {
                        if (!LiveHandsCommitGateway.commitTrusted(target, equipped, next))
                            throw new IllegalStateException("body publication rejected");
                    }, ItemEntity::setItem, () -> true));
            require(outcome == BodyWorldCrossTransfer.Outcome.SUCCESS, "head equipment transfers back to world grid");
            LiveHands returned = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(returned.revision() == equipped.revision() + 1, "return advances body revision once");
            var returnedHead = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                    .filter(slot -> slot.id().equals("head")).findFirst().orElseThrow();
            require(returnedHead.stack().isEmpty() && returnedHead.token().isEmpty(), "head slot emptied on return");
            var returnedChild = GridContents.read(world.getItem()).orElseThrow().entries().stream()
                    .filter(entry -> entry.token().equals("cap-child")).findFirst().orElseThrow();
            require(returnedChild.stack().is(ModEquipmentItems.GREY_CAP.get()) && returnedChild.stack().getCount() == 1,
                    "same cap restored to world grid");
            require(returned.token("left").equals(before.token("left")) && returned.token("right").equals(before.token("right"))
                    && ItemStack.matches(returned.stackCopy("left").orElseThrow(), before.stackCopy("left").orElseThrow())
                    && ItemStack.matches(returned.stackCopy("right").orElseThrow(), before.stackCopy("right").orElseThrow()),
                    "both hands remain unchanged after round trip");

            ItemStack roundTripReference = world.getItem();
            LiveHands beforeReequip = returned;
            BodyWorldCrossAction reEquip = new BodyWorldCrossAction(
                    BodyWorldCrossAction.Direction.WORLD_TO_EQUIPMENT, 1, 3, world.getUUID(), action.session(), 0,
                    beforeReequip.revision(), true, "head", null, "cap-child", 0, 0, 0, 0, 0, 0);
            outcome = LiveHandsCommitGateway.batch(() -> BodyWorldCrossTransfer.applyTrusted(body, world,
                    roundTripReference, roundTripReference.copy(), reEquip, (target, next) -> {
                        if (!LiveHandsCommitGateway.commitTrusted(target, beforeReequip, next))
                            throw new IllegalStateException("body publication rejected");
                    }, ItemEntity::setItem, () -> true));
            require(outcome == BodyWorldCrossTransfer.Outcome.SUCCESS, "cap can be re-equipped for compensation check");
            LiveHands beforeCompensation = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            ItemStack compensationWorldReference = world.getItem();
            BodyWorldCrossAction failingReturn = new BodyWorldCrossAction(
                    BodyWorldCrossAction.Direction.EQUIPMENT_TO_WORLD, 1, 4, world.getUUID(), action.session(), 0,
                    beforeCompensation.revision(), true, "head", "cap-child", "cap-child", 0, 0, 0, 0, 0, 0);
            outcome = LiveHandsCommitGateway.batch(() -> BodyWorldCrossTransfer.applyTrusted(body, world,
                    compensationWorldReference, compensationWorldReference.copy(), failingReturn,
                    (target, next) -> {
                        if (!LiveHandsCommitGateway.commitTrusted(target,
                                target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()), next))
                            throw new IllegalStateException("body publication rejected");
                    }, (entity, stack) -> { throw new IllegalStateException("world publication rejected"); }, () -> true));
            require(outcome == BodyWorldCrossTransfer.Outcome.DENIED, "failed world publication denies equipment removal");
            LiveHands compensated = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var compensatedHead = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                    .filter(slot -> slot.id().equals("head")).findFirst().orElseThrow();
            require(compensated.revision() > beforeCompensation.revision()
                    && compensatedHead.token().filter("cap-child"::equals).isPresent()
                    && compensatedHead.stack().filter(stack -> stack.is(ModEquipmentItems.GREY_CAP.get())).isPresent(),
                    "compensation restores cap to head with monotonic revision");
            require(GridContents.read(world.getItem()).orElseThrow().entries().isEmpty(),
                    "failed world publication leaves world grid empty");
            require(compensated.token("left").equals(before.token("left")) && compensated.token("right").equals(before.token("right"))
                    && ItemStack.matches(compensated.stackCopy("left").orElseThrow(), before.stackCopy("left").orElseThrow())
                    && ItemStack.matches(compensated.stackCopy("right").orElseThrow(), before.stackCopy("right").orElseThrow()),
                    "compensation preserves both occupied hands");
            helper.succeed();
        });
    }

    /** Fixture-only setup: record unloaded guard coordinates, then load precisely the bounded probe ring. */
    private static String loadReachabilityRing(GameTestHelper helper, Villager body, ItemEntity target) {
        var level = helper.getLevel();
        Vec3 eye = body.getEyePosition();
        AABB box = target.getBoundingBox();
        Vec3 point = closestPoint(eye, box);
        BlockPos eyeBlock = BlockPos.containing(eye), pointBlock = BlockPos.containing(point);
        int minX = (Math.min(eyeBlock.getX(), pointBlock.getX()) >> 4) - 1;
        int maxX = (Math.max(eyeBlock.getX(), pointBlock.getX()) >> 4) + 1;
        int minZ = (Math.min(eyeBlock.getZ(), pointBlock.getZ()) >> 4) - 1;
        int maxZ = (Math.max(eyeBlock.getZ(), pointBlock.getZ()) >> 4) + 1;
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (level.getChunkSource().getChunkNow(x, z) == null) {
                missing.add(x + "," + z);
                level.getChunk(x, z);
            }
        }
        return "reachDiag eye=" + eye + " closestPoint=" + point + " finite=" + finite(eye) + "/" + finite(point)
                + " buildHeight=" + !level.isOutsideBuildHeight(eyeBlock) + "/"
                + !level.isOutsideBuildHeight(pointBlock) + " border=" + level.getWorldBorder().isWithinBounds(eye)
                + "/" + level.getWorldBorder().isWithinBounds(point) + " distanceSq=" + eye.distanceToSqr(point)
                + " missingChunksBeforeLoad=" + missing + " ringArea=" + ((long)(maxX-minX+1)*(maxZ-minZ+1));
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static Vec3 closestPoint(Vec3 eye, AABB box) {
        return new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
