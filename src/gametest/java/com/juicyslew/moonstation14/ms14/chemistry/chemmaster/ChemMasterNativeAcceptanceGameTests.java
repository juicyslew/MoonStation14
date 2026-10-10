package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.entity.player.Player;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Exercises the real package-private processing consumer, not packet/session transport. */
@GameTestTags({"chemmaster", "beaker"})
@PrefixGameTestTemplate(false)
public final class ChemMasterNativeAcceptanceGameTests {
    private ChemMasterNativeAcceptanceGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 30, templateNamespace = "moonstation14")
    public static void selectedInputExtractionSurvivesBeakerEjectionAndSwap(GameTestHelper helper) {
        ChemMasterBlockEntity machine = machine(helper);
        var water = ModReagents.createKey("water");
        var sugar = ModReagents.createKey("sugar");
        ItemStack input = new ItemStack(ModItems.BEAKER.get());
        input.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(water, 4f, sugar, 2f)));
        ItemToken oldToken = seed(machine, input);
        helper.assertTrue(process(machine, ChemMasterControlRequest.Operation.INPUT_REAGENT, water, 100), "selected preset extraction commits");
        var left = machine.inputSlot().snapshot().stack().get(ModDataComponents.REAGENT.get());
        helper.assertTrue(left.centContents().get(water) == 300 && left.centContents().get(sugar) == 200,
                "only selected species is extracted from actual slot component");
        // A new item is admitted by the real slot API; the machine-owned buffer is untouched by the swap.
        ItemStack replacement = new ItemStack(ModItems.BEAKER.get());
        var ledger = LevelItemLocationLedgers.forLevel((ServerLevel) machine.getLevel());
        ItemToken token = new ItemToken("chem-swap-" + UUID.randomUUID());
        var slot = machine.inputSlot();
        var oldBeakerRelease = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        helper.assertTrue(slot.eject(slot.snapshot().revision(), ledger.snapshot().revision(), oldBeakerRelease).isPresent(), "old beaker ejected");
        helper.assertTrue(ledger.consume(ledger.snapshot().revision(), oldToken, oldBeakerRelease).accepted(),
                "trusted-core fixture retires the released old beaker claim");
        var replacementOrigin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        helper.assertTrue(ledger.register(token, replacementOrigin).accepted(), "replacement source registered");
        helper.assertTrue(slot.insert(slot.snapshot().revision(), ledger.snapshot().revision(), token, replacementOrigin, replacement), "replacement beaker inserted");
        helper.assertTrue(machine.buffer().snapshot().contents().centContents().get(water) == 100,
                "buffer survives beaker ejection and replacement admission");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30, templateNamespace = "moonstation14")
    public static void returnClampsToCapacityAndEmptyInputDiscardConsumesBuffer(GameTestHelper helper) {
        ChemMasterBlockEntity machine = machine(helper);
        var water = ModReagents.createKey("water");
        ItemStack input = new ItemStack(ModItems.BEAKER.get());
        input.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(water, 49.5f)));
        seed(machine, input);
        ChemMasterBuffer buffer = machine.buffer();
        // Use the actual transfer operation to populate the owned buffer.
        var source = new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(new ReagentComponent(Map.of(water, 2f)));
        helper.assertTrue(buffer.extractFrom(source, water, ChemMasterBuffer.Amount.ALL) == 200, "buffer seed");
        helper.assertTrue(process(machine, ChemMasterControlRequest.Operation.BUFFER_REAGENT, water,
                ChemMasterControlRequest.allAmountCents()), "return all processed");
        helper.assertTrue(machine.inputSlot().snapshot().stack().get(ModDataComponents.REAGENT.get()).centContents().get(water) == 5000,
                "return is clamped to ordinary beaker capacity");
        helper.assertTrue(machine.buffer().snapshot().contents().centContents().get(water) == 150,
                "only the beaker's remaining 50 cents are admitted");

        var slot = machine.inputSlot();
        var ledger = LevelItemLocationLedgers.forLevel((ServerLevel) machine.getLevel());
        var origin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        helper.assertTrue(slot.eject(slot.snapshot().revision(), ledger.snapshot().revision(), origin).isPresent(), "beaker removed");
        helper.assertTrue(machine.configure(machine.stateRevision(), ChemMasterControlRequest.Mode.DISCARD, machine.sort()), "discard mode");
        helper.assertTrue(process(machine, ChemMasterControlRequest.Operation.BUFFER_REAGENT, water, 100), "discard works without input beaker");
        helper.assertTrue(machine.buffer().snapshot().contents().centContents().get(water) == 50,
                "no-input discard consumes exactly 100 cents, preserving the remaining 50");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, templateNamespace = "moonstation14")
    public static void bufferRemainsInertUntilAdmittedReturnCompletesKnownReaction(GameTestHelper helper) {
        ChemMasterBlockEntity machine = machine(helper);
        var chlorine = ModReagents.createKey("chlorine");
        var sodium = ModReagents.createKey("sodium");
        var tableSalt = ModReagents.createKey("tablesalt");
        ItemStack input = new ItemStack(ModItems.BEAKER.get());
        input.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(chlorine, 1f, sodium, 1f)));
        seed(machine, input);
        helper.assertTrue(process(machine, ChemMasterControlRequest.Operation.INPUT_REAGENT, chlorine, 100), "selected chlorine extracted");
        var extractedInput = machine.inputSlot().snapshot().stack().get(ModDataComponents.REAGENT.get()).centContents();
        helper.assertTrue(extractedInput.get(sodium) == 100 && !extractedInput.containsKey(tableSalt),
                "ChemMaster buffer extraction does not run the sodium-plus-chlorine recipe");
        helper.assertTrue(machine.buffer().snapshot().contents().centContents().get(chlorine) == 100,
                "chlorine is held inertly in machine buffer");
        helper.assertTrue(process(machine, ChemMasterControlRequest.Operation.BUFFER_REAGENT, chlorine, 100), "admitted chlorine return");
        var reacted = machine.inputSlot().snapshot().stack().get(ModDataComponents.REAGENT.get()).centContents();
        helper.assertTrue(reacted.get(tableSalt) == 200 && !reacted.containsKey(chlorine) && !reacted.containsKey(sodium),
                "positive admitted return runs existing tablesalt recipe (1 chlorine + 1 sodium -> 2 tablesalt)");
        helper.assertTrue(machine.buffer().snapshot().contents().centContents().isEmpty(), "returned chlorine left the buffer");
        helper.succeed();
    }

    @GameTest(template = "power_live_chain_clear", timeoutTicks = 900, templateNamespace = "moonstation14")
    @GameTestTags({"power", "chemmaster-appearance"})
    public static void actual250WMachineMeterRequiresLiveChainClosedBreakerAndCurrentPlacement(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        // Keep this focused rather than coupling to the lamp-specific PowerDeviceGameTests setup:
        // same established native cable/device layout, with only the receiver replaced by a ChemMaster.
        BlockPos source = at(helper, 3, 1, 2);
        BlockPos substation = at(helper, 3, 1, 4);
        BlockPos apc = at(helper, 3, 1, 6);
        BlockPos machinePos = at(helper, 3, 1, 8);
        for (int z = 2; z <= 8; z++) level.setBlock(at(helper, 4, 1, z), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++) level.setBlock(at(helper, 2, 1, z), Blocks.STONE.defaultBlockState(), 3);
        placePowerDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placePowerDevice(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placePowerDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        level.setBlock(machinePos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(machinePos, ModBlocks.CHEM_MASTER.get().defaultBlockState(), 3);
        ChemMasterBlockEntity machine = (ChemMasterBlockEntity) level.getBlockEntity(machinePos);
        helper.assertTrue(machine != null && machine.powerLoadWatts() == 250.0 && machine.cableReach() > 0,
                "placed resolved ChemMaster contributes its actual prototype demand and reach");
        for (int z = 2; z <= 4; z++) helper.assertTrue(CableStorage.place(level, at(helper, 4, 1, z), Direction.WEST, CableTier.HV), "HV cable");
        for (int z = 4; z <= 6; z++) helper.assertTrue(CableStorage.place(level, at(helper, 2, 1, z), Direction.EAST, CableTier.MV), "MV cable");
        for (int z = 6; z <= 8; z++) helper.assertTrue(CableStorage.place(level, at(helper, 4, 1, z), Direction.WEST, CableTier.APC), "APC cable");
        CableFaceNode[] ports = {
                new CableFaceNode(at(helper, 4, 1, 2), Direction.WEST, CableTier.HV),
                new CableFaceNode(at(helper, 4, 1, 4), Direction.WEST, CableTier.HV),
                new CableFaceNode(at(helper, 2, 1, 4), Direction.EAST, CableTier.MV),
                new CableFaceNode(at(helper, 2, 1, 6), Direction.EAST, CableTier.MV),
                new CableFaceNode(at(helper, 4, 1, 6), Direction.WEST, CableTier.APC),
                new CableFaceNode(at(helper, 4, 1, 8), Direction.WEST, CableTier.APC)
        };
        PowerFixtureReadiness.await(helper, level, 500,
                () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level, ports),
                () -> "ports=" + java.util.Arrays.toString(ports) + ", topology=" + PowerGraphService.topologyReady(level)
                        + ", indexed=" + PowerRuntime.indexedDeviceCount(level), () -> {
                    awaitFreshMachinePower(helper, level, machine, apc, () -> {
                        var observed = PowerRuntime.chemMasterMeter(level, machine);
                        helper.assertTrue(observed.known() && observed.deliveredWatts() == 250.0
                                        && PowerRuntime.hasFullFreshChemMasterPower(level, machine, machine.placementIdentity()),
                                 "native live runtime meter delivers the complete resolved 250 W demand to current identity");
                        PowerFixtureReadiness.await(helper, level, 10,
                                () -> machine.getBlockState().getValue(ChemMasterBlock.POWERED),
                                () -> "appearance=" + machine.getBlockState(), () -> {
                        UUID appearanceIdentity = machine.placementIdentity();
                        helper.assertTrue(machine.getBlockState().getValue(ChemMasterBlock.POWERED)
                                        && !machine.getBlockState().getValue(ChemMasterBlock.LOADED),
                                "fresh full delivery projects powered empty appearance without an open UI");
                        ItemStack loadedBeaker = new ItemStack(ModItems.BEAKER.get());
                        seed(machine, loadedBeaker);
                        helper.assertTrue(machine.getBlockState().getValue(ChemMasterBlock.POWERED)
                                        && machine.getBlockState().getValue(ChemMasterBlock.LOADED)
                                        && machine.placementIdentity().equals(appearanceIdentity),
                                "committed slot insertion projects powered loaded state without changing placement identity");
                        PowerDeviceBlockEntity apcEntity = (PowerDeviceBlockEntity) level.getBlockEntity(apc);
                        Player actor = new Player(level, apc, 0, new GameProfile(UUID.randomUUID(), "chemmaster-breaker-test")) {
                            @Override public boolean isCreative() { return true; }
                            @Override public boolean isSpectator() { return false; }
                        };
                        actor.getAbilities().instabuild = true;
                        helper.assertTrue(apcEntity.toggleBreaker(actor, 0), "open real APC breaker");
                         helper.runAfterDelay(PowerRuntime.TICKS_PER_SOLVE + 2, () -> {
                             helper.assertTrue(!PowerRuntime.hasFullFreshChemMasterPower(level, machine, machine.placementIdentity()),
                                     "breaker loss makes native fresh full-demand predicate fail closed");
                             helper.assertTrue(!machine.getBlockState().getValue(ChemMasterBlock.POWERED)
                                             && machine.getBlockState().getValue(ChemMasterBlock.LOADED)
                                             && machine.placementIdentity().equals(appearanceIdentity),
                                     "power loss retains committed loaded appearance and placement identity");
                             var slot = machine.inputSlot();
                             var ledger = LevelItemLocationLedgers.forLevel(level);
                             helper.assertTrue(slot.eject(slot.snapshot().revision(), ledger.snapshot().revision(),
                                             new ItemLocation.BodyHand(UUID.randomUUID(), "left")).isPresent(),
                                     "eject committed input through real machine slot and ledger");
                             machine.slotChanged();
                             helper.assertTrue(!machine.getBlockState().getValue(ChemMasterBlock.POWERED)
                                             && !machine.getBlockState().getValue(ChemMasterBlock.LOADED),
                                     "ejection projects unpowered empty state");
                             var oldIdentity = machine.placementIdentity();
                            level.setBlock(machinePos, Blocks.AIR.defaultBlockState(), 3);
                            level.setBlock(machinePos.below(), Blocks.STONE.defaultBlockState(), 3);
                            level.setBlock(machinePos, ModBlocks.CHEM_MASTER.get().defaultBlockState(), 3);
                            var replacement = (ChemMasterBlockEntity) level.getBlockEntity(machinePos);
                            helper.assertTrue(replacement != null && !replacement.placementIdentity().equals(oldIdentity)
                                            && !PowerRuntime.hasFullFreshChemMasterPower(level, replacement, oldIdentity),
                                    "old placement identity cannot authorize physically replaced ChemMaster");
                             helper.succeed();
                         });
                        });
                    });
                });
    }

    private static void awaitFreshMachinePower(GameTestHelper helper, ServerLevel level, ChemMasterBlockEntity machine,
                                               BlockPos apc, Runnable ready) {
        PowerFixtureReadiness.await(helper, level, 200,
                () -> PowerRuntime.hasFullFreshChemMasterPower(level, machine, machine.placementIdentity()),
                () -> "meter=" + PowerRuntime.chemMasterMeter(level, machine) + ", demand=" + machine.powerLoadWatts()
                        + ", apc=" + level.getBlockEntity(apc), ready);
    }

    private static BlockPos at(GameTestHelper helper, int x, int y, int z) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        if (!helper.getBounds().contains(pos.getCenter())) throw new GameTestAssertException("power fixture placement outside template bounds: " + pos);
        return pos;
    }

    private static void placePowerDevice(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
        if (kind == PowerDeviceKind.APC) {
            if (!com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement.placeApc(level, pos, facing)) throw new IllegalStateException("APC support/placement failed at " + pos);
            return;
        }
        var block = switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case APC -> ModBlocks.APC.get();
            default -> throw new IllegalArgumentException("unsupported fixture device " + kind);
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static ChemMasterBlockEntity machine(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        ServerLevel level = (ServerLevel) helper.getLevel();
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.CHEM_MASTER.get().defaultBlockState(), 3);
        return (ChemMasterBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static ItemToken seed(ChemMasterBlockEntity machine, ItemStack beaker) {
        var ledger = LevelItemLocationLedgers.forLevel((ServerLevel) machine.getLevel());
        var token = new ItemToken("chem-input-" + UUID.randomUUID());
        var origin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        if (!ledger.register(token, origin).accepted() || !machine.inputSlot().insert(0, ledger.snapshot().revision(), token, origin, beaker))
            throw new IllegalStateException("fixture beaker admission failed");
        machine.slotChanged();
        return token;
    }

    private static boolean process(ChemMasterBlockEntity machine, ChemMasterControlRequest.Operation operation,
                                   net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent,
                                   long amount) {
        return ChemMasterSessionService.process(machine, new ChemMasterControlRequest(UUID.randomUUID(),
                machine.placementIdentity(), ((ServerLevel) machine.getLevel()).dimension().location(), machine.getBlockPos(),
                0, machine.stateRevision(), machine.inputSlot().snapshot().revision(), operation, machine.mode(), machine.sort(),
                Optional.of(reagent), amount));
    }
}
