package com.juicyslew.moonstation14.gametest.power.runtime;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances;
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
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"power"})
@PrefixGameTestTemplate(false)
public final class PowerDeviceGameTests {
    private PowerDeviceGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
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

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
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

    @GameTestTags({"suite-stabilization"})
    @GameTest(template = "empty", timeoutTicks = 1400, templateNamespace = "moonstation14")
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

        awaitLampExtensionPath(helper, level, apcOutputHost, closeLamp, nearbyLamp, farLamp, wrongTierHost, () -> {
            helper.runAfterDelay(PowerRuntime.TICKS_PER_SOLVE, () -> {
            require(graphState(level, apcOutputHost, Direction.WEST, CableTier.APC).knowledge()
                            == LoadedPowerGraph.Knowledge.KNOWN,
                    "APC output cable component is known before checking lamp receivers");
            assertLamp(level, closeLamp, true, "lamp directly above powered floor cable is powered without facing it");
            assertLamp(level, nearbyLamp, true, "lamp within three blocks receives nearby LV power");
            assertLamp(level, farLamp, false, "lamp beyond three blocks remains dark");
            assertLamp(level, wrongTierLamp, false, "MV cable cannot power an LV lamp");

            level.setBlock(closeLamp, Blocks.AIR.defaultBlockState(), 3);
            placeDevice(level, closeLamp, PowerDeviceKind.LAMP, Direction.NORTH);
            awaitLampExtensionPath(helper, level, apcOutputHost, closeLamp, nearbyLamp, farLamp, wrongTierHost, () -> {
                helper.runAfterDelay(PowerRuntime.TICKS_PER_SOLVE, () -> {
                assertLamp(level, closeLamp, true, "replacement lamp resolves its LV extension receiver");
                helper.succeed();
            });
            });
            });
        });
    }

    @GameTestTags({"apc-light-source"})
    @GameTest(template = "empty", timeoutTicks = 140, templateNamespace = "moonstation14")
    public static void isolatedApcProjectsNativeEmissionAndSurvivesOpenBreaker(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        // Elevate the sealed fixture beyond the harness's nearby beacon propagation radius.
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(20);
        // A sealed opaque room excludes the GameTest beacon/debug marker and neighboring fixtures.
        for (int x = -1; x <= 7; x++) for (int y = -1; y <= 5; y++) for (int z = -1; z <= 7; z++) {
            if (x <= 1 || x >= 5 || y <= 0 || y >= 4 || z <= 1 || z >= 5)
                level.setBlock(origin.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
        }
        BlockPos apc = origin.offset(3, 2, 3);
        require(PowerDeviceFixturePlacement.placeApc(level, apc, Direction.WEST), "place isolated wall-mounted APC");
        PowerDeviceBlockEntity device = (PowerDeviceBlockEntity) level.getBlockEntity(apc);
        var profile = ModPowerAppearances.catalog(level).get(ModPowerAppearances.APC);
        require(profile != null, "resolved APC profile exists");
        device.setEnergyJoules(0);
        helper.runAfterDelay(10, () -> {
        int baseline = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, apc.north());
        require(baseline == 0, "sealed APC room starts at zero block light; got " + baseline + diagnosticEmitters(level, apc));
        device.setEnergyJoules(500_000);
        helper.runAfterDelay(PowerRuntime.TICKS_PER_SOLVE + 2, () -> {
            var state = level.getBlockState(apc);
            require(state.getValue(PowerDeviceBlock.GLOW), "charged battery projects GLOW");
            require(state.getValue(PowerDeviceBlock.EMISSION) == profile.lightLevel(), "resolved profile emission is projected");
            require(state.getLightEmission(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == profile.lightLevel(),
                    "native context-free state query sees emission without a block entity");
            require(chunkSourcesApc(level, apc), "native chunk source scan discovers already-on APC without a GLOW transition");
            require(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, apc) == profile.lightLevel()
                    && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, apc.north()) == profile.lightLevel() - 1,
                    "isolated APC illuminates itself and adjacent air");
            require(device.toggleBreakerAuthorized(true, 0), "open APC breaker through authorized entity API");
            helper.runAfterDelay(PowerRuntime.TICKS_PER_SOLVE + 2, () -> {
                require(level.getBlockState(apc).getValue(PowerDeviceBlock.GLOW), "charged open-breaker APC remains lit");
                device.setEnergyJoules(0);
                helper.runAfterDelay(PowerRuntime.TICKS_PER_SOLVE + 2, () -> {
                    var dark = level.getBlockState(apc);
                    require(!dark.getValue(PowerDeviceBlock.GLOW) && dark.getValue(PowerDeviceBlock.EMISSION) == 0,
                            "zero-energy no-input APC extinguishes");
                    require(dark.getLightEmission(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == 0,
                            "context-free state query reports extinction");
                    require(!chunkSourcesApc(level, apc), "native chunk source scan excludes extinguished APC");
                    awaitDark(helper, level, apc, 40);
                });
            });
        });
        });
    }

    private static boolean chunkSourcesApc(ServerLevel level, BlockPos apc) {
        boolean[] found = {false};
        level.getChunk(apc).findBlockLightSources((sourcePos, sourceState) -> {
            if (sourcePos.equals(apc) && sourceState.getBlock() instanceof PowerDeviceBlock block
                    && block.kind() == PowerDeviceKind.APC) found[0] = true;
        });
        return found[0];
    }

    private static void awaitDark(GameTestHelper helper, ServerLevel level, BlockPos apc, int remaining) {
        int brightness = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, apc.north());
        if (brightness == 0) { helper.succeed(); return; }
        if (remaining <= 0) throw new GameTestAssertException("sealed APC neighbor stayed lit: " + brightness
                + diagnosticEmitters(level, apc));
        helper.runAfterDelay(1, () -> awaitDark(helper, level, apc, remaining - 1));
    }

    private static String diagnosticEmitters(ServerLevel level, BlockPos center) {
        StringBuilder result = new StringBuilder("; local emitters=");
        BlockPos.betweenClosed(center.offset(-15, -15, -15), center.offset(15, 15, 15)).forEach(pos -> {
            var state = level.getBlockState(pos);
            int emission = state.getLightEmission(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (emission > 0 || state.getLightEmission(level, pos) > 0)
                result.append(pos).append(':').append(state.getBlock()).append("[empty=").append(emission)
                        .append(",context=").append(state.getLightEmission(level, pos)).append("] ");
        });
        return result.toString();
    }

    @GameTest(template = "power_live_chain_clear", timeoutTicks = 1200, templateNamespace = "moonstation14")
    @GameTestTags({"machine-model-regression"})
    public static void liveSourceSubstationApcBatteryAndLampChain(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        // Shift the requested x=0 device row into the bounded template; y and z remain aligned.
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
            var sourceHv=new CableFaceNode(helper.absolutePos(new BlockPos(4,1,2)),Direction.WEST,CableTier.HV);
            var subHv=new CableFaceNode(helper.absolutePos(new BlockPos(4,1,4)),Direction.WEST,CableTier.HV);
            var subMv=new CableFaceNode(helper.absolutePos(new BlockPos(2,1,4)),Direction.EAST,CableTier.MV);
            var apcMv=new CableFaceNode(helper.absolutePos(new BlockPos(2,1,6)),Direction.EAST,CableTier.MV);
            var apcOut=new CableFaceNode(helper.absolutePos(new BlockPos(4,1,6)),Direction.WEST,CableTier.APC);
            com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness.await(helper,level,600,
                    ()->com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness.topologyReadyWithKnownPorts(
                            level,sourceHv,subHv,subMv,apcMv,apcOut)
                            &&level.getBlockState(lamp).getValue(PowerDeviceBlock.LIT),
                    ()->"source="+PowerGraphService.state(level,sourceHv)+", subHV="+PowerGraphService.state(level,subHv)
                            +", subMV="+PowerGraphService.state(level,subMv)+", apcMV="+PowerGraphService.state(level,apcMv)
                            +", apcOut="+PowerGraphService.state(level,apcOut)+", "
                            +PowerRuntime.lastSolveDiagnostic(level),()->{
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

    @GameTestTags({"suite-stabilization"})
    @GameTest(template = "empty", timeoutTicks = 1400, templateNamespace = "moonstation14")
    public static void arbitraryStoneHostReplacementEventuallyStopsEnergizedCable(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos substation = helper.absolutePos(new BlockPos(3, 1, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, 1, 6));
        BlockPos lamp = helper.absolutePos(new BlockPos(3, 1, 8));
        BlockPos outputHost = helper.absolutePos(new BlockPos(4, 1, 6));
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

        awaitArbitraryHostPath(helper, level, source, substation, apc, outputHost, lamp, () -> {
            helper.runAfterDelay(PowerRuntime.TICKS_PER_SOLVE, () -> {
            assertLamp(level, lamp, true, "stone-host cable powers lamp before replacement");
            // Remove the charged backup before testing that the cable path itself
            // loses power; otherwise the APC correctly keeps the lamp energized.
            ((PowerDeviceBlockEntity) level.getBlockEntity(apc)).setEnergyJoules(0);
            level.setBlock(changedHost, Blocks.DIRT.defaultBlockState(), 3);
            // setBlock(..., 3) is not assumed to emit NeighborNotify; indexed chunks
            // are lazily revalidated under the graph refresh budget.
            awaitArbitraryHostPath(helper, level, source, substation, apc, outputHost, lamp, () -> {
                helper.runAfterDelay(PowerRuntime.TICKS_PER_SOLVE, () -> {
                assertLamp(level, lamp, false, "replaced arbitrary host cannot leave a stale energized path");
                require(CableStorage.get(level, changedHost, Direction.WEST) == null,
                        "changed host record was pruned after bounded refresh");
                helper.succeed();
            });
            });
            });
        });
    }

    private static void placeDevice(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
        if (kind == PowerDeviceKind.APC) {
            require(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement.placeApc(level, pos, facing), "place supported APC");
            return;
        }
        var block = switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case APC -> ModBlocks.APC.get();
            case LAMP -> ModBlocks.POWER_LAMP.get();
            case DEBUG_LOAD_LAMP -> ModBlocks.HIGH_LOAD_TEST_LAMP.get();
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static void awaitLampExtensionPath(GameTestHelper helper, ServerLevel level, BlockPos apcOutputHost,
            BlockPos closeLamp, BlockPos nearbyLamp, BlockPos farLamp, BlockPos wrongTierHost, Runnable ready) {
        var output = new CableFaceNode(apcOutputHost, Direction.WEST, CableTier.APC);
        var wrongTier = new CableFaceNode(wrongTierHost, Direction.UP, CableTier.MV);
        PowerFixtureReadiness.await(helper, level, 500,
                () -> {
                    var close = PowerGraphService.nearestApcExtensionNode(level, closeLamp, 3);
                    var nearby = PowerGraphService.nearestApcExtensionNode(level, nearbyLamp, 3);
                    return close != null && nearby != null
                            && PowerFixtureReadiness.topologyReadyWithKnownPorts(level, output, close, nearby, wrongTier)
                            && PowerGraphService.nearestApcExtensionNode(level, farLamp, 3) == null;
                },
                () -> "APCout=" + PowerGraphService.state(level, output) + ", closeReceiver="
                        + PowerGraphService.nearestApcExtensionNode(level, closeLamp, 3) + ", nearbyReceiver="
                        + PowerGraphService.nearestApcExtensionNode(level, nearbyLamp, 3) + ", farReceiver="
                        + PowerGraphService.nearestApcExtensionNode(level, farLamp, 3) + ", wrongTier="
                        + PowerGraphService.state(level, wrongTier) + ", topologyReady="
                        + PowerGraphService.topologyReady(level) + ", indexedDevices="
                        + PowerRuntime.indexedDeviceCount(level), ready);
    }

    private static void awaitArbitraryHostPath(GameTestHelper helper, ServerLevel level, BlockPos source,
            BlockPos substation, BlockPos apc, BlockPos outputHost, BlockPos lamp, Runnable ready) {
        var sourceHv = new CableFaceNode(source.relative(Direction.EAST), Direction.WEST, CableTier.HV);
        var subHv = new CableFaceNode(substation.relative(Direction.EAST), Direction.WEST, CableTier.HV);
        var subMv = new CableFaceNode(substation.relative(Direction.WEST), Direction.EAST, CableTier.MV);
        var apcMv = new CableFaceNode(apc.relative(Direction.WEST), Direction.EAST, CableTier.MV);
        var output = new CableFaceNode(outputHost, Direction.WEST, CableTier.APC);
        var receiver = new CableFaceNode(lamp.relative(Direction.EAST), Direction.WEST, CableTier.APC);
        var apcEntity = (PowerDeviceBlockEntity) level.getBlockEntity(apc);
        PowerFixtureReadiness.await(helper, level, 500,
                () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level, sourceHv, subHv, subMv, apcMv,
                        output, receiver),
                () -> "sourceHV=" + PowerGraphService.state(level, sourceHv) + ", subHV="
                        + PowerGraphService.state(level, subHv) + ", subMV=" + PowerGraphService.state(level, subMv)
                        + ", apcMV=" + PowerGraphService.state(level, apcMv) + ", output="
                        + PowerGraphService.state(level, output) + ", receiver=" + PowerGraphService.state(level, receiver)
                        + ", APCvisual=" + PowerRuntime.apcVisualState(level, apcEntity) + ", topologyReady="
                        + PowerGraphService.topologyReady(level) + ", indexedDevices="
                        + PowerRuntime.indexedDeviceCount(level), ready);
    }

    private static void assertLamp(ServerLevel level, BlockPos lamp, boolean expected, String message) {
        boolean lit = level.getBlockState(lamp).getValue(PowerDeviceBlock.LIT);
        if (lit != expected) {
            CableFaceNode cable = new CableFaceNode(lamp.relative(Direction.EAST), Direction.WEST, CableTier.APC);
            LoadedPowerGraph.NodeState graph = PowerGraphService.state(level, cable);
            throw new GameTestAssertException(message + " (lit=" + lit + ", lampCable=" + graph
                    + ", expectedPorts=" + expectedPortStates(level, lamp)
                    + ", indexedDevices=" + PowerRuntime.indexedDeviceCount(level)
                    + ", solve=" + boundedSolve(level, lamp) + ")");
        }
    }

    private static String boundedSolve(ServerLevel level, BlockPos lamp) {
        String solve = PowerRuntime.lastSolveDiagnostic(level);
        int lamps = solve.indexOf(",lampWatts=");
        if (lamps < 0) return solve.length() <= 240 ? solve : solve.substring(0, 240) + "...";
        String lampEntry = lamp.toShortString() + "=";
        int start = solve.indexOf(lampEntry, lamps);
        int end = solve.indexOf(',', start < 0 ? lamps : start);
        if (end < 0) end = solve.indexOf('}', start < 0 ? lamps : start);
        return solve.substring(0, Math.min(lamps, 240)) + ",lampWatts[" + lampEntry
                + (start < 0 ? "absent" : solve.substring(start + lampEntry.length(), end))
                + "] (diagnosticLength=" + solve.length() + ")";
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
