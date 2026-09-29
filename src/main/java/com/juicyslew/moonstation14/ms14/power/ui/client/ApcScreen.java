package com.juicyslew.moonstation14.ms14.power.ui.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenu;
import com.juicyslew.moonstation14.ms14.power.ui.ApcToggleRequest;
import com.juicyslew.moonstation14.ms14.power.ui.ApcToggleResponse;
import com.juicyslew.moonstation14.ms14.power.ui.ApcVisualState;
import com.juicyslew.moonstation14.ms14.ui.client.MachineSwitch;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.animation.ApcSpriteAnimations;
import com.juicyslew.moonstation14.ms14.ui.client.window.TitlePressRouting;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import com.juicyslew.moonstation14.ms14.ui.model.animation.LayeredSpriteAnimation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** The breaker may show pending intent; only server menu snapshots confirm device state. */
public final class ApcScreen extends AbstractContainerScreen<ApcMenu> {
    private static final ResourceLocation APC_IMAGE = ResourceLocation.fromNamespaceAndPath(
            MoonStation14.MOD_ID, "textures/gui/apc/apc.png");
    private static final int WIDTH = 350;
    private static final int HEIGHT = 190;
    private static final int RESPONSE_TIMEOUT_TICKS = 40;
    private static final LayeredSpriteAnimation FULL_PREVIEW =
            ApcSpriteAnimations.withDisplay(ApcSpriteAnimations.FULL, true);
    private static final LayeredSpriteAnimation CHARGING_PREVIEW =
            ApcSpriteAnimations.withDisplay(ApcSpriteAnimations.CHARGING, true);
    private static final LayeredSpriteAnimation LACK_PREVIEW =
            ApcSpriteAnimations.withDisplay(ApcSpriteAnimations.LACK, true);
    private static final Component TITLE = Component.translatable("gui.moonstation14.apc.title");
    private static final Component BREAKER = Component.translatable("gui.moonstation14.apc.breaker");
    private static final Component ON = Component.translatable("gui.moonstation14.apc.breaker_on");
    private static final Component OFF = Component.translatable("gui.moonstation14.apc.breaker_off");
    private static final Component TRIPPED = Component.translatable("gui.moonstation14.apc.overload_tripped");
    private static final Component WAITING = Component.translatable("gui.moonstation14.apc.waiting");
    private static final Component PENDING = Component.translatable("gui.moonstation14.apc.pending");
    private static final Component TIMED_OUT = Component.translatableWithFallback(
            "gui.moonstation14.apc.response_timeout", "Response timed out; retry");
    private static final Component READY = Component.translatable("gui.moonstation14.apc.ready");
    private static final Component BATTERY = Component.translatable("gui.moonstation14.apc.battery");
    private static final Component EXTERNAL = Component.translatable("gui.moonstation14.apc.external");
    private static final Component LOAD = Component.translatable("gui.moonstation14.apc.load");
    private static final Component UNAVAILABLE = Component.translatable("gui.moonstation14.apc.unavailable");

    private MachineSwitch breakerSwitch;
    private Button closeButton;
    private WindowGeometryController geometry;
    private boolean windowDragged;
    private long inFlightRequest = -1;
    private long nextRequestId;
    private int pendingTicks;
    private long authoritativeRevision;
    private boolean authoritativeBreakerClosed;
    private boolean authoritativeTripLatched;
    private boolean presentationBreakerClosed;
    private Boolean queuedDesired;
    private float animatedBatteryPermille;
    private boolean snapshotObserved;
    private boolean awaitingFreshSnapshot;
    private boolean responseTimedOut;
    private ApcVisualState previewState = ApcVisualState.UNKNOWN;
    private long previewStartNanos;

    public ApcScreen(ApcMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelY = HEIGHT + 20;
    }

