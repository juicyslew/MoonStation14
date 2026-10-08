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

/** Connected-world checks for body pull motion and source ownership. */
public final class LoosePullMotionGameTests {
    private LoosePullMotionGameTests() { }

    public static void pullMovesHorizontallyWithoutChangingSource(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int x = 0; x <= 5; x++)
            for (int z = 0; z <= 4; z++)
                level.setBlock(new BlockPos(origin.getX() + x, origin.getY() - 1, origin.getZ() + z),
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        require(level.addFreshEntity(body), "body spawned");
        require(BodySystem.reconcile(body), "body initialized");
        require(LiveHands.initialize(body).isPresent(), "hands initialized");
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        LooseItemEntity target = LooseItemEntity.create(level,
                new Vec3(origin.getX() + 2.5, origin.getY(), origin.getZ() + .5), stack);
        target.setNoPickUpDelay();
        var bounds = target.getBoundingBox().inflate(1);
        var chunks = LooseChunkBounds.from(bounds);
        for (int x = chunks.minX(); x <= chunks.maxX(); x++)
            for (int z = chunks.minZ(); z <= chunks.maxZ(); z++) level.getChunk(x, z);
        require(level.addFreshEntity(target), "target spawned");
        target.tick(); // start from a settled, grounded state rather than free-fall
        body.setXRot((float) Math.toDegrees(Math.atan2(body.getEyeY() - target.getBoundingBox().getCenter().y,
                Math.sqrt(Math.pow(body.getX() - target.getX(), 2) + Math.pow(body.getZ() - target.getZ(), 2)))));
        ItemStack source = target.getItem().copy();
        Vec3 start = target.position();
        require(BodyPullService.toggleFixture(body, target.getUUID(), "left", hands.revision(), () -> true)
                == BodyPullService.Result.SUCCESS, "pull starts");
        // The resolved tether adds slack to the initial endpoint distance. Walk the
        // real puller beyond that allowance before expecting spring motion.
        for (int i = 0; i < 12; i++) {
            body.setPos(body.getX() - .2, body.getY(), body.getZ());
            BodyPullService.onPostServerTick(null);
            target.tick();
        }
        Vec3 delta = target.position().subtract(start);
        Vec3 toward = body.position().subtract(start);
        require(delta.x * toward.x + delta.z * toward.z > 0, "puller travel beyond tether slack moves item toward body horizontally");
        require(Math.abs(delta.y) < 1.0, "pull does not lift item by targeting body center");
        require(level.getEntity(target.getUUID()) == target && !target.isRemoved()
                        && ItemStack.matches(source, target.getItem()), "source UUID and stack remain unchanged");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
