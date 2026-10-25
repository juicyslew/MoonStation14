package com.juicyslew.moonstation14.ms14.radiation.control.client;

import com.juicyslew.moonstation14.ms14.radiation.control.RadiationCollectorControlSnapshot;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small non-pausing, snapshot-only context action for a radiation collector. */
public final class RadiationCollectorContextScreen extends Screen {
    private final RadiationCollectorControlSnapshot snapshot;
    private static final int W = 154, H = 70;

    public RadiationCollectorContextScreen(RadiationCollectorControlSnapshot snapshot) {
        super(Component.translatable("gui.moonstation14.radiation_collector.title"));
        this.snapshot = snapshot;
    }

    RadiationCollectorControlSnapshot snapshot() { return snapshot; }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void tick() {
        closeIfInvalid();
        super.tick();
    }

    private void closeIfInvalid() {
        if (!RadiationCollectorControlClient.STATE.owns(snapshot)) {
            if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
            RadiationCollectorControlClient.STATE.closeOwned(snapshot);
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        closeIfInvalid();
        if (minecraft == null || minecraft.screen != this) return;
        int x = (width - W) / 2, y = (height - H) / 2;
        MachineWindowRenderer.window(graphics, font, x, y, W, H, title);
        if (snapshot.ejectAvailable()) {
            graphics.fill(x + 8, y + 36, x + W - 8, y + 59, 0xff26363d);
            graphics.drawString(font, Component.translatable("gui.moonstation14.radiation_collector.eject"), x + 16, y + 43, MachineWindowRenderer.TEXT, true);
        } else graphics.drawString(font, Component.translatable("gui.moonstation14.radiation_collector.empty"), x + 10, y + 42, MachineWindowRenderer.MUTED_TEXT, true);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && snapshot.ejectAvailable()) {
            int x = (width - W) / 2, y = (height - H) / 2;
            if (mouseX >= x + 8 && mouseX < x + W - 8 && mouseY >= y + 36 && mouseY < y + 59)
                com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestRadiationCollectorEject(snapshot);
        }
        if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
        RadiationCollectorControlClient.STATE.close();
        return true;
    }

    @Override public void onClose() {
        RadiationCollectorControlClient.STATE.closeOwned(snapshot);
        super.onClose();
    }
}
