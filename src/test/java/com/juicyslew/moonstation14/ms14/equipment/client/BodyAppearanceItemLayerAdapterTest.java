package com.juicyslew.moonstation14.ms14.equipment.client;

import com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyAppearanceItemLayerAdapterTest {
    @Test void descriptorDispatchKeepsNoneCoatOffMouseRenderer() {
        var profile = ResourceLocation.fromNamespaceAndPath("moonstation14", "bee_tiny");
        var animal = new PortableBodyAppearance(profile, "Bee", PortableBodyAppearance.Coat.NONE,
                PortableBodyAppearance.State.CRITICAL, "bee");
        var mouse = new PortableBodyAppearance(ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse_tiny"),
                "Mouse", PortableBodyAppearance.Coat.WHITE, PortableBodyAppearance.State.ALIVE);
        assertFalse(BodyAppearanceItemRenderer.usesMouseAdapter(animal));
        assertTrue(BodyAppearanceItemRenderer.usesMouseAdapter(mouse));
    }
}
