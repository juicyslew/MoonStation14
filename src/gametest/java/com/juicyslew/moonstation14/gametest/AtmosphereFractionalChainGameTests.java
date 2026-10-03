package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
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

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereFractionalChainGameTests {
    private AtmosphereFractionalChainGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_fractional_chain", timeoutTicks = 120)
    public static void fractionalHotspotCarriesFireThroughSealedThreeCellHallway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        require(!AtmosphereService.INSTANCE.isEnabled(), "isolated chain fixture requires singleton atmosphere disabled");
        for (int x = 1; x <= 5; x++) for (int y = 0; y <= 2; y++) for (int z = 0; z <= 2; z++)
            helper.setBlock(new BlockPos(x, y, z), x >= 2 && x <= 4 && y == 1 && z == 1
                    ? Blocks.AIR : Blocks.STONE);
        BlockPos source = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos middle = source.east();
        BlockPos far = middle.east();
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos[] cells = {source, middle, far};
        for (BlockPos cell : cells) {
            service.sample(level, cell);
            for (int pass = 1; pass <= 40 && !claimed(level, cell); pass++) {
                service.tick(level, (long) pass * AtmosphereService.TICK_CADENCE);
                service.sample(level, cell);
            }
            require(claimed(level, cell), "hallway cell must be finite before injection: " + cell);
            require(service.sample(level, cell).orElseThrow().totalMoles() == 0,
                    "finite hallway cell must start as defined vacuum: " + cell);
        }
        inject(service, level, source, 450);
        inject(service, level, middle, 293.15);
        inject(service, level, far, 293.15);
        GasMixture[] initial = new GasMixture[cells.length];
        for (int i = 0; i < cells.length; i++) initial[i] = service.sample(level, cells[i]).orElseThrow();
        double[] chemistryEnergy = {0};
        Map<GasType, Double> chemistrySpecies = new EnumMap<>(GasType.class);
        int[] duePass = {0};
        int[] sourcePartial = {-1};
        int[] sourceFullPass = {-1};
        int[] middleFirstHeatPass = {-1};
        int[] middleFullPass = {-1};
        int[] farFirstHeatPass = {-1};
        boolean[] finished = {false};
        helper.startSequence().thenExecuteFor(100, () -> {
            if (finished[0] || level.getGameTime() % AtmosphereService.TICK_CADENCE != 0) return;
            require(++duePass[0] <= 40, "far cell did not ignite within forty actual due passes");
            int beforeSourceIntensity = service.fireIntensity(level, source);
            GasMixture[] beforeAll = samples(service, level, cells);

            var sourceResult = service.reactFiniteCell(level, source).orElseThrow(
                    () -> new GameTestAssertException("source must naturally react on due pass " + duePass[0]));
            account(sourceResult, chemistryEnergy, chemistrySpecies);
            GasMixture[] afterSourceAll = samples(service, level, cells);
            int sourceIntensity = service.fireIntensity(level, source);
            if (sourcePartial[0] < 0 && sourceIntensity > 0 && sourceIntensity < 255) sourcePartial[0] = duePass[0];
            if (sourceIntensity == 255 && sourceFullPass[0] < 0) sourceFullPass[0] = duePass[0];

            int beforeMiddleIntensity = service.fireIntensity(level, middle);
            var middleResult = service.reactFiniteCell(level, middle);
            if (middleResult.isPresent()) account(middleResult.get(), chemistryEnergy, chemistrySpecies);
            GasMixture[] afterMiddleAll = samples(service, level, cells);
            if (afterSourceAll[1].thermalEnergy() > beforeAll[1].thermalEnergy()) {
                if (middleFirstHeatPass[0] < 0) {
                    middleFirstHeatPass[0] = duePass[0];
                    require(sourcePartial[0] > 0 && sourceFullPass[0] < duePass[0],
                            "source must grow naturally from partial to full before heating middle");
                    require(beforeSourceIntensity == 255,
                            "source must already be full intensity before offering heat to middle");
                    require(middleResult.isEmpty() || middleResult.get().events().isEmpty(),
                            "middle heating pass cannot also react");
                }
                require(afterSourceAll[1].gasMoles().equals(beforeAll[1].gasMoles()), "middle face heating changed species");
                require(beforeMiddleIntensity == 0, "middle must not already be firing on its heating pass");
            }
            if (service.fireIntensity(level, middle) == 255 && middleFullPass[0] < 0) middleFullPass[0] = duePass[0];

            int beforeFarIntensity = service.fireIntensity(level, far);
            var farResult = service.reactFiniteCell(level, far);
            if (farResult.isPresent()) account(farResult.get(), chemistryEnergy, chemistrySpecies);
            GasMixture[] afterFarAll = samples(service, level, cells);
            if (afterMiddleAll[2].thermalEnergy() > afterSourceAll[2].thermalEnergy() && farFirstHeatPass[0] < 0) {
                farFirstHeatPass[0] = duePass[0];
                require(middleFullPass[0] > 0 && middleFullPass[0] < duePass[0],
                        "middle must grow to full fire before first far-cell heating");
                require(beforeMiddleIntensity == 255,
                        "middle must already be full intensity before offering heat to far cell");
                require(farResult.isEmpty() || farResult.get().events().isEmpty(), "far heating pass cannot also react");
                require(afterMiddleAll[2].gasMoles().equals(afterSourceAll[2].gasMoles()), "far face heating changed species");
                require(beforeFarIntensity == 0, "far cell must not already be firing on its heating pass");
            }
            if (farResult.isPresent() && farResult.get().events().stream()
                    .anyMatch(event -> event.effect() == GasReactionData.EffectType.TRITIUM_FIRE)) {
                require(middleFirstHeatPass[0] > 0 && farFirstHeatPass[0] > middleFirstHeatPass[0],
                        "both real deferred heat-and-ignite stages must occur in order");
                verifyLedgers(service, level, cells, initial, chemistryEnergy[0], chemistrySpecies);
                require(service.boundaryGasLedger(level).values().stream().allMatch(v -> close(v, 0)),
                        "sealed hallway must not export gas to exterior");
                finished[0] = true;
                helper.succeed();
            }
            require(service.boundaryGasLedger(level).values().stream().allMatch(v -> close(v, 0)),
                    "sealed hallway must not export gas to exterior");
            verifyLedgers(service, level, cells, initial, chemistryEnergy[0], chemistrySpecies);
        }).thenExecute(() -> require(finished[0], "fractional fire chain did not reach far-cell real chemistry"));
    }

    private static void inject(AtmosphereService service, ServerLevel level, BlockPos cell, double temperature) {
        require(service.addGas(level, cell, GasType.TRITIUM, 2, temperature), "tritium injection failed");
        require(service.addGas(level, cell, GasType.OXYGEN, 4, temperature), "oxygen injection failed");
    }

    private static boolean claimed(ServerLevel level, BlockPos cell) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cell.getX() >> 4, cell.getZ() >> 4);
        if (chunk == null) return false;
        var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        return data != null && data.isFiniteClaimed(cell.getX() & 15, cell.getY(), cell.getZ() & 15);
    }

    private static GasMixture[] samples(AtmosphereService service, ServerLevel level, BlockPos[] cells) {
        GasMixture[] result = new GasMixture[cells.length];
        for (int i = 0; i < cells.length; i++) result[i] = service.sample(level, cells[i]).orElseThrow();
        return result;
    }

    private static void account(GasReactionEvaluator.Result result, double[] energy,
                                Map<GasType, Double> species) {
        energy[0] += result.energyDeltaJoules();
        result.speciesDelta().forEach((type, delta) -> species.merge(type, delta, Double::sum));
    }

    private static void verifyLedgers(AtmosphereService service, ServerLevel level, BlockPos[] cells,
                                      GasMixture[] initial, double chemistryEnergy,
                                      Map<GasType, Double> chemistrySpecies) {
        double totalEnergyDelta = 0;
        Map<GasType, Double> totalSpeciesDelta = new EnumMap<>(GasType.class);
        for (int i = 0; i < cells.length; i++) {
            GasMixture current = service.sample(level, cells[i]).orElseThrow();
            totalEnergyDelta += current.thermalEnergy() - initial[i].thermalEnergy();
            for (GasType type : GasType.values()) {
                double delta = current.moles(type) - initial[i].moles(type);
                totalSpeciesDelta.merge(type, delta, Double::sum);
            }
        }
        for (GasType type : GasType.values())
            require(close(totalSpeciesDelta.getOrDefault(type, 0.0), chemistrySpecies.getOrDefault(type, 0.0)),
                    "hallway cumulative species ledger mismatch for " + type);
        require(close(totalEnergyDelta, chemistryEnergy), "hallway energy delta must equal cumulative reaction joules");
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= Math.max(1e-7, Math.max(Math.abs(a), Math.abs(b)) * 1e-10);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
