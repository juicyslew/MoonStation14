package com.juicyslew.moonstation14.ms14.medical.syringe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyringeModeServiceTest {
    private static ResourceLocation id(String path) { return ResourceLocation.withDefaultNamespace(path); }

    @Test void cyclesResolvedModeChoicesAndRejectsInvalidMetadata() {
        var allowed = List.of(id("draw"), id("inject"), id("custom"));
        assertEquals(id("inject"), SyringeModeService.nextMode(allowed, id("draw")));
        assertEquals(id("draw"), SyringeModeService.nextMode(allowed, id("custom")));
        assertNull(SyringeModeService.nextMode(allowed, id("unknown")));
        assertNull(SyringeModeService.nextMode(List.of(), id("draw")));
    }

    @Test void cyclesPrototypeDoseChoicesWithoutAssumingFiveTenOrFifteen() {
        var choices = List.of(2f, 7.5f, 22f);
        assertEquals(7.5f, SyringeModeService.nextDose(choices, 2f));
        assertEquals(2f, SyringeModeService.nextDose(choices, 22f));
        assertNull(SyringeModeService.nextDose(choices, Float.NaN));
    }
}
