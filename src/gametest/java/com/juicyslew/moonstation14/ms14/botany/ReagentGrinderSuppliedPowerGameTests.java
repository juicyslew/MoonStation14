package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestTags({"botany", "botany-grinder-power"})
@PrefixGameTestTemplate(false)
public final class ReagentGrinderSuppliedPowerGameTests {
    private ReagentGrinderSuppliedPowerGameTests() { }

    @GameTest(template = "power_receiver_tall_clear", templateNamespace = "moonstation14", timeoutTicks = 220)
    public static void suppliedApcPowerIsLostDuringTimedGrinding(GameTestHelper helper) {
        var power = AirAlarmPowerFixture.place(helper);
        ServerLevel level = power.level();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 12, 8));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.REAGENT_GRINDER.get().defaultBlockState(), 3);
        helper.runAfterDelay(2, () -> {
            var grinder = (ReagentGrinderBlockEntity) level.getBlockEntity(pos);
            var ledger = LevelItemLocationLedgers.forLevel(level);
            var beaker = new ItemStack(ModItems.BEAKER.get());
            beaker.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of()));
            var wheat = ProduceFactory.createHarvest(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), 5);
            var beakerToken = new ItemToken("grinder-power-beaker-" + UUID.randomUUID());
            var wheatToken = new ItemToken("grinder-power-wheat-" + UUID.randomUUID());
            var beakerOrigin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
            var wheatOrigin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
            require(ledger.register(beakerToken, beakerOrigin).accepted() && ledger.register(wheatToken, wheatOrigin).accepted(), "register uniquely owned items");
            var beakerSlot = grinder.beakerSlot();
            var wheatSlot = grinder.chamberSlots().get(0);
            require(beakerSlot.insert(0, ledger.snapshot().revision(), beakerToken, beakerOrigin, beaker), "insert beaker through owned slot");
            require(wheatSlot.insert(0, ledger.snapshot().revision(), wheatToken, wheatOrigin, wheat), "insert wheat through owned slot");
            PowerFixtureReadiness.await(helper, level, 55,
                    () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level,
                            new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(power.cable(), net.minecraft.core.Direction.WEST,
                                    com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC)),
                    () -> "APC topology ready before timed program", () -> {
                        require(grinder.isPowered(), "idle zero-demand grinder is available");
                        require(grinder.startTrusted(grinder.startProof(), level.getGameTime(), true), "start registered grinder program");
                        long started = level.getGameTime();
                        PowerFixtureReadiness.await(helper, level, 35,
                                () -> PowerRuntime.processingMeter(level, grinder).known()
                                        && PowerRuntime.processingMeter(level, grinder).deliveredWatts() == 750,
                                () -> "await actual 750 W allocation; meter=" + PowerRuntime.processingMeter(level, grinder), () -> {
                                    require(grinder.isPowered(), "fresh allocated 750 W makes grinder available");
                                    var apc = (com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity) level.getBlockEntity(power.apc());
                                    require(apc != null && apc.toggleBreakerAuthorized(true, 0), "open APC breaker through power API");
                                    require(!grinder.isPowered(), "breaker loss makes active grinder unavailable");
                                    long remaining = Math.max(1, started + 70 - level.getGameTime());
                                    helper.runAfterDelay((int) remaining, () -> {
                                        long cents = beakerSlot.snapshot().stack().get(ModDataComponents.REAGENT.get()).centContents().values().stream().mapToLong(Long::longValue).sum();
                                        require(level.getGameTime() - started == 70 && cents == 650 && wheatSlot.snapshot().stack().isEmpty(),
                                                "clock completes at 70 ticks despite power loss, transferring 650 cents and consuming wheat");
                                        require(ledger.snapshot().locationOf(wheatToken).isEmpty(), "source token retired exactly once");
                                        helper.runAfterDelay(3, () -> {
                                            require(beakerSlot.snapshot().stack().get(ModDataComponents.REAGENT.get()).centContents().values().stream().mapToLong(Long::longValue).sum() == 650,
                                                    "completion is not repeated");
                                            helper.succeed();
                                        });
                                    });
                                });
                    });
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
