package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@GameTestTags({"guns", "power", "owned-combat", "capacitor-recharger"})
@PrefixGameTestTemplate(false)
public final class CapacitorRechargerPowerGameTests {
    private CapacitorRechargerPowerGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", batch = "capacitor_recharger_persistence", timeoutTicks = 50)
    public static void reconstructionBeforeLevelAttachmentRestoresSlotAndBatteryClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.WEAPON_CAPACITOR_RECHARGER.get().defaultBlockState(), 3);
        registerCleanup(helper, level, pos);
        helper.runAfterDelay(2, () -> {
            var original = (WeaponCapacitorRechargerBlockEntity) level.getBlockEntity(pos);
            var item = new ItemStack(ModItems.WEAPON_ENERGY_SHOTGUN.get());
            item.set(ModDataComponents.BATTERY_STATE.get(), new BatteryState(173));
            var owned = original.slot();
            require(owned.initialize(owned.snapshot().revision(), item), "machine admits item through owned slot");
            var before = owned.snapshot();
            var ledger = LevelItemLocationLedgers.forLevel(level);
            int claimsBefore = ledger.snapshot().size();
            var save = original.saveWithFullMetadata(level.registryAccess());
            require(owned.suspend(), "old runtime slot releases its claim for unload/reload reconstruction");

            // Deliberately deserialize before assigning the entity to the live ServerLevel.
            var restored = new WeaponCapacitorRechargerBlockEntity(pos, level.getBlockState(pos));
            restored.loadWithComponents(save, level.registryAccess());
            require(restored.getLevel() == null, "test exercises normal pre-attachment NBT loading");
            level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).removeBlockEntity(pos);
            level.setBlockEntity(restored);
            var next = restored.slot();
            require(next != null && restored.placementIdentity().equals(before.owner()), "saved owner identity restores before slot construction");
            var after = next.snapshot();
            require(before.token().equals(after.token()) && ItemStack.matches(before.stack(), after.stack()),
                    "exact stack, charge component, and item token survive reload");
            require(ledger.snapshot().size() == claimsBefore
                            && ledger.snapshot().locationOf(after.token()).orElseThrow().equals(new ItemLocation.MachineSlot(after.owner(), "charger_slot")),
                    "restored item has exactly its original machine-slot ledger claim");
            helper.succeed();
        });
    }

    @GameTest(template = "power_receiver_tall_clear", templateNamespace = "moonstation14", batch = "capacitor_recharger_integral_power", timeoutTicks = 340)
    public static void actualApcAllocationGatesIntegralAndNestedCellCharging(GameTestHelper helper) {
        var power = AirAlarmPowerFixture.place(helper);
        ServerLevel level = power.level();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 12, 8));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.WEAPON_CAPACITOR_RECHARGER.get().defaultBlockState(), 3);
        registerCleanup(helper, level, pos);
        helper.runAfterDelay(2, () -> {
            var machine = (WeaponCapacitorRechargerBlockEntity) level.getBlockEntity(pos);
            java.util.function.Supplier<com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.VentMeterObservation> meter =
                    () -> com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, machine);
            PowerFixtureReadiness.await(helper, level, 60,
                    () -> meter.get().known() && meter.get().deliveredWatts() == 1d,
                    () -> "empty charger has fresh idle demand: " + meter.get(), () -> {
                var ledger = LevelItemLocationLedgers.forLevel(level);
                var slot = machine.slot();
                var gun = new ItemStack(ModItems.WEAPON_ENERGY_SHOTGUN.get());
                gun.set(ModDataComponents.BATTERY_STATE.get(), new BatteryState(0));
                require(slot.initialize(slot.snapshot().revision(), gun), "install integral battery item through owned slot");
                require(machine.prepareInsertedCharge(), "slot admission initializes once without replacing source charge");
                require(slot.snapshot().stack().get(ModDataComponents.BATTERY_STATE.get()).charge() == 0,
                        "depleted source charge remains depleted on admission");
                PowerFixtureReadiness.await(helper, level, 55,
                        () -> meter.get().known() && meter.get().deliveredWatts() == 20d,
                    () -> "charger receives full 20 W allocation: " + meter.get(), () -> captureTwentyAuthorizedTicks(
                            helper, level, machine, slot,
                            () -> slot.snapshot().stack().get(ModDataComponents.BATTERY_STATE.get()).charge(), window -> {
                        float after = window.after();
                        require(window.admittedTicks() == 20, "integral counts twenty authorized ticks: " + window.diagnostic());
                        require(window.endTick() - window.startTick() >= 20,
                                "integral advances span at least twenty physical ticks: " + window.diagnostic());
                        require(Math.abs(after - window.before() - window.expectedJoulesPerTick() * 20f) <= 2e-3f,
                                "integral total must equal resolved rate for twenty admissions: " + window.diagnostic());
                        var occupied = slot.snapshot();
                        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
                        var outgoing = slot.eject(occupied.revision(), ledger.snapshot().revision(), hand).orElseThrow();
                        machine.slotChanged();
                        require(slot.insert(slot.snapshot().revision(), ledger.snapshot().revision(), outgoing.token(), hand, outgoing.stack()),
                                "same identified item can leave and re-enter without losing charge");
                        machine.slotChanged(); machine.prepareInsertedCharge();
                        require(slot.snapshot().stack().get(ModDataComponents.BATTERY_STATE.get()).charge() == after,
                                "admission of an already initialized stack does not refill it");
                        var apc = (com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity) level.getBlockEntity(power.apc());
                        require(apc.toggleBreakerAuthorized(true, 0), "open APC breaker via authorized power API");
                        float stopped = slot.snapshot().stack().get(ModDataComponents.BATTERY_STATE.get()).charge();
                        PowerFixtureReadiness.await(helper, level, 32,
                                () -> !meter.get().known(), () -> "breaker loss invalidates full-power sample", () ->
                                helper.runAfterDelay(20, () -> {
                                    require(slot.snapshot().stack().get(ModDataComponents.BATTERY_STATE.get()).charge() == stopped,
                                            "unpowered ticks do not charge or bank elapsed time");
                                    helper.succeed();
                                }));
                    }));
            });
        });
    }

    @GameTest(template = "power_receiver_tall_clear", templateNamespace = "moonstation14", batch = "capacitor_recharger_cell_power", timeoutTicks = 420)
    public static void actualApcAllocationChargesSmallCellAndSvalinnOwnedNestedCell(GameTestHelper helper) {
        var power = AirAlarmPowerFixture.place(helper);
        ServerLevel level = power.level();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 12, 8));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.WEAPON_CAPACITOR_RECHARGER.get().defaultBlockState(), 3);
        registerCleanup(helper, level, pos);
        helper.runAfterDelay(2, () -> {
            var machine = (WeaponCapacitorRechargerBlockEntity) level.getBlockEntity(pos);
            var slot = machine.slot();
            var ledger = LevelItemLocationLedgers.forLevel(level);
            var cell = new ItemStack(ModItems.POWER_CELL_SMALL.get());
            cell.set(ModDataComponents.BATTERY_STATE.get(), new BatteryState(0));
            require(slot.initialize(slot.snapshot().revision(), cell), "install standalone actual small cell");
            require(machine.prepareInsertedCharge(), "initialize only fresh standalone cell state");
            require(slot.snapshot().stack().get(ModDataComponents.BATTERY_STATE.get()).charge() == 0f,
                    "standalone cell is not refilled on admission before power is sampled");
            java.util.function.Supplier<com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.VentMeterObservation> meter =
                    () -> com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, machine);
            PowerFixtureReadiness.await(helper, level, 60,
                    () -> meter.get().known() && meter.get().deliveredWatts() == 20d,
                    () -> "full cell charging demand allocated: " + meter.get(), () -> captureTwentyAuthorizedTicks(
                            helper, level, machine, slot,
                            () -> slot.snapshot().stack().get(ModDataComponents.BATTERY_STATE.get()).charge(), cellWindow -> {
                require(cellWindow.admittedTicks() == 20, "standalone cell counts twenty authorized ticks: " + cellWindow.diagnostic());
                require(cellWindow.endTick() - cellWindow.startTick() >= 20,
                        "standalone cell advances span at least twenty physical ticks: " + cellWindow.diagnostic());
                require(Math.abs(cellWindow.after() - cellWindow.before() - cellWindow.expectedJoulesPerTick() * 20f) <= 2e-3f,
                        "standalone cell total must equal resolved rate for twenty admissions: " + cellWindow.diagnostic());
                var old = slot.snapshot();
                var origin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
                slot.eject(old.revision(), ledger.snapshot().revision(), origin).orElseThrow();
                var nestedCell = new ItemStack(ModItems.POWER_CELL_SMALL.get());
                nestedCell.set(ModDataComponents.BATTERY_STATE.get(), new BatteryState(0));
                var svalinn = new ItemStack(ModItems.WEAPON_LASER_SVALINN.get());
                svalinn.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(), new GunPowerCellSlotState(Optional.of(nestedCell)));
                var svalinnToken = new ItemToken("charger-svalinn-" + UUID.randomUUID());
                var svalinnOrigin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
                require(ledger.register(svalinnToken, svalinnOrigin).accepted()
                                && slot.insert(slot.snapshot().revision(), ledger.snapshot().revision(), svalinnToken, svalinnOrigin, svalinn),
                        "insert Svalinn with its actual nested cell and distinct conserved stack identity");
                machine.slotChanged(); require(machine.prepareInsertedCharge(), "preserve nested cell's existing zero charge");
                var installed = slot.snapshot();
                var originalNested = installed.stack().get(ModDataComponents.POWER_CELL_SLOT_STATE.get()).cell().orElseThrow();
                require(originalNested.get(ModDataComponents.BATTERY_STATE.get()).charge() == 0f,
                        "nested cell remains depleted until its own powered measurement window");
                PowerFixtureReadiness.await(helper, level, 55,
                        () -> meter.get().known() && meter.get().deliveredWatts() == 20d,
                        () -> "Svalinn nested-cell demand receives full network power", () -> captureTwentyAuthorizedTicks(
                                helper, level, machine, slot,
                    () -> slot.snapshot().stack().get(ModDataComponents.POWER_CELL_SLOT_STATE.get()).cell()
                                        .orElseThrow().get(ModDataComponents.BATTERY_STATE.get()).charge(), nestedWindow -> {
                    var after = slot.snapshot().stack().get(ModDataComponents.POWER_CELL_SLOT_STATE.get()).cell().orElseThrow();
                    require(nestedWindow.admittedTicks() == 20,
                            "nested cell counts twenty authorized ticks: " + nestedWindow.diagnostic());
                    require(nestedWindow.endTick() - nestedWindow.startTick() >= 20,
                            "nested cell advances span at least twenty physical ticks: " + nestedWindow.diagnostic());
                    require(Math.abs(nestedWindow.after() - nestedWindow.before() - nestedWindow.expectedJoulesPerTick() * 20f) <= 2e-3f,
                            "nested cell total must equal resolved rate for twenty admissions: " + nestedWindow.diagnostic());
                    require(after.getItem() == originalNested.getItem() && after.getCount() == originalNested.getCount()
                                    && java.util.Objects.equals(after.get(ModDataComponents.LOOSE_OBJECT_PROFILE.get()), originalNested.get(ModDataComponents.LOOSE_OBJECT_PROFILE.get())),
                            "nested cell identity and non-charge item components survive mutation");
                    helper.succeed();
                    }));
                    }));
        });
    }

    private record AuthorizedWindow(float before, float after, long startTick, long endTick,
                                    int admittedTicks, float expectedJoulesPerTick, List<String> samples) {
        String diagnostic() { return "charge=" + before + "->" + after + " admitted=" + admittedTicks
                + " expectedJoules/admission=" + expectedJoulesPerTick + " ticks=" + startTick + ".." + endTick
                + " trace=" + samples; }
    }

    /** Measures twenty actual full-power authorization stamps, allowing bounded topology pauses without banking them. */
    private static void captureTwentyAuthorizedTicks(GameTestHelper helper, ServerLevel level,
            WeaponCapacitorRechargerBlockEntity machine, com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot slot,
            Supplier<Float> charge, Consumer<AuthorizedWindow> complete) {
        float startCharge = charge.get();
        long startTick = level.getGameTime();
        long startAuthorization = machine.lastAuthorizedAdvanceTick();
        var token = slot.snapshot().token();
        require(token != null, "authorized charge measurement requires the stable installed slot token");
        var chargerProfile = ModChargerProfiles.serverCatalog().get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_capacitor_recharger"));
        require(chargerProfile != null, "resolved charger profile required to measure joules");
        float expectedJoulesPerAdmittedTick = chargerProfile.chargeRate()
                / net.minecraft.SharedConstants.TICKS_PER_SECOND;
        sampleAuthorizedTick(helper, level, machine, slot, charge, complete, new ArrayList<>(100),
                startCharge, startTick, token.value(), expectedJoulesPerAdmittedTick,
                startAuthorization, startCharge, startTick, 0, 0);
    }

    private static void sampleAuthorizedTick(GameTestHelper helper, ServerLevel level,
            WeaponCapacitorRechargerBlockEntity machine,
            com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot slot,
            Supplier<Float> charge, Consumer<AuthorizedWindow> complete, ArrayList<String> samples,
            float startCharge, long startTick, String stableToken, float expectedJoulesPerAdmittedTick,
            long previousAuthorization, float previousCharge, long previousObservationTick,
            int admittedTicks, int wallTicks) {
        if (admittedTicks == 20) {
            complete.accept(new AuthorizedWindow(startCharge, charge.get(), startTick, level.getGameTime(),
                    admittedTicks, expectedJoulesPerAdmittedTick, List.copyOf(samples)));
            return;
        }
        if (wallTicks >= 100) {
            throw new GameTestAssertException("charger did not accumulate 20 full-power admissions within 100 wall ticks: "
                    + new AuthorizedWindow(startCharge, charge.get(), startTick, level.getGameTime(), admittedTicks,
                    expectedJoulesPerAdmittedTick, List.copyOf(samples)).diagnostic());
        }
        helper.runAfterDelay(1, () -> {
            long observationTick = level.getGameTime();
            require(observationTick - previousObservationTick == 1,
                    "native measurement skipped a physical server tick: previous=" + previousObservationTick + " now=" + observationTick);
            long authorization = machine.lastAuthorizedAdvanceTick();
            float currentCharge = charge.get();
            var snapshot = slot.snapshot();
            require(snapshot.token() != null && stableToken.equals(snapshot.token().value()),
                    "installed slot token changed during charger measurement; token=" + snapshot.token());
            var meter = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, machine);
            var decision = ChargerAuthorizationObservation.observe(previousAuthorization, authorization,
                    previousCharge, currentCharge, expectedJoulesPerAdmittedTick);
            require(decision.valid(), "charger authorization/charge observation invalid: " + decision);
            int nextAdmissions = admittedTicks + (decision.admitted() ? 1 : 0);
            long stampDelta = previousAuthorization == Long.MIN_VALUE || authorization == Long.MIN_VALUE
                    ? 0 : authorization - previousAuthorization;
            samples.add("tick=" + observationTick + ",authorizedStamp=" + authorization + ",stampDelta=" + stampDelta
                    + ",charge=" + previousCharge + "->" + currentCharge + ",demand=" + machine.demandWatts()
                    + ",meterKnown=" + meter.known() + ",delivered=" + meter.deliveredWatts()
                    + ",topology=" + com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level)
                    + ",slotToken=" + stableToken);
            sampleAuthorizedTick(helper, level, machine, slot, charge, complete, samples, startCharge, startTick,
                    stableToken, expectedJoulesPerAdmittedTick, authorization, currentCharge, observationTick,
                    nextAdmissions, wallTicks + 1);
        });
    }

    private static void registerCleanup(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup() {
                try {
                    if (level.getBlockEntity(pos) instanceof WeaponCapacitorRechargerBlockEntity machine) {
                        var slot = machine.slot();
                        if (slot != null && !slot.isRetired()) slot.dispose();
                    }
                    if (level.getBlockState(pos).is(ModBlocks.WEAPON_CAPACITOR_RECHARGER.get()))
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                } catch (Throwable failure) {
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.error("Failed to clean owned charger GameTest fixture at {}", pos, failure);
                }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                    net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
