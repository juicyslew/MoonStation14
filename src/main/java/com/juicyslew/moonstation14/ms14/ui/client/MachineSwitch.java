package com.juicyslew.moonstation14.ms14.ui.client;

import java.util.function.Consumer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/** Client presentation only: the callback requests intent; server snapshots must call setPresentedState. */
public final class MachineSwitch extends net.minecraft.client.gui.components.AbstractButton {
    private static final int OUTLINE = 0xff10191d;
    private static final int DISABLED = 0xff5c686c;
    private static final int OFF = 0xffb85b48;
    private static final int ON = 0xff5bbd80;
    private static final int THUMB = 0xffe4e9e8;

    private final Component label;
    private final Component onMessage;
    private final Component offMessage;
    private final Consumer<Boolean> onToggle;
    private boolean presentedOn;

    public MachineSwitch(int x, int y, int width, int height, Component label, Component onText,
                         Component offText, boolean presentedOn, Consumer<Boolean> onToggle) {
        super(x, y, width, height, label);
        this.label = label;
        onMessage = Component.empty().append(label).append(" ").append(onText);
        offMessage = Component.empty().append(label).append(" ").append(offText);
        this.onToggle = onToggle;
        setPresentedState(presentedOn, true);
    }

    /** Does not invoke the callback. Call this on every authoritative or optimistic UI update. */
    public void setPresentedState(boolean on, boolean enabled) {
        this.presentedOn = on;
        active = enabled;
        // Vanilla button narration reads the current message; use cached state labels.
        setMessage(on ? onMessage : offMessage);
    }

    public boolean presentedOn() {
        return presentedOn;
    }

    /** Uses vanilla hover/focus tooltip and narration. */
    public void setSwitchTooltip(Component tooltip) {
        setTooltip(tooltip == null ? null : net.minecraft.client.gui.components.Tooltip.create(tooltip));
    }

    @Override
    public void onPress() {
        if (active) onToggle.accept(!presentedOn);
    }

    /** Mouse and keyboard activation share this vanilla hook; only server-approved actions emit switch audio. */
    @Override
    public void playDownSound(SoundManager soundManager) {
        // The server plays the positional machine switch sound after accepting the request.
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        if (w < 20 || h < 10) return;
        int edge = isHoveredOrFocused() ? 0xffd2e2e7 : OUTLINE;
        graphics.fill(x, y, x + w, y + h, edge);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xff24343b);
        int trackWidth = Math.min(34, w - 8);
        int trackX = x + w - trackWidth - 4;
        int trackY = y + (h - 12) / 2;
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 12,
                !active ? DISABLED : presentedOn ? ON : OFF);
        int thumbX = presentedOn ? trackX + trackWidth - 11 : trackX + 2;
        graphics.fill(thumbX, trackY + 2, thumbX + 9, trackY + 10, THUMB);
        if (trackX > x + 6) {
            graphics.enableScissor(x + 4, y + 1, trackX - 2, y + h - 1);
            graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, label,
                    x + 5, y + (h - 8) / 2, active ? MachineWindowRenderer.TEXT : DISABLED, false);
            graphics.disableScissor();
        }
    }
}
