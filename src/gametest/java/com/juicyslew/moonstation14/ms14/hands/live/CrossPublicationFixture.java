package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Builds the normal live-hands fixture within its package-level state API. */
public final class CrossPublicationFixture {
    private CrossPublicationFixture() { }
    public static LiveHands initialize(LivingEntity body) {
        LiveHands state = LiveHands.initialize(body).orElseThrow();
        ItemStack bag = new ItemStack(ModItems.BAG.get());
        bag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
        ItemStack belt = new ItemStack(ModItems.BELT.get());
        belt.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
        state = state.putWhole(state.revision(), "left", new ItemToken("bag"), bag).state();
        List<String> declaredSlots = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                .map(LiveHands.EquipmentSlot::id).toList();
        state = state.equip(state.revision(), declaredSlots, "left", "back", "bag").orElseThrow();
        state = state.putWhole(state.revision(), "left", new ItemToken("belt"), belt).state();
        state = state.putWhole(state.revision(), "right", new ItemToken("stick"), new ItemStack(Items.STICK)).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), state);
        return state;
    }
}
