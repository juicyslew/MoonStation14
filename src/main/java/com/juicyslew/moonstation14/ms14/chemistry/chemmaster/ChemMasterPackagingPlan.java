package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure, cent-exact whole-mixture allocation for one ChemMaster packaging request. */
public final class ChemMasterPackagingPlan {
    public static final int MAX_LABEL_LENGTH = 50;
    public record Pills(long withdrawalCents, Map<ResourceKey<ReagentData>, Long> sourceRemainder,
                        List<Map<ResourceKey<ReagentData>, Long>> doses, int pillType, String label) {
        public Pills {
            ReagentUnits.validateCents(withdrawalCents, "withdrawalCents");
            sourceRemainder = Map.copyOf(sourceRemainder);
            doses = doses.stream().map(Map::copyOf).toList();
            Objects.requireNonNull(label);
        }
    }
    public record Bottle(long withdrawalCents, Map<ResourceKey<ReagentData>, Long> sourceRemainder,
                         Map<ResourceKey<ReagentData>, Long> mixture, String label) {
        public Bottle {
            ReagentUnits.validateCents(withdrawalCents, "withdrawalCents");
            sourceRemainder = Map.copyOf(sourceRemainder); mixture = Map.copyOf(mixture);
            Objects.requireNonNull(label);
        }
    }
    private ChemMasterPackagingPlan() { }

    public static Pills pills(Map<ResourceKey<ReagentData>, Long> source, int count, int doseUnits,
                              int doseLimitUnits, int pillType, String label) {
        validateCommon(source, label);
        if (count < 1 || doseUnits < 1 || doseLimitUnits < 1 || doseUnits > doseLimitUnits
                || pillType < 0 || pillType > 19) throw new IllegalArgumentException("invalid pill request");
        long dose = Math.multiplyExact((long) doseUnits, 100L);
        long total = Math.multiplyExact(dose, count);
        if (ReagentUnits.total(source.values()) < total) throw new IllegalArgumentException("insufficient source");
        var remaining = new ReagentAttachment(ReagentComponent.fromCents(source));
        var doses = new ArrayList<Map<ResourceKey<ReagentData>, Long>>(count);
        for (int i = 0; i < count; i++) doses.add(remaining.splitUnits(dose));
        return new Pills(total, remaining.snapshotUnits(), doses, pillType, label);
    }

    public static Bottle bottle(Map<ResourceKey<ReagentData>, Long> source, int doseUnits, long availableCapacityCents,
                                String label) {
        validateCommon(source, label);
        if (doseUnits < 1 || availableCapacityCents < 0) throw new IllegalArgumentException("invalid bottle request");
        long amount = Math.multiplyExact((long) doseUnits, 100L);
        if (amount > availableCapacityCents || ReagentUnits.total(source.values()) < amount)
            throw new IllegalArgumentException("insufficient source or output room");
        var remaining = new ReagentAttachment(ReagentComponent.fromCents(source));
        var mixture = remaining.splitUnits(amount);
        return new Bottle(amount, remaining.snapshotUnits(), mixture, label);
    }

    private static void validateCommon(Map<ResourceKey<ReagentData>, Long> source, String label) {
        Objects.requireNonNull(source); Objects.requireNonNull(label);
        if (label.length() > MAX_LABEL_LENGTH) throw new IllegalArgumentException("label too long");
        ReagentUnits.total(source.values());
    }
}
