package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"guns", "capacitor-recharger-drain"})
@PrefixGameTestTemplate(false)
public final class CapacitorRechargerDrainGameTests {
    private CapacitorRechargerDrainGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void breakingStationaryChargerDropsItsOwnedStackExactlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.WEAPON_CAPACITOR_RECHARGER.get().defaultBlockState(), 3);
        helper.runAfterDelay(2, () -> {
            var machine = (WeaponCapacitorRechargerBlockEntity) level.getBlockEntity(pos);
            var slot = machine.slot();
            var item = new ItemStack(ModItems.WEAPON_ENERGY_SHOTGUN.get());
            item.set(ModDataComponents.BATTERY_STATE.get(), new BatteryState(173));
            helper.assertTrue(slot.initialize(slot.snapshot().revision(), item), "charger slot claims actual item once");
            var before = slot.snapshot();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            var drops = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2), entity -> true);
            helper.assertTrue(drops.size() == 1, "one physical stack leaves the removed charger");
            helper.assertTrue(ItemStack.isSameItemSameComponents(before.stack(), drops.get(0).getItem()),
                    "drop retains item identity and charge component");
            helper.assertTrue(LevelItemLocationLedgers.forLevel(level).snapshot().locationOf(before.token()).isEmpty(),
                    "slot/world staging claim is retired after successful publication");
            helper.succeed();
        });
    }
}
