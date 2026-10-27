package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

/** Pure ordinary holder rules; catalog/reagent/world adapters stay outside this class. */
public final class PlantMetabolismEvaluator {
    public record Environment(float pressureKpa, float temperatureK) { }
    public record GasExchangeReport(boolean attempted, int missingGasCount,
                                    java.util.List<GasType> unmetRequiredGases,
                                    java.util.List<GasType> failedExudationGases) {
        public GasExchangeReport {
            unmetRequiredGases = java.util.List.copyOf(unmetRequiredGases);
            failedExudationGases = java.util.List.copyOf(failedExudationGases);
        }
        public static GasExchangeReport none() { return new GasExchangeReport(false, 0, java.util.List.of(), java.util.List.of()); }
    }
    public record Result(PlantInstance.Status status, int stage, int plannedYield, boolean changed,
                         GasExchangeReport gasExchange) {
        public Result(PlantInstance.Status status, int stage, int plannedYield, boolean changed) {
            this(status, stage, plannedYield, changed, GasExchangeReport.none());
        }
    }
    /** Read-only appearance projection shared by server packets and metabolism results. */
    public record Appearance(PlantInstance.Status status, int stage) { }
    public record EnvironmentWarnings(boolean pressure, boolean temperature) { }
    /** Named-gas world boundary; implementations own availability, writes and fresh samples. */
    public interface GasExchange {
        Optional<Environment> environment();
        boolean consume(GasType gas, double amount);
        boolean exude(GasType gas, double amount, float temperatureK);
    }
    /** World output owner; commit is invoked only after every output has been published. */
    @FunctionalInterface public interface AutoHarvest { boolean publish(PlantInstance plant, SeedProfile seed, float cycleAge, Runnable sourceCommit); }
    private PlantMetabolismEvaluator() { }

    /** The callback processes soil first; due polls run once and never replay missed intervals. */
    public static Optional<Result> poll(PlantInstance plant, TrayProfile tray, SeedProfile seed, long now,
                                         Consumer<PlantInstance> soilMetabolism, RandomGenerator random,
                                         Optional<Environment> environment) {
        return poll(plant, tray, () -> seed, now, soilMetabolism, random, environment, p -> { });
    }

    /** Polls soil first; a due mutation callback may replace genetics before the cycle profile is resolved. */
    public static Optional<Result> poll(PlantInstance plant, TrayProfile tray, Supplier<SeedProfile> seed,
                                          long now, Consumer<PlantInstance> soilMetabolism, RandomGenerator random,
                                          Optional<Environment> environment, Consumer<PlantInstance> mutationCycle) {
        return poll(plant, tray, seed, now, soilMetabolism, random, environment, mutationCycle, null);
    }
    public static Optional<Result> poll(PlantInstance plant, TrayProfile tray, Supplier<SeedProfile> seed,
                                           long now, Consumer<PlantInstance> soilMetabolism, RandomGenerator random,
                                           Optional<Environment> environment, Consumer<PlantInstance> mutationCycle,
                                           GasExchange gases) {
        return poll(plant, tray, seed, now, soilMetabolism, random, environment, mutationCycle, gases, null);
    }
    public static Optional<Result> poll(PlantInstance plant, TrayProfile tray, Supplier<SeedProfile> seed,
                                           long now, Consumer<PlantInstance> soilMetabolism, RandomGenerator random,
                                           Optional<Environment> environment, Consumer<PlantInstance> mutationCycle,
                                           GasExchange gases, AutoHarvest autoHarvest) {
        Objects.requireNonNull(plant); Objects.requireNonNull(tray); Objects.requireNonNull(soilMetabolism);
        if (now < plant.nextUpdate()) return Optional.empty();
        plant.updatePoll(now + tray.pollDelayTicks());
        soilMetabolism.accept(plant);
        boolean forceUpdate = plant.consumeForceCycleUpdate();
        if (forceUpdate || now >= plant.lastCycle() + tray.cycleDelayTicks()) mutationCycle.accept(plant);
        return Optional.of(cycle(plant, tray, seed.get(), now, random, environment, gases, autoHarvest, forceUpdate));
    }

