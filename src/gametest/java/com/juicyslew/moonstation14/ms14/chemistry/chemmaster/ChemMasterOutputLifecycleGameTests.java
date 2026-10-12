package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;
import java.util.UUID;

/** Saved block-entity payload restores through MachineItemSlot and the existing item-location ledger. */
@GameTestTags({"chemmaster-output-lifecycle"})
@PrefixGameTestTemplate(false)
public final class ChemMasterOutputLifecycleGameTests {
    private ChemMasterOutputLifecycleGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 30, templateNamespace = "moonstation14")
    public static void filledCanisterAndSlotOwnershipSurviveMachineSaveRestore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.CHEM_MASTER.get().defaultBlockState(), 3);
        var machine = (ChemMasterBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(machine != null && machine.isLoadedValid(), "ChemMaster loaded before save/restore");
        var ledger = LevelItemLocationLedgers.forLevel(level);
        var water = ModReagents.createKey("water");
        var sugar = ModReagents.createKey("sugar");

        var inputToken = new ItemToken("lifecycle-input-" + UUID.randomUUID());
        var outputToken = new ItemToken("lifecycle-output-" + UUID.randomUUID());
        var inputHand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var outputHand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        ItemStack beaker = new ItemStack(ModItems.BEAKER.get());
        beaker.set(ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(
                Map.of(water, 3f, sugar, 2f)));
        helper.assertTrue(ledger.register(inputToken, inputHand).accepted(), "input token registered");
        helper.assertTrue(machine.inputSlot().insert(0, ledger.snapshot().revision(), inputToken, inputHand, beaker),
                "beaker admitted to machine input owner");
        ItemStack canister = new ItemStack(ModItems.PILL_CANISTER.get());
        helper.assertTrue(ledger.register(outputToken, outputHand).accepted(), "output canister token registered");
        helper.assertTrue(machine.outputSlot().insert(0, ledger.snapshot().revision(), outputToken, outputHand, canister),
                "canister admitted to machine output owner");
        machine.slotChanged();
        helper.assertTrue(machine.buffer().addWholeMixture(new ReagentAttachment(Map.of(water, 8f, sugar, 7f))),
                "buffer mixture seeded");
        machine.stateChanged();
        var beforeInput = machine.inputSlot().snapshot();
        var beforeOutput = machine.outputSlot().snapshot();
        helper.assertTrue(machine.createPills(machine.placementIdentity(), machine.stateRevision(), beforeInput.revision(),
                machine.bufferRevision(), beforeOutput.revision(), beforeInput.token(), beforeOutput.token(),
                ChemMasterBlockEntity.PackagingSource.BUFFER, 2, 5, "Saved batch", 13)
                == ChemMasterBlockEntity.PackagingResult.SUCCESS, "filled child pills committed before save");
        var savedOutput = machine.outputSlot().snapshot();
        var entriesBefore = GridContents.read(savedOutput.stack()).orElseThrow().entries();
        helper.assertTrue(entriesBefore.size() == 2, "two child pills occupy saved canister");
        var parentOwner = machine.ownerId();
        var placement = machine.placementIdentity();
        long ledgerClaimsBefore = ledger.snapshot().size();
        var childClaimsBefore = entriesBefore.stream().map(entry -> Map.entry(new ItemToken(entry.token()),
                ledger.snapshot().locationOf(new ItemToken(entry.token())).orElseThrow())).toList();
        helper.assertTrue(childClaimsBefore.stream().allMatch(entry -> entry.getValue() instanceof ItemLocation.StorageCell cell
                && cell.container().equals(outputToken)), "children are claimed by this canister's storage cells");
        var encoded = machine.saveWithFullMetadata(level.registryAccess());

        // Supported block-entity NBT save/recreate with the same world ledger; not a chunk unload
        // or persisted-ledger restart simulation.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(ledger.snapshot().locationOf(inputToken).isEmpty()
                && ledger.snapshot().locationOf(outputToken).isEmpty(), "removed machine suspends both parent slot claims");
        helper.assertTrue(childClaimsBefore.stream().allMatch(entry -> ledger.snapshot().locationOf(entry.getKey())
                .filter(entry.getValue()::equals).isPresent()), "suspending parent slots leaves child claims intact");
        level.setBlock(pos, ModBlocks.CHEM_MASTER.get().defaultBlockState(), 3);
        var restored = (ChemMasterBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(restored != null, "restored block entity recreated");
        restored.loadWithComponents(encoded, level.registryAccess());
        helper.assertTrue(restored.isLoadedValid(), "restored slots and buffer resolve through their existing owners");
        var restoredInput = restored.inputSlot().snapshot();
        var restoredOutput = restored.outputSlot().snapshot();
        helper.assertTrue(restored.ownerId().equals(parentOwner) && restored.placementIdentity().equals(placement),
                "save/restore preserves slot owner and placement identity");
        helper.assertTrue(inputToken.equals(restoredInput.token()) && outputToken.equals(restoredOutput.token()),
                "both parent tokens restored to their original machine slots");
        helper.assertTrue(ledger.snapshot().size() == ledgerClaimsBefore,
                "restore reclaims parents once without duplicating child ledger entries");
        helper.assertTrue(childClaimsBefore.stream().allMatch(entry -> ledger.snapshot().locationOf(entry.getKey())
                .filter(entry.getValue()::equals).isPresent()), "all child StorageCell claims are unchanged after restore");
        var restoredEntries = GridContents.read(restoredOutput.stack()).orElseThrow().entries();
        helper.assertTrue(restoredEntries.size() == entriesBefore.size()
                && restoredEntries.stream().allMatch(entry -> entry.stack().get(ModDataComponents.PILL_TYPE.get()).type() == 13
                    && entry.stack().get(ModDataComponents.REAGENT.get()).centContents().values().stream()
                        .mapToLong(Long::longValue).sum() == 500), "restored grid owner exposes original typed doses");
        helper.assertTrue(restored.buffer().quantities().values().stream().mapToLong(Long::longValue).sum() == 500,
                "buffer state restored alongside slots");
        helper.assertTrue(restoredInput.stack().get(ModDataComponents.REAGENT.get()).centContents().values()
                .stream().mapToLong(Long::longValue).sum() == 500, "input beaker contents restored independently");

        var destination = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var ejected = restored.outputSlot().eject(restoredOutput.revision(), ledger.snapshot().revision(), destination);
        helper.assertTrue(ejected.isPresent() && ejected.orElseThrow().token().equals(outputToken),
                "shared MachineItemSlot ejects the exact parent token");
        helper.assertTrue(ledger.snapshot().locationOf(outputToken).filter(destination::equals).isPresent()
                && restored.outputSlot().snapshot().stack().isEmpty(), "ejection changes only the parent location");
        var accessibleChildren = GridContents.read(ejected.orElseThrow().stack()).orElseThrow().entries();
        helper.assertTrue(accessibleChildren.size() == 2 && childClaimsBefore.stream().allMatch(entry ->
                ledger.snapshot().locationOf(entry.getKey()).filter(entry.getValue()::equals).isPresent()),
                "ejected canister grid retains its uniquely claimed child pills");
        helper.succeed();
    }
}
