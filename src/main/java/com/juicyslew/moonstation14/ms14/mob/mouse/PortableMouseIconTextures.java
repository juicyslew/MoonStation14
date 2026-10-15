package com.juicyslew.moonstation14.ms14.mob.mouse;

import com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance;
import net.minecraft.resources.ResourceLocation;

/** SS14 mouse RSI single-frame selector for portable inventory/HUD item views. */
public final class PortableMouseIconTextures {
    private PortableMouseIconTextures() { }
    public static ResourceLocation texture(PortableBodyAppearance.State state) {
        return texture(state, PortableBodyAppearance.Coat.WHITE);
    }
    public static ResourceLocation texture(PortableBodyAppearance.State state, PortableBodyAppearance.Coat coat) {
        String frame = switch (state) {
            case ALIVE -> "icon";
            case CRITICAL -> "dead";
            case DEAD -> "splat";
        };
        return ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/entity/mouse/icon/" + frame + "-" + variantColumn(coat) + ".png");
    }
    public static int variantColumn(PortableBodyAppearance.Coat coat) {
        return switch (coat) { case WHITE -> 0; case BROWN -> 1; case GRAY -> 2; };
    }

    /** Complete production icon blit selection, shared by the live renderer and CPU evidence. */
    public static Frame frame(PortableBodyAppearance view) {
        var id = texture(view.state(), view.coat());
        return new Frame(id, 0, 0, 32, 32, 32, 32, 16, 16);
    }

    public record Frame(ResourceLocation texture, int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                       int sheetWidth, int sheetHeight, int drawWidth, int drawHeight) { }
}
