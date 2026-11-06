package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.*;

/** Passive ordinary cargo entity-section unload/reload diagnostic; not a flight or player-lifecycle test. */
@GameTestTags({"shuttle-cargo-chunk-persistence", "shuttle"})
@PrefixGameTestTemplate(false)
public final class SableCargoChunkPersistenceGameTests {
    private static final int SCENE_SIZE = 32, PASSIVE_TICKS = 380, ENTITY_WAIT = 200;
    static final int NORMAL_SABLE_SUBSTEPS = 6;
    private static final String LOG = "[SABLE_CARGO_CHUNK_DIAGNOSTIC] ";
    private SableCargoChunkPersistenceGameTests() { }

    /** Narrow lifecycle composition seam; implementations must keep diagnostics non-loading. */
    public interface OfflineBodyWitness {
        default boolean flightMode() { return false; }
        /** Normal fixtures use the canonical SDK default; specialized diagnostics may override it. */
        default int expectedSubsteps() { return NORMAL_SABLE_SUBSTEPS; }
        /** Elapsed ticks after confirmed natural unload; this does not imply off-chunk simulation. */
        default int unloadedQuietTicks() { return 20; }
        /** Native upward launch speed in m/s; this is fixture input, not a production setting. */
        default double launchSpeed() { return 20; }
        default void onUnloadedFrame(SableFixturePlot plot, int elapsedTicks) { }
        default void onReacquiredFrame(SableFixturePlot plot) { }
        /** Non-loading sample while dependencies settle; entries stay UUID-ordered and may be null until loaded. */
        default void onReloadObservation(SableFixturePlot plot, List<Entity> cargo) { }
        void awaitOffline(Runnable ready);
        Entity oldBody();
        String diagnostic();
        void reconnectAndVerify(SableFixturePlot reloaded, Runnable passed);
    }

