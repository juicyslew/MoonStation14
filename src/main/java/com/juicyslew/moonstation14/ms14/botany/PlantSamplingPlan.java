package com.juicyslew.moonstation14.ms14.botany;

/** Pure source sampling admission and packet-state calculation. Publication and sampled rolls belong to the runtime owner. */
public final class PlantSamplingPlan {
    public record Plan(float healthLoss, Float packetHealthOverride, float postCommitHealth, float endurance) { }
    private PlantSamplingPlan() { }

    public static boolean isEligible(PlantInstance plant) {
        if (plant == null || plant.seed() == null || plant.dead() || plant.sampled()) return false;
        SeedProfile current = plant.effectiveProfile();
        return current != null && PlantMetabolismEvaluator.rawGrowthStage(plant, current) > 1;
    }

    public static Plan prepare(PlantInstance plant, int damageRoll) {
        if (!isEligible(plant)) return null;
        SeedProfile seed = plant.effectiveProfile();
        float endurance = seed.biology().endurance();
        if (!Float.isFinite(endurance) || endurance < 0 || damageRoll < 3 || damageRoll > 4)
            throw new IllegalArgumentException("invalid sampling inputs");
        float loss = damageRoll * 10f;
        float rawPostLoss = plant.health() - loss;
        return new Plan(loss, plant.ready() ? null : rawPostLoss,
                Math.max(0, Math.min(endurance, rawPostLoss)), endurance);
    }
}
