package com.juicyslew.moonstation14.ms14.doors.ui.client;

import com.juicyslew.moonstation14.ms14.doors.ui.*;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent;
import com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/** Small, non-modal airlock maintenance circuit. All row identity and status comes from the server. */
public final class AirlockWireScreen extends Screen implements GameplayWindow {
    private static final int W = AirlockWireScreenLayout.WIDTH, H = AirlockWireScreenLayout.HEIGHT;
    private static final ResourceLocation WIRE = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/gui/airlock/wire_1.png");
    private static final ResourceLocation WIRE_CUT = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/gui/airlock/wire_1_cut.png");
    private static final ResourceLocation WIRE_ALT = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/gui/airlock/wire_2.png");
    private static final ResourceLocation WIRE_ALT_CUT = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/gui/airlock/wire_2_cut.png");
    private static final ResourceLocation LIGHT_OFF = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/gui/airlock/light_off_base.png");
    private static final ResourceLocation LIGHT_ON = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/gui/airlock/light_on_base.png");
    private final int windowId;
    private final UUID session;
    private final PopupMovementIntent movement = new PopupMovementIntent();
    private AirlockWireSnapshot state;
    private long nextRequest;
    private long pending = -1;
    private AirlockWireRequest.Operation pendingOperation;
    private int waitTicks;
    private int screenTicks;
    private int x, y, dragX, dragY;
    private boolean dragging;
    private boolean started;
    private boolean viewInitialized;
    boolean captureTransition;
    private boolean closingFromServer;
    private String message;

    public AirlockWireScreen(int windowId, UUID session) {
        super(Component.literal("Airlock — Wires"));
        this.windowId = windowId;
        this.session = session;
    }

    public int windowId() { return windowId; }
    public UUID session() { return session; }

