package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LiveEquipmentStateGameTests {
    private LiveEquipmentStateGameTests() { }

    @GameTest(template = "empty", batch = "equipment_state", timeoutTicks = 20)
    public static void boundHumanOwnsOrderedEquipmentInSameAttachment(GameTestHelper helper) {
        var human = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        var pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands empty = LiveHands.initialize(human).orElseThrow();
            var slots = LiveHands.equipmentSnapshot(human).orElseThrow();
            require(slots.persisted() && slots.slots().stream().map(LiveHands.EquipmentSlot::id)
                    .toList().equals(List.of("belt", "back"))
                    && slots.slots().stream().allMatch(slot -> slot.token().isEmpty() && slot.stack().isEmpty()),
                    "human has saved, empty ordered slots");
            require(LiveHands.equipmentSnapshot(pig).isEmpty() && LiveHands.initialize(pig).isEmpty(),
                    "pig has no equipment capability");
            LiveHands held = empty.putWhole(0, "left", new ItemToken("owned"),
                    new ItemStack(Items.DIAMOND, 4)).state();
            human.setData(ModDataAttachments.LIVE_HANDS.get(), held);
            LiveHands equipped = held.equip(1, human, "left", "belt", "owned").orElseThrow();
            require(human.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == held
                    && equipped.revision() == 2 && equipped.stackCopy("left").isEmpty(),
                    "pure transition does not publish partial ownership");
            human.setData(ModDataAttachments.LIVE_HANDS.get(), equipped);
            ItemStack copy = LiveHands.equipmentSnapshot(human).orElseThrow().slots().get(0).stack().orElseThrow();
            copy.setCount(1);
            require(LiveHands.equipmentSnapshot(human).orElseThrow().slots().get(0).stack().orElseThrow()
                    .getCount() == 4 && equipped.equip(2, human, "right", "belt", "owned").isEmpty(),
                    "defensive read and occupied denial");
            LiveHands removed = equipped.unequip(2, human, "belt", "right", "owned").orElseThrow();
            require(removed.revision() == 3 && removed.stackCopy("right").orElseThrow().getCount() == 4
                    && equipped.stackCopy("right").isEmpty(), "one revision preserves exact occupant");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
