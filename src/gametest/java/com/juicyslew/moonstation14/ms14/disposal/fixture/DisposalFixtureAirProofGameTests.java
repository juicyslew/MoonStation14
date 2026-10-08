package com.juicyslew.moonstation14.ms14.disposal.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class DisposalFixtureAirProofGameTests {
    private DisposalFixtureAirProofGameTests() { }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void boundedTrustedSeedRejectsConnectedCellOutsideInputWithoutMutation(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        AtmosphereService atmos = AtmosphereService.INSTANCE;
        Set<net.minecraft.resources.ResourceKey<Level>> oldVacuum = atmos.vacuumDimensionsForTest();
        try {
            atmos.configureAtServerStart(Set.of(level.dimension()), atmos.spaceDimensionsForTest());
            BlockPos first = helper.absolutePos(new BlockPos(3, 2, 3));
            BlockPos second = first.east();
            for (BlockPos cell : List.of(first, second)) {
                for (var direction : net.minecraft.core.Direction.values()) {
                    BlockPos neighbor = cell.relative(direction);
                    if (!neighbor.equals(first) && !neighbor.equals(second))
                        level.setBlock(neighbor, Blocks.STONE.defaultBlockState(), 3);
                }
            }
            level.setBlock(first, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(second, Blocks.AIR.defaultBlockState(), 3);
            var firstBefore = atmos.readAtmosphere(level, first).orElseThrow();
            var secondBefore = atmos.readAtmosphere(level, second).orElseThrow();
            GasMixture requested = new GasMixture(java.util.Map.of(GasType.OXYGEN, 8.0), 293.15);

            require(!atmos.initializeEnclosedFiniteMixtureForTrustedSetup(level, List.of(first), requested),
                    "proof rejects connected passable cell omitted from trusted input");
            var firstAfter = atmos.readAtmosphere(level, first).orElseThrow();
            var secondAfter = atmos.readAtmosphere(level, second).orElseThrow();
            require(firstAfter.status() != AtmosphereReading.Status.FINITE
                            && secondAfter.status() != AtmosphereReading.Status.FINITE,
                    "failed bounded proof claims neither cell finite");
            require(sameMixture(firstAfter.mixture(), firstBefore.mixture())
                            && sameMixture(secondAfter.mixture(), secondBefore.mixture()),
                    "failed bounded proof writes no gas mixture");
            helper.succeed();
        } finally {
            atmos.configureAtServerStart(oldVacuum, atmos.spaceDimensionsForTest());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static boolean sameMixture(GasMixture first, GasMixture second) {
        return first.gasMoles().equals(second.gasMoles())
                && Double.compare(first.temperatureKelvin(), second.temperatureKelvin()) == 0;
    }
}
