package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Mutable plant-holder state, independent of rendering and world ownership. */
public final class PlantInstance {
    public record MutationState(float level, float modifier, float pests, boolean sampled) {
        public static final Codec<MutationState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("level", 0f).forGetter(MutationState::level),
                Codec.FLOAT.optionalFieldOf("modifier", 1f).forGetter(MutationState::modifier),
                Codec.FLOAT.optionalFieldOf("pests", 0f).forGetter(MutationState::pests),
                Codec.BOOL.optionalFieldOf("sampled", false).forGetter(MutationState::sampled)
        ).apply(i, MutationState::new));
        public MutationState(float level, float modifier, float pests) { this(level, modifier, pests, false); }
        public MutationState(float level, float modifier) { this(level, modifier, 0, false); }
        public MutationState { level = clampFinite(level, 0, 100); modifier = clampFinite(modifier, 0, 3); pests = clampFinite(pests, 0, 10); }
    }
    public static final Codec<PlantInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("seed").forGetter(p -> java.util.Optional.ofNullable(p.seed)),
             PlantSeedReference.CODEC.optionalFieldOf("genetics").forGetter(p -> java.util.Optional.ofNullable(p.seedReference)),
            Codec.FLOAT.fieldOf("age").forGetter(p -> p.age), Codec.FLOAT.fieldOf("health").forGetter(p -> p.health),
            Codec.FLOAT.fieldOf("water").forGetter(p -> p.water), Codec.FLOAT.fieldOf("nutrition").forGetter(p -> p.nutrition),
             Codec.FLOAT.fieldOf("weeds").forGetter(p -> p.weeds), Codec.FLOAT.fieldOf("toxins").forGetter(p -> p.toxins),
            Codec.FLOAT.fieldOf("last_produce_age").forGetter(p -> p.lastProduceAge), Codec.FLOAT.fieldOf("skip_aging").forGetter(p -> p.skipAging),
            Codec.LONG.fieldOf("last_cycle").forGetter(p -> p.lastCycle), Codec.LONG.fieldOf("next_update").forGetter(p -> p.nextUpdate),
            Codec.LONG.fieldOf("revision").forGetter(p -> p.revision), Codec.BOOL.fieldOf("ready").forGetter(p -> p.ready),
             Codec.BOOL.fieldOf("dead").forGetter(p -> p.dead),
             MutationState.CODEC.optionalFieldOf("mutation", new MutationState(0, 1)).forGetter(p -> new MutationState(p.mutationLevel, p.mutationModifier, p.pests, p.sampled))
         ).apply(i, (seed, reference, age, health, water, nutrition, weeds, toxins, produce, skip, cycle, update, revision, ready, dead, mutation) -> {
        PlantInstance p = new PlantInstance(water, nutrition, cycle);
            p.seed = seed.orElse(null); p.seedReference=reference.orElse(null); p.age=age; p.health=health; p.weeds=weeds; p.toxins=toxins; p.pests=mutation.pests();
        p.lastProduceAge=produce; p.skipAging=skip; p.lastCycle=cycle; p.nextUpdate=update; p.revision=revision;
          p.ready=ready; p.dead=dead; p.sampled=mutation.sampled(); p.mutationLevel=mutation.level(); p.mutationModifier=mutation.modifier(); return p;
    }));
    public enum Status { EMPTY, GROWING, HARVEST, DEAD }
    private ResourceLocation seed;
    private PlantSeedReference seedReference;
    private float age, health, water, nutrition, weeds, toxins, pests, lastProduceAge, skipAging;
    private long lastCycle, nextUpdate, revision;
    private boolean ready, dead, sampled;
    private float mutationLevel;
    private float mutationModifier = 1f;
    private boolean forceCycleUpdate;

    public PlantInstance(float water, float nutrition, long now) {
        this.water = clamp(water, 0, 100); this.nutrition = clamp(nutrition, 0, 100);
        lastCycle = now; nextUpdate = now;
    }
    public ResourceLocation seed() { return seed; }
    public GeneticValue genetics() { return seedReference == null ? null : seedReference.current(); }
    public PlantSeedReference seedReference() { return seedReference; }
     public SeedProfile effectiveProfile() { GeneticValue value = genetics(); return value == null ? null : value.profile(); }
    public float age() { return age; } public float health() { return health; }
    public float water() { return water; } public float nutrition() { return nutrition; }
    public float weeds() { return weeds; } public float toxins() { return toxins; }
    public float pests() { return pests; }
    public float lastProduceAge() { return lastProduceAge; } public float skipAging() { return skipAging; }
    public long lastCycle() { return lastCycle; } public long nextUpdate() { return nextUpdate; }
    public long revision() { return revision; } public boolean ready() { return ready; } public boolean dead() { return dead; }
    public boolean sampled() { return sampled; }
    void sampled(boolean value) { if (sampled != value) { sampled = value; touch(); } }
    public float mutationLevel() { return mutationLevel; }
    public float mutationModifier() { return mutationModifier; }
    /** Source mutation trigger state is holder-owned, not part of the inherited seed genetics. */
    public void adjustMutationLevel(float amount) {
        requireFinite(amount); mutationLevel = clampFinite(mutationLevel + amount, 0, 100); touch();
    }
    public void mutationModifier(float modifier) {
        mutationModifier = clampFinite(modifier, 0, 3); touch();
    }
    public void adjustMutationModifier(float amount) { requireFinite(amount); mutationModifier(clampFinite(mutationModifier + amount, 0, 3)); }
    void consumeMutationLevel() { if (mutationLevel != 0) { mutationLevel = 0; touch(); } }
     void resetMutationOnDeath() { mutationLevel = 0; mutationModifier = 1; pests = 0; touch(); }
    private static float clampFinite(float value, float low, float high) { requireFinite(value); return clamp(value, low, high); }
    private static void requireFinite(float value) { if (!Float.isFinite(value)) throw new IllegalArgumentException("non-finite mutation value"); }
    public Status status() { return seed == null ? Status.EMPTY : dead ? Status.DEAD : ready ? Status.HARVEST : Status.GROWING; }
    public float soilToxins() { return toxins; }
    void soil(float water, float nutrition, float weeds, float toxins) { this.water=water; this.nutrition=nutrition; this.weeds=weeds; this.toxins=toxins; }
    void pestLevel(float value) { pests=clamp(value,0,10); }
    void clampSoil() { soil(clamp(water,0,100),clamp(nutrition,0,100),clamp(weeds,0,10),clamp(toxins,0,100)); pestLevel(pests); }
    void plant(ResourceLocation id, SeedProfile profile, long now) {
        seed = id; seedReference = new PlantSeedReference(new GeneticValue(id, profile), false); age = 1; health = profile.biology().endurance(); lastCycle = now;
        nextUpdate = now; ready = false; dead = false; sampled = false; lastProduceAge = 0; skipAging = 0; revision++;
    }
    void plant(GeneticValue value, long now) { plant(value.species(), value.profile(), now); seedReference.update(value); }
    void plant(PlantSeedReference reference, long now) { if (reference == null) throw new IllegalArgumentException("missing seed reference"); plant(reference.current(), now); seedReference=reference; }
    void plant(PlantSeedReference reference, long now, Float healthOverride) {
        plant(reference, now);
        if (healthOverride != null) health = healthOverride;
    }
    void genetics(GeneticValue value) { if (value == null || !value.species().equals(seed)) throw new IllegalArgumentException("genetics species mismatch"); seedReference.update(value); revision++; }
    void ensureUniqueSeed() { if (seedReference != null && !seedReference.unique()) { seedReference=seedReference.ensureUnique(); revision++; } }
    void markSeedShared() { if (seedReference != null) seedReference.unique(false); }
    void markSeedShared(PlantSeedReference reference) { if (seedReference == reference) markSeedShared(); }
    void transitionSpecies(GeneticValue value) {
        if (value == null) throw new IllegalArgumentException("missing genetics");
        seed = value.species(); seedReference = new PlantSeedReference(value, true); revision++;
    }
    void transitionSpecies(PlantSeedReference reference) {
        if (reference == null) throw new IllegalArgumentException("missing genetics reference");
        seed = reference.current().species(); seedReference = reference; revision++;
    }
      void clearPlant() { seed = null; seedReference = null; age = health = lastProduceAge = skipAging = pests = 0; mutationModifier=1; ready = dead = sampled = false; forceCycleUpdate = false; revision++; }
    void cycle(long now) { lastCycle = now; revision++; }
    void updatePoll(long when) { nextUpdate = when; revision++; }
    /** Shift scheduled lifecycle timestamps by fixture-held time without advancing gameplay. */
    void shiftSchedule(long ticks) { if (ticks > 0) { lastCycle += ticks; nextUpdate += ticks; revision++; } }
    void resumeFixtureSchedule(long pauseStarted, long now) {
        shiftSchedule(Math.max(0L, now - Math.max(pauseStarted, lastCycle)));
    }
    /** Marks a trusted domain mutation that does not otherwise advance the plant revision. */
    void touch() { revision++; }
    void state(float age, float health, float lastProduceAge, float skipAging, boolean ready, boolean dead) {
        this.age=age; this.health=health; this.lastProduceAge=lastProduceAge; this.skipAging=skipAging; this.ready=ready; this.dead=dead;
    }
    void adjustCareHealth(float amount) {
        requireFinite(amount);
        if (seed == null || dead) return;
        health += amount;
        if (health <= 0) { dead = true; ready = false; resetMutationOnDeath(); }
        touch();
    }
    void clampCareHealth(float endurance) {
        if (seed == null) { health = 0; dead = false; touch(); return; }
        if (!Float.isFinite(endurance) || endurance < 0) throw new IllegalArgumentException("invalid plant endurance");
        health = clamp(health, 0, endurance);
        touch();
    }
    void samplingHealth(float value) { health = value; touch(); }
    void cryoxadone(SeedProfile profile, java.util.function.IntSupplier matureDeviationRoll) {
        if (seed == null || dead || profile == null) return;
        var life = profile.lifecycle();
        int deviation;
        if (age > life.maturation())
            deviation = (int) Math.max(life.maturation() - 1, age - matureDeviationRoll.getAsInt());
        else deviation = (int) (life.maturation() / life.growthStages());
        age -= deviation;
        lastProduceAge = age;
        skipAging++;
        forceCycleUpdate = true;
        touch();
    }
    void affectGrowth(int amount, SeedProfile profile) {
        if (seed == null || dead || profile == null) return;
        var life = profile.lifecycle();
        if (amount > 0) {
            if (age < life.maturation()) age += amount;
            else if (!ready && life.yield() <= 0) lastProduceAge -= amount;
        } else {
            if (age < life.maturation()) skipAging++;
            else if (!ready && life.yield() <= 0) lastProduceAge += amount;
        }
        touch();
    }
    boolean consumeForceCycleUpdate() { boolean forced = forceCycleUpdate; forceCycleUpdate = false; return forced; }
    static float clamp(float n, float low, float high) { return Math.max(low, Math.min(high, n)); }
}
