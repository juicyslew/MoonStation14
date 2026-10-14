package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/** Pure ordinary holder rules; catalog/reagent/world adapters stay outside this class. */
public final class PlantMetabolismEvaluator {
    public record Environment(float pressureKpa, float temperatureK) { }
    public record Result(PlantInstance.Status status, int stage, int plannedYield, boolean changed) { }
    /** Read-only appearance projection shared by server packets and metabolism results. */
    public record Appearance(PlantInstance.Status status, int stage) { }
    public record EnvironmentWarnings(boolean pressure, boolean temperature) { }
    private PlantMetabolismEvaluator() { }

    /** The callback processes soil first; due polls run once and never replay missed intervals. */
    public static Optional<Result> poll(PlantInstance plant, TrayProfile tray, SeedProfile seed, long now,
                                        Consumer<PlantInstance> soilMetabolism, RandomGenerator random,
                                        Optional<Environment> environment) {
        Objects.requireNonNull(plant); Objects.requireNonNull(tray); Objects.requireNonNull(soilMetabolism);
        if (now < plant.nextUpdate()) return Optional.empty();
        plant.updatePoll(now + tray.pollDelayTicks());
        soilMetabolism.accept(plant);
        return Optional.of(cycle(plant, tray, seed, now, random, environment));
    }

    /** Runs one cycle at most. Null resolved seed means empty holder, never a fabricated seed default. */
    public static Result cycle(PlantInstance plant, TrayProfile tray, SeedProfile seed, long now,
                               RandomGenerator random, Optional<Environment> environment) {
        Objects.requireNonNull(plant); Objects.requireNonNull(tray); Objects.requireNonNull(random); Objects.requireNonNull(environment);
        if (now < plant.lastCycle() + tray.cycleDelayTicks()) return result(plant, seed, false);
        boolean planted = plant.seed() != null;
        if (planted && (seed == null || !Float.isFinite(seed.lifecycle().maturation()) || seed.lifecycle().maturation() <= 0))
            return result(plant, seed, false);
        // An active plant needs a known exposure before any cycle work, including the weed roll.
        // Empty/dead holders still maintain weeds without an atmosphere sample.
        if (planted && !plant.dead() && environment.isEmpty()) return result(plant, seed, false);
        if (plant.water() > 10 && plant.nutrition() > 5 && random.nextFloat() < (planted ? .01f : .05f))
            plant.soil(plant.water(),plant.nutrition(),plant.weeds()+2,plant.soilToxins());
        if (!planted || plant.dead()) { plant.cycle(now); plant.clampSoil(); return result(plant,seed,true); }

        SeedProfile.Biology b=seed.biology(); SeedProfile.Lifecycle l=seed.lifecycle();
        float age=plant.age(), health=plant.health(), skip=plant.skipAging(), last=plant.lastProduceAge();
        boolean ready=plant.ready(), dead=false;
        plant.cycle(now);
        if (skip > 0) skip--; else if (random.nextFloat() < .8f) age += 1;
        if (b.nutrientConsumption()>0 && plant.nutrition()>0 && random.nextFloat()<.75f)
            plant.soil(plant.water(),plant.nutrition()-b.nutrientConsumption(),plant.weeds(),plant.soilToxins());
        if (b.waterConsumption()>0 && plant.water()>0 && random.nextFloat()<.75f)
            plant.soil(plant.water()-b.waterConsumption()*2,plant.nutrition(),plant.weeds(),plant.soilToxins());
        int mod=random.nextInt(1,3);
        if (skip < 10) {
            if (plant.nutrition()>5) { if (random.nextFloat()<.35f) health+=mod; }
            else { if(age<l.maturation()) skip++; else if(!ready && l.yield()<=0) last--; health-=mod; }
            if (plant.water()>10) { if (random.nextFloat()<.35f) health+=mod; }
            else { if(age<l.maturation()) skip++; else if(!ready && l.yield()<=0) last--; health-=mod; }
        }
        Environment env=environment.get();
        EnvironmentWarnings exposure = improperEnvironment(seed, env);
        if(exposure.pressure()) health-=mod;
        if(exposure.temperature()) health-=mod;
        int uptake=Math.max(1,(int)Math.rint(plant.soilToxins()/10.0));
        if(plant.soilToxins()>b.toxinTolerance()) health-=uptake;
        plant.soil(plant.water(),plant.nutrition(),plant.weeds(),plant.soilToxins()-uptake);
        if(plant.weeds()>=b.weedTolerance()) health--;
        if(age>l.lifespan()) health-=random.nextInt(3,5);
        if(health<=0) { dead=true; ready=false; plant.soil(plant.water(),plant.nutrition(),plant.weeds()+1,plant.soilToxins()); }
        else if(!ready && age>l.production() && age-last>l.production()) { ready=true; last=age; }
        plant.state(age,PlantInstance.clamp(health,0,b.endurance()),last,skip,ready,dead);
        plant.clampSoil();
        return result(plant,seed,true);
    }
    public static Result harvest(PlantInstance plant, SeedProfile seed, float yieldMod) {
        if (!plant.ready() || plant.dead() || seed==null) return result(plant,seed,false);
        int yield=Math.max(1,Math.round(seed.lifecycle().yield()*yieldMod));
        if(seed.lifecycle().harvest()==SeedProfile.Harvest.REPEAT)
            plant.state(plant.age(),plant.health(),plant.age(),plant.skipAging(),false,false);
        else plant.clearPlant();
        return new Result(plant.status(), plant.status()==PlantInstance.Status.GROWING
                ? Math.max(1,Math.min(seed.lifecycle().growthStages(),(int)(plant.age()*seed.lifecycle().growthStages()/seed.lifecycle().maturation()))) : 0,yield,true);
    }
    /** Read-only harvest projection; callers must publish outputs before applying harvest(). */
    public static int previewHarvestYield(PlantInstance plant, SeedProfile seed, float yieldMod) {
        if (plant == null || !plant.ready() || plant.dead() || seed == null
                || !Float.isFinite(yieldMod) || yieldMod <= 0) return 0;
        return Math.max(1, Math.round(seed.lifecycle().yield() * yieldMod));
    }
    public static void clear(PlantInstance plant) { plant.clearPlant(); }
    public static void hoe(PlantInstance plant) { plant.soil(plant.water(),plant.nutrition(),0,plant.soilToxins()); }
    private static Result result(PlantInstance p,SeedProfile s,boolean changed) {
        Appearance appearance = appearance(p, s);
        return new Result(appearance.status(),appearance.stage(),0,changed);
    }

