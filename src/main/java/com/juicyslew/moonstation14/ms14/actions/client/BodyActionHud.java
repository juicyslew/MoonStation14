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
        private final java.util.Map<net.minecraft.resources.ResourceLocation, Long> previousCooldown = new java.util.HashMap<>();
        private final java.util.Map<net.minecraft.resources.ResourceLocation, Long> blinkUntil = new java.util.HashMap<>();
        private int width, height, left, top;
        private boolean visible;
        private java.util.UUID trackedBody;
        private long trackedEpoch;

        void update() {
            var mc = Minecraft.getInstance();
            var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForHud();
            long epoch = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
            if (body == null || trackedBody == null || !trackedBody.equals(body.getUUID()) || trackedEpoch != epoch) {
                previousCooldown.clear(); blinkUntil.clear();
                trackedBody = body == null ? null : body.getUUID(); trackedEpoch = body == null ? 0 : epoch;
            }
            var snap = BodyActionClient.snapshot();
            visible = !mc.options.hideGui && mc.level != null && body != null && body.level() == mc.level
                    && snap != null && snap.bodyId().equals(body.getUUID())
                    && snap.bodyEpoch() == com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch()
                    && com.juicyslew.moonstation14.ms14.ui.client.GameplayHudPolicy.visible(mc.screen);
            width = mc.getWindow().getGuiScaledWidth(); height = mc.getWindow().getGuiScaledHeight();
            left = 6; top = BodyActionLayout.actionBarTop();
            if (visible) PointerOverlayRegistry.register(this); else PointerOverlayRegistry.unregister(this);
        }
        @Override public boolean visible() { return visible; }
        @Override public boolean pointerAvailable() {
            var mc = Minecraft.getInstance();
            return pointerHostAllowed(mc.screen, visible, mc.mouseHandler.isMouseGrabbed());
        }
        @Override public boolean contains(double x, double y) {
            if (!visible) return false;
            var snap = BodyActionClient.snapshot();
            int count = snap == null ? 0 : (int) BodyActionSlots.resolve(snap).stream().filter(s -> s.entry() != null).count();
            double lx = BodyActionLayout.toLogical(x, left), ly = BodyActionLayout.toLogical(y, top);
            return lx >= 0 && lx < 28 && ly >= 0 && ly < count * 30;
        }
        @Override public void renderIntoGui(GuiGraphics g, int mx, int my, float partial, int w, int h) {
            update(); if (!visible) return;
            var snap = BodyActionClient.snapshot(); if (snap == null) return;
            g.pose().pushPose();
            g.pose().translate(left, top, 0);
            g.pose().scale(BodyActionLayout.ACTION_UI_SCALE, BodyActionLayout.ACTION_UI_SCALE, 1);
            int row = 0;
            for (var slot : BodyActionSlots.resolve(snap)) {
                var entry = slot.entry(); if (entry == null) continue;
                int x = 0, y = row++ * 30;
                g.fill(x, y, x + 28, y + 28, 0xaa0d1118); g.fill(x, y, x + 28, y + 1, 0xff66717c);
                int logicalMouseX = (int)BodyActionLayout.toLogical(mx, left), logicalMouseY = (int)BodyActionLayout.toLogical(my, top);
                boolean hovered = logicalMouseX >= x && logicalMouseX < x + 28 && logicalMouseY >= y && logicalMouseY < y + 28;
                if (hovered) g.fill(x, y, x + 28, y + 28, 0x665d7184);
                    if (entry.active()) g.fill(x, y, x + 28, y + 28, 0x554fc777);
                    int inset = BodyActionLayout.ACTION_BAR_ICON_INSET, iconSize = BodyActionLayout.ACTION_BAR_ICON;
                    g.blit(entry.iconOn().filter(ignored -> entry.active()).orElse(entry.icon()), x + inset, y + inset, iconSize, iconSize, 0, 0, 16, 16, 16, 16);
                    if (!entry.enabled() || BodyActionClient.pending()) g.fill(x + inset, y + inset, x + inset + iconSize, y + inset + iconSize, 0x99000000);
                    long cd = BodyActionClient.remainingCooldown(entry);
                    long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
                    long previous = previousCooldown.getOrDefault(entry.actionId(), 0L);
                    if (previous > 0 && cd == 0) blinkUntil.put(entry.actionId(), now + 2);
                    previousCooldown.put(entry.actionId(), cd);
                    if (cd > 0) {
                        drawCooldownWipe(g, x + inset, y + inset, BodyActionLayout.cooldownWipeProgress(cd, entry.useDelayTicks()));
                        int seconds = (int)Math.ceil(cd / 20.0);
                        String text = Integer.toString(seconds);
                        g.drawString(Minecraft.getInstance().font, text, x + 14 - Minecraft.getInstance().font.width(text)/2, y + 10, 0xffffffff, true);
                    } else {
                        float blink = BodyActionLayout.cooldownBlinkProgress(blinkUntil.getOrDefault(entry.actionId(), 0L) - now);
                        if (blink > 0) g.fill(x + inset, y + inset, x + inset + iconSize, y + inset + iconSize, ((int)(blink * 0x88) << 24) | 0x00ffffff);
                    }
                    if (hovered && pointerAvailable()) {
                        var tip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
                        tip.add(net.minecraft.network.chat.Component.translatable(entry.displayName()));
                        if (!entry.disabledReason().isBlank()) tip.add(net.minecraft.network.chat.Component.translatable(
                                "gui.moonstation14.actions.disabled." + entry.disabledReason().toLowerCase(java.util.Locale.ROOT)));
                        long remaining = BodyActionClient.remainingCooldown(entry);
                        if (remaining > 0) tip.add(net.minecraft.network.chat.Component.literal("Cooldown: "
                                + BodyActionLayout.cooldownSeconds(remaining)));
                        if (BodyActionClient.pending()) tip.add(net.minecraft.network.chat.Component.literal("Action pending"));
                        g.renderTooltip(Minecraft.getInstance().font, tip.stream().map(net.minecraft.network.chat.Component::getVisualOrderText).toList(), mx, my);
                    }
                g.drawString(Minecraft.getInstance().font, Integer.toString(slot.number()), x + 1, y + 20, 0xffeeeeee, false);
            }
            g.pose().popPose();
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
            int index = BodyActionLayout.actionBarSlot((int)BodyActionLayout.toLogical(x, left), (int)BodyActionLayout.toLogical(y, top), 0, 0);
            var actual = BodyActionSlots.resolve(snap).stream().filter(s -> s.entry() != null).toList();
            if (index < 0 || index >= actual.size()) return true;
            BodyActionClient.activate(actual.get(index).entry()); return true;
        }
        @Override public boolean mouseDragged(double x,double y){return false;}
        @Override public boolean mouseReleased(int button){return false;}
        @Override public boolean mouseScrolled(double x,double y,double delta){return false;}
        @Override public boolean keyPressed(int key,int scan,int modifiers){return false;}
        @Override public boolean charTyped(char value,int modifiers){return false;}
        @Override public boolean isSearchFocused(){return false;}
        @Override public void clearTextFocus(){}
        @Override public void resetOwnedContext(){visible=false;}
    }
}
