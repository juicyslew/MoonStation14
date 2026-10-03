package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotKernel;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereHotspotMutationGameTests {
    private AtmosphereHotspotMutationGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_hotspot_mutation", timeoutTicks = 100)
    public static void successfulExternalThermalWriteReconcilesActualHotspotAndRejectedWriteDoesNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        require(!AtmosphereService.INSTANCE.isEnabled(), "isolated fixture requires singleton atmosphere disabled");
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            for (int y = 1; y <= 2; y++)
                helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 2 ? Blocks.AIR : Blocks.STONE);
        }
        BlockPos cell = helper.absolutePos(new BlockPos(2, 1, 2));
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        for (int pass = 1; pass <= 12 && !claimed(level, cell); pass++) {
            service.sample(level, cell);
            service.tick(level, (long) pass * AtmosphereService.TICK_CADENCE);
        }
        require(claimed(level, cell), "test cell must be finite");
        require(service.addGas(level, cell, GasType.TRITIUM, 2, 900), "inject tritium");
        require(service.addGas(level, cell, GasType.OXYGEN, 4, 900), "inject oxygen");
        waitForDue(helper, level, 0, () -> {
            require(service.reactFiniteCell(level, cell).isPresent(), "must seed an actual fractional hotspot");
            HotspotKernel.HotspotState prior = actualHotspot(service, level, cell);
            require(prior != null && prior.fraction() < 1, "reaction must retain an actual fractional hotspot");
            GasMixture before = service.sample(level, cell).orElseThrow();
            require(service.addEnergy(level, cell, -before.thermalEnergy() * .20), "cooling write must commit");
            GasMixture cooled = service.sample(level, cell).orElseThrow();
            HotspotKernel.HotspotState reconciled = actualHotspot(service, level, cell);
            require(reconciled != null && reconciled.temperatureKelvin() < prior.temperatureKelvin(),
                    "successful external write must reconcile the queued hotspot temperature");
            double hotEnergy = reconciled.fraction() * cooled.heatCapacity() * reconciled.temperatureKelvin();
            require(hotEnergy <= cooled.thermalEnergy() + Math.max(1e-8, cooled.thermalEnergy() * 1e-12),
                    "reconciled hotspot partition cannot exceed committed gas energy");

            GasMixture unchangedGas = cooled;
            HotspotKernel.HotspotState unchangedHotspot = reconciled;
            require(!service.addEnergy(level, cell, -Double.MAX_VALUE), "invalid unaffordable write must fail");
            require(same(unchangedGas, service.sample(level, cell).orElseThrow()),
                    "failed write must not change gas");
            require(unchangedHotspot.equals(actualHotspot(service, level, cell)),
                    "failed write must not change actual queued hotspot metadata");
            helper.succeed();
        });
    }

    private static HotspotKernel.HotspotState actualHotspot(AtmosphereService service, ServerLevel level, BlockPos pos) {
        try {
            Field queuesField = AtmosphereService.class.getDeclaredField("reactionQueues");
            queuesField.setAccessible(true);
            Object queue = ((Map<?, ?>) queuesField.get(service)).get(level);
            if (queue == null) return null;
            Method hotspot = queue.getClass().getDeclaredMethod("hotspot", BlockPos.class);
            hotspot.setAccessible(true);
            return (HotspotKernel.HotspotState) hotspot.invoke(queue, pos);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("could not inspect actual reaction queue hotspot: " + failure);
        }
    }

    private static void waitForDue(GameTestHelper helper, ServerLevel level, int waited, Runnable action) {
        if (level.getGameTime() % AtmosphereService.TICK_CADENCE == 0) action.run();
        else if (waited >= AtmosphereService.TICK_CADENCE + 1)
            throw new GameTestAssertException("could not reach due atmosphere tick");
        else helper.runAfterDelay(1, () -> waitForDue(helper, level, waited + 1, action));
    }

    private static boolean claimed(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = (LevelChunk) level.getChunk(pos);
        var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        return data != null && data.isFiniteClaimed(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
    }

    private static boolean same(GasMixture first, GasMixture second) {
        return first.gasMoles().equals(second.gasMoles())
                && Math.abs(first.thermalEnergy() - second.thermalEnergy()) <= Math.max(1e-8, first.thermalEnergy() * 1e-12);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
