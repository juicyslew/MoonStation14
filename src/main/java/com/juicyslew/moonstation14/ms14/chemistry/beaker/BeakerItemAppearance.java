package com.juicyslew.moonstation14.ms14.chemistry.beaker;

import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.world.level.Level;

/** Beaker-specific projection of SS14 RoundToLevels; jug tiering intentionally remains distinct. */
public final class BeakerItemAppearance {
    private BeakerItemAppearance() { }

    public static int fillTier(ReagentComponent reagent, BeakerData data) {
        if (!supported(data) || reagent == null || reagent.centContents().isEmpty()) return 0;
        long amount = reagent.centContents().values().stream().mapToLong(Long::longValue).sum();
        long capacity = (long) data.solutionContainer().maxVolume() * 100L;
        if (amount <= 0) return 0;
        if (amount >= capacity) return data.solutionVisuals().maxFillLevels();
        long numerator = amount * (data.solutionVisuals().maxFillLevels() - 1L);
        return (int) Math.max(1L, (numerator + capacity - 1L) / capacity);
    }

    public static int color(ReagentComponent reagent, BeakerData data, Level level) {
        if (!supported(data) || !data.solutionVisuals().changeColor() || reagent == null || reagent.contents().isEmpty()) return -1;
        int color = ReagentComponent.getBlendedColor(reagent.contents(), level);
        return color == -1 ? -1 : color;
    }

    public static boolean supported(BeakerData data) {
        return data != null && data.solutionVisuals() != null
                && data.solutionVisuals().solutionName().equals(data.solutionContainer().solutionName())
                && data.solutionContainer().solutionName().equals("beaker");
    }
}
