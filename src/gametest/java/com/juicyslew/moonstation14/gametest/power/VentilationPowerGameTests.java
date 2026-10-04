package com.juicyslew.moonstation14.gametest.power;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestTags({"power", "ventilation"})
@PrefixGameTestTemplate(false)
public final class VentilationPowerGameTests {
    private VentilationPowerGameTests() { }

    @GameTest(template = "power_live_chain_clear", timeoutTicks = 240, templateNamespace = "moonstation14")
    public static void loadedInheritedDefinitionsDriveDefaultsAndReceiver(GameTestHelper helper) {
        var catalog = PrototypeRuntime.serverVentilationDevices();
        var ventDefinition = catalog.get(ModVentilationDevices.VENT);
        var scrubberDefinition = catalog.get(ModVentilationDevices.SCRUBBER);
        require(ventDefinition != null && scrubberDefinition != null,
                "resource reload publishes both inherited concrete definitions");
        Fixture fixture = connectVent(helper);
        VentilationDeviceBlockEntity vent = fixture.vent();
        require(vent.prototype() == ventDefinition, "live BE resolves published vent definition");
        var settings = vent.config().settings();
        require(settings.externalPressureBoundKPa() == ventDefinition.gasVentPump().externalPressureBoundKPa()
                && settings.targetPressureChangeKPaPerSecond() == ventDefinition.gasVentPump().targetPressureChangeKPaPerSecond()
                && settings.targetPressureKPa() == ventDefinition.gasVentPump().externalPressureBoundKPa()
                && settings.selectedLayer().name().equals(ventDefinition.pipeLane().name()),
                "new vent defaults derive from inherited definition");
        BlockPos scrubberPos = helper.absolutePos(new BlockPos(4, 1, 10));
        fixture.level().setBlock(scrubberPos, ModBlocks.GAS_SCRUBBER.get().defaultBlockState(), 3);
        VentilationDeviceBlockEntity scrubber = (VentilationDeviceBlockEntity) fixture.level().getBlockEntity(scrubberPos);
        require(scrubber != null && scrubber.prototype() == scrubberDefinition
                && scrubber.config().settings().volumeRateM3PerSecond() == scrubberDefinition.gasVentScrubber().volumeRateM3PerSecond()
                && scrubber.config().settings().filteredGases().equals(java.util.Set.copyOf(scrubberDefinition.gasVentScrubber().filteredGases()))
                && scrubber.selectedLayer().name().equals(scrubberDefinition.pipeLane().name()),
                "new scrubber defaults derive from inherited definition");
        helper.runAfterDelay(100, () -> {
            require(PowerRuntime.ventilationMeter(fixture.level(), vent).known()
                    && PowerRuntime.ventilationMeter(fixture.level(), scrubber).known()
                    && PowerRuntime.ventilationMeter(fixture.level(), scrubber).deliveredWatts()
                        == scrubberDefinition.apcPowerReceiver().requestedWatts(),
                    "prototype reach and requested watts supply the graph receiver and solve");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void unconnectedVentHasUnknownPowerMeter(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.GAS_VENT.get().defaultBlockState(), 3);
        VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(pos);
        require(vent != null, "vent block entity exists");
        require(!PowerRuntime.ventilationMeter(level, vent).known(), "unconnected vent power is unknown");
        helper.succeed();
    }

    @GameTest(template = "power_live_chain_clear", timeoutTicks = 240, templateNamespace = "moonstation14")
    public static void poweredVentMeterTracksBreakerAndReplacement(GameTestHelper helper) {
        Fixture fixture = connectVent(helper);
        helper.runAfterDelay(100, () -> {
            ServerLevel level = fixture.level();
            VentilationDeviceBlockEntity vent = fixture.vent();
            require(PowerRuntime.ventilationMeter(level, vent).known(), "powered vent meter is known after solve");
            require(PowerRuntime.ventilationMeter(level, vent).deliveredWatts() == 5, "powered vent receives 5 W meter sample");
            PowerDeviceBlockEntity apcEntity = (PowerDeviceBlockEntity) level.getBlockEntity(fixture.apc());
            Player player = creativePlayer(level, fixture.apc());
            require(apcEntity.toggleBreaker(player, 0), "open APC breaker");
            require(!PowerRuntime.ventilationMeter(level, vent).known(), "open breaker immediately invalidates meter");
            require(apcEntity.toggleBreaker(player, 0), "close APC breaker");
            helper.runAfterDelay(40, () -> {
                require(PowerRuntime.ventilationMeter(level, vent).known(), "meter returns after fresh solve");
                level.setBlock(fixture.ventPos(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(fixture.ventPos(), ModBlocks.GAS_VENT.get().defaultBlockState().setValue(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock.FACING, Direction.NORTH), 3);
                VentilationDeviceBlockEntity replacement = (VentilationDeviceBlockEntity) level.getBlockEntity(fixture.ventPos());
                require(replacement != null && replacement != vent, "replacement vent has a fresh block entity");
                require(!PowerRuntime.ventilationMeter(level, vent).known(), "removed vent meter is unknown");
                require(!PowerRuntime.ventilationMeter(level, replacement).known(), "replacement awaits fresh solve");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "power_live_chain_clear", timeoutTicks = 240, templateNamespace = "moonstation14")
    public static void paidWorkCallbackIsSingleUseAndInvalidated(GameTestHelper helper) {
        Fixture fixture = connectVent(helper);
        helper.succeedWhen(() -> {
            ServerLevel level = fixture.level();
            VentilationDeviceBlockEntity vent = fixture.vent();
            require(PowerRuntime.ventilationMeter(level, vent).known()
                    && PowerRuntime.peekCompletedVentilationSeconds(level, vent) >= 1,
                    "a completed solve authorizes at least one second of paid work");
            int[] calls = {0};
            double[] seconds = {0};
            require(!PowerRuntime.performCompletedVentilationWork(level, vent, elapsed -> {
                calls[0]++;
                seconds[0] = elapsed;
                return false;
            }), "callback may decline gas transfer");
            require(calls[0] == 1 && seconds[0] == 1,
                    "callback runs once with solver-derived elapsed time");
            require(!PowerRuntime.performCompletedVentilationWork(level, vent, elapsed -> {
                calls[0]++;
                return true;
            }) && calls[0] == 1, "consumed authorization cannot be replayed");
            PowerDeviceBlockEntity apcEntity = (PowerDeviceBlockEntity) level.getBlockEntity(fixture.apc());
            require(apcEntity.toggleBreaker(creativePlayer(level, fixture.apc()), 0), "open APC breaker");
            require(!PowerRuntime.ventilationMeter(level, vent).known(), "open breaker immediately invalidates meter");
            require(!PowerRuntime.performCompletedVentilationWork(level, vent, elapsed -> {
                calls[0]++;
                return true;
            }) && calls[0] == 1,
                    "open breaker invalidates budget immediately");
            VentilationDeviceBlockEntity oldVent = vent;
            level.setBlock(fixture.ventPos(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(fixture.ventPos(), ModBlocks.GAS_VENT.get().defaultBlockState().setValue(
                    com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock.FACING, Direction.NORTH), 3);
            VentilationDeviceBlockEntity replacement = (VentilationDeviceBlockEntity) level.getBlockEntity(fixture.ventPos());
            require(replacement != null && replacement != oldVent
                    && !PowerRuntime.performCompletedVentilationWork(level, replacement, elapsed -> true),
                    "replacement cannot use the old vent authorization");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240, templateNamespace = "moonstation14")
    public static void localExtensionReceiverRespectsRadiusAndCableCuts(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        // Keep this whole chain above cables in neighboring GameTests' floor-level fixtures.
        int y = 12;
        BlockPos source = helper.absolutePos(new BlockPos(3, y, 2));
        BlockPos substation = helper.absolutePos(new BlockPos(3, y, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, y, 6));
        BlockPos within = helper.absolutePos(new BlockPos(4, y, 10));
        BlockPos lastCable = helper.absolutePos(new BlockPos(4, y, 7));
        BlockPos remainingCable = helper.absolutePos(new BlockPos(4, y, 6));
        for (int z = 2; z <= 7; z++)
            level.setBlock(helper.absolutePos(new BlockPos(4, y, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(2, y, z)), Blocks.STONE.defaultBlockState(), 3);
        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        for (int z = 2; z <= 4; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, y, z)), Direction.WEST, CableTier.HV), "place elevated HV cable");
        for (int z = 4; z <= 6; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, y, z)), Direction.EAST, CableTier.MV), "place elevated MV cable");
        for (int z = 6; z <= 7; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, y, z)), Direction.WEST, CableTier.APC), "place elevated APC cable");
        // nodesNear fails closed if even an empty chunk intersecting the radius is unloaded.
        // Include the full source chain and a one-block cable-frontier margin as well.
        for (int cx = (Math.min(source.getX(), within.getX()) - 4) >> 4;
                  cx <= (Math.max(source.getX(), within.getX()) + 4) >> 4; cx++)
            for (int cz = (Math.min(source.getZ(), within.getZ()) - 4) >> 4;
                     cz <= (Math.max(source.getZ(), within.getZ()) + 4) >> 4; cz++)
                level.getChunk(cx, cz);
        // The last APC cable is exactly three blocks away; its predecessor is four away.
        level.setBlock(within, ModBlocks.GAS_SCRUBBER.get().defaultBlockState(), 3);
        VentilationDeviceBlockEntity scrubber = (VentilationDeviceBlockEntity) level.getBlockEntity(within);
        require(scrubber != null, "scrubber exists at radius three");
        awaitExtensionSolve(helper, scrubber, new CableFaceNode(lastCable, Direction.WEST, CableTier.APC),
                new CableFaceNode(remainingCable, Direction.WEST, CableTier.APC), 0);
    }

    private static void awaitExtensionSolve(GameTestHelper helper, VentilationDeviceBlockEntity scrubber,
                                            CableFaceNode intended, CableFaceNode remaining, int elapsed) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        CableFaceNode selected = PowerGraphService.nearestApcExtensionNode(level, scrubber.getBlockPos(), 3);
        if (selected != null)
            require(selected.equals(intended), "radius-three receiver selects the intended APC cable, not a foreign node: " + selected);
        var meter = PowerRuntime.ventilationMeter(level, scrubber);
        if (meter.known() && meter.deliveredWatts() == 5) {
            require(intended.equals(selected), "paid solve uses the intended radius-three APC receiver");
            require(CableStorage.remove(level, intended.host(), intended.face(), CableTier.APC) == CableTier.APC,
                    "cut nearest APC cable");
            require(!PowerRuntime.ventilationMeter(level, scrubber).known(), "cut invalidates old sample immediately");
            helper.runAfterDelay(45, () -> {
                require(CableStorage.has(level, remaining.host(), remaining.face(), CableTier.APC),
                        "radius-four APC cable remains after cut");
                require(remaining.equals(PowerGraphService.nearestApcExtensionNode(level, scrubber.getBlockPos(), 4)),
                        "remaining APC cable is known at radius four");
                require(PowerGraphService.nearestApcExtensionNode(level, scrubber.getBlockPos(), 3) == null,
                        "no APC receiver remains within radius three after cut");
                require(!PowerRuntime.ventilationMeter(level, scrubber).known(),
                        "remaining APC cable at radius four cannot power scrubber; meter="
                                + PowerRuntime.ventilationMeter(level, scrubber) + ", lastSolve="
                                + PowerRuntime.lastSolveDiagnostic(level));
                helper.succeed();
            });
            return;
        }
        require(elapsed < 175, "radius-three extension receives a completed 5 W solve without a DOWN cable; meter="
                + meter + ", topologyReady=" + PowerGraphService.topologyReady(level)
                + ", nearby=" + PowerGraphService.nodesNear(level, scrubber.getBlockPos(), 3, CableTier.APC)
                + ", nearest=" + selected
                + ", lastSolve=" + PowerRuntime.lastSolveDiagnostic(level));
        helper.runAfterDelay(5, () -> awaitExtensionSolve(helper, scrubber, intended, remaining, elapsed + 5));
    }

    private static Fixture connectVent(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos substation = helper.absolutePos(new BlockPos(3, 1, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, 1, 6));
        BlockPos ventPos = helper.absolutePos(new BlockPos(3, 1, 8));
        for (int z = 2; z <= 8; z++)
            level.setBlock(helper.absolutePos(new BlockPos(4, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(2, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(ventPos, ModBlocks.GAS_VENT.get().defaultBlockState().setValue(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock.FACING, Direction.NORTH), 3);
        level.setBlock(helper.absolutePos(new BlockPos(3, 0, 8)), Blocks.STONE.defaultBlockState(), 3);
        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        for (int z = 2; z <= 4; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.HV), "place HV cable");
        for (int z = 4; z <= 6; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, z)), Direction.EAST, CableTier.MV), "place MV cable");
        for (int z = 6; z <= 7; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.APC), "place APC cable");

        VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(ventPos);
        require(vent != null, "vent block entity exists");
        return new Fixture(level, vent, apc, ventPos);
    }

    private static Player creativePlayer(ServerLevel level, BlockPos pos) {
        Player player = new Player(level, pos, 0, new GameProfile(UUID.randomUUID(), "vent-power-test")) {
            @Override public boolean isCreative() { return true; }
            @Override public boolean isSpectator() { return false; }
        };
        player.getAbilities().instabuild = true;
        return player;
    }

    private record Fixture(ServerLevel level, VentilationDeviceBlockEntity vent, BlockPos apc, BlockPos ventPos) { }

    private static void placeDevice(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
        var block = switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case APC -> ModBlocks.APC.get();
            default -> throw new IllegalArgumentException("Unsupported device in fixture: " + kind);
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
