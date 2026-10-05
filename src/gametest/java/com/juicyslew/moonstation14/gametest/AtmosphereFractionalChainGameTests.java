package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState;
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

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_fractional_chain", timeoutTicks = 420)
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
        service.activateCellAndNeighbors(level, source);
        for (BlockPos cell : cells) {
            service.sample(level, cell);
            for (int pass = 1; pass <= 60 && !claimed(level, cell); pass++) {
                service.tick(level, pass);
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
        boolean[] finished = {false};
        boolean[] sourceSeen = {false};
        boolean[] middleSeen = {false};
        boolean[] farSeen = {false};
        String[] sourceEvidence = {"none"}, middleEvidence = {"none"}, farEvidence = {"none"};
        helper.startSequence().thenExecuteFor(400, () -> {
            // Drive the same service entry point once per real GameTest/server callback. Stages
            // resume at their own 15 Hz admission rate; completed cycles are not 2-tick passes.
            service.tick(level, level.getGameTime());
            require(service.boundaryGasLedger(level).values().stream().allMatch(v -> close(v, 0)),
                    "sealed hallway must not export gas to exterior");
            GasMixture[] after = samples(service, level, cells);
            // A committed local reaction event proves chemistry here; transported products or
            // hotspot metadata alone do not establish that this cell burned.
            sourceSeen[0] |= recordLocalFire(service, level, source, sourceEvidence);
            middleSeen[0] |= recordLocalFire(service, level, middle, middleEvidence);
            farSeen[0] |= recordLocalFire(service, level, far, farEvidence);
            if (sourceSeen[0] && farSeen[0]) {
                GasMixture[] finalMixtures = after;
                double burned = 0, waterMade = 0, oxygenUsed = 0;
                for (int i = 0; i < cells.length; i++) {
                    double fuelLoss = initial[i].moles(GasType.TRITIUM) - finalMixtures[i].moles(GasType.TRITIUM);
                    double waterGain = finalMixtures[i].moles(GasType.WATER_VAPOR)
                            - initial[i].moles(GasType.WATER_VAPOR);
                    double localOxygenUsed = initial[i].moles(GasType.OXYGEN)
                            - finalMixtures[i].moles(GasType.OXYGEN);
                    burned += fuelLoss;
                    waterMade += waterGain;
                    oxygenUsed += localOxygenUsed;
                }
                double speciesUlpBudget = 0;
                for (int i = 0; i < cells.length; i++)
                    speciesUlpBudget += 8 * (Math.ulp((float) initial[i].moles(GasType.TRITIUM))
                            + Math.ulp((float) initial[i].moles(GasType.WATER_VAPOR))
                            + Math.ulp((float) initial[i].moles(GasType.OXYGEN)));
                require(Math.abs(burned - waterMade) <= speciesUlpBudget
                                && Math.abs(oxygenUsed - 0.5 * burned) <= speciesUlpBudget,
                        "sealed reaction chain must conserve tritium/water and consume 0.5 O2 per tritium; burned="
                                + burned + ", water=" + waterMade + ", oxygen=" + oxygenUsed);
                finished[0] = true;
                helper.succeed();
            }
        }).thenExecute(() -> require(finished[0], "runtime stages failed to commit source and far-cell local fire chemistry; "
                + "source=" + actualHotspot(service, level, source) + ", middle="
                + actualHotspot(service, level, middle) + ", far=" + actualHotspot(service, level, far)
                + ", source-local-fire=" + sourceSeen[0] + " [" + sourceEvidence[0] + "]"
                + ", middle-local-fire=" + middleSeen[0] + " [" + middleEvidence[0] + "]"
                + ", far-local-fire=" + farSeen[0] + " [" + farEvidence[0] + "]"));
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

    private static HotspotState actualHotspot(AtmosphereService service, ServerLevel level, BlockPos pos) {
        return service.diagnosticHotspot(level, pos).orElse(null);
    }

    private static boolean recordLocalFire(AtmosphereService service, ServerLevel level, BlockPos pos, String[] evidence) {
        var whole = service.diagnosticCommittedWholeAir(level, pos).orElse(null);
        if (whole == null || whole.fireExtentMoles() <= 0) return false;
        var fire = whole.events().stream().filter(event -> event.effect() == GasReactionData.EffectType.TRITIUM_FIRE
                        && event.extentMoles() > 0)
                .findFirst().orElse(null);
        if (fire == null) return false;
        evidence[0] = "cycle=" + whole.cycleIdentity() + " reaction=" + fire.id()
                + " event-extent=" + fire.extentMoles() + " fire-extent=" + whole.fireExtentMoles();
        return true;
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