    @Override
    protected void init() {
        super.init();
        closeButton = null;
        breakerSwitch = null;
        if (geometry == null) {
            geometry = new WindowGeometryController(Math.max(1, width), Math.max(1, height),
                    Math.max(0, leftPos), Math.max(0, topPos), WIDTH, HEIGHT, WIDTH, HEIGHT, false);
        } else {
            geometry.setScreenSize(Math.max(1, width), Math.max(1, height));
            // Vanilla re-centers during init; do the same until the user actually moves the window.
            geometry.setBounds(windowDragged ? geometry.x() : Math.max(0, (width - WIDTH) / 2),
                    windowDragged ? geometry.y() : Math.max(0, (height - HEIGHT) / 2), WIDTH, HEIGHT);
        }
        syncWindowPosition();
        // Resize calls init again on this screen: do not reset its request, queue, or animation.
        observeMenuSnapshot();
        closeButton = addRenderableWidget(WindowChromeButtons.close(leftPos, topPos, WIDTH, this::onClose));
        breakerSwitch = addRenderableWidget(new MachineSwitch(leftPos + 90, topPos + 66, 120, 20,
                BREAKER, ON, OFF, presentationBreakerClosed, ignored -> sendToggle()));
        syncWindowPosition();
        updateBreakerSwitch();
    }

    private static int fittedWidgetCoordinate(int desired, int viewport, int widgetSize) {
        return Math.clamp(desired, 0, Math.max(0, viewport - widgetSize));
    }

