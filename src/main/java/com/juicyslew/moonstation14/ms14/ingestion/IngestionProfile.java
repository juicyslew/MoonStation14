package com.juicyslew.moonstation14.ms14.ingestion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Resolved local Edible composition used by authoritative ingestion consumers. */
public record IngestionProfile(Edible edible) {
    public record Edible(int doseCents, int delayTicks, int forceFeedDelayTicks, boolean destroyOnEmpty, boolean consumeSource,
                         ResourceLocation completionSound, float volumeDb, float pitchVariation) {
        private static final ResourceLocation DEFAULT_SOUND = ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/items/drink");
        public static final Codec<Edible> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 10000).optionalFieldOf("dose_cents", 500).forGetter(Edible::doseCents),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("delay_ticks", 20).forGetter(Edible::delayTicks),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("force_feed_delay_ticks", 40).forGetter(Edible::forceFeedDelayTicks),
                Codec.BOOL.optionalFieldOf("destroy_on_empty", true).forGetter(Edible::destroyOnEmpty),
                Codec.BOOL.optionalFieldOf("consume_source", false).forGetter(Edible::consumeSource),
                ResourceLocation.CODEC.optionalFieldOf("completion_sound", DEFAULT_SOUND).forGetter(Edible::completionSound),
                Codec.floatRange(-60f, 6f).optionalFieldOf("completion_volume_db", -2f).forGetter(Edible::volumeDb),
                Codec.floatRange(0f, 1f).optionalFieldOf("completion_pitch_variation", 0.25f).forGetter(Edible::pitchVariation)
        ).apply(i, Edible::new));
        public Edible {
            if (doseCents < 1 || doseCents > 10000 || delayTicks < 1 || forceFeedDelayTicks < 1
                    || completionSound == null || !Float.isFinite(volumeDb) || volumeDb < -60f || volumeDb > 6f
                    || !Float.isFinite(pitchVariation) || pitchVariation < 0f || pitchVariation > 1f)
                throw new IllegalArgumentException("invalid ingestion dose/delay/audio policy");
        }
        public Edible(int doseCents, int delayTicks, int forceFeedDelayTicks, boolean destroyOnEmpty, boolean consumeSource) {
            this(doseCents, delayTicks, forceFeedDelayTicks, destroyOnEmpty, consumeSource, DEFAULT_SOUND, -2f, .25f);
        }
        public Edible(int doseCents, int delayTicks, boolean destroyOnEmpty) { this(doseCents, delayTicks, 40, destroyOnEmpty, false); }
        public Edible(int doseCents, int delayTicks, boolean destroyOnEmpty, boolean consumeSource) { this(doseCents, delayTicks, 40, destroyOnEmpty, consumeSource); }
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
