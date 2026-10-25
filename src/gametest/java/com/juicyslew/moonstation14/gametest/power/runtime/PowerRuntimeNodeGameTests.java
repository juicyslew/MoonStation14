package com.juicyslew.moonstation14.gametest.power.runtime;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestTags({"power", "singularity-power-foundation"})
@PrefixGameTestTemplate(false)
public final class PowerRuntimeNodeGameTests {
    private PowerRuntimeNodeGameTests() { }

    @GameTest(template = "power_live_chain_clear", timeoutTicks = 600, templateNamespace = "moonstation14")
    @GameTestTags("power-runtime-node-meter")
    public static void indexedRuntimeNodeSupplierFeedsRealMvNetworkAndIdentityBoundMeter(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        FixtureChunkTickets.Lease lease = FixtureChunkTickets.acquire(helper, helper.getBounds().inflate(16));
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos substation = helper.absolutePos(new BlockPos(3, 1, 4));
        BlockPos receiver = helper.absolutePos(new BlockPos(3, 1, 6));
        level.setBlock(helper.absolutePos(new BlockPos(4, 1, 2)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(4, 1, 3)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(4, 1, 4)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(2, 1, 4)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(2, 1, 5)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(2, 1, 6)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(2, 1, 2)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(2, 1, 3)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(source, ModBlocks.HV_SOURCE.get().defaultBlockState()
                .setValue(PowerDeviceBlock.FACING, Direction.EAST), 3);
        FixtureNode supplier = new FixtureNode(source, level.getBlockState(source), true, false);
        level.setBlockEntity(supplier);
        level.setBlock(substation, ModBlocks.HV_MV_SUBSTATION.get().defaultBlockState()
                .setValue(PowerDeviceBlock.FACING, Direction.EAST), 3);
        level.setBlock(receiver, ModBlocks.POWER_LAMP.get().defaultBlockState()
                .setValue(PowerDeviceBlock.FACING, Direction.WEST), 3);
        FixtureNode consumer = new FixtureNode(receiver, level.getBlockState(receiver), false, true);
        level.setBlockEntity(consumer);
        for (int z = 2; z <= 4; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.HV), "HV cable segment");
        for (int z = 4; z <= 6; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, z)), Direction.EAST, CableTier.MV), "MV cable segment");
        var sourcePort = new CableFaceNode(helper.absolutePos(new BlockPos(4, 1, 2)), Direction.WEST, CableTier.HV);
        var subHvPort = new CableFaceNode(helper.absolutePos(new BlockPos(4, 1, 4)), Direction.WEST, CableTier.HV);
        var subMvPort = new CableFaceNode(helper.absolutePos(new BlockPos(2, 1, 4)), Direction.EAST, CableTier.MV);
        var consumerPort = new CableFaceNode(helper.absolutePos(new BlockPos(2, 1, 6)), Direction.EAST, CableTier.MV);
        int[] stablePowerTicks = {0};
        PowerFixtureReadiness.await(helper, level, 500, () -> {
                     var meter = PowerRuntime.mvPowerMeter(level, consumer);
                     boolean ready = PowerFixtureReadiness.topologyReadyWithKnownPorts(level, sourcePort, subHvPort,
                             subMvPort, consumerPort) && level.getBlockEntity(receiver) == consumer
                             && meter.known() && meter.deliveredWatts() == 5_000
                             && consumer.graphKnown && consumer.allocation == 5_000;
                    stablePowerTicks[0] = ready ? stablePowerTicks[0] + 1 : 0;
                    return stablePowerTicks[0] >= 10;
                      },
                () -> "meter=" + PowerRuntime.mvPowerMeter(level, consumer)
                        + ", " + PowerRuntime.lastSolveDiagnostic(level),
                () -> {
                    require(PowerRuntime.mvPowerMeter(level, consumer).deliveredWatts() == 5_000,
                            "actual MV allocation reaches the indexed runtime receiver: meter="
                                    + PowerRuntime.mvPowerMeter(level, consumer) + ", callback=" + consumer.allocation
                                    + ", callbackKnown=" + consumer.graphKnown + ", callbackSampleTick=" + consumer.sampleTick
                                    + ", receiverIdentityCurrent=" + (level.getBlockEntity(receiver) == consumer)
                                    + ", topologyGeneration=" + PowerGraphService.topologyGeneration(level)
                                    + ", topology=" + PowerGraphService.state(level, sourcePort) + "/"
                                    + PowerGraphService.state(level, subHvPort) + "/"
                                    + PowerGraphService.state(level, subMvPort) + "/"
                                    + PowerGraphService.state(level, consumerPort) + ", "
                                    + PowerRuntime.lastSolveDiagnostic(level));
                    require(consumer.graphKnown && consumer.allocation == 5_000,
                            "the same authoritative solve callback receives the actual allocation");
                    FixtureNode replacement = new FixtureNode(receiver, level.getBlockState(receiver), false, true);
                    level.setBlockEntity(replacement);
                    PowerRuntime.deviceChanged(level, receiver);
                    require(!PowerRuntime.mvPowerMeter(level, replacement).known(),
                            "replacement identity cannot inherit the old solve meter");
                    require(!PowerRuntime.mvPowerMeter(level, consumer).known(),
                            "removed/replaced identity cannot retain a grant");
                    require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, 2)),
                            Direction.EAST, CableTier.MV), "hybrid node MV receiver cable");
                    require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, 3)),
                            Direction.EAST, CableTier.MV), "hybrid MV cable segment");
                    FixtureNode hybrid = new FixtureNode(source, level.getBlockState(source), true, true);
                    level.setBlockEntity(hybrid);
                    PowerRuntime.deviceChanged(level, source);
                    var hybridMvPort = new CableFaceNode(helper.absolutePos(new BlockPos(2, 1, 2)),
                            Direction.EAST, CableTier.MV);
                    int[] stableHybridTicks = {0};
                     PowerFixtureReadiness.await(helper, level, 300, () -> {
                                boolean ready = PowerFixtureReadiness.topologyReadyWithKnownPorts(level, sourcePort, subHvPort,
                                    subMvPort, consumerPort, hybridMvPort)
                                    && !PowerRuntime.mvPowerMeter(level, hybrid).known()
                                    && PowerRuntime.mvPowerMeter(level, replacement).known()
                                    && PowerRuntime.mvPowerMeter(level, replacement).deliveredWatts() == 0
                                    && !hybrid.graphKnown && hybrid.allocation == 0;
                                stableHybridTicks[0] = ready ? stableHybridTicks[0] + 1 : 0;
                                return stableHybridTicks[0] >= 10;
                             },
                             () -> "hybrid=" + PowerRuntime.mvPowerMeter(level, hybrid)
                                     + ", receiver=" + PowerRuntime.mvPowerMeter(level, replacement)
                                     + ", " + PowerRuntime.lastSolveDiagnostic(level),
                            () -> {
                                require(!hybrid.graphKnown && hybrid.allocation == 0,
                                        "mixed-role node receives neither MV grant nor graph-known meter");
                                require(PowerRuntime.mvPowerMeter(level, replacement).deliveredWatts() == 0,
                                        "mixed-role source cannot inject an offer to downstream MV load");
                                helper.succeed();
                     });
                });
    }

    /** Test-only provider fixture on existing registered BE types; no shipping fake machine/block. */
    private static final class FixtureNode extends PowerDeviceBlockEntity implements PowerRuntimeNode {
        private final boolean supplier;
        private final boolean receiver;
        private final SupplierOffer offer = new SupplierOffer(41_300, 41_300);
        private double allocation;
        private boolean graphKnown;
        private long sampleTick = Long.MIN_VALUE;

        private FixtureNode(BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                            boolean supplier, boolean receiver) {
            super(pos, state);
            this.supplier = supplier;
            this.receiver = receiver;
        }

        @Override public List<DevicePort> powerRuntimePorts() {
            var ports = new java.util.ArrayList<DevicePort>(2);
            if (supplier) ports.add(new DevicePort(getBlockPos(), Direction.EAST, CableTier.HV));
            if (receiver) ports.add(new DevicePort(getBlockPos(), Direction.WEST, CableTier.MV));
            return List.copyOf(ports);
        }
        @Override public SupplierOffer powerSupplierOffer() { return supplier ? offer : null; }
        @Override public Double mvPowerDemandWatts() { return receiver ? 5_000.0 : null; }
        @Override public void receiveMvPowerAllocation(double actualWatts, boolean known, long sampleTick) {
            allocation = actualWatts;
            graphKnown = known;
            this.sampleTick = sampleTick;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
