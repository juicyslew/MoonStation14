package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.server.level.ServerLevel;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;

@GameTestTags({"atmosphere"})
@PrefixGameTestTemplate(false)
public final class FiniteRoomGasEndpointGameTests {
    private static long syntheticGameTime;
    private static Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> previousVacuumDimensions;

    private FiniteRoomGasEndpointGameTests() { }

    @BeforeBatch(batch = "finite_room_endpoint")
    public static void setupBatch(ServerLevel level) {
        AtmosphereService service = AtmosphereService.INSTANCE;
        previousVacuumDimensions = service.vacuumDimensionsForTest();
        service.configureAtServerStart(Set.of(level.dimension()), service.spaceDimensionsForTest());
    }

    @AfterBatch(batch = "finite_room_endpoint")
    public static void teardownBatch(ServerLevel level) {
        AtmosphereService service = AtmosphereService.INSTANCE;
        service.configureAtServerStart(previousVacuumDimensions, service.spaceDimensionsForTest());
    }

@GameTest(template = "atmos_large_empty", batch = "finite_room_endpoint", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void trustedSetupReplacesOnlyLiveFiniteCellMixture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos room = buildClaimedVacuumRoom(helper, 1);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        GasMixture vacuum = service.sample(level, room).orElseThrow(() -> missing("claimed vacuum", room));
        LevelChunk roomChunk = (LevelChunk) level.getChunk(room);
        roomChunk.setUnsaved(false);
        require(service.initializeFiniteMixtureForTrustedSetup(level, room, vacuum),
                "already-vacuum trusted initialization succeeds");
        require(service.initializeFiniteMixtureForTrustedSetup(level, room, vacuum),
                "repeated already-vacuum initialization succeeds");
        var roomData = roomChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        require(roomData != null && roomData.get(room.getX() & 15, room.getY(), room.getZ() & 15) == null
                        && !roomChunk.isUnsaved(),
                "vacuum no-op retains sparse absence without dirtying the chunk");
        GasMixture authored = new GasMixture(Map.of(GasType.OXYGEN, 2.5, GasType.CARBON_DIOXIDE, 1.25), 415.0);
        require(service.initializeFiniteMixtureForTrustedSetup(level, room, authored), "trusted setup replaces vacuum");
        require(same(service.sample(level, room).orElseThrow(), authored),
                "complete composition and exact temperature replace the previous mixture");
        require(!same(vacuum, authored), "fixture starts from claimed vacuum");

        BlockPos exterior = helper.absolutePos(new BlockPos(1, 1, 2));
        GasMixture beforeRejected = service.sample(level, room).orElseThrow();
        require(!service.initializeFiniteMixtureForTrustedSetup(level, exterior, authored), "unclaimed exterior denied");
        helper.setBlock(new BlockPos(2, 5, 2), Blocks.AIR);
        require(!service.initializeFiniteMixtureForTrustedSetup(level, room, authored), "unsealed finite claim denied");
        require(same(service.sample(level, room).orElseThrow(), beforeRejected), "denials preserve stored mixture");
        helper.setBlock(new BlockPos(2, 5, 2), Blocks.STONE);

        require(!service.initializeFiniteMixtureForTrustedSetup(level, room.offset(16 * 128, 0, 16 * 128), authored),
                "unloaded cell denied");
        helper.succeed();
    }

@GameTest(template = "atmos_large_empty", batch = "finite_room_endpoint", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void sealed144CellEndpointAcceptsWithoutCreatingGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos room = buildClaimedVacuumRoom(helper, 6);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        requireVacuumRoomCells(level, room, 6, "claims do not seed gas or AIR overrides");
        var endpoint = service.finiteRoomEndpoint(level, room)
                .orElseThrow(() -> missing("144-cell sealed endpoint", room));
        require(endpoint.eligible() && endpoint.snapshot() != null && endpoint.snapshot().totalMoles() == 0,
                "all 144 claimed vacuum cells form one eligible finite room");
        requireVacuumRoomCells(level, room, 6, "endpoint proof must not create gas or change blocks");

        // The old claim remains persisted across a topology breach; the live proof must reject it.
        BlockPos door = new BlockPos(1, 1, 2);
        helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true));
        require(service.finiteRoomEndpoint(level, room).isEmpty(), "open doorway must deny stale finite claim");
        helper.setBlock(door, Blocks.STONE);
        require(service.finiteRoomEndpoint(level, room).isPresent(), "closing doorway restores enclosure");

        helper.setBlock(new BlockPos(2, 5, 2), Blocks.AIR);
        require(service.finiteRoomEndpoint(level, room).isEmpty(), "exterior roof breach must deny endpoint");
        requireVacuumRoomCells(level, room, 6, "denied proofs must not create gas");

        // A far chunk is explicitly verified absent: endpoint probing must not load it.
        BlockPos unloaded = room.offset(16 * 128, 0, 16 * 128);
        require(level.getChunkSource().getChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4,
                ChunkStatus.FULL, false) == null, "unloaded fixture chunk must remain absent");
        require(service.finiteRoomEndpoint(level, unloaded).isEmpty(), "unloaded endpoint must be denied");
        require(level.getChunkSource().getChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4,
                ChunkStatus.FULL, false) == null, "endpoint denial must not load chunks");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "finite_room_endpoint", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void closed168CellRoomExceedsSynchronousEndpointBound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos room = buildClaimedVacuumRoom(helper, 7);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        requireVacuumRoomCells(level, room, 7, "claims do not seed gas or AIR overrides");
        require(service.finiteRoomEndpoint(level, room).isEmpty(),
                "168 claimed enclosed cells cost 1176 probes and must exceed the 1024 cap");
        requireVacuumRoomCells(level, room, 7, "bounded proof must not seed gas");
        helper.succeed();
    }

    /** Inspect only the claimed room cells; the shared GameTest chunk may contain unrelated overrides. */
    private static void requireVacuumRoomCells(ServerLevel level, BlockPos room, int width, String message) {
        for (int x = 0; x < width; x++) for (int z = 0; z < 6; z++) for (int y = 0; y < 4; y++) {
            BlockPos pos = room.offset(x, y, z);
            var data = ((LevelChunk) level.getChunk(pos)).getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            int lx = pos.getX() & 15, lz = pos.getZ() & 15;
            require(data != null && data.isFiniteClaimed(lx, pos.getY(), lz)
                            && data.get(lx, pos.getY(), lz) == null && level.getBlockState(pos).is(Blocks.AIR),
                    message + " at " + pos);
        }
    }

    /** Claims already-persisted finite ownership without inserting an AIR block or gas override. */
    private static BlockPos buildClaimedVacuumRoom(GameTestHelper helper, int width) {
        for (int x = 1; x <= width + 2; x++) for (int z = 1; z <= 8; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
            for (int y = 1; y <= 4; y++) {
                boolean inside = x >= 2 && x <= width + 1 && z >= 2 && z <= 7;
                BlockPos local = new BlockPos(x, y, z);
                helper.setBlock(local, inside ? Blocks.AIR : Blocks.STONE);
                if (inside) {
                    BlockPos pos = helper.absolutePos(local);
                    LevelChunk chunk = (LevelChunk) helper.getLevel().getChunk(pos);
                    chunk.getData(ModDataAttachments.ATMOSPHERE_CHUNK.get())
                            .claimFinite(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
                    chunk.setUnsaved(true);
                }
            }
        }
        return helper.absolutePos(new BlockPos(2, 1, 2));
    }

    @GameTest(template = "atmos_large_empty", batch = "finite_room_endpoint", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void finiteRoomEndpointPublishesAndRollsBackRawTransfer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            for (int y = 1; y <= 2; y++)
                helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 2 ? Blocks.AIR : Blocks.STONE);
        }
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos room = helper.absolutePos(new BlockPos(2, 1, 2));
        classify(service, level, room);
        require(service.addGas(level, room, GasType.TRITIUM, 8, 300), "seed finite source gas");
        GasMixture original = service.sample(level, room).orElseThrow(() -> missing("initial room sample", room));
        PipeGasTransaction.Endpoint source = service.finiteRoomEndpoint(level, room)
                .orElseThrow(() -> missing("initial finite room endpoint", room));
        require(same(source.snapshot(), original), "endpoint starts from persisted room snapshot");

        MemoryEndpoint receiver = new MemoryEndpoint(new GasMixture(Map.of(GasType.OXYGEN, 1.0), 450));
        PipeGasTransaction.Plan plan = PipeGasTransaction.plan(source, receiver, 1, 1, 2, 10000,
                Set.of(GasType.TRITIUM)).orElseThrow(() -> missing("initial transfer plan", room));
        Map<GasType, Double> moles = new java.util.EnumMap<>(GasType.class);
        for (GasType gas : GasType.values()) moles.put(gas, source.snapshot().moles(gas) + receiver.gas.moles(gas));
        double energy = source.snapshot().thermalEnergy() + receiver.gas.thermalEnergy();
        PipeGasTransaction.commit(source, receiver, plan);
        GasMixture committed = service.sample(level, room).orElseThrow(() -> missing("committed room sample", room));
        for (GasType gas : GasType.values())
            require(Math.abs(committed.moles(gas) + receiver.gas.moles(gas) - moles.get(gas)) < 1e-9,
                    "committed transfer conserves " + gas);
        require(Math.abs(committed.thermalEnergy() + receiver.gas.thermalEnergy() - energy) < 1e-7,
                "committed transfer conserves heat");
        source.publish(); // Successful publication is idempotent.
        require(receiver.publications == 1, "receiver publishes once");

        // A second isolated cell verifies destination rejection compensates the raw source write.
        BlockPos rollbackPos = helper.absolutePos(new BlockPos(2, 1, 3));
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        classify(service, level, rollbackPos);
        require(service.addGas(level, rollbackPos, GasType.TRITIUM, 8, 300), "seed rollback gas");
        GasMixture rollbackBefore = service.sample(level, rollbackPos)
                .orElseThrow(() -> missing("rollback initial sample", rollbackPos));
        PipeGasTransaction.Endpoint rollbackSource = service.finiteRoomEndpoint(level, rollbackPos)
                .orElseThrow(() -> missing("rollback finite room endpoint", rollbackPos));
        MemoryEndpoint rejecting = new MemoryEndpoint(new GasMixture(Map.of(GasType.OXYGEN, 1.0), 450));
        rejecting.rejectNext = true;
        var rollbackPlan = PipeGasTransaction.plan(rollbackSource, rejecting, 1, 1, 2, 10000,
                Set.of(GasType.TRITIUM)).orElseThrow(() -> missing("rollback transfer plan", rollbackPos));
        try {
            PipeGasTransaction.commit(rollbackSource, rejecting, rollbackPlan);
            throw new AssertionError("destination rejection must fail transaction");
        } catch (IllegalStateException expected) { }
        require(same(service.sample(level, rollbackPos)
                .orElseThrow(() -> missing("post-rollback room sample", rollbackPos)), rollbackBefore),
                "rollback restores room gas");
        require(rejecting.publications == 0, "failed transfer publishes nothing");
        LevelChunk chunk = (LevelChunk) level.getChunk(rollbackPos);
        require(same(chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get())
                .get(rollbackPos.getX() & 15, rollbackPos.getY(), rollbackPos.getZ() & 15), rollbackBefore),
                "rollback restores exact sparse stored value");

        // A finite claim survives a sparse ambient cell; compensation after an unmutated
        // destination rejection must accept the already-restored initial value.
        BlockPos sparsePos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.STONE);
        classify(service, level, sparsePos);
        LevelChunk sparseChunk = (LevelChunk) level.getChunk(sparsePos);
        var sparseData = sparseChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        int lx = sparsePos.getX() & 15, lz = sparsePos.getZ() & 15;
        GasMixture sparseInitial = service.sample(level, sparsePos)
                .orElseThrow(() -> missing("sparse initial sample", sparsePos));
        sparseData.put(lx, sparsePos.getY(), lz, sparseInitial, sparseInitial);
        GasMixture ambient = service.sample(level, sparsePos)
                .orElseThrow(() -> missing("sparse ambient sample", sparsePos));
        require(sparseData.get(lx, sparsePos.getY(), lz) == null && sparseData.isFiniteClaimed(lx, sparsePos.getY(), lz),
                "ambient override removal retains finite claim");
        PipeGasTransaction.Endpoint sparseEndpoint = service.finiteRoomEndpoint(level, sparsePos)
                .orElseThrow(() -> missing("sparse finite room endpoint", sparsePos));
        Map<GasType, Double> rawMoles = new java.util.EnumMap<>(GasType.class);
        rawMoles.putAll(ambient.gasMoles());
        rawMoles.put(GasType.TRITIUM, 2.0);
        GasMixture raw = new GasMixture(rawMoles, ambient.temperatureKelvin());
        require(sparseEndpoint.writeRaw(ambient, raw), "raw write to sparse ambient endpoint");
        require(sparseEndpoint.writeRaw(null, ambient), "rollback accepts its pending raw value");
        require(sparseData.get(lx, sparsePos.getY(), lz) == null, "ambient rollback restores sparse absence");
        require(!sparseEndpoint.writeRaw(null, raw), "arbitrary null-before writes remain rejected");

        PipeGasTransaction.Endpoint pending = service.finiteRoomEndpoint(level, sparsePos)
                .orElseThrow(() -> missing("pending finite room endpoint", sparsePos));
        Map<GasType, Double> pendingMoles = new java.util.EnumMap<>(GasType.class);
        pendingMoles.putAll(ambient.gasMoles());
        pendingMoles.put(GasType.TRITIUM, ambient.moles(GasType.TRITIUM) + 1);
        GasMixture pendingNext = new GasMixture(pendingMoles, ambient.temperatureKelvin());
        require(pending.writeRaw(ambient, pendingNext), "prepare sparse pending endpoint");
        service.configureAtServerStart(Set.of(level.dimension()), service.spaceDimensionsForTest());
        require(pending.writeRaw(null, ambient), "compensate unpublished sparse pending endpoint");
        require(sparseData.get(lx, sparsePos.getY(), lz) == null && sparseData.isFiniteClaimed(lx, sparsePos.getY(), lz),
                "pending compensation restores sparse absence and retains finite claim");

        helper.succeed();
    }

    private static void classify(AtmosphereService service, ServerLevel level, BlockPos pos) {
        for (int pass = 1; pass <= 12 && service.sample(level, pos).isEmpty(); pass++)
            service.tick(level, syntheticGameTime += AtmosphereService.TICK_CADENCE);
        require(service.sample(level, pos).isPresent(), "sealed cell must be classified");
        LevelChunk chunk = (LevelChunk) level.getChunk(pos);
        var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        require(data != null && data.isFiniteClaimed(pos.getX() & 15, pos.getY(), pos.getZ() & 15),
                "cell requires finite ownership");
    }

    private static void require(boolean test, String message) {
        if (!test) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
    private static IllegalStateException missing(String stage, BlockPos pos) {
        return new IllegalStateException("Missing value at stage '" + stage + "' position " + pos);
    }
    private static void requireThrows(Runnable action, String message) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
    private static boolean same(GasMixture a, GasMixture b) {
        if (a == null || b == null || Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) != 0
                || Double.compare(a.thermalEnergy(), b.thermalEnergy()) != 0) return false;
        for (GasType gas : GasType.values()) if (Double.compare(a.moles(gas), b.moles(gas)) != 0) return false;
        return true;
    }

    private static final class MemoryEndpoint implements PipeGasTransaction.Endpoint {
        private final Object identity = new Object();
        private GasMixture gas;
        private int publications;
        private boolean rejectNext;
        private MemoryEndpoint(GasMixture gas) { this.gas = gas; }
        public Object identity() { return identity; }
        public long version() { return 0; }
        public GasMixture snapshot() { return gas; }
        public boolean eligible() { return true; }
        public boolean writeRaw(GasMixture before, GasMixture next) {
            if (rejectNext) { rejectNext = false; return false; }
            gas = next;
            return true;
        }
        public void publish() { publications++; }
    }
}
