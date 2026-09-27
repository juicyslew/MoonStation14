package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereRoomGameTests {
    private static final double INJECTED_MOLES = 8.0;
    private static final double ROOM_TEMPERATURE_KELVIN = 293.15;

    private AtmosphereRoomGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sealedRoomDiffusesGasAndConservesSpecies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildSealedRoom(helper);

        // This test explicitly opts this isolated service into vacuum for this dimension;
        // it never changes the process-global atmosphere policy.
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos near = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos far = helper.absolutePos(new BlockPos(4, 2, 4));
        require(service.sample(level, near).isEmpty(),
                "covered room begins UNKNOWN until ownership classification advances");
        GasMixture initial = classifyUntilSample(service, level, near, 12);
        require(initial.totalMoles() == 0.0,
                "sealed room must begin at vacuum");
        LevelChunk roomChunk = (LevelChunk) level.getChunk(near);
        require(roomChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get()) != null
                        && roomChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get())
                        .isFiniteClaimed(far.getX() & 15, far.getY(), far.getZ() & 15),
                "classification must persist a finite claim even for an ambient-valued room cell");
        require(service.addBreathableAir(level, near, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "breathable gas injection into sealed room must succeed");

        double pressureBefore = service.sample(level, far).orElseThrow().pressureKpa(1.0);
        double oxygenBefore = roomMoles(service, level, GasType.OXYGEN, helper);
        double nitrogenBefore = roomMoles(service, level, GasType.NITROGEN, helper);
        require(Math.abs(oxygenBefore - INJECTED_MOLES * 0.21) < 1.0e-9,
                "injection must add the expected oxygen inventory");
        require(Math.abs(nitrogenBefore - INJECTED_MOLES * 0.79) < 1.0e-9,
                "injection must add the expected nitrogen inventory");

        for (long gameTime = AtmosphereService.TICK_CADENCE;
             gameTime <= 40L * AtmosphereService.TICK_CADENCE; gameTime += AtmosphereService.TICK_CADENCE) {
            service.tick(level, gameTime);
        }

        GasMixture farMixture = service.sample(level, far).orElseThrow();
        require(farMixture.pressureKpa(1.0) > pressureBefore + 0.05,
                "gas must reach a distant room cell within 40 atmosphere passes; pressure was "
                        + farMixture.pressureKpa(1.0) + " kPa");
        require(Math.abs(roomMoles(service, level, GasType.OXYGEN, helper) - oxygenBefore) < 1.0e-6,
                "closed room diffusion must conserve oxygen");
        require(Math.abs(roomMoles(service, level, GasType.NITROGEN, helper) - nitrogenBefore) < 1.0e-6,
                "closed room diffusion must conserve nitrogen");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coveredExteriorSealingAndBreachRespectOwnership(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCoveredRoomWithSideGap(helper);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos roomCell = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos gap = helper.absolutePos(new BlockPos(3, 1, 1));

        require(service.sample(level, roomCell).isEmpty(), "covered air should remain unknown before classification");
        GasMixture exposed = classifyUntilSample(service, level, roomCell, 12);
        require(Math.abs(exposed.pressureKpa(1.0)) < 1.0e-9,
                "covered room connected to an open side gap must classify as exterior vacuum");
        require(service.sample(level, gap).orElseThrow().totalMoles() == 0.0,
                "open side gap must be exterior");
        require(!service.addBreathableAir(level, roomCell, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "exterior-connected covered air must reject injection");

        // Close the last side opening and explicitly notify this isolated service;
        // GameTest block events notify only the global service instance.
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.STONE);
        service.topologyChanged(level, gap);
        require(service.sample(level, roomCell).isEmpty(),
                "topology change must invalidate the previous exterior classification");
        classifyUntilSample(service, level, roomCell, 12);
        require(service.addBreathableAir(level, roomCell, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "fully sealed covered room must become finite and accept injection");

        double gasBeforeBreach = roomMoles(service, level, helper);
        double oxygenBeforeBreach = roomMoles(service, level, GasType.OXYGEN, helper);
        double nitrogenBeforeBreach = roomMoles(service, level, GasType.NITROGEN, helper);
        require(gasBeforeBreach > 0.0, "finite room must contain injected gas before breach");
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.AIR);
        service.topologyChanged(level, gap);
        GasMixture retained = service.sample(level, roomCell).orElseThrow(
                () -> new GameTestAssertException("previous finite claims must survive the breach"));
        require(retained.totalMoles() > 0.0,
                "breach must not instantly erase gas from the previously finite room cell");

        for (long gameTime = AtmosphereService.TICK_CADENCE;
             gameTime <= 12L * AtmosphereService.TICK_CADENCE; gameTime += AtmosphereService.TICK_CADENCE) {
            service.tick(level, gameTime);
        }
        double gasAfterDrain = roomMoles(service, level, helper);
        require(gasAfterDrain < gasBeforeBreach * 0.5,
                "gas should drain substantially through the exterior edge; before=" + gasBeforeBreach
                        + ", after=" + gasAfterDrain);
        require(service.sample(level, gap).orElseThrow().totalMoles() == 0.0,
                "vacuum exterior must remain ambient rather than accumulating exported gas");
        double oxygenExported = service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0);
        double nitrogenExported = service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        require(Math.abs(oxygenBeforeBreach - roomMoles(service, level, GasType.OXYGEN, helper)
                - oxygenExported) < 1.0e-6,
                "oxygen export ledger must equal removed oxygen");
        require(Math.abs(nitrogenBeforeBreach - roomMoles(service, level, GasType.NITROGEN, helper)
                - nitrogenExported) < 1.0e-6,
                "nitrogen export ledger must equal removed nitrogen");
        require(oxygenExported >= 0.0 && nitrogenExported >= 0.0
                        && oxygenExported + nitrogenExported <= gasBeforeBreach + 1.0e-6,
                "boundary exchange must not create gas");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullWallOpeningDrainsMoreThanOneBlockOpening(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildSealedRoom(helper, 0);
        buildSealedRoom(helper, 8);
        for (int y = 1; y <= 2; y++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(1, y, z), Blocks.AIR);
                helper.setBlock(new BlockPos(9, y, z), Blocks.AIR);
            }
        }
        // Restore all but one block in the comparison room's opening.
        for (int y = 1; y <= 2; y++) {
            for (int z = 2; z <= 4; z++) {
                if (y != 1 || z != 2) helper.setBlock(new BlockPos(9, y, z), Blocks.STONE);
            }
        }

        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos fullRoom = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos singleRoom = helper.absolutePos(new BlockPos(11, 1, 3));
        for (int y = 1; y <= 2; y++) {
            for (int z = 2; z <= 4; z++) {
                service.topologyChanged(level, helper.absolutePos(new BlockPos(1, y, z)));
                if (y == 1 && z == 2) {
                    service.topologyChanged(level, helper.absolutePos(new BlockPos(9, y, z)));
                }
            }
        }
        classifyUntilSample(service, level, fullRoom, 12);
        classifyUntilSample(service, level, singleRoom, 12);
        require(service.addBreathableAir(level, fullRoom, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "full-opening room should accept gas before the comparison");
        require(service.addBreathableAir(level, singleRoom, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "single-opening room should accept gas before the comparison");

        for (long gameTime = AtmosphereService.TICK_CADENCE;
             gameTime <= 12L * AtmosphereService.TICK_CADENCE; gameTime += AtmosphereService.TICK_CADENCE) {
            service.tick(level, gameTime);
        }
        double fullOpeningRemaining = sealedRoomMoles(service, level, helper, 0);
        double singleOpeningRemaining = sealedRoomMoles(service, level, helper, 8);
        require(fullOpeningRemaining < singleOpeningRemaining,
                "a full-wall opening must export more gas than a one-block opening; full="
                        + fullOpeningRemaining + ", one-block=" + singleOpeningRemaining);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void skyExposedCellUsesAmbientAndRejectsInjection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of());
        BlockPos candidate = helper.absolutePos(new BlockPos(2, 100, 2));
        int firstFreeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, candidate.getX(), candidate.getZ());
        if (firstFreeY > candidate.getY()) {
            candidate = new BlockPos(candidate.getX(), firstFreeY + 1, candidate.getZ());
        }
        require(candidate.getY() < level.getMaxBuildHeight(), "fixture must have room for an open sky cell");
        require(level.getHeight(Heightmap.Types.MOTION_BLOCKING, candidate.getX(), candidate.getZ()) <= candidate.getY(),
                "selected cell must be verified above the motion-blocking heightmap");

        LevelChunk chunk = (LevelChunk) level.getChunk(candidate);
        require(chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get()) == null,
                "fresh atmosphere service must not have materialized chunk overrides");
        GasMixture ambient = service.sample(level, candidate).orElseThrow(
                () -> new GameTestAssertException("verified sky-exposed cell must be sampleable"));
        require(Math.abs(ambient.pressureKpa(1.0) - GasMixture.breathableAir().pressureKpa(1.0)) < 1.0e-9,
                "sky-exposed cell must report breathable ambient");
        require(!service.addBreathableAir(level, candidate, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "exterior cell must reject breathable-air injection");
        require(chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get()) == null,
                "sampling and rejected exterior injection must not materialize chunk overrides");
        helper.succeed();
    }

    private static void buildSealedRoom(GameTestHelper helper) {
        buildSealedRoom(helper, 0);
    }

    private static void buildSealedRoom(GameTestHelper helper, int offsetX) {
        // Interior is x/z 2..4 and y 1..2. Complete stone boundaries keep every
        // interior cell below the heightmap roof and block all six escape paths.
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x + offsetX, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x + offsetX, 3, z), Blocks.STONE);
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int i = 1; i <= 5; i++) {
                helper.setBlock(new BlockPos(1 + offsetX, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(5 + offsetX, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(i + offsetX, y, 1), Blocks.STONE);
                helper.setBlock(new BlockPos(i + offsetX, y, 5), Blocks.STONE);
            }
        }
    }

    private static void buildCoveredRoomWithSideGap(GameTestHelper helper) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        for (int y = 1; y <= 1; y++) {
            for (int i = 0; i <= 3; i++) {
                helper.setBlock(new BlockPos(0, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(3, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(i, y, 0), Blocks.STONE);
                helper.setBlock(new BlockPos(i, y, 3), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.AIR);
    }

    private static GasMixture classifyUntilSample(AtmosphereService service, ServerLevel level,
                                                   BlockPos pos, int maxPasses) {
        for (int pass = 1; pass <= maxPasses; pass++) {
            service.tick(level, (long) pass * AtmosphereService.TICK_CADENCE);
            var sample = service.sample(level, pos);
            if (sample.isPresent()) return sample.get();
        }
        throw new GameTestAssertException("cell ownership did not classify within " + maxPasses + " passes");
    }

    private static double roomMoles(AtmosphereService service, ServerLevel level, GasType type,
                                    GameTestHelper helper) {
        double total = 0.0;
        for (int x = 2; x <= 4; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 4; z++) {
                    total += service.sample(level, helper.absolutePos(new BlockPos(x, y, z)))
                            .orElseThrow().moles(type);
                }
            }
        }
        return total;
    }

    private static double roomMoles(AtmosphereService service, ServerLevel level, GameTestHelper helper) {
        return roomMoles(service, level, helper, 0);
    }

    private static double roomMoles(AtmosphereService service, ServerLevel level, GameTestHelper helper, int offsetX) {
        double total = 0.0;
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                total += service.sample(level, helper.absolutePos(new BlockPos(x + offsetX, 1, z)))
                        .orElseThrow().totalMoles();
            }
        }
        return total;
    }

    private static double sealedRoomMoles(AtmosphereService service, ServerLevel level,
                                         GameTestHelper helper, int offsetX) {
        double total = 0.0;
        for (int x = 2; x <= 4; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 4; z++) {
                    total += service.sample(level, helper.absolutePos(new BlockPos(x + offsetX, y, z)))
                            .orElseThrow().totalMoles();
                }
            }
        }
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
