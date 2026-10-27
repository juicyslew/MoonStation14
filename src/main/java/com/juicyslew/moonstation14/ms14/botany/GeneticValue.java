package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Immutable instance genetics: species identity plus its complete resolved profile snapshot. */
public record GeneticValue(ResourceLocation species, SeedProfile profile, java.util.List<PlantMutationRecord> mutations) {
    public GeneticValue(ResourceLocation species, SeedProfile profile) { this(species, profile, java.util.List.of()); }
    /** Minecraft adaptation: instance genetics retain a full local snapshot while taking only
     * species identity/product/visual/lifecycle genes from the resolved target profile. */
    public GeneticValue transformSpecies(ResourceLocation targetSpecies, SeedProfile target) {
        if (targetSpecies == null || target == null) throw new IllegalArgumentException("missing target species profile");
        var chemicals = new java.util.LinkedHashMap<>(profile.chemicals());
        chemicals.entrySet().removeIf(entry -> entry.getValue().inherent() && !target.chemicals().containsKey(entry.getKey()));
        target.chemicals().forEach((id, chemical) -> chemicals.putIfAbsent(id, chemical));
        var transformed = new SeedProfile(target.packet(), target.product(), target.visualKey(), profile.biology(),
                new SeedProfile.Lifecycle(target.lifecycle().growthStages(), profile.lifecycle().lifespan(),
                        profile.lifecycle().maturation(), profile.lifecycle().production(), profile.lifecycle().yield(),
                         profile.lifecycle().potency(), profile.lifecycle().harvest(), profile.lifecycle().viable(),
                          profile.lifecycle().seedless(), profile.lifecycle().ligneous()), chemicals, profile.consumeGasses(), profile.exudeGasses(), target.mutationTargets(),
                profile.withDefaultScreamSound().scream(), target.sourceName());
        return new GeneticValue(targetSpecies, transformed, mutations);
    }
    public GeneticValue adjustPotency(float delta) {
        var old = profile.lifecycle();
        var life = new SeedProfile.Lifecycle(old.growthStages(), old.lifespan(), old.maturation(), old.production(),
                old.yield(), Math.max(1, old.potency() + delta), old.harvest(), old.viable(), old.seedless(), old.ligneous());
        return withLifecycle(life);
    }
    public GeneticValue robustHarvest(float limit, float increase, float seedlessThreshold) {
        var old = profile.lifecycle();
        SeedProfile.Lifecycle life = old;
        if (old.potency() < limit) {
            float potency = Math.min(old.potency() + increase, limit);
            life = new SeedProfile.Lifecycle(old.growthStages(), old.lifespan(), old.maturation(), old.production(),
                    old.yield(), potency, old.harvest(), old.viable(), old.seedless() || potency > seedlessThreshold, old.ligneous());
        } else if (old.yield() > 1) {
            life = new SeedProfile.Lifecycle(old.growthStages(), old.lifespan(), old.maturation(), old.production(),
                    old.yield() - 1, old.potency(), old.harvest(), old.viable(), old.seedless(), old.ligneous());
        }
        return withLifecycle(life);
    }
    public GeneticValue diethylamine(boolean lifespanIncrease, boolean enduranceIncrease) {
        var oldLife = profile.lifecycle();
        var life = new SeedProfile.Lifecycle(oldLife.growthStages(), oldLife.lifespan() + (lifespanIncrease ? 1f : 0f),
                oldLife.maturation(), oldLife.production(), oldLife.yield(), oldLife.potency(), oldLife.harvest(),
                oldLife.viable(), oldLife.seedless(), oldLife.ligneous());
        var oldBio = profile.biology();
        var biology = new SeedProfile.Biology(oldBio.endurance() + (enduranceIncrease ? 1f : 0f),
                oldBio.nutrientConsumption(), oldBio.waterConsumption(), oldBio.idealHeat(), oldBio.heatTolerance(),
                oldBio.lowPressure(), oldBio.highPressure(), oldBio.weedTolerance(), oldBio.weedHighLevel(),
                oldBio.toxinTolerance(), oldBio.pestTolerance());
        return withProfile(biology, life);
    }
    public GeneticValue viable() {
        var old = profile.lifecycle();
        return withLifecycle(new SeedProfile.Lifecycle(old.growthStages(), old.lifespan(), old.maturation(),
                old.production(), old.yield(), old.potency(), old.harvest(), true, old.seedless(), old.ligneous()));
    }
    public GeneticValue restoreSeeds() {
        var old = profile.lifecycle();
        return withLifecycle(new SeedProfile.Lifecycle(old.growthStages(), old.lifespan(), old.maturation(),
                old.production(), old.yield(), old.potency(), old.harvest(), old.viable(), false, old.ligneous()));
    }
    private GeneticValue withLifecycle(SeedProfile.Lifecycle lifecycle) {
        return withProfile(profile.biology(), lifecycle);
    }
    private GeneticValue withProfile(SeedProfile.Biology biology, SeedProfile.Lifecycle lifecycle) {
        var copy = new SeedProfile(profile.packet(), profile.product(), profile.visualKey(), biology, lifecycle,
                profile.chemicals(), profile.consumeGasses(), profile.exudeGasses(), profile.mutationTargets(), profile.scream(), profile.sourceName());
        return new GeneticValue(species, copy, mutations);
    }
    public static final Codec<GeneticValue> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("species").forGetter(GeneticValue::species),
            SeedProfile.CODEC.fieldOf("profile").forGetter(GeneticValue::profile),
            PlantMutationRecord.CODEC.listOf().optionalFieldOf("mutations", java.util.List.of()).forGetter(GeneticValue::mutations)
    ).apply(i, GeneticValue::new));

    public GeneticValue {
        if (species == null || profile == null) throw new IllegalArgumentException("invalid genetic value");
        mutations = java.util.List.copyOf(mutations);
    }
    public GeneticValue withRecordedMutation(PlantMutationRecord mutation) {
        if (mutations.stream().anyMatch(existing -> existing.name().equals(mutation.name()))) return this;
        var copy = new java.util.ArrayList<>(mutations); copy.add(mutation);
        return new GeneticValue(species, profile, copy);
    }
}
