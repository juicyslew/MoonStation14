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

    /** Minecraft held-model adaptation of the existing world mouse texture/pose policy. */
    public static HeldPresentation heldPresentation(com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance view) {
        var variant = switch (view.coat()) {
            case WHITE -> MouseAppearanceComponent.Variant.WHITE;
            case BROWN -> MouseAppearanceComponent.Variant.BROWN;
            case GRAY -> MouseAppearanceComponent.Variant.GRAY;
            case NONE -> throw new IllegalArgumentException("mouse held presentation cannot consume a non-mouse descriptor");
        };
        boolean dead = view.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.DEAD;
        boolean nonAlive = view.state() != com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.ALIVE;
        return new HeldPresentation(texture(variant, dead), nonAlive ? "down_hold" : "rest", dead);
    }

    public record HeldPresentation(ResourceLocation texture, String pose, boolean dead) { }
}
