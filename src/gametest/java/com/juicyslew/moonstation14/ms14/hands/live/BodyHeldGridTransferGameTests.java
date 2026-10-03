package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyHeldGridTransferGameTests {
    private BodyHeldGridTransferGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heldGridTransferPublishesOneRevisionAndRejectsStale(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            LiveHands bag = initial.putWhole(0, "left", new ItemToken("bag"), new ItemStack(ModItems.BAG.get())).state();
            LiveHands occupied = bag.putWhole(1, "right", new ItemToken("tool"), new ItemStack(ModItems.PICKAXE.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), occupied);
            var origin = new GridGeometry.Cell(0, 0);
            require(BodyHeldGridTransfer.transferOwned(body, "left", "bag", "right", "tool", 2,
                    origin, GridGeometry.Rotation.DEG_0, null, null, true, () -> true)
                    == BodyHeldGridTransfer.Result.SUCCESS, "store succeeds");
            LiveHands stored = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(stored.revision() == 3 && stored.token("right").isEmpty()
                    && GridContents.read(stored.stackCopy("left").orElseThrow()).orElseThrow().entries().size() == 1,
                    "single published snapshot contains child");
            require(BodyHeldGridTransfer.transferOwned(body, "left", "bag", "tool", "tool", 2,
                    origin, null, new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_90, false, () -> true)
                    == BodyHeldGridTransfer.Result.DENIED, "stale or mismatched host denied");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == stored, "denial did not publish");
            require(BodyHeldGridTransfer.transferOwned(body, "left", "bag", null, "tool", 3,
                    origin, GridGeometry.Rotation.DEG_0, new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_90,
                    false, () -> true) == BodyHeldGridTransfer.Result.SUCCESS, "move succeeds");
            LiveHands moved = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(moved.revision() == 4, "move increments exactly once");
            require(BodyHeldGridTransfer.transferOwned(body, "left", "bag", "right", "tool", 4,
                    new GridGeometry.Cell(1, 0), null, null, null, false, () -> true)
                    == BodyHeldGridTransfer.Result.SUCCESS, "take succeeds");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).revision() == 5,
                    "take publishes one revision");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heldGridTransferRejectsNonStorageTargetWithoutPublishing(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            LiveHands bag = initial.putWhole(0, "left", new ItemToken("bag"), new ItemStack(ModItems.BAG.get())).state();
            LiveHands occupied = bag.putWhole(1, "right", new ItemToken("tool"), new ItemStack(ModItems.PICKAXE.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), occupied);
            var before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyHeldGridTransfer.transferOwned(body, "right", "tool", "left", "bag", occupied.revision(),
                    new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, null, null, true, () -> true)
                    == BodyHeldGridTransfer.Result.DENIED, "non-storage target denied");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    && before.revision() == occupied.revision() && before.token("left").filter("bag"::equals).isPresent()
                    && before.token("right").filter("tool"::equals).isPresent(), "denial preserves snapshot, tokens, and revision");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heldGridTransferRejectsStorageHostAsItsOwnSourceWithoutPublishing(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            LiveHands bag = initial.putWhole(0, "left", new ItemToken("bag"), new ItemStack(ModItems.BAG.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), bag);
            require(BodyHeldGridTransfer.transferOwned(body, "left", "bag", "left", "bag", bag.revision(),
                    new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, null, null, true, () -> true)
                    == BodyHeldGridTransfer.Result.DENIED, "host cannot be its own source");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == bag
                    && bag.revision() == 1 && bag.token("left").filter("bag"::equals).isPresent(), "self-target denial publishes nothing");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
