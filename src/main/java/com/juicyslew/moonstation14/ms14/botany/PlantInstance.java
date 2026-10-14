package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Mutable plant-holder state, independent of rendering and world ownership. */
public final class PlantInstance {
    public static final Codec<PlantInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("seed").forGetter(p -> java.util.Optional.ofNullable(p.seed)),
            Codec.FLOAT.fieldOf("age").forGetter(p -> p.age), Codec.FLOAT.fieldOf("health").forGetter(p -> p.health),
            Codec.FLOAT.fieldOf("water").forGetter(p -> p.water), Codec.FLOAT.fieldOf("nutrition").forGetter(p -> p.nutrition),
            Codec.FLOAT.fieldOf("weeds").forGetter(p -> p.weeds), Codec.FLOAT.fieldOf("toxins").forGetter(p -> p.toxins),
            Codec.FLOAT.fieldOf("last_produce_age").forGetter(p -> p.lastProduceAge), Codec.FLOAT.fieldOf("skip_aging").forGetter(p -> p.skipAging),
            Codec.LONG.fieldOf("last_cycle").forGetter(p -> p.lastCycle), Codec.LONG.fieldOf("next_update").forGetter(p -> p.nextUpdate),
            Codec.LONG.fieldOf("revision").forGetter(p -> p.revision), Codec.BOOL.fieldOf("ready").forGetter(p -> p.ready),
            Codec.BOOL.fieldOf("dead").forGetter(p -> p.dead)
    ).apply(i, (seed, age, health, water, nutrition, weeds, toxins, produce, skip, cycle, update, revision, ready, dead) -> {
        PlantInstance p = new PlantInstance(water, nutrition, cycle);
        p.seed = seed.orElse(null); p.age=age; p.health=health; p.weeds=weeds; p.toxins=toxins;
        p.lastProduceAge=produce; p.skipAging=skip; p.lastCycle=cycle; p.nextUpdate=update; p.revision=revision;
        p.ready=ready; p.dead=dead; return p;
    }));
    public enum Status { EMPTY, GROWING, HARVEST, DEAD }
    private ResourceLocation seed;
    private float age, health, water, nutrition, weeds, toxins, lastProduceAge, skipAging;
    private long lastCycle, nextUpdate, revision;
    private boolean ready, dead;

    public PlantInstance(float water, float nutrition, long now) {
        this.water = clamp(water, 0, 100); this.nutrition = clamp(nutrition, 0, 100);
        lastCycle = now; nextUpdate = now;
    }
    public ResourceLocation seed() { return seed; }
    public float age() { return age; } public float health() { return health; }
    public float water() { return water; } public float nutrition() { return nutrition; }
    public float weeds() { return weeds; } public float toxins() { return toxins; }
    public float lastProduceAge() { return lastProduceAge; } public float skipAging() { return skipAging; }
    public long lastCycle() { return lastCycle; } public long nextUpdate() { return nextUpdate; }
    public long revision() { return revision; } public boolean ready() { return ready; } public boolean dead() { return dead; }
    public Status status() { return seed == null ? Status.EMPTY : dead ? Status.DEAD : ready ? Status.HARVEST : Status.GROWING; }
    public float soilToxins() { return toxins; }
    void soil(float water, float nutrition, float weeds, float toxins) { this.water=water; this.nutrition=nutrition; this.weeds=weeds; this.toxins=toxins; }
    void clampSoil() { soil(clamp(water,0,100),clamp(nutrition,0,100),clamp(weeds,0,10),clamp(toxins,0,100)); }
    void plant(ResourceLocation id, SeedProfile profile, long now) {
        seed = id; age = 1; health = profile.biology().endurance(); lastCycle = now;
        nextUpdate = now; ready = false; dead = false; lastProduceAge = 0; skipAging = 0; revision++;
    }
    void clearPlant() { seed = null; age = health = lastProduceAge = skipAging = 0; ready = dead = false; revision++; }
    void cycle(long now) { lastCycle = now; revision++; }
    void updatePoll(long when) { nextUpdate = when; revision++; }
    /** Marks a trusted domain mutation that does not otherwise advance the plant revision. */
    void touch() { revision++; }
    void state(float age, float health, float lastProduceAge, float skipAging, boolean ready, boolean dead) {
        this.age=age; this.health=health; this.lastProduceAge=lastProduceAge; this.skipAging=skipAging; this.ready=ready; this.dead=dead;
    }
    static float clamp(float n, float low, float high) { return Math.max(low, Math.min(high, n)); }
}
