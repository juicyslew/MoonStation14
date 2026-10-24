package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;

/** Source ChangeHarvest genetics effect. It changes instance genetics only; harvest automation is a separate consumer. */
public record PlantHarvestMutation() {
    public static final Codec<PlantHarvestMutation> CODEC = Codec.unit(new PlantHarvestMutation());

    public GeneticValue apply(GeneticValue value) {
        if (value == null) throw new IllegalArgumentException("missing mutation input");
        SeedProfile p = value.profile();
        var l = p.lifecycle();
        var harvest = switch (l.harvest()) {
            case NO_REPEAT -> SeedProfile.Harvest.REPEAT;
            case REPEAT -> SeedProfile.Harvest.SELF_HARVEST;
            case SELF_HARVEST -> SeedProfile.Harvest.SELF_HARVEST;
        };
        var lifecycle = new SeedProfile.Lifecycle(l.growthStages(), l.lifespan(), l.maturation(), l.production(),
                l.yield(), l.potency(), harvest, l.viable(), l.seedless(), l.ligneous());
        var profile = new SeedProfile(p.packet(), p.product(), p.visualKey(), p.biology(), lifecycle,
                p.chemicals(), p.consumeGasses(), p.exudeGasses(), p.mutationTargets(), p.scream(), p.sourceName());
        return new GeneticValue(value.species(), profile, value.mutations());
    }
}
