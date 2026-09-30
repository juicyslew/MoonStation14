package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyPouchTransferGameTests {
    private BodyPouchTransferGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void insertExtractOnePublicationAndRejectStale(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands empty = LiveHands.initialize(body).orElseThrow();
            LiveHands pouch = empty.putWhole(0, "left", new ItemToken("pouch"),
                    new ItemStack(ModItems.POUCH.get())).state();
            ItemStack source = new ItemStack(Items.DIAMOND, 7);
            LiveHands occupied = pouch.putWhole(1, "right", new ItemToken("child"), source).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), occupied);
            source.setCount(1);
            require(BodyPouchTransfer.transferOwned(body, "left", "pouch", "right", "child", 2,
                    true, () -> true) == BodyPouchTransfer.Result.SUCCESS, "insert succeeds");
            LiveHands filled = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(filled.revision() == 3 && filled.stackCopy("right").isEmpty()
                    && filled.token("left").orElseThrow().equals("pouch")
                    && PouchContents.read(filled.stackCopy("left").orElseThrow()).orElseThrow()
                    .child().orElseThrow().stack().getCount() == 7, "single snapshot owns child");
            require(BodyPouchTransfer.transferOwned(body, "left", "pouch", "right", "child", 2,
                    false, () -> true) == BodyPouchTransfer.Result.DENIED, "stale revision rejects");
            require(BodyPouchTransfer.transferOwned(body, "left", "pouch", "right", "pouch", 3,
                    false, () -> true) == BodyPouchTransfer.Result.DENIED, "duplicate token rejects");
            require(BodyPouchTransfer.transferOwned(body, "left", "pouch", "right", "child", 3,
                    false, () -> false) == BodyPouchTransfer.Result.DENIED, "lost authority rejects");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == filled,
                    "rejections never publish");
            require(BodyPouchTransfer.transferOwned(body, "left", "pouch", "right", "child", 3,
                    false, () -> true) == BodyPouchTransfer.Result.SUCCESS, "extract succeeds");
            LiveHands extracted = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(extracted.revision() == 4 && extracted.token("right").orElseThrow().equals("child")
                    && extracted.stackCopy("right").orElseThrow().getCount() == 7
                    && PouchContents.read(extracted.stackCopy("left").orElseThrow()).orElseThrow().child().isEmpty()
                    && filled.stackCopy("right").isEmpty(), "one revision and original snapshot unchanged");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void malformedChildAndOccupiedDestinationReject(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands empty = LiveHands.initialize(body).orElseThrow();
            LiveHands pouch = empty.putWhole(0, "left", new ItemToken("p"),
                    new ItemStack(ModItems.POUCH.get())).state();
            LiveHands occupied = pouch.putWhole(1, "right", new ItemToken("c"),
                    new ItemStack(ModItems.POUCH.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), occupied);
            require(BodyPouchTransfer.transferOwned(body, "left", "p", "right", "c", 2,
                    true, () -> true) == BodyPouchTransfer.Result.DENIED, "nested pouch rejects");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == occupied,
                    "no attachment publication");
            ItemStack filled = PouchContents.put(new ItemStack(ModItems.POUCH.get()), "child",
                    new ItemStack(Items.APPLE)).orElseThrow();
            LiveHands withChild = empty.putWhole(0, "left", new ItemToken("p"), filled).state()
                    .putWhole(1, "right", new ItemToken("other"), new ItemStack(Items.DIAMOND)).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), withChild);
            require(BodyPouchTransfer.transferOwned(body, "left", "p", "right", "child", 2,
                    false, () -> true) == BodyPouchTransfer.Result.DENIED, "occupied destination rejects");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == withChild,
                    "no overwrite");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
