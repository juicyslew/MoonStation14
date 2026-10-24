package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MeleeMultiInjectionTest {
    private static final ResourceKey<ReagentData> A = key("a"), B = key("b");
    @Test void distributionUsesActualDetachedAttachmentSplitsAndDropsRemainder() {
        var five = distribute(Map.of(A, 300L), 5);
        assertEquals(5, five.size());
        assertTrue(five.stream().allMatch(share -> share.equals(Map.of(A, 60L))));
        var three = distribute(Map.of(A, 200L), 3);
        assertEquals(3, three.size());
        assertTrue(three.stream().allMatch(share -> share.equals(Map.of(A, 66L))));
        assertEquals(2, 200L - three.stream().mapToLong(s -> s.getOrDefault(A, 0L)).sum());
    }

    @Test void mixedReagentsRemainCentExactAcrossShares() {
        var source = new ReagentAttachment(ReagentComponent.fromCents(Map.of(A, 120L, B, 80L)));
        var dose = source.splitUnits(200);
        var shares = MeleeChemicalInjectorSystem.distributeDetachedDose(dose, 3);
        assertEquals(3, shares.size());
        assertEquals(198L, shares.stream().mapToLong(s -> s.values().stream().mapToLong(Long::longValue).sum()).sum());
        assertEquals(2L, dose.values().stream().mapToLong(Long::longValue).sum()
                - shares.stream().flatMap(s -> s.values().stream()).mapToLong(Long::longValue).sum());
    }

    private static java.util.List<Map<ResourceKey<ReagentData>, Long>> distribute(Map<ResourceKey<ReagentData>, Long> input, int count) {
        var source = new ReagentAttachment(ReagentComponent.fromCents(input));
        return MeleeChemicalInjectorSystem.distributeDetachedDose(source.splitUnits(source.totalUnits()), count);
    }
    private static ResourceKey<ReagentData> key(String path) {
        return ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }
}
