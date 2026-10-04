package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

public final class BodyLooseThrowGameTests {
    private BodyLooseThrowGameTests() { }

    public static void throwsWholeStackAndDeniesStaleRequests(GameTestHelper helper) {
        Villager body = body(helper);
        Set<UUID> looseBefore = looseIds(helper, body);
        ItemStack original = new ItemStack(net.minecraft.world.item.Items.DIAMOND, 7);
        original.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("whole throw"));
        LiveHands stocked = stock(body, original, "throw-token");
        require(BodyHandItemTransferGameTestAccess.throwOwned(body, "left", "throw-token", stocked.revision() - 1)
                == BodyLooseThrow.Result.DENIED, "stale throw denied");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == stocked
                && looseIds(helper, body).equals(looseBefore),
                "stale denial leaves owner and world unchanged");
        require(BodyHandItemTransferGameTestAccess.throwOwned(body, "left", "throw-token", stocked.revision())
                == BodyLooseThrow.Result.SUCCESS, "fresh whole-stack throw succeeds");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var loose = newLooseAfter(helper, body, looseBefore);
        require(after.revision() == stocked.revision() + 1 && after.stackCopy("left").isEmpty()
                && loose.size() == 1 && loose.getFirst().getItem().getCount() == 7
                && loose.getFirst().getItem().getHoverName().getString().equals("whole throw"),
                "entire component-bearing stack transferred once");
        helper.succeed();
    }

    public static void armedGrenadeComponentSurvivesLooseThrow(GameTestHelper helper) {
        Villager body = body(helper);
        Set<UUID> looseBefore = looseIds(helper, body);
        ItemStack grenade = new ItemStack(ModItems.GRENADE.get());
        GrenadeFuseData fuse = new GrenadeFuseData(GrenadeFuse.Phase.ARMED, helper.getLevel().getGameTime() + 200, true, false);
        grenade.set(ModDataComponents.GRENADE_FUSE.get(), fuse);
        LiveHands stocked = stock(body, grenade, "armed-grenade");
        require(BodyHandItemTransferGameTestAccess.throwOwned(body, "left", "armed-grenade", stocked.revision())
                == BodyLooseThrow.Result.SUCCESS, "armed grenade throw succeeds");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var loose = newLooseAfter(helper, body, looseBefore);
        require(after.revision() == stocked.revision() + 1 && after.stackCopy("left").isEmpty(),
                "successful throw clears hand and advances revision once");
        require(loose.size() == 1 && ItemStack.isSameItemSameComponents(loose.getFirst().getItem(), grenade)
                        && loose.getFirst().getItem().getCount() == grenade.getCount()
                        && fuse.equals(loose.getFirst().getItem().get(ModDataComponents.GRENADE_FUSE.get())),
                "whole grenade stack, armed phase, and deadline preserved on loose grenade");
        helper.succeed();
    }

    public static void jugThrowFitsUnderTwoBlockCeiling(GameTestHelper helper) {
        Villager body = body(helper);
        Set<UUID> looseBefore = looseIds(helper, body);
        body.setYRot(-90);
        body.setXRot(0);
        BlockPos feet = body.blockPosition();
        int ceilingY = feet.getY() + 2;
        // Cover the initial launch point and its forward path, not just the body's head.
        for (int x = feet.getX() - 1; x <= feet.getX() + 4; x++) {
            for (int z = feet.getZ() - 1; z <= feet.getZ() + 1; z++) {
                helper.getLevel().setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        ItemStack original = new ItemStack(ModItems.JUG.get());
        original.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("whole jug"));
        LiveHands stocked = stock(body, original, "ceiling-jug");
        require(BodyHandItemTransferGameTestAccess.throwOwned(body, "left", "ceiling-jug", stocked.revision())
                == BodyLooseThrow.Result.SUCCESS, "jug throw succeeds below ceiling");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var loose = newLooseAfter(helper, body, looseBefore);
        require(after.revision() == stocked.revision() + 1 && after.stackCopy("left").isEmpty(),
                "successful throw clears hand and advances revision once");
        require(loose.size() == 1 && ItemStack.isSameItemSameComponents(loose.getFirst().getItem(), original)
                        && loose.getFirst().getItem().getCount() == original.getCount(),
                "exactly one unchanged whole jug is in the world");
        require(loose.getFirst().getBoundingBox().maxY <= ceilingY,
                "entire jug collision box remains below ceiling: " + loose.getFirst().getBoundingBox());
        require(loose.getFirst().getDeltaMovement().dot(body.getLookAngle()) > 0,
                "jug has nonzero forward launch velocity");
        helper.succeed();
    }

    private static Villager body(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(LiveHands.initialize(body).isPresent(), "eligible body initialized");
        return body;
    }

    private static LiveHands stock(Villager body, ItemStack stack, String token) {
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands next = before.putWhole(before.revision(), "left", new ItemToken(token), stack).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), next);
        return next;
    }

    private static Set<UUID> looseIds(GameTestHelper helper, Villager body) {
        return helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, body.getBoundingBox().inflate(8))
                .stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
    }

    private static java.util.List<LooseItemEntity> newLooseAfter(GameTestHelper helper, Villager body, Set<UUID> before) {
        return helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, body.getBoundingBox().inflate(8))
                .stream().filter(entity -> !before.contains(entity.getUUID())).toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
