package com.juicyslew.moonstation14.ms14.ui.client.collection;

import com.juicyslew.moonstation14.ms14.ui.model.BoundedViewportModel;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Pan/zoom sample viewport. Only supplied points are drawn; no interpolated samples or edges. */
public final class SampleViewportWidget extends AbstractWidget {
    private final BoundedViewportModel model;
    private final double worldWidth;
    private final double worldHeight;
    private boolean panning;

    /** World dimensions must match the bounds used to construct the model. */
    public SampleViewportWidget(int x, int y, int width, int height, BoundedViewportModel model,
                                double worldWidth, double worldHeight) {
        super(x, y, width, height, Component.literal("Sample viewport"));
        this.model = Objects.requireNonNull(model);
        if (!Double.isFinite(worldWidth) || !Double.isFinite(worldHeight) || worldWidth <= 0 || worldHeight <= 0)
            throw new IllegalArgumentException("invalid world bounds");
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xff101d25);
        graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        try {
            double sx = getWidth() * model.zoom() / worldWidth;
            double sy = getHeight() * model.zoom() / worldHeight;
            for (int i = 0; i < model.size(); i++) {
                BoundedViewportModel.Point point = model.at(i);
                if (!model.visible(point)) continue;
                int px = getX() + (int) Math.round(getWidth() / 2.0 + (point.x() - model.centerX()) * sx);
                int py = getY() + (int) Math.round(getHeight() / 2.0 + (point.y() - model.centerY()) * sy);
                graphics.fill(px - 2, py - 2, px + 2, py + 2, 0xff8edbc5);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mx, my)) return false;
        setFocused(true);
        panning = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!panning || button != 0 || getWidth() <= 0 || getHeight() <= 0) return false;
        model.view(model.centerX() - dx * worldWidth / (getWidth() * model.zoom()),
                model.centerY() - dy * worldHeight / (getHeight() * model.zoom()), model.zoom());
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0 || !panning) return false;
        panning = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!active || !visible || !isMouseOver(mx, my) || vertical == 0) return false;
        model.view(model.centerX(), model.centerY(), model.zoom() * (vertical > 0 ? 1.25 : 0.8));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused()) return false;
        double stepX = worldWidth / (8 * model.zoom());
        double stepY = worldHeight / (8 * model.zoom());
        switch (key) {
            case 262 -> model.view(model.centerX() + stepX, model.centerY(), model.zoom());
            case 263 -> model.view(model.centerX() - stepX, model.centerY(), model.zoom());
            case 264 -> model.view(model.centerX(), model.centerY() + stepY, model.zoom());
            case 265 -> model.view(model.centerX(), model.centerY() - stepY, model.zoom());
            case 334, 61 -> model.view(model.centerX(), model.centerY(), model.zoom() * 1.25);
            case 333, 45 -> model.view(model.centerX(), model.centerY(), model.zoom() * 0.8);
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
