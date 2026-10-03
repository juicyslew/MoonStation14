package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransferGameTestAccess;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BodyPickupTransitionGameTests {
    private BodyPickupTransitionGameTests() { }

    public static void committedPickupTransition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true);
        body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body), "body spawned");
        require(LiveHands.initialize(body).isPresent(), "hands initialized");
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemEntity item = new ItemEntity(level, pos.getX() + 3.5, pos.getY() + 1.5,
                pos.getZ() + .5, new ItemStack(Items.DIAMOND));
        require(level.addFreshEntity(item), "pickup item spawned");
        require(BodyHandItemTransferGameTestAccess.pickupOwned(body, item.getUUID(), "left", before.revision())
                == BodyHandItemTransfer.Result.SUCCESS, "real pickup committed");
        LiveHands committed = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(committed != null && committed.compatible(body), "committed hands compatible with real body");
        var transition = BodyHandRequestService.pickupTransition(committed, item.getUUID(), "left", true, body);
        require(transition.isPresent() && transition.orElseThrow().worldEntity().equals(item.getUUID())
                && transition.orElseThrow().hostToken().equals(committed.token("left").orElseThrow()),
                "descriptor uses actual committed token and selected source UUID");
        require(BodyHandRequestService.pickupTransition(before, item.getUUID(), "left", true, body).isEmpty(),
                "pre-attachment state cannot describe committed pickup");
        body.discard();
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
