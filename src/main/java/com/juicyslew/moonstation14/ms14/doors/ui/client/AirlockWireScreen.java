package com.juicyslew.moonstation14.ms14.doors.ui.client;

import com.juicyslew.moonstation14.ms14.doors.ui.*;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent;
import com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

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
    private final Consumer<AirlockWireRequest> requestSender;
    private final Runnable closeOwner;
    private final Runnable serverCloseRequest;
    private final Supplier<MovementKeys> movementKeys;
    private final BiConsumer<Integer,Integer> embeddedInitializer;
    private final BooleanSupplier fontBound;
    private AirlockWireSnapshot state;
    private long nextRequest;
    private long pending = -1;
    private AirlockWireRequest.Operation pendingOperation;
    private int waitTicks;
    private int screenTicks;
    private int x, y;
    private WindowGeometryController geometry;
    private boolean started;
    private boolean viewInitialized;
    boolean captureTransition;
    private boolean closingFromServer;
    private String message;
    record MovementKeys(boolean authorized,boolean focused,boolean up,boolean down,boolean left,boolean right,
                        boolean jump,boolean shift,boolean sprint) { }

    public AirlockWireScreen(int windowId, UUID session) {
        this(windowId,session,AirlockWireNetworking::send,
                ()->com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.wireClosed(),
                ()->{if(Minecraft.getInstance().getConnection()!=null)AirlockWireNetworking.requestClose(windowId,session);},
                AirlockWireScreen::nativeMovementKeys,null,null);
    }

    AirlockWireScreen(int windowId,UUID session,Consumer<AirlockWireRequest> requestSender,
                      Runnable closeOwner,Runnable serverCloseRequest,Supplier<MovementKeys> movementKeys,
                      BiConsumer<Integer,Integer> embeddedInitializer,BooleanSupplier fontBound) {
        super(Component.literal("Airlock — Wires"));
        this.windowId = windowId;
        this.session = session;
        this.requestSender=java.util.Objects.requireNonNull(requestSender);
        this.closeOwner=java.util.Objects.requireNonNull(closeOwner);
        this.serverCloseRequest=java.util.Objects.requireNonNull(serverCloseRequest);
        this.movementKeys=java.util.Objects.requireNonNull(movementKeys);
        this.embeddedInitializer=embeddedInitializer==null?(w,h)->init(Minecraft.getInstance(),w,h):embeddedInitializer;
        this.fontBound=fontBound==null?()->font!=null:fontBound;
    }

    public int windowId() { return windowId; }
    public UUID session() { return session; }
    int windowX(){return x;} int windowY(){return y;}

    public boolean contains(double mx, double my) { return within(mx, my); }
    public boolean workspaceMouseClicked(double mx, double my, int button) {
        mouseClicked(mx, my, button);
        return true;
    }
    public boolean workspaceMouseDragged(double mx, double my, int button, double dx, double dy) {
        if (geometry == null || !geometry.isPointerCaptured()) return false;
        mouseDragged(mx, my, button, dx, dy);
        return true;
    }
    public boolean workspaceMouseReleased(double mx, double my, int button) {
        if (geometry == null || !geometry.isPointerCaptured()) return false;
        mouseReleased(mx, my, button);
        return true;
    }
    public void workspaceSize(int newWidth, int newHeight) {
        if (!WireEmbeddedScreenInitPolicy.needsInit(viewInitialized, fontBound.getAsBoolean(), width, height, newWidth, newHeight)) return;
        boolean first = !viewInitialized;
        int oldX = x, oldY = y;
        embeddedInitializer.accept(newWidth,newHeight);
        this.width=newWidth;this.height=newHeight;
        viewInitialized = true;
        if (first) configureInputViewport(width,height);
        else { x=oldX;y=oldY;resizeInputViewport(width,height); }
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
        requestSender.accept(new AirlockWireRequest(windowId, session, pending,
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
        AirlockWirePaint.paint(new GuiGraphicsPaint(g,font),new AirlockWirePaint.View(x,y,state,screenTicks,message,mouseX,mouseY,title));
        if (AirlockWireScreenLayout.indicator(x, y, 3).contains(mouseX, mouseY)
                || AirlockWireScreenLayout.indicator(x, y, 4).contains(mouseX, mouseY))
            g.renderTooltip(font, Component.literal("Wire continuity; AI/log service not implemented"), mouseX, mouseY);
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
            if (WindowChromeButtons.contains(WindowChromeButtons.closeBounds(x,y,W,1f),mx,my)) { onClose(); return true; }
            if (geometry != null && geometry.beginPointer(mx,my,button,27,23)) return true;
            int rowIndex = AirlockWireScreenLayout.hitWire(x, y, mx, my, state == null ? 0 : state.rows().size());
            if (rowIndex >= 0 && state != null && rowIndex < state.rows().size()) {
                var row = state.rows().get(rowIndex);
                send(row.id(), AirlockWireRequest.Operation.CLICK);
            }
            return true;
        }
        // An outside press is not a wire action and must not be replayed into world-use.
        return false;
    }

    private boolean within(double mx, double my) { return mx >= x && mx < x + W && my >= y && my < y + H; }
    void configureInputViewport(int screenWidth,int screenHeight) {
        this.width=screenWidth;this.height=screenHeight;
        x=Math.max(0,(screenWidth-W)/2);y=Math.max(0,(screenHeight-H)/2);
        geometry=new WindowGeometryController(Math.max(1,screenWidth),Math.max(1,screenHeight),x,y,W,H,W,H,false);
        viewInitialized=true;
    }
    public boolean workspaceOwnsPointerCapture() { return geometry != null && geometry.isPointerCaptured(); }
    void resizeInputViewport(int screenWidth,int screenHeight) {
        if(geometry==null){configureInputViewport(screenWidth,screenHeight);return;}
        this.width=screenWidth;this.height=screenHeight;geometry.setScreenSize(Math.max(1,screenWidth),Math.max(1,screenHeight));
        geometry.setBounds(x,y,W,H);syncGeometry();
    }
    private void syncGeometry() { if (geometry != null) { x=geometry.x(); y=geometry.y(); } }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && geometry != null && geometry.dragPointer(mx,my)) { syncGeometry(); return true; }
        return within(mx, my);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (geometry != null && geometry.releasePointer(button)) return true;
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
        if (geometry != null) geometry.cancelPointer();
        movement.sample(false, false, false, false, false, false, false, false);
        if (!closingFromServer) {
            closingFromServer = true;
            serverCloseRequest.run();
            closeOwner.run();
        }
    }
    @Override public PopupMovementIntent.State movementIntent() {
        var keys=movementKeys.get();
        boolean eligible=keys.authorized()&&keys.focused();
        return movement.sample(eligible,keys.up(),keys.down(),keys.left(),keys.right(),keys.jump(),keys.shift(),keys.sprint());
    }
    private static MovementKeys nativeMovementKeys() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (!BodyHandClient.hasCommittedBody() || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE)
            return new MovementKeys(false,false,false,false,false,false,false,false,false);
        var o = Minecraft.getInstance().options;
        return new MovementKeys(true,true,held(o.keyUp, window), held(o.keyDown, window), held(o.keyLeft, window),
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