    /** Runs one cycle at most. Null resolved seed means empty holder, never a fabricated seed default. */
    public static Result cycle(PlantInstance plant, TrayProfile tray, SeedProfile seed, long now,
                               RandomGenerator random, Optional<Environment> environment) {
        return cycle(plant, tray, seed, now, random, environment, null, null);
    }
    public static Result cycle(PlantInstance plant, TrayProfile tray, SeedProfile seed, long now,
                               RandomGenerator random, Optional<Environment> environment, GasExchange gases) {
        return cycle(plant, tray, seed, now, random, environment, gases, null);
    }
    public static Result cycle(PlantInstance plant, TrayProfile tray, SeedProfile seed, long now,
                               RandomGenerator random, Optional<Environment> environment, GasExchange gases,
                               AutoHarvest autoHarvest) {
        return cycle(plant, tray, seed, now, random, environment, gases, autoHarvest, false);
    }
    private static Result cycle(PlantInstance plant, TrayProfile tray, SeedProfile seed, long now,
                                RandomGenerator random, Optional<Environment> environment, GasExchange gases,
                                AutoHarvest autoHarvest, boolean forceUpdate) {
        Objects.requireNonNull(plant); Objects.requireNonNull(tray); Objects.requireNonNull(random); Objects.requireNonNull(environment);
        if (!forceUpdate && now < plant.lastCycle() + tray.cycleDelayTicks()) return result(plant, seed, false);
        boolean planted = plant.seed() != null;
        if (planted && (seed == null || !Float.isFinite(seed.lifecycle().maturation()) || seed.lifecycle().maturation() <= 0))
            return result(plant, seed, false);
        // An active plant needs a known exposure before any cycle work, including the weed roll.
        // Empty/dead holders still maintain weeds without an atmosphere sample.
        if (planted && !plant.dead() && environment.isEmpty()) return result(plant, seed, false);
        if (plant.water() > 10 && plant.nutrition() > 5 && random.nextFloat() < (planted ? .01f : .05f))
            plant.soil(plant.water(),plant.nutrition(),plant.weeds()+2,plant.soilToxins());
        if (!planted || plant.dead()) { plant.cycle(now); plant.clampSoil(); return result(plant,seed,true); }

        int missingGas = 0;
        java.util.List<GasType> unmetGases = new java.util.ArrayList<>();
        java.util.List<GasType> failedExudes = new java.util.ArrayList<>();
        boolean hasGasGenes = !seed.consumeGasses().isEmpty() || !seed.exudeGasses().isEmpty();
        boolean exchangeAttempted = hasGasGenes;
        boolean requiresGasWrite = seed.consumeGasses().values().stream().anyMatch(amount -> amount > 0)
                || !seed.exudeGasses().isEmpty();
        if (requiresGasWrite && gases == null) {
            for (var required : seed.consumeGasses().entrySet()) if (required.getValue() > 0) unmetGases.add(required.getKey());
            failedExudes.addAll(seed.exudeGasses().keySet());
            return result(plant, seed, false, new GasExchangeReport(true, unmetGases.size(), unmetGases, failedExudes));
        }
        if (gases != null) for (var required : seed.consumeGasses().entrySet()) {
            // Upstream's available < amount check lets zero pass without AdjustMoles/write.
            if (required.getValue() > 0 && !gases.consume(required.getKey(), required.getValue())) {
                missingGas++; unmetGases.add(required.getKey());
            }
        }

        SeedProfile.Biology b=seed.biology(); SeedProfile.Lifecycle l=seed.lifecycle();
        if (random.nextFloat() < .01f) plant.pestLevel(plant.pests() + .5f);
        float age=plant.age(), health=plant.health(), skip=plant.skipAging(), last=plant.lastProduceAge();
        boolean ready=plant.ready(), wasReady=plant.ready(), dead=false;
        plant.cycle(now);
        if (skip > 0) skip--; else if (random.nextFloat() < .8f) age += 1;
        if (b.nutrientConsumption()>0 && plant.nutrition()>0 && random.nextFloat()<.75f)
            plant.soil(plant.water(),plant.nutrition()-b.nutrientConsumption(),plant.weeds(),plant.soilToxins());
        if (b.waterConsumption()>0 && plant.water()>0 && random.nextFloat()<.75f)
            plant.soil(plant.water()-b.waterConsumption()*2,plant.nutrition(),plant.weeds(),plant.soilToxins());
        int mod=random.nextInt(1,3);
        if (!l.viable()) {
            if (age < l.maturation()) skip++;
            else if (!ready && l.yield() <= 0) last--;
            health -= 6 * mod;
        }
        if (skip < 10) {
            if (plant.nutrition()>5) { if (random.nextFloat()<.35f) health+=mod; }
            else { if(age<l.maturation()) skip++; else if(!ready && l.yield()<=0) last--; health-=mod; }
            if (plant.water()>10) { if (random.nextFloat()<.35f) health+=mod; }
            else { if(age<l.maturation()) skip++; else if(!ready && l.yield()<=0) last--; health-=mod; }
        }
        Environment env = gases == null || !hasGasGenes ? environment.orElseThrow() : gases.environment().orElse(null);
        if (env == null) return result(plant, seed, false,
                new GasExchangeReport(exchangeAttempted, missingGas, unmetGases, failedExudes));
        EnvironmentWarnings exposure = improperEnvironment(seed, env);
        if (missingGas > 0) health -= missingGas;
        if(exposure.pressure()) health-=mod;
        if(exposure.temperature()) health-=mod;
        if (gases != null && !seed.exudeGasses().isEmpty()) {
            int count = seed.exudeGasses().size();
            for (var emitted : seed.exudeGasses().entrySet()) {
                float scaled = emitted.getValue() * (float) Math.rint(seed.lifecycle().potency()) / count;
                long potency = (long) Math.rint(scaled);
                if (!gases.exude(emitted.getKey(), Math.max(1, potency), env.temperatureK()))
                    failedExudes.add(emitted.getKey());
            }
        }
        int uptake=Math.max(1,(int)Math.rint(plant.soilToxins()/10.0));
        if(plant.soilToxins()>b.toxinTolerance()) health-=uptake;
        plant.soil(plant.water(),plant.nutrition(),plant.weeds(),plant.soilToxins()-uptake);
        if(plant.weeds()>=b.weedTolerance()) health--;
        if(plant.pests()>0 && plant.pests()>b.pestTolerance()) health--;
        if(age>l.lifespan()) health-=random.nextInt(3,5);
        if(health<=0) { dead=true; ready=false; plant.resetMutationOnDeath(); plant.soil(plant.water(),plant.nutrition(),plant.weeds()+1,plant.soilToxins()); }
        plant.state(age,PlantInstance.clamp(health,0,b.endurance()),last,skip,ready,dead);
        // AutoHarvest consumes only readiness carried into this actual cycle. Store the current
        // post-growth age/health before invoking the world publisher so its source guard is fresh.
        if (!dead && wasReady && ready && l.harvest()==SeedProfile.Harvest.SELF_HARVEST && autoHarvest!=null) {
            final float harvestAge = age;
            autoHarvest.publish(plant, seed, harvestAge, () -> afterHarvest(plant, seed, harvestAge));
            // Read committed state back even for a recovery-required publisher result: the batch
            // may have committed the source before a later presentation step failed.
            ready=plant.ready(); last=plant.lastProduceAge();
        }
        if(!dead && !ready && age>l.production() && age-last>l.production()) { ready=true; last=age; }
        plant.state(age,PlantInstance.clamp(health,0,b.endurance()),last,skip,ready,dead);
        plant.clampSoil();
        return result(plant,seed,true, new GasExchangeReport(exchangeAttempted, missingGas, unmetGases, failedExudes));
    }
    public static Result harvest(PlantInstance plant, SeedProfile seed, float yieldMod) {
        if (!plant.ready() || plant.dead() || seed==null) return result(plant,seed,false);
        int yield=Math.max(0,Math.round(seed.lifecycle().yield()*yieldMod));
        afterHarvest(plant, seed, plant.age());
        return new Result(plant.status(), plant.status()==PlantInstance.Status.GROWING
                ? Math.max(1,Math.min(seed.lifecycle().growthStages(),(int)(plant.age()*seed.lifecycle().growthStages()/seed.lifecycle().maturation()))) : 0,yield,true);
    }
    /** Shared source lifecycle transition for manual and automatic output owners. */
    public static void afterHarvest(PlantInstance plant, SeedProfile seed, float currentAge) {
        if(seed.lifecycle().harvest()!=SeedProfile.Harvest.NO_REPEAT)
            plant.state(currentAge,plant.health(),currentAge,plant.skipAging(),false,false);
        else plant.clearPlant();
    }
    /** Read-only harvest projection; callers must publish outputs before applying harvest(). */
    public static int previewHarvestYield(PlantInstance plant, SeedProfile seed, float yieldMod) {
        if (plant == null || !plant.ready() || plant.dead() || seed == null
                || !Float.isFinite(yieldMod) || yieldMod <= 0) return 0;
        return Math.max(0, Math.round(seed.lifecycle().yield() * yieldMod));
    }
    /** SS14 system AutoHarvest asks GenerateProduce for at least one item even at zero yield. */
    public static int automaticHarvestCount(SeedProfile seed) {
        return seed == null ? 0 : Math.max(1, seed.lifecycle().yield());
    }
    public static void clear(PlantInstance plant) { plant.clearPlant(); }
    public static void hoe(PlantInstance plant) { plant.soil(plant.water(),plant.nutrition(),0,plant.soilToxins()); }
    private static Result result(PlantInstance p,SeedProfile s,boolean changed) {
        Appearance appearance = appearance(p, s);
        return new Result(appearance.status(),appearance.stage(),0,changed);
    }
    private static Result result(PlantInstance p, SeedProfile s, boolean changed, GasExchangeReport gas) {
        Appearance appearance = appearance(p, s);
        return new Result(appearance.status(), appearance.stage(), 0, changed, gas);
    }

    public static Appearance appearance(PlantInstance p, SeedProfile s) {
        int stage=0;
        if(s!=null && p.seed()!=null && !p.dead() && p.status()!=PlantInstance.Status.HARVEST && s.lifecycle().maturation()>0)
            stage=Math.min(s.lifecycle().growthStages(), rawGrowthStage(p, s));
        else if(s!=null && p.status()==PlantInstance.Status.HARVEST) stage=s.lifecycle().growthStages();
        return new Appearance(p.status(), stage);
    }

    /** Source holder's uncapped logical growth stage, distinct from the clamped visual stage. */
    public static int rawGrowthStage(PlantInstance plant, SeedProfile seed) {
        if (plant == null || plant.seed() == null || seed == null) return 0;
        float maturation = seed.lifecycle().maturation();
        if (maturation <= 0) return 1;
        return Math.max(1, (int) (plant.age() * seed.lifecycle().growthStages() / maturation));
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
