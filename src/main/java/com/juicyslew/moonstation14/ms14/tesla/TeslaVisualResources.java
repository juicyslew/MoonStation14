package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.ms14.ui.model.animation.SpriteSheet;
import net.minecraft.resources.ResourceLocation;

/** Shared, server-safe Tesla texture IDs and source-sheet animation layouts. */
public final class TeslaVisualResources {
    public static final ResourceLocation MAIN = texture("structures/power/generation/tesla/energy_ball.rsi/energy_ball.png");
    public static final ResourceLocation MINI = texture("structures/power/generation/tesla/energy_miniball.rsi/tesla_projectile.png");
    public static final ResourceLocation LIGHTNING = texture("effects/lightning.rsi/lightning_1.png");
    public static final ResourceLocation EMP_PULSE = texture("effects/emp.rsi/emp_pulse.png");
    public static final com.juicyslew.moonstation14.ms14.ui.model.animation.SpriteSheet EMP_PULSE_SHEET =
            new com.juicyslew.moonstation14.ms14.ui.model.animation.SpriteSheet("emp_pulse",96,96,32,32,new int[][]{{100,100,100,100,100,100,100,100}});

    public static final SpriteSheet MAIN_SHEET = new SpriteSheet("energy_ball", 288, 384, 96, 96,
            new int[][]{{120,40,120,80,40,120,40,120,80,40,120,40}});
    public static final SpriteSheet MINI_SHEET = new SpriteSheet("tesla_projectile", 96, 128, 32, 32,
            new int[][]{{50,80,80,80,80,80,80,80,80,80,80,80}});

    private TeslaVisualResources() { }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/ss14/" + path);
    }
}
