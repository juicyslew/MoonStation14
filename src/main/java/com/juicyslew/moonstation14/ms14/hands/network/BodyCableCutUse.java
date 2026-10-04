package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.power.cable.CableCutSelection;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Body-owned cutter operation: a cable record is exchanged only for its world spool. */
public final class BodyCableCutUse {
    private BodyCableCutUse() { }

    public static BodyBlockUseResult.Outcome use(BodyUseItemContext context) {
        if (!(context.authority().body().level() instanceof ServerLevel level)) return BodyBlockUseResult.Outcome.DENIED;
        var pos = context.pos();
        ItemStack held = context.stack();
        if (held.isEmpty() || held.getCount() != 1 || context.actor().isSpectator()
                || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(context.actor(), pos)
                || context.actor().distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64
                || BodyHeldItemMutation.preview(context.actor(), context.authority(), context.hands(), context.handId(),
                context.token(), context.revision(), held, held).isEmpty()) return BodyBlockUseResult.Outcome.DENIED;
        var found = java.util.EnumSet.noneOf(CableTier.class);
        for (CableTier tier : CableTier.values()) if (CableStorage.has(level, pos, context.face(), tier)) found.add(tier);
        var choice = CableCutSelection.select(found, null);
        if (!choice.allowed()) return BodyBlockUseResult.Outcome.DENIED;
        CableTier tier = CableStorage.remove(level, pos, context.face(), choice.tier());
        if (tier == null) return BodyBlockUseResult.Outcome.DENIED;
        ItemStack spool = new ItemStack(switch (tier) {
            case HV -> ModItems.HV_CABLE_SPOOL.get();
            case MV -> ModItems.MV_CABLE_SPOOL.get();
            case APC -> ModItems.APC_CABLE_SPOOL.get();
        });
        ItemEntity output = new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, spool);
        if (!level.addFreshEntity(output)) {
            if (!CableStorage.place(level, pos, context.face(), tier)) return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            return BodyBlockUseResult.Outcome.DENIED;
        }
        return BodyBlockUseResult.Outcome.PLACED;
    }
}
