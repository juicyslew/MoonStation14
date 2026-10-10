package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterProcessingFixtureBridge;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

/** Trusted native-core demonstration; does not claim authenticated UI transport coverage. */
@GameTestTags({"chemmaster", "chemmaster-fixture", "chemmaster-reactions"})
@PrefixGameTestTemplate(false)
public final class ChemMasterReactionDemoGameTests {
    private ChemMasterReactionDemoGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void sodiumAndChlorineReactOnlyOnAdmittedBufferReturn(GameTestHelper h) {
        ServerLevel level = (ServerLevel) h.getLevel();
        BlockPos machinePos = h.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(machinePos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(machinePos, ModBlocks.CHEM_MASTER.get().defaultBlockState(), 3);
        ChemMasterBlockEntity machine = (ChemMasterBlockEntity) level.getBlockEntity(machinePos);

        var sodium = ModReagents.createKey("sodium");
        var chlorine = ModReagents.createKey("chlorine");
        var salt = ModReagents.createKey("tablesalt");
        dumpJug(h, machine, new BlockPos(3, 1, 2), sodium);
        dumpJug(h, machine, new BlockPos(2, 1, 3), chlorine);
        var staged = machine.buffer().snapshot().contents().centContents();
        h.assertTrue(staged.getOrDefault(sodium, 0L) == 2500 && staged.getOrDefault(chlorine, 0L) == 2500
                        && !staged.containsKey(salt) && staged.values().stream().mapToLong(Long::longValue).sum() == 5000,
                "world-dumped sodium and chlorine remain inert before processing");

        ItemStack large = new ItemStack(ModItems.BEAKER_LARGE.get());
        seed(machine, large);
        h.assertTrue(ChemMasterProcessingFixtureBridge.process(machine, ChemMasterControlRequest.Operation.BUFFER_REAGENT, sodium, 2500),
                "selected sodium return admitted");
        h.assertTrue(ChemMasterProcessingFixtureBridge.process(machine, ChemMasterControlRequest.Operation.BUFFER_REAGENT, chlorine, 2500),
                "selected chlorine return admitted");
        var result = machine.inputSlot().snapshot().stack().get(ModDataComponents.REAGENT.get()).centContents();
        h.assertTrue(result.getOrDefault(salt, 0L) == 5000 && !result.containsKey(sodium) && !result.containsKey(chlorine),
                "existing live recursive reaction yields 2 tablesalt per sodium-plus-chlorine unit");
        h.assertTrue(machine.buffer().snapshot().contents().centContents().isEmpty()
                        && machine.inputSlot().snapshot().stack().getCount() == 1
                        && result.values().stream().mapToLong(Long::longValue).sum() == 5000,
                "buffer empties and conserved 50 units fit in the large beaker");
        h.succeed();
    }

    private static void dumpJug(GameTestHelper h, ChemMasterBlockEntity machine, BlockPos fixture, net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent) {
        ServerLevel level = (ServerLevel) h.getLevel();
        BlockPos origin = h.absolutePos(fixture);
        var actor = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, fixture);
        actor.setNoAi(true); actor.setNoGravity(true);
        actor.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        ItemStack jug = new ItemStack(ModItems.JUG.get());
        jug.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(reagent, 25f)));
        var item = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, origin.getCenter(), jug);
        level.addFreshEntity(item);
        var result = ChemMasterWorldDump.transferOwned(actor,
                new ChemMasterWorldDump.ExpectedSource(item.getUUID(), jug), machine.getBlockPos(),
                machine.placementIdentity(), machine.stateRevision(), machine.bufferRevision(), () -> true);
        h.assertTrue(result == ChemMasterWorldDump.Result.SUCCESS && item.getItem().getCount() == 1
                        && item.getItem().get(ModDataComponents.REAGENT.get()) == null,
                "actual jug dump transfers mixture and retains emptied jug");
    }

    private static void seed(ChemMasterBlockEntity machine, ItemStack stack) {
        var ledger = LevelItemLocationLedgers.forLevel((ServerLevel) machine.getLevel());
        var token = new ItemToken("chem-reaction-" + UUID.randomUUID());
        var origin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        if (!ledger.register(token, origin).accepted()
                || !machine.inputSlot().insert(0, ledger.snapshot().revision(), token, origin, stack))
            throw new IllegalStateException("fixture beaker admission failed");
        machine.slotChanged();
    }

}
