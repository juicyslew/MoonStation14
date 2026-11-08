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

@GameTestTags({"chemmaster-output"})
@PrefixGameTestTemplate(false)
public final class ChemMasterPackagingGameTests {
    @GameTest(template = "empty", timeoutTicks = 30, templateNamespace = "moonstation14")
    public static void bufferPillsCommitToOwnedCanisterAndStaleRetryIsUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.CHEM_MASTER.get().defaultBlockState(), 3);
        var machine = (ChemMasterBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(machine != null && machine.isLoadedValid(), "ChemMaster loaded in native world");
        var ledger = LevelItemLocationLedgers.forLevel(level);
        ItemToken canisterToken = new ItemToken("output-canister-" + UUID.randomUUID());
        ItemStack canister = new ItemStack(ModItems.PILL_CANISTER.get());
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        helper.assertTrue(ledger.register(canisterToken, hand).accepted(), "canister identity is ledger-owned before insertion");
        var output = machine.outputSlot();
        helper.assertTrue(output.insert(output.snapshot().revision(), ledger.snapshot().revision(), canisterToken, hand, canister),
                "registered actual canister accepted by resolved output policy");
        machine.slotChanged();

        var a = ModReagents.createKey("water");
        var b = ModReagents.createKey("sugar");
        helper.assertTrue(machine.buffer().addWholeMixture(new ReagentAttachment(Map.of(a, 10f, b, 10f))), "mixed buffer seeded");
        machine.stateChanged();
        var in = machine.inputSlot().snapshot(); var out = output.snapshot();
        var result = machine.createPills(machine.placementIdentity(), machine.stateRevision(), in.revision(),
                machine.bufferRevision(), out.revision(), in.token(), out.token(), ChemMasterBlockEntity.PackagingSource.BUFFER,
                2, 5, "Native label", 19);
        helper.assertTrue(result == ChemMasterBlockEntity.PackagingResult.SUCCESS, "two pills commit from full mixture");
        var committed = output.snapshot();
        var grid = GridContents.read(committed.stack()).orElseThrow();
        helper.assertTrue(grid.entries().size() == 2, "both pills remain stored in inserted canister");
        helper.assertTrue(grid.entries().stream().allMatch(entry -> entry.stack().get(ModDataComponents.PILL_TYPE.get()).type() == 19),
                "pill types match requested visual variant");
        helper.assertTrue(grid.entries().stream().allMatch(entry -> entry.stack().get(ModDataComponents.REAGENT.get()).centContents().values().stream().mapToLong(Long::longValue).sum() == 500),
                "each pill contains exact 5-unit dose");
        helper.assertTrue(grid.entries().stream().allMatch(entry -> ledger.snapshot().locationOf(new ItemToken(entry.token()))
                .filter(location -> location.equals(new ItemLocation.StorageCell(canisterToken, "main", ItemLocation.Orientation.UNSPECIFIED,
                        entry.x() + "," + entry.y()))).isPresent()), "every child token has its parent cell ownership");
        long remaining = machine.buffer().quantities().values().stream().mapToLong(Long::longValue).sum();
        var rejected = machine.createPills(machine.placementIdentity(), machine.stateRevision(), machine.inputSlot().snapshot().revision(),
                machine.bufferRevision(), out.revision(), in.token(), committed.token(), ChemMasterBlockEntity.PackagingSource.BUFFER,
                1, 1, "", 0);
        helper.assertTrue(rejected == ChemMasterBlockEntity.PackagingResult.STALE_STATE, "stale output revision rejects");
        helper.assertTrue(remaining == machine.buffer().quantities().values().stream().mapToLong(Long::longValue).sum()
                && GridContents.read(output.snapshot().stack()).orElseThrow().entries().size() == 2,
                "stale request changes neither source nor output");
        var freshInput = machine.inputSlot().snapshot(); var freshOutput = output.snapshot();
        var beforeRejectedContents = machine.buffer().quantities();
        var oversized = machine.createPills(machine.placementIdentity(), machine.stateRevision(), freshInput.revision(),
                machine.bufferRevision(), freshOutput.revision(), freshInput.token(), freshOutput.token(),
                ChemMasterBlockEntity.PackagingSource.BUFFER, 9, 1, "", 0);
        helper.assertTrue(oversized == ChemMasterBlockEntity.PackagingResult.OUTPUT_FULL,
                "request beyond actual remaining canister cells rejects before source withdrawal");
        var insufficient = machine.createPills(machine.placementIdentity(), machine.stateRevision(), freshInput.revision(),
                machine.bufferRevision(), freshOutput.revision(), freshInput.token(), freshOutput.token(),
                ChemMasterBlockEntity.PackagingSource.BUFFER, 1, 20, "", 0);
        helper.assertTrue(insufficient == ChemMasterBlockEntity.PackagingResult.INSUFFICIENT_SOURCE,
                "insufficient source rejects without packaging");
        var replaced = machine.createPills(machine.placementIdentity(), machine.stateRevision(), freshInput.revision(),
                machine.bufferRevision(), freshOutput.revision(), freshInput.token(), new ItemToken("replaced-output"),
                ChemMasterBlockEntity.PackagingSource.BUFFER, 1, 1, "", 0);
        helper.assertTrue(replaced == ChemMasterBlockEntity.PackagingResult.STALE_STATE,
                "output-token replacement proof is rejected");
        helper.assertTrue(beforeRejectedContents.equals(machine.buffer().quantities())
                && GridContents.read(output.snapshot().stack()).orElseThrow().entries().size() == 2,
                "full, insufficient, and replaced-output requests leave source and contents unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30, templateNamespace = "moonstation14")
    public static void wholeMixtureFillsInsertedBottleWithoutSpawningPackage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.CHEM_MASTER.get().defaultBlockState(), 3);
        var machine = (ChemMasterBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(machine != null && machine.isLoadedValid(), "ChemMaster loaded in native world");
        var ledger = LevelItemLocationLedgers.forLevel(level);
        ItemToken bottleToken = new ItemToken("output-bottle-" + UUID.randomUUID());
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        helper.assertTrue(ledger.register(bottleToken, hand).accepted(), "actual bottle registered");
        var output = machine.outputSlot();
        helper.assertTrue(output.insert(output.snapshot().revision(), ledger.snapshot().revision(), bottleToken, hand, bottle),
                "resolved empty bottle fits removable output slot");
        machine.slotChanged();
        var water = ModReagents.createKey("water"); var sugar = ModReagents.createKey("sugar");
        helper.assertTrue(machine.buffer().addWholeMixture(new ReagentAttachment(Map.of(water, 5f, sugar, 5f))), "mixture seeded");
        machine.stateChanged();
        var in = machine.inputSlot().snapshot(); var out = output.snapshot();
        var result = machine.outputToBottle(machine.placementIdentity(), machine.stateRevision(), in.revision(),
                machine.bufferRevision(), out.revision(), in.token(), out.token(), ChemMasterBlockEntity.PackagingSource.BUFFER,
                5, "Bottle label");
        helper.assertTrue(result == ChemMasterBlockEntity.PackagingResult.SUCCESS, "whole-mixture bottle draw commits");
        var filled = output.snapshot().stack();
        var reagents = filled.get(ModDataComponents.REAGENT.get());
        helper.assertTrue(reagents != null && reagents.centContents().values().stream().mapToLong(Long::longValue).sum() == 500,
                "existing bottle contains exact whole mixture amount");
        helper.assertTrue(machine.buffer().quantities().values().stream().mapToLong(Long::longValue).sum() == 500,
                "buffer retained undrawn mixture");
        helper.assertTrue(filled.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) != null,
                "nonempty label augments bottle base name");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30, templateNamespace = "moonstation14")
    public static void inputBeakerPillsWithdrawWholeMixedDoseConservatively(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.CHEM_MASTER.get().defaultBlockState(), 3);
        var machine = (ChemMasterBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(machine != null && machine.isLoadedValid(), "ChemMaster loaded in native world");
        var ledger = LevelItemLocationLedgers.forLevel(level);
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var inputToken = new ItemToken("input-beaker-" + UUID.randomUUID());
        ItemStack beaker = new ItemStack(ModItems.BEAKER.get());
        var water = ModReagents.createKey("water"); var sugar = ModReagents.createKey("sugar");
        beaker.set(ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(Map.of(water, 5f, sugar, 5f)));
        helper.assertTrue(ledger.register(inputToken, hand).accepted(), "beaker token registered");
        var input = machine.inputSlot();
        helper.assertTrue(input.insert(input.snapshot().revision(), ledger.snapshot().revision(), inputToken, hand, beaker),
                "beaker accepted in existing input slot");
        var outputToken = new ItemToken("output-canister-" + UUID.randomUUID());
        ItemStack canister = new ItemStack(ModItems.PILL_CANISTER.get());
        var outputHand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        helper.assertTrue(ledger.register(outputToken, outputHand).accepted(), "canister token registered");
        var output = machine.outputSlot();
        helper.assertTrue(output.insert(output.snapshot().revision(), ledger.snapshot().revision(), outputToken, outputHand, canister),
                "canister accepted in output slot");
        machine.slotChanged();
        var in = input.snapshot(); var out = output.snapshot();
        var result = machine.createPills(machine.placementIdentity(), machine.stateRevision(), in.revision(),
                machine.bufferRevision(), out.revision(), in.token(), out.token(), ChemMasterBlockEntity.PackagingSource.INPUT,
                2, 5, "", 0);
        helper.assertTrue(result == ChemMasterBlockEntity.PackagingResult.SUCCESS, "beaker source packaging commits");
        var leftover = input.snapshot().stack().get(ModDataComponents.REAGENT.get());
        helper.assertTrue(leftover == null || leftover.centContents().isEmpty(), "exact withdrawal consumed source mixture");
        var children = GridContents.read(output.snapshot().stack()).orElseThrow().entries();
        helper.assertTrue(children.size() == 2 && children.stream().allMatch(entry -> entry.stack()
                .get(ModDataComponents.REAGENT.get()).centContents().values().stream().mapToLong(Long::longValue).sum() == 500),
                "both beaker-derived pills contain exact mixed doses");
        helper.succeed();
    }

}
