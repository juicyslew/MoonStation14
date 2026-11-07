package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.AtmospherePrecisionBudget;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.HashSet;

/** End-to-end coverage of the explicitly-built fixture through normal server systems. */
@GameTestTags({"ventilation", "atmosphere", "power", "atmos-pipe-equalization-runtime"})
@PrefixGameTestTemplate(false)
public final class VentilationFixtureGameTests {
    private VentilationFixtureGameTests() { }
    private record RoomChunkSnapshot(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereChunkData data,
            int claims, java.util.Map<com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereChunkData.CellPosition,
            com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> cells) { }

    @GameTest(template = "atmos_large_empty", batch = "ventilation_fixture", timeoutTicks = 300, templateNamespace = "moonstation14")
    @GameTestTags({"ventilation", "atmosphere", "trusted-setup-activation"})
    public static void preparedTokenCannotActivatePhysicallyClosedButUnownedRooms(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        VentilationFixturePlan plan=VentilationFixturePlan.standard();
        BlockPos origin=freshUnownedOrigin(helper,plan);
        BlockPos max=origin.offset(VentilationFixturePlan.WIDTH-1,VentilationFixturePlan.HEIGHT-1,VentilationFixturePlan.DEPTH-1);
        Set<Long> newlyForced=new HashSet<>();
        for(int cx=(origin.getX()>>4)-1;cx<=(max.getX()>>4)+1;cx++)for(int cz=(origin.getZ()>>4)-1;cz<=(max.getZ()>>4)+1;cz++) {
            long key=ChunkPos.asLong(cx,cz);
            if(!level.getForcedChunks().contains(key)){level.setChunkForced(cx,cz,true);newlyForced.add(key);}
            level.getChunk(cx,cz);
        }
        try {
            require(VentilationFixtureService.build(level,origin).startsWith("Built finite ventilation fixture"),"fixture build for activation guard");
            var entry=VentilationFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            require(AtmosphereService.INSTANCE.trustedSetupRoomReady(level,plan.sourceCells().stream().map(origin::offset).toList(),null),
                    "closed unowned room admits authoring proof");
            require(VentilationFixtureSavedData.forMutation(level).markInitialized(origin,entry.scrubberUUID(),entry.ventUUID(),entry.fixtureUUID()),
                    "fixture can be given prepared token for strict activation check");
            var guardedEntry=VentilationFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            var roomCells=new java.util.ArrayList<BlockPos>();
            plan.sourceCells().forEach(p->roomCells.add(origin.offset(p)));
            plan.recipientCells().forEach(p->roomCells.add(origin.offset(p)));
            var beforeActivation=roomChunkSnapshot(level,roomCells);
            String activated=VentilationFixtureService.activateLaboratoryTransfer(level,origin);
            require(activated.contains("Source room is not fully finite/owned"),"prepared unowned room must refuse activation: "+activated);
            require(beforeActivation.equals(roomChunkSnapshot(level,roomCells))
                    &&guardedEntry.equals(VentilationFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null)),
                    "strict activation refusal preserves room ownership/gas and prepared saved identity");
            var scrub=(VentilationDeviceBlockEntity)level.getBlockEntity(origin.offset(plan.scrubber()));
            var vent=(VentilationDeviceBlockEntity)level.getBlockEntity(origin.offset(plan.vent()));
            require(scrub!=null&&vent!=null&&!scrub.config().settings().configuredEnabled()&&!vent.config().settings().configuredEnabled(),
                    "failed strict activation leaves both transfer devices disabled");
        } finally { unforce(level,newlyForced); }
        helper.succeed();
    }

    @GameTestTags("suite-stabilization")
    @GameTest(template = "atmos_large_empty", batch = "ventilation_fixture", timeoutTicks = 600, templateNamespace = "moonstation14")
    public static void builtFixtureInitializesAndReportsResolvedRuntime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService atmos = AtmosphereService.INSTANCE;
        VentilationFixturePlan plan = VentilationFixturePlan.standard();
        BlockPos origin = freshUnownedOrigin(helper, plan);
        BlockPos min = origin, max = origin.offset(VentilationFixturePlan.WIDTH - 1,
                VentilationFixturePlan.HEIGHT - 1, VentilationFixturePlan.DEPTH - 1);
        Set<Long> newlyForced = new HashSet<>();
        for (int cx = (min.getX() >> 4) - 1; cx <= (max.getX() >> 4) + 1; cx++)
            for (int cz = (min.getZ() >> 4) - 1; cz <= (max.getZ() >> 4) + 1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                if (!level.getForcedChunks().contains(key)) {
                    level.setChunkForced(cx, cz, true);
                    newlyForced.add(key);
                }
                level.getChunk(cx, cz);
            }
        try {
            for (int x = 0; x < VentilationFixturePlan.WIDTH; x++)
                for (int y = 0; y < VentilationFixturePlan.HEIGHT; y++)
                    for (int z = 0; z < VentilationFixturePlan.DEPTH; z++)
                        require(level.getBlockState(origin.offset(x, y, z)).isAir(),
                                "remote footprint must start empty at " + origin.offset(x, y, z));
            require(VentilationFixtureService.build(level, origin).startsWith("Built finite ventilation fixture"), "service builds real fixture");
            var sourceCells = plan.sourceCells().stream().map(origin::offset).toList();
            var unownedCell = sourceCells.getFirst();
            require(atmos.finiteRoomEndpointForTrustedSimulation(level, unownedCell).isEmpty(),
                    "fresh built source cell must not already be finite-owned");
            var sourceChunk = level.getChunkSource().getChunkNow(unownedCell.getX() >> 4, unownedCell.getZ() >> 4);
            require(sourceChunk != null, "source proof chunk loaded");
            var beforeData = sourceChunk.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.ATMOSPHERE_CHUNK.get());
            var beforeClaims = beforeData == null ? 0 : beforeData.claimCount();
            var beforeCells = beforeData == null ? java.util.Map.of() : beforeData.snapshot();
            boolean beforeDirty = sourceChunk.isUnsaved();
            var setupEntryBefore=VentilationFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            var fullProofCells=new java.util.ArrayList<BlockPos>(sourceCells);
            plan.recipientCells().stream().map(origin::offset).forEach(fullProofCells::add);
            var rejectionSnapshot=roomChunkSnapshot(level,fullProofCells);
            require(atmos.trustedSetupRoomReady(level, sourceCells, null), "closed unowned source room is trusted-setup ready");
            var afterData = sourceChunk.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.ATMOSPHERE_CHUNK.get());
            require(afterData == beforeData && (afterData == null || afterData.claimCount() == beforeClaims && afterData.snapshot().equals(beforeCells))
                    && sourceChunk.isUnsaved() == beforeDirty, "read-only room proof must not create attachment, claim, gas, or dirty chunk");
            require(sourceCells.stream().allMatch(p -> atmos.finiteRoomEndpointForTrustedSimulation(level, p).isEmpty()),
                    "successful trusted setup proof must not claim its cells");
            require(!atmos.trustedSetupRoomReady(level, java.util.List.of(), null), "empty trusted setup room rejected");
            require(!atmos.trustedSetupRoomReady(level, java.util.List.of(unownedCell, unownedCell), null), "duplicate trusted setup cells rejected");
            require(!atmos.trustedSetupRoomReady(level, java.util.Collections.nCopies(8193, unownedCell), null), "oversized trusted setup room rejected");
            require(!atmos.trustedSetupRoomReady(level, sourceCells.subList(1, sourceCells.size()), null), "incomplete supplied component rejected");
            BlockPos opening = plan.sourceCells().stream().map(origin::offset)
                    .flatMap(cell -> java.util.Arrays.stream(Direction.values()).map(cell::relative))
                    .filter(p -> level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.GLASS))
                    .findFirst().orElseThrow(() -> new GameTestAssertException("source room has no glass boundary"));
            var priorWall = level.getBlockState(opening);
            level.setBlock(opening, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            try { require(!atmos.trustedSetupRoomReady(level, sourceCells, null), "open loaded room rejected by trusted proof"); }
            finally { level.setBlock(opening, priorWall, 3); }
            require(rejectionSnapshot.equals(roomChunkSnapshot(level,fullProofCells)),
                    "all rejected proofs preserve attachment identity, claims, and gas after restoring the test wall");
            require(setupEntryBefore.equals(VentilationFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null)),
                    "rejected proofs preserve uninitialized fixture guard");
            helper.runAtTickTime(5, () -> initializeWhenReady(helper, level, origin, plan, 5, newlyForced));
        } catch (RuntimeException | Error failure) {
            unforce(level, newlyForced);
            throw failure;
        }
    }

    private static void initializeWhenReady(GameTestHelper helper, ServerLevel level, BlockPos origin,
                                             VentilationFixturePlan plan, int tick,
                                             Set<Long> newlyForced) {
        try {
            boolean roomsOwned = plan.sourceCells().stream().allMatch(p ->
                    AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, origin.offset(p)).isPresent())
                    && plan.recipientCells().stream().allMatch(p ->
                    AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, origin.offset(p)).isPresent());
            if (!roomsOwned) {
                if (tick >= 300) throw new GameTestAssertException("normal atmosphere loop did not establish finite ownership for every fixture room cell");
                helper.runAtTickTime(tick + 10, () -> initializeWhenReady(helper, level, origin, plan, tick + 10, newlyForced));
                return;
            }
            var liveScrubber = (VentilationDeviceBlockEntity) level.getBlockEntity(origin.offset(plan.scrubber()));
            var alarm = (AirAlarmBlockEntity) level.getBlockEntity(origin.offset(plan.alarm()));
            if (liveScrubber == null || alarm == null
                    || !PowerRuntime.ventilationMeter(level, liveScrubber).known()
                    || !PowerRuntime.airAlarmMeter(level, alarm).known()) {
                if (tick >= 300) throw new GameTestAssertException("live scrubber/alarm paid meter did not settle before one-shot fixture initialization; scrubber="
                        + (liveScrubber == null ? "missing" : PowerRuntime.ventilationMeter(level, liveScrubber))
                        + " alarm=" + (alarm == null ? "missing" : PowerRuntime.airAlarmMeter(level, alarm))
                        + " indexedDevices=" + PowerRuntime.indexedDeviceCount(level));
                helper.runAtTickTime(tick + 5, () -> initializeWhenReady(helper, level, origin, plan, tick + 5, newlyForced));
                return;
            }
            String initialized = VentilationFixtureService.initialize(level, origin);
            require(initialized.startsWith("Initialized 18 finite room cells") && initialized.contains("2 mol Plasma per cell"), "initialize service: " + initialized);
            require(VentilationFixtureService.initialize(level, origin).contains("already initialized"), "repeat initialization is guarded");
            String status = VentilationFixtureService.status(level, origin);
            require(status.contains("source 18/18 finite cells") && status.contains("recipient 18/18 finite cells"), "status reports measured finite rooms: " + status);
            require(status.contains("connected nodes Plasma=") && status.contains("concealed pipe=owner active Plasma="), "status reports actual pipe inventories: " + status);
            require(status.contains("alarm power=meter known delivered=") && status.contains("scrubber power=meter known delivered="), "status reports live delivered power: " + status);
            require(status.contains("scrubber enabled=true mode=FILTER") && status.contains("vent enabled=false"), "resolved device settings match fixture scenario");
            BlockPos scrubPos = origin.offset(plan.scrubber());
            var scrubber = (VentilationDeviceBlockEntity) level.getBlockEntity(scrubPos);
            require(scrubber != null && scrubber.config().settings().filteredGases().equals(Set.of(plan.testGas())), "scrubber filters only fixture test gas");
            var meter = PowerRuntime.ventilationMeter(level, scrubber);
            require(meter.known() && meter.deliveredWatts() >= scrubber.prototype().apcPowerReceiver().requestedWatts(),
                    "real scrubber meter is fully paid: " + meter);
            System.out.println("VENTILATION_FIXTURE_PASS initialized-and-status");
            GasTotals initial = totals(level, origin, plan);
            double initializedUlp = initial.sourceInventory().moleUlpSums().get(plan.testGas())
                    + Math.ulp((float) 36.0);
            require(Math.abs(initial.source() - 36.0) <= initializedUlp,
                    "initialized source Plasma inventory is 36 mol; actual=" + initial.source() + " budget=" + initializedUlp);
            require(com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility.hasOverlay(plan.testGas())
                    && com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility.alphaByte(plan.testGas(), plan.testGasMolesPerCell(), 1.0) > 0,
                    "initial source concentration has a visible gas overlay");
            var counters = AtmosphereService.INSTANCE.diagnosticRuntime(level);
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 10, () -> captureScrubbedGas(helper, level, origin, plan, newlyForced, initial, counters, 10));
        } catch (RuntimeException | Error failure) {
            unforce(level, newlyForced);
            throw failure;
        }
    }

    private record GasTotals(double source, double recipient, double pipe,
                             AtmospherePrecisionBudget.Inventory allInventory,
                             AtmospherePrecisionBudget.Inventory transportInventory,
                             AtmospherePrecisionBudget.Inventory sourcePipeInventory,
                             AtmospherePrecisionBudget.Inventory sourceInventory,
                             AtmospherePrecisionBudget.Inventory pipeInventory, String pipeDetail,
                             int pipeFacePairs, int pipeNodeCount) {
        double totalTestGas() { return source + recipient + pipe; }
    }

    private static GasTotals totals(ServerLevel level, BlockPos origin, VentilationFixturePlan plan) {
        require(plan.sourceCells().contains(plan.scrubber()) && plan.recipientCells().contains(plan.vent()),
                "fixture control volumes include the passable scrubber and vent cells");
        double source = roomGas(level, origin, plan.sourceCells(), plan.testGas());
        double recipient = roomGas(level, origin, plan.recipientCells(), plan.testGas());
        var sourceMixtures = roomMixtures(level, origin, plan.sourceCells());
        var recipientMixtures = roomMixtures(level, origin, plan.recipientCells());
        var sourceInventory = AtmospherePrecisionBudget.Inventory.capture(sourceMixtures);
        java.util.List<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> all = new java.util.ArrayList<>(sourceMixtures);
        all.addAll(recipientMixtures);
        BlockPos first = origin.offset(plan.pipes().stream().filter(p -> !p.concealed()).findFirst().orElseThrow().position());
        var component = LoadedPipeComponent.discover(level, first, VisualPipeBlockEntity.PRIMARY)
                .orElseThrow(() -> new GameTestAssertException("fixture pipe component unavailable during test-gas conservation check"));
        var inventory = com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level)
                .map(com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData::snapshot)
                .orElseThrow(() -> new GameTestAssertException("fixture pipe inventory unavailable during test-gas conservation check"));
        var representative = component.nodes().getFirst().key();
        var networkGas = inventory.network(representative).orElseThrow(
                () -> new GameTestAssertException("fixture pipe network unavailable during test-gas conservation check")).mixture();
        double pipe = networkGas.moles(plan.testGas());
        var pipeMixtures = component.nodes().stream().map(n -> inventory.nodeShare(n.key())).toList();
        StringBuilder pipeDetail = new StringBuilder();
        for (var node : component.nodes()) {
            if (pipeDetail.length() > 0) pipeDetail.append("; ");
            boolean visual = level.getBlockEntity(node.key().position()) instanceof VisualPipeBlockEntity;
            pipeDetail.append(node.key().position()).append("[").append(visual ? "visual" : "concealed")
                    .append(",volume=").append(node.volume()).append(",active=").append(node.entry().active())
                    .append(",version=").append(node.entry().version()).append(",owner=").append(node.incarnation())
                    .append(",Plasma=").append(inventory.nodeShare(node.key()).moles(plan.testGas()))
                    .append(",energy=").append(inventory.nodeShare(node.key()).thermalEnergy()).append(']');
        }
        all.addAll(pipeMixtures);
        var transport = new java.util.ArrayList<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture>(recipientMixtures);
        transport.addAll(pipeMixtures);
        var sourcePipe = new java.util.ArrayList<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture>(sourceMixtures);
        sourcePipe.addAll(pipeMixtures);
        return new GasTotals(source, recipient, pipe, AtmospherePrecisionBudget.Inventory.capture(all),
                AtmospherePrecisionBudget.Inventory.capture(transport), AtmospherePrecisionBudget.Inventory.capture(sourcePipe), sourceInventory,
                AtmospherePrecisionBudget.Inventory.capture(pipeMixtures), pipeDetail.toString(),
                facePairs(component.nodes().stream().map(node -> node.key().position()).toList()), component.nodes().size());
    }

    /** Selects a deterministic untouched slot instead of reusing stale test-owned SavedData/blocks. */
    private static BlockPos freshUnownedOrigin(GameTestHelper helper, VentilationFixturePlan plan) {
        ServerLevel level = helper.getLevel();
        var saved = VentilationFixtureSavedData.existing(level).orElse(null);
        if (saved != null && saved.count() >= VentilationFixtureSavedData.MAX_FIXTURES)
            throw new GameTestAssertException("no ventilation fixture ownership capacity remains for a fresh test footprint");
        BlockPos first = helper.absolutePos(new BlockPos(200, 20, 200));
        for (int slot = 0; slot < VentilationFixtureSavedData.MAX_FIXTURES; slot++) {
            BlockPos candidate = first.offset(slot * (VentilationFixturePlan.WIDTH + 4), 0, 0);
            if (saved != null && saved.entry(candidate).isPresent()) continue;
            int minChunkX = candidate.getX() >> 4, maxChunkX = (candidate.getX() + VentilationFixturePlan.WIDTH - 1) >> 4;
            int minChunkZ = candidate.getZ() >> 4, maxChunkZ = (candidate.getZ() + VentilationFixturePlan.DEPTH - 1) >> 4;
            for (int cx = minChunkX - 1; cx <= maxChunkX + 1; cx++)
                for (int cz = minChunkZ - 1; cz <= maxChunkZ + 1; cz++) level.getChunk(cx, cz);
            boolean clear = true;
            for (int x = 0; x < VentilationFixturePlan.WIDTH && clear; x++)
                for (int y = 0; y < VentilationFixturePlan.HEIGHT && clear; y++)
                    for (int z = 0; z < VentilationFixturePlan.DEPTH; z++) {
                        BlockPos pos = candidate.offset(x, y, z);
                        if (!level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()
                                || level.getBlockEntity(pos) != null) { clear = false; break; }
                    }
            if (clear) {
                var bounds = new net.minecraft.world.phys.AABB(candidate.getX(), candidate.getY(), candidate.getZ(),
                        candidate.getX() + VentilationFixturePlan.WIDTH, candidate.getY() + VentilationFixturePlan.HEIGHT,
                        candidate.getZ() + VentilationFixturePlan.DEPTH);
                if (level.getEntities((net.minecraft.world.entity.Entity) null, bounds,
                        net.minecraft.world.entity.Entity::isAlive).isEmpty()) return candidate;
            }
        }
        throw new GameTestAssertException("no deterministic empty unowned remote fixture slot is available");
    }

    private static java.util.Map<Long,RoomChunkSnapshot> roomChunkSnapshot(ServerLevel level,java.util.List<BlockPos> cells) {
        var chunks=new java.util.LinkedHashMap<Long,RoomChunkSnapshot>();
        for(BlockPos cell:cells) {
            int cx=cell.getX()>>4,cz=cell.getZ()>>4;long key=ChunkPos.asLong(cx,cz);
            if(chunks.containsKey(key))continue;
            var chunk=level.getChunkSource().getChunkNow(cx,cz);
            if(chunk==null)throw new GameTestAssertException("room snapshot chunk unloaded: "+cell);
            var data=chunk.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.ATMOSPHERE_CHUNK.get());
            chunks.put(key,new RoomChunkSnapshot(data,data==null?0:data.claimCount(),data==null?java.util.Map.of():data.snapshot()));
        }
        return java.util.Map.copyOf(chunks);
    }

    private static java.util.List<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> roomMixtures(
            ServerLevel level, BlockPos origin, java.util.List<BlockPos> cells) {
        java.util.List<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> result = new java.util.ArrayList<>();
        for (BlockPos relative : cells) {
            var reading = AtmosphereService.INSTANCE.readAtmosphere(level, origin.offset(relative))
                    .orElseThrow(() -> new GameTestAssertException("finite fixture cell unavailable: " + relative));
            require(reading.status() == com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE,
                    "fixture cell remains finite: " + relative);
            result.add(reading.mixture());
        }
        return result;
    }

    private static double roomGas(ServerLevel level, BlockPos origin, java.util.List<BlockPos> cells, GasType gas) {
        double total = 0;
        for (BlockPos relative : cells) {
            var reading = AtmosphereService.INSTANCE.readAtmosphere(level, origin.offset(relative))
                    .orElseThrow(() -> new GameTestAssertException("finite fixture room sample unavailable"));
            require(reading.status() == com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE,
                    "fixture room remains finite");
            total += reading.mixture().moles(gas);
        }
        return total;
    }

    private static void captureScrubbedGas(GameTestHelper helper, ServerLevel level, BlockPos origin, VentilationFixturePlan plan,
                                           Set<Long> newlyForced, GasTotals initial,
                                           com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereRuntimeTelemetry.Snapshot countersBefore,
                                           int elapsed) {
        try {
                GasTotals now = totals(level, origin, plan);
            if (now.source() < initial.source() - 1e-6 && now.pipe() > initial.pipe() + 1e-6) {
                var countersAfter = AtmosphereService.INSTANCE.diagnosticRuntime(level);
                VentilationDeviceBlockEntity scrubber = (VentilationDeviceBlockEntity) level.getBlockEntity(origin.offset(plan.scrubber()));
                double operations = AtmospherePrecisionBudget.aggregateOperations(
                        countersAfter.completedCycles() - countersBefore.completedCycles(), plan.sourceCells().size(),
                        now.pipeNodeCount(), elapsed, scrubber.prototype().deviceIntervalSeconds());
                require(AtmospherePrecisionBudget.conservedAndSignalExceedsBudget(
                                initial.sourcePipeInventory(), now.sourcePipeInventory(), plan.testGas(),
                                now.pipe() - initial.pipe(), operations),
                        "source-plus-pipe aggregate conservation/budget must prove captured packet; captured="
                                + (now.pipe() - initial.pipe()) + " operations=" + operations);
                double budget = initial.sourcePipeInventory().moleBudget(plan.testGas(), now.sourcePipeInventory(), operations);
                double capturedMoles = now.pipe() - initial.pipe();
                require(budget < capturedMoles, "source-plus-pipe precision budget must remain below measured captured gas; captured="
                        + capturedMoles + " budget=" + budget + " operations=" + operations);
                require(now.recipient() == 0.0, "disabled vent keeps recipient at zero test gas; recipient=" + now.recipient());
                var current = scrubber.config().settings();
                var disabled = new VentilationDeviceConfig.Settings(current.revision(), false, current.externalPressureBoundKPa(),
                        current.targetPressureChangeKPaPerSecond(), current.targetPressureKPa(), current.volumeRateM3PerSecond(),
                        current.scrubberMode(), current.filteredGases(), current.selectedLayer());
                require(scrubber.compareAndSetSettings(current.revision(), disabled), "disable scrubber through revision-checked device settings");
                System.out.println("VENTILATION_FIXTURE_PASS finite-scrub-capture");
                var disabledCounters = AtmosphereService.INSTANCE.diagnosticRuntime(level);
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 10, () -> verifyDisabledScrubberAndEnableVent(helper, level, origin, plan,
                        newlyForced, initial, now, disabledCounters));
                return;
            }
            if (elapsed >= 200) {
                var scrubber = (VentilationDeviceBlockEntity) level.getBlockEntity(origin.offset(plan.scrubber()));
                var sourcePos = origin.offset(plan.sourceCells().getFirst());
                var sourceEndpoint = AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, sourcePos);
                int localReach = scrubber == null || scrubber.prototype() == null ? 0
                        : scrubber.prototype().extensionCableReceiver().localReachBlocks();
                throw new GameTestAssertException("normal paid scrubber did not capture source test gas; source=" + now.source()
                        + " pipe=" + now.pipe() + " worldTick=" + level.getGameTime()
                        + " sampleTick=" + PowerRuntime.currentSampleTick(level)
                        + " meter=" + (scrubber == null ? "missing" : PowerRuntime.ventilationMeter(level, scrubber))
                        + " powerDiagnostic=" + (scrubber == null ? "missing" : PowerRuntime.ventilationMeterDiagnostic(level, scrubber))
                        + " connection=" + (scrubber == null ? "missing" : PowerRuntime.apcConnection(level, scrubber))
                        + " settings=" + (scrubber == null ? "missing" : scrubber.config().settings())
                        + " prototype=" + (scrubber == null ? "missing" : scrubber.prototype())
                        + " transfer={species=" + (scrubber == null ? "missing" : scrubber.transferObservation().speciesMoles())
                        + ",thermalJoules=" + (scrubber == null ? "missing" : scrubber.transferObservation().thermalJoules()) + "}"
                        + " sourceEndpoint={present=" + sourceEndpoint.isPresent()
                        + (sourceEndpoint.isPresent() ? ",endpoint=" + sourceEndpoint.get() : "") + "}"
                        + " sourceMixture=" + AtmosphereService.INSTANCE.readAtmosphere(level, sourcePos)
                        + " pipeBackport=" + now.pipeDetail()
                        + " receiverGraph=" + PowerGraphService.diagnosticSnapshot(level,
                                scrubber == null ? origin.offset(plan.scrubber()) : scrubber.getBlockPos(), localReach,
                                com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC)
                        + " graph=" + PowerGraphService.diagnosticSnapshot(level));
            }
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 10, () -> captureScrubbedGas(helper, level, origin, plan,
                    newlyForced, initial, countersBefore, elapsed + 10));
        } catch (RuntimeException | Error failure) { unforce(level, newlyForced); throw failure; }
    }

    private static void verifyDisabledScrubberAndEnableVent(GameTestHelper helper, ServerLevel level, BlockPos origin,
            VentilationFixturePlan plan, Set<Long> newlyForced, GasTotals initial, GasTotals captured,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereRuntimeTelemetry.Snapshot countersBefore) {
        try {
            GasTotals now = totals(level, origin, plan);
            var countersAfter = AtmosphereService.INSTANCE.diagnosticRuntime(level);
            double operations = AtmospherePrecisionBudget.operations(
                    countersAfter.completedCycles() - countersBefore.completedCycles(), facePairs(plan.sourceCells()), 0);
            requireConserved(captured.sourceInventory(), now.sourceInventory(), operations,
                    "disabled scrubber source finite component");
            double pipeOperations = AtmospherePrecisionBudget.operations(
                    countersAfter.completedCycles() - countersBefore.completedCycles(), now.pipeFacePairs(), 0);
            requireConserved(captured.pipeInventory(), now.pipeInventory(), pipeOperations,
                    "disabled scrubber closed pipe component");
            double disabledBudget = captured.pipeInventory().moleBudget(plan.testGas(), now.pipeInventory(), pipeOperations);
            double capturedPacket = captured.pipe() - initial.pipe();
            require(disabledBudget < capturedPacket, "disabled-pipe precision budget below prior captured packet; budget="
                    + disabledBudget + " capturedPacket=" + capturedPacket + " operations=" + pipeOperations
                    + " localPipeFacePairs=" + now.pipeFacePairs());
            VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(origin.offset(plan.vent()));
            var current = vent.config().settings();
            var enabled = new VentilationDeviceConfig.Settings(current.revision(), true, current.externalPressureBoundKPa(),
                    current.targetPressureChangeKPaPerSecond(), current.targetPressureKPa(), current.volumeRateM3PerSecond(),
                    current.scrubberMode(), current.filteredGases(), current.selectedLayer());
            if (!current.configuredEnabled()) require(vent.compareAndSetSettings(current.revision(), enabled), "enable vent through revision-checked device settings");
            GasTotals releaseStart = totals(level, origin, plan);
            var releaseCounters = AtmosphereService.INSTANCE.diagnosticRuntime(level);
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 10, () -> pollVentRelease(helper, level, origin, plan, newlyForced,
                    releaseStart, releaseCounters, 10));
        } catch (RuntimeException | Error failure) { unforce(level, newlyForced); throw failure; }
    }

    private static void pollVentRelease(GameTestHelper helper, ServerLevel level, BlockPos origin,
            VentilationFixturePlan plan, Set<Long> newlyForced, GasTotals releaseStart,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereRuntimeTelemetry.Snapshot countersBefore,
            int elapsed) {
        try {
            GasTotals now = totals(level, origin, plan);
            var vent = (VentilationDeviceBlockEntity) level.getBlockEntity(origin.offset(plan.vent()));
            var countersAfter = AtmosphereService.INSTANCE.diagnosticRuntime(level);
            long cycles = countersAfter.completedCycles() - countersBefore.completedCycles();
            double operations = AtmospherePrecisionBudget.operations(cycles, fixtureFacePairs(plan),
                    Math.max(1, (elapsed + PowerRuntime.TICKS_PER_SOLVE - 1) / PowerRuntime.TICKS_PER_SOLVE));
            if (now.recipient() > releaseStart.recipient() && now.pipe() < releaseStart.pipe()) {
                requireConserved(releaseStart.allInventory(), now.allInventory(), operations, "vent release phase");
                double budget = releaseStart.transportInventory().moleBudget(
                        plan.testGas(), now.transportInventory(), operations);
                double released = releaseStart.pipe() - now.pipe();
                double received = now.recipient() - releaseStart.recipient();
                require(budget < released, "local precision budget below measured vent release; released=" + released
                        + " budget=" + budget + " operations=" + operations);
                require(Math.abs(released - received) <= budget,
                        "packet control-volume transfer conserves released Plasma; pipeLoss=" + released
                                + " recipientGain=" + received + " budget=" + budget + " operations=" + operations);
                requireConserved(releaseStart.transportInventory(), now.transportInventory(), operations,
                        "vent packet control volume");
                requireConserved(releaseStart.sourceInventory(), now.sourceInventory(), operations,
                        "scrubber remains disabled during vent release");
                System.out.println("VENTILATION_FIXTURE_PASS finite-vent-release");
                require(VentilationFixtureService.initialize(level, origin).contains("already initialized"), "gas initialization remains one-shot after transfer");
                unforce(level, newlyForced);
                helper.succeed(); return;
            }
            if (elapsed >= 200) throw new GameTestAssertException("normal paid vent did not release captured gas before deadline; elapsed="
                    + elapsed + " pipePlasma=" + now.pipe() + " recipientPlasma=" + now.recipient()
                    + " releaseStartPipe=" + releaseStart.pipe() + " releaseStartRecipient=" + releaseStart.recipient()
                    + " meter=" + (vent == null ? "missing" : PowerRuntime.ventilationMeter(level, vent))
                    + " topologyReady=" + PowerGraphService.topologyReady(level) + " topologyGeneration="
                    + PowerGraphService.topologyGeneration(level) + " indexedDevices=" + PowerRuntime.indexedDeviceCount(level)
                    + " settingsValid=" + (vent != null && vent.settingsValid())
                    + " settings=" + (vent == null ? "missing" : vent.config().settings())
                    + " roomFiniteEndpoint=" + AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,
                            origin.offset(plan.vent().relative(Direction.SOUTH))).isPresent()
                    + " fixtureStatus=" + VentilationFixtureService.status(level, origin)
                    + " pipeNodes=" + now.pipeDetail()
                    + " energyDelta=" + (now.allInventory().energy() - releaseStart.allInventory().energy())
                    + " operations=" + operations);
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 10, () -> pollVentRelease(helper, level, origin, plan, newlyForced,
                    releaseStart, countersBefore, elapsed + 10));
        } catch (RuntimeException | Error failure) { unforce(level, newlyForced); throw failure; }
    }

    private static void unforce(ServerLevel level, Set<Long> chunks) {
        for (long key : chunks) {
            ChunkPos pos = new ChunkPos(key);
            level.setChunkForced(pos.x, pos.z, false);
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static void requireConserved(AtmospherePrecisionBudget.Inventory before,
            AtmospherePrecisionBudget.Inventory after, double operations, String phase) {
        for (GasType gas : GasType.values()) {
            double delta = after.moles(gas) - before.moles(gas);
            double budget = before.moleBudget(gas, after, operations);
            require(Math.abs(delta) <= budget, phase + " " + gas + " delta=" + delta + " budget=" + budget
                    + " operations=" + operations + " before=" + before.moles(gas) + " after=" + after.moles(gas));
        }
        double delta = after.energy() - before.energy();
        double budget = before.energyBudget(after, operations);
        require(Math.abs(delta) <= budget, phase + " energy delta=" + delta + " budget=" + budget
                + " operations=" + operations);
    }

    private static int fixtureFacePairs(VentilationFixturePlan plan) {
        java.util.List<BlockPos> cells = new java.util.ArrayList<>(plan.sourceCells());
        cells.addAll(plan.recipientCells());
        return facePairs(cells);
    }

    private static int facePairs(java.util.List<BlockPos> cells) {
        int pairs = 0;
        for (int i = 0; i < cells.size(); i++) for (int j = i + 1; j < cells.size(); j++) {
            BlockPos a = cells.get(i), b = cells.get(j);
            if (Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY())
                    + Math.abs(a.getZ() - b.getZ()) == 1) pairs++;
        }
        return pairs;
    }
}
