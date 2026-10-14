package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.codec.json.PlantMetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Applies the supported fixed-dose reagent effects and consumes one unit per reagent each poll. */
public final class PlantReagentMetabolism {
    public static final long POLL_CENTS = 100;

    private PlantReagentMetabolism() { }

    public interface Target {
        void adjustWater(float amount);
        void adjustNutrition(float amount);
    }

    public record UnsupportedEffect(String reagentId, String effectType) { }
    public record Result(List<UnsupportedEffect> unsupportedEffects) {
        public Result { unsupportedEffects = List.copyOf(unsupportedEffects); }
    }

    /** Resolve the whole snapshot before effects/removals, so stale catalog entries fail closed. */
    public static Result poll(ReagentAttachment source,
                              Function<ResourceKey<ReagentData>, ReagentData> resolvedLookup,
                              Target target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(resolvedLookup, "resolvedLookup");
        Objects.requireNonNull(target, "target");

        Map<ResourceKey<ReagentData>, Long> snapshot = source.snapshotUnits();
        Map<ResourceKey<ReagentData>, ReagentData> resolved = new LinkedHashMap<>();
        snapshot.forEach((key, cents) -> {
            if (cents > 0) resolved.put(key, Objects.requireNonNull(resolvedLookup.apply(key),
                    "Unknown reagent in metabolism source: " + key.location()));
        });

        List<UnsupportedEffect> unsupported = new ArrayList<>();
        for (var entry : snapshot.entrySet()) {
            if (entry.getValue() < POLL_CENTS) continue;
            ReagentData reagent = resolved.get(entry.getKey());
            for (PlantMetabolismData effect : reagent.plantMetabolism()) {
                if (effect instanceof PlantMetabolismData.PlantAdjustWater water) {
                    target.adjustWater(water.amount());
                } else if (effect instanceof PlantMetabolismData.PlantAdjustNutrition nutrition) {
                    target.adjustNutrition(nutrition.amount());
                } else {
                    unsupported.add(new UnsupportedEffect(reagent.id(), effect.type()));
                }
            }
        }
        for (ResourceKey<ReagentData> key : resolved.keySet()) source.removeUnits(key, POLL_CENTS);
        return new Result(unsupported);
    }
}
