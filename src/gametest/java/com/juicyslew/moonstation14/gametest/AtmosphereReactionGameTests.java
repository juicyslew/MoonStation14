package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereChunkData;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotKernel;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereReactionGameTests {
    private static final Map<String, GasReactionData.EffectType> BUNDLED = Map.of(
            "plasma_fire", GasReactionData.EffectType.PLASMA_FIRE,
            "tritium_fire", GasReactionData.EffectType.TRITIUM_FIRE,
            "frezon_coolant", GasReactionData.EffectType.FREZON_COOLANT,
            "frezon_production", GasReactionData.EffectType.FREZON_PRODUCTION,
            "ammonia_oxygen", GasReactionData.EffectType.AMMONIA_OXYGEN,
            "n2o_decomposition", GasReactionData.EffectType.N2O_DECOMPOSITION);

    private AtmosphereReactionGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_catalog", timeoutTicks = 100)
    public static void serverCatalogContainsAllSixEnabledReactions(GameTestHelper helper) {
        var catalog = PrototypeRuntime.serverGasReactions();
        require(catalog.asMap().keySet().equals(BUNDLED.keySet().stream().map(AtmosphereReactionGameTests::id)
                        .collect(java.util.stream.Collectors.toSet())),
                "server catalog must contain exactly the six bundled reaction IDs");
        BUNDLED.forEach((name, effect) -> require(catalog.get(id(name)).effects().size() == 1
                        && catalog.get(id(name)).effects().getFirst().type() == effect,
                "server prototype must enable its expected effect: " + name));
        // The two priority-2 temperature windows do not intersect. A real-world tie cannot
        // exercise ID ordering; that synthetic tie belongs in detached evaluator unit tests.
        require(catalog.get(id("frezon_production")).priority() == 2
                        && catalog.get(id("ammonia_oxygen")).priority() == 2
                        && catalog.get(id("frezon_production")).maximumTemperature()
                        < catalog.get(id("ammonia_oxygen")).minimumTemperature(),
                "priority-2 prototypes must have nonoverlapping temperature gates");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_tritium", timeoutTicks = 100)
    public static void tritiumFireCommitsPartialHotspot(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.add(GasType.TRITIUM, 2, 900);
        f.add(GasType.OXYGEN, 4, 900);
        f.onDue(() -> {
            var step = f.commit("tritium_fire");
            direction(step, GasType.TRITIUM, -1);
            direction(step, GasType.OXYGEN, -1);
            direction(step, GasType.WATER_VAPOR, 1);
            require(step.after().moles(GasType.TRITIUM) > 0
                            && -step.event().speciesDelta().get(GasType.TRITIUM) < 0.2,
                    "seeded tritium hotspot must consume only a partial portion");
            positiveEnergy(step);
            helper.succeed();
        });
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_plasma", timeoutTicks = 100)
    public static void plasmaFireCommitsPartialHotspot(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.add(GasType.PLASMA, 1, 1643.15);
        f.add(GasType.OXYGEN, 20, 1643.15);
        f.onDue(() -> {
            var step = f.commit("plasma_fire");
            direction(step, GasType.PLASMA, -1);
            direction(step, GasType.OXYGEN, -1);
            direction(step, GasType.CARBON_DIOXIDE, 1);
            require(step.after().moles(GasType.PLASMA) > 0
                            && -step.event().speciesDelta().get(GasType.PLASMA) < 0.1,
                    "seeded plasma hotspot must burn less than its 10% starting portion");
            positiveEnergy(step);
            helper.succeed();
        });
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_frezon_production", timeoutTicks = 100)
    public static void coldFrezonProductionConvertsOxygenAndTritium(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.add(GasType.TRITIUM, 1, 30);
        f.add(GasType.OXYGEN, 50, 30);
        f.add(GasType.NITROGEN, 1, 30);
        f.onDue(() -> {
            var step = f.commit("frezon_production");
            direction(step, GasType.TRITIUM, -1);
            direction(step, GasType.OXYGEN, -1);
            direction(step, GasType.FREZON, 1);
            direction(step, GasType.NITROGEN, 1);
            zeroEnergy(step);
            helper.succeed();
        });
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_frezon_coolant", timeoutTicks = 100)
    public static void frezonCoolantConsumesNitrogenAndRemovesRawJoules(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.add(GasType.FREZON, 1, 373.15);
        f.add(GasType.NITROGEN, 100, 373.15);
        f.onDue(() -> {
            var step = f.commit("frezon_coolant");
            direction(step, GasType.FREZON, -1);
            direction(step, GasType.NITROGEN, -1);
            direction(step, GasType.NITROUS_OXIDE, 1);
            require(step.result().energyDeltaJoules() < 0 && close(step.result().energyDeltaJoules(), -30000),
                    "coolant must debit 30000 unscaled joules");
            helper.succeed();
        });
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_ammonia", timeoutTicks = 100)
    public static void ammoniaOxygenMakesN2OAndWater(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.add(GasType.AMMONIA, 2, 400);
        f.add(GasType.OXYGEN, 2, 400);
        f.onDue(() -> {
            var step = f.commit("ammonia_oxygen");
            direction(step, GasType.AMMONIA, -1);
            direction(step, GasType.OXYGEN, -1);
            direction(step, GasType.NITROUS_OXIDE, 1);
            direction(step, GasType.WATER_VAPOR, 1);
            zeroEnergy(step);
            helper.succeed();
        });
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_n2o", timeoutTicks = 100)
    public static void hotN2ODecomposesToNitrogenAndOxygen(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.add(GasType.NITROUS_OXIDE, 2, 850);
        f.onDue(() -> {
            var step = f.commit("n2o_decomposition");
            direction(step, GasType.NITROUS_OXIDE, -1);
            direction(step, GasType.NITROGEN, 1);
            direction(step, GasType.OXYGEN, 1);
            zeroEnergy(step);
            helper.succeed();
        });
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_cold_gate", timeoutTicks = 100)
    public static void frezonProductionStopsAboveColdCutoff(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.add(GasType.TRITIUM, 1, 74);
        f.add(GasType.OXYGEN, 50, 74);
        f.add(GasType.NITROGEN, 1, 74);
        f.onDue(() -> { f.noReaction(); helper.succeed(); });
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_hot_gate", timeoutTicks = 100)
    public static void n2oDecompositionStopsBelowHotGate(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.add(GasType.NITROUS_OXIDE, 2, 849);
        f.onDue(() -> { f.noReaction(); helper.succeed(); });
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reaction_attachment_roundtrip", timeoutTicks = 100)
    public static void serializedChunkAttachmentFeedsNewServiceWithoutFireCatchUp(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.add(GasType.TRITIUM, 2, 900);
        f.add(GasType.OXYGEN, 4, 900);
        f.onDue(() -> {
            var first = f.commit("tritium_fire"); // Grow the old service's transient hotspot.
            LevelChunk chunk = (LevelChunk) f.level().getChunk(f.cell());
            var attachment = ModDataAttachments.ATMOSPHERE_CHUNK.get();
            AtmosphereChunkData original = chunk.getExistingDataOrNull(attachment);
            require(original != null, "finite reactive source must have a saved chunk attachment");
            var encoded = AtmosphereChunkData.CODEC.encodeStart(JsonOps.INSTANCE, original).result().orElseThrow();
            require(encoded.getAsJsonObject().size() == 3
                            && encoded.getAsJsonObject().has("cells")
                            && encoded.getAsJsonObject().has("finite_cells"),
                    "only gas overrides and finite ownership are serialized, never hotspot state");
            AtmosphereChunkData decoded = AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, encoded)
                    .result().orElseThrow();
            int x = f.cell().getX() & 15, y = f.cell().getY(), z = f.cell().getZ() & 15;
            require(decoded.isFiniteClaimed(x, y, z) && decoded.get(x, y, z) != null,
                    "decoded attachment must retain both finite claim and reactive gas override");
            require(decoded.get(x, y, z).gasMoles().equals(first.after().gasMoles()),
                    "saved override must contain the committed gas, not a duplicate fire record");
            chunk.setData(attachment, decoded);
            chunk.setUnsaved(true);
            AtmosphereService loadedService = AtmosphereService.withVacuumDimensions(Set.of(f.level().dimension()));
            GasMixture saved = loadedService.sample(f.level(), f.cell()).orElseThrow();
            require(saved.gasMoles().equals(first.after().gasMoles()),
                    "new service must read the decoded chunk attachment, not old in-memory gas");
            var expectedSeed = HotspotKernel.evaluate(saved, PrototypeRuntime.serverGasReactions(), null);
            require(expectedSeed.totalFireExtentMoles() > 0, "saved gas must remain a fire candidate");
            // A GameTest cannot restart the server or trigger an isolated service's private
            // chunk-load hook. Advance real game time by one due step; no synthetic downtime ticks.
            helper.runAfterDelay(AtmosphereService.TICK_CADENCE, () -> {
                require(f.level().getGameTime() % AtmosphereService.TICK_CADENCE == 0,
                        "follow-up must be a fresh due step");
                GasMixture before = loadedService.sample(f.level(), f.cell()).orElseThrow();
                var committed = loadedService.reactFiniteCell(f.level(), f.cell()).orElseThrow(
                        () -> new GameTestAssertException("new service must react from the saved finite override"));
                require(committed.events().size() == 1
                                && committed.events().getFirst().id().equals(id("tritium_fire"))
                                && close(committed.events().getFirst().extentMoles(), expectedSeed.totalFireExtentMoles()),
                        "new service must seed fresh fraction, not restore grown hotspot or catch up downtime");
                GasMixture after = loadedService.sample(f.level(), f.cell()).orElseThrow();
                require(after.moles(GasType.TRITIUM) < before.moles(GasType.TRITIUM)
                                && after.moles(GasType.WATER_VAPOR) > before.moles(GasType.WATER_VAPOR),
                        "new due step must write combustion gas back to decoded attachment");
                require(loadedService.reactFiniteCell(f.level(), f.cell()).isEmpty()
                                && loadedService.sample(f.level(), f.cell()).orElseThrow().gasMoles().equals(after.gasMoles()),
                        "one new service cannot burn the same finite cell twice on this due step");
                helper.succeed();
            });
        });
    }

    private static Fixture fixture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Do not alter global policy: if the singleton is ticking, it can race this isolated service.
        require(!AtmosphereService.INSTANCE.isEnabled(),
                "isolated reaction fixtures require the singleton atmosphere disabled");
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            for (int y = 1; y <= 2; y++)
                helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 2 ? Blocks.AIR : Blocks.STONE);
        }
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        for (BlockPos cell : new BlockPos[] {pos, pos.above()}) {
            service.sample(level, cell); // Queue strict ownership; never infer it from a UI fallback.
            for (int pass = 1; pass <= 12 && !claimed(level, cell); pass++) {
                service.tick(level, (long) pass * AtmosphereService.TICK_CADENCE);
                service.sample(level, cell);
            }
            require(claimed(level, cell) && service.sample(level, cell).orElseThrow().totalMoles() == 0,
                    "both sealed room cells must be strictly finite and initially vacuum: " + cell);
        }
        return new Fixture(helper, level, service, pos);
    }

    private static boolean claimed(ServerLevel level, BlockPos cell) {
        var data = ((LevelChunk) level.getChunk(cell)).getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        return data != null && data.isFiniteClaimed(cell.getX() & 15, cell.getY(), cell.getZ() & 15);
    }

    private record Step(GasMixture before, GasMixture after, GasReactionEvaluator.Result result,
                        GasReactionEvaluator.Event event) { }

    private record Fixture(GameTestHelper helper, ServerLevel level, AtmosphereService service, BlockPos cell) {
        void add(GasType type, double moles, double kelvin) {
            require(service.addGas(level, cell, type, moles, kelvin), "finite gas injection failed: " + type);
        }

        // Classification used synthetic service ticks only BEFORE injection. Never tick that
        // service between the before sample and explicit commit, or the sample becomes stale.
        void onDue(Runnable assertion) { onDue(assertion, 0); }

        private void onDue(Runnable assertion, int waited) {
            require(waited <= 8, "explicit reaction did not reach an even due game tick");
            if (level.getGameTime() % AtmosphereService.TICK_CADENCE == 0) assertion.run();
            else helper.runAfterDelay(1, () -> onDue(assertion, waited + 1));
        }

        Step commit(String name) {
            require(claimed(level, cell) && claimed(level, cell.above()), "reaction lost finite ownership");
            GasMixture before = service.sample(level, cell).orElseThrow();
            var result = service.reactFiniteCell(level, cell).orElseThrow(() ->
                    new GameTestAssertException("expected finite reaction " + name));
            GasMixture after = service.sample(level, cell).orElseThrow();
            require(result.events().size() == 1 && result.events().getFirst().id().equals(id(name))
                            && result.events().getFirst().effect() == BUNDLED.get(name),
                    "server must commit exactly the expected prototype event: " + name + " " + result.events());
            var event = result.events().getFirst();
            require(event.extentMoles() > 0 && close(result.energyDeltaJoules(), event.energyDeltaJoules()),
                    "event and result must agree on signed raw joules");
            for (GasType type : GasType.values()) {
                double change = after.moles(type) - before.moles(type);
                require(close(change, result.speciesDelta().getOrDefault(type, 0.0))
                                && close(change, event.speciesDelta().getOrDefault(type, 0.0)),
                        "local event, result and cell species ledgers differ for " + type);
            }
            require(close(after.thermalEnergy() - before.thermalEnergy(), result.energyDeltaJoules()),
                    "local cell thermal energy must equal signed reaction joules");
            require(service.reactFiniteCell(level, cell).isEmpty(), "one commit per finite cell per due tick");
            return new Step(before, after, result, event);
        }

        void noReaction() {
            require(claimed(level, cell), "gate fixture lost finite ownership");
            GasMixture before = service.sample(level, cell).orElseThrow();
            var result = service.reactFiniteCell(level, cell);
            require(result.isEmpty() || result.get().events().isEmpty(), "temperature gate must reject chemistry");
            GasMixture after = service.sample(level, cell).orElseThrow();
            require(after.gasMoles().equals(before.gasMoles())
                            && close(after.thermalEnergy(), before.thermalEnergy()),
                    "rejected temperature gate must not mutate the finite cell");
        }
    }

    private static void direction(Step step, GasType type, int sign) {
        require(step.event().speciesDelta().getOrDefault(type, 0.0) * sign > 0,
                "incorrect " + type + " direction in " + step.event().id());
    }

    private static void positiveEnergy(Step step) {
        require(step.event().energyDeltaJoules() > 0, "fire must add raw joules");
    }

    private static void zeroEnergy(Step step) {
        require(close(step.event().energyDeltaJoules(), 0), "this chemistry must not create raw joules");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path);
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= Math.max(1e-7, Math.max(Math.abs(a), Math.abs(b)) * 1e-10);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
