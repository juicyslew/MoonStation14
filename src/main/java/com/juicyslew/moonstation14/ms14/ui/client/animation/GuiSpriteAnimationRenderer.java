package com.juicyslew.moonstation14.ms14.ui.client.animation;

import com.juicyslew.moonstation14.ms14.ui.model.animation.LayeredSpriteAnimation;
import com.juicyslew.moonstation14.ms14.ui.model.animation.SpriteSheet;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** GUI-scaled sprite adapter. Core geometry/timing can also drive a separate 3D UV adapter. */
public final class GuiSpriteAnimationRenderer {
    private final Map<String, ResourceLocation> textures;

    /** Cache this renderer and its resource locations; no images are decoded or uploaded per draw. */
    public GuiSpriteAnimationRenderer(Map<String, ResourceLocation> textures) {
        this.textures = Map.copyOf(textures);
    }

    public void draw(GuiGraphics graphics, LayeredSpriteAnimation animation, int direction,
                     long elapsedMillis, int x, int y, int width, int height) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(animation, "animation");
        if (width <= 0 || height <= 0) return;
        for (int i = 0; i < animation.size(); i++) {
            LayeredSpriteAnimation.Layer layer = animation.layer(i);
            if (!layer.visible()) continue;
            SpriteSheet sheet = layer.sheet();
            ResourceLocation texture = textures.get(sheet.state());
            if (texture == null) throw new IllegalArgumentException("No texture for state: " + sheet.state());
            int frame = sheet.frameAt(sheet.directions() == 1 ? 0 : direction, elapsedMillis);
            graphics.blit(texture, x, y, width, height, (float) sheet.u(sheet.directions() == 1 ? 0 : direction, frame),
                    (float) sheet.v(sheet.directions() == 1 ? 0 : direction, frame), sheet.frameWidth(),
                    sheet.frameHeight(), sheet.width(), sheet.height());
        }
    }
}
