package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.entities.LooseChunkBounds;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class LoosePullGameTests {
    private LoosePullGameTests() { }

    public static void loosePullReservesAndReleasesWithoutMovingOwnership(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int x = 0; x <= 5; x++)
            for (int z = 0; z <= 4; z++)
                level.setBlock(new BlockPos(origin.getX() + x, origin.getY() - 1, origin.getZ() + z),
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true);
        body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body), "body spawned");
        require(BodySystem.reconcile(body), "body system initialized");
        require(LiveHands.initialize(body).isPresent(), "hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());

        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("pull fixture"));
        LooseItemEntity target = LooseItemEntity.create(level,
                new Vec3(origin.getX() + 2.5, origin.getY(), origin.getZ() + .5), stack);
        target.setNoPickUpDelay();
        require(level.addFreshEntity(target), "loose target spawned");
        target.tick(); // settle initial motion on the stone support before measuring pull behavior
        body.setXRot((float) Math.toDegrees(Math.atan2(body.getEyeY() - target.getBoundingBox().getCenter().y,
                Math.sqrt(Math.pow(body.getX() - target.getX(), 2) + Math.pow(body.getZ() - target.getZ(), 2)))));
        // The production tick intentionally requires its motion-sweep neighborhood to be resident.
        var motionBounds = target.getBoundingBox().expandTowards(target.getDeltaMovement()).inflate(1.0);
        var chunks = LooseChunkBounds.from(motionBounds);
        for (int cx = chunks.minX(); cx <= chunks.maxX(); cx++)
            for (int cz = chunks.minZ(); cz <= chunks.maxZ(); cz++)
                level.getChunk(cx, cz);
        var worldStack = target.getItem().copy();
        var targetId = target.getUUID();

        require(BodyPullService.toggleFixture(body, target.getUUID(), "left", initial.revision(), () -> true)
                == BodyPullService.Result.SUCCESS, "pull starts with trusted authority");
        LiveHands reserved = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(reserved.reservation("left").equals(java.util.Optional.of(target.getUUID())),
                "exact target reserved in hand");
        require(reserved.stackCopy("left").isEmpty() && ItemStack.matches(target.getItem(), worldStack)
                && level.getEntity(target.getUUID()) == target && !target.isRemoved(),
                "pull reservation does not move world ownership");

        // Displace the actor, not the item: this makes the live constraint produce an inward impulse.
        body.setPos(body.getX() - .25, body.getY(), body.getZ());
        BodyPullService.onPostServerTick(null);
        Vec3 inward = body.position().add(0, body.getBbHeight() * .5, 0)
                .subtract(target.position().add(0, target.getBbHeight() * .5, 0));
        require(target.getDeltaMovement().dot(inward) > 0,
                "post-server pull tick applies impulse toward displaced body");
        require(target.getUUID().equals(targetId) && level.getEntity(targetId) == target && !target.isRemoved()
                        && ItemStack.matches(target.getItem(), worldStack)
                        && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).reservation("left")
                        .equals(java.util.Optional.of(targetId)),
                "pull tick retains target identity, components, and reservation");

        Vec3 beforePhysics = target.position();
        target.tick();
        Vec3 movement = target.position().subtract(beforePhysics);
        Vec3 horizontalTowardBody = new Vec3(inward.x, 0, inward.z);
        require(movement.x * horizontalTowardBody.x + movement.z * horizontalTowardBody.z > 0,
                "loose entity physics moves target horizontally toward body");
        require(Math.sqrt(movement.x * movement.x + movement.z * movement.z) <= PullConstraint.MAX_IMPULSE,
                "one loose entity tick remains within bounded pull impulse");
        require(level.getEntity(targetId) == target && !target.isRemoved()
                        && ItemStack.matches(target.getItem(), worldStack),
                "physics tick does not transfer source ownership");

        require(BodyPullService.stopFixture(body) == BodyPullService.Result.SUCCESS, "pull stops");
        LiveHands released = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(released.reservation("left").isEmpty() && released.stackCopy("left").isEmpty()
                        && ItemStack.matches(target.getItem(), worldStack) && level.getEntity(target.getUUID()) == target
                        && !target.isRemoved(),
                "release clears reservation and retains exact world entity stack");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
