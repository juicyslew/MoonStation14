package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeInventorySnapshotGameTests {
    private CreativeInventorySnapshotGameTests() { }

    private static List<ItemStack> empty(int size) {
        return new ArrayList<>(java.util.Collections.nCopies(size, ItemStack.EMPTY));
    }

    private static CreativeInventorySnapshot capture(List<ItemStack> main, List<ItemStack> armor,
                                                      List<ItemStack> offhand, ItemStack cursor, int selected) {
        return CreativeInventorySnapshot.capture(main, armor, offhand, cursor, selected, List.of());
    }

    @GameTest(template = "empty")
    public static void invalidStackDiagnosisIsReadOnlyAndMatchesStrictCapture(GameTestHelper helper) {
        List<ItemStack> main = empty(36);
        List<ItemStack> armor = empty(4);
        List<ItemStack> offhand = empty(1);
        require(CreativeInventorySnapshot.diagnose(main, armor, offhand, ItemStack.EMPTY, 0) == null,
                "canonical EMPTY is valid");
        require(CreativeInventorySnapshot.diagnose(main, armor, offhand, new ItemStack(Items.AIR, 0), 0) == null,
                "zero-count observation is absent");
        main.set(7, new ItemStack(Items.AIR, 0));
        require(CreativeInventorySnapshot.diagnose(main, armor, offhand, ItemStack.EMPTY, 0) == null,
                "zero-count main residue absent");
        main.set(7, ItemStack.EMPTY);
        armor.set(2, new ItemStack(Items.AIR, 0));
        require(CreativeInventorySnapshot.diagnose(main, armor, offhand, ItemStack.EMPTY, 0) == null,
                "zero-count armor residue absent");
        armor.set(2, ItemStack.EMPTY);
        offhand.set(0, new ItemStack(Items.AIR, 0));
        require(CreativeInventorySnapshot.diagnose(main, armor, offhand, ItemStack.EMPTY, 0) == null,
                "zero-count offhand residue absent");
        offhand.set(0, ItemStack.EMPTY);

        ItemStack zeroStone = new ItemStack(Items.STONE);
        zeroStone.setCount(0);
        main.set(3, zeroStone);
        require(CreativeInventorySnapshot.diagnose(main, armor, offhand, ItemStack.EMPTY, 0) == null,
                "shrunken main residue absent");
        main.set(3, ItemStack.EMPTY);
        ItemStack overstack = new ItemStack(Items.DIAMOND_SWORD, 2);
        require(overstack.getMaxStackSize() == 1, "max-one fixture");
        assertIssue(main, armor, offhand, overstack, 0,
                CreativeInventorySnapshot.ValidationCategory.INVALID_COUNT, "cursor", 0);
        assertIssue(main, armor, offhand, ItemStack.EMPTY, 9,
                CreativeInventorySnapshot.ValidationCategory.INVALID_SELECTION, "selection", -1);
        main.set(4, null);
        assertIssue(main, armor, offhand, ItemStack.EMPTY, 0,
                CreativeInventorySnapshot.ValidationCategory.NULL, "main", 4);
        main.set(4, ItemStack.EMPTY);
        assertIssue(main, armor, offhand, null, 0,
                CreativeInventorySnapshot.ValidationCategory.NULL, "cursor", 0);
        ItemStack named = new ItemStack(Items.STONE, 12);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("must remain unchanged"));
        main.set(1, named);
        assertIssue(main, armor, offhand, overstack, 0,
                CreativeInventorySnapshot.ValidationCategory.INVALID_COUNT, "cursor", 0);
        require(named.getCount() == 12 && named.getHoverName().getString().equals("must remain unchanged"),
                "occupied source contents unchanged");
        main.set(1, ItemStack.EMPTY);
        require(CreativeInventorySnapshot.diagnose(main, armor, offhand, ItemStack.EMPTY, 0) == null,
                "diagnosis clears when second observation is canonical");
        helper.succeed();
    }

    private static void assertIssue(List<ItemStack> main, List<ItemStack> armor, List<ItemStack> offhand,
                                    ItemStack cursor, int selected,
                                    CreativeInventorySnapshot.ValidationCategory category,
                                    String compartment, int slot) {
        List<ItemStack> mainBefore = new ArrayList<>(main);
        List<ItemStack> armorBefore = new ArrayList<>(armor);
        List<ItemStack> offhandBefore = new ArrayList<>(offhand);
        ItemStack cursorBefore = cursor;
        int cursorCount = cursor == null ? -1 : cursor.getCount();
        int zeroCount = main.get(3) == null ? -1 : main.get(3).getCount();
        var issue = CreativeInventorySnapshot.diagnose(main, armor, offhand, cursor, selected);
        require(issue != null && issue.category() == category && issue.compartment().equals(compartment)
                && issue.slot() == slot, "diagnosis " + compartment + "[" + slot + "]");
        if (category == CreativeInventorySnapshot.ValidationCategory.NULL) {
            try {
                capture(main, armor, offhand, cursor, selected);
                throw new net.minecraft.gametest.framework.GameTestAssertException("null input accepted");
            } catch (NullPointerException expected) {
                // The strict capture contract uses requireNonNull for malformed sources.
            }
        } else expectFailure(() -> capture(main, armor, offhand, cursor, selected));
        for (int i = 0; i < main.size(); i++) require(main.get(i) == mainBefore.get(i), "main reference " + i);
        for (int i = 0; i < armor.size(); i++) require(armor.get(i) == armorBefore.get(i), "armor reference " + i);
        for (int i = 0; i < offhand.size(); i++) require(offhand.get(i) == offhandBefore.get(i), "offhand reference " + i);
        require(cursor == cursorBefore && (cursor == null || cursor.getCount() == cursorCount), "cursor unchanged");
        require(main.get(3) == null || main.get(3).getCount() == zeroCount, "main stack count unchanged");
    }

    @GameTest(template = "empty")
    public static void emptyAndFullNativeRoundtripAndIsolation(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var empty = capture(empty(36), empty(4), empty(1), ItemStack.EMPTY, 0);
        var emptyRestored = CreativeInventorySnapshot.decode(empty.encode(registries), registries);
        for (int i = 0; i < 42; i++) require(emptyRestored.stackCopy(i).isEmpty(), "empty slot " + i);

        List<ItemStack> main = empty(36);
        for (int i = 0; i < main.size(); i++) main.set(i, new ItemStack(Items.STONE, i % 64 + 1));
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("isolated component"));
        main.set(0, named);
        List<ItemStack> armor = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND_BOOTS),
                new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.DIAMOND_HELMET)));
        List<ItemStack> offhand = new ArrayList<>(List.of(new ItemStack(Items.SHIELD)));
        ItemStack cursor = new ItemStack(Items.EMERALD, 17);
        var snapshot = capture(main, armor, offhand, cursor, 8);
        CompoundTag encodedSnapshot = snapshot.encode(registries);
        for (int slot : new int[] {0, 36, 37, 38, 39, 40}) {
            CompoundTag single = encodedSnapshot.getList("slots", 10).getCompound(slot).getCompound("stack");
            require(!single.contains("count") || single.contains("count", net.minecraft.nbt.Tag.TAG_INT)
                    && single.getInt("count") == 1, "canonical one-count slot " + slot);
        }
        named.set(DataComponents.CUSTOM_NAME, Component.literal("changed source"));
        main.set(1, ItemStack.EMPTY);
        cursor.shrink(1);
        ItemStack output = snapshot.stackCopy(0);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("changed output"));
        var restored = CreativeInventorySnapshot.decode(encodedSnapshot, registries);
        require(restored.encode(registries).equals(encodedSnapshot), "canonical one-count roundtrip");
        require(restored.selectedHotbarIndex() == 8, "selection");
        require(restored.stackCopy(0).getHoverName().getString().equals("isolated component"), "component");
        require(restored.stackCopy(1).getCount() == 2, "source isolated");
        require(restored.stackCopy(40).is(Items.SHIELD), "offhand");
        require(restored.stackCopy(0).getCount() == 1 && restored.stackCopy(36).getCount() == 1
                && restored.stackCopy(40).getCount() == 1, "single-item counts");
        require(restored.stackCopy(41).getCount() == 17, "cursor");
        require(restored.stackCopy(36).is(Items.DIAMOND_BOOTS), "armor");
        for (int i = 0; i < 42; i++) require(!restored.stackCopy(i).isEmpty(), "full slot " + i);
        ItemStack decodedOutput = restored.stackCopy(0);
        decodedOutput.set(DataComponents.CUSTOM_NAME, Component.literal("changed decoded output"));
        require(restored.stackCopy(0).getHoverName().getString().equals("isolated component"), "decoded egress isolated");
        ItemStack emptyOutput = emptyRestored.stackCopy(0);
        require(emptyOutput != ItemStack.EMPTY, "empty egress is not shared EMPTY");
        emptyOutput.setCount(9);
        require(emptyRestored.stackCopy(0).getCount() == 0, "empty egress isolated");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedAndUnsupportedInputsFailClosed(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var empty = capture(empty(36), empty(4), empty(1), ItemStack.EMPTY, 0);
        expectFailure(() -> CreativeInventorySnapshot.capture(empty(36), empty(4), empty(1),
                ItemStack.EMPTY, 0, List.of(new ItemStack(Items.STONE))));
        expectFailure(() -> capture(empty(35), empty(4), empty(1), ItemStack.EMPTY, 0));
        expectFailure(() -> capture(empty(36), empty(4), empty(1), ItemStack.EMPTY, 9));
        expectFailure(() -> capture(empty(36), empty(4), empty(1), new ItemStack(Items.STONE, 65), 0));
        ItemStack zeroStone = new ItemStack(Items.STONE);
        zeroStone.setCount(0);
        require(zeroStone.isEmpty() && zeroStone.getCount() == 0, "zero-count fixture");
        List<ItemStack> malformedMain = empty(36);
        malformedMain.set(0, zeroStone);
        require(capture(malformedMain, empty(4), empty(1), ItemStack.EMPTY, 0).stackCopy(0).isEmpty(),
                "zero-count residue is absent from detached snapshot");
        ItemStack positiveAir = new ItemStack(Items.AIR, 0);
        positiveAir.setCount(1);
        // The public ItemStack API masks AIR as empty, including its count. Capture cannot
        // distinguish this from an ordinary zero-count residue and must not claim otherwise.
        require(positiveAir.isEmpty() && positiveAir != ItemStack.EMPTY, "positive-count air fixture");
        require(capture(empty(36), empty(4), empty(1), positiveAir, 0).stackCopy(41).isEmpty(),
                "AIR masked as empty by public API treated absent");
        // Zero count is an observational boundary: the underlying item/components are unreadable.
        require(capture(empty(36), empty(4), empty(1), new ItemStack(Items.AIR, 0), 0).stackCopy(41).isEmpty(),
                "zero-count cursor treated absent");
        expectFailure(() -> CreativeInventorySnapshot.capture(empty(36), empty(4), empty(1),
                ItemStack.EMPTY, 0, List.of(zeroStone)));
        CompoundTag base = empty.encode(registries);
        CompoundTag duplicate = base.copy();
        duplicate.getList("slots", 10).getCompound(1).putInt("slot", 0);
        expectFailure(() -> CreativeInventorySnapshot.decode(duplicate, registries));
        CompoundTag missing = base.copy();
        missing.getList("slots", 10).remove(1);
        expectFailure(() -> CreativeInventorySnapshot.decode(missing, registries));
        CompoundTag range = base.copy();
        range.getList("slots", 10).getCompound(0).putInt("slot", 42);
        expectFailure(() -> CreativeInventorySnapshot.decode(range, registries));
        CompoundTag unknown = base.copy();
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "moonstation14:not_registered_for_test");
        stack.putInt("count", 1);
        unknown.getList("slots", 10).getCompound(0).put("stack", stack);
        expectFailure(() -> CreativeInventorySnapshot.decode(unknown, registries));
        stack.putString("id", "minecraft:stone");
        stack.putInt("count", 65);
        expectFailure(() -> CreativeInventorySnapshot.decode(unknown, registries));
        stack.remove("count");
        CompoundTag components = new CompoundTag();
        components.putString("moonstation14:not_registered_for_test", "value");
        stack.put("components", components);
        expectFailure(() -> CreativeInventorySnapshot.decode(unknown, registries));
        stack.remove("components");
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        require(ItemStack.CODEC.parse(ops, stack).getOrThrow().getCount() == 1,
                "missing count codec default");
        boolean canonicalHasCount = ((CompoundTag) ItemStack.CODEC.encodeStart(ops,
                new ItemStack(Items.STONE)).getOrThrow()).contains("count");
        if (canonicalHasCount) expectFailure(() -> CreativeInventorySnapshot.decode(unknown, registries));
        else require(CreativeInventorySnapshot.decode(unknown, registries).stackCopy(0).getCount() == 1,
                "canonical missing count accepted");
        stack.putInt("count", 1);
        if (canonicalHasCount)
            require(CreativeInventorySnapshot.decode(unknown, registries).stackCopy(0).getCount() == 1,
                    "canonical explicit one accepted");
        else expectFailure(() -> CreativeInventorySnapshot.decode(unknown, registries));
        stack.putByte("count", (byte) 1);
        expectFailure(() -> CreativeInventorySnapshot.decode(unknown, registries));
        stack.putInt("count", 0);
        expectFailure(() -> CreativeInventorySnapshot.decode(unknown, registries));
        stack.putInt("count", -1);
        expectFailure(() -> CreativeInventorySnapshot.decode(unknown, registries));
        List<ItemStack> namedMain = empty(36);
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("nested"));
        namedMain.set(0, named);
        CompoundTag occupied = capture(namedMain, empty(4), empty(1), ItemStack.EMPTY, 0).encode(registries);
        CompoundTag occupiedStack = occupied.getList("slots", 10).getCompound(0).getCompound("stack");
        CompoundTag customData = new CompoundTag();
        CompoundTag nested = new CompoundTag();
        nested.putString("arbitrary", "preserved");
        customData.put("nested", nested);
        occupiedStack.getCompound("components").put("minecraft:custom_data", customData);
        var restoredCustom = CreativeInventorySnapshot.decode(occupied, registries);
        require(restoredCustom.encode(registries).equals(occupied), "valid custom component payload retained");
        CompoundTag malformedNested = occupied.copy();
        CompoundTag malformedStack = malformedNested.getList("slots", 10).getCompound(0).getCompound("stack");
        CompoundTag unbreakable = new CompoundTag();
        unbreakable.putBoolean("show_in_tooltip", true);
        unbreakable.putString("ignored_extra", "must not disappear");
        malformedStack.getCompound("components").put("minecraft:unbreakable", unbreakable);
        ItemStack parsed = ItemStack.CODEC.parse(ops, malformedStack)
                .getOrThrow(message -> new IllegalArgumentException("Nested fixture decode: " + message));
        require(!ItemStack.CODEC.encodeStart(ops, parsed).getOrThrow().equals(malformedStack),
                "nested fixture must exercise silent normalization");
        expectFailure(() -> CreativeInventorySnapshot.decode(malformedNested, registries));
        CompoundTag overlimit = base.copy();
        CompoundTag padding = new CompoundTag();
        for (int i = 0; i < 70; i++) padding.putString("padding" + i, "x".repeat(16_000));
        overlimit.put("padding", padding);
        expectFailure(() -> CreativeInventorySnapshot.decode(overlimit, registries));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lastItemSplitAndShrinkAreReadOnlyEmptyObservations(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        List<ItemStack> main = empty(36);
        List<ItemStack> armor = empty(4);
        List<ItemStack> offhand = empty(1);
        ItemStack dropped = new ItemStack(Items.STONE);
        dropped.split(1);
        main.set(0, dropped);
        ItemStack hand = new ItemStack(Items.EMERALD);
        hand.shrink(1);
        offhand.set(0, hand);
        ItemStack cursor = new ItemStack(Items.DIAMOND);
        cursor.shrink(1);
        ItemStack bag = new ItemStack(Items.BUNDLE);
        bag.set(DataComponents.CUSTOM_NAME, Component.literal("retained component"));
        main.set(12, bag);
        ItemStack surviving = new ItemStack(Items.STONE, 19);
        main.set(13, surviving);
        require(CreativeInventorySnapshot.diagnose(main, armor, offhand, cursor, 0) == null,
                "last-item residues accepted by observation");
        var snapshot = capture(main, armor, offhand, cursor, 0);
        var encoded = snapshot.encode(registries);
        var decoded = CreativeInventorySnapshot.decode(encoded, registries);
        require(encoded.equals(decoded.encode(registries)), "residue codec roundtrip");
        require(main.get(0) == dropped && dropped.getCount() == 0 && offhand.get(0) == hand
                && hand.getCount() == 0 && cursor.getCount() == 0, "source residues unchanged");
        require(ItemStack.matches(decoded.stackCopy(12), bag)
                && ItemStack.matches(decoded.stackCopy(13), surviving)
                && decoded.stackCopy(13).getCount() == 19, "positive items/components preserved");
        require(decoded.stackCopy(0).isEmpty() && decoded.stackCopy(40).isEmpty()
                && decoded.stackCopy(41).isEmpty(), "zero-count slots absent in detached value");
        helper.succeed();
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("invalid input accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
