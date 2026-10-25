package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Opaque identity for a botany seed. Serialized values are snapshots; decoding never reconnects aliases. */
public final class PlantSeedReference {
    public static final Codec<PlantSeedReference> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(PlantSeedReference::id),
            GeneticValue.CODEC.fieldOf("genetics").forGetter(PlantSeedReference::current),
            Codec.BOOL.fieldOf("unique").forGetter(PlantSeedReference::unique)
    ).apply(i, PlantSeedReference::detached));
    public static final StreamCodec<ByteBuf, PlantSeedReference> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    private final UUID id;
    private GeneticValue current;
    private boolean unique;

    public PlantSeedReference(GeneticValue current, boolean unique) { this(UUID.randomUUID(), current, unique); }
    private PlantSeedReference(UUID id, GeneticValue current, boolean unique) {
        if (id == null || current == null) throw new IllegalArgumentException("invalid seed reference");
        this.id = id; this.current = current; this.unique = unique;
    }
    private static PlantSeedReference detached(UUID id, GeneticValue value, boolean unique) {
        return new PlantSeedReference(id, value, unique);
    }
    public UUID id() { return id; }
    public GeneticValue current() { return current; }
    public boolean unique() { return unique; }
    void update(GeneticValue value) { current = value; }
    void unique(boolean value) { unique = value; }
    public PlantSeedReference ensureUnique() {
        return unique ? this : new PlantSeedReference(
                new GeneticValue(current.species(), current.profile().withDefaultScreamSound(), current.mutations()), true);
    }
    @Override public boolean equals(Object other) { return other instanceof PlantSeedReference ref && id.equals(ref.id); }
    @Override public int hashCode() { return id.hashCode(); }
}
