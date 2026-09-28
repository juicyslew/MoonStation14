package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Places one tier-specific cable record on the exact clicked host face. */
public final class CableSpoolItem extends Item {
    private final CableTier tier;
    public CableSpoolItem(Properties properties, CableTier tier) { super(properties); this.tier = tier; }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        var player = context.getPlayer();
        var pos = context.getClickedPos();
        if (!(context.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || !allowed(context, player)
                || !CableStorage.place(level, pos, context.getClickedFace(), tier)) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    static boolean allowed(UseOnContext context, net.minecraft.world.entity.player.Player player) {
        return player != null && !player.isSpectator()
                && player.distanceToSqr(context.getClickedPos().getX() + 0.5,
                context.getClickedPos().getY() + 0.5, context.getClickedPos().getZ() + 0.5) <= 64.0
                && context.getLevel().mayInteract(player, context.getClickedPos())
                && player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), context.getItemInHand());
    }
}
