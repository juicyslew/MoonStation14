package com.juicyslew.moonstation14.ms14.loose_objects.appearance;

import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import org.junit.jupiter.api.Test;
import java.util.Map;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JugItemAppearanceTest {
    @Test void fillTiersFollowGeneratedJugModelThresholdsAndClampAtCapacity() {
        assertEquals(0, JugItemAppearance.fillTier(new ReagentComponent(), 15_000));
        assertEquals(0, JugItemAppearance.fillTier(amount(2_499), 15_000));
        assertEquals(1, JugItemAppearance.fillTier(amount(2_500), 15_000));
        assertEquals(2, JugItemAppearance.fillTier(amount(5_000), 15_000));
        assertEquals(3, JugItemAppearance.fillTier(amount(7_500), 15_000));
        assertEquals(4, JugItemAppearance.fillTier(amount(10_000), 15_000));
        assertEquals(5, JugItemAppearance.fillTier(amount(12_500), 15_000));
        assertEquals(6, JugItemAppearance.fillTier(amount(15_000), 15_000));
        assertEquals(6, JugItemAppearance.fillTier(amount(20_000), 15_000));
    }

    @Test void preservesNegativePackedArgbColorsAndFallsBackOnlyForNoTintSentinel() {
        assertEquals(0xff123456, JugItemAppearance.resolveColor(0xff123456));
        assertEquals(0xffffffff, JugItemAppearance.resolveColor(-1));
    }

    private static ReagentComponent amount(long cents) {
        var key = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
        return ReagentComponent.fromCents(Map.of(key, cents));
    }
}
