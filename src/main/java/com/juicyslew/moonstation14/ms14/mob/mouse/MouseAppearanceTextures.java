package com.juicyslew.moonstation14.ms14.mob.mouse;

import com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent;
import net.minecraft.resources.ResourceLocation;

/** Pure authored-sprite selector shared by the client renderer and server-safe tests. */
public final class MouseAppearanceTextures {
    private MouseAppearanceTextures() { }

    public static ResourceLocation texture(MouseAppearanceComponent.Variant variant, boolean dead) {
        String color = switch (variant) {
            case WHITE -> "white";
            case BROWN -> "brown";
            case GRAY -> "gray";
        };
        return ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/entity/mouse/mouse_"
                + color + (dead ? "_dead" : "") + ".png");
    }
}
