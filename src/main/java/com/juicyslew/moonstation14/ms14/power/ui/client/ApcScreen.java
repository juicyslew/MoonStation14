package com.juicyslew.moonstation14.ms14.power.ui.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenu;
import com.juicyslew.moonstation14.ms14.power.ui.ApcToggleRequest;
import com.juicyslew.moonstation14.ms14.power.ui.ApcToggleResponse;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** The breaker label may optimistically present intent; only server menu snapshots drive device state. */
public final class ApcScreen extends AbstractContainerScreen<ApcMenu> {
    private static final ResourceLocation APC_IMAGE = ResourceLocation.fromNamespaceAndPath(
            MoonStation14.MOD_ID, "textures/gui/apc/apc.png");
    private static final int WIDTH = 250;
    private static final int HEIGHT = 174;
    private static final int RESPONSE_TIMEOUT_TICKS = 40;

    private Button breakerButton;
    private long inFlightRequest = -1;
    private long nextRequestId;
    private int pendingTicks;
    private long authoritativeRevision;
    private boolean authoritativeBreakerClosed;
    private boolean authoritativeTripLatched;
    private boolean presentationBreakerClosed;
    private Boolean queuedDesired;
    private float animatedBatteryPermille;

    public ApcScreen(ApcMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelY = HEIGHT + 20;
    }

    @Override
    protected void init() {
        super.init();
        // Start from this menu's current server snapshot; never carry a previous screen's bar forward.
        authoritativeBreakerClosed = menu.displayedBreaker() != 0;
        authoritativeTripLatched = menu.displayedTripLatched();
        presentationBreakerClosed = authoritativeBreakerClosed;
        authoritativeRevision = menu.displayedRevision();
        animatedBatteryPermille = authoritativeBatteryPermille();
        int x = leftPos + 128;
        int y = topPos + 58;
        breakerButton = addRenderableWidget(Button.builder(breakerLabel(), button -> sendToggle())
                .bounds(x, y, 105, 20).build());
        updateBreakerButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Presentation-only easing. The displayed percentage and all device state remain authoritative.
        animatedBatteryPermille += (authoritativeBatteryPermille() - animatedBatteryPermille) * 0.25f;
        observeMenuSnapshot();
        if (inFlightRequest >= 0 && ++pendingTicks >= RESPONSE_TIMEOUT_TICKS) {
            // Lost result: abandon the local prediction and let fresh menu data be authoritative.
            inFlightRequest = -1;
            pendingTicks = 0;
            queuedDesired = null;
            presentationBreakerClosed = authoritativeBreakerClosed;
        }
        updateBreakerButton();
    }

    private void sendDesired(boolean desiredClosed) {
        presentationBreakerClosed = desiredClosed;
        if (inFlightRequest >= 0) {
            queuedDesired = desiredClosed;
            return;
        }
        inFlightRequest = nextRequestId++;
        pendingTicks = 0;
        PacketDistributor.sendToServer(new ApcToggleRequest(menu.containerId, menu.session(),
                authoritativeRevision, inFlightRequest, desiredClosed));
        updateBreakerButton();
    }

    private void sendToggle() {
        boolean desired = !presentationBreakerClosed;
        presentationBreakerClosed = desired;
        if (inFlightRequest >= 0) queuedDesired = desired;
        else sendDesired(desired);
        updateBreakerButton();
    }

    private void observeMenuSnapshot() {
        long revision = menu.displayedRevision();
        if (revision >= authoritativeRevision) {
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
        if (response.hasSnapshot() && response.revision() >= authoritativeRevision) {
            // Accepted results confirm and rejected results roll back the intent. Both reconcile
            // from the authorized snapshot, never from client-derived device state.
            authoritativeRevision = response.revision();
            authoritativeBreakerClosed = response.breakerClosed();
            authoritativeTripLatched = response.tripLatched();
        }
        inFlightRequest = -1;
        pendingTicks = 0;
        Boolean next = queuedDesired;
        queuedDesired = null;
        presentationBreakerClosed = authoritativeBreakerClosed;
        if (response.hasSnapshot() && next != null && next != authoritativeBreakerClosed)
            sendDesired(next);
        updateBreakerButton();
    }

    private void updateBreakerButton() {
        if (breakerButton == null) return;
        breakerButton.setMessage(breakerLabel());
        breakerButton.active = true;
    }

    private Component breakerLabel() {
        return Component.translatable(presentationBreakerClosed
                ? "gui.moonstation14.apc.breaker_on" : "gui.moonstation14.apc.breaker_off");
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + WIDTH, topPos + HEIGHT, 0xff18232a);
        graphics.fill(leftPos + 5, topPos + 5, leftPos + WIDTH - 5, topPos + HEIGHT - 5, 0xff26343c);
        graphics.fill(leftPos + 10, topPos + 10, leftPos + WIDTH - 10, topPos + 29, 0xff10191e);

        graphics.blit(APC_IMAGE, leftPos + 20, topPos + 48, 0, 0, 32, 32, 32, 32);
        graphics.drawString(font, Component.translatable("gui.moonstation14.apc.breaker"),
                leftPos + 128, topPos + 42, 0xffdce7eb, false);
        if (authoritativeTripLatched) {
            graphics.drawString(font, Component.translatable("gui.moonstation14.apc.overload_tripped"),
                    leftPos + 128, topPos + 83, 0xffff5c4d, false);
        }

        graphics.drawString(font, Component.translatable("gui.moonstation14.apc.battery"),
                leftPos + 20, topPos + 94, 0xffdce7eb, false);
        int battery = authoritativeBatteryPermille();
        int barX = leftPos + 20;
        int barY = topPos + 110;
        int barWidth = 210;
        graphics.fill(barX, barY, barX + barWidth, barY + 10, 0xff101519);
        int fillWidth = Math.round(barWidth * animatedBatteryPermille / 1000.0f);
        if (fillWidth > 0) graphics.fill(barX, barY, barX + fillWidth, barY + 10, 0xff55c98b);
        graphics.drawString(font, Component.literal((battery + 5) / 10 + "%"),
                barX, barY + 15, 0xffffffff, false);

        graphics.drawString(font, Component.translatable("gui.moonstation14.apc.external_unavailable"),
                leftPos + 20, topPos + 139, 0xffaebbc0, false);
        graphics.drawString(font, Component.translatable("gui.moonstation14.apc.load_unavailable"),
                leftPos + 20, topPos + 151, 0xffaebbc0, false);
    }

    private int authoritativeBatteryPermille() {
        return Math.max(0, Math.min(1000, menu.displayedBatteryPermille()));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.moonstation14.apc.title"), 10, 15,
                0xfff1f6f8, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
