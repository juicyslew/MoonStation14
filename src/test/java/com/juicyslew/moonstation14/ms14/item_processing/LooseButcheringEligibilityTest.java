package com.juicyslew.moonstation14.ms14.item_processing;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LooseButcheringEligibilityTest {
    @Test void sharedRuntimeAndQuerySourceRuleRequiresKnifeComposition() {
        var profile = new ItemProcessingProfile.Butcherable(List.of(
                new ItemProcessingProfile.ItemOutput(ResourceLocation.withDefaultNamespace("bone"), 1)), 8,
                ItemProcessingProfile.Butcherable.Type.KNIFE);
        assertTrue(LooseButcheringService.supportsType(profile));
        assertFalse(LooseButcheringService.supportsType(null));
        // The enum currently models only KNIFE; broadening it must update this shared gate and its tests.
        assertEquals(1, ItemProcessingProfile.Butcherable.Type.values().length);
    }
}
