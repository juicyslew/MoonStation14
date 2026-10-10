package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.medical.MedicalItemData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BodyHandTargetUseMedicalLookupTest {
    @Test void ordinaryLiquidContainerAbsentFromMedicalCatalogFallsThrough() {
        var catalog = new PrototypeCatalog<MedicalItemData>(Map.of());
        var medical = BodyHandRequestService.targetUseMedicalLookup(ResourceLocation.withDefaultNamespace("beaker"), catalog);
        assertNull(medical);
        var invoked = new java.util.concurrent.atomic.AtomicInteger();
        String route = BodyHandRequestService.dispatchTargetUse(medical,
                () -> "syringe", () -> { invoked.incrementAndGet(); return "generic-liquid-fill"; });
        assertEquals("generic-liquid-fill", route);
        assertEquals(1, invoked.get());
    }
}
