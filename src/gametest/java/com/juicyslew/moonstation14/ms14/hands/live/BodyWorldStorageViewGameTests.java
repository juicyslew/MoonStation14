package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** Spawned-body seam coverage; no authenticated ServerPlayer lease can be created by this fixture. */
public final class BodyWorldStorageViewGameTests {
    private BodyWorldStorageViewGameTests() { }

    public static void aimedWorldStorageReadOnly(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true); body.setYRot(-90); body.setYHeadRot(-90); body.setXRot(0);
        require(LiveHands.initialize(body).isPresent(), "body initialized");
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack filled = new ItemStack(ModItems.BAG.get());
        filled.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                new GridContents.Entry("world-child", new ItemStack(Items.IRON_INGOT, 17),
                        0, 0, GridGeometry.Rotation.DEG_0))));
        ItemEntity item = spawn(helper, body, filled, 2);
        var view = BodyWorldStorageView.inspectOwned(body, item.getUUID(), 42, hands.revision(), () -> true).orElseThrow();
        require(view.sessionEpoch() == 42 && view.handRevision() == hands.revision()
                && view.worldEntityId().equals(item.getUUID()) && view.kind().equals("bag")
                && view.entries().size() == 1 && view.entries().getFirst().token().equals("world-child")
                && view.entries().getFirst().stack().getCount() == 17, "filled bag summary");
        view.entries().getFirst().stack().shrink(17);
        require(view.entries().getFirst().stack().getCount() == 17
                && ItemStack.matches(filled, item.getItem()) && !item.isRemoved()
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands,
                "inspection detached and did not mutate either owner");
        require(BodyWorldStorageView.inspectOwned(body, item.getUUID(), 42, hands.revision(), () -> false).isEmpty(),
                "invalid lease denied");
        item.discard();
        require(BodyWorldStorageView.inspectOwned(body, item.getUUID(), 42, hands.revision(), () -> true).isEmpty(),
                "removed owner denied");

        ItemEntity empty = spawn(helper, body, new ItemStack(ModItems.BAG.get()), 2);
        require(BodyWorldStorageView.inspectOwned(body, empty.getUUID(), 42, hands.revision(), () -> true)
                .orElseThrow().entries().isEmpty(), "empty aimed bag accepted");
        empty.discard();
        ItemEntity far = spawn(helper, body, filled, 7);
        require(BodyWorldStorageView.inspectOwned(body, far.getUUID(), 42, hands.revision(), () -> true).isEmpty(),
                "far bag denied");
        far.discard();
        ItemEntity blocked = spawn(helper, body, filled, 2);
        BlockPos wall = helper.absolutePos(new BlockPos(3, 2, 2));
        helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        require(BodyWorldStorageView.inspectOwned(body, blocked.getUUID(), 42, hands.revision(), () -> true).isEmpty(),
                "blocked bag denied");
        helper.succeed();
    }

    private static ItemEntity spawn(GameTestHelper helper, Villager body, ItemStack stack, int distance) {
        ItemEntity item = new ItemEntity(helper.getLevel(), body.getX() + distance, body.getY() + 1.5,
                body.getZ(), stack.copy());
        require(helper.getLevel().addFreshEntity(item), "spawned world owner");
        return item;
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
