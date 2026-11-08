package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChemMasterPackagingPlanTest {
    private static ResourceKey<ReagentData> key(String value) {
        return ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", value));
    }

    @Test void pillPlanConservesWholeMixedCentMixtureAcrossSequentialDoses() {
        var a = key("pack-a"); var b = key("pack-b");
        var source = Map.of(a, 205L, b, 395L);
        var plan = ChemMasterPackagingPlan.pills(source, 3, 1, 20, 19, "Label");
        assertEquals(300, plan.withdrawalCents());
        assertEquals(3, plan.doses().size());
        assertTrue(plan.doses().stream().allMatch(d -> d.values().stream().mapToLong(Long::longValue).sum() == 100));
        var result = new java.util.HashMap<ResourceKey<ReagentData>, Long>(plan.sourceRemainder());
        plan.doses().forEach(d -> d.forEach((key, value) -> result.merge(key, value, Long::sum)));
        assertEquals(source, result);
        assertEquals(19, plan.pillType());
    }

    @Test void bottlePlanHonorsExactCapacityAndLabelUtf16Limit() {
        var a = key("pack-a"); var b = key("pack-b");
        var source = Map.of(a, 120L, b, 180L);
        var plan = ChemMasterPackagingPlan.bottle(source, 3, 300, "x".repeat(50));
        assertEquals(300, plan.withdrawalCents());
        assertEquals(source, plan.mixture());
        assertTrue(plan.sourceRemainder().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ChemMasterPackagingPlan.bottle(source, 1, 99, ""));
        assertThrows(IllegalArgumentException.class, () -> ChemMasterPackagingPlan.bottle(source, 1, 300, "x".repeat(51)));
    }

    @Test void invalidPillDoseCountTypeAndLabelAreRejected() {
        var source = Map.of(key("pack-a"), 5000L);
        assertThrows(IllegalArgumentException.class, () -> ChemMasterPackagingPlan.pills(source, 0, 1, 20, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> ChemMasterPackagingPlan.pills(source, 1, 21, 20, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> ChemMasterPackagingPlan.pills(source, 1, 1, 20, 20, ""));
        assertThrows(IllegalArgumentException.class, () -> ChemMasterPackagingPlan.pills(source, 1, 1, 20, 0, "x".repeat(51)));
        assertThrows(IllegalArgumentException.class, () -> ChemMasterPackagingPlan.pills(source, 3, 20, 20, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> ChemMasterPackagingPlan.pills(source, 1, 1, 0, 0, ""));
    }
}
