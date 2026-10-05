package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;

/** Real-world entity coverage for the orientation-independent storage reach check. */
public final class BodyWorldStorageReachGameTests {
    private BodyWorldStorageReachGameTests() { }

    public static void worldStorageReach(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        body.setYRot(180);
        body.setYHeadRot(180);
        ItemEntity close = spawn(helper, body, 2);
        require(reachable(helper, body, close), "close storage host reachable when body looks away");

        BlockPos wall = helper.absolutePos(new BlockPos(3, 2, 2));
        helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        require(!reachable(helper, body, close), "wall blocks storage host");
        helper.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());

        ItemEntity far = spawn(helper, body, 7);
        require(!reachable(helper, body, far), "host beyond five blocks denied");
        far.discard();
        close.discard();
        require(!reachable(helper, body, close), "removed host denied");
        ItemEntity replacement = spawn(helper, body, 2);
        require(!replacement.getUUID().equals(close.getUUID()), "replacement has distinct UUID");
        require(!reachableByUuid(helper, body, close.getUUID()), "replaced UUID does not resolve to a host");
        helper.succeed();
    }

    private static ItemEntity spawn(GameTestHelper helper, Villager body, int distance) {
        ItemEntity item = new ItemEntity(helper.getLevel(), body.getX() + distance, body.getY() + 1.5,
                body.getZ(), new ItemStack(Blocks.CHEST));
        require(helper.getLevel().addFreshEntity(item), "spawned world item");
        return item;
    }

    private static boolean reachable(GameTestHelper helper, Villager body, ItemEntity item) {
        return BodyItemTargetProbe.reachableStorageHost((ServerLevel) helper.getLevel(), body, item, 5);
    }

    private static boolean reachableByUuid(GameTestHelper helper, Villager body, java.util.UUID uuid) {
        return BodyItemTargetProbe.reachableStorageHost((ServerLevel) helper.getLevel(), body,
                (ItemEntity) helper.getLevel().getEntity(uuid), 5);
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
