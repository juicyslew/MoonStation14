package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Persistent vertical action bar painted and routed in shared pointer-workspace order. */
public final class BodyActionHud {
    private static final Bar BAR = new Bar();
    private BodyActionHud() { }

    public static void render(RenderGuiEvent.Post event) {
        BAR.update();
    }
    static boolean pointerHostAllowed(Screen screen, boolean visible, boolean mouseGrabbed) {
        return BodyActionLayout.pointerHostAllowed(visible,
                PointerOverlayRegistry.allowsHost(screen)
                        || com.juicyslew.moonstation14.ms14.ui.client.GameplayHudPolicy.hudPaintsWorkspace(screen),
                mouseGrabbed);
    }
    private static final class Bar implements PointerOverlayWindow {
        private int width, height, left, top;
        private int projectionWidth = -1, projectionHeight = -1;
        private boolean visible;
        private java.util.UUID trackedBody;
        private long trackedEpoch;
        private java.util.List<BodyActionSlots.Slot> paintedSlots;
        private BodyActionHudProjection projection;
        private record ActionIdentity(net.minecraft.resources.ResourceLocation actionId, String providerId) { }
        private final java.util.Map<ActionIdentity, Long> previousCooldown = new java.util.HashMap<>();
        private final java.util.Map<ActionIdentity, Long> blinkUntil = new java.util.HashMap<>();
        void update() {
            var mc = Minecraft.getInstance();
            var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForHud();
            long epoch = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
            if (body == null || trackedBody == null || !trackedBody.equals(body.getUUID()) || trackedEpoch != epoch) {
                previousCooldown.clear(); blinkUntil.clear();
                projection = null; paintedSlots = null;
                trackedBody = body == null ? null : body.getUUID(); trackedEpoch = body == null ? 0 : epoch;
            }
            var snap = BodyActionClient.snapshot();
            visible = !mc.options.hideGui && mc.level != null && body != null && body.level() == mc.level
                    && snap != null && snap.bodyId().equals(body.getUUID())
                    && snap.bodyEpoch() == com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch()
                    && com.juicyslew.moonstation14.ms14.ui.client.GameplayHudPolicy.visible(mc.screen);
            width = mc.getWindow().getGuiScaledWidth(); height = mc.getWindow().getGuiScaledHeight();
            left = BodyActionLayout.actionBarLeft(); top = BodyActionLayout.actionBarTop();
            if (!visible) { projection = null; paintedSlots = null; PointerOverlayRegistry.unregister(this); }
            else PointerOverlayRegistry.register(this);
        }
        @Override public boolean visible() { return visible; }
        @Override public boolean pointerAvailable() {
            var mc = Minecraft.getInstance();
            return pointerHostAllowed(mc.screen, visible, mc.mouseHandler.isMouseGrabbed());
        }
        @Override public boolean contains(double x, double y) {
            if (!visible) return false;
            double lx = BodyActionLayout.toLogical(x, left), ly = BodyActionLayout.toLogical(y, top);
            return projection != null && projection.hit(lx, ly) != null;
        }
        @Override public void renderIntoGui(GuiGraphics g, int mx, int my, float partial, int w, int h) {
            update(); if (!visible) return;
            var slots = BodyActionClient.slots();
            if (slots != paintedSlots || projection == null
                    || projectionWidth != width || projectionHeight != height) {
                int aw = Math.max(0, (int)Math.floor((width-left)/BodyActionLayout.ACTION_UI_SCALE));
                int ah = Math.max(0, (int)Math.floor((height-top)/BodyActionLayout.ACTION_UI_SCALE));
                paintedSlots = slots; projectionWidth = width; projectionHeight = height;
                projection = BodyActionHudProjection.build(slots, aw, ah);
            }
            if (projection.cells().isEmpty()) return;
            g.pose().pushPose();
            g.pose().translate(left, top, 0);
            g.pose().scale(BodyActionLayout.ACTION_UI_SCALE, BodyActionLayout.ACTION_UI_SCALE, 1);
            java.util.List<net.minecraft.network.chat.Component> tooltip = null;
            for (var cell : projection.cells()) {
                var rect = cell.rect(); var entry = cell.entry();
                int x = rect.x(), y = rect.y();
                g.fill(x, y, x + 28, y + BodyActionLayout.ACTION_BAR_CELL_HEIGHT, 0xaa0d1118); g.fill(x, y, x + 28, y + 1, 0xff66717c);
                int logicalMouseX = (int)BodyActionLayout.actionBarPointer(mx, left), logicalMouseY = (int)BodyActionLayout.actionBarPointer(my, top);
                boolean hovered = rect.contains(logicalMouseX, logicalMouseY);
                if (hovered) g.fill(x, y, x + 28, y + BodyActionLayout.ACTION_BAR_CELL_HEIGHT, 0x665d7184);
                if (BodyActionPresentation.activePressedStyle(entry)) g.fill(x, y, x + BodyActionLayout.ACTION_BAR_CELL_WIDTH, y + BodyActionLayout.ACTION_BAR_CELL_HEIGHT, 0x554fc777);
                    int inset = BodyActionLayout.ACTION_BAR_ICON_INSET, iconSize = BodyActionLayout.ACTION_BAR_ICON;
                    g.blit(BodyActionPresentation.icon(entry), x + inset, y + inset, iconSize, iconSize, 0, 0, 32, 32, 32, 32);
                    if (!entry.enabled() || BodyActionClient.pending()) g.fill(x + inset, y + inset, x + inset + iconSize, y + inset + iconSize, 0x99000000);
                    long cd = BodyActionClient.remainingCooldown(entry);
                    long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
                    var identity = new ActionIdentity(entry.actionId(), entry.providerId());
                    long previous = previousCooldown.getOrDefault(identity, 0L);
                    if (previous > 0 && cd == 0) blinkUntil.put(identity, now + 2);
                    previousCooldown.put(identity, cd);
                    if (cd > 0) {
                        drawCooldownWipe(g, x + inset, y + inset, BodyActionLayout.cooldownWipeProgress(cd, entry.useDelayTicks()));
                        int seconds = (int)Math.ceil(cd / 20.0);
                        String text = Integer.toString(seconds);
                        g.drawString(Minecraft.getInstance().font, text, x + 14 - Minecraft.getInstance().font.width(text)/2, y + 10, 0xffffffff, true);
                    } else {
                        float blink = BodyActionLayout.cooldownBlinkProgress(blinkUntil.getOrDefault(identity, 0L) - now);
                        if (blink > 0) g.fill(x + inset, y + inset, x + inset + iconSize, y + inset + iconSize, ((int)(blink * 0x88) << 24) | 0x00ffffff);
                    }
                    if (hovered && pointerAvailable()) {
                        tooltip = new java.util.ArrayList<>();
                        tooltip.add(net.minecraft.network.chat.Component.translatable(entry.displayName()));
                        tooltip.add(BodyActionKeys.labelForSlot(rect.slotNumber()));
                        if (!entry.disabledReason().isBlank()) tooltip.add(net.minecraft.network.chat.Component.translatable(
                                "gui.moonstation14.actions.disabled." + entry.disabledReason().toLowerCase(java.util.Locale.ROOT)));
                        long remaining = BodyActionClient.remainingCooldown(entry);
                        if (remaining > 0) tooltip.add(net.minecraft.network.chat.Component.literal("Cooldown: "
                                + BodyActionLayout.cooldownSeconds(remaining)));
                        if (BodyActionClient.pending()) tooltip.add(net.minecraft.network.chat.Component.literal("Action pending"));
                    }
                String keyLabel = BodyActionKeys.compactLabelForSlot(rect.slotNumber());
                int maxLabelWidth = rect.labelWidth();
                while (!keyLabel.isEmpty() && Minecraft.getInstance().font.width(keyLabel) > maxLabelWidth)
                    keyLabel = keyLabel.substring(0, keyLabel.length() - 1);
                if (!keyLabel.isEmpty()) g.drawString(Minecraft.getInstance().font, keyLabel,
                        rect.labelX() + (rect.labelWidth() - Minecraft.getInstance().font.width(keyLabel)) / 2,
                        rect.labelY(), 0xffeeeeee, false);
            }
            g.pose().popPose();
            if (tooltip != null) g.renderTooltip(Minecraft.getInstance().font,
                    tooltip.stream().map(net.minecraft.network.chat.Component::getVisualOrderText).toList(),
                    BodyActionLayout.tooltipAnchor(mx), BodyActionLayout.tooltipAnchor(my));
        }
        private static void drawCooldownWipe(GuiGraphics g, int x, int y, float progress) {
            final int radius = 12;
            for (int py = -radius; py < radius; py++) for (int px = -radius; px < radius; px++) {
                if (px * px + py * py > radius * radius) continue;
                double angle = Math.atan2(py, px) / (Math.PI * 2.0) + .25;
                if (angle < 0) angle += 1;
                if (angle < progress) g.fill(x + radius + px, y + radius + py,
                        x + radius + px + 1, y + radius + py + 1, 0xaa000000);
            }
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (!contains(x,y)) return false;
            if (button != 0) return true;
            var mc = Minecraft.getInstance(); var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForHud();
            var snap = BodyActionClient.snapshot();
            if (body == null || mc.level == null || body.level() != mc.level || snap == null
                    || !snap.bodyId().equals(body.getUUID()) || snap.bodyEpoch() != com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch()) return true;
            double lx = BodyActionLayout.toLogical(x, left), ly = BodyActionLayout.toLogical(y, top);
            var target = projection == null ? null : projection.hit(lx, ly);
            if (target != null) BodyActionClient.activate(target.entry()); return true;
        }
        @Override public boolean mouseDragged(double x,double y){return false;}
        @Override public boolean mouseReleased(int button){return false;}
        @Override public boolean mouseScrolled(double x,double y,double delta){return false;}
        @Override public boolean keyPressed(int key,int scan,int modifiers){return false;}
        @Override public boolean charTyped(char value,int modifiers){return false;}
        @Override public boolean isSearchFocused(){return false;}
        @Override public void clearTextFocus(){}
        @Override public void resetOwnedContext(){visible=false; projection=null; paintedSlots=null;}
    }
}
