package com.juicyslew.moonstation14.ms14.doors.ui;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Client-safe state. Rows contain cosmetic data and opaque IDs, never wire functions. */
public record AirlockWireSnapshot(int containerId, UUID session, long requestId, boolean accepted, Rejection rejection,
                                  long rootRevision, boolean supplyPowered,
                                  AirlockWireIndicators.Light powerIndicator, AirlockWireIndicators.Light boltIndicator,
                                   AirlockWireIndicators.Light boltLightsIndicator, AirlockWireIndicators.Light timingIndicator,
                                   AirlockWireIndicators.Light safetyIndicator,
                                   AirlockWireIndicators.Light aiInteractIndicator, AirlockWireIndicators.Light logIndicator,
                                  boolean cuttingTool, boolean pulsingTool,
                                  List<Row> rows) implements CustomPacketPayload {
    public record Row(int id, int argb, String letter, boolean cut) { }
    public enum Rejection { NONE, STUNNED, INVALID_ACTION, REFRESH }
    public AirlockWireSnapshot(int containerId, UUID session, long requestId, boolean accepted, long rootRevision,
                               boolean supplyPowered, AirlockWireIndicators.Light powerIndicator,
                               AirlockWireIndicators.Light boltIndicator, AirlockWireIndicators.Light boltLightsIndicator,
                               AirlockWireIndicators.Light timingIndicator, AirlockWireIndicators.Light safetyIndicator,
                               AirlockWireIndicators.Light aiInteractIndicator, AirlockWireIndicators.Light logIndicator,
                               boolean cuttingTool, boolean pulsingTool, List<Row> rows) {
        this(containerId,session,requestId,accepted,accepted?Rejection.NONE:Rejection.REFRESH,rootRevision,supplyPowered,
                powerIndicator,boltIndicator,boltLightsIndicator,timingIndicator,safetyIndicator,aiInteractIndicator,logIndicator,cuttingTool,pulsingTool,rows);
    }
    /** Compatibility for producers not yet migrated to the continuity indicators. */
    public AirlockWireSnapshot(int containerId, UUID session, long requestId, boolean accepted, long rootRevision,
                               boolean supplyPowered, AirlockWireIndicators.Light powerIndicator,
                               AirlockWireIndicators.Light boltIndicator, AirlockWireIndicators.Light boltLightsIndicator,
                               AirlockWireIndicators.Light timingIndicator, AirlockWireIndicators.Light safetyIndicator,
                               boolean cuttingTool, boolean pulsingTool, List<Row> rows) {
        this(containerId, session, requestId, accepted, rootRevision, supplyPowered, powerIndicator, boltIndicator,
                boltLightsIndicator, timingIndicator, safetyIndicator, AirlockWireIndicators.Light.OFF,
                AirlockWireIndicators.Light.OFF, cuttingTool, pulsingTool, rows);
    }
    public static final Type<AirlockWireSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "airlock_wire_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AirlockWireSnapshot> STREAM_CODEC = StreamCodec.of(
            (b, v) -> {
                b.writeVarInt(v.containerId); b.writeUUID(v.session); b.writeVarLong(v.requestId); b.writeBoolean(v.accepted); b.writeEnum(v.rejection);
                b.writeVarLong(v.rootRevision); b.writeBoolean(v.supplyPowered);
                b.writeEnum(v.powerIndicator); b.writeEnum(v.boltIndicator); b.writeEnum(v.boltLightsIndicator);
                b.writeEnum(v.timingIndicator); b.writeEnum(v.safetyIndicator);
                b.writeEnum(v.aiInteractIndicator); b.writeEnum(v.logIndicator);
                b.writeBoolean(v.cuttingTool); b.writeBoolean(v.pulsingTool);
                b.writeVarInt(v.rows.size());
                for (var row : v.rows) { b.writeVarInt(row.id); b.writeInt(row.argb); b.writeUtf(row.letter, 4); b.writeBoolean(row.cut); }
            },
            b -> {
                int menu = b.readVarInt(); UUID session = b.readUUID(); long request = b.readVarLong(); boolean accepted = b.readBoolean(); Rejection rejection=b.readEnum(Rejection.class);
                long revision = b.readVarLong(); boolean supplyPowered = b.readBoolean();
                var power = b.readEnum(AirlockWireIndicators.Light.class);
                var bolts = b.readEnum(AirlockWireIndicators.Light.class);
                var lights = b.readEnum(AirlockWireIndicators.Light.class);
                var timing = b.readEnum(AirlockWireIndicators.Light.class);
                var safety = b.readEnum(AirlockWireIndicators.Light.class);
                var aiInteract = b.readEnum(AirlockWireIndicators.Light.class);
                var log = b.readEnum(AirlockWireIndicators.Light.class);
                boolean cutting = b.readBoolean(), pulsing = b.readBoolean();
                int count = b.readVarInt(); if (count < 0 || count > AirlockState.Wire.values().length) throw new IllegalArgumentException("invalid wire row count");
                var rows = new java.util.ArrayList<Row>(count);
                for (int i = 0; i < count; i++) rows.add(new Row(b.readVarInt(), b.readInt(), b.readUtf(4), b.readBoolean()));
                return new AirlockWireSnapshot(menu, session, request, accepted, rejection, revision, supplyPowered,
                        power, bolts, lights, timing, safety, aiInteract, log, cutting, pulsing, rows);
            });
    public AirlockWireSnapshot {
        if (containerId < 0 || session == null || requestId < 0 || rootRevision < 0 || powerIndicator == null
                || boltIndicator == null || boltLightsIndicator == null || timingIndicator == null || safetyIndicator == null
                || aiInteractIndicator == null || logIndicator == null
                 || rejection == null || rows == null || rows.size() > AirlockState.Wire.values().length) throw new IllegalArgumentException("invalid airlock wire snapshot");
        rows = List.copyOf(rows);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
