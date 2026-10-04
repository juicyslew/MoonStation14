package com.juicyslew.moonstation14.ms14.storage.grid;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GridContentsGameTests {
    private GridContentsGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void codecsLegacyReadAndValidation(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STICK, 50);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("kept native component"));
        GridContents contents = GridContents.of(List.of(new GridContents.Entry("child", stack, 0, 0,
                GridGeometry.Rotation.DEG_0)));
        stack.setCount(1);
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var encoded = GridContents.CODEC.encodeStart(ops, contents);
        require(encoded.result().isPresent(), "registry-aware persistent encoding succeeds");
        var decoded = GridContents.CODEC.parse(ops, encoded.result().orElseThrow());
        require(decoded.result().isPresent(), "registry-aware persistent decoding succeeds");
        var entry = decoded.result().orElseThrow().entries().getFirst();
        require(entry.stack().getCount() == 50 && entry.token().equals("child"), "count and token round trip");
        require(ItemStack.matches(entry.stack(), contents.entries().getFirst().stack()), "persistent codec retains native components");
        RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            GridContents.STREAM_CODEC.encode(wire, contents);
            require(contents.equals(GridContents.STREAM_CODEC.decode(wire)), "stream codec retains count and native components");
        } finally {
            wire.release();
        }
        require(contents.equals(decoded.result().orElseThrow())
                && contents.hashCode() == decoded.result().orElseThrow().hashCode(), "value equality/hash contract");

        ItemStack bag = new ItemStack(ModItems.BAG.get());
        bag.set(ModDataComponents.POUCH_CONTENTS.get(), com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents.of(
                "legacy", new ItemStack(Items.STICK, 50)));
        var virtual = GridContents.read(bag).orElseThrow();
        require(bag.has(ModDataComponents.POUCH_CONTENTS.get()) && !bag.has(ModDataComponents.GRID_CONTENTS.get()),
                "read does not migrate or mutate host");
        var migrated = GridContents.applyCopyWithGrid(bag, virtual).orElseThrow();
        require(!migrated.has(ModDataComponents.POUCH_CONTENTS.get()) && bag.has(ModDataComponents.POUCH_CONTENTS.get()),
                "explicit copy-on-write applies migration");

        ItemStack dual = bag.copy();
        dual.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
        require(GridContents.read(dual).isEmpty() && GridContents.applyCopyWithGrid(dual, virtual).isEmpty(),
                "dual representation rejected without overwrite");
        require(GridContents.read(new ItemStack(Items.STICK)).isEmpty(), "ordinary stick is not a grid host");

        GridContents overlapping = GridContents.of(List.of(
                new GridContents.Entry("overlap-a", new ItemStack(Items.STICK), 0, 0, GridGeometry.Rotation.DEG_0),
                new GridContents.Entry("overlap-b", new ItemStack(Items.STICK), 0, 0, GridGeometry.Rotation.DEG_0)));
        require(overlapping.entries().size() == 2, "structural construction preserves overlapping decoded entries");
        ItemStack overlappingBag = new ItemStack(ModItems.BAG.get());
        overlappingBag.set(ModDataComponents.GRID_CONTENTS.get(), overlapping);
        require(GridContents.read(overlappingBag).isEmpty(), "live host read rejects overlap using authoritative footprints");

        List<GridContents.Entry> tooMany = new ArrayList<>();
        for (int i = 0; i < 33; i++) tooMany.add(new GridContents.Entry("t" + i, new ItemStack(Items.STICK), i % 8, i / 8,
                GridGeometry.Rotation.DEG_0));
        requireThrows(() -> GridContents.of(tooMany), "entry cap enforced at value construction");
        requireThrows(() -> new GridContents.Entry(" ", new ItemStack(Items.STICK), 0, 0,
                GridGeometry.Rotation.DEG_0), "malformed token rejected");
        requireThrows(() -> GridContents.of(List.of(new GridContents.Entry("outside", new ItemStack(Items.STICK), 8, 0,
                GridGeometry.Rotation.DEG_0))), "invalid geometry cannot be persisted");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void boundedCodecRejectsOversizedAndMalformedLists(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<GridContents.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 32; i++) entries.add(new GridContents.Entry("t" + i,
                new ItemStack(Items.STICK), i % 8, i / 8, GridGeometry.Rotation.DEG_0));
        JsonArray snapshot = GridContents.CODEC.encodeStart(ops, GridContents.of(entries)).getOrThrow().getAsJsonArray();
        require(snapshot.size() == 32 && GridContents.CODEC.parse(ops, snapshot).result().isPresent(),
                "valid 32-entry snapshot decodes");

        JsonArray oversized = snapshot.deepCopy();
        oversized.add(snapshot.get(0).deepCopy());
        var rejected = GridContents.CODEC.parse(ops, oversized);
        require(rejected.result().isEmpty() && rejected.error().isPresent()
                        && rejected.error().orElseThrow().message().contains("oversized"),
                "33 entries rejected during preflight, before duplicate entry decoding");
        JsonArray malformedOversized = oversized.deepCopy();
        malformedOversized.set(0, new JsonObject());
        require(GridContents.CODEC.parse(ops, malformedOversized).error().orElseThrow().message().contains("oversized"),
                "oversized list rejected before any malformed ItemStack is decoded");

        JsonArray missing = snapshot.deepCopy();
        missing.get(0).getAsJsonObject().remove("stack");
        require(GridContents.CODEC.parse(ops, missing).result().isEmpty(), "missing child rejected without partial contents");
        JsonArray negative = snapshot.deepCopy();
        negative.get(0).getAsJsonObject().addProperty("x", -1);
        require(GridContents.CODEC.parse(ops, negative).result().isEmpty(), "negative placement rejected without partial contents");
        require(GridContents.CODEC.parse(ops, new JsonObject()).result().isEmpty(), "non-list rejected");

        ItemStack host = new ItemStack(ModItems.BAG.get());
        JsonObject encodedHost = ItemStack.CODEC.encodeStart(ops, host).getOrThrow().getAsJsonObject();
        JsonObject components = encodedHost.has("components") ? encodedHost.getAsJsonObject("components") : new JsonObject();
        components.add("moonstation14:grid_contents", oversized);
        encodedHost.add("components", components);
        require(ItemStack.CODEC.parse(ops, encodedHost).result().isEmpty(), "host with 33 entries fails decoding");
        require(!host.has(ModDataComponents.GRID_CONTENTS.get()) && GridContents.read(host).orElseThrow().entries().isEmpty(),
                "failed host decoding never mutates live host");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
    private static void requireThrows(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new GameTestAssertException(message);
    }
}
