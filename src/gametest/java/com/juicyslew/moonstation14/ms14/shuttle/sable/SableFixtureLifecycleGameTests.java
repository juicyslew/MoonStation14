package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.CabinGasInventorySnapshot;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.fixture.FixturePipeAdmission;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestTags({"shuttle-fixture-lifecycle", "shuttle", "shuttle-cabin-ventilation", "shuttle-owner-regression"})
@PrefixGameTestTemplate(false)
public final class SableFixtureLifecycleGameTests {
    private SableFixtureLifecycleGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", batch = "sable_disposable_lifecycle", timeoutTicks = 100)
    public static void disposablePlotRemovalRetiresExactPipeOwner(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        var authored = SableFixturePlot.allocateAndAuthor(level,
                new Vector3d(origin.getX() + .5, origin.getY(), origin.getZ() + .5), plot -> {
                    BlockPos storage = plot.storageOrigin();
                    plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                            ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                    return ShuttleCabinFixture.build(level, storage, plot.engineId());
                });
        SableFixtureScene.trackPlot(helper, authored.plot());
        BlockPos pipePos = authored.result().at(ShuttleCabinFixture.PIPE);
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var key = new PipeTopology.NodeKey(pipePos, PipeTopology.Layer.PRIMARY);
        var image = saved.snapshot();
        var live = image.get(key).orElseThrow();
        var oldId = live.incarnation();
        var mixture = image.nodeShare(key);
        var oldSubLevelId = authored.plot().engineId();
        BlockPos sentinel = helper.absolutePos(new BlockPos(2, 1, 2));
        require(level.getBlockState(sentinel).isAir(), "foreign sentinel cell is initially empty");
        require(level.setBlock(sentinel, Blocks.GOLD_BLOCK.defaultBlockState(), 3)
                        && level.getBlockState(sentinel).is(Blocks.GOLD_BLOCK),
                "foreign sentinel is installed outside the Sable plot at " + sentinel);
        boolean invalidFootprintRejected = false;
        try { authored.plot().ensureStorageFootprint(sentinel, 1, 1, 1); }
        catch (IllegalArgumentException expected) { invalidFootprintRejected = true; }
        require(invalidFootprintRejected, "cross-plot footprint rejected before it can be registered for teardown");
        authored.plot().close();
        require(SubLevelContainer.getContainer(level).getSubLevel(oldSubLevelId) == null
                        && SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                        .noneMatch(owner -> owner.getUniqueId().equals(oldSubLevelId)),
                "fixture owner is retired immediately by close, before another world tick");
        require(level.getBlockState(sentinel).is(Blocks.GOLD_BLOCK),
                "invalid cross-plot footprint did not register or clear its in-plot foreign sentinel at "
                        + sentinel + " state=" + level.getBlockState(sentinel));
        var after = saved.snapshot();
        var archived = after.retiredSnapshot().get(oldId);
        require(level.getBlockState(pipePos).isAir() && level.getBlockEntity(pipePos) == null,
                "fixture teardown invokes normal block removal hooks at its own pipe position");
        require(after.get(key).isEmpty(), "removed pipe's loaded owner key is absent");
        require(archived != null && archived.mixture().gasMoles().equals(mixture.gasMoles())
                        && Double.compare(archived.mixture().thermalEnergy(), mixture.thermalEnergy()) == 0,
                "normal pipe removal archives exact gas and thermal energy under old incarnation");

        var replacementAuthored = SableFixturePlot.allocateAndAuthor(level,
                new Vector3d(origin.getX() + 1.5, origin.getY(), origin.getZ() + .5), plot -> {
                    BlockPos storage = plot.storageOrigin();
                    plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                            ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                    return ShuttleCabinFixture.build(level, storage, plot.engineId());
                });
        SableFixtureScene.trackPlot(helper, replacementAuthored.plot());
        BlockPos replacementPipe = replacementAuthored.result().at(ShuttleCabinFixture.PIPE);
        var replacementKey = new PipeTopology.NodeKey(replacementPipe, PipeTopology.Layer.PRIMARY);
        var replacementEntry = saved.snapshot().get(replacementKey).orElseThrow();
        require(!replacementEntry.incarnation().equals(oldId), "new authored cabin receives a fresh pipe-owner UUID");
        var afterReplacement = saved.snapshot();
        if (replacementPipe.equals(pipePos)) {
            require(afterReplacement.get(key).filter(entry -> entry.incarnation().equals(replacementEntry.incarnation())
                            && !entry.incarnation().equals(oldId)).isPresent(),
                    "recycled loaded key belongs only to the exact fresh replacement incarnation");
        } else {
            require(afterReplacement.get(key).isEmpty(), "old loaded pipe key remains absent when storage was not reused");
        }
        require(afterReplacement.get(key).filter(entry -> entry.incarnation().equals(oldId)).isEmpty(),
                "retired old incarnation is never live under the old key");
        var replacementArchive = afterReplacement.retiredSnapshot().get(oldId);
        require(replacementArchive != null && replacementArchive.mixture().gasMoles().equals(mixture.gasMoles())
                        && Double.compare(replacementArchive.mixture().thermalEnergy(), mixture.thermalEnergy()) == 0,
                "replacement authoring preserves exact retired gas and thermal archive");
        authored.plot().close(); // Deliberate late repeated close must not touch a replacement owner/craft.
        require(level.getBlockState(replacementPipe).is(com.juicyslew.moonstation14.block.ModBlocks.GAS_PIPE_VISUAL.get())
                        && saved.snapshot().get(new PipeTopology.NodeKey(replacementPipe, PipeTopology.Layer.PRIMARY))
                        .filter(entry -> entry.incarnation().equals(replacementEntry.incarnation())).isPresent(),
                "repeated close of removed plot leaves replacement pipe and its owner untouched");
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("SABLE_FIXTURE_LIFECYCLE oldStorage={} newStorage={} reused={}",
                pipePos, replacementPipe, pipePos.equals(replacementPipe));
        replacementAuthored.plot().close();
        helper.succeed();
    }

