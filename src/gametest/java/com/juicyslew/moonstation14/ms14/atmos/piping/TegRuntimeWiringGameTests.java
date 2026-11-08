package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestTags({"atmosphere", "teg", "teg_runtime_wiring"})
@PrefixGameTestTemplate(false)
public final class TegRuntimeWiringGameTests {
    private TegRuntimeWiringGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void blockEntityDispatchProcessesOnlyPaidIntervalAndRejectsBrokenAssembly(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos northPos = corePos.relative(Direction.NORTH);
        BlockPos southPos = corePos.relative(Direction.SOUTH);
        var lease = FixtureChunkTickets.around(helper, new BlockPos(3, 1, 3), new BlockPos(5, 1, 5), 1);
        place(level, corePos, ModBlocks.TEG_CENTER.get(), Direction.NORTH);
        place(level, northPos, ModBlocks.TEG_CIRCULATOR.get(), Direction.EAST);
        place(level, southPos, ModBlocks.TEG_CIRCULATOR.get(), Direction.WEST);
        helper.runAfterDelay(35, () -> {
            try {
                var core = host(level, corePos);
                var north = host(level, northPos);
                var south = host(level, southPos);
                var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
                seed(saved, north, northPos, "inlet", new GasMixture(Map.of(GasType.OXYGEN, 5d), 900));
                seed(saved, north, northPos, "outlet", GasMixture.vacuum());
                seed(saved, south, southPos, "inlet", new GasMixture(Map.of(GasType.NITROGEN, 5d), 100));
                seed(saved, south, southPos, "outlet", GasMixture.vacuum());
                var inletKey=new PipeTopology.NodeKey(northPos, north.gasPorts().stream().filter(p -> p.id().equals("inlet")).findFirst().orElseThrow().layer(), "inlet");
                require(saved.snapshot().nodeShare(inletKey).moles(GasType.OXYGEN) == 5d, "finite hot oxygen seed admitted");

                long now = PowerRuntime.currentSampleTick(level);
                require(now >= 20, "wait for authoritative sample clock");
                core.receiveApcPowerAllocation(1000, true, now - 20);
                core.receiveApcPowerAllocation(1000, true, now);
                AtmosMachineBlockEntity.tick(level, core);
                var processed = core.tegRuntimeState();
                require(processed.status() == com.juicyslew.moonstation14.ms14.atmos.teg.TegRuntime.Status.PROCESSED,
                        "the actual BE ticker dispatches TEG processor after a paid interval");
                require(processed.lastGeneratedJoules() > 0 && processed.cumulativeGeneratedJoules() > 0,
                        "paid interval produces positive TEG energy");
                require(core.powerSupplierOffer() != null && core.powerSupplierOffer().ratedMaximumWatts() > 0,
                        "processed assembly publishes a fresh HV offer");

                double generated = processed.cumulativeGeneratedJoules();
                for (long elapsed : new long[]{1, 5, 12, 20}) {
                    var bounded = core.powerSupplierOffer(elapsed);
                    require(bounded != null && Double.isFinite(bounded.currentWatts())
                                    && bounded.currentWatts() >= 0
                                    && bounded.currentWatts() <= bounded.ratedMaximumWatts()
                                    && bounded.currentWatts() * elapsed / 20d <= generated + Math.ulp(generated) * 8,
                            "duration-bounded source offer stays within paid generated energy");
                }
                var noArgOffer = core.powerSupplierOffer();
                var boundedOffer = core.powerSupplierOffer(20);
                require(noArgOffer != null && boundedOffer != null
                                && boundedOffer.currentWatts() > 0
                                && noArgOffer.currentWatts() == boundedOffer.currentWatts(),
                        "default offer uses one solve interval");
                double offeredWatts = boundedOffer.currentWatts();
                double repeatedOffer = core.powerSupplierOffer(20).currentWatts();
                require(repeatedOffer == offeredWatts, "offer queries do not consume generated energy");
                core.receivePowerSupplyAllocation(offeredWatts, true, 20, now);
                require(core.hvAllocationKnown()
                                && core.hvDeliveredJoules() == offeredWatts
                                && core.hvDeliveredJoules() <= generated + Math.ulp(generated) * 8,
                        "actual source callback settles only paid generated energy");
                require(core.powerSupplierOffer(20).currentWatts() == 0,
                        "settled source offer is consumed until another thermal commit");
                core.receivePowerSupplyAllocation(offeredWatts, true, 20, now);
                require(!core.hvAllocationKnown() && core.hvDeliveredWatts() == offeredWatts
                                && core.hvDeliveredJoules() == offeredWatts,
                        "repeat positive callback cannot settle energy twice");
                core.receivePowerSupplyAllocation(0, false, 20, now);
                require(!core.hvAllocationKnown() && core.hvDeliveredWatts() == 0
                                && core.hvDeliveredJoules() == 0,
                        "unknown zero callback clears the receipt");
                AtmosMachineBlockEntity.tick(level, core);
                require(core.tegRuntimeState().cumulativeGeneratedJoules() == generated,
                        "another BE tick cannot repeat work without another payment");

                level.setBlock(southPos, ModBlocks.TEG_CIRCULATOR.get().defaultBlockState()
                        .setValue(AtmosMachineBlock.FACING, Direction.NORTH), 3);
                var changedCore = host(level, corePos);
                AtmosMachineBlockEntity.tick(level, changedCore);
                require(changedCore.tegRuntimeState().status()
                                == com.juicyslew.moonstation14.ms14.atmos.teg.TegRuntime.Status.INVALID_ASSEMBLY,
                        "misoriented circulator invalidates assembly");
                require(changedCore.powerSupplierOffer().ratedMaximumWatts() == 0,
                        "invalid assembly fails closed for HV export");
                cleanup(level, corePos, northPos, southPos);
                lease.close();
                helper.succeed();
            } catch (Throwable failure) {
                cleanup(level, corePos, northPos, southPos);
                lease.close();
                helper.fail("TEG runtime wiring fixture failed: " + failure.getMessage());
            }
        });
    }

    private static void place(ServerLevel level, BlockPos pos, AtmosMachineBlock block, Direction facing) {
        level.setBlock(pos, block.defaultBlockState().setValue(AtmosMachineBlock.FACING, facing), 3);
    }

    private static void cleanup(ServerLevel level, BlockPos core, BlockPos north, BlockPos south) {
        for (BlockPos pos : new BlockPos[]{core, north, south})
            if (level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity)
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
    }

    private static AtmosMachineBlockEntity host(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity host) return host;
        throw new GameTestAssertException("machine block entity missing at " + pos);
    }

    private static void seed(PipeGasInventorySavedData saved, AtmosMachineBlockEntity host,
                                         BlockPos pos, String id, GasMixture gas) {
        var port = host.gasPorts().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                .orElseThrow(() -> new GameTestAssertException("admitted machine port missing: " + id));
        var key = new PipeTopology.NodeKey(pos, port.layer(), port.id());
        PipeGasInventory before = saved.snapshot();
        var entry = before.get(key).orElseThrow(() -> new GameTestAssertException("port inventory not admitted: " + id));
        GasMixture networkSeed=new GasMixture(gas.gasMoles(),gas.temperatureKelvin(),before.mixture(key).volumeCubicMeters());
        var after = before.withMixture(key, entry.incarnation(), entry.version(), networkSeed);
        require(saved.replace(before.revision(), before, after), "finite port gas seed CAS failed for " + id);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
