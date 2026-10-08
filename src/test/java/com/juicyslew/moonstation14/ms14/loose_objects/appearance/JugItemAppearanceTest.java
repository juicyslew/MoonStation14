package com.juicyslew.moonstation14.ms14.loose_objects.appearance;

import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import org.junit.jupiter.api.Test;
import java.util.Map;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.ms14.jug.JugData;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;
import net.minecraft.world.item.Item;

class JugItemAppearanceTest {
    @Test void nullLevelAppearanceUsesNormalRegisteredJugAndSafeResolvedDefault() {
        assertEquals(20_000, JugItemAppearance.capacityCents((net.minecraft.world.level.Level) null));
    }

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

    @Test void levelAwareCapacityHelperUsesEachResolvedJugInstanceInsteadOfServerCatalog() {
        var examine = new JugData.ExaminableSolution("drink", true, false, true, 3);
        var low = new JugData(ExamineMetadata.EMPTY, new JugData.SolutionContainer("drink", 100f), examine);
        var high = new JugData(ExamineMetadata.EMPTY, new JugData.SolutionContainer("drink", 500f), examine);
        int lowCapacity = Math.round(JugItem.capacityFromResolved(low) * 100f);
        int highCapacity = Math.round(JugItem.capacityFromResolved(high) * 100f);
        assertEquals(10_000, lowCapacity);
        assertEquals(50_000, highCapacity);
        assertEquals(6, JugItemAppearance.fillTier(amount(20_000), lowCapacity));
        assertEquals(2, JugItemAppearance.fillTier(amount(20_000), highCapacity));
    }

    private static ReagentComponent amount(long cents) {
        var key = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
        return ReagentComponent.fromCents(Map.of(key, cents));
    }
}
