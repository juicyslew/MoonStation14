package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.power.cable.CableCutSelection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Cuts only the exact clicked host face and returns the matching spool. */
public final class WirecutterItem extends Item {
    public WirecutterItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        var player = context.getPlayer();
        var pos = context.getClickedPos();
        if (!(context.getLevel() instanceof ServerLevel level) || !CableSpoolItem.allowed(context, player))
            return InteractionResult.FAIL;
        var choice = CableCutSelection.selectAt(level, pos, context.getClickedFace(), context.getClickLocation());
        if (!choice.allowed()) return InteractionResult.FAIL;
        var tier = com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(
                level, pos, context.getClickedFace(), choice.tier());
        if (tier == null) return InteractionResult.FAIL;
        var returned = CableSpoolItem.freshStack(tier);
        if (!player.getInventory().add(returned)) player.drop(returned, false);
        return InteractionResult.CONSUME;
    }

}

