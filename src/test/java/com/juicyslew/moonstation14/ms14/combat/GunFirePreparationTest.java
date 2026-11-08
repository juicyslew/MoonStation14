package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunFirePreparationTest {
    @Test void deferredSpentCasingMaterializesRegisteredCartridge() {
        var casing = GunFireSystem.materializeSpentCartridge(ResourceLocation.fromNamespaceAndPath("moonstation14", "cartridge_magnum"));
        assertNotNull(casing);
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "cartridge_magnum"),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(casing.getItem()));
        assertTrue(casing.get(ModDataComponents.CARTRIDGE_SPENT.get()));
    }
    @Test void launchVelocityIncludesShooterMotionAndRejectsInvalidInputs() {
        var v = GunFireSystem.projectileVelocity(new Vec3(0, 0, 2), 0, 40, new Vec3(1, .5, 0));
        assertEquals(new Vec3(1, .5, 2), v);
        assertThrows(IllegalArgumentException.class, () -> GunFireSystem.projectileVelocity(Vec3.ZERO, 0, 40, Vec3.ZERO));
    }
}
