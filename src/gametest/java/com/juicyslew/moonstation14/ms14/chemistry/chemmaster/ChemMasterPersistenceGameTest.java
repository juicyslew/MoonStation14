package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;
import java.util.Map;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;

@GameTestTags({"chemmaster", "machine-slots"})
@PrefixGameTestTemplate(false)
public final class ChemMasterPersistenceGameTest {
    @GameTest(template = "empty", timeoutTicks = 30, templateNamespace = "moonstation14")
    public static void singletonDropPreservesStateAndReplacesPlacementIdentity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.CHEM_MASTER.get().defaultBlockState(), 3);
        ChemMasterBlockEntity original = (ChemMasterBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(original != null, "machine BE placed");
        UUID owner = original.ownerId();
        UUID placement = original.placementIdentity();
        var ledger = LevelItemLocationLedgers.forLevel(level);
        ItemToken itemToken = new ItemToken("beaker-lifecycle-" + UUID.randomUUID());
        ItemStack beaker = new ItemStack(ModItems.BEAKER.get());
        var water = ModReagents.createKey("water");
        beaker.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(water, 5f)));
        var handLocation = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        helper.assertTrue(ledger.register(itemToken, handLocation).accepted(), "slot source registered");
        var slot = original.inputSlot();
        helper.assertTrue(slot != null && slot.insert(0, ledger.snapshot().revision(), itemToken, handLocation, beaker),
                "whole reagent beaker inserted");
        var waterKey = ModReagents.createKey("water");
        var stagedBeaker = new ReagentAttachment(new ReagentComponent(Map.of(waterKey, 5f)));
        helper.assertTrue(original.buffer().extractFrom(stagedBeaker, waterKey,
                com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer.Amount.ALL) == 500,
                "buffer seeded with exact reagent quantity");
        var savedBuffer = original.buffer().snapshot();
        original.slotChanged();

        var oldRuntimeSlot = slot;

        var actor = new FakePlayer(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "chemmaster-persistence"));
        actor.setGameMode(GameType.SURVIVAL);
        actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.assertTrue(actor.gameMode.destroyBlock(pos), "survival break succeeds");
        var matchingDrops = level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(Vec3.atCenterOf(pos), 5, 5, 5))
                .stream().filter(entity -> entity.getItem().is(ModItems.CHEM_MASTER.get())).toList();
        helper.assertTrue(matchingDrops.size() == 1, "one singleton machine drop exists");
        ItemEntity dropped = matchingDrops.getFirst();
        ItemStack machineItem = dropped.getItem().copy();
        helper.assertTrue(machineItem.has(DataComponents.BLOCK_ENTITY_DATA), "drop includes standard block entity data");
        CustomData saved = machineItem.get(DataComponents.BLOCK_ENTITY_DATA);
        helper.assertTrue(saved != null, "drop component decodes");
        helper.assertTrue(saved.copyTag().hasUUID("slotOwner"), "persistent slot owner saved");
        helper.assertTrue(saved.copyTag().hasUUID("placementIdentity"), "old placement identity saved");
        helper.assertTrue(!ledger.snapshot().locations().containsKey(itemToken), "removed BE retires active ledger claim");
        helper.assertTrue(oldRuntimeSlot.isRetired()
                        && !oldRuntimeSlot.mutate(oldRuntimeSlot.snapshot().revision(), itemToken, ignored -> true)
                        && oldRuntimeSlot.eject(oldRuntimeSlot.snapshot().revision(), ledger.snapshot().revision(), handLocation).isEmpty(),
                "removed runtime slot cannot mutate or eject dormant payload");

        actor.setItemInHand(InteractionHand.MAIN_HAND, machineItem);
        BlockPos support = pos.below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false);
        var result = ModItems.CHEM_MASTER.get().useOn(new net.minecraft.world.item.context.UseOnContext(actor, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(result.consumesAction(), "saved machine item places");
        ChemMasterBlockEntity replacement = (ChemMasterBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(replacement != null, "replacement BE placed");
        helper.assertTrue(owner.equals(replacement.ownerId()), "slot owner survives break/re-place");
        helper.assertTrue(!placement.equals(replacement.placementIdentity()), "placement incarnation is fresh");
        helper.assertTrue(replacement.isLoadedValid(), "replacement state loads valid");
        var restored = replacement.inputSlot();
        helper.assertTrue(restored != null && itemToken.equals(restored.snapshot().token())
                        && ItemStack.matches(beaker, restored.snapshot().stack()),
                "break/re-place restores exact beaker token and components");
        helper.assertTrue(ledger.snapshot().locations().get(itemToken)
                        .equals(new ItemLocation.MachineSlot(owner, "beakerSlot")),
                "new active runtime instance reclaims exact slot owner");
        helper.assertTrue(savedBuffer.equals(replacement.buffer().snapshot()), "inert buffer survives break/re-place");
        helper.succeed();
    }
}
