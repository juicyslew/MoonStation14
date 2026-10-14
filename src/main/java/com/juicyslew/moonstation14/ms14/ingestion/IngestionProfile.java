package com.juicyslew.moonstation14.ms14.ingestion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Resolved local Edible composition used by authoritative ingestion consumers. */
public record IngestionProfile(Edible edible) {
    public enum Kind { FOOD, DRINK }

    public record Edible(int doseCents, int delayTicks, int forceFeedDelayTicks, boolean destroyOnEmpty,
                         boolean consumeSource, ResourceLocation completionSound, float volumeDb,
                         float pitchVariation, SourceSolution sourceSolution, Kind kind) {
        private static final ResourceLocation DEFAULT_SOUND = ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/items/drink");
        private static final ResourceLocation FOOD_SOUND = ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/items/eat");
        private static ResourceLocation defaultSound(Kind kind) { return kind == Kind.FOOD ? FOOD_SOUND : DEFAULT_SOUND; }
        public static final Codec<Edible> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 10000).optionalFieldOf("dose_cents", 500).forGetter(Edible::doseCents),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("delay_ticks", 20).forGetter(Edible::delayTicks),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("force_feed_delay_ticks", 40).forGetter(Edible::forceFeedDelayTicks),
                Codec.BOOL.optionalFieldOf("destroy_on_empty", true).forGetter(Edible::destroyOnEmpty),
                Codec.BOOL.optionalFieldOf("consume_source", false).forGetter(Edible::consumeSource),
                ResourceLocation.CODEC.optionalFieldOf("completion_sound").forGetter(e -> java.util.Optional.ofNullable(e.completionSound())),
                Codec.floatRange(-60f, 6f).optionalFieldOf("completion_volume_db", -2f).forGetter(Edible::volumeDb),
                Codec.floatRange(0f, 1f).optionalFieldOf("completion_pitch_variation", .25f).forGetter(Edible::pitchVariation),
                SourceSolution.CODEC.optionalFieldOf("source_solution").forGetter(e -> java.util.Optional.ofNullable(e.sourceSolution())),
                Codec.STRING.xmap(Kind::valueOf, Kind::name).optionalFieldOf("kind", Kind.DRINK).forGetter(Edible::kind)
        ).apply(i, (dose, delay, forceDelay, destroy, consume, sound, volume, pitch, source, kind) ->
                new Edible(dose, delay, forceDelay, destroy, consume,
                        sound.orElseGet(() -> defaultSound(kind)),
                        volume, pitch, source.orElse(null), kind)));

        public record SourceSolution(int capacityCents, java.util.Map<ResourceLocation, Long> contentsCents) {
            public static final Codec<SourceSolution> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.intRange(1, 1000000).fieldOf("capacity_cents").forGetter(SourceSolution::capacityCents),
                    Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).fieldOf("contents_cents").forGetter(SourceSolution::contentsCents)
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

        public Edible(int doseCents, int delayTicks, int forceFeedDelayTicks, boolean destroyOnEmpty, boolean consumeSource) {
            this(doseCents, delayTicks, forceFeedDelayTicks, destroyOnEmpty, consumeSource, DEFAULT_SOUND, -2f, .25f, null, Kind.DRINK);
        }
        public Edible(int doseCents, int delayTicks, boolean destroyOnEmpty) { this(doseCents, delayTicks, 40, destroyOnEmpty, false); }
        public Edible(int doseCents, int delayTicks, boolean destroyOnEmpty, boolean consumeSource) { this(doseCents, delayTicks, 40, destroyOnEmpty, consumeSource); }
        public Edible(int doseCents, int delayTicks, boolean destroyOnEmpty, SourceSolution solution, Kind kind) {
            this(doseCents, delayTicks, 40, destroyOnEmpty, false, defaultSound(kind), -2f, .25f, solution, kind);
        }
        public Edible {
            if (doseCents < 1 || doseCents > 10000 || delayTicks < 1 || forceFeedDelayTicks < 1 || kind == null
                    || completionSound == null || !Float.isFinite(volumeDb) || volumeDb < -60f || volumeDb > 6f
                    || !Float.isFinite(pitchVariation) || pitchVariation < 0f || pitchVariation > 1f)
                throw new IllegalArgumentException("invalid ingestion policy");
        }
    }
    public static final Codec<IngestionProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Edible.CODEC.fieldOf("edible").forGetter(IngestionProfile::edible)
    ).apply(i, IngestionProfile::new));
    public IngestionProfile { if (edible == null) throw new IllegalArgumentException("edible is required"); }
    public int doseCents() { return edible.doseCents(); }
    public int delayTicks() { return edible.delayTicks(); }
    public boolean destroyOnEmpty() { return edible.destroyOnEmpty(); }
    public boolean consumeSource() { return edible.consumeSource(); }
    public int forceFeedDelayTicks() { return edible.forceFeedDelayTicks(); }
    public ResourceLocation completionSound() { return edible.completionSound(); }
    public float completionVolumeDb() { return edible.volumeDb(); }
    public float completionPitchVariation() { return edible.pitchVariation(); }
}

