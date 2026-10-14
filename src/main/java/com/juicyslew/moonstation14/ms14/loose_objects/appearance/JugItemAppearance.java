package com.juicyslew.moonstation14.ms14.loose_objects.appearance;

import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.world.level.Level;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.JugItem;

/** Shared jug fill-tier and liquid-color presentation derived from its authoritative reagent component. */
public final class JugItemAppearance {
    private JugItemAppearance() { }

    public static int fillTier(ReagentComponent reagent, int capacityCents) {
        if (reagent == null || capacityCents <= 0) return 0;
        long total = reagent.centContents().values().stream().mapToLong(Long::longValue).sum();
        return (int) Math.min(6L, total * 6L / capacityCents);
    }

    public static int color(ReagentComponent reagent, Level level) {
        if (reagent == null || reagent.centContents().isEmpty()) return 0xffffffff;
        int color = ReagentComponent.getBlendedColor(reagent.contents(), level);
        return resolveColor(color);
    }

    public static int capacityCents() {
        var item = ModItems.JUG.get();
        return item instanceof com.juicyslew.moonstation14.item.custom.JugItem jug
                ? Math.round(jug.getCapacity() * 100f) : 15000;
    }

    /** Resolves capacity on the same logical side as the world/render context. */
    public static int capacityCents(Level level) {
        var item = ModItems.JUG.get();
        return item instanceof JugItem jug ? Math.round(jug.getCapacity(level) * 100f) : 20000;
    }

    /** Focused fixture/runtime helper for an already selected jug item instance. */
    public static int capacityCents(JugItem jug, Level level) {
        return Math.round(jug.getCapacity(level) * 100f);
    }

    static int resolveColor(int color) { return color == -1 ? 0xffffffff : color; }
}

