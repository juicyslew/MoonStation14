package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.chat.Component;

/** Cuts only the exact clicked host face and returns the matching spool. */
public final class CableCutterItem extends Item {
    public CableCutterItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        var player = context.getPlayer();
        var pos = context.getClickedPos();
        if (!(context.getLevel() instanceof ServerLevel level) || !CableSpoolItem.allowed(context, player))
            return InteractionResult.FAIL;
        var offhand = player.getOffhandItem();
        CableTier selected = tierForSpool(offhand);
        int count = 0;
        for (CableTier candidate : CableTier.values())
            if (CableStorage.has(level, pos, context.getClickedFace(), candidate)) count++;
        if (selected == null && count > 1) {
            player.displayClientMessage(Component.translatable("message.moonstation14.cable_cutter.ambiguous"), true);
            return InteractionResult.FAIL;
        }
        CableTier tier = selected == null ? CableStorage.remove(level, pos, context.getClickedFace())
                : CableStorage.remove(level, pos, context.getClickedFace(), selected);
        if (tier == null) return InteractionResult.FAIL;
        ItemStack returned = new ItemStack(switch (tier) {
            case HV -> ModItems.HV_CABLE_SPOOL.get();
            case MV -> ModItems.MV_CABLE_SPOOL.get();
            case APC -> ModItems.APC_CABLE_SPOOL.get();
        });
        if (!player.getInventory().add(returned)) player.drop(returned, false);
        return InteractionResult.CONSUME;
    }

    private static CableTier tierForSpool(ItemStack stack) {
        if (stack.is(ModItems.HV_CABLE_SPOOL.get())) return CableTier.HV;
        if (stack.is(ModItems.MV_CABLE_SPOOL.get())) return CableTier.MV;
        if (stack.is(ModItems.APC_CABLE_SPOOL.get())) return CableTier.APC;
        return null;
    }
}
