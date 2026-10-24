package com.juicyslew.moonstation14.ms14.blood;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BloodstreamWholeAdditionTest {
    private static final ResourceKey<ReagentData> A = key("a");
    private static final ResourceKey<ReagentData> B = key("b");

    @Test void exactFitAcceptedAndOverCapacityRejectedWithoutPartialFitOrMutation() {
        Map<ResourceKey<ReagentData>, Long> before = Map.of(A, 80L);
        Map<ResourceKey<ReagentData>, Long> dose = Map.of(B, 20L);
        assertEquals(Map.of(A, 80L, B, 20L), BloodstreamSolutionAccess.wholeDoseCandidate(before, dose, 100));
        assertNull(BloodstreamSolutionAccess.wholeDoseCandidate(before, Map.of(B, 21L), 100));
        assertEquals(Map.of(A, 80L), before);
        assertEquals(Map.of(B, 20L), dose);
    }

    @Test void rejectsAdditionOverflowNegativeAndZeroDoseEntries() {
        assertNull(BloodstreamSolutionAccess.wholeDoseCandidate(Map.of(A, Long.MAX_VALUE), Map.of(A, 1L), Long.MAX_VALUE));
        assertNull(BloodstreamSolutionAccess.wholeDoseCandidate(Map.of(), Map.of(A, -1L), 100));
        assertNull(BloodstreamSolutionAccess.wholeDoseCandidate(Map.of(), Map.of(A, 0L), 100));
    }

    private static ResourceKey<ReagentData> key(String path) {
        return ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }
}
