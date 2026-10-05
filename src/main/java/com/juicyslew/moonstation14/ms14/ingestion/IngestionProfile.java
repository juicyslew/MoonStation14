package com.juicyslew.moonstation14.ms14.ingestion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved local Edible composition used by authoritative ingestion consumers. */
public record IngestionProfile(Edible edible) {
    public record Edible(int doseCents, int delayTicks, boolean destroyOnEmpty) {
        public static final Codec<Edible> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 10000).optionalFieldOf("dose_cents", 500).forGetter(Edible::doseCents),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("delay_ticks", 20).forGetter(Edible::delayTicks),
                Codec.BOOL.optionalFieldOf("destroy_on_empty", true).forGetter(Edible::destroyOnEmpty)
        ).apply(i, Edible::new));
        public Edible {
            if (doseCents < 1 || doseCents > 10000 || delayTicks < 1) throw new IllegalArgumentException("invalid ingestion dose/delay");
        }
    }
    public static final Codec<IngestionProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Edible.CODEC.fieldOf("edible").forGetter(IngestionProfile::edible)
    ).apply(i, IngestionProfile::new));
    public IngestionProfile { if (edible == null) throw new IllegalArgumentException("edible is required"); }
    public int doseCents() { return edible.doseCents(); }
    public int delayTicks() { return edible.delayTicks(); }
    public boolean destroyOnEmpty() { return edible.destroyOnEmpty(); }
}
