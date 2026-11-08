package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Pure fixed-point domain plan for slicing one current source solution into independent outputs. */
public final class SlicingPlan {
    private SlicingPlan() { }

    /** Each sequential slice extracts from the remaining source before output-capacity loss is applied. */
    public static List<Map<ResourceKey<ReagentData>, Long>> prepare(
            Map<ResourceKey<ReagentData>, Long> sourceCents, int count, long outputCapacityCents) {
        Objects.requireNonNull(sourceCents, "sourceCents");
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        ReagentUnits.validateCents(outputCapacityCents, "outputCapacityCents");
        long request = ReagentUnits.total(sourceCents.values()) / count;
        Map<ResourceKey<ReagentData>, Long> remaining = new LinkedHashMap<>(sourceCents);
        var result = new ArrayList<Map<ResourceKey<ReagentData>, Long>>(count);
        for (int i = 0; i < count; i++) {
            Map<ResourceKey<ReagentData>, Long> extracted = ReagentUnits.split(remaining, request);
            for (var entry : extracted.entrySet()) {
                long left = remaining.getOrDefault(entry.getKey(), 0L) - entry.getValue();
                if (left == 0) remaining.remove(entry.getKey());
                else remaining.put(entry.getKey(), left);
            }
            long admitted = Math.min(request, outputCapacityCents);
            result.add(ReagentUnits.split(extracted, admitted));
        }
        return List.copyOf(result);
    }
}
