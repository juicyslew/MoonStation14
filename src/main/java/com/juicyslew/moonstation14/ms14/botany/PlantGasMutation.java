package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.random.RandomGenerator;

/** Source-shaped mutation of one randomly selected gas gene; this changes genetics only. */
public record PlantGasMutation(Target target, float minimum, float maximum) {
    public enum Target { CONSUME, EXUDE }
    public static final Codec<PlantGasMutation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(Target::valueOf, Target::name).fieldOf("target").forGetter(PlantGasMutation::target),
            Codec.FLOAT.fieldOf("minimum").forGetter(PlantGasMutation::minimum),
            Codec.FLOAT.fieldOf("maximum").forGetter(PlantGasMutation::maximum)
    ).apply(i, PlantGasMutation::new));
    public PlantGasMutation {
        if (target == null || !Float.isFinite(minimum) || !Float.isFinite(maximum) || minimum < 0 || maximum < minimum)
            throw new IllegalArgumentException("invalid gas mutation bounds");
    }
    public static PlantGasMutation source(Target target) { return new PlantGasMutation(target, .01f, .5f); }
    public GeneticValue apply(GeneticValue genetics, RandomGenerator random) {
        if (genetics == null || random == null) throw new IllegalArgumentException("missing gas mutation input");
        // Keep the source body draw order: amount first, then uniform canonical enum pick.
        float amount = random.nextFloat(minimum, maximum);
        GasType gas = GasType.values()[random.nextInt(GasType.values().length)];
        SeedProfile profile = genetics.profile();
        var consumed = new EnumMap<GasType, Float>(GasType.class); consumed.putAll(profile.consumeGasses());
        var exuded = new EnumMap<GasType, Float>(GasType.class); exuded.putAll(profile.exudeGasses());
        var values = target == Target.CONSUME ? consumed : exuded;
        values.put(gas, values.getOrDefault(gas, 0f) + amount);
        SeedProfile changed = new SeedProfile(profile.packet(), profile.product(), profile.visualKey(), profile.biology(),
                profile.lifecycle(), profile.chemicals(), consumed, exuded, profile.mutationTargets(), profile.scream(), profile.sourceName());
        return new GeneticValue(genetics.species(), changed, genetics.mutations());
    }
}
