package com.juicyslew.moonstation14.ms14.metabolism;

import com.juicyslew.moonstation14.component.codec.json.MetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;

/** Immutable data made available to an effect router during an attempt. */
public record MetabolismEffectInvocation(
        ResourceKey<ReagentData> reagent,
        MetabolismStage stage,
        MetabolismSystem.SourceSnapshot sourceSnapshot,
        MetabolismData metabolism,
        MetabolismResult<ResourceKey<ReagentData>> result
) {
    public MetabolismEffectInvocation {
        Objects.requireNonNull(reagent, "reagent");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        Objects.requireNonNull(metabolism, "metabolism");
        Objects.requireNonNull(result, "result");
    }

    public float actualRemoved() {
        return result.actualRemoved();
    }

    public float scale() {
        return result.scale();
    }

    public java.util.Map<ResourceKey<ReagentData>, Float> requestedProduction() {
        return result.requestedProduction();
    }

    public ResourceKey<ReagentData> reagentKey() {
        return reagent;
    }

    public MetabolismData definition() {
        return metabolism;
    }
}
