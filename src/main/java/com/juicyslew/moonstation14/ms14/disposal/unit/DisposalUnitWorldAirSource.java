package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ExposureAirSource;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.BreathExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;
import java.util.function.Predicate;

/** Pre-flush occupants breathe the known room cell above the unit; unknown air never becomes vacuum/ambient. */
public final class DisposalUnitWorldAirSource implements ExposureAirSource {
    private static final double ROOM_VOLUME_M3 = 1.0;
    private final ServerLevel level;
    private final BlockPos cell;
    public DisposalUnitWorldAirSource(ServerLevel level, BlockPos unitPos) {
        this.level = java.util.Objects.requireNonNull(level);
        this.cell = java.util.Objects.requireNonNull(unitPos).above().immutable();
    }
    @Override public Optional<Sample> sample() {
        return AtmosphereService.INSTANCE.sample(level, cell).map(gas -> new Sample(gas, ROOM_VOLUME_M3));
    }
    @Override public Optional<BreathExchange> exchange(double requestedMoles, GasMixture exhaled, Predicate<GasMixture> validator) {
        return AtmosphereService.INSTANCE.exchangeBreath(level, cell, requestedMoles, exhaled, validator);
    }
    @Override public boolean exchangeThermalEnergy(double joules) {
        return AtmosphereService.INSTANCE.addEnergy(level, cell, joules);
    }
}
