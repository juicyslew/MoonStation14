package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReagentAttachmentFixedPointTest {
    private static final ResourceKey<ReagentData> A = ModReagents.createKey("cent_a");
    private static final ResourceKey<ReagentData> B = ModReagents.createKey("cent_b");
    private static final ResourceKey<ReagentData> UNKNOWN = ModReagents.createKey("nonexistent_test");

    @Test
    void legacyInputsAreCanonicalizedOnceAndFloatCodecShapeIsStable() {
        ReagentComponent component = new ReagentComponent(Map.of(A, .125f, B, Float.MIN_VALUE));
        assertEquals(Map.of(A, .12f), component.contents());
        var encoded = ReagentComponent.CODEC.encodeStart(JsonOps.INSTANCE, component).getOrThrow();
        assertEquals(component, ReagentComponent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());

        var oldFloatData = com.google.gson.JsonParser.parseString("{\"moonstation14:cent_a\":0.125}");
        ReagentComponent decoded = ReagentComponent.CODEC.parse(JsonOps.INSTANCE, oldFloatData).getOrThrow();
        assertEquals(Map.of(A, .12f), decoded.contents());
        assertEquals(decoded, ReagentComponent.CODEC.parse(JsonOps.INSTANCE,
                ReagentComponent.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow()).getOrThrow());

        var highDecimalData = com.google.gson.JsonParser.parseString("{\"moonstation14:cent_a\":200000.01}");
        assertEquals(20_000_001L, ReagentComponent.CODEC.parse(JsonOps.INSTANCE, highDecimalData)
                .getOrThrow().centContents().get(A));
    }

    @Test
    void exactHighCentDataSurvivesComponentAttachmentJsonNbtAndWireCodecs() {
        ReagentComponent expected = ReagentComponent.fromCents(Map.of(A, 20_000_001L));
        assertEquals(Map.of(A, 20_000_001L), expected.centContents());
        assertEquals(expected, ReagentAttachment.CODEC.parse(JsonOps.INSTANCE,
                ReagentAttachment.CODEC.encodeStart(JsonOps.INSTANCE, expected.toAttachment()).getOrThrow()).getOrThrow().toComponent());

        var json = ReagentComponent.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
        assertTrue(json.getAsJsonObject().get("moonstation14:cent_a").getAsJsonPrimitive().isNumber());
        assertEquals(20_000_001L, ReagentComponent.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().centContents().get(A));
        DynamicOps<Tag> nbtOps = NbtOps.INSTANCE;
        Tag encodedNbt = ReagentComponent.CODEC.encodeStart(nbtOps, expected).getOrThrow();
        assertInstanceOf(DoubleTag.class, ((CompoundTag) encodedNbt).get("moonstation14:cent_a"));
        assertEquals(expected, ReagentComponent.CODEC.parse(nbtOps, encodedNbt).getOrThrow());
        CompoundTag legacyNbt = new CompoundTag();
        legacyNbt.put("moonstation14:cent_a", FloatTag.valueOf(.125f));
        assertEquals(Map.of(A, 12L), ReagentComponent.CODEC.parse(nbtOps, legacyNbt).getOrThrow().centContents());
        ReagentComponent maximum = ReagentComponent.fromCents(Map.of(A, ReagentUnits.MAX_CENTS));
        assertEquals(maximum, ReagentComponent.CODEC.parse(JsonOps.INSTANCE,
                ReagentComponent.CODEC.encodeStart(JsonOps.INSTANCE, maximum).getOrThrow()).getOrThrow());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ReagentComponent.STREAM_CODEC.encode(buffer, expected);
        assertEquals(expected, ReagentComponent.STREAM_CODEC.decode(buffer));
        RegistryFriendlyByteBuf attachmentBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ReagentAttachment.STREAM_CODEC.encode(attachmentBuffer, expected.toAttachment());
        assertEquals(expected.toAttachment(), ReagentAttachment.STREAM_CODEC.decode(attachmentBuffer));
    }

    @Test
    void unknownCatalogKeySurvivesComponentAndAttachmentWireCodecsWithEmptyRegistryAccess() {
        ReagentComponent expected = ReagentComponent.fromCents(Map.of(UNKNOWN, 500L));

        RegistryFriendlyByteBuf componentBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        assertDoesNotThrow(() -> ReagentComponent.STREAM_CODEC.encode(componentBuffer, expected));
        ReagentComponent decoded = assertDoesNotThrow(() -> ReagentComponent.STREAM_CODEC.decode(componentBuffer));
        assertEquals(Map.of(UNKNOWN, 500L), decoded.centContents());

        RegistryFriendlyByteBuf attachmentBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        assertDoesNotThrow(() -> ReagentAttachment.STREAM_CODEC.encode(attachmentBuffer, expected.toAttachment()));
        ReagentAttachment decodedAttachment = assertDoesNotThrow(
                () -> ReagentAttachment.STREAM_CODEC.decode(attachmentBuffer));
        assertEquals(Map.of(UNKNOWN, 500L), decodedAttachment.toComponent().centContents());
        assertEquals(5f, decodedAttachment.getMap().get(UNKNOWN));
    }

    @Test
    void componentSnapshotsAreImmutableAndInvalidPersistentNumbersAreRejected() {
        Map<ResourceKey<ReagentData>, Float> input = new LinkedHashMap<>();
        input.put(A, .12f);
        ReagentComponent component = new ReagentComponent(input);
        input.put(A, 7f);
        assertEquals(Map.of(A, 12L), component.centContents());
        assertThrows(UnsupportedOperationException.class, () -> component.contents().put(A, 1f));
        assertThrows(UnsupportedOperationException.class, () -> component.centContents().put(A, 9L));
        assertThrows(IllegalArgumentException.class, () -> ReagentComponent.fromCents(Map.of(A, ReagentUnits.MAX_CENTS + 1)));
        assertThrows(IllegalArgumentException.class, () -> ReagentComponent.fromCents(Map.of(A, -1L)));
        for (String invalid : new String[]{"-0.01", "1e999", "21474836.48"}) {
            assertTrue(ReagentComponent.CODEC.parse(JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseString("{\"moonstation14:cent_a\":" + invalid + "}")).error().isPresent());
        }
    }

    @Test
    void storageAndCapacityAdmissionRemainExactToCents() {
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, 49.9f));
        var result = attachment.addCapacitySafe(Map.of(B, 1f), 50f);
        assertEquals(.1f, result.retained().get(B));
        assertEquals(.9f, result.excess().get(B));
        assertEquals(50f, attachment.getMap().values().stream().mapToDouble(Float::doubleValue).sum(), 0.001d);

        ReagentAttachment puddle = new ReagentAttachment(Map.of(A, 199_999.99f));
        puddle.specificAdd(A, .01f, 200_000f);
        assertEquals(200_000f, puddle.getMap().get(A));
        assertThrows(IllegalArgumentException.class, () -> puddle.specificAdd(A, 1f, Float.MAX_VALUE));
    }

    @Test
    void proportionalSplitAssignsOddCentRemainderByResourceKeyOrder() {
        Map<ResourceKey<ReagentData>, Float> reversed = new LinkedHashMap<>();
        reversed.put(B, .01f);
        reversed.put(A, .01f);
        ReagentAttachment attachment = new ReagentAttachment(reversed);
        var removed = attachment.naiveRemove(.01f);
        assertEquals(.01f, removed.get(A));
        assertFalse(removed.containsKey(B));
        assertEquals(Map.of(B, .01f), attachment.getMap());
    }

    @Test
    void centSplitReturnsImmutableConservedSnapshotAndCapsAtAvailableTotal() {
        ReagentAttachment attachment = ReagentComponent.fromCents(Map.of(A, 100L, B, 300L)).toAttachment();

        Map<ResourceKey<ReagentData>, Long> split = attachment.splitUnits(101L);

        assertEquals(101L, ReagentUnits.total(split.values()));
        assertEquals(74L, attachment.snapshotUnits().get(A));
        assertEquals(225L, attachment.snapshotUnits().get(B));
        assertEquals(299L, attachment.totalUnits());
        assertThrows(UnsupportedOperationException.class, () -> split.put(A, 0L));

        Map<ResourceKey<ReagentData>, Long> remainder = attachment.splitUnits(1_000L);
        assertEquals(299L, ReagentUnits.total(remainder.values()));
        assertEquals(Map.of(A, 74L, B, 225L), remainder);
        assertTrue(attachment.isEmpty());
    }

    @Test
    void invalidCentSplitIsMutationFree() {
        ReagentAttachment attachment = ReagentComponent.fromCents(Map.of(A, 100L, B, 300L)).toAttachment();
        Map<ResourceKey<ReagentData>, Long> before = attachment.snapshotUnits();

        assertThrows(IllegalArgumentException.class, () -> attachment.splitUnits(-1L));
        assertThrows(IllegalArgumentException.class, () -> attachment.splitUnits(ReagentUnits.MAX_CENTS + 1L));
        assertEquals(before, attachment.snapshotUnits());
    }

    @Test
    void reactionProductsFloorToCentsAndTinyReactionCannotManufactureOrLoop() {
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, .01f));
        var reaction = new com.juicyslew.moonstation14.recipe.ReactionRecipe(
                Map.of(A, 1f), Map.of(), Map.of(B, .125f));
        attachment.resolveReaction(reaction, 10f);
        assertTrue(attachment.getMap().isEmpty(), "sub-cent reaction product is floored away");

        ReagentAttachment bounded = new ReagentAttachment(Map.of(A, 1f));
        bounded.resolveReaction(new com.juicyslew.moonstation14.recipe.ReactionRecipe(
                Map.of(A, 1f), Map.of(), Map.of(B, .5f)), 10f);
        assertEquals(Map.of(B, .5f), bounded.getMap());
    }

    @Test
    void reactionOverCapacityDefersWholeRecipeWithoutLosingOldContents() {
        var reaction = new com.juicyslew.moonstation14.recipe.ReactionRecipe(
                Map.of(A, .01f), Map.of(), Map.of(B, .02f));
        ReagentAttachment full = ReagentComponent.fromCents(Map.of(A, 1L, B, 19_999_999L)).toAttachment();
        Map<ResourceKey<ReagentData>, Long> before = full.snapshotUnits();

        full.resolveReaction(reaction, 200_000f);
        assertEquals(before, full.snapshotUnits());

        // Free one cent of capacity. The deferred reaction now consumes its exact
        // input and admits its complete output, retaining every unrelated reagent.
        assertEquals(1L, full.removeUnits(B, 1L));
        full.resolveReaction(reaction, 200_000f);
        assertEquals(Map.of(B, 20_000_000L), full.snapshotUnits());
        assertFalse(full.snapshotUnits().containsKey(A));
        assertEquals(20_000_000L, full.totalUnits());
    }

    @Test
    void reactionProductBeyondSupportedMaximumIsMutationFree() {
        ReagentAttachment attachment = ReagentComponent.fromCents(
                Map.of(A, 1L, B, ReagentUnits.MAX_CENTS - 1)).toAttachment();
        Map<ResourceKey<ReagentData>, Long> before = attachment.snapshotUnits();
        var reaction = new com.juicyslew.moonstation14.recipe.ReactionRecipe(
                Map.of(A, .01f), Map.of(), Map.of(B, .02f));

        assertDoesNotThrow(() -> attachment.resolveReaction(reaction, 21_474_836f));
        assertEquals(before, attachment.snapshotUnits());
    }
}
