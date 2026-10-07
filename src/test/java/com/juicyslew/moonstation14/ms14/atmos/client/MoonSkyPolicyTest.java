package com.juicyslew.moonstation14.ms14.atmos.client;

import com.juicyslew.moonstation14.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoonSkyPolicyTest {
    @Test
    void rendersOnlyInOverworld() {
        assertTrue(Config.shouldRenderMoonSky(Level.OVERWORLD));
        assertFalse(Config.shouldRenderMoonSky(Level.NETHER));
        assertFalse(Config.shouldRenderMoonSky(Level.END));

        ResourceKey<Level> other = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("example", "other"));
        assertFalse(Config.shouldRenderMoonSky(other));
    }
}
