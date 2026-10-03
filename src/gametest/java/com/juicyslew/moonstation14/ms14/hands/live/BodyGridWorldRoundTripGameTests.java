package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** World-item conservation coverage for item-owned grid contents. */
public final class BodyGridWorldRoundTripGameTests {
    private BodyGridWorldRoundTripGameTests() { }

    public static void explicitGridBagWorldRoundTripConservesChildren(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands empty = hands(body);
        ItemStack bag = gridBag();
        ItemEntity source = spawn(helper, body, bag);
        require(BodyHandItemTransfer.pickupOwned(body, source.getUUID(), "left", empty.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "grid bag accepted from world");
        LiveHands held = hands(body);
        String owner = held.token("left").orElseThrow();
        require(source.isRemoved() && BodyHandStateService.representable(held, body), "single bag owner in body hand");
        assertGrid(held.stackCopy("left").orElseThrow());

        Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
        require(loaded != null, "saved body fixture created");
        loaded.load(body.saveWithoutId(new CompoundTag()));
        LiveHands saved = hands(loaded);
        require(saved.token("left").orElseThrow().equals(owner), "saved body retains owner token");
        assertGrid(saved.stackCopy("left").orElseThrow());

        require(BodyHandItemTransfer.dropOwned(body, "left", owner, held.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "grid bag dropped");
        LiveHands droppedState = hands(body);
        List<ItemEntity> drops = items(helper, body);
        require(drops.size() == 1, "one world bag after drop");
        ItemEntity dropped = drops.getFirst();
        assertGrid(dropped.getItem());
        ItemEntity roundTrip = EntityType.ITEM.create(helper.getLevel());
        require(roundTrip != null, "world item load fixture created");
        roundTrip.load(dropped.saveWithoutId(new CompoundTag()));
        assertGrid(roundTrip.getItem());

        dropped.setPos(source.getX(), source.getY(), source.getZ());
        dropped.setNoPickUpDelay();
        require(BodyHandItemTransfer.pickupOwned(body, dropped.getUUID(), "left", droppedState.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "dropped grid bag picked up");
        LiveHands again = hands(body);
        require(dropped.isRemoved() && items(helper, body).isEmpty(), "world owner consumed once");
        assertGrid(again.stackCopy("left").orElseThrow());
        require(BodyHandStateService.representable(again, body), "round-trip state remains representable");
        helper.succeed();
    }

    public static void hostileGridPickupAndLegacyBeltRemainConserved(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands empty = hands(body);
        ItemStack valid = gridBag();
        ItemStack dual = valid.copy();
        dual.set(ModDataComponents.POUCH_CONTENTS.get(), PouchContents.empty());
        denied(helper, body, empty, dual);

        ItemStack legacy = PouchContents.put(new ItemStack(ModItems.BELT.get()), "legacy-pickaxe",
                new ItemStack(ModItems.PICKAXE.get())).orElseThrow();
        require(GridContents.read(legacy).isEmpty(), "oversized legacy belt child stays outside new grid model");
        ItemEntity old = spawn(helper, body, legacy);
        require(BodyHandItemTransfer.pickupOwned(body, old.getUUID(), "left", empty.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "legacy filled belt remains hand-pickable");
        ItemStack held = hands(body).stackCopy("left").orElseThrow();
        require(held.has(ModDataComponents.POUCH_CONTENTS.get()) && !held.has(ModDataComponents.GRID_CONTENTS.get()),
                "pickup does not migrate legacy data");
        require(PouchContents.read(held).orElseThrow().child().orElseThrow().stack().is(ModItems.PICKAXE),
                "legacy pickaxe retained");
        helper.succeed();
    }

    private static ItemStack gridBag() {
        ItemStack tokened = new ItemStack(Items.IRON_INGOT, 50);
        tokened.set(DataComponents.CUSTOM_NAME, Component.literal("grid-roundtrip-marker"));
        GridContents contents = GridContents.of(List.of(
                new GridContents.Entry("component-stack", tokened, 0, 0, GridGeometry.Rotation.DEG_0),
                new GridContents.Entry("irregular-pickaxe", new ItemStack(ModItems.PICKAXE.get()), 2, 0, GridGeometry.Rotation.DEG_0)));
        ItemStack bag = new ItemStack(ModItems.BAG.get());
        bag.set(ModDataComponents.GRID_CONTENTS.get(), contents);
        return bag;
    }

    private static void assertGrid(ItemStack bag) {
        require(bag.is(ModItems.BAG) && bag.getCount() == 1, "one bag stack");
        GridContents content = GridContents.read(bag).orElseThrow();
        require(content.entries().size() == 2, "both grid children retained");
        GridContents.Entry stack = content.entries().stream().filter(e -> e.token().equals("component-stack")).findFirst().orElseThrow();
        require(stack.stack().getCount() == 50 && stack.stack().has(DataComponents.CUSTOM_NAME)
                && stack.stack().get(DataComponents.CUSTOM_NAME).getString().equals("grid-roundtrip-marker"), "50-count component stack retained");
        GridContents.Entry pickaxe = content.entries().stream().filter(e -> e.token().equals("irregular-pickaxe")).findFirst().orElseThrow();
        require(pickaxe.stack().is(ModItems.PICKAXE) && pickaxe.stack().getCount() == 1, "irregular child token and item retained");
    }

    private static void denied(GameTestHelper helper, Villager body, LiveHands before, ItemStack stack) {
        ItemEntity source = spawn(helper, body, stack);
        require(BodyHandItemTransfer.pickupOwned(body, source.getUUID(), "left", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "malformed grid host rejected");
        require(hands(body) == before && !source.isRemoved() && ItemStack.matches(stack, source.getItem()),
                "rejection retains exact world item and body attachment");
        source.discard();
    }

    private static ItemEntity spawn(GameTestHelper helper, Villager body, ItemStack stack) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemEntity source = new ItemEntity(helper.getLevel(), pos.getX() + 2.5, pos.getY() + 1.5, pos.getZ() + .5, stack.copy());
        require(helper.getLevel().addFreshEntity(source), "world item spawned");
        return source;
    }

    private static List<ItemEntity> items(GameTestHelper helper, Villager body) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5), e -> !e.getItem().isEmpty());
    }

    private static Villager body(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true); body.setYRot(-90); body.setYHeadRot(-90); body.setXRot(0);
        require(LiveHands.initialize(body).isPresent(), "spawned prototype-bound body");
        return body;
    }

    private static LiveHands hands(Villager body) { return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
