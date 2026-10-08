package com.juicyslew.moonstation14.ms14.medical;

import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded wire codec shared by common packet DTOs. */
final class HealthAnalyzerPayloadCodec {
    private static final int MAX_NAME = 128, MAX_SPECIES = 64, MAX_STATE = 24, MAX_DAMAGE_TYPES = 32;
    private HealthAnalyzerPayloadCodec() { }
    static void writeState(RegistryFriendlyByteBuf b, HealthAnalyzerWindowState s) {
        b.writeUUID(s.scannerId()); b.writeVarLong(s.epoch()); b.writeUUID(s.actorId()); b.writeVarLong(s.frame());
        b.writeBoolean(s.open()); b.writeBoolean(s.readings().isPresent()); s.readings().ifPresent(r -> writeSnapshot(b, r));
    }
    static HealthAnalyzerWindowState readState(RegistryFriendlyByteBuf b) {
        UUID scanner = b.readUUID(); long epoch = b.readVarLong(); UUID actor = b.readUUID(); long frame = b.readVarLong();
        boolean open = b.readBoolean(); Optional<HealthAnalyzerSnapshot> readings = b.readBoolean() ? Optional.of(readSnapshot(b, scanner, epoch, actor, frame)) : Optional.empty();
        return new HealthAnalyzerWindowState(scanner, epoch, actor, frame, open, readings);
    }
    private static void writeSnapshot(RegistryFriendlyByteBuf b, HealthAnalyzerSnapshot r) {
        b.writeVarInt(r.targetEntityId()); b.writeUUID(r.targetId()); b.writeUtf(r.name(), MAX_NAME); b.writeUtf(r.species(), MAX_SPECIES); b.writeUtf(r.state(), MAX_STATE);
        b.writeBoolean(r.active()); b.writeBoolean(r.inRange()); b.writeBoolean(r.temperatureKelvin().isPresent()); r.temperatureKelvin().ifPresent(b::writeFloat);
        b.writeBoolean(r.usableBloodFraction().isPresent()); r.usableBloodFraction().ifPresent(b::writeFloat);
        b.writeBoolean(r.bleeding()); b.writeBoolean(r.unrevivable()); b.writeFloat(r.totalDamage()); b.writeVarInt(r.damage().size());
        r.damage().forEach((key, value) -> { b.writeUtf(key, 32); b.writeFloat(value); });
    }
    private static HealthAnalyzerSnapshot readSnapshot(RegistryFriendlyByteBuf b, UUID scanner, long epoch, UUID actor, long frame) {
        int entity = b.readVarInt(); UUID target = b.readUUID(); String name = b.readUtf(MAX_NAME), species = b.readUtf(MAX_SPECIES), state = b.readUtf(MAX_STATE);
        boolean active = b.readBoolean(), inRange = b.readBoolean(); Optional<Float> temp = b.readBoolean() ? Optional.of(b.readFloat()) : Optional.empty();
        Optional<Float> blood = b.readBoolean() ? Optional.of(b.readFloat()) : Optional.empty(); boolean bleeding = b.readBoolean(), unrevivable = b.readBoolean(); float total = b.readFloat();
        int count = b.readVarInt(); if (count < 0 || count > MAX_DAMAGE_TYPES) throw new IllegalArgumentException("invalid damage entry count");
        Map<String, Float> damage = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) if (damage.put(b.readUtf(32), b.readFloat()) != null) throw new IllegalArgumentException("duplicate damage key");
        var resolved = HealthAnalyzerSnapshot.readings(scanner, epoch, actor, entity, target, frame,
                name, species, state, active, inRange, temp, blood, bleeding, unrevivable, damage);
        if (Math.abs(resolved.totalDamage() - total) > 0.001f)
            throw new IllegalArgumentException("analyzer damage total does not match typed damage");
        return resolved;
    }
}
