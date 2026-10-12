package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.fire.FireStackSystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere"})
public final class AtmosphereRoomGameTests {
    private static final double INJECTED_MOLES = 8.0;
    private static final double ROOM_TEMPERATURE_KELVIN = 293.15;
    private static final int HALLWAY_LENGTH = 31;
    private static final int HALLWAY_OWNERSHIP_PASS_LIMIT = 96;
    private static final int HALLWAY_DIFFUSION_PASS_LIMIT = 60;

    private AtmosphereRoomGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_fire_entity", timeoutTicks = 100)
    public static void committedFiniteFireExposesOnlySupportedSourceOccupants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        requireIsolatedReactionFixture();
        buildSealedHotspotCell(helper, 2, 2);
        BlockPos source = helper.absolutePos(new BlockPos(2, 1, 2));
        var human = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var blaze = helper.spawn(EntityType.BLAZE, new BlockPos(2, 1, 2));
        var stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
        var pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        for (var mob : new net.minecraft.world.entity.Mob[] {human, blaze, pig}) {
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setPos(source.getX() + 0.5, source.getY(), source.getZ() + 0.5);
        }
        stand.setNoGravity(true);
        stand.setPos(source.getX() + 0.5, source.getY(), source.getZ() + 0.5);
        CharacterIdentitySystem.enroll(human, level, ModCharacters.HUMAN_ID);
        require(FireStackSystem.supports(human) && FireStackSystem.supports(pig)
                        && !FireStackSystem.supports(blaze) && !FireStackSystem.supports(stand),
                "human and pig are flammable; immune blaze and unbound stand are not");
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        classifyUntilSample(service, level, source, 12);
        require(isFiniteClaimed(level, source), "source must be finite");
        classifyUntilSample(service, level, source.above(), 12);
        require(isFiniteClaimed(level, source.above()), "source headspace must be finite");
        for (var occupant : new net.minecraft.world.entity.LivingEntity[] {human, blaze, stand, pig}) {
            BlockPos eye = BlockPos.containing(occupant.getEyePosition());
            require(new AABB(source).intersects(occupant.getBoundingBox()),
                    "source occupant must overlap the queried fire cell: " + occupant.getType());
            require((eye.equals(source) || eye.equals(source.above())) && isFiniteClaimed(level, eye),
                    "source occupant eye must be inside finite headspace: " + occupant.getType());
        }
        require(service.addGas(level, source, GasType.TRITIUM, 12, 900), "fire fuel");
        require(service.addGas(level, source, GasType.OXYGEN, 12, 900), "fire oxygen");
        GasMixture initial = service.sample(level, source).orElseThrow();
        boolean[] exposed = {false};
        helper.startSequence().thenExecuteFor(60, () -> {
            service.tick(level, level.getGameTime());
            var first = FireStackSystem.existing(human);
            var pigFirst = FireStackSystem.existing(pig);
            if (first.stacks() <= 0f || pigFirst.stacks() <= 0f) return;
            require(new AABB(source).intersects(human.getBoundingBox())
                            && new AABB(source).intersects(pig.getBoundingBox()),
                    "eligible entities must still overlap the committed fire source");
            GasMixture burned = service.sample(level, source).orElseThrow();
            require(burned.moles(GasType.TRITIUM) < initial.moles(GasType.TRITIUM)
                            && burned.moles(GasType.OXYGEN) < initial.moles(GasType.OXYGEN)
                            && burned.moles(GasType.WATER_VAPOR) > initial.moles(GasType.WATER_VAPOR),
                    "entity stacks require committed finite-cell tritium chemistry");
            require(first.ignited() && pigFirst.stacks() > 0f && pigFirst.ignited(),
                    "supported human and pig receive cycle-committed fire stacks");
            require(!human.isOnFire() && !pig.isOnFire(), "exposure does not assign vanilla fire ticks");
            require(!blaze.hasData(ModDataAttachments.FIRE_STACK.get())
                            && !stand.hasData(ModDataAttachments.FIRE_STACK.get()),
                    "immune and unbound entities must remain untouched");
            exposed[0] = true;
            helper.succeed();
        }).thenExecute(() -> require(exposed[0], "cycle-committed source fire never exposed supported occupants; "
                + "intensity=" + service.fireIntensity(level, source) + ", source="
                + service.sample(level, source).orElseThrow().gasMoles() + ", human="
                + FireStackSystem.existing(human) + ", pig=" + FireStackSystem.existing(pig)));
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_hotspot_spread", timeoutTicks = 400)
    public static void cycleFireReachesOpenReceiverAndGuardsOxygenStarvation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        requireIsolatedReactionFixture();
        // Two sealed, separately claimed pairs. No exterior or entity search participates.
        for (int start : new int[] {2, 6}) {
            for (int x = start - 1; x <= start + 2; x++) for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                for (int y = 1; y <= 2; y++)
                    helper.setBlock(new BlockPos(x, y, z),
                            (x == start || x == start + 1) && z == 2 ? Blocks.AIR : Blocks.STONE);
            }
        }
        // A third source and receiver are finite but separated by a full-height stone wall.
        for (int x = 9; x <= 13; x++) for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            for (int y = 1; y <= 2; y++)
                helper.setBlock(new BlockPos(x, y, z),
                        (x == 10 || x == 12) && z == 2 ? Blocks.AIR : Blocks.STONE);
        }
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos source = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos receiver = source.east();
        BlockPos starvedSource = helper.absolutePos(new BlockPos(6, 1, 2));
        BlockPos starved = starvedSource.east();
        BlockPos walledSource = helper.absolutePos(new BlockPos(10, 1, 2));
        BlockPos walled = helper.absolutePos(new BlockPos(12, 1, 2));
        helper.setBlock(new BlockPos(12, 2, 2), Blocks.STONE);
        var receiverOccupant = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 2));
        receiverOccupant.setNoAi(true);
        receiverOccupant.setNoGravity(true);
        receiverOccupant.setPos(receiver.getX() + 0.5, receiver.getY(), receiver.getZ() + 0.5);
        CharacterIdentitySystem.enroll(receiverOccupant, level, ModCharacters.HUMAN_ID);
        require(FireStackSystem.supports(receiverOccupant), "receiver fixture must be flammable");
        require(level.getBlockState(walledSource.east()).is(Blocks.STONE), "stone must close the third face");
        for (Direction face : Direction.values())
            require(!AtmosphereTopology.canExchange(level, walled, walled.relative(face)),
                    "wall-separated finite cell must be topologically sealed at face " + face);
        for (BlockPos pos : new BlockPos[] {source, receiver, starvedSource, starved, walledSource, walled}) {
            classifyUntilSample(service, level, pos, 12);
            require(isFiniteClaimed(level, pos), "fixture needs finite ownership " + pos);
            if (!pos.equals(walled)) {
                classifyUntilSample(service, level, pos.above(), 12);
                require(isFiniteClaimed(level, pos.above()), "fixture needs finite headspace " + pos.above());
            }
        }
        require(new AABB(receiver).intersects(receiverOccupant.getBoundingBox())
                        && BlockPos.containing(receiverOccupant.getEyePosition()).equals(receiver.above()),
                "receiver occupant must overlap the receiver with eyes in finite headspace");
        for (BlockPos pos : new BlockPos[] {source, starvedSource, walledSource}) {
            require(service.addGas(level, pos, GasType.TRITIUM, 12, 900), "source fuel");
            require(service.addGas(level, pos, GasType.OXYGEN, 12, 900), "source oxygen");
        }
        require(service.addGas(level, receiver, GasType.TRITIUM, 1, 300), "receiver fuel");
        require(service.addGas(level, receiver, GasType.OXYGEN, 1, 300), "receiver oxygen");
        require(service.sample(level, starved).orElseThrow().moles(GasType.OXYGEN) == 0.0,
                "finite vacuum neighbor must start without oxygen");
        require(service.addGas(level, starved, GasType.TRITIUM, 1, 300), "starved receiver fuel");
        require(service.addGas(level, walled, GasType.TRITIUM, 1, 300), "walled receiver fuel");
        require(service.addGas(level, walled, GasType.OXYGEN, 1, 300), "walled receiver oxygen");
        GasMixture starvedInitial = service.sample(level, starved).orElseThrow();
        GasMixture walledInitial = service.sample(level, walled).orElseThrow();
        GasMixture initialReceiver = service.sample(level, receiver).orElseThrow();
        boolean[] sourceFireSeen = {false}, receiverMetadataSeen = {false}, receiverBurned = {false};
        helper.startSequence().thenExecuteFor(120, () -> {
            GasMixture receiverBefore = service.sample(level, receiver).orElseThrow();
            HotspotState sourceBeforeState = actualHotspot(service, level, source);
            HotspotState receiverBeforeState = actualHotspot(service, level, receiver);
            service.tick(level, level.getGameTime());
            GasMixture receiverAfter = service.sample(level, receiver).orElseThrow();
            HotspotState sourceAfterState = actualHotspot(service, level, source);
            HotspotState receiverAfterState = actualHotspot(service, level, receiver);
            sourceFireSeen[0] |= sourceAfterState != null && sourceAfterState.bypass();
            receiverMetadataSeen[0] |= receiverAfterState != null;
            if (sourceBeforeState != null && sourceBeforeState.skippedFirstProcess() && sourceBeforeState.bypass()
                    && receiverBeforeState == null && receiverAfterState != null) {
                require(receiverAfter.gasMoles().equals(receiverBefore.gasMoles()),
                        "a first-time HOT spread recipient must retain its gas until a later processing pass");
                receiverMetadataSeen[0] = true;
            }
            // The open pair intentionally diffuses reactants and products. Positive hotspot volume
            // is the committed receiver fire extent; local net T can rise as fuel enters by LINDA.
            receiverBurned[0] |= receiverAfterState != null && receiverAfterState.volumeLiters() > 0;
            GasMixture starvedAfter = service.sample(level, starved).orElseThrow();
            GasMixture walledAfter = service.sample(level, walled).orElseThrow();
            if (starvedAfter.moles(GasType.OXYGEN) <= 0.2) {
                require(actualHotspot(service, level, starved) == null,
                        "HOT exposure must not create hotspot metadata for an oxygen-starved finite neighbor");
            }
            require(walledAfter.gasMoles().equals(walledInitial.gasMoles())
                            && Math.abs(walledAfter.thermalEnergy() - walledInitial.thermalEnergy())
                                    <= 4 * (Math.ulp((float) walledAfter.thermalEnergy())
                                            + Math.ulp((float) walledInitial.thermalEnergy())),
                    "wall-sealed finite cell must not receive hotspot heat or chemistry; before="
                            + walledInitial.gasMoles() + "/" + walledInitial.thermalEnergy() + "J, after="
                            + walledAfter.gasMoles() + "/" + walledAfter.thermalEnergy() + "J, hotspot="
                            + actualHotspot(service, level, walled));
            if (sourceFireSeen[0] && receiverMetadataSeen[0] && receiverBurned[0]) {
                require(new AABB(receiver).intersects(receiverOccupant.getBoundingBox()),
                        "receiver occupant must still overlap the finite receiver cell");
            require(service.boundaryGasLedger(level).values().stream().allMatch(value -> Math.abs(value) < 1e-9),
                        "sealed fire fixture must not export gas");
                helper.succeed();
            }
        }).thenExecute(() -> require(sourceFireSeen[0] && receiverMetadataSeen[0] && receiverBurned[0],
                "cycle fire failed to expose and later burn the open-face receiver; sourceSeen=" + sourceFireSeen[0]
                        + ", receiverMetadata=" + receiverMetadataSeen[0] + ", receiverBurned=" + receiverBurned[0]
                        + ", source=" + actualHotspot(service, level, source) + ", receiver="
                        + actualHotspot(service, level, receiver) + ", receiverInitial=" + initialReceiver.gasMoles()
                        + ", receiverFinal=" + service.sample(level, receiver).orElseThrow().gasMoles()));
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_reaction_commit", timeoutTicks = 100)
    public static void explicitReactionCommitsOnlyFiniteCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        requireIsolatedReactionFixture();
        buildSealedHotspotCell(helper, 2, 2);
        BlockPos finite = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos exterior = helper.absolutePos(new BlockPos(5, 100, 5));
        int firstFreeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, exterior.getX(), exterior.getZ());
        if (firstFreeY > exterior.getY()) exterior = new BlockPos(exterior.getX(), firstFreeY + 1, exterior.getZ());
        require(exterior.getY() < level.getMaxBuildHeight(), "fixture needs a loaded sky cell");
        level.getChunk(exterior);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        Map<String, GasReactionData.EffectType> baselineReactions = Map.of(
                "plasma_fire", GasReactionData.EffectType.PLASMA_FIRE,
                "tritium_fire", GasReactionData.EffectType.TRITIUM_FIRE,
                "frezon_coolant", GasReactionData.EffectType.FREZON_COOLANT,
                "frezon_production", GasReactionData.EffectType.FREZON_PRODUCTION,
                "ammonia_oxygen", GasReactionData.EffectType.AMMONIA_OXYGEN,
                "n2o_decomposition", GasReactionData.EffectType.N2O_DECOMPOSITION);
        baselineReactions.forEach((name, effect) -> {
            var id = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, name);
            var definition = PrototypeRuntime.serverGasReactions().get(id);
            require(definition != null && definition.effects().stream().anyMatch(e -> e.type() == effect),
                    "server reaction snapshot missing baseline effect " + effect.id() + " at " + id);
        });
        classifyUntilSample(service, level, finite, 12);
        require(isFiniteClaimed(level, finite), "reaction cell must have a finite claim");
        classifyUntilSample(service, level, finite.above(), 12);
        require(isFiniteClaimed(level, finite.above()), "reaction cell headspace must be finite");
        require(service.addGas(level, finite, GasType.TRITIUM, 2, 900), "inject tritium");
        require(service.addGas(level, finite, GasType.OXYGEN, 4, 900), "inject oxygen");
        if (level.getGameTime() % AtmosphereService.TICK_CADENCE != 0)
            require(service.reactFiniteCell(level, finite).isEmpty(), "odd-tick explicit pass must be rejected");
        BlockPos sky = exterior;
        Runnable verifyDuePass = () -> {
        GasMixture before = service.sample(level, finite).orElseThrow();
        GasMixture skyBefore = service.sample(level, sky).orElseThrow();
        require(service.reactFiniteCell(level, sky).isEmpty(), "exterior cannot react");
        var committed = service.reactFiniteCell(level, finite).orElseThrow(
                () -> new GameTestAssertException("expected a committed finite tritium fire"));
        GasMixture after = service.sample(level, finite).orElseThrow();
        var fireEvents = committed.events().stream()
                .filter(event -> event.effect() == GasReactionData.EffectType.TRITIUM_FIRE).toList();
        require(fireEvents.size() == 1, "one partial tritium-fire event per explicit step");
        double fuelConsumed = before.moles(GasType.TRITIUM) - after.moles(GasType.TRITIUM);
        require(fuelConsumed > 0 && fuelConsumed < before.moles(GasType.TRITIUM) * 0.5,
                "seeded fire must not consume the whole cell's fuel");
        require(Math.abs(fireEvents.getFirst().speciesDelta().getOrDefault(GasType.TRITIUM, 0.0)
                + fuelConsumed) <= Math.ulp((float) before.moles(GasType.TRITIUM))
                        + Math.ulp((float) after.moles(GasType.TRITIUM)),
                "fire event must report only committed partial fuel consumption within cell-float rounding");
        require(service.reactFiniteCell(level, finite).isEmpty(),
                "second explicit pass in the same game tick must not commit");
        require(service.sample(level, finite).orElseThrow().moles(GasType.TRITIUM) == after.moles(GasType.TRITIUM),
                "second explicit pass must not consume more tritium fuel");
        require(after.moles(GasType.TRITIUM) < before.moles(GasType.TRITIUM)
                        && after.moles(GasType.OXYGEN) < before.moles(GasType.OXYGEN)
                        && after.moles(GasType.WATER_VAPOR) > before.moles(GasType.WATER_VAPOR)
                        && after.thermalEnergy() > before.thermalEnergy(),
                "fire must commit products and thermal energy together");
        require(committed.mixture() == after || committed.mixture().gasMoles().equals(after.gasMoles()),
                "committed result must match the saved composition");
        require(service.sample(level, sky).orElseThrow().gasMoles().equals(skyBefore.gasMoles()),
                "strict exterior remains ambient");
        LevelChunk skyChunk = (LevelChunk) level.getChunk(sky);
        var skyData = skyChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        require(skyData == null || skyData.get(sky.getX() & 15, sky.getY(), sky.getZ() & 15) == null,
                "reaction must not persist an exterior override");
        helper.succeed();
        };
        if (level.getGameTime() % AtmosphereService.TICK_CADENCE == 0) verifyDuePass.run();
        else com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, verifyDuePass);
    }

    private static void requireIsolatedReactionFixture() {
        // Tests operate on fixture cells; global atmospherics is always active.
    }

    private static void buildSealedHotspotCell(GameTestHelper helper, int centerX, int centerZ) {
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                for (int y = 1; y <= 2; y++)
                    helper.setBlock(new BlockPos(x, y, z),
                            x == centerX && z == centerZ ? Blocks.AIR : Blocks.STONE);
            }
        }
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_long_hallway", timeoutTicks = 120)
    public static void longHallwayServiceDiffusesTritiumAcrossAll124Cells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildTritiumHallway(helper);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos near = helper.absolutePos(new BlockPos(2, 1, 2));

        // Strict samples queue ownership work; keep requesting every cell while the service
        // classifies the whole room, and do not inject until every cell has a finite claim.
        for (int x = 2; x <= HALLWAY_LENGTH + 1; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    BlockPos cell = helper.absolutePos(new BlockPos(x, y, z));
                    level.getChunk(cell); // The 31-cell structure may cross a chunk boundary.
                    require(service.sample(level, cell).isEmpty(),
                            "hallway cells must be unknown before ownership classification");
                }
            }
        }

        int ownershipPasses = 0;
        BlockPos ownershipSeed = helper.absolutePos(new BlockPos(2, 1, 2));
        service.activateCellAndNeighbors(level, ownershipSeed);
        service.activateCellAndNeighbors(level, ownershipSeed);
        service.sample(level, ownershipSeed);
        while (ownershipPasses < HALLWAY_OWNERSHIP_PASS_LIMIT
                && !hallwayHasFiniteClaims(level, helper)) {
            ownershipPasses++;
            service.tick(level, ownershipPasses);
            for (int x = 2; x <= HALLWAY_LENGTH + 1; x++) {
                for (int y = 1; y <= 2; y++) {
                    for (int z = 2; z <= 3; z++)
                        service.sample(level, helper.absolutePos(new BlockPos(x, y, z)));
                }
            }
        }
        require(hallwayHasFiniteClaims(level, helper),
                "all 124 hallway cells must be finitely owned before injection; classification passes="
                        + ownershipPasses);
        require(service.sample(level, near).orElseThrow().totalMoles() == 0.0,
                "classified hallway must begin at vacuum");
        require(service.addGas(level, near, GasType.TRITIUM, 2.0, ROOM_TEMPERATURE_KELVIN),
                "TRITIUM injection into the finite hallway must succeed");

        int diffusionPasses = 0;
        String lastTransportDiagnostic = "none";
        while (diffusionPasses < HALLWAY_DIFFUSION_PASS_LIMIT
                && !farHallwayHasTritium(service, level, helper)) {
            diffusionPasses++;
            service.tick(level, ownershipPasses + diffusionPasses);
            lastTransportDiagnostic = hallwayTransportSnapshot(service, level, helper, diffusionPasses);
        }
        require(farHallwayHasTritium(service, level, helper),
                "TRITIUM must reach every preclassified far-end hallway cell within "
                        + HALLWAY_DIFFUSION_PASS_LIMIT + " due passes; elapsed=" + diffusionPasses
                        + " passes; last transport=" + lastTransportDiagnostic);

        double totalTritium = hallwayMoles(service, level, helper, GasType.TRITIUM);
        require(Math.abs(totalTritium - 2.0) < 1.0e-6,
                "closed hallway must conserve its 2 mol TRITIUM dose; total=" + totalTritium);
        require(service.boundaryGasLedger(level).isEmpty(),
                "airtight hallway must not export any gas to an exterior ledger");
        helper.succeed();
    }

    private static String hallwayTransportSnapshot(AtmosphereService service, ServerLevel level,
                                                    GameTestHelper helper, int pass) {
        int nonempty = 0;
        double total = 0;
        double minPressure = Double.POSITIVE_INFINITY;
        double maxPressure = Double.NEGATIVE_INFINITY;
        StringBuilder farCells = new StringBuilder();
        for (int x = 2; x <= HALLWAY_LENGTH + 1; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    GasMixture gas = service.diagnosticCell(level, helper.absolutePos(new BlockPos(x, y, z))).gas();
                    if (gas == null) continue;
                    double pressure = gas.pressureKpa();
                    minPressure = Math.min(minPressure, pressure);
                    maxPressure = Math.max(maxPressure, pressure);
                    double tritium = gas.moles(GasType.TRITIUM);
                    total += tritium;
                    if (tritium > 0) nonempty++;
                    if (x == HALLWAY_LENGTH + 1) {
                        if (farCells.length() > 0) farCells.append(',');
                        farCells.append(tritium);
                    }
                }
            }
        }
        AtmosphereService.PressureDiagnostic pressure = service.diagnosticPressure(level);
        AtmosphereService.PatchDiagnostic patch = service.diagnosticPatch(level);
        AtmosphereService.DiagnosticTiming timing = service.diagnosticTiming(level);
        return "pass=" + pass + " stage=" + service.atmosphereStage(level) + "/c"
                + service.completedCycles(level) + " n=" + nonempty + "/124 N=" + total + " P="
                + minPressure + ".." + maxPressure + " far=" + farCells + " solver=" + pressure
                + " patch=" + patch + " timing=" + timing;
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_long_hallway", timeoutTicks = 120)
    public static void repeatedTritiumProducerDoseTransportsAcrossNominalHallway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildTritiumHallway(helper);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));

        for (int x = 2; x <= HALLWAY_LENGTH + 1; x++)
            for (int y = 1; y <= 2; y++)
                for (int z = 2; z <= 3; z++) {
                    BlockPos cell = helper.absolutePos(new BlockPos(x, y, z));
                    level.getChunk(cell);
                    require(service.sample(level, cell).isEmpty(), "hallway ownership must be classified");
                }
        int ownershipPasses = 0;
        while (ownershipPasses < HALLWAY_OWNERSHIP_PASS_LIMIT && !hallwayHasFiniteClaims(level, helper)) {
            ownershipPasses++;
            service.tick(level, ownershipPasses);
            for (int x = 2; x <= HALLWAY_LENGTH + 1; x++)
                for (int y = 1; y <= 2; y++)
                    for (int z = 2; z <= 3; z++)
                        service.sample(level, helper.absolutePos(new BlockPos(x, y, z)));
        }
        require(hallwayHasFiniteClaims(level, helper), "nominal hallway claim classification passes=" + ownershipPasses);

        // 41.6 mol in a one-cubic-metre cell is approximately 101.5 kPa at 293.15 K.
        for (int x = 2; x <= HALLWAY_LENGTH + 1; x++)
            for (int y = 1; y <= 2; y++)
                for (int z = 2; z <= 3; z++)
                    require(service.addBreathableAir(level, helper.absolutePos(new BlockPos(x, y, z)),
                                    41.6, ROOM_TEMPERATURE_KELVIN), "seed nominal breathable mixture");
        BlockPos near = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos far = helper.absolutePos(new BlockPos(HALLWAY_LENGTH + 1, 1, 2));
        require(Math.abs(service.sample(level, near).orElseThrow().pressureKpa(1.0) - 101.5) < 0.2,
                "fixture source must start near nominal station pressure");
        double initialHallwayMoles = hallwayMoles(service, level, helper, GasType.OXYGEN)
                + hallwayMoles(service, level, helper, GasType.NITROGEN);
        double initialEnergy = hallwayEnergy(service, level, helper);
        double injected = 0.0;
        int observedSteps = 60; // Three seconds at 20 TPS; the atmosphere completes five 0.6-second cycles.
        for (int step = 1; step <= observedSteps; step++) {
            GasMixture source = service.sample(level, near).orElseThrow();
            // Equivalent to the 20 mol/s producer's 1 mol/tick dose, with its 202.65 kPa cap.
            if (source.pressureKpa(1.0) < 202.65) {
                double dose = 1.0;
                if (source.withGasDelta(GasType.TRITIUM, dose, ROOM_TEMPERATURE_KELVIN)
                        .pressureKpa(1.0) > 202.65) {
                    double low = 0.0;
                    double high = dose;
                    for (int search = 0; search < 80; search++) {
                        double middle = low + (high - low) * 0.5;
                        if (source.withGasDelta(GasType.TRITIUM, middle, ROOM_TEMPERATURE_KELVIN)
                                .pressureKpa(1.0) <= 202.65) low = middle;
                        else high = middle;
                    }
                    dose = low;
                }
                if (dose > 0.0 && service.addGas(level, near, GasType.TRITIUM, dose,
                        ROOM_TEMPERATURE_KELVIN)) injected += dose;
            }
            long gameTime = ownershipPasses + step;
            service.tick(level, gameTime);
        }
        GasMixture farAfterWindow = service.sample(level, far).orElseThrow();
        GasMixture nearAfterWindow = service.sample(level, near).orElseThrow();
        double farTritium = farAfterWindow.moles(GasType.TRITIUM);
        double nearTritium = nearAfterWindow.moles(GasType.TRITIUM);
        double nearPressure = nearAfterWindow.pressureKpa(1.0);
        double farPressure = farAfterWindow.pressureKpa(1.0);
        double totalMolesGradient = nearAfterWindow.totalMoles() - farAfterWindow.totalMoles();
        double totalPressureGradient = nearPressure - farPressure;
        // Monstermos equalizes pressure with bulk packets; species-specific LINDA fallback is local.
        // Thus a measurable far pressure response is the producer-window transport assertion;
        // visible far tritium may lag the pressure-driven bulk flow without contradicting SS14.
        require(farPressure > 101.55 && totalMolesGradient >= 0.0 && totalMolesGradient < injected,
                "after " + observedSteps + " server callbacks (3.0s), far tritium=" + farTritium
                        + " mol, near tritium=" + nearTritium + " mol, near pressure=" + nearPressure
                        + " kPa, far pressure=" + farPressure + " kPa, total injected=" + injected
                        + " mol, near/far total-moles gradient=" + totalMolesGradient
                        + " mol, near/far pressure gradient=" + totalPressureGradient
                        + " kPa; requires far pressure >101.55 kPa and reduced bulk-mole gradient");
        double energyAfterDoses = hallwayEnergy(service, level, helper);
        int quietCallbacks = 0;
        for (int step = 1; step <= 2000
                && service.sample(level, far).orElseThrow().moles(GasType.TRITIUM) <= 0.0; step++) {
            service.tick(level, ownershipPasses + observedSteps + step);
            quietCallbacks = step;
        }
        double farTritiumEventually = service.sample(level, far).orElseThrow().moles(GasType.TRITIUM);
        GasMixture nearEventually = service.sample(level, near).orElseThrow();
        GasMixture farEventually = service.sample(level, far).orElseThrow();
        require(farTritiumEventually > 0.0,
                 "tritium must eventually traverse the 29-face-edge hallway within 2000 quiet callbacks; waited="
                         + quietCallbacks + ", far tritium=" + farTritiumEventually
                        + " mol, near tritium=" + nearEventually.moles(GasType.TRITIUM)
                        + " mol, near pressure=" + nearEventually.pressureKpa(1.0)
                        + " kPa, far pressure=" + farEventually.pressureKpa(1.0) + " kPa, injected=" + injected
                        + " mol; species diffusion may lag pressure equilibration");
        double finalEnergy = hallwayEnergy(service, level, helper);
        double expectedEnergy = initialEnergy + injected * GasType.TRITIUM.molarHeatCapacity()
                * ROOM_TEMPERATURE_KELVIN;
        double energyTolerance = Math.max(1.0e-5, 8 * hallwayEnergyUlpBudget(service, level, helper));
        require(Math.abs(energyAfterDoses - expectedEnergy) < energyTolerance
                        && Math.abs(finalEnergy - expectedEnergy) < energyTolerance,
                "closed nominal room must account for producer-dose enthalpy and conserve it; initial="
                        + initialEnergy + ", injected=" + injected + " mol, expected=" + expectedEnergy
                        + ", after 1s=" + energyAfterDoses + ", final=" + finalEnergy
                         + ", per-cell float-storage ULP tolerance=" + energyTolerance);
        int totalCallbacks = ownershipPasses + observedSteps + quietCallbacks;
        for (GasType gas : new GasType[]{GasType.TRITIUM, GasType.OXYGEN, GasType.NITROGEN}) {
            double expected = gas == GasType.TRITIUM ? injected
                    : gas == GasType.OXYGEN ? initialHallwayMoles * 0.21 : initialHallwayMoles * 0.79;
            double actual = hallwayMoles(service, level, helper, gas);
            double ulpBudget = hallwaySpeciesUlpBudget(service, level, helper, gas, expected, totalCallbacks,
                    gas == GasType.TRITIUM ? observedSteps : 0);
            require(Math.abs(actual - expected) <= ulpBudget,
                    "closed hallway inventory exceeded per-cell float-storage ULP budget for " + gas
                            + "; expected=" + expected + ", actual=" + actual + ", callbacks=" + totalCallbacks
                            + ", budget=" + ulpBudget);
        }
        require(service.boundaryGasLedger(level).isEmpty(), "sealed hallway must not export gas");
        helper.succeed();
    }

    private static double hallwayEnergy(AtmosphereService service, ServerLevel level, GameTestHelper helper) {
        double energy = 0.0;
        for (int x = 2; x <= HALLWAY_LENGTH + 1; x++)
            for (int y = 1; y <= 2; y++)
                for (int z = 2; z <= 3; z++)
                    energy += service.sample(level, helper.absolutePos(new BlockPos(x, y, z)))
                            .orElseThrow().thermalEnergy();
        return energy;
    }

    private static double hallwayEnergyUlpBudget(AtmosphereService service, ServerLevel level, GameTestHelper helper) {
        double budget = 0;
        for (int x = 2; x <= HALLWAY_LENGTH + 1; x++)
            for (int y = 1; y <= 2; y++)
                for (int z = 2; z <= 3; z++)
                    budget += Math.ulp((float) service.sample(level, helper.absolutePos(new BlockPos(x, y, z)))
                            .orElseThrow().thermalEnergy());
        return budget;
    }

    private static double hallwaySpeciesUlpBudget(AtmosphereService service, ServerLevel level, GameTestHelper helper,
                                                  GasType type, double expectedTotal, int callbacks,
                                                  int producerDoseWrites) {
        // Bound float storage rounding across actual producer writes and LINDA/equalization opportunities.
        double transportCommitOpportunities = Math.ceil(callbacks / (double) AtmosphereService.TICK_CADENCE);
        double budget = Math.ulp((float) expectedTotal) * (producerDoseWrites + transportCommitOpportunities);
        for (int x = 2; x <= HALLWAY_LENGTH + 1; x++)
            for (int y = 1; y <= 2; y++)
                for (int z = 2; z <= 3; z++)
                    budget += Math.ulp((float) service.sample(level, helper.absolutePos(new BlockPos(x, y, z)))
                            .orElseThrow().moles(type)) * transportCommitOpportunities;
        return budget;
    }

    private static void buildTritiumHallway(GameTestHelper helper) {
        int firstX = 1;
        int lastX = HALLWAY_LENGTH + 2;
        for (int x = firstX; x <= lastX; x++) {
            for (int z = 1; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int x = firstX; x <= lastX; x++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
            }
            for (int z = 1; z <= 4; z++) {
                helper.setBlock(new BlockPos(firstX, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(lastX, y, z), Blocks.STONE);
            }
        }
    }

    private static boolean hallwayHasFiniteClaims(ServerLevel level, GameTestHelper helper) {
        for (int x = 2; x <= HALLWAY_LENGTH + 1; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    BlockPos cell = helper.absolutePos(new BlockPos(x, y, z));
                    LevelChunk chunk = (LevelChunk) level.getChunk(cell);
                    var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
                    if (data == null || !data.isFiniteClaimed(cell.getX() & 15, cell.getY(), cell.getZ() & 15))
                        return false;
                }
            }
        }
        return true;
    }

    private static boolean isFiniteClaimed(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = (LevelChunk) level.getChunk(pos);
        var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        return data != null && data.isFiniteClaimed(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
    }

    private static BlockPos firstMissingSealedRoomClaim(ServerLevel level, GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 4; z++) {
                    BlockPos cell = helper.absolutePos(new BlockPos(x, y, z));
                    if (!isFiniteClaimed(level, cell)) return cell;
                }
            }
        }
        return null;
    }

    private static void requireGapSamplePresent(AtmosphereService service, ServerLevel level, BlockPos gap,
                                               BlockPos producer, String stage) {
        if (service.sample(level, gap).isPresent()) return;
        ChunkAccess loaded = level.getChunkSource().getChunk(gap.getX() >> 4, gap.getZ() >> 4,
                ChunkStatus.FULL, false);
        LevelChunk chunk = loaded instanceof LevelChunk full ? full : null;
        if (chunk == null) {
            throw new GameTestAssertException("verified exterior gap sample missing " + stage
                    + "; gap=" + gap + " chunk=" + chunkCoordinates(gap)
                    + ", producer=" + producer + " chunk=" + chunkCoordinates(producer)
                    + ", gapChunkLoaded=false (diagnostics do not load chunks or advance simulation)");
        }
        var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        boolean finiteClaimed = data != null
                && data.isFiniteClaimed(gap.getX() & 15, gap.getY(), gap.getZ() & 15);
        var state = level.getBlockState(gap);
        int firstFreeY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING,
                gap.getX() & 15, gap.getZ() & 15);
        throw new GameTestAssertException("verified exterior gap sample missing " + stage
                + "; gap=" + gap + " chunk=" + chunkCoordinates(gap)
                + ", producer=" + producer + " chunk=" + chunkCoordinates(producer)
                + ", firstFreeY=" + firstFreeY + ", gapState=" + state
                + ", gapPassable=" + AtmosphereTopology.isPassable(level, gap)
                + ", finiteClaimed=" + finiteClaimed);
    }

    private static String chunkCoordinates(BlockPos pos) {
        return "(" + (pos.getX() >> 4) + "," + (pos.getZ() >> 4) + ")";
    }

    private static double hallwayMoles(AtmosphereService service, ServerLevel level, GameTestHelper helper,
                                       GasType type) {
        double total = 0.0;
        for (int x = 2; x <= HALLWAY_LENGTH + 1; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    BlockPos cell = helper.absolutePos(new BlockPos(x, y, z));
                    total += service.sample(level, cell)
                            .orElseThrow(() -> new GameTestAssertException("room cell became unclassified at "
                                    + cell)).moles(type);
                }
            }
        }
        return total;
    }

    private static boolean farHallwayHasTritium(AtmosphereService service, ServerLevel level,
                                                 GameTestHelper helper) {
        boolean allPositive = true;
        for (int x = 28; x <= 32; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    GasMixture mixture = service.sample(level, helper.absolutePos(new BlockPos(x, y, z)))
                            .orElseThrow(() -> new GameTestAssertException(
                                    "preclassified far-end hallway cell became non-finite"));
                    allPositive &= mixture.moles(GasType.TRITIUM) > 0.0;
                }
            }
        }
        return allPositive;
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_sealed_room", timeoutTicks = 100)
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
        int ownershipPasses = 0;
        BlockPos missingClaim;
        while (ownershipPasses < 12 && (missingClaim = firstMissingSealedRoomClaim(level, helper)) != null) {
            ownershipPasses++;
            service.tick(level, (long) (12 + ownershipPasses) * AtmosphereService.TICK_CADENCE);
            for (int x = 2; x <= 4; x++) {
                for (int y = 1; y <= 2; y++) {
                    for (int z = 2; z <= 4; z++)
                        service.sample(level, helper.absolutePos(new BlockPos(x, y, z)));
                }
            }
        }
        missingClaim = firstMissingSealedRoomClaim(level, helper);
        require(missingClaim == null,
                "all 18 sealed-room cells must have finite claims before injection; missing=" + missingClaim
                        + ", classification passes=" + ownershipPasses);
        require(service.sample(level, near).isPresent() && service.sample(level, far).isPresent(),
                "near and far room cells must both be strictly sampleable after ownership classification");
        require(service.addBreathableAir(level, near, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "breathable gas injection into sealed room must succeed");

        double pressureBefore = service.sample(level, far).orElseThrow().pressureKpa(1.0);
        double oxygenBefore = roomMoles(service, level, GasType.OXYGEN, helper);
        double nitrogenBefore = roomMoles(service, level, GasType.NITROGEN, helper);
        require(Math.abs(oxygenBefore - INJECTED_MOLES * 0.21) <= Math.ulp((float) (INJECTED_MOLES * 0.21)),
                "injection must add the expected oxygen inventory");
        require(Math.abs(nitrogenBefore - INJECTED_MOLES * 0.79) <= Math.ulp((float) (INJECTED_MOLES * 0.79)),
                "injection must add the expected nitrogen inventory");

        for (long gameTime = AtmosphereService.TICK_CADENCE;
             gameTime <= 40L * AtmosphereService.TICK_CADENCE; gameTime += AtmosphereService.TICK_CADENCE) {
            service.tick(level, gameTime);
        }

        GasMixture farMixture = service.sample(level, far).orElseThrow();
        require(farMixture.pressureKpa(1.0) > pressureBefore + 0.05,
                "gas must reach a distant room cell within 40 atmosphere passes; pressure was "
                        + farMixture.pressureKpa(1.0) + " kPa");
        require(Math.abs(roomMoles(service, level, GasType.OXYGEN, helper) - oxygenBefore)
                        <= roomSpeciesUlpBudget(service, level, helper, GasType.OXYGEN, oxygenBefore),
                "closed room diffusion must conserve oxygen");
        require(Math.abs(roomMoles(service, level, GasType.NITROGEN, helper) - nitrogenBefore)
                        <= roomSpeciesUlpBudget(service, level, helper, GasType.NITROGEN, nitrogenBefore),
                "closed room diffusion must conserve nitrogen");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_covered_exterior", timeoutTicks = 100)
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
        require(service.sample(level, gap).orElseThrow(() -> new GameTestAssertException(
                        "open side gap was not classified as exterior at " + gap)).totalMoles() == 0.0,
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
        double oxygenBeforeBreach = coveredRoomMoles(service, level, GasType.OXYGEN, helper);
        double nitrogenBeforeBreach = coveredRoomMoles(service, level, GasType.NITROGEN, helper);
        require(gasBeforeBreach > 0.0, "finite room must contain injected gas before breach");
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.AIR);
        service.topologyChanged(level, gap);
        GasMixture retained = service.sample(level, roomCell).orElseThrow(
                () -> new GameTestAssertException("previous finite claims must survive the breach at " + roomCell));
        require(retained.totalMoles() > 0.0,
                "breach must not instantly erase gas from the previously finite room cell");

        for (long gameTime = 1; gameTime <= 96; gameTime++) service.tick(level, gameTime);
        double gasAfterDrain = roomMoles(service, level, helper);
        require(gasAfterDrain < gasBeforeBreach * 0.5,
                "gas should drain substantially through the exterior edge; before=" + gasBeforeBreach
                        + ", after=" + gasAfterDrain);
        require(service.sample(level, gap).orElseThrow(() -> new GameTestAssertException(
                        "breached exterior gap was not classified at " + gap)).totalMoles() == 0.0,
                "vacuum exterior must remain ambient rather than accumulating exported gas");
        double oxygenExported = service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0);
        double nitrogenExported = service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        require(Math.abs(oxygenBeforeBreach - coveredRoomMoles(service, level, GasType.OXYGEN, helper)
                - oxygenExported) < 1.0e-6,
                "oxygen export ledger must equal removed oxygen; before=" + oxygenBeforeBreach + ", after="
                        + coveredRoomMoles(service, level, GasType.OXYGEN, helper) + ", ledger=" + oxygenExported);
        require(Math.abs(nitrogenBeforeBreach - coveredRoomMoles(service, level, GasType.NITROGEN, helper)
                - nitrogenExported) < 1.0e-6,
                "nitrogen export ledger must equal removed nitrogen; before=" + nitrogenBeforeBreach + ", after="
                        + coveredRoomMoles(service, level, GasType.NITROGEN, helper) + ", ledger=" + nitrogenExported);
        require(oxygenExported >= 0.0 && nitrogenExported >= 0.0
                        && oxygenExported + nitrogenExported <= gasBeforeBreach + 1.0e-6,
                "boundary exchange must not create gas");

        // Unrelated producer-like updates and broad neighbor notifications must not invalidate a
        // separately proven covered exterior gap (even momentarily).
        BlockPos unrelatedProducer = helper.absolutePos(new BlockPos(7, 1, 1));
        requireGapSamplePresent(service, level, gap, unrelatedProducer, "before unrelated producer stone edit");
        helper.setBlock(new BlockPos(7, 1, 1), Blocks.STONE);
        service.topologyChanged(level, unrelatedProducer);
        requireGapSamplePresent(service, level, gap, unrelatedProducer, "after unrelated producer stone edit");
        helper.setBlock(new BlockPos(7, 1, 1), Blocks.AIR);
        service.topologyChanged(level, unrelatedProducer);
        requireGapSamplePresent(service, level, gap, unrelatedProducer, "after unrelated producer air edit");
        for (int notify = 0; notify < 4; notify++) {
            service.topologyChanged(level, unrelatedProducer);
            requireGapSamplePresent(service, level, gap, unrelatedProducer,
                    "after unrelated producer notification " + (notify + 1));
        }
        double exportedBeforeProducerDose = service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0)
                + service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        require(service.addBreathableAir(level, roomCell, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "finite claimed room cell accepts a fresh producer-like gas dose");
        for (long gameTime = 14L * AtmosphereService.TICK_CADENCE;
             gameTime <= 30L * AtmosphereService.TICK_CADENCE; gameTime += AtmosphereService.TICK_CADENCE) {
            service.tick(level, gameTime);
        }
        require(service.sample(level, gap).isPresent(),
                "the active covered boundary must be automatically reclassified without analyzer sampling");
        double exportedAfterProducerDose = service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0)
                + service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        require(exportedAfterProducerDose > exportedBeforeProducerDose + 1.0e-6,
                "producer-like gas dose must be exported after automatic boundary rediscovery");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_breathable_boundary", timeoutTicks = 100)
    public static void depletedFiniteRoomReplenishesThroughBreathableOpeningAndKeepsSavedState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCoveredRoomWithSideGap(helper);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of());
        BlockPos gapLocal = new BlockPos(3, 1, 1);
        BlockPos gap = helper.absolutePos(gapLocal);
        BlockPos roomCell = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.setBlock(gapLocal, Blocks.STONE);
        service.topologyChanged(level, gap);
        classifyUntilSample(service, level, roomCell, 12);
        require(isFiniteClaimed(level, roomCell), "sealed room must be finitely claimed before depletion");

        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                BlockPos cell = helper.absolutePos(new BlockPos(x, 1, z));
                classifyUntilSample(service, level, cell, 12);
            }
        }
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                BlockPos cell = helper.absolutePos(new BlockPos(x, 1, z));
                GasMixture initial = service.sample(level, cell).orElseThrow();
                require(service.removeGasUpTo(level, cell, initial.totalMoles()) > 0.0,
                        "finite room cell must be depleted explicitly at " + cell);
            }
        }
        for (int pass = 1; pass <= 3; pass++)
            service.tick(level, (long) (pass + 12) * AtmosphereService.TICK_CADENCE);
        require(coveredRoomMoles(service, level, GasType.OXYGEN, helper) == 0.0
                        && coveredRoomMoles(service, level, GasType.NITROGEN, helper) == 0.0,
                "sealed depleted room must not refill from ambient");

        helper.setBlock(gapLocal, Blocks.AIR);
        service.topologyChanged(level, gap);
        for (int pass = 1; pass <= 12; pass++)
            service.tick(level, (long) (pass + 16) * AtmosphereService.TICK_CADENCE);
        require(coveredRoomMoles(service, level, GasType.OXYGEN, helper) > 0.0
                        && coveredRoomMoles(service, level, GasType.NITROGEN, helper) > 0.0,
                "breathable exterior must replenish both gases through the finite boundary");
        require(service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0) < 0.0
                        && service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0) < 0.0,
                "ambient replenishment must be accounted as signed boundary import, not space export");

        // Keep the interior gradient active after the initial import. Each finite equalization
        // patch can change on every pass, but must not starve the immutable ambient edge.
        double oxygenImportedBefore = service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0);
        double nitrogenImportedBefore = service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        for (int pass = 1; pass <= 8; pass++) {
            GasMixture boundary = service.sample(level, roomCell).orElseThrow();
            require(service.removeGasUpTo(level, roomCell, boundary.totalMoles() * 0.5) > 0.0,
                    "persistent gradient must remove finite boundary gas on pass " + pass);
            service.tick(level, (long) (pass + 28) * AtmosphereService.TICK_CADENCE);
        }
        require(service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0) < oxygenImportedBefore
                        && service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0) < nitrogenImportedBefore,
                "persistent changing finite patch must keep importing both ambient species");
        require(coveredRoomMoles(service, level, GasType.OXYGEN, helper) > 0.0
                        && coveredRoomMoles(service, level, GasType.NITROGEN, helper) > 0.0,
                "both imported species must remain in the finite room under repeated gradient");

        helper.setBlock(gapLocal, Blocks.STONE);
        service.topologyChanged(level, gap);
        LevelChunk chunk = (LevelChunk) level.getChunk(roomCell);
        var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        require(isFiniteClaimed(level, roomCell) && data != null
                        && data.get(roomCell.getX() & 15, roomCell.getY(), roomCell.getZ() & 15) != null,
                "closing the opening must retain the established finite claim and saved override");
        require(service.sample(level, roomCell).orElseThrow().moles(GasType.OXYGEN) > 0.0,
                "reclosed room must retain imported oxygen");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_door_toggle", timeoutTicks = 100)
    public static void closedDoorToggleAutomaticallyOpensAndClosesExteriorRoute(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCoveredRoomWithSideGap(helper);
        BlockPos lowerLocal = new BlockPos(3, 1, 1);
        BlockPos upperLocal = new BlockPos(3, 2, 1);
        helper.setBlock(upperLocal, Blocks.STONE);
        helper.setBlock(lowerLocal, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.OPEN, false));
        helper.setBlock(upperLocal, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.OPEN, false));

        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos lower = helper.absolutePos(lowerLocal);
        BlockPos roomCell = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos gap = helper.absolutePos(lowerLocal);
        GasMixture closedRoom = classifyUntilSample(service, level, roomCell, 12);
        require(closedRoom.totalMoles() == 0.0,
                "closed door must initially enclose a finite vacuum room");
        require(isFiniteClaimed(level, roomCell),
                "closed room must have a finite ownership claim before the door is opened");
        require(service.addBreathableAir(level, roomCell, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "closed room must accept gas before opening the door");
        double gasBeforeOpening = roomMoles(service, level, helper);
        service.neighborNotified(level, lower); // Capture closed baseline after proving finite ownership.

        setDoorOpen(helper, lowerLocal, upperLocal, true);
        service.neighborNotified(level, lower);
        for (int pass = 1; pass <= 60; pass++)
            service.tick(level, (long) (pass + 1) * AtmosphereService.TICK_CADENCE);
        GasMixture afterOpening = service.sample(level, roomCell).orElseThrow(() ->
                new GameTestAssertException("previous finite room claim was lost when door opened at " + roomCell
                        + "; " + doorRoomDiagnostic(service, level, roomCell, gap, lower)));
        require(isFiniteClaimed(level, roomCell),
                "opening the door must retain the previously finite room ownership claim; "
                        + doorRoomDiagnostic(service, level, roomCell, gap, lower));
        require(service.sample(level, gap).isPresent(),
                "open door gap must be automatically classified exterior without sampling it to trigger work");
        double gasAfterOpening = roomMoles(service, level, helper);
        require(gasAfterOpening < gasBeforeOpening,
                "open door must vent the finite room; before=" + gasBeforeOpening + ", after=" + gasAfterOpening
                        + ", gapStatus=" + service.diagnosticCell(level, gap).status()
                        + ", timing=" + service.diagnosticTiming(level)
                        + ", boundary=" + service.boundaryGasLedger(level));
        require(service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0)
                        + service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0) > 0.0,
                "open door must record vented gas in the boundary ledger");

        setDoorOpen(helper, lowerLocal, upperLocal, false);
        service.neighborNotified(level, lower);
        require(service.sample(level, roomCell).isPresent(),
                "reclosing the door must preserve the room's established finite ownership");
        require(isFiniteClaimed(level, roomCell),
                "reclosing the door must not erase the previously established finite claim");

        setDoorOpen(helper, lowerLocal, upperLocal, true);
        service.neighborNotified(level, lower);
        double exportedBeforeReopenDose = service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0)
                + service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        require(service.addBreathableAir(level, roomCell, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "reopened finite room must accept a fresh gas dose");
        for (long i = 1; i <= 60; i++) service.tick(level, 14L * AtmosphereService.TICK_CADENCE + i);
        GasMixture reopenedGap = service.sample(level, gap).orElseThrow(() ->
                new GameTestAssertException("reopened door gap was not classified after service ticks at " + gap));
        require(reopenedGap.totalMoles() == 0.0,
                "reopening must automatically classify the door gap as ambient exterior after service ticks");
        double exportedAfterReopenDose = service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0)
                + service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        require(exportedAfterReopenDose > exportedBeforeReopenDose + 1.0e-6,
                "fresh gas dose must resume exporting through the automatically rediscovered open door; before="
                        + exportedBeforeReopenDose + ", after=" + exportedAfterReopenDose + ", room="
                        + roomMoles(service, level, helper) + ", boundary=" + service.boundaryGasLedger(level)
                        + ", timing=" + service.diagnosticTiming(level));
        helper.succeed();
    }

    private static String doorRoomDiagnostic(AtmosphereService service, ServerLevel level,
                                             BlockPos roomCell, BlockPos gap, BlockPos door) {
        AtmosphereService.DiagnosticTiming timing = service.diagnosticTiming(level);
        AtmosphereService.OwnershipDiagnostic ownership = service.diagnosticOwnership(level, roomCell);
        return "room=" + service.diagnosticCell(level, roomCell)
                + ", claimed=" + isFiniteClaimed(level, roomCell)
                + ", loaded=" + level.hasChunkAt(roomCell)
                + ", block=" + level.getBlockState(roomCell)
                + ", passable=" + AtmosphereTopology.isPassable(level, roomCell)
                + ", gap=" + service.diagnosticCell(level, gap).status()
                + ", doorPassable=" + AtmosphereTopology.isPassable(level, door)
                + ", exchange=" + AtmosphereTopology.canExchange(level, roomCell, gap)
                + ", cycle=" + service.atmosphereStage(level) + "/" + service.completedCycles(level)
                + ", timing=" + (timing == null ? "none" : timing.gameTime() + "/" + timing.committedKernel()
                        + "/" + timing.workStatus() + "/q" + timing.queued())
                + ", ownership=" + ownership.status() + "/" + ownership.reason();
    }

    private static void setDoorOpen(GameTestHelper helper, BlockPos lower, BlockPos upper, boolean open) {
        helper.setBlock(lower, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.OPEN, open));
        helper.setBlock(upper, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.OPEN, open));
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_ambient_door_gradient", timeoutTicks = 120)
    public static void breathableDoorReplenishesInteriorOxygenThroughFiniteRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCoveredRoomWithSideGap(helper);
        BlockPos lower = new BlockPos(3, 1, 1), upper = new BlockPos(3, 2, 1);
        helper.setBlock(upper, Blocks.STONE);
        setDoorOpen(helper, lower, upper, false);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of());
        BlockPos near = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos deep = helper.absolutePos(new BlockPos(1, 1, 2));
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                BlockPos cell = helper.absolutePos(new BlockPos(x, 1, z));
                classifyUntilSample(service, level, cell, 12);
                require(isFiniteClaimed(level, cell),
                        "each interior cell must belong to the sealed finite room at " + cell);
            }
        }
        double initialOxygen = service.sample(level, deep).orElseThrow().moles(GasType.OXYGEN);
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                BlockPos cell = helper.absolutePos(new BlockPos(x, 1, z));
                require(service.removeGasUpTo(level, cell, 1_000) > 0,
                        "each claimed interior cell must accept depletion at " + cell);
            }
        }
        double depletedOxygen = service.sample(level, deep).orElseThrow().moles(GasType.OXYGEN);
        require(depletedOxygen < initialOxygen, "deep finite interior must start depleted");
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                BlockPos cell = helper.absolutePos(new BlockPos(x, 1, z));
                require(service.sample(level, cell).orElseThrow().moles(GasType.OXYGEN) <= initialOxygen * 0.01,
                        "every finite interior oxygen reserve must be depleted before opening at " + cell);
            }
        }
        service.neighborNotified(level, helper.absolutePos(lower));
        setDoorOpen(helper, lower, upper, true);
        service.neighborNotified(level, helper.absolutePos(lower));
        for (int pass = 1; pass <= 12; pass++)
            service.tick(level, (long) pass * AtmosphereService.TICK_CADENCE);
        require(service.sample(level, helper.absolutePos(lower)).isPresent(), "doorway must classify exterior");
        double firstImport = service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0);
        require(firstImport < 0, "first post-open boundary processing must import oxygen");
        for (int pass = 13; pass <= 35; pass++) {
            if (pass >= 28) {
                GasMixture boundary = service.sample(level, near).orElseThrow();
                require(service.removeGasUpTo(level, near, boundary.totalMoles() * 0.5) > 0,
                        "repeated doorway depletion must keep an ambient gradient on pass " + pass);
            }
            service.tick(level, (long) pass * AtmosphereService.TICK_CADENCE);
        }
        require(service.sample(level, deep).orElseThrow().moles(GasType.OXYGEN) > depletedOxygen,
                "breathable exterior must replenish the deep cell after all finite neighbors were depleted");
        require(service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0) < 0,
                "inhaled ambient oxygen must publish a negative export ledger");
        require(service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0) < firstImport,
                "repeated post-open cycles must keep importing oxygen");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_tiny_gas_heater", timeoutTicks = 100)
    public static void tinyFiniteInventoryRejectsHeaterAndBreathableDoorRecovers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        requireIsolatedReactionFixture();
        buildCoveredRoomWithSideGap(helper);
        BlockPos lower = new BlockPos(3, 1, 1), upper = new BlockPos(3, 2, 1);
        helper.setBlock(upper, Blocks.STONE);
        setDoorOpen(helper, lower, upper, false);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of());
        BlockPos near = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos gap = helper.absolutePos(lower);
        classifyUntilSample(service, level, near, 12);
        require(isFiniteClaimed(level, near), "sealed room cell must have a finite claim");
        GasMixture before = service.sample(level, near).orElseThrow();
        require(service.removeGasUpTo(level, near, before.totalMoles()) > 0.0,
                "finite cell must accept explicit evacuation");
        require(service.addGas(level, near, GasType.OXYGEN, 1.0e-30, 293.15),
                "finite cell must accept a tiny gas inventory");
        GasMixture tiny = service.sample(level, near).orElseThrow();
        require(tiny.moles(GasType.OXYGEN) > 0.0 && tiny.totalMoles() < 1.0e-29,
                "tiny inventory must remain strictly finite");
        require(!service.addHeaterEnergy(level, near, 40_000.0),
                "heater must reject a sub-minimum finite gas inventory");
        GasMixture afterRejectedHeater = service.sample(level, near).orElseThrow();
        require(afterRejectedHeater.gasMoles().equals(tiny.gasMoles())
                        && afterRejectedHeater.temperatureKelvin() == tiny.temperatureKelvin()
                        && afterRejectedHeater.thermalEnergy() == tiny.thermalEnergy(),
                "rejected heater offer must leave species, temperature, and energy unchanged");

        // Deplete the other finite cells too, so the opening has an ambient gradient to restore.
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                BlockPos cell = helper.absolutePos(new BlockPos(x, 1, z));
                if (!cell.equals(near))
                    require(service.removeGasUpTo(level, cell, 1_000) > 0,
                            "each other finite room cell must accept depletion at " + cell);
            }
        }

        service.neighborNotified(level, helper.absolutePos(lower));
        setDoorOpen(helper, lower, upper, true);
        service.neighborNotified(level, helper.absolutePos(lower));
        for (int pass = 1; pass <= 60; pass++)
            service.tick(level, (long) pass * AtmosphereService.TICK_CADENCE);
        require(service.sample(level, gap).isPresent(), "open door must be verified as exterior ambient");
        double importedOxygen = service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0);
        double importedNitrogen = service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        double importedEnergy = service.boundaryEnergyLedger(level);
        require(importedOxygen < 0.0 && importedNitrogen < 0.0 && importedEnergy < 0.0,
                "heater-free ambient import must record negative oxygen, nitrogen, and energy exports");

        for (int pass = 1; pass <= 12; pass++) {
            service.addHeaterEnergy(level, near, 40_000.0);
            service.tick(level, (long) (pass + 4) * AtmosphereService.TICK_CADENCE);
            GasMixture mixture = service.sample(level, near).orElseThrow();
            require(Double.isFinite(mixture.temperatureKelvin())
                            && mixture.temperatureKelvin() <= 262_144.0,
                    "repeated due-step heater offers must not cause non-finite or runaway temperature");
            require(mixture.moles(GasType.OXYGEN) + mixture.moles(GasType.NITROGEN) > 0.0,
                    "heater cycling must retain real breathable gas");
        }
        GasMixture recovered = service.sample(level, near).orElseThrow();
        require(recovered.moles(GasType.OXYGEN) + recovered.moles(GasType.NITROGEN) > tiny.totalMoles(),
                "verified breathable exterior must replenish the finite cell above its tiny inventory");
        require(importedOxygen < 0.0 && importedNitrogen < 0.0 && importedEnergy < 0.0,
                "ambient import was measured before heater input");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_wall_opening", timeoutTicks = 100)
    public static void fullWallOpeningDrainsMoreThanOneBlockOpening(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildSealedRoom(helper, 0);
        buildSealedRoom(helper, 8);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos fullRoom = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos singleRoom = helper.absolutePos(new BlockPos(11, 1, 3));
        classifyUntilSample(service, level, fullRoom, 12);
        classifyUntilSample(service, level, singleRoom, 12);
        require(isFiniteClaimed(level, fullRoom) && isFiniteClaimed(level, singleRoom),
                "both comparison rooms must be finitely claimed before either wall is opened");
        require(service.addBreathableAir(level, fullRoom, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "full-opening room should accept gas before the comparison");
        require(service.addBreathableAir(level, singleRoom, INJECTED_MOLES, ROOM_TEMPERATURE_KELVIN),
                "single-opening room should accept gas before the comparison");
        double initialFullGas = sealedRoomMoles(service, level, helper, 0);
        double initialSingleGas = sealedRoomMoles(service, level, helper, 8);
        double initialGas = initialFullGas + initialSingleGas;
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

        for (int y = 1; y <= 2; y++) {
            for (int z = 2; z <= 4; z++) {
                service.topologyChanged(level, helper.absolutePos(new BlockPos(1, y, z)));
                if (y == 1 && z == 2) {
                    service.topologyChanged(level, helper.absolutePos(new BlockPos(9, y, z)));
                }
            }
        }
        for (long gameTime = 1; gameTime <= 96; gameTime++) service.tick(level, gameTime);
        double fullOpeningRemaining = sealedRoomMoles(service, level, helper, 0);
        double singleOpeningRemaining = sealedRoomMoles(service, level, helper, 8);
        require(initialFullGas - fullOpeningRemaining > 8 * Math.ulp((float) initialFullGas)
                        && initialSingleGas - singleOpeningRemaining > 8 * Math.ulp((float) initialSingleGas),
                "each opening geometry must produce finite gas export without claiming width-proportional flow; full="
                        + fullOpeningRemaining + ", one-block=" + singleOpeningRemaining);
        double exported = service.boundaryGasLedger(level).getOrDefault(GasType.OXYGEN, 0.0)
                + service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        require(Math.abs(fullOpeningRemaining + singleOpeningRemaining + exported - initialGas)
                        <= 8 * Math.ulp((float) initialGas),
                "comparison rooms must conserve their combined gas inventory through the boundary ledger; initial="
                        + initialGas + ", remaining=" + (fullOpeningRemaining + singleOpeningRemaining)
                        + ", exported=" + exported);
        require(exported > 0, "both opened rooms must publish finite boundary gas export");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "atmosphere_sky_exposure", timeoutTicks = 20)
    public static void skyExposedCellUsesAmbientAndRejectsInjection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of());
        // A fresh service does not imply a fresh world chunk: earlier batches can leave
        // chunk attachments behind. Load a remote fixture chunk outside the test boards.
        BlockPos candidate = helper.absolutePos(new BlockPos(4098, 100, 4098));
        LevelChunk chunk = (LevelChunk) level.getChunk(candidate);
        int firstFreeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, candidate.getX(), candidate.getZ());
        if (firstFreeY > candidate.getY()) {
            candidate = new BlockPos(candidate.getX(), firstFreeY + 1, candidate.getZ());
        }
        require(candidate.getY() < level.getMaxBuildHeight(), "fixture must have room for an open sky cell");
        require(level.getHeight(Heightmap.Types.MOTION_BLOCKING, candidate.getX(), candidate.getZ()) <= candidate.getY(),
                "selected cell must be verified above the motion-blocking heightmap");

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

        // The template may include a sky-blocking mask above its declared structure bounds.
        // Keep the room roof intact, but give this side-gap column direct access to sky so
        // unrelated edits elsewhere cannot be part of the exterior proof.
        ServerLevel level = helper.getLevel();
        BlockPos localGap = new BlockPos(3, 1, 1);
        BlockPos gap = helper.absolutePos(localGap);
        int firstFreeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, gap.getX(), gap.getZ());
        int maxLocalY = 8;
        int structureYOffset = gap.getY() - localGap.getY();
        for (int localY = localGap.getY() + 1;
             localY < firstFreeY - structureYOffset && localY <= maxLocalY;
             localY++) {
            helper.setBlock(new BlockPos(localGap.getX(), localY, localGap.getZ()), Blocks.AIR);
        }
        firstFreeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, gap.getX(), gap.getZ());
        require(firstFreeY <= gap.getY(),
                "side gap must have direct sky exposure after clearing its bounded column; gap=" + gap
                        + ", firstFreeY=" + firstFreeY + ", maximumLocalY=" + maxLocalY);
        require(AtmosphereTopology.isPassable(level, gap),
                "direct-sky side gap must be a passable gas cell at " + gap);
    }

    private static GasMixture classifyUntilSample(AtmosphereService service, ServerLevel level,
                                                   BlockPos pos, int maxPasses) {
        service.activateCellAndNeighbors(level, pos);
        int callbackLimit = Math.max(60, maxPasses * 5);
        for (int pass = 1; pass <= callbackLimit; pass++) {
            service.tick(level, pass);
            var sample = service.sample(level, pos);
            if (sample.isPresent()) return sample.get();
        }
        throw new GameTestAssertException("cell ownership did not classify within " + callbackLimit
                + " callbacks (" + callbackLimit * 15 / 20 + " stage admissions); timing="
                + service.diagnosticTiming(level));
    }

    private static double roomMoles(AtmosphereService service, ServerLevel level, GasType type,
                                    GameTestHelper helper) {
        double total = 0.0;
        for (int x = 2; x <= 4; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 4; z++) {
                    BlockPos cell = helper.absolutePos(new BlockPos(x, y, z));
                    total += service.sample(level, cell)
                            .orElseThrow(() -> new GameTestAssertException("room cell became unclassified at "
                                    + cell)).moles(type);
                }
            }
        }
        return total;
    }

    private static HotspotState actualHotspot(AtmosphereService service, ServerLevel level, BlockPos pos) {
        try {
            Field queuesField = AtmosphereService.class.getDeclaredField("reactionQueues");
            queuesField.setAccessible(true);
            Object queue = ((Map<?, ?>) queuesField.get(service)).get(level);
            if (queue == null) return null;
            Method hotspot = queue.getClass().getDeclaredMethod("hotspot", BlockPos.class);
            hotspot.setAccessible(true);
            return (HotspotState) hotspot.invoke(queue, pos);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("could not inspect finite hotspot metadata: " + failure);
        }
    }

    private static double roomSpeciesUlpBudget(AtmosphereService service, ServerLevel level, GameTestHelper helper,
                                                GasType type, double expectedTotal) {
        double budget = Math.ulp((float) expectedTotal);
        for (int x = 2; x <= 4; x++) for (int y = 1; y <= 2; y++) for (int z = 2; z <= 4; z++)
            budget += Math.ulp((float) service.sample(level, helper.absolutePos(new BlockPos(x, y, z)))
                    .orElseThrow().moles(type));
        return 8 * budget;
    }

    private static double coveredRoomMoles(AtmosphereService service, ServerLevel level, GasType type,
                                            GameTestHelper helper) {
        double total = 0.0;
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                BlockPos cell = helper.absolutePos(new BlockPos(x, 1, z));
                total += service.sample(level, cell)
                        .orElseThrow(() -> new GameTestAssertException("covered room cell became unclassified at "
                                + cell)).moles(type);
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
                BlockPos cell = helper.absolutePos(new BlockPos(x + offsetX, 1, z));
                total += service.sample(level, cell)
                        .orElseThrow(() -> new GameTestAssertException("room cell became unclassified at "
                                + cell)).totalMoles();
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
                    BlockPos cell = helper.absolutePos(new BlockPos(x + offsetX, y, z));
                    total += service.sample(level, cell)
                            .orElseThrow(() -> new GameTestAssertException("sealed comparison cell became "
                                    + "unclassified at " + cell))
                            .totalMoles();
                }
            }
        }
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
