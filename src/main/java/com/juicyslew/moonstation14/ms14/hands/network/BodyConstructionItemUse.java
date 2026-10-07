package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.item.custom.CableSpoolItem;
import com.juicyslew.moonstation14.item.custom.WirecutterItem;
import com.juicyslew.moonstation14.ms14.hands.live.BodyBlockItemPlacement;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Body-owned implementations for the existing construction-item use behaviors. */
public final class BodyConstructionItemUse {
    private BodyConstructionItemUse() { }

    public static BodyBlockUseResult.Outcome use(BodyUseItemContext context) {
        if (BodyFloorToolUse.supports(context.stack())) return BodyFloorToolUse.use(context);
        if (context.stack().getItem() instanceof CableSpoolItem spool)
            return placeCable(context, spool);
        if (context.stack().getItem() instanceof WirecutterItem)
            return BodyCableCutUse.use(context);
        if (context.stack().getItem() instanceof BodyMetalTool tool) return tool.use(context);
        return BodyBlockUseResult.Outcome.DENIED;
    }

    private static BodyBlockUseResult.Outcome placeCable(BodyUseItemContext context, CableSpoolItem spool) {
        if (!(context.authority().body().level() instanceof ServerLevel level)) return BodyBlockUseResult.Outcome.DENIED;
        ItemStack before = context.stack();
        ItemStack after = before.copy();
        after.shrink(1);
        if (BodyHeldItemMutation.preview(context.actor(), context.authority(), context.hands(), context.handId(),
                context.token(), context.revision(), before, after).isEmpty()
                || !CableStorage.place(level, context.pos(), context.face(), spool.bodyUseTier()))
            return BodyBlockUseResult.Outcome.DENIED;
        var committed = BodyHeldItemMutation.commit(context.actor(), context.authority(), context.hands(),
                context.handId(), context.token(), context.revision(), before, after);
        return switch (committed) {
            case SUCCESS -> BodyBlockUseResult.Outcome.PLACED;
            case DENIED -> {
                if (CableStorage.remove(level, context.pos(), context.face(), spool.bodyUseTier()) == null)
                    yield BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
                yield BodyBlockUseResult.Outcome.DENIED;
            }
            case RECOVERY_REQUIRED -> BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
        };
    }
}
