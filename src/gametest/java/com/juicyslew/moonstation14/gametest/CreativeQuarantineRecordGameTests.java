package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeQuarantineRecord;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeQuarantineRecordGameTests {
    private static final UUID WORLD = UUID.fromString("00a260ca-0975-4f19-806d-f94e1e377951");
    private static final UUID ACCOUNT = UUID.fromString("00a260ca-0975-4f19-806d-f94e1e377952");
    private static final UUID TRANSITION = UUID.fromString("00a260ca-0975-4f19-806d-f94e1e377953");

    private CreativeQuarantineRecordGameTests() { }

    private static CreativeInventorySnapshot snapshot() {
        List<ItemStack> main = new ArrayList<>(Collections.nCopies(36, ItemStack.EMPTY));
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("journal component"));
        main.set(0, named);
        main.set(1, new ItemStack(Items.STONE, 47));
        return CreativeInventorySnapshot.capture(main, Collections.nCopies(4, ItemStack.EMPTY),
                List.of(ItemStack.EMPTY), new ItemStack(Items.EMERALD, 13), 6, List.of());
    }

    private static CreativeQuarantineRecord record(RegistryAccess registries,
                                                   CreativeQuarantineRecord.Phase phase) {
        return new CreativeQuarantineRecord(WORLD, ACCOUNT, 7, TRANSITION, 3, phase, snapshot(), registries);
    }

    private static CreativeQuarantineRecord decode(CompoundTag tag, RegistryAccess registries) {
        return CreativeQuarantineRecord.decode(tag, registries, WORLD, ACCOUNT, 6, TRANSITION, 3);
    }

    @GameTest(template = "empty")
    public static void phasesRoundtripWithoutGrantAndCopyIsolation(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        for (var phase : CreativeQuarantineRecord.Phase.values()) {
            var original = record(registries, phase);
            CompoundTag encoded = original.encode();
            var restored = decode(encoded, registries);
            require(restored.phase() == phase && restored.revision() == 7
                    && restored.sessionGeneration() == 3 && restored.world().equals(WORLD)
                    && restored.account().equals(ACCOUNT) && restored.transition().equals(TRANSITION), "metadata");
            require(restored.encode().equals(encoded), "native NBT roundtrip");
            encoded.getCompound("snapshot").getList("slots", 10).getCompound(1).putInt("slot", 0);
            require(restored.snapshot(registries).stackCopy(1).getCount() == 47, "input NBT detached");
            var egress = restored.encode();
            egress.putString("phase", "tampered");
            require(restored.phase() == phase, "output NBT detached");
            ItemStack output = restored.snapshot(registries).stackCopy(0);
            output.set(DataComponents.CUSTOM_NAME, Component.literal("tampered"));
            require(restored.snapshot(registries).stackCopy(0).getHoverName().getString()
                    .equals("journal component"), "item component detached");
            require(restored.snapshot(registries).stackCopy(41).getCount() == 13
                    && restored.snapshot(registries).selectedHotbarIndex() == 6, "cursor and selection");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectsWrongIdentityOrderingAndMalformedData(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var base = record(registries, CreativeQuarantineRecord.Phase.PARKED).encode();
        expectFailure(() -> CreativeQuarantineRecord.decode(base, registries, UUID.randomUUID(), ACCOUNT, 6, TRANSITION, 3));
        expectFailure(() -> CreativeQuarantineRecord.decode(base, registries, WORLD, UUID.randomUUID(), 6, TRANSITION, 3));
        expectFailure(() -> CreativeQuarantineRecord.decode(base, registries, WORLD, ACCOUNT, 7, TRANSITION, 3));
        expectFailure(() -> CreativeQuarantineRecord.decode(base, registries, WORLD, ACCOUNT, 8, TRANSITION, 3));
        expectFailure(() -> CreativeQuarantineRecord.decode(base, registries, WORLD, ACCOUNT, 6, UUID.randomUUID(), 3));
        expectFailure(() -> CreativeQuarantineRecord.decode(base, registries, WORLD, ACCOUNT, 6, TRANSITION, 4));
        expectFailure(() -> decode(null, registries));
        expectFailure(() -> new CreativeQuarantineRecord(WORLD, ACCOUNT, -1, TRANSITION, 3,
                CreativeQuarantineRecord.Phase.PREPARED, snapshot(), registries));
        expectFailure(() -> new CreativeQuarantineRecord(WORLD, ACCOUNT, 1, TRANSITION, -1,
                CreativeQuarantineRecord.Phase.PREPARED, snapshot(), registries));
        CompoundTag schema = base.copy();
        schema.putInt("schema", 2);
        expectFailure(() -> decode(schema, registries));
        CompoundTag phase = base.copy();
        phase.putString("phase", "CREATIVE_AVAILABLE");
        expectFailure(() -> decode(phase, registries));
        CompoundTag missing = base.copy();
        missing.remove("snapshot");
        expectFailure(() -> decode(missing, registries));
        CompoundTag extra = base.copy();
        extra.putBoolean("authorized", true);
        expectFailure(() -> decode(extra, registries));
        CompoundTag type = base.copy();
        type.putInt("revision", 7);
        expectFailure(() -> decode(type, registries));
        CompoundTag uuid = base.copy();
        uuid.put("world", new IntArrayTag(new int[] { 1, 2, 3 }));
        expectFailure(() -> decode(uuid, registries));
        CompoundTag negative = base.copy();
        negative.putLong("revision", -1);
        expectFailure(() -> decode(negative, registries));
        CompoundTag malformed = base.copy();
        malformed.getCompound("snapshot").getList("slots", 10).getCompound(1).putInt("slot", 0);
        expectFailure(() -> decode(malformed, registries));
        CompoundTag oversized = base.copy();
        oversized.putString("padding", "x".repeat(CreativeQuarantineRecord.MAX_BYTES));
        expectFailure(() -> decode(oversized, registries));
        CompoundTag oversizedSnapshot = base.copy();
        oversizedSnapshot.getCompound("snapshot").putString("padding", "x".repeat(1_048_000));
        expectFailure(() -> decode(oversizedSnapshot, registries));
        helper.succeed();
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            return;
        }
        throw new GameTestAssertException("invalid journal accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
