package com.juicyslew.moonstation14.gametest.power.runtime;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlock;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
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

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "power"})
@PrefixGameTestTemplate(false)
public final class DisposalPowerGameTests {
    private DisposalPowerGameTests() { }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_power_clear", timeoutTicks = 360)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"machine-model-regression"})
    public static void liveApcSupplyMetersDisposalUnitAndLossRevokesPower(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(19, 1, 18));
        BlockPos substation = helper.absolutePos(new BlockPos(19, 1, 20));
        BlockPos apc = helper.absolutePos(new BlockPos(19, 1, 22));
        BlockPos unitPos = helper.absolutePos(new BlockPos(19, 1, 24));
        for (int z = 2; z <= 8; z++) level.setBlock(helper.absolutePos(new BlockPos(20, 1, z + 16)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++) level.setBlock(helper.absolutePos(new BlockPos(18, 1, z + 16)), Blocks.STONE.defaultBlockState(), 3);
        place(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        place(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        place(level, apc, PowerDeviceKind.APC, Direction.WEST);
        for (int z = 2; z <= 4; z++) require(CableStorage.place(level, helper.absolutePos(new BlockPos(20, 1, z + 16)), Direction.WEST, CableTier.HV), "HV cable");
        for (int z = 4; z <= 6; z++) require(CableStorage.place(level, helper.absolutePos(new BlockPos(18, 1, z + 16)), Direction.EAST, CableTier.MV), "MV cable");
        for (int z = 6; z <= 8; z++) require(CableStorage.place(level, helper.absolutePos(new BlockPos(20, 1, z + 16)), Direction.WEST, CableTier.APC), "APC cable");
        level.setBlock(unitPos, ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        DisposalUnitBlockEntity unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
        require(unit != null, "registered disposal unit BE");
        ((PowerDeviceBlockEntity) level.getBlockEntity(apc)).setEnergyJoules(500_000);
        awaitMeter(helper, level, unit, unitPos, source, apc, 0);
    }

    private static void awaitMeter(GameTestHelper helper, ServerLevel level, DisposalUnitBlockEntity unit,
                                   BlockPos unitPos, BlockPos source, BlockPos apc, int elapsed) {
        var meter = PowerRuntime.disposalUnitMeter(level, unit);
        if (!meter.known()) {
            require(elapsed < 240, "live disposal meter solve timed out: " + meter + ", " + PowerRuntime.lastSolveDiagnostic(level));
            helper.runAfterDelay(5, () -> awaitMeter(helper, level, unit, unitPos, source, apc, elapsed + 5));
            return;
        }
        require(unit.prototype() != null && unit.prototype().powerWatts() == 1000
                && meter.deliveredWatts() == 1000, "resolved unit requests and receives 1000 W: " + meter);
        require(level.getBlockState(unitPos).getValue(DisposalUnitBlock.POWERED), "unit powered from fresh meter");
        level.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
        helper.runAfterDelay(60, () -> {
            var battery = PowerRuntime.disposalUnitMeter(level, unit);
            require(battery.known() && battery.deliveredWatts() == 1000,
                    "charged APC battery legitimately carries unit load after upstream cut: " + battery);
            require(level.getBlockState(unitPos).getValue(DisposalUnitBlock.POWERED), "battery-backed unit remains powered");
            PowerDeviceBlockEntity apcDevice = (PowerDeviceBlockEntity) level.getBlockEntity(apc);
            long breakerRevision = apcDevice.breakerRevision();
            Player player = new Player(level, apc, 0, new GameProfile(UUID.randomUUID(), "disposal-power-test")) {
                @Override public boolean isCreative() { return true; }
                @Override public boolean isSpectator() { return false; }
            };
            player.getAbilities().instabuild = true;
            require(apcDevice.toggleBreaker(player, 0), "authorized nearby test player opens APC breaker");
            require(!apcDevice.breakerClosed() && apcDevice.breakerRevision() == breakerRevision + 1,
                    "authorized breaker transition advances revision");
            var tripped = PowerRuntime.disposalUnitMeter(level, unit);
            require(!tripped.known(), "breaker revision immediately revokes stale full-power meter: " + tripped);
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> {
                require(!level.getBlockState(unitPos).getValue(DisposalUnitBlock.POWERED),
                        "next unit tick projects fresh unpowered state after APC breaker opens");
                helper.succeed();
            });
        });
    }

    private static void place(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
        if (kind == PowerDeviceKind.APC) {
            require(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement.placeApc(level, pos, facing), "place supported APC");
            return;
        }
        var block = switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case APC -> ModBlocks.APC.get();
            default -> throw new IllegalArgumentException("not a supply device");
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
