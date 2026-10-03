package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Per-dimension once-only admission and initialization guard for debug ventilation fixtures. */
public final class VentilationFixtureSavedData extends SavedData {
    public static final String DATA_NAME = "moonstation14_ventilation_fixture";
    public static final int MAX_FIXTURES = 16;
    public static final Factory<VentilationFixtureSavedData> FACTORY = new Factory<>(
            VentilationFixtureSavedData::new, VentilationFixtureSavedData::load, null);

    public record Entry(BlockPos origin, UUID scrubberUUID, UUID ventUUID, UUID fixtureUUID, boolean initialized) {
        public Entry {
            origin = new BlockPos(Objects.requireNonNull(origin));
            Objects.requireNonNull(scrubberUUID); Objects.requireNonNull(ventUUID); Objects.requireNonNull(fixtureUUID);
            if (scrubberUUID.equals(ventUUID) || scrubberUUID.equals(fixtureUUID) || ventUUID.equals(fixtureUUID))
                throw new IllegalArgumentException("Fixture device UUIDs must be distinct");
        }
        @Override public BlockPos origin() { return new BlockPos(origin); }
        private Entry initializedCopy() { return new Entry(origin, scrubberUUID, ventUUID, fixtureUUID, true); }
    }

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("origin").forGetter(Entry::origin),
            Codec.STRING.fieldOf("scrubber").xmap(UUID::fromString, UUID::toString).forGetter(Entry::scrubberUUID),
            Codec.STRING.fieldOf("vent").xmap(UUID::fromString, UUID::toString).forGetter(Entry::ventUUID),
            Codec.STRING.fieldOf("fixture").xmap(UUID::fromString, UUID::toString).forGetter(Entry::fixtureUUID),
            Codec.BOOL.fieldOf("initialized").forGetter(Entry::initialized)
    ).apply(instance, Entry::new));
    private static final Codec<List<Entry>> CODEC = ENTRY_CODEC.listOf().validate(entries -> {
        if (entries.size() > MAX_FIXTURES) return com.mojang.serialization.DataResult.error(() -> "Too many ventilation fixtures");
        Set<BlockPos> origins = new HashSet<>(); Set<UUID> ids = new HashSet<>();
        for (Entry entry : entries) {
            if (!origins.add(entry.origin())) return com.mojang.serialization.DataResult.error(() -> "Duplicate fixture origin");
            if (!ids.add(entry.scrubberUUID()) || !ids.add(entry.ventUUID()) || !ids.add(entry.fixtureUUID()))
                return com.mojang.serialization.DataResult.error(() -> "Duplicate fixture device UUID");
        }
        return com.mojang.serialization.DataResult.success(entries);
    });
    private final LinkedHashMap<BlockPos, Entry> entries = new LinkedHashMap<>();
    public VentilationFixtureSavedData() { }
    private VentilationFixtureSavedData(List<Entry> restored) { for (Entry e : restored) entries.put(e.origin(), e); }

    public static Optional<VentilationFixtureSavedData> existing(ServerLevel level) {
        return Optional.ofNullable(Objects.requireNonNull(level).getDataStorage().get(FACTORY, DATA_NAME));
    }
    public static VentilationFixtureSavedData forMutation(ServerLevel level) {
        Objects.requireNonNull(level);
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Fixture saved data mutation requires server thread");
        VentilationFixtureSavedData data = level.getDataStorage().get(FACTORY, DATA_NAME);
        return data != null ? data : level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
    public Optional<Entry> entry(BlockPos origin) { return Optional.ofNullable(entries.get(new BlockPos(Objects.requireNonNull(origin)))); }
    public int count() { return entries.size(); }

    public boolean recordBuilt(BlockPos origin, UUID scrubber, UUID vent, UUID alarm) {
        Entry candidate = new Entry(origin, scrubber, vent, alarm, false);
        if (entries.containsKey(candidate.origin()) || entries.size() >= MAX_FIXTURES) return false;
        for (Entry old : entries.values())
            if (old.scrubberUUID().equals(scrubber) || old.scrubberUUID().equals(vent) || old.scrubberUUID().equals(alarm)
                    || old.ventUUID().equals(scrubber) || old.ventUUID().equals(vent) || old.ventUUID().equals(alarm)
                    || old.fixtureUUID().equals(scrubber) || old.fixtureUUID().equals(vent) || old.fixtureUUID().equals(alarm)) return false;
        entries.put(candidate.origin(), candidate); setDirty(); return true;
    }
    public boolean markInitialized(BlockPos origin, UUID scrubber, UUID vent, UUID alarm) {
        Entry old = entries.get(new BlockPos(Objects.requireNonNull(origin)));
        if (old == null || old.initialized() || !old.scrubberUUID().equals(scrubber)
                || !old.ventUUID().equals(vent) || !old.fixtureUUID().equals(alarm)) return false;
        entries.put(old.origin(), old.initializedCopy()); setDirty(); return true;
    }
    public static VentilationFixtureSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("fixtures", CompoundTag.TAG_LIST)) throw new IllegalArgumentException("Missing ventilation fixture data");
        List<Entry> decoded = CODEC.parse(NbtOps.INSTANCE, tag.get("fixtures"))
                .getOrThrow(message -> new IllegalArgumentException("Invalid ventilation fixtures: " + message));
        return new VentilationFixtureSavedData(decoded);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("fixtures", CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(entries.values()))
                .getOrThrow(message -> new IllegalStateException("Could not encode ventilation fixtures: " + message)));
        return tag;
    }
}
