package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.List;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

/** Resolved, typed SS14-derived plant data; visual keys are metadata, not geometry. */
public record SeedProfile(ResourceLocation packet, ResourceLocation product, String visualKey,
                           Biology biology, Lifecycle lifecycle, Map<ResourceLocation, Chemical> chemicals,
                              Map<GasType, Float> consumeGasses, Map<GasType, Float> exudeGasses,
                              List<ResourceLocation> mutationTargets, Scream scream, String sourceName) {
    public enum Harvest { NO_REPEAT, REPEAT, SELF_HARVEST }
    /** Resolved scream capability; audio event is the existing registered emote event. */
    public record Scream(boolean enabled, ResourceLocation sound, float volumeDb) {
        public static final ResourceLocation DEFAULT_SOUND = ResourceLocation.fromNamespaceAndPath("moonstation14", "emote/scream");
        public static final Scream DISABLED = new Scream(false, DEFAULT_SOUND, -10f);
        public static final Codec<Scream> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("enabled", false).forGetter(Scream::enabled),
                ResourceLocation.CODEC.optionalFieldOf("sound", DEFAULT_SOUND).forGetter(Scream::sound),
                Codec.FLOAT.optionalFieldOf("volume_db", -10f).forGetter(Scream::volumeDb)
        ).apply(i, Scream::new));
        public Scream { if (sound == null || !Float.isFinite(volumeDb)) throw new IllegalArgumentException("invalid scream profile"); }
        public Scream withEnabled(boolean enabled) { return new Scream(enabled, sound, volumeDb); }
    }
    public record Chemical(float min, float max, float potencyDivisor, boolean inherent) {
        public static final Codec<Chemical> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("min").forGetter(Chemical::min),
                Codec.FLOAT.fieldOf("max").forGetter(Chemical::max),
                Codec.FLOAT.fieldOf("potency_divisor").forGetter(Chemical::potencyDivisor),
                Codec.BOOL.optionalFieldOf("inherent", true).forGetter(Chemical::inherent)
        ).apply(i, Chemical::new));
        public Chemical(float min, float max, float potencyDivisor) { this(min, max, potencyDivisor, true); }
        public Chemical {
            if (!Float.isFinite(min) || min < 0 || !Float.isFinite(max) || max < min
                    || !Float.isFinite(potencyDivisor) || potencyDivisor < 0
                    || BotanyFixedPoint.fromFloat(min) > BotanyFixedPoint.MAX_CENTS
                    || BotanyFixedPoint.fromFloat(max) > BotanyFixedPoint.MAX_CENTS)
                throw new IllegalArgumentException("invalid seed chemical quantity");
        }
        public float amount(float potency) {
            if (!Float.isFinite(potency) || potency < 0) throw new IllegalArgumentException("invalid potency");
            return potencyDivisor <= 0 || potency <= 0 ? min : Math.min(max, min + potency / potencyDivisor);
        }
        /** FixedPoint2 source order: quantize Min and potency contribution separately, then clamp. */
        public long amountCents(float potency) {
            if (!Float.isFinite(potency) || potency < 0) throw new IllegalArgumentException("invalid potency");
            long minCents = BotanyFixedPoint.fromFloat(min);
            long maxCents = BotanyFixedPoint.fromFloat(max);
            long contribution = potencyDivisor <= 0 || potency <= 0 ? 0
                    : BotanyFixedPoint.fromFloat(potency / potencyDivisor);
            return Math.min(maxCents, Math.addExact(minCents, contribution));
        }
        public Chemical extendCents(long amountCents) {
            long extended = Math.addExact(BotanyFixedPoint.fromFloat(max), amountCents);
            float newMax = BotanyFixedPoint.toFloat(extended);
            return new Chemical(min, newMax, 100f / newMax, inherent);
        }
    }
    public record Biology(float endurance, float nutrientConsumption, float waterConsumption, float idealHeat,
                          float heatTolerance, float lowPressure, float highPressure, float weedTolerance,
                           float weedHighLevel, float toxinTolerance, float pestTolerance) {
        public static final Codec<Biology> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("endurance").forGetter(Biology::endurance), Codec.FLOAT.fieldOf("nutrient_consumption").forGetter(Biology::nutrientConsumption),
                Codec.FLOAT.fieldOf("water_consumption").forGetter(Biology::waterConsumption), Codec.FLOAT.fieldOf("ideal_heat").forGetter(Biology::idealHeat),
                Codec.FLOAT.fieldOf("heat_tolerance").forGetter(Biology::heatTolerance), Codec.FLOAT.fieldOf("low_pressure").forGetter(Biology::lowPressure),
                Codec.FLOAT.fieldOf("high_pressure").forGetter(Biology::highPressure), Codec.FLOAT.fieldOf("weed_tolerance").forGetter(Biology::weedTolerance),
                Codec.FLOAT.fieldOf("weed_high_level").forGetter(Biology::weedHighLevel), Codec.FLOAT.fieldOf("toxin_tolerance").forGetter(Biology::toxinTolerance),
                Codec.FLOAT.optionalFieldOf("pest_tolerance", 5f).forGetter(Biology::pestTolerance)
        ).apply(i, Biology::new));
        public Biology(float endurance, float nutrientConsumption, float waterConsumption, float idealHeat, float heatTolerance, float lowPressure, float highPressure, float weedTolerance, float weedHighLevel, float toxinTolerance) { this(endurance,nutrientConsumption,waterConsumption,idealHeat,heatTolerance,lowPressure,highPressure,weedTolerance,weedHighLevel,toxinTolerance,5f); }
        public Biology { if (lowPressure > highPressure || !Float.isFinite(endurance + nutrientConsumption + waterConsumption + idealHeat + heatTolerance + lowPressure + highPressure + weedTolerance + weedHighLevel + toxinTolerance + pestTolerance)) throw new IllegalArgumentException("invalid seed biology"); }
    }
    public record Lifecycle(int growthStages, float lifespan, float maturation, float production, int yield, float potency, Harvest harvest, boolean viable, boolean seedless, boolean ligneous) {
        public static final Codec<Lifecycle> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 64).fieldOf("growth_stages").forGetter(Lifecycle::growthStages), Codec.FLOAT.fieldOf("lifespan").forGetter(Lifecycle::lifespan),
                Codec.FLOAT.fieldOf("maturation").forGetter(Lifecycle::maturation), Codec.FLOAT.fieldOf("production").forGetter(Lifecycle::production),
                 Codec.INT.fieldOf("yield").forGetter(Lifecycle::yield), Codec.FLOAT.fieldOf("potency").forGetter(Lifecycle::potency),
                 Codec.STRING.xmap(Harvest::valueOf, Harvest::name).fieldOf("harvest").forGetter(Lifecycle::harvest),
                  Codec.BOOL.optionalFieldOf("viable", true).forGetter(Lifecycle::viable), Codec.BOOL.optionalFieldOf("seedless", false).forGetter(Lifecycle::seedless),
                  Codec.BOOL.optionalFieldOf("ligneous", false).forGetter(Lifecycle::ligneous)
        ).apply(i, Lifecycle::new));
        public Lifecycle(int growthStages,float lifespan,float maturation,float production,int yield,float potency,Harvest harvest) { this(growthStages,lifespan,maturation,production,yield,potency,harvest,true,false); }
        public Lifecycle(int growthStages,float lifespan,float maturation,float production,int yield,float potency,Harvest harvest,boolean viable,boolean seedless) {
            this(growthStages,lifespan,maturation,production,yield,potency,harvest,viable,seedless,false);
        }
        public Lifecycle {
            if (growthStages < 1 || !Float.isFinite(lifespan) || !Float.isFinite(maturation)
                    || maturation < 0 || !Float.isFinite(production) || !Float.isFinite(potency) || yield < 0)
                throw new IllegalArgumentException("invalid seed lifecycle");
        }
    }
    public static final Codec<SeedProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("packet").forGetter(SeedProfile::packet),
            ResourceLocation.CODEC.fieldOf("product").forGetter(SeedProfile::product),
            Codec.STRING.fieldOf("visual_key").forGetter(SeedProfile::visualKey),
            Biology.CODEC.fieldOf("biology").forGetter(SeedProfile::biology),
            Lifecycle.CODEC.fieldOf("lifecycle").forGetter(SeedProfile::lifecycle),
            Codec.unboundedMap(ResourceLocation.CODEC, Chemical.CODEC).fieldOf("chemicals").forGetter(SeedProfile::chemicals),
            Codec.unboundedMap(Codec.STRING.xmap(GasType::fromId, GasType::id), Codec.FLOAT).optionalFieldOf("consume_gasses", Map.of()).forGetter(SeedProfile::consumeGasses),
             Codec.unboundedMap(Codec.STRING.xmap(GasType::fromId, GasType::id), Codec.FLOAT).optionalFieldOf("exude_gasses", Map.of()).forGetter(SeedProfile::exudeGasses),
              ResourceLocation.CODEC.listOf().optionalFieldOf("mutation_targets", List.of()).forGetter(SeedProfile::mutationTargets),
              Scream.CODEC.optionalFieldOf("scream", Scream.DISABLED).forGetter(SeedProfile::scream),
              Codec.STRING.fieldOf("source_name").forGetter(SeedProfile::sourceName)
     ).apply(i, SeedProfile::new));
    public SeedProfile(ResourceLocation packet, ResourceLocation product, String visualKey, Biology biology, Lifecycle lifecycle,
                       Map<ResourceLocation, Chemical> chemicals, String sourceName) {
         this(packet, product, visualKey, biology, lifecycle, chemicals, Map.of(), Map.of(), List.of(), Scream.DISABLED, sourceName);
    }
    public SeedProfile {
        chemicals = Map.copyOf(chemicals);
        consumeGasses = validatedGasses(consumeGasses);
        exudeGasses = validatedGasses(exudeGasses);
        mutationTargets = List.copyOf(mutationTargets);
        if (scream == null) scream = Scream.DISABLED;
        if (packet == null || product == null || visualKey == null || biology == null || lifecycle == null
                || sourceName == null || sourceName.isBlank())
            throw new IllegalArgumentException("invalid seed profile");
    }
    /** Clone behavior shared by SS14 seed-copy paths: retain capability, but use default audio. */
    public SeedProfile withDefaultScreamSound() {
        return withScream(new Scream(scream.enabled(), Scream.DEFAULT_SOUND, -10f));
    }
    public SeedProfile withScream(Scream value) {
        return new SeedProfile(packet, product, visualKey, biology, lifecycle, chemicals, consumeGasses,
                exudeGasses, mutationTargets, value, sourceName);
    }
    private static Map<GasType, Float> validatedGasses(Map<GasType, Float> values) {
        Map<GasType, Float> copy = new java.util.EnumMap<>(GasType.class);
        values.forEach((gas, amount) -> {
            if (gas == null || amount == null || !Float.isFinite(amount) || amount < 0)
                throw new IllegalArgumentException("invalid seed gas amount");
            copy.put(gas, amount);
        });
        return java.util.Collections.unmodifiableMap(copy);
    }
}
