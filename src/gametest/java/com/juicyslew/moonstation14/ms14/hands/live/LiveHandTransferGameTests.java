package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
public final class LiveHandTransferGameTests {
    private LiveHandTransferGameTests() { }

    public static void transfersExactInactiveOccupantAndDenialsAreAtomic(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands empty = LiveHands.initialize(body).orElseThrow();
            ItemStack original = new ItemStack(Items.APPLE, 7);
            original.set(DataComponents.CUSTOM_NAME, Component.literal("preserved hand item"));
            LiveHands occupied = empty.putWhole(0, "right", new ItemToken("held-token"), original).state();
            LiveHands.WholeResult result = occupied.transferWhole(1, "right", "left", new ItemToken("held-token"));
            require(result.succeeded(), "inactive source hand transfers");
            LiveHands moved = result.state();
            require(moved.revision() == occupied.revision() + 1, "revision increments once");
            require(moved.token("right").isEmpty() && moved.token("left").filter("held-token"::equals).isPresent(), "token moves intact");
            require(ItemStack.matches(original, moved.stackCopy("left").orElseThrow()), "stack count and components preserved");

            denyUnchanged(moved, moved.revision() - 1, "right", "left", "held-token", "stale denied");
            LiveHands full = moved.putWhole(moved.revision(), "right", new ItemToken("other-token"), new ItemStack(Items.STICK)).state();
            denyUnchanged(full, full.revision(), "left", "right", "held-token", "occupied destination denied");
            denyUnchanged(moved, moved.revision(), "left", "left", "held-token", "same hand denied");
            denyUnchanged(moved, moved.revision(), "left", "missing", "held-token", "unknown hand denied");
            helper.succeed();
        });
    }

    private static void denyUnchanged(LiveHands state, long revision, String source, String destination,
                                      String token, String message) {
        var result = state.transferWhole(revision, source, destination, new ItemToken(token));
        require(!result.succeeded() && result.state() == state, message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
