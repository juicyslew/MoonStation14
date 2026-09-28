package com.juicyslew.moonstation14.gametest.power.runtime;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
import com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerDeviceGameTests {
    private PowerDeviceGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lampLitBlockstateChangesActualLightEmission(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos lamp = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(lamp, ModBlocks.POWER_LAMP.get().defaultBlockState(), 3);
        PowerDeviceBlockEntity originalDevice = (PowerDeviceBlockEntity) level.getBlockEntity(lamp);
        require(level.getBlockState(lamp).getLightEmission(level, lamp) == 0, "unlit lamp emits no light");
        level.setBlock(lamp, level.getBlockState(lamp).setValue(PowerDeviceBlock.LIT, true), 3);
        require(level.getBlockState(lamp).getLightEmission(level, lamp) == 14, "lit lamp emits level 14 light");
        require(level.getBlockEntity(lamp) == originalDevice, "same-block lit transition retains its device entity");
        level.setBlock(lamp, level.getBlockState(lamp).setValue(PowerDeviceBlock.FACING, Direction.SOUTH), 3);
        require(level.getBlockEntity(lamp) == originalDevice, "same-block facing transition retains its device entity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void coveringAndUncoveringCableHostPreservesElectricalConnection(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos b = helper.absolutePos(new BlockPos(3, 1, 2));
        level.setBlock(a, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        level.setBlock(b, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        require(CableStorage.place(level, a, Direction.UP, CableTier.HV), "place first cable node");
        require(CableStorage.place(level, b, Direction.UP, CableTier.HV), "place adjacent cable node");
        level.setBlock(a, level.getBlockState(a).setValue(StationFloorBlock.TILE_FINISH,
                StationFloorBlock.TileFinish.STEEL), 3);
        require(CableStorage.get(level, a, Direction.UP) == CableTier.HV,
                "covering the host with station tile retains cable record");
        level.setBlock(a, level.getBlockState(a).setValue(StationFloorBlock.TILE_FINISH,
                StationFloorBlock.TileFinish.NONE), 3);
        require(CableStorage.get(level, a, Direction.UP) == CableTier.HV,
                "uncovering the host retains the same cable record");
        require(CableStorage.get(level, b, Direction.UP) == CableTier.HV,
                "adjacent cable node remains connected at matching tier");
        require(PowerTopology.cableEdge(new CableFaceNode(a, Direction.UP, CableTier.HV),
                        new CableFaceNode(b, Direction.UP, CableTier.HV)).isPresent(),
                "same-tier adjacent face records form an electrical edge");
        require(CableStorage.place(level, b, Direction.DOWN, CableTier.MV),
                "a different face can carry a separate tier");
        require(PowerTopology.cableEdge(new CableFaceNode(a, Direction.UP, CableTier.HV),
                        new CableFaceNode(b, Direction.DOWN, CableTier.MV)).isEmpty(),
                "wrong tier and face do not form an electrical edge");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 320)
    public static void lampExtensionReceiverUsesNearestKnownLvCableWithinThreeBlocks(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos apc = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos apcOutputHost = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos secondWallHost = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos floorCableHost = helper.absolutePos(new BlockPos(2, 0, 3));
        BlockPos closeLamp = helper.absolutePos(new BlockPos(2, 1, 3));
        BlockPos nearbyLamp = helper.absolutePos(new BlockPos(2, 1, 5));
        BlockPos farLamp = helper.absolutePos(new BlockPos(2, 1, 8));
        BlockPos wrongTierHost = helper.absolutePos(new BlockPos(2, 0, 10));
        BlockPos wrongTierLamp = helper.absolutePos(new BlockPos(2, 1, 10));
        level.setBlock(apcOutputHost, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(secondWallHost, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(floorCableHost, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        level.setBlock(wrongTierHost, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        placeDevice(level, closeLamp, PowerDeviceKind.LAMP, Direction.SOUTH);
        placeDevice(level, nearbyLamp, PowerDeviceKind.LAMP, Direction.NORTH);
        placeDevice(level, farLamp, PowerDeviceKind.LAMP, Direction.WEST);
        placeDevice(level, wrongTierLamp, PowerDeviceKind.LAMP, Direction.EAST);
        ((PowerDeviceBlockEntity) level.getBlockEntity(apc)).setEnergyJoules(500_000);
        require(CableStorage.place(level, apcOutputHost, Direction.WEST, CableTier.APC), "place APC output LV cable");
        require(CableStorage.place(level, secondWallHost, Direction.WEST, CableTier.APC), "place second wall LV cable");
        require(CableStorage.place(level, floorCableHost, Direction.UP, CableTier.APC), "place floor LV cable");
        require(CableStorage.place(level, wrongTierHost, Direction.UP, CableTier.MV), "place wrong-tier cable");

        helper.runAfterDelay(100, () -> {
            require(graphState(level, apcOutputHost, Direction.WEST, CableTier.APC).knowledge()
                            == LoadedPowerGraph.Knowledge.KNOWN,
                    "APC output cable component is known before checking lamp receivers");
            assertLamp(level, closeLamp, true, "lamp directly above powered floor cable is powered without facing it");
            assertLamp(level, nearbyLamp, true, "lamp within three blocks receives nearby LV power");
            assertLamp(level, farLamp, false, "lamp beyond three blocks remains dark");
            assertLamp(level, wrongTierLamp, false, "MV cable cannot power an LV lamp");

            level.setBlock(closeLamp, Blocks.AIR.defaultBlockState(), 3);
            placeDevice(level, closeLamp, PowerDeviceKind.LAMP, Direction.NORTH);
            helper.runAfterDelay(60, () -> {
                assertLamp(level, closeLamp, true, "replacement lamp resolves its LV extension receiver");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void liveSourceSubstationApcBatteryAndLampChain(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        // Shift the requested x=0 device row into the empty template; y and z remain aligned.
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos substation = helper.absolutePos(new BlockPos(3, 1, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, 1, 6));
        BlockPos lamp = helper.absolutePos(new BlockPos(3, 1, 8));

        // Full-sturdy wall hosts: x=1, z=0..6 and x=-1, z=2..4 in the unshifted fixture.
        for (int z = 2; z <= 8; z++) level.setBlock(helper.absolutePos(new BlockPos(4, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++) level.setBlock(helper.absolutePos(new BlockPos(2, 1, z)), Blocks.STONE.defaultBlockState(), 3);

        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        placeDevice(level, lamp, PowerDeviceKind.LAMP, Direction.EAST);

        // Cable networks deliberately split at each device's tier-changing ports.
        for (int z = 2; z <= 4; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.HV), "place HV cable");
        for (int z = 4; z <= 6; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, z)), Direction.EAST, CableTier.MV), "place MV cable");
        for (int z = 6; z <= 8; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.APC), "place APC cable");

        // Let chunk refresh, incremental graph indexing, and the 20-tick runtime solve settle.
        helper.runAfterDelay(100, () -> {
            assertLamp(level, lamp, true, "source powers lamp through HV, MV, and APC");

            // Removing HV isolates the source, but the precharged APC battery supplies the lamp.
            level.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
            helper.runAfterDelay(60, () -> {
                assertLamp(level, lamp, true, "precharged battery keeps lamp lit after HV break");
                PowerDeviceBlockEntity apcEntity = (PowerDeviceBlockEntity) level.getBlockEntity(apc);
                Player player = new Player(level, apc, 0, new GameProfile(UUID.randomUUID(), "power-test")) {
                    @Override public boolean isCreative() { return true; }
                    @Override public boolean isSpectator() { return false; }
                };
                player.getAbilities().instabuild = true;
                require(apcEntity.toggleBreaker(player, 0), "authorized nearby player opens APC breaker");
                helper.runAfterDelay(40, () -> {
                    assertLamp(level, lamp, false, "open breaker disconnects battery output");
                    apcEntity.setEnergyJoules(0);
                    require(apcEntity.toggleBreaker(player, 0), "authorized nearby player closes APC breaker");
                    helper.runAfterDelay(40, () -> {
                        assertLamp(level, lamp, false, "empty battery and absent source leave lamp dark");

                        // Restore the source, but substitute an HV cable on the MV host face.
                        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
                        require(CableStorage.remove(level, helper.absolutePos(new BlockPos(2, 1, 4)), Direction.EAST) == CableTier.MV,
                                "remove MV cable for wrong-tier isolation check");
                        require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, 4)), Direction.EAST, CableTier.HV),
                                "install wrong-tier HV cable on MV link");
                        helper.runAfterDelay(80, () -> {
                            assertLamp(level, lamp, false, "wrong tier cannot energize the downstream lamp");
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void arbitraryStoneHostReplacementEventuallyStopsEnergizedCable(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos substation = helper.absolutePos(new BlockPos(3, 1, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, 1, 6));
        BlockPos lamp = helper.absolutePos(new BlockPos(3, 1, 8));
        BlockPos changedHost = helper.absolutePos(new BlockPos(4, 1, 3));
        for (int z = 2; z <= 8; z++)
            level.setBlock(helper.absolutePos(new BlockPos(4, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(2, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        placeDevice(level, lamp, PowerDeviceKind.LAMP, Direction.EAST);
        for (int z = 2; z <= 4; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.HV),
                    "place stone-face cable at z=" + z);
        for (int z = 4; z <= 6; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, z)), Direction.EAST, CableTier.MV),
                    "place MV cable at z=" + z);
        for (int z = 6; z <= 8; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.APC),
                    "place APC cable at z=" + z);

        helper.runAfterDelay(100, () -> {
            assertLamp(level, lamp, true, "stone-host cable powers lamp before replacement");
            // Remove the charged backup before testing that the cable path itself
            // loses power; otherwise the APC correctly keeps the lamp energized.
            ((PowerDeviceBlockEntity) level.getBlockEntity(apc)).setEnergyJoules(0);
            level.setBlock(changedHost, Blocks.DIRT.defaultBlockState(), 3);
            // setBlock(..., 3) is not assumed to emit NeighborNotify; indexed chunks
            // are lazily revalidated under the graph refresh budget.
            helper.runAfterDelay(100, () -> {
                assertLamp(level, lamp, false, "replaced arbitrary host cannot leave a stale energized path");
                require(CableStorage.get(level, changedHost, Direction.WEST) == null,
                        "changed host record was pruned after bounded refresh");
                helper.succeed();
            });
        });
    }

    private static void placeDevice(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
        var block = switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case APC -> ModBlocks.APC.get();
            case LAMP -> ModBlocks.POWER_LAMP.get();
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static void assertLamp(ServerLevel level, BlockPos lamp, boolean expected, String message) {
        boolean lit = level.getBlockState(lamp).getValue(PowerDeviceBlock.LIT);
        if (lit != expected) {
            CableFaceNode cable = new CableFaceNode(lamp.relative(Direction.EAST), Direction.WEST, CableTier.APC);
            LoadedPowerGraph.NodeState graph = PowerGraphService.state(level, cable);
            throw new GameTestAssertException(message + " (lit=" + lit + ", lampCable=" + graph
                    + ", expectedPorts=" + expectedPortStates(level, lamp)
                    + ", indexedDevices=" + PowerRuntime.indexedDeviceCount(level)
                    + ", solve=" + PowerRuntime.lastSolveDiagnostic(level) + ")");
        }
    }

    private static String expectedPortStates(ServerLevel level, BlockPos lamp) {
        BlockPos source = lamp.offset(0, 0, -6);
        BlockPos substation = lamp.offset(0, 0, -4);
        BlockPos apc = lamp.offset(0, 0, -2);
        return "sourceHV=" + graphState(level, source.relative(Direction.EAST), Direction.WEST, CableTier.HV)
                + ",subHV=" + graphState(level, substation.relative(Direction.EAST), Direction.WEST, CableTier.HV)
                + ",subMV=" + graphState(level, substation.relative(Direction.WEST), Direction.EAST, CableTier.MV)
                + ",apcMV=" + graphState(level, apc.relative(Direction.WEST), Direction.EAST, CableTier.MV)
                + ",apcOut=" + graphState(level, apc.relative(Direction.EAST), Direction.WEST, CableTier.APC);
    }

    private static LoadedPowerGraph.NodeState graphState(ServerLevel level, BlockPos host, Direction face, CableTier tier) {
        return PowerGraphService.state(level, new CableFaceNode(host, face, tier));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
