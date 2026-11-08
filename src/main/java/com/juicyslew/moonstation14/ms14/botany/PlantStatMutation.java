package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.random.RandomGenerator;

/** Immutable, source-shaped ChangeStat mutation. Float stats and integer yield deliberately use different arithmetic. */
public record PlantStatMutation(Target target, float minimum, float maximum, int steps) {
    public static final Codec<PlantStatMutation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(Target::valueOf, Target::name).fieldOf("target").forGetter(PlantStatMutation::target),
            Codec.FLOAT.fieldOf("minimum").forGetter(PlantStatMutation::minimum),
            Codec.FLOAT.fieldOf("maximum").forGetter(PlantStatMutation::maximum),
            Codec.INT.fieldOf("steps").forGetter(PlantStatMutation::steps)
    ).apply(i, PlantStatMutation::new));
    public enum Target {
        ENDURANCE, WATER_CONSUMPTION, NUTRIENT_CONSUMPTION, IDEAL_HEAT, HEAT_TOLERANCE,
        LOW_PRESSURE, HIGH_PRESSURE, WEED_TOLERANCE, TOXIN_TOLERANCE, PEST_TOLERANCE,
        LIFESPAN, MATURATION, PRODUCTION, POTENCY, YIELD, VIABLE, SEEDLESS, CAN_SCREAM, LIGNEOUS
    }
    public PlantStatMutation {
        if (target == null || steps <= 0 || !Float.isFinite(minimum) || !Float.isFinite(maximum) || maximum < minimum
                || (target != Target.YIELD && !isToggle(target) && maximum == minimum)
                || (isToggle(target) && (minimum != 0 || maximum != 0))
                || (target == Target.YIELD && (minimum != (int)minimum || maximum != (int)maximum || maximum <= minimum)))
            throw new IllegalArgumentException("invalid plant stat mutation");
    }

    public static PlantStatMutation numeric(Target target, float min, float max, int steps) {
        if (target == Target.YIELD || isToggle(target)) throw new IllegalArgumentException("target is not float stat");
        return new PlantStatMutation(target,min,max,steps);
    }
    public static PlantStatMutation integerYield(int min, int max, int steps) { return new PlantStatMutation(Target.YIELD,min,max,steps); }
    public static PlantStatMutation toggle(Target target) {
        if (!isToggle(target)) throw new IllegalArgumentException("target is not a boolean stat");
        return new PlantStatMutation(target,0,0,1);
    }
    private static boolean isToggle(Target target) { return target == Target.VIABLE || target == Target.SEEDLESS || target == Target.CAN_SCREAM || target == Target.LIGNEOUS; }

    public GeneticValue apply(GeneticValue value, RandomGenerator random) {
        if (value == null || random == null) throw new IllegalArgumentException("missing mutation input");
        SeedProfile p = value.profile();
        var b = p.biology(); var l = p.lifecycle();
        if (target == Target.CAN_SCREAM) {
            return new GeneticValue(value.species(), copy(p,b,l,p.scream().withEnabled(!p.scream().enabled())), value.mutations());
        }
        if (target == Target.VIABLE || target == Target.SEEDLESS || target == Target.LIGNEOUS) {
            var changed = new SeedProfile.Lifecycle(l.growthStages(),l.lifespan(),l.maturation(),l.production(),l.yield(),l.potency(),l.harvest(),
                    target == Target.VIABLE ? !l.viable() : l.viable(), target == Target.SEEDLESS ? !l.seedless() : l.seedless(),
                    target == Target.LIGNEOUS ? !l.ligneous() : l.ligneous());
            return new GeneticValue(value.species(), copy(p,b,changed), value.mutations());
        }
        if (target == Target.YIELD) {
            int current = l.yield(); int bin = (int)(((current - (int)minimum) / ((int)maximum - (int)minimum)) * steps);
            boolean increase = random.nextFloat() < 1f - (float)bin / steps;
            int changedYield = Math.max((int)minimum, Math.min((int)maximum, current + (increase ? 1 : -1)));
            return new GeneticValue(value.species(), copy(p,b,new SeedProfile.Lifecycle(l.growthStages(),l.lifespan(),l.maturation(),l.production(),changedYield,l.potency(),l.harvest(),l.viable(),l.seedless(),l.ligneous())), value.mutations());
        }
        float current = stat(b,l); int bin = (int) Math.rint((current-minimum) / ((maximum-minimum)/steps));
        bin = Math.max(0,Math.min(steps,bin));
        boolean increase = random.nextFloat() < 1f - (float)bin / steps;
        float next = Math.max(minimum,Math.min(maximum,current + (increase ? 1 : -1) * ((maximum-minimum)/(steps*2f))));
        if (isBiologyTarget(target)) b = biologyWith(b,target,next);
        else l = lifecycleWith(l,target,next);
        return new GeneticValue(value.species(),copy(p,b,l), value.mutations());
    }
    private float stat(SeedProfile.Biology b, SeedProfile.Lifecycle l) { return switch(target) {
        case ENDURANCE -> b.endurance(); case WATER_CONSUMPTION -> b.waterConsumption(); case NUTRIENT_CONSUMPTION -> b.nutrientConsumption();
        case IDEAL_HEAT -> b.idealHeat(); case HEAT_TOLERANCE -> b.heatTolerance(); case LOW_PRESSURE -> b.lowPressure();
        case HIGH_PRESSURE -> b.highPressure(); case WEED_TOLERANCE -> b.weedTolerance(); case TOXIN_TOLERANCE -> b.toxinTolerance();
        case PEST_TOLERANCE -> b.pestTolerance(); case LIFESPAN -> l.lifespan(); case MATURATION -> l.maturation();
        case PRODUCTION -> l.production(); case POTENCY -> l.potency(); default -> throw new IllegalStateException("not numeric");
    }; }
    private static boolean isBiologyTarget(Target target) { return switch (target) {
        case ENDURANCE, WATER_CONSUMPTION, NUTRIENT_CONSUMPTION, IDEAL_HEAT, HEAT_TOLERANCE,
                LOW_PRESSURE, HIGH_PRESSURE, WEED_TOLERANCE, TOXIN_TOLERANCE, PEST_TOLERANCE -> true;
        case LIFESPAN, MATURATION, PRODUCTION, POTENCY, YIELD, VIABLE, SEEDLESS, CAN_SCREAM, LIGNEOUS -> false;
    }; }
    private static SeedProfile.Biology biologyWith(SeedProfile.Biology b, Target t,float x) { return new SeedProfile.Biology(
            t==Target.ENDURANCE?x:b.endurance(),t==Target.NUTRIENT_CONSUMPTION?x:b.nutrientConsumption(),t==Target.WATER_CONSUMPTION?x:b.waterConsumption(),
            t==Target.IDEAL_HEAT?x:b.idealHeat(),t==Target.HEAT_TOLERANCE?x:b.heatTolerance(),t==Target.LOW_PRESSURE?x:b.lowPressure(),
            t==Target.HIGH_PRESSURE?x:b.highPressure(),t==Target.WEED_TOLERANCE?x:b.weedTolerance(),b.weedHighLevel(),t==Target.TOXIN_TOLERANCE?x:b.toxinTolerance(),t==Target.PEST_TOLERANCE?x:b.pestTolerance()); }
    private static SeedProfile.Lifecycle lifecycleWith(SeedProfile.Lifecycle l,Target t,float x) { return new SeedProfile.Lifecycle(l.growthStages(),t==Target.LIFESPAN?x:l.lifespan(),t==Target.MATURATION?x:l.maturation(),t==Target.PRODUCTION?x:l.production(),l.yield(),t==Target.POTENCY?x:l.potency(),l.harvest(),l.viable(),l.seedless(),l.ligneous()); }
    private static SeedProfile copy(SeedProfile p,SeedProfile.Biology b,SeedProfile.Lifecycle l) { return copy(p,b,l,p.scream()); }
    private static SeedProfile copy(SeedProfile p,SeedProfile.Biology b,SeedProfile.Lifecycle l,SeedProfile.Scream scream) { return new SeedProfile(p.packet(),p.product(),p.visualKey(),b,l,p.chemicals(),p.consumeGasses(),p.exudeGasses(),p.mutationTargets(), scream, p.sourceName()); }
}
