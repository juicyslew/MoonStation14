package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TrustedPipeFixtureInitializationTest {
    @Test void comparesResolvedVolumeAgainstGasMixturesCanonicalFloatVolume() {
        var seed = new GasMixture(Map.of(GasType.NITROGEN, 1d), 300, .1);
        assertNotEquals(.1, seed.volumeCubicMeters());
        assertTrue(TrustedPipeFixtureInitialization.matchesCanonicalMixtureVolume(
                seed.volumeCubicMeters(), .1), "a correctly constructed 0.1 m3 mixture passes float canonicalization");
        assertFalse(TrustedPipeFixtureInitialization.matchesCanonicalMixtureVolume(1, .1),
                "a genuinely wrong port volume remains rejected");
        assertFalse(TrustedPipeFixtureInitialization.matchesCanonicalMixtureVolume(Double.NaN, .1));
        assertFalse(TrustedPipeFixtureInitialization.matchesCanonicalMixtureVolume(.1, Double.POSITIVE_INFINITY));
    }

    @Test void combinesPerPortSeedContributionsOnceAtWholeNetworkVolume() {
        var first=new GasMixture(Map.of(GasType.NITROGEN,2d),300,.25);
        var second=new GasMixture(Map.of(GasType.NITROGEN,3d),300,.25);

        var network=TrustedPipeFixtureInitialization.mergeContributions(java.util.List.of(first,second),1.0);

        assertEquals(5d,network.moles(GasType.NITROGEN),1e-6);
        assertEquals(1.0,network.volumeCubicMeters());
        assertEquals(first.thermalEnergy()+second.thermalEnergy(),network.thermalEnergy(),1e-3);
    }
}
