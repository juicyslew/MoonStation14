package com.juicyslew.moonstation14.ms14.botany;

/** Package bridge for tests in other packages that need domain fixture state. */
public final class PlantInstanceTestAccess {
    private PlantInstanceTestAccess() { }

    public static void state(PlantInstance plant, float age, float health, float lastProduceAge,
                             float skipAging, boolean ready, boolean dead) {
        plant.state(age, health, lastProduceAge, skipAging, ready, dead);
    }

    public static void soil(PlantInstance plant, float water, float nutrition, float weeds, float toxins) {
        plant.soil(water, nutrition, weeds, toxins);
    }

    public static void plant(PlantInstance plant, net.minecraft.resources.ResourceLocation seed,
                             SeedProfile profile, long now) {
        plant.plant(seed, profile, now);
    }
}
