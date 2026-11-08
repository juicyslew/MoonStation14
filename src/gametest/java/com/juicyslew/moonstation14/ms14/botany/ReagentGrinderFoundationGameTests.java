package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestTags({"botany", "botany-grinder-foundation"})
@PrefixGameTestTemplate(false)
public final class ReagentGrinderFoundationGameTests {
    private ReagentGrinderFoundationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void registeredGrinderSuspendsAndRestoresOwnedSlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(relative);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.REAGENT_GRINDER.get().defaultBlockState(), 3);
        helper.runAfterDelay(2, () -> {
            var machine = (ReagentGrinderBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(machine.isPowered(), "zero-demand grinder is available without an APC");
            var profile = machine == null ? null : machine.resolvedProfile();
            helper.assertTrue(machine != null && machine.isLoadedValid() && profile != null
                    && !profile.extractor() && profile.chamberCapacity() == 6 && profile.startDelayTicks() == 70
                    && profile.powerWatts() == 0 && profile.activePowerWatts() == 750
                    && profile.minimumPacketYield() == 0,
                    "registered grinder resolves native profile defaults after server initialization");
            var ledger = LevelItemLocationLedgers.forLevel(level);
            var beaker = new ItemStack(ModItems.BEAKER.get());
            beaker.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(ModReagents.createKey("water"), 3f)));
            var wheat = ProduceFactory.createHarvest(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), 5);
            var beakerToken = new ItemToken("grinder-beaker-" + UUID.randomUUID());
            var wheatToken = new ItemToken("grinder-wheat-" + UUID.randomUUID());
            var origin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
            helper.assertTrue(ledger.register(beakerToken, origin).accepted() && ledger.register(wheatToken,
                    new ItemLocation.BodyHand(UUID.randomUUID(), "left")).accepted(), "fixture items receive unique ledger identities");
            var beakerSlot = machine.beakerSlot();
            var chamber = machine.chamberSlots();
            helper.assertTrue(beakerSlot.insert(0, ledger.snapshot().revision(), beakerToken, origin, beaker), "fixture restores beaker through shared slot owner");
            var wheatOrigin = ledger.snapshot().locationOf(wheatToken).orElseThrow();
            helper.assertTrue(chamber.get(0).insert(0, ledger.snapshot().revision(), wheatToken, wheatOrigin, wheat), "fixture restores wheat through shared slot owner");
            var owner = beakerSlot.ownerId();
            var placement = machine.placementIdentity();
            var beakerBefore = beakerSlot.snapshot();
            var wheatBefore = chamber.get(0).snapshot();
            int count = ledger.snapshot().size();
            var saved = machine.saveWithFullMetadata(level.registryAccess());
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(ledger.snapshot().locationOf(beakerToken).isEmpty() && ledger.snapshot().locationOf(wheatToken).isEmpty(),
                    "removal suspends every occupied slot claim");
            level.setBlock(pos, ModBlocks.REAGENT_GRINDER.get().defaultBlockState(), 3);
            var restored = (ReagentGrinderBlockEntity) level.getBlockEntity(pos);
            restored.loadWithComponents(saved, level.registryAccess());
            helper.assertTrue(restored.isLoadedValid() && !machine.isLoadedValid(), "only replacement block entity is valid at loaded placement");
            var restoredBeaker = restored.beakerSlot();
            var restoredChamber = restored.chamberSlots();
            helper.assertTrue(restored.placementIdentity().equals(placement) && restoredBeaker.ownerId().equals(owner)
                    && restoredChamber.size() == 6, "placement and six-slot owner composition persist");
            helper.assertTrue(ItemStack.matches(beakerBefore.stack(), restoredBeaker.snapshot().stack())
                    && ItemStack.matches(wheatBefore.stack(), restoredChamber.get(0).snapshot().stack())
                    && beakerBefore.token().equals(restoredBeaker.snapshot().token())
                    && wheatBefore.token().equals(restoredChamber.get(0).snapshot().token()), "exact stacks and item tokens survive NBT restoration");
            helper.assertTrue(ledger.snapshot().size() == count
                    && ledger.snapshot().locationOf(beakerToken).orElseThrow().equals(new ItemLocation.MachineSlot(owner, "beakerSlot"))
                    && ledger.snapshot().locationOf(wheatToken).orElseThrow().equals(new ItemLocation.MachineSlot(owner, "chamber0")),
                    "restored items reclaim their unique original ledger locations");
            helper.succeed();
        });
    }

    @GameTestTags({"botany", "botany-grinder-completion"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 110)
    public static void grinderTransfersHarvestAtTimedCompletionOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.REAGENT_GRINDER.get().defaultBlockState(), 3);
        helper.runAfterDelay(2, () -> {
            var machine = (ReagentGrinderBlockEntity) level.getBlockEntity(pos);
            var ledger = LevelItemLocationLedgers.forLevel(level);
            var beaker = new ItemStack(ModItems.BEAKER.get());
            beaker.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of()));
            var wheat = ProduceFactory.createHarvest(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), 5);
            var beakerToken = new ItemToken("grinder-completion-beaker-" + UUID.randomUUID());
            var wheatToken = new ItemToken("grinder-completion-wheat-" + UUID.randomUUID());
            var beakerOrigin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
            var wheatOrigin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
            helper.assertTrue(ledger.register(beakerToken, beakerOrigin).accepted()
                    && ledger.register(wheatToken, wheatOrigin).accepted(), "register fixture ownership");
            var beakerSlot = machine.beakerSlot(); var wheatSlot = machine.chamberSlots().get(0);
            helper.assertTrue(beakerSlot.insert(0, ledger.snapshot().revision(), beakerToken, beakerOrigin, beaker), "insert beaker through slot owner");
            helper.assertTrue(wheatSlot.insert(0, ledger.snapshot().revision(), wheatToken, wheatOrigin, wheat), "insert wheat through slot owner");
            helper.assertTrue(machine.startTrusted(machine.startProof(), level.getGameTime(), true), "trusted domain start accepts valid setup");
            helper.assertTrue(!machine.isPowered(), "active 750 W demand is unknown without supplied power");
            helper.runAfterDelay(69, () -> {
                helper.assertTrue(beakerSlot.snapshot().stack().get(ModDataComponents.REAGENT.get()).centContents().values().stream().mapToLong(Long::longValue).sum() == 0
                        && !wheatSlot.snapshot().stack().isEmpty(), "contents unchanged before deadline");
                helper.runAfterDelay(1, () -> {
                    var contents = beakerSlot.snapshot().stack().get(ModDataComponents.REAGENT.get());
                    helper.assertTrue(contents.centContents().values().stream().mapToLong(Long::longValue).sum() == 650 && wheatSlot.snapshot().stack().isEmpty(), "deadline transfers all 650 cents and consumes source");
                    helper.assertTrue(machine.isPowered(), "completion returns grinder to available zero demand");
                    helper.assertTrue(ledger.snapshot().locationOf(wheatToken).isEmpty(), "consumed source token retired from ledger");
                    helper.runAfterDelay(3, () -> {
                        helper.assertTrue(beakerSlot.snapshot().stack().get(ModDataComponents.REAGENT.get()).centContents().values().stream().mapToLong(Long::longValue).sum() == 650,
                                "later ticks do not duplicate transfer");
                        helper.succeed();
                    });
                });
            });
        });
    }
}
