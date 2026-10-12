package com.juicyslew.moonstation14.ms14.ingestion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved local Edible composition used by authoritative ingestion consumers. */
public record IngestionProfile(Edible edible) {
    public enum Kind { FOOD, DRINK }
    public record Edible(int doseCents, int delayTicks, boolean destroyOnEmpty, SourceSolution sourceSolution, Kind kind) {
        public static final Codec<Edible> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 10000).optionalFieldOf("dose_cents", 500).forGetter(Edible::doseCents),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("delay_ticks", 20).forGetter(Edible::delayTicks),
                Codec.BOOL.optionalFieldOf("destroy_on_empty", true).forGetter(Edible::destroyOnEmpty),
                SourceSolution.CODEC.optionalFieldOf("source_solution").forGetter(e -> java.util.Optional.ofNullable(e.sourceSolution())),
                Codec.STRING.xmap(Kind::valueOf, Kind::name).optionalFieldOf("kind", Kind.DRINK).forGetter(Edible::kind)
        ).apply(i, (dose, delay, destroy, source, kind) -> new Edible(dose, delay, destroy, source.orElse(null), kind)));
        public record SourceSolution(int capacityCents, java.util.Map<net.minecraft.resources.ResourceLocation, Long> contentsCents) {
            public static final Codec<SourceSolution> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.intRange(1, 1000000).fieldOf("capacity_cents").forGetter(SourceSolution::capacityCents),
                    Codec.unboundedMap(net.minecraft.resources.ResourceLocation.CODEC, Codec.LONG).fieldOf("contents_cents").forGetter(SourceSolution::contentsCents)
            ).apply(i, SourceSolution::new));
            public SourceSolution {
                contentsCents = java.util.Map.copyOf(contentsCents);
                long total = 0;
                for (long amount : contentsCents.values()) {
                    if (amount < 0 || amount > 1000000) throw new IllegalArgumentException("invalid source solution amount");
                    total = Math.addExact(total, amount);
                }
                if (total > capacityCents) throw new IllegalArgumentException("source solution exceeds capacity");
            }
        }
        public Edible(int doseCents, int delayTicks, boolean destroyOnEmpty) { this(doseCents, delayTicks, destroyOnEmpty, null, Kind.DRINK); }
        public Edible(int doseCents, int delayTicks, boolean destroyOnEmpty, SourceSolution sourceSolution) { this(doseCents, delayTicks, destroyOnEmpty, sourceSolution, Kind.DRINK); }
        public Edible {
            if (doseCents < 1 || doseCents > 10000 || delayTicks < 1 || kind == null) throw new IllegalArgumentException("invalid ingestion dose/delay/kind");
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
