package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.BreathExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.function.Predicate;

/** Uses the actual loaded world cell; unknown atmosphere remains unavailable rather than invented. */
public final class WorldCellExposureAir implements ExposureAirSource {
    private final ServerLevel level;
    private final BlockPos cell;
    public WorldCellExposureAir(ServerLevel level, BlockPos cell) {
        this.level = java.util.Objects.requireNonNull(level);
        this.cell = java.util.Objects.requireNonNull(cell).immutable();
    }
    @Override public Optional<Sample> sample() {
        return AtmosphereService.INSTANCE.sample(level, cell).map(gas -> new Sample(gas, gas.volumeCubicMeters()));
    }
    @Override public Optional<BreathExchange> exchange(double requestedMoles, GasMixture exhaled,
                                                        Predicate<GasMixture> validator) {
        return AtmosphereService.INSTANCE.exchangeBreath(level, cell, requestedMoles, exhaled, validator);
    }
    @Override public boolean exchangeThermalEnergy(double joules) {
        return AtmosphereService.INSTANCE.addEnergy(level, cell, joules);
    }
}
