package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Resolved, typed SS14-derived plant data; visual keys are metadata, not geometry. */
public record SeedProfile(ResourceLocation packet, ResourceLocation product, String visualKey,
                          Biology biology, Lifecycle lifecycle, Map<ResourceLocation, Chemical> chemicals) {
    public enum Harvest { NO_REPEAT, REPEAT }
    public record Chemical(float min, float max, float potencyDivisor) {
        public static final Codec<Chemical> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 10000).fieldOf("min").forGetter(Chemical::min),
                Codec.floatRange(0, 10000).fieldOf("max").forGetter(Chemical::max),
                Codec.floatRange(Float.MIN_NORMAL, 10000).fieldOf("potency_divisor").forGetter(Chemical::potencyDivisor)
        ).apply(i, Chemical::new));
        public Chemical {
            if (!Float.isFinite(min) || min < 0 || !Float.isFinite(max) || max < min
                    || !Float.isFinite(potencyDivisor) || potencyDivisor <= 0)
                throw new IllegalArgumentException("invalid seed chemical quantity");
        }
        public float amount(float potency) {
            if (!Float.isFinite(potency) || potency < 0) throw new IllegalArgumentException("invalid potency");
            return Math.min(max, min + potency / potencyDivisor);
        }
    }
    public record Biology(float endurance, float nutrientConsumption, float waterConsumption, float idealHeat,
                          float heatTolerance, float lowPressure, float highPressure, float weedTolerance,
                          float weedHighLevel, float toxinTolerance) {
        public static final Codec<Biology> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("endurance").forGetter(Biology::endurance), Codec.FLOAT.fieldOf("nutrient_consumption").forGetter(Biology::nutrientConsumption),
                Codec.FLOAT.fieldOf("water_consumption").forGetter(Biology::waterConsumption), Codec.FLOAT.fieldOf("ideal_heat").forGetter(Biology::idealHeat),
                Codec.FLOAT.fieldOf("heat_tolerance").forGetter(Biology::heatTolerance), Codec.FLOAT.fieldOf("low_pressure").forGetter(Biology::lowPressure),
                Codec.FLOAT.fieldOf("high_pressure").forGetter(Biology::highPressure), Codec.FLOAT.fieldOf("weed_tolerance").forGetter(Biology::weedTolerance),
                Codec.FLOAT.fieldOf("weed_high_level").forGetter(Biology::weedHighLevel), Codec.FLOAT.fieldOf("toxin_tolerance").forGetter(Biology::toxinTolerance)
        ).apply(i, Biology::new));
        public Biology { if (lowPressure > highPressure || !Float.isFinite(endurance + nutrientConsumption + waterConsumption + idealHeat + heatTolerance + lowPressure + highPressure + weedTolerance + weedHighLevel + toxinTolerance)) throw new IllegalArgumentException("invalid seed biology"); }
    }
    public record Lifecycle(int growthStages, float lifespan, float maturation, float production, int yield, float potency, Harvest harvest) {
        public static final Codec<Lifecycle> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 64).fieldOf("growth_stages").forGetter(Lifecycle::growthStages), Codec.FLOAT.fieldOf("lifespan").forGetter(Lifecycle::lifespan),
                Codec.FLOAT.fieldOf("maturation").forGetter(Lifecycle::maturation), Codec.FLOAT.fieldOf("production").forGetter(Lifecycle::production),
                Codec.intRange(0, 10000).fieldOf("yield").forGetter(Lifecycle::yield), Codec.FLOAT.fieldOf("potency").forGetter(Lifecycle::potency),
                Codec.STRING.xmap(Harvest::valueOf, Harvest::name).fieldOf("harvest").forGetter(Lifecycle::harvest)
        ).apply(i, Lifecycle::new));
        public Lifecycle {
            if (growthStages < 1 || !Float.isFinite(lifespan) || !Float.isFinite(maturation)
                    || maturation < 0 || !Float.isFinite(production) || !Float.isFinite(potency))
                throw new IllegalArgumentException("invalid seed lifecycle");
        }
    }
    public static final Codec<SeedProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("packet").forGetter(SeedProfile::packet),
            ResourceLocation.CODEC.fieldOf("product").forGetter(SeedProfile::product),
            Codec.STRING.fieldOf("visual_key").forGetter(SeedProfile::visualKey),
            Biology.CODEC.fieldOf("biology").forGetter(SeedProfile::biology),
            Lifecycle.CODEC.fieldOf("lifecycle").forGetter(SeedProfile::lifecycle),
            Codec.unboundedMap(ResourceLocation.CODEC, Chemical.CODEC).fieldOf("chemicals").forGetter(SeedProfile::chemicals)
    ).apply(i, (packet, product, visual, biology, lifecycle, chemicals) -> new SeedProfile(packet, product, visual, biology, lifecycle, chemicals)));
    public SeedProfile {
        chemicals = Map.copyOf(chemicals);
        if (packet == null || product == null || visualKey == null || biology == null || lifecycle == null)
            throw new IllegalArgumentException("invalid seed profile");
    }
}
