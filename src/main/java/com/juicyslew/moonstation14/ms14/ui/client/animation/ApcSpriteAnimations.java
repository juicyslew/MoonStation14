package com.juicyslew.moonstation14.ms14.ui.client.animation;

import com.juicyslew.moonstation14.ms14.ui.model.animation.LayeredSpriteAnimation;
import com.juicyslew.moonstation14.ms14.ui.model.animation.SpriteSheet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Imported APC RSI metadata. State selection is the caller's responsibility, not a power heuristic. */
public final class ApcSpriteAnimations {
    // RobustToolbox RsiDirection: South=0, North=1, East=2, West=3.
    public static final int SOUTH = 0, NORTH = 1, EAST = 2, WEST = 3;
    public static final SpriteSheet BASE = new SpriteSheet("base", 64, 64, 32, 32,
            repeated(1000));
    public static final SpriteSheet FULL = new SpriteSheet("display-full", 64, 128, 32, 32,
            repeated(1000, 1000));
    public static final SpriteSheet CHARGING = new SpriteSheet("display-charging", 256, 128, 32, 32,
            repeated(100, 100, 100, 100, 100, 100, 100, 200));
    public static final SpriteSheet LACK = new SpriteSheet("display-lack", 128, 128, 32, 32,
            repeated(250, 250, 1000, 3000));

    private static final String ROOT = "textures/gui/apc/animation/";
    public static final ResourceLocation BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath("moonstation14", ROOT + "base.png");
    public static final ResourceLocation FULL_TEXTURE = ResourceLocation.fromNamespaceAndPath("moonstation14", ROOT + "display-full.png");
    public static final ResourceLocation CHARGING_TEXTURE = ResourceLocation.fromNamespaceAndPath("moonstation14", ROOT + "display-charging.png");
    public static final ResourceLocation LACK_TEXTURE = ResourceLocation.fromNamespaceAndPath("moonstation14", ROOT + "display-lack.png");
    public static final GuiSpriteAnimationRenderer GUI = new GuiSpriteAnimationRenderer(Map.of(
            BASE.state(), BASE_TEXTURE, FULL.state(), FULL_TEXTURE,
            CHARGING.state(), CHARGING_TEXTURE, LACK.state(), LACK_TEXTURE));

    private ApcSpriteAnimations() {}

    /** Base below a selected display; unknown display states must be resolved by the caller. */
    public static LayeredSpriteAnimation withDisplay(SpriteSheet display, boolean displayVisible) {
        Objects.requireNonNull(display, "display");
        if (display != FULL && display != CHARGING && display != LACK)
            throw new IllegalArgumentException("Unknown APC display descriptor");
        return new LayeredSpriteAnimation(List.of(
                new LayeredSpriteAnimation.Layer(BASE, true),
                new LayeredSpriteAnimation.Layer(display, displayVisible)));
    }

    private static int[][] repeated(int... milliseconds) {
        return new int[][] { milliseconds.clone(), milliseconds.clone(), milliseconds.clone(), milliseconds.clone() };
    }
}
