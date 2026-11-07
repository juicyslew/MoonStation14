package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasFixtureTestSupport;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
public final class TegFixturePlacementGameTests {
    private TegFixturePlacementGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags("teg-fixture-construction")
    public static void additionalLayerAdmissionPreservesExistingFiniteGasAndRejectsReadmission(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(3, 1, 3));
        var lease = FixtureChunkTickets.acquire(helper, new AABB(host).inflate(2));
        FixtureChunkTickets.awaitEntityTicking(helper, lease, 40, () -> true,
                "TEG pipe placement host entity-ticking", () -> {
                    require(TegFixturePlacement.admitExposedLanes(level, List.of(host), VisualPipeBlockEntity.PRIMARY) == null,
                            "fresh primary admission succeeds");
                    var primaryKey = new PipeTopology.NodeKey(host, PipeTopology.Layer.PRIMARY);
                    var data = PipeGasInventorySavedData.existing(level).orElseThrow();
                    var primaryBefore = LoadedPipeGasOwner.activeSnapshot(level, host, VisualPipeBlockEntity.PRIMARY).orElseThrow();
                    require(primaryBefore.node().equals(primaryKey), "primary active snapshot uses canonical topology key");

                    var inventoryBefore = data.snapshot();
                    var primaryEntry = inventoryBefore.get(primaryKey).orElseThrow();
                    GasMixture seedContents = new GasMixture(Map.of(GasType.NITROGEN, 7.0), 7.0 * 20.8 * 325.0);
                    GasMixture seeded = PipeGasFixtureTestSupport.atCurrentNetworkVolume(inventoryBefore, primaryKey,
                            seedContents.gasMoles(), seedContents.temperatureKelvin());
                    require(PipeGasFixtureTestSupport.seedLane(level, primaryKey, primaryBefore.incarnation(),
                                    primaryEntry.version(), seeded),
                            "seed known finite nitrogen once by inventory CAS");
                     var seededPrimary = snapshot(level,primaryKey);

                    require(TegFixturePlacement.admitExposedLanes(level, List.of(host), VisualPipeBlockEntity.SECONDARY) == null,
                            "additional secondary lane admission succeeds");
                    var afterSecondary = LoadedPipeGasOwner.activeSnapshot(level, host, VisualPipeBlockEntity.PRIMARY).orElseThrow();
                     assertGasAndIdentity(seededPrimary, snapshot(level,primaryKey), "secondary admission preserves seeded primary exactly");
                     var secondary = LoadedPipeGasOwner.activeSnapshot(level, host, VisualPipeBlockEntity.SECONDARY).orElseThrow();
                     var secondaryGas=data.snapshot().nodeShare(secondary.node());
                     require(!secondary.incarnation().equals(afterSecondary.incarnation()), "secondary has a distinct active owner");
                     require(secondaryGas.gasMoles().isEmpty() && secondaryGas.thermalEnergy() == 0.0,
                            "new secondary owner is vacuum");

                    String refused = TegFixturePlacement.admitExposedLanes(level, List.of(host), VisualPipeBlockEntity.PRIMARY);
                    require(refused != null, "readmission of already active primary is refused");
                    var afterRefusal = LoadedPipeGasOwner.activeSnapshot(level, host, VisualPipeBlockEntity.PRIMARY).orElseThrow();
                     assertGasAndIdentity(seededPrimary, snapshot(level,primaryKey), "refused primary readmission changes neither gas nor identity");
                     var secondaryAfterRefusal = LoadedPipeGasOwner.activeSnapshot(level, host, VisualPipeBlockEntity.SECONDARY).orElseThrow();
                     var secondaryAfterGas=data.snapshot().nodeShare(secondaryAfterRefusal.node());
                     require(secondaryAfterRefusal.incarnation().equals(secondary.incarnation())
                                     && secondaryAfterGas.gasMoles().equals(secondaryGas.gasMoles())
                                     && Double.compare(secondaryAfterGas.thermalEnergy(), secondaryGas.thermalEnergy()) == 0,
                            "refused readmission leaves secondary owner unchanged");
                    helper.succeed();
                }, host);
    }

    private record OwnerSnapshot(PipeGasInventory.Entry owner,GasMixture gas) { }
    private static OwnerSnapshot snapshot(ServerLevel level,PipeTopology.NodeKey key) {
        var inventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
        return new OwnerSnapshot(inventory.get(key).orElseThrow(),inventory.nodeShare(key));
    }
    private static void assertGasAndIdentity(OwnerSnapshot expected, OwnerSnapshot actual, String message) {
        require(expected.owner().node().equals(actual.owner().node()) && expected.owner().incarnation().equals(actual.owner().incarnation())
                        && expected.gas().gasMoles().equals(actual.gas().gasMoles())
                        && Double.compare(expected.gas().thermalEnergy(), actual.gas().thermalEnergy()) == 0,
                message);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