    private void syncWindowPosition() {
        // The fixed artwork cannot fit a smaller viewport. Center it and let vanilla clip it;
        // the controller itself remains within the viewport and cannot produce invalid bounds.
        leftPos = geometry.x() + (width < WIDTH ? (width - WIDTH) / 2 : 0);
        topPos = geometry.y() + (height < HEIGHT ? (height - HEIGHT) / 2 : 0);
        if (closeButton != null) {
            closeButton.setX(fittedWidgetCoordinate(leftPos + WIDTH - WindowChromeButtons.EDGE
                    - WindowChromeButtons.SIZE, width, WindowChromeButtons.SIZE));
            closeButton.setY(fittedWidgetCoordinate(topPos + WindowChromeButtons.EDGE,
                    height, WindowChromeButtons.SIZE));
        }
        if (breakerSwitch != null) {
            breakerSwitch.setX(fittedWidgetCoordinate(leftPos + 90, width, breakerSwitch.getWidth()));
            breakerSwitch.setY(fittedWidgetCoordinate(topPos + 66, height, breakerSwitch.getHeight()));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean widgetHit = (closeButton != null && closeButton.visible && closeButton.active
                && closeButton.isMouseOver(mouseX, mouseY))
                || (breakerSwitch != null && breakerSwitch.visible && breakerSwitch.active
                && breakerSwitch.isMouseOver(mouseX, mouseY));
        if (TitlePressRouting.isTitlePress(mouseX, mouseY, button, leftPos, topPos, WIDTH,
                WindowGeometryController.TITLE_HEIGHT, width, height, widgetHit)
                && geometry.beginPointer(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int previousX = geometry.x();
        int previousY = geometry.y();
        if (button == 0 && geometry.dragPointer(mouseX, mouseY)) {
            if (geometry.x() != previousX || geometry.y() != previousY) windowDragged = true;
            syncWindowPosition();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (geometry.releasePointer(button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        observeMenuSnapshot();
        if (snapshotObserved) {
            animatedBatteryPermille += (authoritativeBatteryPermille() - animatedBatteryPermille) * 0.25f;
        }
        if (inFlightRequest >= 0 && ++pendingTicks >= RESPONSE_TIMEOUT_TICKS) {
            // A lost result cannot confirm the intent. Roll back to the newest known state,
            // but allow a new request at its revision; do not replay the queued intent.
            inFlightRequest = -1;
            pendingTicks = 0;
            queuedDesired = null;
            presentationBreakerClosed = authoritativeBreakerClosed;
            responseTimedOut = true;
        }
        updateBreakerSwitch();
    }

    private void sendDesired(boolean desiredClosed) {
        if (!snapshotObserved || awaitingFreshSnapshot) return;
        responseTimedOut = false;
        presentationBreakerClosed = desiredClosed;
        if (inFlightRequest >= 0) {
            queuedDesired = desiredClosed;
            return;
        }
        inFlightRequest = nextRequestId++;
        pendingTicks = 0;
        PacketDistributor.sendToServer(new ApcToggleRequest(menu.containerId, menu.session(),
                authoritativeRevision, inFlightRequest, desiredClosed));
        updateBreakerSwitch();
    }

    private void sendToggle() {
        if (!snapshotObserved || awaitingFreshSnapshot) return;
        boolean desired = !presentationBreakerClosed;
        if (inFlightRequest >= 0) {
            presentationBreakerClosed = desired;
            queuedDesired = desired;
        } else sendDesired(desired);
        updateBreakerSwitch();
    }

    private void observeMenuSnapshot() {
        if (!menu.hasReceivedInitialSnapshot()) return;
        long revision = menu.displayedRevision();
        boolean initialSnapshot = !snapshotObserved;
        if (initialSnapshot) {
            snapshotObserved = true;
            animatedBatteryPermille = authoritativeBatteryPermille();
        }
        // The first commit may have revision zero. Later commits must be strictly newer:
        // a correlated response can precede the menu commit at the same revision.
        if (initialSnapshot || revision > authoritativeRevision) {
            if (!initialSnapshot) awaitingFreshSnapshot = false;
            responseTimedOut = false;
            authoritativeRevision = revision;
            authoritativeBreakerClosed = menu.displayedBreaker() != 0;
            authoritativeTripLatched = menu.displayedTripLatched();
            if (inFlightRequest < 0) presentationBreakerClosed = authoritativeBreakerClosed;
        }
    }

    /** Called only for the currently displayed screen; correlation guards discard late/replayed results. */
    public void handleResponse(ApcToggleResponse response) {
        if (response.containerId() != menu.containerId || !response.session().equals(menu.session())
                || response.requestId() != inFlightRequest) return;
        if (response.hasSnapshot()) {
            // A newer menu commit may already represent another viewer's change. Retire this
            // result without letting its older, authorized snapshot roll that state back.
            if (response.revision() >= authoritativeRevision) {
                authoritativeRevision = response.revision();
                authoritativeBreakerClosed = response.breakerClosed();
                authoritativeTripLatched = response.tripLatched();
            }
            awaitingFreshSnapshot = false;
        } else {
            awaitingFreshSnapshot = true;
        }
        responseTimedOut = false;
        inFlightRequest = -1;
        pendingTicks = 0;
        Boolean next = queuedDesired;
        queuedDesired = null;
        presentationBreakerClosed = authoritativeBreakerClosed;
        if (response.hasSnapshot() && next != null && next != authoritativeBreakerClosed) sendDesired(next);
        updateBreakerSwitch();
    }

    private void updateBreakerSwitch() {
        if (breakerSwitch != null) breakerSwitch.setPresentedState(presentationBreakerClosed,
                snapshotObserved && !awaitingFreshSnapshot);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        MachineWindowRenderer.window(graphics, font, x, y, WIDTH, HEIGHT, TITLE);
        renderApcPreview(graphics, x + 14, y + 37);

        MachineWindowRenderer.insetPanel(graphics, x + 84, y + 37, 252, 64);
        graphics.drawString(font, BREAKER, x + 90, y + 44, MachineWindowRenderer.MUTED_TEXT, false);
        // Keep long status translations out of the external column. The footer below
        // still shows the complete pending/timeout message without truncation.
        graphics.enableScissor(x + 90, y + 55, x + 218, y + 55 + font.lineHeight);
        try {
            graphics.drawString(font, !snapshotObserved || awaitingFreshSnapshot ? WAITING
                            : inFlightRequest >= 0 ? PENDING : responseTimedOut ? TIMED_OUT
                            : authoritativeBreakerClosed ? ON : OFF,
                    x + 90, y + 55, MachineWindowRenderer.TEXT, false);
        } finally {
            graphics.disableScissor();
        }
        if (snapshotObserved && !awaitingFreshSnapshot && authoritativeTripLatched) {
            graphics.drawString(font, TRIPPED, x + 90, y + 89, 0xffff705b, false);
        }
        // Stack each right-column value below its label; right-aligned statusRow values
        // collide with their labels in this narrow pane.
        graphics.drawString(font, EXTERNAL, x + 222, y + 44, MachineWindowRenderer.MUTED_TEXT, false);
        graphics.drawString(font, UNAVAILABLE, x + 222, y + 56, MachineWindowRenderer.TEXT, false);
        graphics.drawString(font, LOAD, x + 222, y + 70, MachineWindowRenderer.MUTED_TEXT, false);
        graphics.drawString(font, UNAVAILABLE, x + 222, y + 82, MachineWindowRenderer.TEXT, false);

        graphics.drawString(font, BATTERY, x + 14, y + 113, MachineWindowRenderer.TEXT, false);
        if (snapshotObserved) {
            MachineWindowRenderer.chargeMeter(graphics, font, x + 14, y + 130, 322, 20,
                    authoritativeBatteryPermille(), animatedBatteryPermille);
        } else {
            MachineWindowRenderer.insetPanel(graphics, x + 14, y + 130, 322, 20);
            graphics.drawCenteredString(font, WAITING, x + WIDTH / 2, y + 135,
                    MachineWindowRenderer.MUTED_TEXT);
        }
        MachineWindowRenderer.divider(graphics, x + 10, y + 158, WIDTH - 20);
        MachineWindowRenderer.footer(graphics, font, x + 14, y + 166, WIDTH - 28, 17,
                !snapshotObserved || awaitingFreshSnapshot ? WAITING : inFlightRequest >= 0 ? PENDING
                        : responseTimedOut ? TIMED_OUT : authoritativeTripLatched ? TRIPPED : READY);
    }

    private void renderApcPreview(GuiGraphics graphics, int x, int y) {
        // Do not infer a display from the battery or predicted breaker. A response can arrive
        // before its matching menu commit, so stale menu data must not drive the preview.
        ApcVisualState selected = !snapshotObserved || awaitingFreshSnapshot || inFlightRequest >= 0
                || responseTimedOut || authoritativeTripLatched
                || menu.displayedRevision() != authoritativeRevision
                ? ApcVisualState.UNKNOWN : menu.displayedVisualState();
        long now = System.nanoTime();
        if (selected != previewState) {
            previewState = selected;
            previewStartNanos = now;
        }
        if (selected == ApcVisualState.UNKNOWN) {
            MachineWindowRenderer.imagePane(graphics, x, y, 64, 64, APC_IMAGE, 32, 32, 60);
            return;
        }
        MachineWindowRenderer.imagePane(graphics, x, y, 64, 64, null, 32, 32, 60);
        LayeredSpriteAnimation animation = switch (selected) {
            case FULL -> FULL_PREVIEW;
            case CHARGING -> CHARGING_PREVIEW;
            case LACK -> LACK_PREVIEW;
            case UNKNOWN -> throw new IllegalStateException("Unknown preview cannot be animated");
        };
        // Fixed south-facing GUI portrait; block orientation is not part of the menu snapshot.
        long elapsedNanos = now - previewStartNanos;
        ApcSpriteAnimations.GUI.draw(graphics, animation, ApcSpriteAnimations.SOUTH,
                elapsedNanos < 0 ? 0 : elapsedNanos / 1_000_000L, x + 2, y + 2, 60, 60);
    }

    private int authoritativeBatteryPermille() {
        return Math.clamp(menu.displayedBatteryPermille(), 0, 1000);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The frame draws its own title; there is no player inventory in this menu.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
