package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ItemPresentationGameTests {
    private ItemPresentationGameTests() { }

    public static void registeredSnapshotCodecsPreservePresentation(GameTestHelper helper) {
        var source = new ItemStack(ModItems.JUG.get());
        var water = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
        var reagent = new ReagentComponent(Map.of(water, 10f));
        source.set(ModDataComponents.REAGENT.get(), reagent);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Blue jug"));
        source.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(7));
        source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        var expected = ItemPresentation.fromStack(source);
        var id = ModItems.JUG.getId().toString();
        var hand = new BodyHandStateSnapshot.Hand("right", "hand-token", id, 1, false,
                null, null, 0, null, expected, null);
        var handSnapshot = new BodyHandStateSnapshot(1, 1, BodyHandStateSnapshot.Reason.OK,
                UUID.randomUUID(), UUID.randomUUID(), 1, 0, "right", List.of(hand));
        var gridEntry = new BodyHandStateSnapshot.GridEntry("grid-item", id, 1, 0, 0, 0, reagent, expected);
        var gridHost = new BodyHandStateSnapshot.GridHost("bag-token", "bag", 7, 4, List.of(gridEntry));
        var bag = new ItemStack(ModItems.BAG.get());
        bag.set(DataComponents.CUSTOM_NAME, Component.literal("Marked bag"));
        bag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
        var bagPresentation = ItemPresentation.fromStack(bag);
        var equipment = new BodyHandStateSnapshot.EquipmentSlot("back", "bag-token", ModItems.BAG.getId().toString(),
                1, null, null, 0, bagPresentation, null);
        var withGrid = new BodyHandStateSnapshot(1, 2, BodyHandStateSnapshot.Reason.OK,
                UUID.randomUUID(), UUID.randomUUID(), 1, 0, "right", List.of(hand), List.of(equipment), List.of(gridHost));

        var buffer = new RegistryFriendlyByteBuf(new FriendlyByteBuf(Unpooled.buffer()), helper.getLevel().registryAccess());
        BodyHandStateSnapshot.STREAM_CODEC.encode(buffer, withGrid);
        var decoded = BodyHandStateSnapshot.STREAM_CODEC.decode(buffer);
        var actualHand = decoded.hands().getFirst().presentation();
        var stack = actualHand.stack();
        check(ItemStack.matches(expected.stack(), stack), "Codec changed the hand presentation stack");
        check(new CustomModelData(7).equals(stack.get(DataComponents.CUSTOM_MODEL_DATA)), "Custom model data was lost");
        check(Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "Glint override was lost");
        check(reagent.equals(stack.get(ModDataComponents.REAGENT.get())), "Reagent component was lost");
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(99));
        check(expected.equals(decoded.hands().getFirst().presentation()), "Decoded stack mutation escaped its copy");
        check(expected.equals(decoded.grids().getFirst().entries().getFirst().presentation()), "Grid presentation changed");

        check(bag.has(ModDataComponents.GRID_CONTENTS.get()), "Presentation construction mutated the source bag");
        check(!bagPresentation.stack().has(ModDataComponents.GRID_CONTENTS.get()), "Bag presentation retained grid ownership");
        check(Component.literal("Marked bag").equals(decoded.equipment().getFirst().presentation().stack().get(DataComponents.CUSTOM_NAME)),
                "Bag presentation lost appearance data");

        var world = new BodyWorldGridSnapshot(1, 1, BodyWorldGridSnapshot.Reason.OK,
                UUID.randomUUID(), UUID.randomUUID(), 1, 0, UUID.randomUUID(), "bag", 7, 4,
                List.of(new BodyWorldGridSnapshot.Entry("world-item", id, 1, 0, 0, 0, reagent, expected)),
                UUID.randomUUID(), 0);
        var worldBuffer = new RegistryFriendlyByteBuf(new FriendlyByteBuf(Unpooled.buffer()), helper.getLevel().registryAccess());
        try {
            BodyWorldGridSnapshot.STREAM_CODEC.encode(worldBuffer, world);
            var worldDecoded = BodyWorldGridSnapshot.STREAM_CODEC.decode(worldBuffer);
            var worldPresentation = worldDecoded.entries().getFirst().presentation();
            check(expected.equals(worldPresentation), "World grid codec changed presentation");
            check(ItemStack.matches(expected.stack(), worldPresentation.stack()), "World grid codec changed stack components");
            check(reagent.equals(worldPresentation.stack().get(ModDataComponents.REAGENT.get())), "World grid codec lost reagent");
            check(Boolean.TRUE.equals(worldPresentation.stack().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "World grid codec lost glint");
        } finally {
            worldBuffer.release();
        }
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