    public boolean contains(double mx, double my) { return within(mx, my); }
    public boolean workspaceMouseClicked(double mx, double my, int button) {
        mouseClicked(mx, my, button);
        return true;
    }
    public boolean workspaceMouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return false;
        mouseDragged(mx, my, button, dx, dy);
        return true;
    }
    public boolean workspaceMouseReleased(double mx, double my, int button) {
        if (!dragging) return false;
        mouseReleased(mx, my, button);
        return true;
    }
    public void workspaceSize(int newWidth, int newHeight) {
        if (!WireEmbeddedScreenInitPolicy.needsInit(viewInitialized, font != null, width, height, newWidth, newHeight)) return;
        boolean first = !viewInitialized;
        int oldX = x, oldY = y;
        init(Minecraft.getInstance(), newWidth, newHeight);
        viewInitialized = true;
        if (!first) {
            x = Math.max(0, Math.min(oldX, width - W));
            y = Math.max(0, Math.min(oldY, height - H));
        }
    }

    @Override protected void init() {
        if (!viewInitialized) { x = (width - W) / 2; y = (height - H) / 2; }
        startView();
    }

    public void startView() {
        if (started) return;
        started = true;
        query();
    }

    private void send(int row, AirlockWireRequest.Operation operation) {
        if (pending >= 0) return;
        pending = nextRequest++; waitTicks = 0;
        pendingOperation = operation;
        AirlockWireNetworking.send(new AirlockWireRequest(windowId, session, pending,
                state == null ? 0 : state.rootRevision(), row, operation));
    }

    private void query() { send(-1, AirlockWireRequest.Operation.QUERY); }

    public void handleResponse(AirlockWireSnapshot response) {
        if (response.containerId() != windowId || !session.equals(response.session()) || response.requestId() != pending) return;
        pending = -1; waitTicks = 0; state = response;
        message=AirlockWireFeedback.afterResponse(message,response,pendingOperation);
        pendingOperation=null;
    }

    @Override public void tick() {
        super.tick();
        screenTicks++;
        if (pending >= 0 && ++waitTicks > 50) { pending = -1; message = "No response"; }
        if (pending < 0 && screenTicks % 20 == 0) query();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var minecraft = Minecraft.getInstance();
        workspaceSize(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        nearestPixelArt();
        MachineWindowRenderer.window(g, font, x, y, W, H, title);
        MachineWindowRenderer.insetPanel(g, x + 6, y + 31, W - 12, 110);
        // PCB traces sit behind the authentic upstream wire/contact art.
        g.fill(x + 14, y + 80, x + 196, y + 82, 0xff52664b);
        g.fill(x + 14, y + 135, x + 196, y + 137, 0xff52664b);
        if (state != null) for (int i = 0; i < state.rows().size(); i++) {
            var row = state.rows().get(i);
            var bounds = AirlockWireScreenLayout.wire(x, y, i);
            boolean alternate = (i & 1) != 0;
            ResourceLocation wireTexture = row.cut() ? (alternate ? WIRE_ALT_CUT : WIRE_CUT) : (alternate ? WIRE_ALT : WIRE);
            RenderSystem.setShaderColor(((row.argb() >> 16) & 255) / 255f, ((row.argb() >> 8) & 255) / 255f,
                    (row.argb() & 255) / 255f, 1f);
            g.blit(wireTexture, bounds.x(), bounds.y(), 0, 0, bounds.width(), bounds.height(), 16, 50);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            g.drawString(font, row.letter(), bounds.x() + 5, bounds.y() + 18, 0xffffffff, true);
        }
        var labels = new String[]{"PWR", "BOLT", "TIMR", "AIA", "LOG", "BLIT", "SAFE"};
        var lights = state == null ? java.util.Collections.nCopies(7, AirlockWireIndicators.Light.OFF)
                : java.util.Arrays.asList(state.powerIndicator(), state.boltIndicator(), state.timingIndicator(),
                state.aiInteractIndicator(), state.logIndicator(), state.boltLightsIndicator(), state.safetyIndicator());
        for (int i = 0; i < labels.length; i++) {
            var bounds = AirlockWireScreenLayout.indicator(x, y, i);
            boolean implemented = i != 3 && i != 4;
            var light = lights.get(i);
            boolean on = AirlockWireLightStyle.isOn(light, screenTicks);
            int diameter = AirlockWireLightStyle.diameter(on);
            int left = bounds.x() + (10 - diameter) / 2, top = bounds.y() + (10 - diameter) / 2;
            g.fill(left, top, left + diameter, top + diameter,
                    on ? AirlockWireLightStyle.ON : AirlockWireLightStyle.OFF);
            g.drawString(font, labels[i], bounds.x() + 12, bounds.y() + 2, MachineWindowRenderer.TEXT, false);
        }
        if (AirlockWireScreenLayout.indicator(x, y, 3).contains(mouseX, mouseY)
                || AirlockWireScreenLayout.indicator(x, y, 4).contains(mouseX, mouseY))
            g.renderTooltip(font, Component.literal("Wire continuity; AI/log service not implemented"), mouseX, mouseY);
        var footer = AirlockWireScreenLayout.footer(x, y);
        String footerText = message != null ? message
                : state != null && !state.supplyPowered() ? "NO POWER" : "";
        int footerColor = message != null ? 0xffffcc55
                : state != null && !state.supplyPowered() ? 0xffff7770 : 0xffaab6ba;
        g.drawString(font, font.plainSubstrByWidth(footerText, footer.width()), footer.x(), footer.y(), footerColor, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static void nearestPixelArt() {
        var manager = Minecraft.getInstance().getTextureManager();
        for (var texture : new ResourceLocation[]{WIRE, WIRE_CUT, WIRE_ALT, WIRE_ALT_CUT, LIGHT_OFF, LIGHT_ON})
            manager.getTexture(texture).setFilter(false, false);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && within(mx, my)) {
            if (mx >= x + W - 17 && my < y + 27) { onClose(); return true; }
            if (my < y + 27) { dragging = true; dragX = (int) mx - x; dragY = (int) my - y; return true; }
            int rowIndex = AirlockWireScreenLayout.hitWire(x, y, mx, my, state == null ? 0 : state.rows().size());
            if (rowIndex >= 0 && state != null && rowIndex < state.rows().size()) {
                var row = state.rows().get(rowIndex);
                send(row.id(), AirlockWireRequest.Operation.CLICK);
            }
            return true;
        }
        if (button == 0) BodyHandClient.gameplayWindowMouseUse(button);
        return false;
    }

    private boolean within(double mx, double my) { return mx >= x && mx < x + W && my >= y && my < y + H; }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) { x = (int) mx - dragX; y = (int) my - dragY; return true; }
        return within(mx, my);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return within(mx, my);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (BodyHandClient.popupAction(key, scan, modifiers, true)) return true;
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
    public void closeFromServer() {
        closingFromServer = true;
        if (Minecraft.getInstance().screen == this) Minecraft.getInstance().setScreen(null);
    }
    @Override public void onClose() {
        closeSession();
    }
    @Override public void removed() {
        closeSession();
        super.removed();
    }
    private void closeSession() {
        if (captureTransition) return;
        AirlockWireClient.removed(this);
        dragging = false;
        movement.sample(false, false, false, false, false, false, false, false);
        if (!closingFromServer) {
            closingFromServer = true;
            if (Minecraft.getInstance().getConnection() != null) AirlockWireNetworking.requestClose(windowId, session);
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.wireClosed();
        }
    }
    @Override public PopupMovementIntent.State movementIntent() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (!BodyHandClient.hasCommittedBody() || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE)
            return movement.sample(false, false, false, false, false, false, false, false);
        var o = Minecraft.getInstance().options;
        return movement.sample(true, held(o.keyUp, window), held(o.keyDown, window), held(o.keyLeft, window),
                held(o.keyRight, window), held(o.keyJump, window), held(o.keyShift, window), held(o.keySprint, window));
    }
    private static boolean held(net.minecraft.client.KeyMapping mapping, long window) {
        var key = mapping.getKey();
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) return false;
        int code = key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.SCANCODE
                ? scanKey(key.getValue()) : key.getValue();
        return code >= 0 && code <= GLFW.GLFW_KEY_LAST && GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
    }
    private static int scanKey(int scan) {
        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++)
            if (GLFW.glfwGetKeyScancode(key) == scan) return key;
        return -1;
    }
    @Override public boolean capturesKeyboard() { return false; }
}

