package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmosphereProfilesTest {
    @Test
    void defaultsEveryDimensionToAirAndVacuumRequiresExplicitOptIn() {
        ResourceKey<Level> overworld = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
        ResourceKey<Level> custom = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("example", "moon"));
        assertEquals(GasMixture.breathableAir().gasMoles(), AtmosphereProfiles.ambient(overworld).gasMoles());
        assertEquals(GasMixture.breathableAir().gasMoles(), AtmosphereProfiles.ambient(custom).gasMoles());
        assertEquals(GasMixture.vacuum().gasMoles(), AtmosphereProfiles.ambient(custom, Set.of(custom)).gasMoles());
        assertEquals(GasMixture.breathableAir().gasMoles(), AtmosphereProfiles.ambient(overworld, Set.of(custom)).gasMoles());
    }
}
