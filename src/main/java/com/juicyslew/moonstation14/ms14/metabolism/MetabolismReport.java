package com.juicyslew.moonstation14.ms14.metabolism;

import com.juicyslew.moonstation14.component.codec.json.MetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable result of one metabolism-system invocation. */
public record MetabolismReport(List<MetabolismAttempt> attempts) {
    public MetabolismReport {
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
    }

    public record MetabolismAttempt(
            ResourceKey<ReagentData> reagent,
            MetabolismStage stage,
            MetabolismSystem.SourceSnapshot sourceSnapshot,
            MetabolismData metabolism,
            MetabolismResult<ResourceKey<ReagentData>> result,
            ReagentAttachment.CapacityAddResult<ResourceKey<ReagentData>> capacity,
            ReagentAttachment.UnitsCapacityAddResult<ResourceKey<ReagentData>> capacityUnits
    ) {
        public MetabolismAttempt {
            Objects.requireNonNull(reagent, "reagent");
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
            Objects.requireNonNull(metabolism, "metabolism");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(capacity, "capacity");
            Objects.requireNonNull(capacityUnits, "capacityUnits");
        }

        public float actualRemoved() {
            return result.actualRemoved();
        }

        public float scale() {
            return result.scale();
        }

        public Map<ResourceKey<ReagentData>, Float> requestedProduction() {
            return result.requestedProduction();
        }

        public Map<ResourceKey<ReagentData>, Float> retained() {
            return capacity.retained();
        }

        public Map<ResourceKey<ReagentData>, Float> excess() {
            return capacity.excess();
        }

        /** Exact requested, retained and excess product quantities in cents. */
        public ReagentAttachment.UnitsCapacityAddResult<ResourceKey<ReagentData>> exactCapacity() {
            return capacityUnits;
        }

        public Map<ResourceKey<ReagentData>, Float> requested() {
            return capacity.requested();
        }

        public ResourceKey<ReagentData> reagentKey() {
            return reagent;
        }

        public MetabolismData definition() {
            return metabolism;
        }
    }
}
