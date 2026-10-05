package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class BodyWorldGridTransferGameTests {
    private BodyWorldGridTransferGameTests() { }
    private static final GridGeometry.Cell ZERO = new GridGeometry.Cell(0, 0);
    private static final GridGeometry.Rotation FLAT = GridGeometry.Rotation.DEG_0;

    public static void storeRotateTakeAndDenyStaleRequests(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        require(LiveHands.initialize(body).isPresent(), "initialized spawned body");
        LiveHands empty = hands(body);
        ItemStack ingot = new ItemStack(Items.IRON_INGOT, 50);
        ingot.set(DataComponents.CUSTOM_NAME, Component.literal("world-grid-marker"));
        LiveHands filled = empty.putWhole(empty.revision(), "left", new ItemToken("ingot"), ingot).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), filled);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemStack bag = new ItemStack(ModItems.BAG.get());
        bag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
        ItemEntity host = new ItemEntity(helper.getLevel(), pos.getX() + 2.5, pos.getY() + 1.5, pos.getZ() + .5, bag);
        require(helper.getLevel().addFreshEntity(host), "spawned world bag");
        host.setNoPickUpDelay();
        ItemStack source = host.getItem();
        require(BodyWorldGridTransfer.storeOwned(body, host, "left", "ingot", filled.revision(), source,
                source.copy(), ZERO, FLAT, () -> true) == BodyWorldGridTransfer.Result.SUCCESS, "store");
        LiveHands stored = hands(body);
        require(stored.revision() == filled.revision() + 1 && stored.token("left").isEmpty()
                && stored.activeHand().equals(filled.activeHand()), "hand cleared once without selection change");
        require(host.getItem().getCount() == 1 && host.getItem().has(ModDataComponents.GRID_CONTENTS.get()), "item owns grid");
        var entry = grid(host).entries().getFirst();
        require(entry.stack().getCount() == 50 && entry.stack().get(DataComponents.CUSTOM_NAME).getString().equals("world-grid-marker"), "components kept");
        ItemStack movedFrom = host.getItem();
        require(BodyWorldGridTransfer.moveOwned(body, host, "ingot", stored.revision(), movedFrom,
                movedFrom.copy(), ZERO, FLAT, new GridGeometry.Cell(1, 0), FLAT, () -> true)
                == BodyWorldGridTransfer.Result.SUCCESS, "move within world owner");
        require(hands(body) == stored && grid(host).entries().getFirst().x() == 1, "move did not mutate body");
        denyMove(body, host, stored, ZERO, FLAT, "old anchor");
        ItemStack moved = host.getItem();
        require(BodyWorldGridTransfer.takeOwned(body, host, "right", "ingot", stored.revision(), moved,
                moved.copy(), new GridGeometry.Cell(1, 0), FLAT, () -> true)
                == BodyWorldGridTransfer.Result.SUCCESS, "take");
        require(grid(host).entries().isEmpty() && hands(body).revision() == stored.revision() + 1
                && hands(body).stackCopy("right").orElseThrow().getCount() == 50, "take conserves child");
        LiveHands taken = hands(body);
        ItemStack afterTake = host.getItem();
        require(BodyWorldGridTransfer.storeOwned(body, host, "right", "ingot", stored.revision(), afterTake,
                afterTake.copy(), ZERO, FLAT, () -> true) == BodyWorldGridTransfer.Result.DENIED, "stale revision");
        require(hands(body) == taken && host.getItem() == afterTake, "stale request no writes");
        ItemStack pickaxeBag = afterTake.copy();
        pickaxeBag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                new GridContents.Entry("pickaxe", new ItemStack(ModItems.PICKAXE.get()), 0, 0, FLAT),
                new GridContents.Entry("unrelated", new ItemStack(Items.GOLD_INGOT, 7), 6, 3, FLAT))));
        host.setItem(pickaxeBag);
        ItemStack pickaxeSource = host.getItem();
        require(BodyWorldGridTransfer.moveOwned(body, host, "pickaxe", taken.revision(), pickaxeSource,
                pickaxeSource.copy(), ZERO, FLAT, new GridGeometry.Cell(2, 0), GridGeometry.Rotation.DEG_90, () -> true)
                == BodyWorldGridTransfer.Result.SUCCESS, "rotate T-shaped pickaxe");
        require(grid(host).entries().size() == 2 && grid(host).entries().get(0).token().equals("pickaxe")
                && grid(host).entries().get(1).token().equals("unrelated") && grid(host).entries().stream()
                .anyMatch(e -> e.token().equals("unrelated") && e.stack().getCount() == 7)
                && hands(body) == taken, "rotate preserves entry order, unrelated entry and body");
        ItemStack rotated = host.getItem();
        require(BodyWorldGridTransfer.takeOwned(body, host, "left", "pickaxe", taken.revision(), rotated,
                rotated.copy(), new GridGeometry.Cell(2, 0), GridGeometry.Rotation.DEG_90, () -> true)
                == BodyWorldGridTransfer.Result.SUCCESS, "take rotated pickaxe");
        require(hands(body).stackCopy("left").orElseThrow().is(ModItems.PICKAXE)
                && grid(host).entries().size() == 1 && grid(host).entries().getFirst().token().equals("unrelated"),
                "pickaxe taken without touching unrelated world child");
        helper.succeed();
    }

    public static void denialsLeaveBothOwnersUnchanged(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        LiveHands.initialize(body).orElseThrow();
        LiveHands state = hands(body);
        state = state.putWhole(state.revision(), "left", new ItemToken("ingot"), new ItemStack(Items.IRON_INGOT)).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), state);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemStack bag = new ItemStack(ModItems.BAG.get());
        bag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                new GridContents.Entry("blocker", new ItemStack(ModItems.PICKAXE.get()), 0, 0, FLAT))));
        ItemEntity host = new ItemEntity(helper.getLevel(), pos.getX() + 2.5, pos.getY() + 1.5, pos.getZ() + .5, bag);
        require(helper.getLevel().addFreshEntity(host), "spawned bag");
        host.setNoPickUpDelay();
        ItemStack reference = host.getItem();
        require(BodyWorldGridTransfer.storeOwned(body, host, "left", "ingot", state.revision(), reference,
                reference.copy(), ZERO, FLAT, () -> true) == BodyWorldGridTransfer.Result.DENIED, "overlap denied");
        require(BodyWorldGridTransfer.takeOwned(body, host, "left", "blocker", state.revision(), reference,
                reference.copy(), ZERO, FLAT, () -> true) == BodyWorldGridTransfer.Result.DENIED, "occupied hand denied");
        require(BodyWorldGridTransfer.moveOwned(body, host, "blocker", state.revision(), reference,
                reference.copy(), ZERO, FLAT, ZERO, FLAT, () -> true) == BodyWorldGridTransfer.Result.DENIED, "no-op denied");
        require(BodyWorldGridTransfer.moveOwned(body, host, "blocker", state.revision(), reference,
                reference.copy(), new GridGeometry.Cell(1, 0), FLAT, ZERO, FLAT, () -> true)
                == BodyWorldGridTransfer.Result.DENIED, "wrong old anchor denied");
        ItemStack changed = reference.copy();
        changed.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        host.setItem(changed);
        require(BodyWorldGridTransfer.moveOwned(body, host, "blocker", state.revision(), reference,
                reference.copy(), ZERO, FLAT, new GridGeometry.Cell(1, 0), FLAT, () -> true)
                == BodyWorldGridTransfer.Result.DENIED, "changed source denied");
        ItemStack current = host.getItem();
        host.setPos(pos.getX() + 8.5, pos.getY() + 1.5, pos.getZ() + .5);
        require(BodyWorldGridTransfer.moveOwned(body, host, "blocker", state.revision(), current,
                current.copy(), ZERO, FLAT, new GridGeometry.Cell(1, 0), FLAT, () -> true)
                == BodyWorldGridTransfer.Result.DENIED, "out of range denied");
        host.setPos(pos.getX() + 2.5, pos.getY() + 1.5, pos.getZ() + .5);
        helper.getLevel().setBlockAndUpdate(pos.offset(1, 1, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        require(BodyWorldGridTransfer.moveOwned(body, host, "blocker", state.revision(), current,
                current.copy(), ZERO, FLAT, new GridGeometry.Cell(1, 0), FLAT, () -> true)
                == BodyWorldGridTransfer.Result.DENIED, "obstructed denied");
        require(hands(body) == state && host.getItem() == current && grid(host).entries().getFirst().x() == 0,
                "all denials preserve both owners");
        helper.succeed();
    }

    public static void storeCompensationKeepsOriginalRevisionStale(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        LiveHands.initialize(body).orElseThrow();
        LiveHands before = hands(body).putWhole(hands(body).revision(), "left", new ItemToken("ingot"),
                new ItemStack(Items.IRON_INGOT, 3)).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), before);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemStack bag = new ItemStack(ModItems.BAG.get());
        bag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
        ItemEntity host = new ItemEntity(helper.getLevel(), pos.getX() + 2.5, pos.getY() + 1.5,
                pos.getZ() + .5, bag);
        require(helper.getLevel().addFreshEntity(host), "spawned world bag");
        host.setNoPickUpDelay();
        LiveHands.WholeResult cleared = before.takeWhole(before.revision(), "left", new ItemToken("ingot"));
        require(cleared.succeeded(), "staged clear");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), cleared.state());
        LiveHands staged = hands(body);
        ItemStack source = host.getItem();
        ItemStack saved = cleared.takenStack().orElseThrow();
        var result = BodyWorldGridTransfer.compensateBody(body, host, helper.getLevel(), before, staged,
                "left", "ingot", saved, source, source.copy(), host.position());
        LiveHands restored = hands(body);
        require(result == BodyWorldGridTransfer.Result.DENIED, "exact compensation denied");
        require(restored != before && restored.revision() > before.revision()
                && restored.token("left").filter("ingot"::equals).isPresent()
                && restored.stackCopy("left").orElseThrow().getCount() == 3, "compensation advances revision and restores source");
        require(BodyWorldGridTransfer.storeOwned(body, host, "left", "ingot", before.revision(), source,
                source.copy(), ZERO, FLAT, () -> true) == BodyWorldGridTransfer.Result.DENIED,
                "original stale revision remains denied");
        helper.succeed();
    }

    private static void denyMove(Villager body, ItemEntity host, LiveHands state, GridGeometry.Cell anchor,
                                 GridGeometry.Rotation rotation, String message) {
        ItemStack ref = host.getItem();
        require(BodyWorldGridTransfer.moveOwned(body, host, "ingot", state.revision(), ref,
                ref.copy(), anchor, rotation, new GridGeometry.Cell(2, 0), rotation, () -> true)
                == BodyWorldGridTransfer.Result.DENIED && host.getItem() == ref && hands(body) == state, message);
    }
    private static GridContents grid(ItemEntity item) { return GridContents.read(item.getItem()).orElseThrow(); }
    private static LiveHands hands(Villager body) { return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); }
    private static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
