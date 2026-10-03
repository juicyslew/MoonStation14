package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.live.CrossPublicationFixture;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

public final class BodyWorldCrossPublicationGameTests {
    private static final List<String> SLOTS = List.of("belt", "back");
    private static final GridGeometry.Cell ZERO = new GridGeometry.Cell(0, 0);
    private static final GridGeometry.Rotation FLAT = GridGeometry.Rotation.DEG_0;
    private BodyWorldCrossPublicationGameTests() { }

    public static void transfersBothDirectionsAndPreservesBothOwners(GameTestHelper helper) {
        setup(helper, (body, world) -> {
            ItemStack reference = world.getItem();
            ItemStack copy = reference.copy();
            LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            BodyWorldCrossAction toBody = action(BodyWorldCrossAction.Direction.WORLD_TO_BODY, world, before, 1, 0);
            var outcome = BodyWorldCrossTransfer.applyTrusted(body, world, reference, copy, toBody,
                    (b, next) -> b.setData(ModDataAttachments.LIVE_HANDS.get(), next), ItemEntity::setItem, () -> true);
            require(outcome == BodyWorldCrossTransfer.Outcome.SUCCESS, "world to body succeeds");
            LiveHands transferred = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(transferred.revision() == before.revision() + 1, "body revision advanced");
            require(transferred.token("left").equals(before.token("left")) && transferred.token("right").equals(before.token("right")), "hand tokens unchanged");
            require(GridContents.read(equipmentStack(body, "back")).orElseThrow().entries().stream()
                    .anyMatch(e -> e.token().equals("child") && e.stack().getCount() == 7
                            && Component.literal("cross-child").equals(e.stack().get(DataComponents.CUSTOM_NAME))), "worn bag receives exact child");
            require(GridContents.read(world.getItem()).orElseThrow().entries().isEmpty(), "world grid emptied");

            BodyWorldCrossAction toWorld = action(BodyWorldCrossAction.Direction.BODY_TO_WORLD, world, transferred, 2, 0);
            outcome = BodyWorldCrossTransfer.applyTrusted(body, world, world.getItem(), world.getItem().copy(), toWorld,
                    (b, next) -> b.setData(ModDataAttachments.LIVE_HANDS.get(), next), ItemEntity::setItem, () -> true);
            require(outcome == BodyWorldCrossTransfer.Outcome.SUCCESS, "body to world succeeds");
            LiveHands returned = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(returned.revision() == transferred.revision() + 1, "return body revision advanced");
            require(GridContents.read(equipmentStack(body, "back")).orElseThrow().entries().isEmpty(), "worn bag emptied");
            assertChild(world.getItem());
            helper.succeed();
        });
    }

    public static void blockedFitAndPublisherFailureConserveChild(GameTestHelper helper) {
        setup(helper, (body, world) -> {
            ItemStack reference = world.getItem();
            ItemStack copy = reference.copy();
            LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            BodyWorldCrossAction invalid = action(BodyWorldCrossAction.Direction.WORLD_TO_BODY, world, before, 1, 7);
            var outcome = BodyWorldCrossTransfer.applyTrusted(body, world, reference, copy, invalid,
                    (b, next) -> b.setData(ModDataAttachments.LIVE_HANDS.get(), next), ItemEntity::setItem, () -> true);
            require(outcome == BodyWorldCrossTransfer.Outcome.DENIED, "out of bounds placement denied");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before && world.getItem() == reference, "fit denial leaves owners unchanged");
            BodyWorldCrossAction valid = action(BodyWorldCrossAction.Direction.WORLD_TO_BODY, world, before, 1, 0);
            outcome = BodyWorldCrossTransfer.applyTrusted(body, world, reference, copy, valid,
                    (b, next) -> { throw new IllegalStateException("publisher failed before write"); },
                    ItemEntity::setItem, () -> true);
            require(outcome == BodyWorldCrossTransfer.Outcome.DENIED, "body publisher failure denied");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before, "body unchanged after publisher failure");
            require(world.getItem() != reference && world.getItem().getCount() == reference.getCount(), "world reference restored");
            assertChild(world.getItem());
            helper.succeed();
        });
    }

    private static void setup(GameTestHelper helper, java.util.function.BiConsumer<Villager, ItemEntity> assertions) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            CrossPublicationFixture.initialize(body);
            var equipment = LiveHands.equipmentSnapshot(body).orElseThrow();
            require(equipment.persisted() && equipment.slots().stream().anyMatch(slot -> slot.id().equals("back")
                    && slot.token().filter("bag"::equals).isPresent() && slot.stack().isPresent())
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("left").isPresent()
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("right").isPresent(),
                    "fixture has persisted back bag and both occupied hands");
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            ItemStack worldBag = new ItemStack(ModItems.BAG.get());
            worldBag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                    new GridContents.Entry("child", named(), 0, 0, FLAT))));
            ItemEntity world = new ItemEntity(helper.getLevel(), pos.getX() + 2.5, pos.getY() + 1.5, pos.getZ() + .5, worldBag);
            require(helper.getLevel().addFreshEntity(world), "world item added");
            world.setNoPickUpDelay();
            assertions.accept(body, world);
        });
    }

    private static BodyWorldCrossAction action(BodyWorldCrossAction.Direction direction, ItemEntity world,
            LiveHands state, long sequence, int newX) {
        return new BodyWorldCrossAction(direction, 1, sequence, world.getUUID(), UUID.randomUUID(), 0,
                state.revision(), true, "back", "bag", "child", 0, 0, 0, newX, 0, 0);
    }

    private static void assertChild(ItemStack host) {
        var child = GridContents.read(host).orElseThrow().entries().stream()
                .filter(entry -> entry.token().equals("child")).findFirst().orElseThrow().stack();
        require(child.getCount() == 7 && Component.literal("cross-child").equals(child.get(DataComponents.CUSTOM_NAME)), "child count and name preserved");
    }

    private static ItemStack equipmentStack(Villager body, String id) {
        return LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream().filter(slot -> slot.id().equals(id))
                .findFirst().orElseThrow().stack().orElseThrow();
    }

    private static ItemStack named() {
        ItemStack stack = new ItemStack(Items.APPLE, 7);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("cross-child"));
        return stack;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
