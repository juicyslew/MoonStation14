package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class BodyPouchWorldRoundTripGameTests {
    private BodyPouchWorldRoundTripGameTests() { }

    public static void worldHandWorldHandConservesFilledPouch(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack filled = filled();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemEntity source = new ItemEntity(helper.getLevel(), origin.getX() + 2.5, origin.getY() + 1.5,
                origin.getZ() + .5, filled.copy());
        require(helper.getLevel().addFreshEntity(source), "world pouch spawned");
        require(BodyHandItemTransfer.pickupOwned(body, source.getUUID(), "left", empty.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "world pouch picked up by spawned body");
        LiveHands held = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(source.isRemoved() && helper.getLevel().getEntity(source.getUUID()) == null
                && held.revision() == empty.revision() + 1, "world source consumed once");
        String ownerToken = held.token("left").orElseThrow();
        assertFilled(held.stackCopy("left").orElseThrow());
        assertNoLooseChild(helper, body);
        assertSavedBody(helper, body, held.revision(), ownerToken);

        require(BodyHandItemTransfer.pickupOwned(body, source.getUUID(), "right", empty.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "stale pickup cannot recreate consumed pouch");
        require(BodyHandItemTransfer.dropOwned(body, "left", ownerToken, empty.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "stale drop cannot duplicate pouch");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == held,
                "denials do not publish another owner");

        require(BodyHandItemTransfer.dropOwned(body, "left", ownerToken, held.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "filled pouch dropped");
        LiveHands droppedState = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(droppedState.revision() == held.revision() + 1 && droppedState.stackCopy("left").isEmpty(),
                "hand cleared after drop");
        List<ItemEntity> pouches = pouches(helper, body);
        require(pouches.size() == 1, "exactly one world pouch after drop");
        ItemEntity dropped = pouches.getFirst();
        assertFilled(dropped.getItem());
        ItemEntity restored = EntityType.ITEM.create(helper.getLevel());
        require(restored != null, "item load fixture created");
        restored.load(dropped.saveWithoutId(new CompoundTag()));
        assertFilled(restored.getItem());
        assertNoLooseChild(helper, body);

        // Aim the actual world owner at the same body-eye first-hit ray used by pickupOwned.
        dropped.setPos(origin.getX() + 2.5, origin.getY() + 1.5, origin.getZ() + .5);
        dropped.setNoPickUpDelay();
        require(BodyHandItemTransfer.pickupOwned(body, dropped.getUUID(), "left", droppedState.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "dropped pouch picked up again");
        LiveHands again = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(dropped.isRemoved() && pouches(helper, body).isEmpty()
                && again.revision() == droppedState.revision() + 1, "second world source consumed once");
        assertFilled(again.stackCopy("left").orElseThrow());
        assertSavedBody(helper, body, again.revision(), again.token("left").orElseThrow());
        assertNoLooseChild(helper, body);
        helper.succeed();
    }

    public static void occupiedPickupAndCanceledDropRetainSingleOwner(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands occupied = empty.putWhole(0, "right", new ItemToken("blocker"),
                new ItemStack(Items.STICK)).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), occupied);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemEntity source = new ItemEntity(helper.getLevel(), origin.getX() + 2.5, origin.getY() + 1.5,
                origin.getZ() + .5, filled());
        require(helper.getLevel().addFreshEntity(source), "world source spawned");
        require(BodyHandItemTransfer.pickupOwned(body, source.getUUID(), "right", occupied.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "occupied hand denies pickup");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == occupied
                && !source.isRemoved(), "denial leaves world source untouched");
        assertFilled(source.getItem());
        require(BodyHandItemTransfer.pickupOwned(body, source.getUUID(), "left", occupied.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "empty hand accepts real world source");
        LiveHands held = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String token = held.token("left").orElseThrow();
        AtomicInteger canceled = new AtomicInteger();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof ItemEntity item
                    && item.getItem().is(ModItems.POUCH)) {
                canceled.incrementAndGet();
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            require(BodyHandItemTransfer.dropOwned(body, "left", token, held.revision(), () -> true)
                    == BodyHandItemTransfer.Result.DENIED, "canceled pouch spawn compensates hand");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        LiveHands compensated = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(canceled.get() == 1 && compensated.revision() == held.revision() + 2
                && compensated.token("left").orElseThrow().equals(token)
                && compensated.token("right").orElseThrow().equals("blocker"),
                "compensation restores pouch token without overwriting occupied hand");
        assertFilled(compensated.stackCopy("left").orElseThrow());
        require(pouches(helper, body).isEmpty(), "canceled drop leaves no second pouch");
        require(BodyHandItemTransfer.dropOwned(body, "left", token, held.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "compensated revision rejects replay");
        assertSavedBody(helper, body, compensated.revision(), token);
        assertNoLooseChild(helper, body);
        helper.succeed();
    }

    public static void worldAdmissionRejectsHiddenOwnership(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands occupied = empty.putWhole(empty.revision(), "right", new ItemToken("existing-owner"),
                new ItemStack(Items.STICK, 2)).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), occupied);
        require(BodyHandStateService.representable(occupied, body), "ordinary occupied hand is ready");

        ItemStack collidingBag = PouchContents.put(new ItemStack(ModItems.BAG.get()), "existing-owner",
                new ItemStack(Items.DIAMOND, 7)).orElseThrow();
        deniedWorldStack(helper, body, occupied, collidingBag);

        ItemStack foreignPouch = filled();
        foreignPouch.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE, 3))));
        deniedWorldStack(helper, body, occupied, foreignPouch);

        ItemStack foreignChildHost = new ItemStack(Items.STICK);
        foreignChildHost.set(ModDataComponents.POUCH_CONTENTS.get(), PouchContents.of("hidden-child",
                new ItemStack(Items.DIAMOND)));
        deniedWorldStack(helper, body, occupied, foreignChildHost);

        ItemStack bag = PouchContents.put(new ItemStack(ModItems.BAG.get()), "unique-bag-child",
                new ItemStack(Items.DIAMOND, 7)).orElseThrow();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemEntity source = new ItemEntity(helper.getLevel(), origin.getX() + 2.5, origin.getY() + 1.5,
                origin.getZ() + .5, bag.copy());
        require(helper.getLevel().addFreshEntity(source), "valid bag spawned");
        require(BodyHandItemTransfer.pickupOwned(body, source.getUUID(), "left", occupied.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "valid filled bag admitted");
        LiveHands held = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyHandStateService.representable(held, body) && source.isRemoved()
                && ItemStack.matches(bag, held.stackCopy("left").orElseThrow()), "valid bag conserved");
        LiveHands equipped = held.equip(held.revision(), body, "left", "back",
                held.token("left").orElseThrow()).orElseThrow();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), equipped);
        require(BodyHandStateService.representable(equipped, body), "equipped filled bag is ready");
        deniedWorldStack(helper, body, equipped, PouchContents.put(new ItemStack(ModItems.POUCH.get()),
                "unique-bag-child", new ItemStack(Items.APPLE)).orElseThrow());
        deniedWorldStack(helper, body, equipped, PouchContents.put(new ItemStack(ModItems.POUCH.get()),
                held.token("left").orElseThrow(), new ItemStack(Items.APPLE)).orElseThrow());

        // A pre-existing malformed attachment is evidence, not permission to replace it with an empty state.
        ItemStack corrupt = new ItemStack(ModItems.POUCH.get());
        corrupt.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE))));
        LiveHands loadedCorrupt = empty.putWhole(0, "right", new ItemToken("old-corrupt"), corrupt).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), loadedCorrupt);
        require(!BodyHandStateService.representable(loadedCorrupt, body), "old corrupt state fails closed");
        deniedWorldStackWithoutReady(helper, body, loadedCorrupt, new ItemStack(Items.DIAMOND, 4));
        helper.succeed();
    }

    private static void deniedWorldStack(GameTestHelper helper, Villager body, LiveHands before, ItemStack stack) {
        deniedWorldStackWithoutReady(helper, body, before, stack);
        require(BodyHandStateService.representable(before, body), "ordinary hand remains ready");
    }

    private static void deniedWorldStackWithoutReady(GameTestHelper helper, Villager body, LiveHands before,
                                                     ItemStack stack) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemEntity source = new ItemEntity(helper.getLevel(), origin.getX() + 2.5, origin.getY() + 1.5,
                origin.getZ() + .5, stack.copy());
        require(helper.getLevel().addFreshEntity(source), "hostile world source spawned");
        require(BodyHandItemTransfer.pickupOwned(body, source.getUUID(), "left", before.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "hidden ownership denied before world clear");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                && source.isAddedToLevel() && !source.isRemoved()
                && helper.getLevel().getEntity(source.getUUID()) == source
                && ItemStack.matches(stack, source.getItem()), "denial conserves exact owners and stack");
        source.discard();
    }

    private static Villager body(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(LiveHands.initialize(body).isPresent(), "spawned prototype-bound human hands");
        return body;
    }

    private static ItemStack filled() {
        return PouchContents.put(new ItemStack(ModItems.POUCH.get()), "tagged-child",
                new ItemStack(Items.DIAMOND, 7)).orElseThrow();
    }

    private static void assertFilled(ItemStack stack) {
        require(stack.is(ModItems.POUCH) && stack.getCount() == 1, "single pouch stack");
        PouchContents.Child child = PouchContents.read(stack).orElseThrow().child().orElseThrow();
        require(child.token().equals("tagged-child") && child.stack().is(Items.DIAMOND)
                && child.stack().getCount() == 7, "one tagged child retained");
    }

    private static void assertSavedBody(GameTestHelper helper, Villager body, long revision, String token) {
        Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
        require(loaded != null, "body load fixture created");
        loaded.load(body.saveWithoutId(new CompoundTag()));
        LiveHands saved = loaded.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(saved != null && saved.revision() == revision
                && saved.token("left").orElseThrow().equals(token), "saved attachment owns pouch token");
        assertFilled(saved.stackCopy("left").orElseThrow());
    }

    private static List<ItemEntity> pouches(GameTestHelper helper, Villager body) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(ModItems.POUCH));
    }

    private static void assertNoLooseChild(GameTestHelper helper, Villager body) {
        require(helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.DIAMOND)).isEmpty(), "child never independently spawned");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
