package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BodyTemperatureAttachment;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BodyTemperatureComponent;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BodyTemperatureSystem;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalExposureMath;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalRegulatorMath;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThermalGameTests {
    private ThermalGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "thermal_vacuum", timeoutTicks = 80)
    public static void strictExteriorVacuumCoolsWithoutGasWrite(GameTestHelper helper) {
        var level = helper.getLevel();
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos outside = new BlockPos(anchor.getX(), level.getMaxBuildHeight() - 10, anchor.getZ());
        level.getChunk(outside);
        var sample = service.sample(level, outside);
        if (sample.isEmpty() || service.readAtmosphere(level, outside).isEmpty()
                || service.readAtmosphere(level, outside).orElseThrow().status()
                != com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.EXTERIOR) {
            throw new AssertionError("fixture must provide a proven exterior sample");
        }
        if (sample.orElseThrow().heatCapacity() != 0) throw new AssertionError("exterior must be vacuum");
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(310.15));
        var policy = new ThermalRegulatorMath.Policy(310.15, 800, 100, 500, 2000, 2000, 2);
        var temperature = new ThermalExposureMath.TemperatureProfile(71.1963435119787, 42, 0.1);
        var damage = new ThermalExposureMath.DamageProfile(325, 260, 1.5, 0.1, 8);
        var space = new ThermalExposureMath.VacuumPolicy(7000, 8, 2.7);
        AtomicInteger gasWrites = new AtomicInteger();
        if (BodyTemperatureSystem.transact(body, sample.orElseThrow(), temperature,
                  damage, policy, space, ignored -> { gasWrites.incrementAndGet(); return true; }, ignored -> true)
                != BodyTemperatureSystem.Outcome.APPLIED || body.kelvin() >= 310.15 - 27
                || body.kelvin() <= 310.15 - 30 || gasWrites.get() != 0
                || sample.orElseThrow().totalMoles() != 0) {
            throw new AssertionError("vacuum must cool body without a gas-energy transaction");
        }
        var roomBody = new BodyTemperatureAttachment(new BodyTemperatureComponent(310.15));
        var evacuatedRoom = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(Map.of(), 2.7);
        if (BodyTemperatureSystem.transact(roomBody, evacuatedRoom, temperature,
                 null, policy, null, ignored -> { throw new AssertionError("no gas energy in evacuated room"); },
                 ignored -> { throw new AssertionError("no damage component"); })
                != BodyTemperatureSystem.Outcome.APPLIED || roomBody.kelvin() < 310.15) {
            throw new AssertionError("finite evacuated room must not receive exterior vacuum sink");
        }
        double prior = roomBody.kelvin();
        if (BodyTemperatureSystem.transact(roomBody, null, temperature,
                 damage, policy, space, ignored -> { throw new AssertionError("unknown gas write"); }, ignored -> true)
                != BodyTemperatureSystem.Outcome.SKIPPED || roomBody.kelvin() != prior) {
            throw new AssertionError("unknown sample must be inert");
        }
        helper.succeed();
    }
}