    public static Appearance appearance(PlantInstance p, SeedProfile s) {
        int stage=0;
        if(s!=null && p.seed()!=null && !p.dead() && p.status()!=PlantInstance.Status.HARVEST && s.lifecycle().maturation()>0)
            stage=Math.max(1,Math.min(s.lifecycle().growthStages(),(int)(p.age()*s.lifecycle().growthStages()/s.lifecycle().maturation())));
        else if(s!=null && p.status()==PlantInstance.Status.HARVEST) stage=s.lifecycle().growthStages();
        return new Appearance(p.status(), stage);
    }

    /** Known atmospheric warning projection; missing samples intentionally produce no warning. */
    public static EnvironmentWarnings improperEnvironment(SeedProfile seed, Environment environment) {
        Objects.requireNonNull(seed); Objects.requireNonNull(environment);
        SeedProfile.Biology biology = seed.biology();
        return new EnvironmentWarnings(environment.pressureKpa() < biology.lowPressure()
                || environment.pressureKpa() > biology.highPressure(),
                Math.abs(environment.temperatureK() - biology.idealHeat()) > biology.heatTolerance());
    }

    /** Read-only five-bit status projection shared by the authoritative tray appearance publication. */
    public static int warningBits(PlantInstance plant, SeedProfile seed, boolean enabled,
                                  Optional<Environment> environment) {
        Objects.requireNonNull(environment);
        if (!enabled || plant == null) return 0;
        int bits = 0;
        if (plant.ready()) bits |= 1;
        if (seed != null && plant.health() <= seed.biology().endurance() / 2) bits |= 2;
        if (plant.weeds() >= 5 || plant.soilToxins() >= 40) bits |= 4;
        if (plant.nutrition() <= 8) bits |= 8;
        if (plant.water() <= 15) bits |= 16;
        if (seed != null && environment.isPresent()) {
            EnvironmentWarnings exposure = improperEnvironment(seed, environment.get());
            if (exposure.pressure() || exposure.temperature()) bits |= 4;
        }
        return bits;
    }
}
