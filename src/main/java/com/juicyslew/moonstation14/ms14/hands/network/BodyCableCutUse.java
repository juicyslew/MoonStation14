package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.item.custom.CableSpoolItem;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.power.cable.CableCutSelection;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
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
        // CHARACTER carriers are deliberately Minecraft spectators; authenticated body
        // admission/hand ownership, not the carrier's vanilla gamemode, gates this action.
        if (held.isEmpty() || held.getCount() != 1
                || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(context.actor(), pos)
                || BodyHeldItemMutation.preview(context.actor(), context.authority(), context.hands(), context.handId(),
                context.token(), context.revision(), held, held).isEmpty()) return BodyBlockUseResult.Outcome.DENIED;
        var choice = CableCutSelection.selectAt(level, pos, context.face(), context.hitLocation());
        if (!choice.allowed()) return BodyBlockUseResult.Outcome.DENIED;
        var tier = CableStorage.remove(level, pos, context.face(), choice.tier());
        if (tier == null) return BodyBlockUseResult.Outcome.DENIED;
        ItemStack spool = CableSpoolItem.freshStack(tier);
        var hit = context.hitLocation();
        double x = hit.x + context.face().getStepX() * .25;
        double y = hit.y + context.face().getStepY() * .25;
        double z = hit.z + context.face().getStepZ() * .25;
        ItemEntity output = new ItemEntity(level, x, y, z, spool);
        if (!level.addFreshEntity(output)) {
            if (!CableStorage.place(level, pos, context.face(), tier)) return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            return BodyBlockUseResult.Outcome.DENIED;
        }
        return BodyBlockUseResult.Outcome.PLACED;
    }
}

