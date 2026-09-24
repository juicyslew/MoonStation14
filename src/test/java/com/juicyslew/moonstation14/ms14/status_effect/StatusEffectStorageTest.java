package com.juicyslew.moonstation14.ms14.status_effect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusEffectStorageTest {
    private static final ResourceKey<StatusEffectData> KEY = ModStatusEffects.createKey("storage_test");

    @Test
    void strictInstanceAndComponentJsonRoundTrip() {
        List<StatusEffectInstance> instances = List.of(
                StatusEffectInstance.pendingFinite(3, 8),
                StatusEffectInstance.activeFinite(8),
                StatusEffectInstance.activePermanent()
        );
        for (StatusEffectInstance expected : instances) {
            JsonElement encoded = StatusEffectInstance.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
            assertEquals(expected, StatusEffectInstance.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
            assertTrue(encoded.getAsJsonObject().has("delay_ticks"));
            assertTrue(encoded.getAsJsonObject().has("state"));
            assertEquals(expected.isPermanent(), !encoded.getAsJsonObject().has("remaining_ticks"));
        }

        Map<ResourceKey<StatusEffectData>, StatusEffectInstance> values = Map.of(
                KEY, instances.get(0));
        JsonElement encoded = StatusEffectComponent.CODEC.encodeStart(JsonOps.INSTANCE,
                new StatusEffectComponent(values)).getOrThrow();
        assertEquals(new StatusEffectComponent(values),
                StatusEffectComponent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void payloadJsonIsDiscriminatedAndMissingPayloadUsesTheMinimalDefault() {
        StatusEffectInstance noPayload = StatusEffectInstance.activePermanent();
        JsonObject minimal = StatusEffectInstance.CODEC.encodeStart(JsonOps.INSTANCE, noPayload)
                .getOrThrow().getAsJsonObject();
        assertFalse(minimal.has("payload"));
        assertEquals(noPayload, StatusEffectInstance.CODEC.parse(JsonOps.INSTANCE, minimal).getOrThrow());

        StatusEffectInstance jitter = StatusEffectInstance.activeFinite(12,
                new StatusEffectPayload.Jitter(25f, 7f));
        JsonObject encoded = StatusEffectInstance.CODEC.encodeStart(JsonOps.INSTANCE, jitter)
                .getOrThrow().getAsJsonObject();
        assertEquals("jitter", encoded.getAsJsonObject("payload").get("type").getAsString());
        assertEquals(jitter, StatusEffectInstance.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());

        StatusEffectInstance movement = StatusEffectInstance.activeFinite(12,
                new StatusEffectPayload.MovementSpeedModifier(.65f));
        JsonObject movementEncoded = StatusEffectInstance.CODEC.encodeStart(JsonOps.INSTANCE, movement)
                .getOrThrow().getAsJsonObject();
        assertEquals("movement_speed", movementEncoded.getAsJsonObject("payload").get("type").getAsString());
        assertEquals(movement, StatusEffectInstance.CODEC.parse(JsonOps.INSTANCE, movementEncoded).getOrThrow());

        JsonObject unknownType = encoded.deepCopy();
        unknownType.getAsJsonObject("payload").addProperty("type", "other");
        assertTrue(StatusEffectInstance.CODEC.parse(JsonOps.INSTANCE, unknownType).error().isPresent());

        JsonObject unknownField = encoded.deepCopy();
        unknownField.getAsJsonObject("payload").addProperty("extra", 1);
        assertTrue(StatusEffectInstance.CODEC.parse(JsonOps.INSTANCE, unknownField).error().isPresent());
    }

    @Test
    void persistentCodecsRoundTripThroughNbtForFiniteAndPermanentStates() {
        List<StatusEffectInstance> instances = List.of(
                StatusEffectInstance.pendingFinite(3, 8),
                StatusEffectInstance.activeFinite(8),
                StatusEffectInstance.activeFinite(12, new StatusEffectPayload.Jitter(25f, 7f)),
                StatusEffectInstance.activeFinite(12, new StatusEffectPayload.MovementSpeedModifier(.65f)),
                StatusEffectInstance.activePermanent(),
                StatusEffectInstance.pendingPermanent(3));
        for (StatusEffectInstance expected : instances) {
            var encoded = StatusEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, expected).getOrThrow();
            assertEquals(expected, StatusEffectInstance.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
        }

        Map<ResourceKey<StatusEffectData>, StatusEffectInstance> values = new LinkedHashMap<>();
        values.put(KEY, StatusEffectInstance.pendingFinite(2, 4));
        values.put(ModStatusEffects.createKey("active"), StatusEffectInstance.activeFinite(6));
        values.put(ModStatusEffects.createKey("jitter_payload"),
                StatusEffectInstance.activeFinite(12, new StatusEffectPayload.Jitter(25f, 7f)));
        values.put(ModStatusEffects.createKey("permanent"), StatusEffectInstance.activePermanent());
        StatusEffectComponent component = new StatusEffectComponent(values);
        var componentNbt = StatusEffectComponent.CODEC.encodeStart(NbtOps.INSTANCE, component).getOrThrow();
        assertEquals(component, StatusEffectComponent.CODEC.parse(NbtOps.INSTANCE, componentNbt).getOrThrow());

        StatusEffectAttachment attachment = new StatusEffectAttachment(component);
        var attachmentNbt = StatusEffectAttachment.CODEC.encodeStart(NbtOps.INSTANCE, attachment).getOrThrow();
        assertEquals(attachment, StatusEffectAttachment.CODEC.parse(NbtOps.INSTANCE, attachmentNbt).getOrThrow());
    }

    @Test
    void oldFloatMapDataIsRejectedAndInvalidInstancesAreRejected() {
        JsonElement oldMap = JsonParser.parseString("{\"moonstation14:storage_test\": 1.5}");
        assertTrue(StatusEffectComponent.CODEC.parse(JsonOps.INSTANCE, oldMap).error().isPresent());

        assertTrue(StatusEffectInstance.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"delay_ticks\": 0, \"remaining_ticks\": 1.5, \"state\": \"active\"}"))
                .error().isPresent());
        assertTrue(StatusEffectInstance.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"delay_ticks\": 2, \"state\": \"active\"}"))
                .error().isPresent());
        assertTrue(StatusEffectInstance.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"delay_ticks\": 0, \"state\": \"active\", \"delya_ticks\": 1}"))
                .error().isPresent());

        CompoundTag unknown = new CompoundTag();
        unknown.putInt("delay_ticks", 0);
        unknown.putString("state", "active");
        unknown.putInt("delya_ticks", 1);
        assertTrue(StatusEffectInstance.CODEC.parse(NbtOps.INSTANCE, unknown).error().isPresent());
    }

    @Test
    void malformedPersistentStatusKeysReturnCodecErrorsForJsonAndNbt() {
        JsonObject json = new JsonObject();
        json.add("moonstation14:not a valid id", StatusEffectInstance.CODEC.encodeStart(
                JsonOps.INSTANCE, StatusEffectInstance.activePermanent()).getOrThrow());
        assertTrue(assertDoesNotThrow(() -> StatusEffectComponent.CODEC.parse(JsonOps.INSTANCE, json))
                .error().isPresent());

        CompoundTag nbt = new CompoundTag();
        nbt.put("moonstation14:not a valid id", StatusEffectInstance.CODEC.encodeStart(
                NbtOps.INSTANCE, StatusEffectInstance.activePermanent()).getOrThrow());
        assertTrue(assertDoesNotThrow(() -> StatusEffectComponent.CODEC.parse(NbtOps.INSTANCE, nbt))
                .error().isPresent());
    }

    @Test
    void canonicalPersistentStatusKeyAliasesAreRejectedWithoutMapOverwrite() {
        JsonObject json = new JsonObject();
        var value = StatusEffectInstance.CODEC.encodeStart(JsonOps.INSTANCE,
                StatusEffectInstance.activePermanent()).getOrThrow();
        json.add("jitter", value);
        json.add("moonstation14:jitter", value.deepCopy());
        assertTrue(assertDoesNotThrow(() -> StatusEffectComponent.CODEC.parse(JsonOps.INSTANCE, json))
                .error().isPresent());

        CompoundTag nbt = new CompoundTag();
        var nbtValue = StatusEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE,
                StatusEffectInstance.activePermanent()).getOrThrow();
        nbt.put("jitter", nbtValue);
        nbt.put("moonstation14:jitter", nbtValue.copy());
        assertTrue(assertDoesNotThrow(() -> StatusEffectComponent.CODEC.parse(NbtOps.INSTANCE, nbt))
                .error().isPresent());
    }

    @Test
    void persistentComponentCodecRejectsOversizedMapsOnDecodeAndEncode() {
        Map<ResourceKey<StatusEffectData>, StatusEffectInstance> oversized = new LinkedHashMap<>();
        for (int index = 0; index <= StatusEffectComponent.MAX_ENTRIES; index++) {
            oversized.put(ModStatusEffects.createKey("persistent_status_" + index),
                    StatusEffectInstance.activePermanent());
        }
        StatusEffectComponent component = new StatusEffectComponent(oversized);
        assertTrue(StatusEffectComponent.CODEC.encodeStart(NbtOps.INSTANCE, component).error().isPresent());

        JsonObject json = new JsonObject();
        CompoundTag nbt = new CompoundTag();
        for (int index = 0; index <= StatusEffectComponent.MAX_ENTRIES; index++) {
            var instance = StatusEffectInstance.CODEC.encodeStart(JsonOps.INSTANCE,
                    StatusEffectInstance.activePermanent()).getOrThrow();
            json.add("moonstation14:persistent_status_" + index, instance);
            Tag instanceNbt = StatusEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE,
                    StatusEffectInstance.activePermanent()).getOrThrow();
            nbt.put("moonstation14:persistent_status_" + index, instanceNbt);
        }
        assertTrue(StatusEffectComponent.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        assertTrue(StatusEffectComponent.CODEC.parse(NbtOps.INSTANCE, nbt).error().isPresent());
    }

    @Test
    void componentAndAttachmentCopiesAndQueriesAreImmutable() {
        Map<ResourceKey<StatusEffectData>, StatusEffectInstance> source = new LinkedHashMap<>();
        source.put(KEY, StatusEffectInstance.activeFinite(4));
        StatusEffectComponent component = new StatusEffectComponent(source);
        source.clear();
        assertTrue(component.contents().containsKey(KEY));
        assertThrows(UnsupportedOperationException.class,
                () -> component.contents().put(KEY, StatusEffectInstance.activePermanent()));

        StatusEffectAttachment attachment = new StatusEffectAttachment(component);
        assertEquals(Optional.of(StatusEffectInstance.activeFinite(4)), attachment.get(KEY));
        assertTrue(attachment.contains(KEY));
        assertTrue(attachment.isActive(KEY));
        assertThrows(UnsupportedOperationException.class, () -> attachment.getMap().clear());

        StatusEffectComponent detached = attachment.toComponent();
        attachment.apply(KEY, StatusEffectOperation.SET, OptionalInt.of(2), 0);
        assertEquals(StatusEffectInstance.activeFinite(4), detached.contents().get(KEY));
    }

    @Test
    void attachmentCentralMutationUsesReducerAndDetachesState() {
        StatusEffectAttachment attachment = new StatusEffectAttachment();
        assertEquals(StatusEffectChangeKind.CREATED,
                attachment.apply(KEY, StatusEffectOperation.SET, OptionalInt.of(5), 0).kind());
        assertEquals(StatusEffectChangeKind.CHANGED,
                attachment.apply(KEY, StatusEffectOperation.ADD, OptionalInt.of(2), 0).kind());
        assertEquals(StatusEffectInstance.activeFinite(7), attachment.get(KEY).orElseThrow());
        assertEquals(StatusEffectChangeKind.CHANGED,
                attachment.apply(KEY, StatusEffectOperation.UPDATE, OptionalInt.of(8), 0).kind());
        assertEquals(StatusEffectChangeKind.CHANGED,
                attachment.apply(KEY, StatusEffectOperation.REMOVE, OptionalInt.of(2), 0).kind());
        assertEquals(StatusEffectInstance.activeFinite(6), attachment.get(KEY).orElseThrow());
        assertEquals(StatusEffectChangeKind.REMOVED,
                attachment.apply(KEY, StatusEffectOperation.REMOVE, OptionalInt.of(6), 0).kind());
        assertFalse(attachment.contains(KEY));
        assertEquals(StatusEffectChangeKind.UNCHANGED,
                attachment.apply(KEY, StatusEffectOperation.REMOVE, OptionalInt.of(1), 0).kind());
    }

    @Test
    void oneTickIsCanonicalAndRemovesExactlyAtBoundaries() {
        ResourceKey<StatusEffectData> alpha = ModStatusEffects.createKey("alpha");
        ResourceKey<StatusEffectData> zeta = ModStatusEffects.createKey("zeta");
        Map<ResourceKey<StatusEffectData>, StatusEffectInstance> source = new LinkedHashMap<>();
        source.put(zeta, StatusEffectInstance.activeFinite(2));
        source.put(KEY, StatusEffectInstance.pendingFinite(1, 1));
        source.put(alpha, StatusEffectInstance.activeFinite(1));
        StatusEffectAttachment attachment = new StatusEffectAttachment(source);

        List<StatusEffectTickChange> first = attachment.advanceOneTick();
        assertEquals(List.of(alpha, KEY, zeta), first.stream().map(StatusEffectTickChange::key).toList());
        assertEquals(StatusEffectTransition.EXPIRED, first.get(0).transition());
        assertEquals(StatusEffectTransition.ACTIVATED, first.get(1).transition());
        assertEquals(StatusEffectTransition.NONE, first.get(2).transition());
        assertFalse(attachment.contains(alpha));
        assertTrue(attachment.isActive(KEY));
        assertEquals(StatusEffectInstance.activeFinite(1), attachment.get(zeta).orElseThrow());

        List<StatusEffectTickChange> second = attachment.advanceOneTick();
        assertEquals(StatusEffectTransition.EXPIRED, second.get(0).transition());
        assertEquals(StatusEffectTransition.EXPIRED, second.get(1).transition());
        assertFalse(attachment.contains(zeta));
        assertFalse(attachment.contains(KEY));
    }

    @Test
    void tickReportRequestsSynchronizationOnlyForLifecycleTransitions() {
        StatusEffectAttachment countdown = new StatusEffectAttachment(
                Map.of(KEY, StatusEffectInstance.activeFinite(3)));
        StatusEffectTickReport countdownReport = StatusEffectSystem.advanceOneTick(countdown);
        assertFalse(countdownReport.synchronizationRequested());
        assertEquals(StatusEffectTransition.NONE, countdownReport.changes().get(0).transition());

        StatusEffectAttachment activation = new StatusEffectAttachment(
                Map.of(KEY, StatusEffectInstance.pendingPermanent(1)));
        assertTrue(StatusEffectSystem.advanceOneTick(activation).synchronizationRequested());

        StatusEffectAttachment expiry = new StatusEffectAttachment(
                Map.of(KEY, StatusEffectInstance.activeFinite(1)));
        assertTrue(StatusEffectSystem.advanceOneTick(expiry).synchronizationRequested());
    }

    @Test
    void boundedNetworkCodecRoundTripsAndRejectsMalformedCountsAndInstances() {
        RegistryFriendlyByteBuf instanceBuffer = buffer();
        StatusEffectInstance.STREAM_CODEC.encode(instanceBuffer, StatusEffectInstance.pendingFinite(2, 9));
        assertEquals(StatusEffectInstance.pendingFinite(2, 9),
                StatusEffectInstance.STREAM_CODEC.decode(instanceBuffer));
        instanceBuffer.release();

        RegistryFriendlyByteBuf jitterBuffer = buffer();
        StatusEffectInstance jitter = StatusEffectInstance.activeFinite(4,
                new StatusEffectPayload.Jitter(30f, 8f));
        StatusEffectInstance.STREAM_CODEC.encode(jitterBuffer, jitter);
        assertEquals(jitter, StatusEffectInstance.STREAM_CODEC.decode(jitterBuffer));
        jitterBuffer.release();

        RegistryFriendlyByteBuf movementBuffer = buffer();
        StatusEffectInstance movement = StatusEffectInstance.activeFinite(4,
                new StatusEffectPayload.MovementSpeedModifier(1.25f));
        StatusEffectInstance.STREAM_CODEC.encode(movementBuffer, movement);
        assertEquals(movement, StatusEffectInstance.STREAM_CODEC.decode(movementBuffer));
        movementBuffer.release();

        RegistryFriendlyByteBuf componentBuffer = buffer();
        StatusEffectComponent expected = new StatusEffectComponent(Map.of(KEY, StatusEffectInstance.activePermanent()));
        StatusEffectComponent.STREAM_CODEC.encode(componentBuffer, expected);
        assertEquals(expected, StatusEffectComponent.STREAM_CODEC.decode(componentBuffer));
        componentBuffer.release();

        RegistryFriendlyByteBuf payloadComponentBuffer = buffer();
        StatusEffectComponent payloadComponent = new StatusEffectComponent(Map.of(KEY,
                StatusEffectInstance.activeFinite(7, new StatusEffectPayload.Jitter(12f, 4f))));
        StatusEffectComponent.STREAM_CODEC.encode(payloadComponentBuffer, payloadComponent);
        assertEquals(payloadComponent, StatusEffectComponent.STREAM_CODEC.decode(payloadComponentBuffer));
        payloadComponentBuffer.release();

        RegistryFriendlyByteBuf oversized = buffer();
        oversized.writeVarInt(StatusEffectComponent.MAX_NETWORK_ENTRIES + 1);
        assertThrows(RuntimeException.class, () -> StatusEffectComponent.STREAM_CODEC.decode(oversized));
        oversized.release();

        RegistryFriendlyByteBuf negativeDelay = buffer();
        negativeDelay.writeVarInt(-1);
        assertThrows(IllegalArgumentException.class, () -> StatusEffectInstance.STREAM_CODEC.decode(negativeDelay));
        negativeDelay.release();

        RegistryFriendlyByteBuf unknownPayload = buffer();
        unknownPayload.writeVarInt(0);
        unknownPayload.writeBoolean(false);
        unknownPayload.writeVarInt(1);
        unknownPayload.writeVarInt(2);
        assertThrows(RuntimeException.class, () -> StatusEffectInstance.STREAM_CODEC.decode(unknownPayload));
        unknownPayload.release();

        RegistryFriendlyByteBuf invalidJitterPayload = buffer();
        invalidJitterPayload.writeVarInt(1);
        invalidJitterPayload.writeFloat(Float.NaN);
        invalidJitterPayload.writeFloat(4f);
        assertThrows(IllegalArgumentException.class,
                () -> StatusEffectPayload.STREAM_CODEC.decode(invalidJitterPayload));
        invalidJitterPayload.release();

        RegistryFriendlyByteBuf invalidMovementPayload = buffer();
        invalidMovementPayload.writeVarInt(2);
        invalidMovementPayload.writeFloat(-1f);
        assertThrows(IllegalArgumentException.class,
                () -> StatusEffectPayload.STREAM_CODEC.decode(invalidMovementPayload));
        invalidMovementPayload.release();

        RegistryFriendlyByteBuf outOfRangeJitterPayload = buffer();
        outOfRangeJitterPayload.writeVarInt(1);
        outOfRangeJitterPayload.writeFloat(301f);
        outOfRangeJitterPayload.writeFloat(4f);
        assertThrows(IllegalArgumentException.class,
                () -> StatusEffectPayload.STREAM_CODEC.decode(outOfRangeJitterPayload));
        outOfRangeJitterPayload.release();

        Map<ResourceKey<StatusEffectData>, StatusEffectInstance> oversizedMap = new LinkedHashMap<>();
        for (int index = 0; index <= StatusEffectComponent.MAX_NETWORK_ENTRIES; index++) {
            oversizedMap.put(ModStatusEffects.createKey("status_" + index), StatusEffectInstance.activePermanent());
        }
        RegistryFriendlyByteBuf oversizedEncode = buffer();
        assertThrows(RuntimeException.class,
                () -> StatusEffectComponent.STREAM_CODEC.encode(oversizedEncode,
                        new StatusEffectComponent(oversizedMap)));
        oversizedEncode.release();
    }

    @Test
    void secondsToTicksUsesCeilingAndRejectsInvalidValues() {
        assertEquals(1, StatusEffectSystem.secondsToTicks(.001f));
        assertEquals(1, StatusEffectSystem.secondsToTicks(.05f));
        assertEquals(2, StatusEffectSystem.secondsToTicks(.0501f));
        assertEquals(20, StatusEffectSystem.secondsToTicks(1f));
        assertEquals(21, StatusEffectSystem.secondsToTicks(1.0001f));

        for (float invalid : new float[]{0f, -0f, -1f, Float.NaN, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> StatusEffectSystem.secondsToTicks(invalid));
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
