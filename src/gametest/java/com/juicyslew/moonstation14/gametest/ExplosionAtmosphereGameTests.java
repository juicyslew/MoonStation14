package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.Config;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.explosion.server.ServerExplosionService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExplosionAtmosphereGameTests {
    private static final double INJECTED_MOLES = 8.0;
    private static final BlastPlanner.Proposal PROPOSAL = new BlastPlanner.Proposal(120, 3, 10);

    private ExplosionAtmosphereGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "explosion_atmos_enabled", timeoutTicks = 100)
    public static void blastBreachNotifiesEnabledGlobalAtmosphere(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService global = AtmosphereService.INSTANCE;
        boolean startupEnabled = Config.ENABLE_ATMOSPHERICS.get();
        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> startupVacuumDimensions =
                Config.parseAtmosphereVacuumDimensions(Config.ATMOSPHERE_VACUUM_DIMENSIONS.get());
        require(global.isEnabled() == startupEnabled, "global atmosphere must match startup configuration before fixture");

        // Match the proven covered-room fixture: a roofed single-cell room with one
        // sky-exposed side gap, sealed with steel before the global service classifies it.
        for (int x = 0; x <= 2; x++) for (int z = 0; z <= 3; z++)
            helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        for (int x = 0; x <= 3; x++) for (int z = 0; z <= 3; z++)
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        for (int i = 0; i <= 3; i++) {
            helper.setBlock(new BlockPos(0, 1, i), Blocks.STONE);
            helper.setBlock(new BlockPos(3, 1, i), Blocks.STONE);
            helper.setBlock(new BlockPos(i, 1, 0), Blocks.STONE);
            helper.setBlock(new BlockPos(i, 1, 3), Blocks.STONE);
        }
        BlockPos localGap = new BlockPos(3, 1, 1);
        helper.setBlock(localGap, Blocks.AIR);
        BlockPos absoluteGap = helper.absolutePos(localGap);
        int firstFreeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, absoluteGap.getX(), absoluteGap.getZ());
        int structureYOffset = absoluteGap.getY() - localGap.getY();
        for (int localY = 2; localY < firstFreeY - structureYOffset && localY <= 8; localY++)
            helper.setBlock(new BlockPos(3, localY, 1), Blocks.AIR);
        helper.setBlock(localGap, ModBlocks.STEEL_WALL_BLOCK.get());
        BlockPos room = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos breach = absoluteGap;
        BlockPos source = helper.absolutePos(new BlockPos(4, 1, 1));

        try {
            global.configureAtServerStart(true, Set.of(level.dimension()));
            require(global.isEnabled(), "global service must be enabled for blast fixture");
            require(classifyUntilSample(global, level, room, 20), "sealed room must classify before blast");
            require(global.addBreathableAir(level, room, INJECTED_MOLES, 293.15),
                    "sealed finite room accepts gas injection; sampled room=" + global.sample(level, room));
            double before = global.sample(level, room).orElseThrow().totalMoles();
            require(before > 0.0, "room contains gas before blast");

            var result = ServerExplosionService.execute(level, source, ModExplosions.DEFAULT_ID, PROPOSAL);
            require(result.status() == ServerExplosionService.Status.APPLIED, "server blast applied: " + result);
            require(level.getBlockState(breach).isAir(), "blast breaks the steel wall at the room boundary");
            require(global.sample(level, room).orElseThrow().totalMoles() > 0.0,
                    "blast topology notification retains finite gas immediately after breach");

            for (int tick = 1; tick <= 60; tick++) global.tick(level, tick);
            double after = global.sample(level, room).orElseThrow().totalMoles();
            require(after < before * 0.5,
                    "notified breach allows gas to drain/export on due passes; before=" + before + ", after=" + after);
            helper.succeed();
        } finally {
            global.configureAtServerStart(startupEnabled, startupVacuumDimensions);
            require(global.isEnabled() == startupEnabled,
                    "global atmosphere enablement restored to startup setting");
        }
    }

    @GameTest(template = "atmos_large_empty", batch = "explosion_atmos_hotspot", timeoutTicks = 140)
    public static void defaultBlastPrimesFiniteReactiveCellWithoutInstantHeating(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService global = AtmosphereService.INSTANCE;
        boolean startupEnabled = Config.ENABLE_ATMOSPHERICS.get();
        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> startupVacuumDimensions =
                Config.parseAtmosphereVacuumDimensions(Config.ATMOSPHERE_VACUUM_DIMENSIONS.get());
        require(global.isEnabled() == startupEnabled, "global atmosphere must match startup configuration before fixture");

        // One sealed, loaded, passable cell; bedrock keeps the default blast from
        // breaching the shell while it exposes the gas cell to the hotspot.
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            helper.setBlock(new BlockPos(x, 2, z), Blocks.BEDROCK);
            helper.setBlock(new BlockPos(x, 1, z), x == 2 && z == 2 ? Blocks.AIR : Blocks.BEDROCK);
        }
        BlockPos cell = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            global.configureAtServerStart(true, Set.of(level.dimension()));
            require(classifyUntilSample(global, level, cell, 20), "sealed blast cell must classify as finite");
            require(global.addGas(level, cell, GasType.TRITIUM, 4, 293.15), "inject finite tritium");
            require(global.addGas(level, cell, GasType.OXYGEN, 4, 293.15), "inject finite oxygen");
            var before = global.sample(level, cell).orElseThrow();
            require(before.temperatureKelvin() < 800 && before.moles(GasType.TRITIUM) >= 4
                            && before.moles(GasType.OXYGEN) >= 4,
                    "cell starts cold with enough tritium and oxygen");

            var result = ServerExplosionService.execute(level, cell, ModExplosions.DEFAULT_ID, PROPOSAL);
            require(result.status() == ServerExplosionService.Status.APPLIED, "server blast applied: " + result);
            var immediatelyAfter = global.sample(level, cell).orElseThrow();
            require(before.gasMoles().equals(immediatelyAfter.gasMoles())
                            && before.thermalEnergy() == immediatelyAfter.thermalEnergy(),
                    "blast queues a hotspot but does not instantly mutate finite-cell gas or energy");

            helper.runAfterDelay(80, () -> commitBlastHotspot(helper, global, level,
                    cell, before, startupEnabled, startupVacuumDimensions));
        } catch (RuntimeException failure) {
            global.configureAtServerStart(startupEnabled, startupVacuumDimensions);
            throw failure;
        }
    }

    private static void commitBlastHotspot(GameTestHelper helper, AtmosphereService service, ServerLevel level,
                                           BlockPos cell, com.juicyslew.moonstation14.ms14.atmos.core.GasMixture before,
                                           boolean startupEnabled,
                                           Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> startupVacuumDimensions) {
        try {
            var after = service.sample(level, cell).orElseThrow();
            require(after.moles(GasType.TRITIUM) < before.moles(GasType.TRITIUM)
                            && after.moles(GasType.OXYGEN) < before.moles(GasType.OXYGEN)
                            && after.moles(GasType.WATER_VAPOR) > before.moles(GasType.WATER_VAPOR)
                            && after.thermalEnergy() > before.thermalEnergy()
                            && Double.isFinite(after.temperatureKelvin())
                            && after.temperatureKelvin() <= GasMixture.MAX_TEMPERATURE_KELVIN
                            && service.fireIntensity(level, cell) > 0,
                    "server cycles must commit finite hotspot chemistry without an unbounded temperature; before="
                            + before.gasMoles() + "/" + before.temperatureKelvin() + "K/" + before.thermalEnergy()
                            + "J, after=" + after.gasMoles() + "/" + after.temperatureKelvin() + "K/"
                            + after.thermalEnergy() + "J, intensity=" + service.fireIntensity(level, cell)
                            + ", timing=" + service.diagnosticTiming(level));
            helper.succeed();
        } finally {
            service.configureAtServerStart(startupEnabled, startupVacuumDimensions);
        }
    }

    private static boolean classifyUntilSample(AtmosphereService service, ServerLevel level, BlockPos pos, int passes) {
        service.activateCellAndNeighbors(level, pos);
        for (int i = 1; i <= Math.max(60, passes * 5); i++) {
            if (service.diagnosticCell(level, pos).status().equals("finite")) return true;
            service.tick(level, i);
        }
        return service.diagnosticCell(level, pos).status().equals("finite");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
