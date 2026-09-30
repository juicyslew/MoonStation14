package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class BodyPickupRouteGameTests {
    private BodyPickupRouteGameTests() { }

    public static void firstHitDenialsAndWorldToHandConservation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true);
        body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body), "body spawned");
        require(LiveHands.initialize(body).isPresent(), "hands initialized");
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemEntity target = item(level, origin, 3);
        ItemEntity nearer = item(level, origin, 2);
        require(denied(body, target, before), "nearer item must win");
        nearer.discard();
        body.setYRot(90);
        body.setYHeadRot(90);
        require(denied(body, target, before), "off-axis target denied");
        body.setYRot(-90);
        body.setYHeadRot(-90);
        BlockPos barrier = origin.offset(1, 1, 0);
        level.setBlock(barrier, Blocks.STONE.defaultBlockState(), 3);
        require(denied(body, target, before), "collision block denied");
        level.setBlock(barrier, Blocks.AIR.defaultBlockState(), 3);
        target.setPickUpDelay(30);
        require(denied(body, target, before), "pickup delay denied");
        target.setNoPickUpDelay();
        target.setTarget(java.util.UUID.randomUUID());
        require(denied(body, target, before), "reserved item denied");
        target.setTarget(null);
        target.setPos(target.getX(), target.getY(), target.getZ() + .26);
        require(target.getBoundingBox().clip(body.getEyePosition(),
                body.getEyePosition().add(body.getLookAngle().scale(5))).isEmpty(), "raw near miss");
        require(BodyHandItemTransfer.pickupOwned(body, target.getUUID(), "left", before.revision(), () -> false)
                == BodyHandItemTransfer.Result.DENIED, "invalid authority denied");
        require(BodyHandItemTransfer.pickupOwned(body, target.getUUID(), "left", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "real world source committed to body hand");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(target.isRemoved() && level.getEntity(target.getUUID()) == null
                && after != null && after.revision() == before.revision() + 1
                && after.stackCopy("left").orElseThrow().getCount() == 3,
                "exact world source consumed and hand owns whole stack");
        require(BodyHandItemTransfer.pickupOwned(body, target.getUUID(), "right", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "replay denied");
        CompoundTag saved = body.saveWithoutId(new CompoundTag());
        Villager loaded = EntityType.VILLAGER.create(level);
        require(loaded != null, "load fixture created");
        loaded.load(saved);
        LiveHands persisted = loaded.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(persisted != null && persisted.revision() == after.revision()
                && persisted.stackCopy("left").orElseThrow().getCount() == 3,
                "save/load retains the body owner");
        body.discard();
        helper.succeed();
    }

    private static boolean denied(Villager body, ItemEntity target, LiveHands before) {
        return BodyHandItemTransfer.pickupOwned(body, target.getUUID(), "left", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED
                && !target.isRemoved() && target.getItem().getCount() == 3
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before;
    }

    private static ItemEntity item(ServerLevel level, BlockPos origin, int offset) {
        ItemEntity item = new ItemEntity(level, origin.getX() + .5 + offset, origin.getY() + 1.5,
                origin.getZ() + .5, new ItemStack(Items.DIAMOND, 3));
        require(level.addFreshEntity(item), "item spawned");
        return item;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
