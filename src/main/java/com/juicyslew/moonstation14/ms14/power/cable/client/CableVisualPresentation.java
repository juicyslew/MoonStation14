package com.juicyslew.moonstation14.ms14.power.cable.client;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.core.Direction;

/** Pure, deterministic cable display palette and face-local wire orientation. */
public final class CableVisualPresentation {
    private CableVisualPresentation() { }

    public static Rgb color(CableTier tier) {
        return switch (tier) {
            case HV -> new Rgb(1F, .24F, .12F);
            case MV -> new Rgb(1F, .78F, .12F);
            // SS14 CableApcExtension is Green (0, 128, 0), not cyan.
            case APC -> new Rgb(0F, 128F / 255F, 0F);
        };
    }

    /** Long axis for the face's thin wire strip; perpendicular to its face normal. */
    public static float wireAxisX(Direction face) {
        return face.getAxis() == Direction.Axis.X ? 0F : 1F;
    }

    public static float wireAxisZ(Direction face) {
        return face.getAxis() == Direction.Axis.X ? 1F : 0F;
    }

    public record Rgb(float red, float green, float blue) { }
}
