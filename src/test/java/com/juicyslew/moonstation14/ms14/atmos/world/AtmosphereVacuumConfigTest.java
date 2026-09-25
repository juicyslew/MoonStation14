package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.Config;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereVacuumConfigTest {
    @Test
    void commonConfigValidatesAndParsesDimensionIds() {
        assertTrue(Config.isValidAtmosphereDimension("minecraft:the_nether"));
        assertFalse(Config.isValidAtmosphereDimension("not a dimension id"));
        assertFalse(Config.isValidAtmosphereDimension(""));
        assertFalse(Config.isValidAtmosphereDimension(42));

        ResourceKey<Level> nether = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"));
        assertEquals(Set.of(nether), Config.parseAtmosphereVacuumDimensions(List.of("minecraft:the_nether")));
        assertTrue(Config.parseAtmosphereVacuumDimensions(List.of()).isEmpty());
    }

    @Test
    void productionPolicyIsConfiguredPerServerAndResetOnStop() {
        AtmosphereService service = AtmosphereService.INSTANCE;
        ResourceKey<Level> moon = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("example", "moon"));
        try {
            assertEquals(GasMixture.breathableAir().gasMoles(), service.ambient(moon).gasMoles());
            service.configureAtServerStart(Set.of(moon));
            assertEquals(GasMixture.vacuum().gasMoles(), service.ambient(moon).gasMoles());
            ResourceKey<Level> end = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "the_end"));
            assertEquals(GasMixture.breathableAir().gasMoles(), service.ambient(end).gasMoles());
        } finally {
            service.onServerStopped();
        }
        assertEquals(GasMixture.breathableAir().gasMoles(), service.ambient(moon).gasMoles());
    }

    @Test
    void ambientWriteComparisonDoesNotDiscardSubEpsilonChanges() {
        GasMixture ambient = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 293.15);
        GasMixture almostAmbient = new GasMixture(Map.of(GasType.OXYGEN, 1.0 + 1.0e-10), 293.15);
        assertFalse(AtmosphereService.same(ambient, almostAmbient));
        assertTrue(AtmosphereService.same(ambient, ambient));
    }
}
