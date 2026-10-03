package com.juicyslew.moonstation14.ms14.sealedwall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeChunkData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Sparse persisted generation identities for explicitly registered sealed-wall hosts. */
public final class SealedWallHostGenerations {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(UUID.fromString(value)); }
        catch (IllegalArgumentException exception) { return DataResult.error(() -> "invalid UUID"); }
    }, UUID::toString);
    private static final Codec<Record> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(Record::position),
            Codec.STRING.fieldOf("state").forGetter(record -> record instanceof Invalidated ? "invalidated" : "active"),
            UUID_CODEC.optionalFieldOf("generation").forGetter(record -> record instanceof Active active
                    ? Optional.of(active.generation()) : Optional.empty())
    ).apply(instance, SealedWallHostGenerations::decode));
    private static final Codec<SealedWallHostGenerations> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(data -> 1),
             RECORD_CODEC.listOf().fieldOf("records").forGetter(SealedWallHostGenerations::records)
    ).apply(instance, SealedWallHostGenerations::decodeAll));
    public static final Codec<SealedWallHostGenerations> CODEC = STRUCTURAL_CODEC.flatXmap(
            data -> data.error == null ? DataResult.success(data) : DataResult.error(() -> data.error), DataResult::success);

    private final Map<Long, Record> records = new HashMap<>();
    private String error;

    public Optional<UUID> active(long position) {
        Record record = records.get(position);
        return record instanceof Active active ? Optional.of(active.generation()) : Optional.empty();
    }

    public boolean hasRecord(long position) { return records.containsKey(position); }

    /** Registers an admission only when no active or invalidated identity already owns the position. */
    public boolean registerIfAbsent(long position, UUID generation) {
        if (generation == null) throw new NullPointerException("generation");
        if (records.containsKey(position) || records.size() >= PipeChunkData.MAX_RECORDS) return false;
        records.put(position, new Active(position, generation));
        return true;
    }

    /** Removes only an active registration created by the caller's just-failed admission. */
    public boolean removeActiveExact(long position, UUID generation) {
        Record record = records.get(position);
        if (record instanceof Active active && active.generation().equals(generation)) {
            records.remove(position);
            return true;
        }
        return false;
    }

    public boolean register(long position, UUID generation) {
        if (generation == null) throw new NullPointerException("generation");
        if (!records.containsKey(position) && records.size() >= PipeChunkData.MAX_RECORDS) return false;
        Record before = records.put(position, new Active(position, generation));
        return !new Active(position, generation).equals(before);
    }

    /** Retains an explicit tombstone so stale registrations cannot become live again. */
    public boolean invalidate(long position) {
        if (!records.containsKey(position) && records.size() >= PipeChunkData.MAX_RECORDS) return false;
        Record before = records.put(position, new Invalidated(position));
        return !(before instanceof Invalidated);
    }

    /** Invalidates only a known identity; unrelated writes must not create persisted tombstones. */
    public boolean invalidateExisting(long position) {
        if (!records.containsKey(position)) return false;
        return invalidate(position);
    }

    public boolean hasPersistedState() { return !records.isEmpty(); }
    private List<Record> records() { return List.copyOf(records.values()); }

    private static Record decode(long position, String state, Optional<UUID> generation) {
        return switch (state) {
            case "active" -> new Active(position, generation.orElseThrow(() -> new IllegalArgumentException("active record lacks generation")));
            case "invalidated" -> {
                if (generation.isPresent()) throw new IllegalArgumentException("invalidated record has generation");
                yield new Invalidated(position);
            }
            default -> throw new IllegalArgumentException("unknown sealed-wall record state: " + state);
        };
    }

    private static SealedWallHostGenerations decodeAll(int version, List<Record> values) {
        SealedWallHostGenerations result = new SealedWallHostGenerations();
        if (version != 1) result.error = "unsupported sealed-wall host generation version: " + version;
        if (values.size() > PipeChunkData.MAX_RECORDS) result.error = "sealed-wall host generation record limit exceeded";
        for (Record record : values) {
            if (result.records.putIfAbsent(record.position(), record) != null) {
                result.error = "duplicate sealed-wall position: " + record.position();
                break;
            }
        }
        return result;
    }

    private sealed interface Record permits Active, Invalidated { long position(); }
    private record Active(long position, UUID generation) implements Record { }
    private record Invalidated(long position) implements Record { }
}