    /** Runs the shared cargo unload/reload phase against an ordinary authority fixture. */
    public static void runWithOfflineBody(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                          OfflineBodyWitness witness) {
        createCargo(helper, fixture.level(), fixture.scene(), fixture.plot(), fixture.cabin().origin(), witness);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 1500)
    public static void ordinaryCargoSurvivesNormalWorldChunkUnloadReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = helper.absolutePos(new BlockPos(1500, 300, 700));
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, site, SCENE_SIZE, 10, SCENE_SIZE);
        SableFixtureScene.awaitEmpty(helper, scene, 100, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(site.getX() + 8.5, site.getY(), site.getZ() + 8.5), plot -> {
                        BlockPos storage = plot.storageOrigin();
                        plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                                ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                        ShuttleCabinFixture.buildHull(level, storage);
                        return storage;
                    });
            SableFixturePlot plot = authored.plot();
            BlockPos storage = authored.result();
            SableFixtureScene.trackPlot(helper, plot);
            SableFixtureScene.requireStoneSupportUnderProjectedFootprint(level, scene, plot,
                    storage, ShuttleCabinFixture.WIDTH, ShuttleCabinFixture.DEPTH);
            SableFixtureScene.awaitStationaryFrame(helper, plot, 100, () ->
                     BoundedGameTestWait.safeDelay(helper, 10, () -> createCargo(helper, level, scene, plot, storage, null)));
        });
    }

    private static void createCargo(GameTestHelper helper, ServerLevel level, SableFixtureScene.Region scene,
                                    SableFixturePlot plot, BlockPos storage, OfflineBodyWitness witness) {
        var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        var roots = new ArrayList<SableOwnedBodyCarryGameTests.RegisteredCargo>();
        CargoCleanup cleanup = new CargoCleanup(level);
        cleanup.register(helper);
        for (int i = 0; i < 3; i++) {
            float units = 5 + i;
            ItemStack stack = new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
            long amount = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(units);
            stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                    com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(java.util.Map.of(water, amount)));
            Vec3 feet = SableOwnedBodyCarryGameTests.deckFeetAt(plot, storage, 2 + i * 1.5, 3);
            var registered = SableOwnedBodyCarryGameTests.registerCargoRoot(level, feet, stack, "chunk-unload-jug-");
            roots.add(registered);
            cleanup.acceptInitial(registered.entity());
        }
        if (shouldObservePersistence(witness != null, witness != null && witness.flightMode()))
            cleanup.watchPersistence(roots.stream().map(r -> r.entity().getUUID()).toList());
        Runnable snapshotAndRelease = () -> awaitReady(helper, level, plot, roots, scene,
                witness != null && witness.flightMode(), 120, 0, () -> {
            if (witness != null && witness.flightMode()) {
                var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel());
                var start = new Vector3d(plot.subLevel().logicalPose().position());
                handle.addLinearAndAngularVelocity(new Vector3d(0, witness.launchSpeed(), 0), new Vector3d());
                awaitFlight(helper, plot, witness, start, 20, () -> witness.awaitOffline(
                        () -> releaseReady(helper, level, plot, roots, scene, witness, cleanup)));
            } else releaseReady(helper, level, plot, roots, scene, witness, cleanup);
        });
        if (witness == null || witness.flightMode()) snapshotAndRelease.run(); else witness.awaitOffline(snapshotAndRelease);
    }

    private static void releaseReady(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
                                     List<SableOwnedBodyCarryGameTests.RegisteredCargo> roots,
                                     SableFixtureScene.Region scene, OfflineBodyWitness witness, CargoCleanup cleanup) {
            List<CargoSnapshot> snapshots = snapshot(level, plot, roots);
            if (witness != null && witness.flightMode()) logNaturalEligibility(level, plot, roots, "RELEASE");
            if (witness != null && witness.flightMode()) logFlightCargo("BEFORE", snapshots, null);
            List<BlockPos> worldChunks = worldWitnesses(plot, snapshots, witness);
            int substeps = SubLevelPhysicsSystem.require(level).getConfig().substepsPerTick;
            requireExpectedSubsteps(substeps, expectedSubsteps(witness));
            String before = describe(level, plot, snapshots, worldChunks, scene, substeps);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "PREPARED {}", before);
            if (!ShuttleSimulationResidency.forLevel(level).release(plot.subLevel()))
                throw new GameTestAssertException("could not explicitly release operational frame residency before natural-unload proof");
            scene.tickets.close();
            level.getChunkSource().save(true);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "RELEASE tick={} {}", helper.getTick(), before);
            long releaseTick=helper.getTick();
            BoundedGameTestWait.safeDelay(helper, 1, () -> observeUnload(helper, level, plot, snapshots,
                    worldChunks, before, scene, substeps, cleanup, witness, releaseTick, 0));
    }

    static int expectedSubsteps(OfflineBodyWitness witness) {
        return witness == null ? NORMAL_SABLE_SUBSTEPS : witness.expectedSubsteps();
    }

    static void requireExpectedSubsteps(int actual, int expected) {
        if (actual != expected) throw new GameTestAssertException("effective Sable substeps changed: expected="
                + expected + " actual=" + actual);
    }

    private static void awaitFlight(GameTestHelper helper, SableFixturePlot plot, OfflineBodyWitness witness,
                                    Vector3d start, int left, Runnable launched) {
        var velocity = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel()).getLinearVelocity(new Vector3d());
        var position = plot.subLevel().logicalPose().position();
        if (position.y - start.y > .25 && velocity.y > 1) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "FLIGHT_STIMULUS tick={} displacementY={} nativeLinear={} launchSpeed={} expectedSubsteps={}",
                    helper.getTick(), position.y - start.y, velocity, witness.launchSpeed(), witness.expectedSubsteps());
            launched.run(); return;
        }
        if (left <= 0) throw new GameTestAssertException("flight premise FAILED after one native launch: displacementY="
                + (position.y-start.y) + " nativeLinear=" + velocity);
        helper.runAfterDelay(1, () -> awaitFlight(helper, plot, witness, start, left - 1, launched));
    }

    private static void awaitReady(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            List<SableOwnedBodyCarryGameTests.RegisteredCargo> roots,
            SableFixtureScene.Region scene, boolean flightMode, int remaining, int stableSamples, Runnable ready) {
        var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel());
        boolean stable = handle.getLinearVelocity(new Vector3d()).lengthSquared() < 1.0e-5
                && handle.getAngularVelocity(new Vector3d()).lengthSquared() < 1.0e-6;
        boolean witnesses = true;
        var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level).snapshot();
        for (var root : roots) {
            Entity current = level.getEntity(root.entity().getUUID());
            var point = current == null ? null : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, current);
            var owner = ledger.locationOf(root.token()).orElse(null);
            boolean exactOwner = owner instanceof com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity w
                    && root.entity().getUUID().equals(w.entityId()) && root.world().equals(w.worldId());
            boolean tracked = point != null && plot.engineId().equals(point.frameId())
                    && ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension) current)
                    .sable$getTrackingSubLevel() == plot.subLevel();
            boolean live = current == root.entity() && current.isAlive() && !current.isRemoved();
            boolean settledCargo = current instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose
                    && loose.settled();
            witnesses &= live && tracked && exactOwner;
            stable &= live && settledCargo && current.getDeltaMovement().lengthSqr() < 1.0e-6;
        }
        int samples = stable && witnesses ? stableSamples + 1 : 0;
        if (samples >= 3) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "CARGO_READY tick={} frame={} cargo={}",
                    helper.getTick(), plot.engineId(), roots.stream().map(r -> r.entity().getUUID()).toList());
            if (flightMode) logNaturalEligibility(level, plot, roots, "CARGO_READY");
            ready.run();
        } else if (remaining <= 0) {
            List<UUID> ids = roots.stream().map(r -> r.entity().getUUID()).toList();
            failBounded("CARGO_READY_TIMEOUT", helper.getTick(), plot.engineId(), ids,
                    readinessEvidence(level, plot, roots, scene, stable, witnesses, stableSamples, remaining));
        } else helper.runAfterDelay(1, () -> awaitReady(helper, level, plot, roots, scene, flightMode,
                remaining - 1, samples, ready));
    }

    private static List<CargoSnapshot> snapshot(ServerLevel level, SableFixturePlot plot,
            List<SableOwnedBodyCarryGameTests.RegisteredCargo> roots) {
        var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level).snapshot();
        var result = new ArrayList<CargoSnapshot>();
        for (var root : roots) {
            var entity = root.entity();
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, entity);
            var owner = ledger.locationOf(root.token()).orElse(null);
            if (point == null || !plot.engineId().equals(point.frameId())
                    || ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension) entity)
                    .sable$getTrackingSubLevel() != plot.subLevel()
                    || !(owner instanceof com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity w)
                    || !entity.getUUID().equals(w.entityId()) || !root.world().equals(w.worldId()))
                throw new GameTestAssertException("cargo snapshot ownership/frame mismatch uuid=" + entity.getUUID()
                        + " point=" + point + " owner=" + owner);
            result.add(new CargoSnapshot(entity, entity.getUUID(), root.token(), root.world(), plot.storageOrigin(), entity.getItem().copy(),
                    point.storagePoint(), point.worldPoint(), new ChunkPos(BlockPos.containing(entity.position()))));
        }
        return List.copyOf(result);
    }

    private static List<BlockPos> worldWitnesses(SableFixturePlot plot, List<CargoSnapshot> cargo, OfflineBodyWitness witness) {
        var chunks = new LinkedHashSet<ChunkPos>();
        var bounds = plot.subLevel().boundingBox().toMojang();
        for (int x = net.minecraft.util.Mth.floor(bounds.minX); x <= net.minecraft.util.Mth.floor(Math.nextDown(bounds.maxX)); x++)
            for (int z = net.minecraft.util.Mth.floor(bounds.minZ); z <= net.minecraft.util.Mth.floor(Math.nextDown(bounds.maxZ)); z++)
                chunks.add(new ChunkPos(x >> 4, z >> 4));
        for (var s : cargo) chunks.add(s.worldChunk());
        if (witness != null && witness.oldBody() != null)
            chunks.add(new ChunkPos(BlockPos.containing(witness.oldBody().position())));
        return chunks.stream().map(c -> new BlockPos(c.getMinBlockX(), 0, c.getMinBlockZ())).toList();
    }

    private static void observeUnload(GameTestHelper helper, ServerLevel level, SableFixturePlot oldPlot,
            List<CargoSnapshot> expected, List<BlockPos> chunks, String before, SableFixtureScene.Region scene,
            int substeps, CargoCleanup cleanup, OfflineBodyWitness witness, long releaseTick, int ticks) {
        String destructive = destructiveReason(oldPlot, level, expected, witness);
        if (destructive != null) throw new GameTestAssertException("DESTRUCTIVE_REMOVAL tick=" + helper.getTick()
                    + " detail=" + destructive + " " + unloadState(level, oldPlot, expected, chunks, witness));
        UnloadState state = passiveState(level, oldPlot, expected, chunks, witness);
        if (state.complete()) {
            if (witness != null) witness.onUnloadedFrame(oldPlot, Math.toIntExact(helper.getTick()-releaseTick));
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "UNLOAD_CONFIRMED tick={} {}",
                    helper.getTick(), unloadState(level, oldPlot, expected, chunks, witness));
            cleanup.logPersistence("ACTUAL_ABSENCE");
            int quietTicks = witness == null ? 20 : witness.unloadedQuietTicks();
            verifyQuiet(helper, level, oldPlot, expected, chunks, before, scene, substeps, cleanup, witness,
                    quietTicks, quietTicks);
            return;
        }
        if (ticks >= PASSIVE_TICKS) {
            String kind = state.anyObserved() ? "PARTIAL_UNLOAD" : "RESIDENT_UNLOAD_NOT_OBSERVED owner=unknown";
            throw new GameTestAssertException(kind + " ticks=" + ticks + " "
                    + unloadState(level, oldPlot, expected, chunks, witness) + " before={" + before + "}");
        }
        helper.runAfterDelay(1, () -> observeUnload(helper, level, oldPlot, expected, chunks,
                before, scene, substeps, cleanup, witness, releaseTick, ticks + 1));
    }

    private static void verifyQuiet(GameTestHelper helper, ServerLevel level, SableFixturePlot oldPlot,
            List<CargoSnapshot> expected, List<BlockPos> chunks, String before, SableFixtureScene.Region scene,
            int substeps, CargoCleanup cleanup, OfflineBodyWitness witness, int left, int total) {
        String destructive = destructiveReason(oldPlot, level, expected, witness);
        if (destructive != null) throw new GameTestAssertException("DESTRUCTIVE_REMOVAL during quiet interval tick="
                + helper.getTick() + " detail=" + destructive + " " + unloadState(level, oldPlot, expected, chunks, witness));
        if (!passivelyUnloaded(level, oldPlot, expected, chunks, witness))
            throw new GameTestAssertException("UNLOAD_QUIET_PERIOD_INTERRUPTED tick=" + helper.getTick()
                    + " " + unloadState(level, oldPlot, expected, chunks, witness));
        if (left == total) com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "UNLOAD_QUIET_START tick={} requestedTicks={}", helper.getTick(), total);
        if (left > 1) helper.runAfterDelay(1, () -> verifyQuiet(helper, level, oldPlot, expected,
                chunks, before, scene, substeps, cleanup, witness, left - 1, total));
        else {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "QUIET_CONFIRMED tick={}", helper.getTick());
            requestReload(helper, level, oldPlot, expected, chunks, before, scene, substeps, cleanup, witness);
        }
    }

    private static void requestReload(GameTestHelper helper, ServerLevel level, SableFixturePlot oldPlot,
            List<CargoSnapshot> expected, List<BlockPos> chunks, String before, SableFixtureScene.Region scene, int substeps,
            CargoCleanup cleanup, OfflineBodyWitness witness) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : chunks) { minX = Math.min(minX, p.getX()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxZ = Math.max(maxZ, p.getZ()); }
        BlockPos min = new BlockPos(minX, 0, minZ), max = new BlockPos(maxX + 15, 0, maxZ + 15);
        FixtureChunkTickets.Lease lease = FixtureChunkTickets.around(helper, min, max, 1);
        cleanup.logPersistence("BEFORE_RELOAD");
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "RELOAD_REQUESTED tick={} bounds={}..{}", helper.getTick(), min, max);
        for (BlockPos p : chunks) level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        Adoption adoption = new Adoption(); adoption.cleanup=cleanup; adoption.witness=witness;
        waitReload(helper, level, oldPlot, expected, chunks, before, scene, substeps, lease, adoption, 0);
    }

    private static void waitReload(GameTestHelper helper, ServerLevel level, SableFixturePlot oldPlot,
            List<CargoSnapshot> expected, List<BlockPos> chunks, String before, SableFixtureScene.Region scene,
            int substeps, FixtureChunkTickets.Lease lease, Adoption adoption, int ticks) {
        var container = SubLevelContainer.getContainer(level);
        var raw = container.getSubLevel(oldPlot.engineId());
        if (raw != null && raw != oldPlot.subLevel()) {
            if (!(raw instanceof ServerSubLevel candidate))
                throw new GameTestAssertException("reloaded plot UUID has non-server owner; retaining fixture: " + raw);
            if (adoption.plot == null) {
                adoption.storage = expected.getFirst().storageOrigin();
                if (!candidate.getPlot().getCenterBlock().equals(adoption.storage))
                    throw new GameTestAssertException("reloaded plot storage origin mismatch expected=" + adoption.storage
                            + " actual=" + candidate.getPlot().getCenterBlock());
                adoption.plot = SableFixturePlot.wrapAssembled(level, candidate, adoption.storage,
                        ShuttleCabinFixture.WIDTH, ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                adoption.plot.validateAssembledFootprint(adoption.storage, ShuttleCabinFixture.WIDTH,
                        ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                SableFixtureScene.trackPlot(helper, adoption.plot);
                if (adoption.witness != null) adoption.witness.onReacquiredFrame(adoption.plot);
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "PLOT_REACQUIRED tick={} uuid={} oldRefChanged=true storage={}",
                        helper.getTick(), candidate.getUniqueId(), adoption.storage);
            } else if (candidate != adoption.plot.subLevel()) {
                throw new GameTestAssertException("reloaded owner replaced after adoption; expected exact current ref="
                        + adoption.plot.subLevel() + " actual=" + candidate);
            }
        }
        if (adoption.plot != null && container.getSubLevel(oldPlot.engineId()) != adoption.plot.subLevel())
            throw new GameTestAssertException("adopted reloaded plot is no longer exact live owner; "
                    + unloadState(level, oldPlot, expected, chunks, adoption.witness));
        var fresh = new ArrayList<Entity>();
        var reloadWitnesses = new ArrayList<String>();
        boolean cargoReady = true;
        var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level).snapshot();
        for (CargoSnapshot s : expected) {
            Entity entity = level.getEntity(s.uuid());
            fresh.add(entity);
            if (entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose)
                adoption.cleanup.acceptReloaded(s.uuid(), loose);
            var point = entity == null ? null
                    : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, entity);
            var tracking = entity instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement
                    ? movement.sable$getTrackingSubLevel() : null;
            var owner = ledger.locationOf(s.token()).orElse(null);
            boolean ready = entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose
                    && entity != s.oldEntity() && entity.isAlive() && !entity.isRemoved() && loose.settled()
                    && loose.sable$frameAnchorState().restoredFromFrame()
                    && ItemStack.matches(s.stack(), loose.getItem()) && adoption.plot != null
                    && point != null && adoption.plot.engineId().equals(point.frameId())
                    && tracking == adoption.plot.subLevel() && point.storagePoint().distanceTo(s.storagePoint()) <= .12
                    && owner instanceof com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity w
                    && s.uuid().equals(w.entityId()) && s.world().equals(w.worldId());
            cargoReady &= ready;
            reloadWitnesses.add("uuid=" + s.uuid() + " entity=" + entity + " settled="
                    + (entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose && loose.settled())
                    + " point=" + point + " tracking=" + tracking + " owner=" + owner + " stack="
                    + (entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose ? loose.getItem() : null)
                    + " ready=" + ready);
        }
        if (adoption.plot != null && adoption.witness != null)
            adoption.witness.onReloadObservation(adoption.plot, reloadObservationSnapshot(fresh));
        if (cargoReady && adoption.plot != null) {
            boolean stable = fresh.stream().allMatch(e -> e.getDeltaMovement().lengthSqr() < 1.0e-6);
            if (stable) {
                verifyReload(helper, level, oldPlot, expected, chunks, before, scene, substeps, lease, adoption, fresh);
                return;
            }
        }
        if (ticks >= ENTITY_WAIT) {
            long tick=helper.getTick();
            List<UUID> cargoUuids=expected.stream().map(CargoSnapshot::uuid).toList();
            String fullState=unloadState(level,oldPlot,expected,chunks,adoption.witness);
            String anchorState = reloadAnchorEvidence(level, expected, adoption.plot);
            adoption.cleanup.logPersistenceEvents("RELOAD_TIMEOUT");
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(LOG
                    +"RELOAD_TIMEOUT tick={} frame={} cargoUUIDs={} plot={} state={} witnesses={} anchorAdmission={} persistence={}",
                    tick,oldPlot.engineId(),cargoUuids,adoption.plot,fullState,reloadWitnesses,anchorState,adoption.cleanup.persistenceSummary());
            failBounded("RELOAD_TIMEOUT", tick, oldPlot.engineId(), cargoUuids,
                    "plot=" + adoption.plot + " state=" + fullState + " witnesses=" + reloadWitnesses
                            + " anchorAdmission=" + anchorState + " persistence=" + adoption.cleanup.persistenceSummary());
        }
        helper.runAfterDelay(1, () -> waitReload(helper, level, oldPlot, expected, chunks,
                before, scene, substeps, lease, adoption, ticks + 1));
    }

    /** Immutable positional snapshot: null entries mean the corresponding UUID has not loaded yet. */
    static List<Entity> reloadObservationSnapshot(List<Entity> fresh) {
        return Collections.unmodifiableList(new ArrayList<>(fresh));
    }

    /** Non-loading timeout snapshot of the existing restoration barrier and its admission dependencies. */
    private static String reloadAnchorEvidence(ServerLevel level, List<CargoSnapshot> expected, SableFixturePlot plot) {
        var container = SubLevelContainer.getContainer(level);
        var storage = expected.getFirst().storageOrigin();
        var storageChunk = level.getChunkSource().getChunkNow(storage.getX() >> 4, storage.getZ() >> 4);
        return expected.stream().map(s -> {
            Entity entity = level.getEntity(s.uuid());
            if (!(entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose))
                return "uuid=" + s.uuid() + " entity=" + entity;
            var state = loose.sable$frameAnchorState();
            var anchor = state.anchor();
            var position = BlockPos.containing(entity.position());
            var worldChunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            var projected = anchor == null || plot == null ? null
                    : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .projectActorPoint(level, anchor.frameId(), anchor.storagePoint());
            var projectedChunk = projected == null ? null : level.getChunkSource().getChunkNow(
                    BlockPos.containing(projected).getX() >> 4, BlockPos.containing(projected).getZ() >> 4);
            var tracking = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension) entity)
                    .sable$getTrackingSubLevel();
            return "uuid=" + s.uuid() + " status=" + state.status() + " pending=" + state.pending()
                    + " reason=" + state.reason() + " anchor=" + anchor + " entityLevelSame=" + (entity.level() == level)
                    + " removed=" + entity.isRemoved() + " passenger=" + entity.isPassenger()
                    + " pos=" + entity.position()
                    + " currentChunk=" + (worldChunk == null ? "absent" : worldChunk.getFullStatus())
                    + " currentEntityTicking=" + level.isPositionEntityTicking(position)
                    + " globalStorageChunk=" + (storageChunk == null ? "absent" : storageChunk.getFullStatus())
                    + " plotContainsStorage=" + (plot != null && plot.subLevel().getPlot().contains(new ChunkPos(storage)))
                    + " projected=" + projected + " projectedChunk=" + (projectedChunk == null ? "absent" : projectedChunk.getFullStatus())
                    + " projectedEntityTicking=" + (projected != null && level.isPositionEntityTicking(BlockPos.containing(projected)))
                    + " tracking=" + tracking + " resolvedFrame=" + (anchor == null || container == null ? null : container.getSubLevel(anchor.frameId()));
        }).toList().toString();
    }

    private static void verifyReload(GameTestHelper helper, ServerLevel level, SableFixturePlot oldPlot,
            List<CargoSnapshot> expected, List<BlockPos> chunks, String before, SableFixtureScene.Region scene,
            int substeps, FixtureChunkTickets.Lease lease, Adoption adoption, List<Entity> fresh) {
        var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level).snapshot();
        for (int i = 0; i < fresh.size(); i++) {
            var e = fresh.get(i);
            var s = expected.get(i);
            if (!(e instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose)
                    || e.getUUID() != s.uuid() && !e.getUUID().equals(s.uuid())
                    || e == s.oldEntity())
                throw new GameTestAssertException("reloaded entity identity/type mismatch expected=" + s.uuid() + " actual=" + e);
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, e);
            var tracked = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)e).sable$getTrackingSubLevel();
            var owner = ledger.locationOf(s.token()).orElse(null);
            if (point == null || !adoption.plot.engineId().equals(point.frameId()) || tracked != adoption.plot.subLevel()
                    || point.storagePoint().distanceTo(s.storagePoint()) > .12
                    || !loose.sable$frameAnchorState().restoredFromFrame()
                    || !(owner instanceof com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity w)
                    || !s.uuid().equals(w.entityId()) || !s.world().equals(w.worldId())
                    || !ItemStack.matches(s.stack(), loose.getItem()))
                throw new GameTestAssertException("reloaded cargo witness mismatch expected=" + s + " point=" + point
                        + " tracked=" + tracked + " ledger=" + owner + " actual=" + loose.getItem());
        }
        if (adoption.cleanup.persistence != null) assertNaturalAnchorPersistence(adoption, expected);
        if (adoption.witness != null && adoption.witness.flightMode()) logFlightCargo("AFTER", expected, fresh);
        var evidence = new ArrayList<String>();
        for (int i = 0; i < fresh.size(); i++) {
            var s = expected.get(i);
            var e = (com.juicyslew.moonstation14.entities.LooseItemEntity) fresh.get(i);
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, e);
            var tracked = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)e)
                    .sable$getTrackingSubLevel();
            var owner = ledger.locationOf(s.token()).orElse(null);
            evidence.add("uuid=" + s.uuid() + " oldRefChanged=" + (s.oldEntity() != e)
                    + " expectedStack=" + s.stack() + " actualStack=" + e.getItem()
                    + " expectedStorage=" + s.storagePoint() + " actualPoint=" + point
                    + " frame=" + tracked + " owner=" + owner);
        }
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "FINAL tick={} cargo={} frame={} chunks={} before={}",
                helper.getTick(), evidence, adoption.plot.engineId(), chunkStates(level, chunks), before);
        adoption.cleanup.logPersistence("SUCCESS");
        adoption.cleanup.logPersistenceEvents("SUCCESS");
        Runnable passed = () -> BoundedGameTestWait.safeDelay(helper, 2, helper::succeed);
        if (adoption.witness == null) passed.run();
        else adoption.witness.reconnectAndVerify(adoption.plot, passed);
    }

    private static void assertNaturalAnchorPersistence(Adoption adoption, List<CargoSnapshot> expected) {
        var snapshot = adoption.cleanup.persistence.snapshot();
        for (var cargo : expected) {
            boolean saved = snapshot.events().stream().anyMatch(e -> e.uuid().equals(cargo.uuid())
                    && e.phase() == com.juicyslew.moonstation14.entities.LooseMotionDiagnostics.PersistenceWatch.Phase.SAVE
                    && hasNaturalAnchorField(e.fields()));
            boolean read = snapshot.events().stream().anyMatch(e -> e.uuid().equals(cargo.uuid())
                    && e.phase() == com.juicyslew.moonstation14.entities.LooseMotionDiagnostics.PersistenceWatch.Phase.READ
                    && hasNaturalAnchorField(e.fields()));
            if (!saved || !read || !snapshot.coverageComplete())
                throw new GameTestAssertException("natural authoritative frame-anchor SAVE/READ missing uuid=" + cargo.uuid()
                        + " saved=" + saved + " read=" + read + " coverage=" + snapshot.coverageComplete());
        }
    }

    /** Matches the existing bounded field formatter's exact compound-type token, not KEY=MISSING. */
    static boolean hasNaturalAnchorField(String fields) {
        return fields != null && fields.contains(com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchor.KEY
                + "[" + net.minecraft.nbt.CompoundTag.TAG_COMPOUND + "]=");
    }

    private static void logFlightCargo(String phase, List<CargoSnapshot> expected, List<Entity> current) {
        var water=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        var evidence=new ArrayList<String>();
        for (int i=0;i<expected.size();i++) {
            CargoSnapshot s=expected.get(i);
            Entity entity=current==null?s.oldEntity():current.get(i);
            ItemStack stack=entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose?loose.getItem():s.stack();
            var reagents=stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            long cents=reagents==null?0:reagents.centContents().getOrDefault(water,0L);
            long wanted=500L+100L*i;
            if (cents!=wanted) throw new GameTestAssertException("flight cargo reagent cents mismatch phase="+phase
                    +" uuid="+s.uuid()+" wanted="+wanted+" actual="+cents);
            evidence.add("uuid="+s.uuid()+" token="+s.token()+" waterCents="+cents+" stack="+stack);
        }
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG+"FLIGHT_CARGO_{} {}",phase,evidence);
    }

    private static void logNaturalEligibility(ServerLevel level, SableFixturePlot plot,
            List<SableOwnedBodyCarryGameTests.RegisteredCargo> roots, String phase) {
        var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level).snapshot();
        var values = new ArrayList<String>();
        for (var root : roots) {
            Entity e = root.entity();
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, e);
            values.add("uuid=" + e.getUUID() + " shouldBeSaved=" + e.shouldBeSaved()
                    + " passenger=" + e.isPassenger() + " passengers=" + e.getPassengers().size()
                    + " removed=" + e.isRemoved() + " nonempty=" + !((com.juicyslew.moonstation14.entities.LooseItemEntity)e).getItem().isEmpty()
                    + " pos=" + e.position() + " worldChunk=" + new ChunkPos(BlockPos.containing(e.position()))
                    + " storagePoint=" + (point == null ? "unknown" : point.storagePoint())
                    + " frame=" + (point == null ? "unknown" : point.frameId())
                    + " owner=" + ledger.locationOf(root.token()).orElse(null));
        }
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "NATURAL_ELIGIBILITY_{} frame={} cargo={}", phase,
                plot.engineId(), values);
    }

    private static boolean passivelyUnloaded(ServerLevel level, SableFixturePlot oldPlot,
            List<CargoSnapshot> cargo, List<BlockPos> chunks, OfflineBodyWitness witness) {
        return passiveState(level, oldPlot, cargo, chunks, witness).complete();
    }
    private static UnloadState passiveState(ServerLevel level, SableFixturePlot plot, List<CargoSnapshot> cargo, List<BlockPos> chunks, OfflineBodyWitness witness) {
        boolean chunksLow = chunks.stream().allMatch(p -> {
            var c = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
            return c == null || !c.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL);
        });
        boolean cargoOut = cargo.stream().allMatch(s -> {
            Entity old = s.oldEntity();
            return old != null && old.isRemoved() && old.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK
                    && level.getEntity(s.uuid()) == null;
        });
        boolean plotOut = plot.subLevel().isRemoved() && SubLevelContainer.getContainer(level).getSubLevel(plot.engineId()) == null;
        boolean someChunkLow = chunks.stream().anyMatch(p -> {
            var c = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
            return c == null || !c.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL);
        });
        boolean someCargoUnloaded = cargo.stream().anyMatch(s -> s.oldEntity().isRemoved()
                && s.oldEntity().getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK);
        Entity body = witness == null ? null : witness.oldBody();
        boolean bodyOut = witness == null || body != null && body.isRemoved()
                && body.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK && level.getEntity(body.getUUID()) == null;
        boolean someBodyUnloaded = body != null && body.isRemoved()
                && body.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK;
        return new UnloadState(chunksLow, cargoOut, plotOut, chunksLow && cargoOut && plotOut && bodyOut,
                someChunkLow || someCargoUnloaded || plotOut || someBodyUnloaded);
    }
    private static String destructiveReason(SableFixturePlot plot, ServerLevel level, List<CargoSnapshot> cargo, OfflineBodyWitness witness) {
        for (CargoSnapshot s : cargo) {
            Entity old = s.oldEntity();
            if (old != null && old.getRemovalReason() != null
                    && old.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK)
                return "uuid=" + s.uuid() + " reason=" + old.getRemovalReason() + " old=" + old + " newLookup=" + level.getEntity(s.uuid());
        }
        Entity body = witness == null ? null : witness.oldBody();
        if (body != null && body.getRemovalReason() != null && body.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK)
            return "body uuid=" + body.getUUID() + " reason=" + body.getRemovalReason() + " lookup=" + level.getEntity(body.getUUID());
        return null;
    }
    private static String unloadState(ServerLevel level, SableFixturePlot plot, List<CargoSnapshot> cargo, List<BlockPos> chunks, OfflineBodyWitness witness) {
        return "plotUUID="+plot.engineId()+" oldPlotRemoved="+plot.subLevel().isRemoved()+" registry="
                +SubLevelContainer.getContainer(level).getSubLevel(plot.engineId())+" cargo="+cargo.stream().map(s->{
            Entity e=s.oldEntity(); return s.uuid()+" oldReason="+(e==null?"unknown":e.getRemovalReason())
                            +" oldRemoved="+(e!=null&&e.isRemoved())+" lookup="+level.getEntity(s.uuid());}).toList()
                +" chunks="+chunkStates(level,chunks) + " body=" + (witness == null ? "none" : bodyState(level, witness));
    }
    private static String bodyState(ServerLevel level, OfflineBodyWitness witness) {
        Entity body = witness.oldBody();
        return "uuid=" + (body == null ? "null" : body.getUUID()) + " worldChunk="
                + (body == null ? "unknown" : new ChunkPos(BlockPos.containing(body.position()))) + " removed="
                + (body != null && body.isRemoved()) + " reason=" + (body == null ? "unknown" : body.getRemovalReason())
                + " lookup=" + (body == null ? null : level.getEntity(body.getUUID())) + " witness=" + witness.diagnostic();
    }
    private static String chunkStates(ServerLevel level,List<BlockPos> chunks) {
        return chunks.stream().map(p->{var c=level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);
            return new ChunkPos(p)+"="+(c==null?"absent":c.getFullStatus());}).toList().toString();
    }
    private static String describe(ServerLevel level,SableFixturePlot plot,List<CargoSnapshot> cargo,List<BlockPos> chunks,
                                   SableFixtureScene.Region scene,int substeps) {
        return "tick="+level.getServer().getTickCount()+" substeps="+substeps+" frame="+plot.engineId()+" storage="+plot.storageOrigin()
                +" pose="+plot.subLevel().logicalPose()+" bounds="+plot.subLevel().boundingBox().toMojang()+" witnesses="+chunks
                +" plotStorageChunk="+new ChunkPos(plot.storageOrigin())+" plotStorageResident="
                +(level.getChunkSource().getChunkNow(plot.storageOrigin().getX()>>4,plot.storageOrigin().getZ()>>4)!=null)
                +" cargo="+cargo+" sceneLease="+scene.tickets.diagnostic(scene.origin);
    }
    private static String diagnostic(ServerLevel level,SableFixturePlot plot,List<SableOwnedBodyCarryGameTests.RegisteredCargo> roots,
                                     SableFixtureScene.Region scene) {
        return "frame="+plot.engineId()+" cargo="+roots.stream().map(r->r.entity().getUUID()+" reason="+r.entity().getRemovalReason()
                +" current="+level.getEntity(r.entity().getUUID())+" point="
                +com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level,r.entity())).toList()
                +" sceneLease="+scene.tickets.diagnostic(scene.origin);
    }

    /** Keep the expensive scoped field observer exclusive to the flight diagnosis fixture. */
    static boolean shouldObservePersistence(boolean hasWitness, boolean flightMode) { return hasWitness && flightMode; }

    private static String readinessEvidence(ServerLevel level, SableFixturePlot plot,
            List<SableOwnedBodyCarryGameTests.RegisteredCargo> roots, SableFixtureScene.Region scene,
            boolean stable, boolean ownershipWitness, int stableSamples, int remaining) {
        return "frame=" + plot.engineId() + " stable=" + stable + " ownershipWitness=" + ownershipWitness
                + " stableSamples=" + stableSamples + " remaining=" + remaining + " cargo=" + roots.stream().map(root -> {
                    Entity e = level.getEntity(root.entity().getUUID());
                    var point = e == null ? null : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, e);
                    var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level)
                            .snapshot().locationOf(root.token()).orElse(null);
                    return "uuid=" + root.entity().getUUID() + " entity=" + e + " point=" + point
                            + " nativeOwner=" + (e instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension m
                                    ? m.sable$getTrackingSubLevel() : null)
                            + " velocity=" + (e == null ? null : e.getDeltaMovement())
                            + " settled=" + (e instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose && loose.settled())
                            + " owner=" + ledger;
                }).toList() + " sceneLease=" + scene.tickets.diagnostic(scene.origin);
    }

    private static void failBounded(String phase, long tick, UUID frame, List<UUID> ids, String evidence) {
        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(LOG
                + "{} FULL tick={} frame={} cargoUUIDs={} evidence={}", phase, tick, frame, ids, evidence);
        throw new GameTestAssertException("SABLE_CARGO_CHUNK_DIAGNOSTIC " + phase + " tick=" + tick
                + " frame=" + frame + " cargoUUIDs=" + ids + "; full witness is in the SABLE_CARGO_CHUNK_DIAGNOSTIC log");
    }

    private record CargoSnapshot(Entity oldEntity, UUID uuid, com.juicyslew.moonstation14.ms14.hands.ItemToken token, UUID world,
                                 BlockPos storageOrigin,
                                 ItemStack stack, Vec3 storagePoint, Vec3 worldPoint, ChunkPos worldChunk) { }
    private record UnloadState(boolean chunksLow, boolean cargoOut, boolean plotOut, boolean complete, boolean anyObserved) { }
    private static final class Adoption {
        SableFixturePlot plot; BlockPos storage; CargoCleanup cleanup; OfflineBodyWitness witness;
    }
    private static final class CargoCleanup {
        private final ServerLevel level; private final Map<UUID,Entity> refs = new LinkedHashMap<>();
        private com.juicyslew.moonstation14.entities.LooseMotionDiagnostics.PersistenceWatch persistence;
        private boolean persistenceEventsLogged;
        CargoCleanup(ServerLevel level) { this.level=level; }
        void register(GameTestHelper helper) {
            helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) { }
                 private void clean() { if (persistence != null) persistence.close(); logPersistenceEvents("TERMINAL"); logPersistence("TERMINAL"); for (var e:refs.entrySet()) if (level.getEntity(e.getKey())==e.getValue()) e.getValue().discard(); }
                public void testPassed(GameTestInfo i,GameTestRunner r) { clean(); }
                public void testFailed(GameTestInfo i,GameTestRunner r) { clean(); }
                public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r) { clean(); }
            });
        }
        void acceptInitial(Entity e) { refs.put(e.getUUID(),e); }
        void watchPersistence(Collection<UUID> ids) {
            persistence = com.juicyslew.moonstation14.entities.LooseMotionDiagnostics.watchPersistence(level, ids);
        }
        String persistenceSummary() {
            if (persistence == null) return "not-registered";
            var snapshot = persistence.snapshot();
            return "counts=" + snapshot.counts() + " capturedEvents=" + snapshot.events().size() + " overflow=" + snapshot.overflow()
                    + " fieldCaptureIncomplete=" + snapshot.fieldCaptureIncomplete()
                    + " postJoinCoverageLost=" + snapshot.postJoinCoverageLost()
                    + " coverageReasons=" + snapshot.coverageReasons()
                    + " postJoinEnd=" + snapshot.postJoinEnd()
                    + " coverageComplete=" + snapshot.coverageComplete() + " closed=" + snapshot.closed();
        }
        void logPersistence(String phase) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "NATURAL_PERSISTENCE_{} {}", phase, persistenceSummary());
        }
        void logPersistenceEvents(String phase) {
            if (persistence == null || persistenceEventsLogged) return;
            persistenceEventsLogged = true;
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(LOG + "NATURAL_PERSISTENCE_EVENTS_{} {}",
                    phase, persistence.snapshot().events());
        }
        void acceptReloaded(UUID id, com.juicyslew.moonstation14.entities.LooseItemEntity e) {
            if (refs.containsKey(id)) refs.put(id,e);
        }
    }
}