    @GameTestTags({"shuttle-cabin-ventilation", "shuttle-owner-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", batch = "sable_disposable_lifecycle", timeoutTicks = 100)
    public static void canonicalCabinRemovalArchivesEveryLaneAndAllowsFreshConstruction(GameTestHelper helper) {
        BlockPos foreignPos = new BlockPos(2200, 300, 1600);
        var ticket = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.around(
                helper, foreignPos, foreignPos, 1);
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(
                helper, ticket, 80, () -> executeCanonicalCabinLifecycle(helper, foreignPos), foreignPos);
    }

    private static void executeCanonicalCabinLifecycle(GameTestHelper helper, BlockPos foreignPos) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        var authored = authorCabin(level, origin);
        SableFixtureScene.trackPlot(helper, authored.plot());
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        ShuttleCabinFixture.Built cabin = authored.result();
        var nodeKeys = new java.util.ArrayList<>(cabin.supplyPipeNodes());
        nodeKeys.addAll(cabin.returnPipeNodes());
        var beforeImage = saved.snapshot();
        var old = new LinkedHashMap<PipeTopology.NodeKey, PipeGasInventory.Entry>();
        var oldGas = new LinkedHashMap<PipeTopology.NodeKey, GasMixture>();
        for (var key : nodeKeys) { old.put(key, beforeImage.get(key).orElseThrow()); oldGas.put(key, beforeImage.nodeShare(key)); }
        require(old.size() == 16, "all 13 Primary and 3 Secondary cabin lane owners captured");
        var returnKey = cabin.returnPipeNodes().getLast();
        var oldSubLevelId = authored.plot().engineId();
        var returnOwner = old.get(returnKey);
        require(LoadedPipeGasOwner.setGasForTrustedFixture(level, returnKey.position(), VisualPipeBlockEntity.SECONDARY,
                        returnOwner.incarnation(), new GasMixture(Map.of(GasType.CARBON_DIOXIDE, 1.0), 293.0)),
                "finite CO2 seeded on exposed Secondary host");
        var seededImage = saved.snapshot();
        old.put(returnKey, seededImage.get(returnKey).orElseThrow());
        oldGas.put(returnKey, seededImage.nodeShare(returnKey));
        CabinGasInventorySnapshot.capture(level, cabin);

        var foreignKey = new PipeTopology.NodeKey(foreignPos, PipeTopology.Layer.PRIMARY);
        require(level.hasChunkAt(foreignPos) && level.getBlockState(foreignPos).isAir()
                        && level.getBlockEntity(foreignPos) == null
                        && saved.snapshot().get(foreignKey).isEmpty(),
                "foreign fixture position is loaded, empty, and has no saved owner before admission");
        var foreignId = FixturePipeAdmission.exposed(level, foreignPos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { clearOwnedForeignPipe(level, saved, foreignPos, foreignKey, foreignId); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { clearOwnedForeignPipe(level, saved, foreignPos, foreignKey, foreignId); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { clearOwnedForeignPipe(level, saved, foreignPos, foreignKey, foreignId); }
        });
        require(LoadedPipeGasOwner.setGasForTrustedFixture(level, foreignPos, VisualPipeBlockEntity.PRIMARY, foreignId,
                new GasMixture(Map.of(GasType.NITROGEN, 2.0), 293.0)), "foreign owner seeded");
        var foreignBefore = saved.snapshot();
        var foreignIdBefore = foreignBefore.get(foreignKey).orElseThrow();
        var foreignGas = foreignBefore.mixture(foreignKey);
        BlockPos storage = authored.plot().storageOrigin();
        authored.plot().close();
        require(SubLevelContainer.getContainer(level).getSubLevel(oldSubLevelId) == null
                        && SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                        .noneMatch(owner -> owner.getUniqueId().equals(oldSubLevelId)),
                "canonical fixture owner is retired immediately by close, before another world tick");
        var after = saved.snapshot();
        for (var e : old.entrySet()) {
            require(after.get(e.getKey()).isEmpty(), "old cabin lane absent from live inventory: " + e.getKey());
            var archived = after.retiredSnapshot().get(e.getValue().incarnation());
            var gas = oldGas.get(e.getKey());
            if (!gas.gasMoles().isEmpty() || gas.thermalEnergy() != 0.0)
                require(archived != null && archived.node().equals(e.getKey()) && archived.incarnation().equals(e.getValue().incarnation())
                                && archived.mixture().gasMoles().equals(gas.gasMoles())
                                && Double.compare(archived.mixture().thermalEnergy(), gas.thermalEnergy()) == 0,
                        "nonvacuum cabin owner archived exactly by old UUID");
            else require(archived == null, "vacuum owner has no fabricated archive record");
        }
        require(after.get(foreignKey).filter(e -> e.incarnation().equals(foreignId)).isPresent()
                        && after.mixture(foreignKey).equals(foreignGas),
                "outside live owner remains unchanged");

        var replacement = authorCabin(level, origin.offset(1, 0, 0));
        SableFixtureScene.trackPlot(helper, replacement.plot());
        boolean reusedStorage = replacement.plot().storageOrigin().equals(storage);
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "SABLE_CANONICAL_CABIN_LIFECYCLE oldStorage={} newStorage={} reused={}",
                storage, replacement.plot().storageOrigin(), reusedStorage);
        for (var key : replacement.result().supplyPipeNodes()) require(saved.snapshot().get(key)
                .filter(e -> old.values().stream().noneMatch(previous -> previous.incarnation().equals(e.incarnation()))).isPresent(),
                "replacement Primary node has fresh active UUID: " + key);
        for (var key : replacement.result().returnPipeNodes()) require(saved.snapshot().get(key)
                .filter(e -> old.values().stream().noneMatch(previous -> previous.incarnation().equals(e.incarnation()))).isPresent(),
                "replacement Secondary node has fresh active UUID: " + key);
        if (reusedStorage) for (var e : old.entrySet()) {
            var fresh = saved.snapshot().get(e.getKey()).orElseThrow();
            require(!fresh.incarnation().equals(e.getValue().incarnation()),
                    "reused storage key now belongs to a fresh active UUID: " + e.getKey());
        }
        for (var e : old.entrySet()) {
            var archive = saved.snapshot().retiredSnapshot().get(e.getValue().incarnation());
            var gas = oldGas.get(e.getKey());
            if (!gas.gasMoles().isEmpty() || gas.thermalEnergy() != 0.0)
                require(archive != null && archive.node().equals(e.getKey()) && archive.incarnation().equals(e.getValue().incarnation())
                                && archive.mixture().gasMoles().equals(gas.gasMoles())
                                && Double.compare(archive.mixture().thermalEnergy(), gas.thermalEnergy()) == 0,
                        "rebuild preserves the original retired gas archive");
        }
        require(saved.snapshot().get(foreignKey).filter(e -> e.incarnation().equals(foreignId)).isPresent(),
                "foreign owner survives replacement construction");
        clearOwnedForeignPipe(level, saved, foreignPos, foreignKey, foreignId);
        var foreignArchive = saved.snapshot().retiredSnapshot().get(foreignId);
        require(saved.snapshot().get(foreignKey).isEmpty()
                        && foreignArchive != null && foreignArchive.incarnation().equals(foreignId)
                        && foreignArchive.mixture().gasMoles().equals(foreignGas.gasMoles())
                        && Double.compare(foreignArchive.mixture().thermalEnergy(), foreignGas.thermalEnergy()) == 0,
                "owned foreign pipe normal removal releases its key and archives exact gas");
        replacement.plot().close();
        helper.succeed();
    }

    private static SableFixturePlot.Authored<ShuttleCabinFixture.Built> authorCabin(ServerLevel level, BlockPos origin) {
        return SableFixturePlot.allocateAndAuthor(level,
                new Vector3d(origin.getX() + .5, origin.getY(), origin.getZ() + .5), plot -> {
                    BlockPos storage = plot.storageOrigin();
                    plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                            ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                    return ShuttleCabinFixture.build(level, storage, plot.engineId());
                });
    }

    private static void clearOwnedForeignPipe(ServerLevel level, PipeGasInventorySavedData saved, BlockPos pos,
                                               PipeTopology.NodeKey key, java.util.UUID expectedId) {
        var visual = level.getBlockEntity(pos);
        if (visual instanceof VisualPipeBlockEntity pipe
                && pipe.functionalIdentity(VisualPipeBlockEntity.PRIMARY).filter(expectedId::equals).isPresent()
                && saved.snapshot().get(key).filter(entry -> entry.incarnation().equals(expectedId)).isPresent())
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
