package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.Config;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereEnableSwitchTest {
    @Test
    void commonConfigDefaultsOff() {
        assertFalse(Config.ENABLE_ATMOSPHERICS.get());
    }

    @Test
    void productionServiceStartsOffAndStopResetsTheStartupSnapshot() {
        AtmosphereService service = AtmosphereService.INSTANCE;
        service.onServerStopped();
        assertFalse(service.isEnabled());
        assertTrue(service.sample(null, null).isEmpty());

        ResourceKey<Level> moon = dimension("example", "moon");
        service.configureAtServerStart(true, Set.of(moon));
        assertTrue(service.isEnabled());
        assertEquals(GasMixture.vacuum().gasMoles(), service.ambient(moon).gasMoles());

        service.onServerStopped();
        assertFalse(service.isEnabled());
        assertEquals(GasMixture.breathableAir().gasMoles(), service.ambient(moon).gasMoles());
        assertEquals(0, service.queueCountForTesting());
    }

    @Test
    void disabledPublicMethodsShortCircuitWithoutCreatingWorkQueues() {
        AtmosphereService service = AtmosphereService.INSTANCE;
        service.onServerStopped();

        assertTrue(service.sample(null, null).isEmpty());
        assertFalse(service.addGas(null, null, GasType.OXYGEN, 1.0, 293.15));
        assertFalse(service.addBreathableAir(null, null, 1.0, 293.15));
        assertEquals(0.0, service.removeGasUpTo(null, null, 1.0));
        assertFalse(service.addEnergy(null, null, 1.0));
        service.invalidate(null, null);
        service.tick(null, 0);
        assertEquals(0, service.queueCountForTesting());
    }

    @Test
    void startupTakesAnImmutableVacuumPolicySnapshot() {
        AtmosphereService service = AtmosphereService.INSTANCE;
        Set<ResourceKey<Level>> policy = new HashSet<>();
        ResourceKey<Level> moon = dimension("example", "moon");
        policy.add(moon);
        try {
            service.configureAtServerStart(true, policy);
            policy.clear();
            assertEquals(GasMixture.vacuum().gasMoles(), service.ambient(moon).gasMoles());
        } finally {
            service.onServerStopped();
        }
    }

    private static ResourceKey<Level> dimension(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
