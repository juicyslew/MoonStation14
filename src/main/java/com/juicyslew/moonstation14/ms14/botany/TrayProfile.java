package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** Resolved PlantHolder/soil composition for an ordinary hydroponics tray. */
public record TrayProfile(int pollDelayTicks, int cycleDelayTicks, float initialWater, float initialNutrition,
                          boolean drawWarnings, int soilCapacityCents, int refillMaximumCents, Optional<WateringSound> wateringSound) {
    public static final Codec<TrayProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 1_000_000).fieldOf("poll_delay_ticks").forGetter(TrayProfile::pollDelayTicks),
            Codec.intRange(1, 1_000_000).fieldOf("cycle_delay_ticks").forGetter(TrayProfile::cycleDelayTicks),
            Codec.floatRange(0, 100).fieldOf("initial_water").forGetter(TrayProfile::initialWater),
            Codec.floatRange(0, 100).fieldOf("initial_nutrition").forGetter(TrayProfile::initialNutrition),
            Codec.BOOL.fieldOf("draw_warnings").forGetter(TrayProfile::drawWarnings),
            Codec.intRange(1, 100_000_000).fieldOf("soil_capacity_cents").forGetter(TrayProfile::soilCapacityCents),
            Codec.intRange(1, 100_000_000).fieldOf("refill_maximum_cents").forGetter(TrayProfile::refillMaximumCents),
            WateringSound.CODEC.optionalFieldOf("watering_sound").forGetter(TrayProfile::wateringSound)
    ).apply(i, TrayProfile::new));

    public TrayProfile(int pollDelayTicks, int cycleDelayTicks, float initialWater, float initialNutrition,
                       boolean drawWarnings, int soilCapacityCents, int refillMaximumCents) {
        this(pollDelayTicks, cycleDelayTicks, initialWater, initialNutrition, drawWarnings, soilCapacityCents, refillMaximumCents, Optional.empty());
    }

    public record WateringSound(ResourceLocation soundResourceId, float volumeDb, float pitchVariation) {
        public static final Codec<WateringSound> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("sound_resource_id").forGetter(WateringSound::soundResourceId),
                Codec.floatRange(-120, 24).fieldOf("volume_db").forGetter(WateringSound::volumeDb),
                Codec.floatRange(0, 1).fieldOf("pitch_variation").forGetter(WateringSound::pitchVariation)
        ).apply(i, WateringSound::new));
        public WateringSound {
            if (soundResourceId == null || !Float.isFinite(volumeDb) || !Float.isFinite(pitchVariation) || pitchVariation < 0 || pitchVariation > 1)
                throw new IllegalArgumentException("invalid watering sound");
        }
    }

    public TrayProfile {
        wateringSound = wateringSound == null ? Optional.empty() : wateringSound;
        if (!Float.isFinite(initialWater) || !Float.isFinite(initialNutrition)
                || pollDelayTicks <= 0 || cycleDelayTicks <= 0 || soilCapacityCents <= 0
                || refillMaximumCents <= 0 || refillMaximumCents > soilCapacityCents)
            throw new IllegalArgumentException("invalid tray profile");
    }
}
