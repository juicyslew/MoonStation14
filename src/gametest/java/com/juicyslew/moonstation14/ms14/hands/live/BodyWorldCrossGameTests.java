package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class BodyWorldCrossGameTests {
    private BodyWorldCrossGameTests() { }
    public static void pureBothDirectionsWithOccupiedHands(GameTestHelper helper) {
        var body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        // Entity join enrollment binds the human prototype on the next tick.
        helper.runAfterDelay(1, () -> {
        var initial = LiveHands.initialize(body).orElseThrow();
        var bag = new ItemStack(ModItems.BAG.get());
        bag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
        var belt = new ItemStack(ModItems.BELT.get());
        belt.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
        var state = initial.putWhole(initial.revision(), "left", new ItemToken("worn"), bag).state()
                .equip(initial.revision() + 1, List.of("belt", "back"), "left", "back", "worn").orElseThrow();
        state = state.putWhole(state.revision(), "left", new ItemToken("held"), belt).state();
        state = state.putWhole(state.revision(), "right", new ItemToken("other"), new ItemStack(Items.STICK)).state();
        var worn = new LiveHands.GridHost(true, "back", "worn");
        var held = new LiveHands.GridHost(false, "left", "held");
        var zero = new GridGeometry.Cell(0, 0);
        var flat = GridGeometry.Rotation.DEG_0;
        var item = new ItemStack(Items.APPLE, 4);
        var wornAdded = state.addGridChild(state.revision(), List.of("belt", "back"), worn, "child", item, zero, flat).orElseThrow();
        require(wornAdded.activeHand().equals(state.activeHand()) && wornAdded.token("right").equals(state.token("right")), "hands untouched");
        require(wornAdded.addGridChild(wornAdded.revision(), List.of("belt", "back"), held, "child", item, zero, flat).isEmpty(), "duplicate token");
        var wornRemoved = wornAdded.removeGridChild(wornAdded.revision(), List.of("belt", "back"), worn, "child", zero, flat).orElseThrow();
        require(wornRemoved.removeGridChild(wornRemoved.revision(), List.of("belt", "back"), worn, "child", zero, flat).isEmpty(), "stale child");
        var heldAdded = wornRemoved.addGridChild(wornRemoved.revision(), List.of("belt", "back"), held, "child", item, zero, flat).orElseThrow();
        require(heldAdded.removeGridChild(heldAdded.revision(), List.of("belt", "back"), held, "child", zero, flat).isPresent(), "held removal");
        require(heldAdded.addGridChild(heldAdded.revision() - 1, List.of("belt", "back"), worn, "other-child", item, zero, flat).isEmpty(), "stale revision");
        require(heldAdded.removeGridChild(heldAdded.revision(), List.of("belt", "back"),
                new LiveHands.GridHost(false, "left", "wrong"), "child", zero, flat).isEmpty(), "changed host");
        require(heldAdded.addGridChild(heldAdded.revision(), List.of("belt", "back"), held, "overlap", item, zero, flat).isEmpty(), "full cell");
        var stack = heldAdded.stackCopy("left").orElseThrow();
        require(GridContents.read(stack).orElseThrow().entries().getFirst().stack().getCount() == 4, "child conserved");
        helper.succeed();
        });
    }
    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
