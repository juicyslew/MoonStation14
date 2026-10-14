package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlRequest;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmMenu;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmNumericBounds;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmResponseQueryPolicy;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineDraftField;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineSearchList;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineTabs;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.model.DraftFieldModel;
import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import com.juicyslew.moonstation14.ms14.ui.model.OptionModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Compact, server-fed alarm controls. All mutations still use the correlated menu protocol. */
public final class AirAlarmScreen extends AbstractContainerScreen<AirAlarmMenu> {
    private static final UUID QUERY_ID = new UUID(0, 0);
    private static final List<OptionModel.Option> TABS = List.of(new OptionModel.Option("controls", "Controls"), new OptionModel.Option("filters", "Filters"));
    private final OptionModel tabModel = new OptionModel(2);
    private final Map<UUID, DraftFieldModel> numericModels = new LinkedHashMap<>();
    private final List<AbstractWidget> devicesWidgets = new ArrayList<>(), settingsWidgets = new ArrayList<>(), filterWidgets = new ArrayList<>();
    private AirAlarmControlSnapshot state;
    private long nextRequest, pending = -1;
    private long listRevision;
    private int pendingTicks;
    private AirAlarmControlRequest.Operation pendingOperation;
    private UUID selected, numericDevice;
    private String localMessage;
    private int page, winX, winY, winW, winH;
    private MachineTabs tabs;
    private MachineSearchList deviceList;
    private Button enabled, mode, submitNumeric, configure, back;
    private MachineDraftField numeric;
    private DraftFieldModel.Intent draftIntent;
    private UUID draftTarget;
    private long draftProtocolRequest = -1;
    private long latestQueryRequest = -1;
    private boolean viewportTooSmall;
    private boolean detail;
    private AirAlarmLayout layout;
    private final Button[] filters = new Button[GasType.values().length];

    public AirAlarmScreen(AirAlarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = AirAlarmLayout.WIDTH; imageHeight = AirAlarmLayout.HEIGHT;
    }

    @Override protected void init() {
        super.init();
        devicesWidgets.clear(); settingsWidgets.clear(); filterWidgets.clear();
        layout = AirAlarmLayout.forViewport(width, height);
        winW = layout.width(); winH = layout.height(); viewportTooSmall = layout.tooSmall();
        imageWidth = winW; imageHeight = winH;
        leftPos = winX = (width - winW) / 2; topPos = winY = (height - winH) / 2;
        inventoryLabelY = winH + 20;
        if (viewportTooSmall || winW < 304 || winH < 224) { send(AirAlarmControlRequest.Operation.QUERY, false, 0); return; }
        var br = layout.detailBack();
        back = addRenderableWidget(Button.builder(Component.literal("Back"), b -> { detail = false; showPage(); }).bounds(br.x(), br.y(), br.width(), br.height()).build()); back.visible = false;
        var closeBounds = layout.close();
        var close = WindowChromeButtons.close(winX, winY, winW, this::onClose);
        close.setPosition(closeBounds.x(), closeBounds.y());
        close.setWidth(closeBounds.width()); close.setHeight(closeBounds.height());
        addRenderableWidget(close);
        deviceList = new MachineSearchList(layout.deviceRows().x(), layout.deviceRows().y(), layout.deviceRows().width(), 4, Component.literal("Devices"),
                new FilteredListModel(32, 4), id -> { try { selected = UUID.fromString(id); replaceNumericField(); refresh(); } catch (IllegalArgumentException ignored) { } });
        devicesWidgets.add(addRenderableWidget(deviceList)); devicesWidgets.add(addRenderableWidget(deviceList.searchBox()));
        var refreshBounds = layout.refresh();
        Button refresh = Button.builder(Component.literal("Refresh"), b -> send(AirAlarmControlRequest.Operation.QUERY, false, 0))
                .bounds(refreshBounds.x(), refreshBounds.y(), refreshBounds.width(), refreshBounds.height()).build();
        devicesWidgets.add(addRenderableWidget(refresh));
        var cr = layout.configure(); configure = addRenderableWidget(Button.builder(Component.literal("Configure"), b -> { if (selectedDevice() != null) { detail = true; showPage(); } }).bounds(cr.x(), cr.y(), cr.width(), cr.height()).build()); devicesWidgets.add(configure);
        var tr = layout.detailTabs(); tabs = addRenderableWidget(new MachineTabs(tr.x(), tr.y(), tr.width(), tr.height(), Component.literal("Device configuration"), tabModel, ignored -> showPage())); tabs.snapshot(0, TABS); tabs.visible = false;
        var enabledBounds = layout.enabled(); var modeBounds = layout.mode();
        enabled = addRenderableWidget(Button.builder(Component.literal("Enabled"), b -> { var d = selectedDevice(); if (d != null) send(AirAlarmControlRequest.Operation.SET_ENABLED, !d.enabled(), 0); }).bounds(enabledBounds.x(), enabledBounds.y(), enabledBounds.width(), enabledBounds.height()).build()); settingsWidgets.add(enabled);
        mode = addRenderableWidget(Button.builder(Component.literal("Mode"), b -> { var d = selectedDevice(); if (d != null) send(AirAlarmControlRequest.Operation.SET_SCRUBBER_MODE, false, (d.mode() + 1) % VentilationDeviceConfig.ScrubberMode.values().length); }).bounds(modeBounds.x(), modeBounds.y(), modeBounds.width(), modeBounds.height()).build()); settingsWidgets.add(mode);
        var editBounds = layout.numericEdit();
        numeric = new MachineDraftField(editBounds.x(), editBounds.y(), editBounds.width(), layout.numericSubmit().width(), 12, Component.literal("Submit"), numericModel(selectedDevice()),
                intent -> submitDraft(intent));
        numericDevice = selectedDevice() == null ? null : selectedDevice().id();
        settingsWidgets.add(addRenderableWidget(numeric)); settingsWidgets.add(addRenderableWidget(numeric.editBox()));
        int gas = 0;
        for (GasType type : GasType.values()) {
            final int n = gas; var bounds = layout.gas(gas); Button button = Button.builder(Component.literal(type.id()), b -> { var d = selectedDevice(); if (d != null) send(AirAlarmControlRequest.Operation.SET_FILTER_GAS, (d.filterBits() & (1 << n)) == 0, n); })
                    .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build();
            filters[gas++] = button; filterWidgets.add(addRenderableWidget(button));
        }
        showPage(); refresh(); send(AirAlarmControlRequest.Operation.QUERY, false, 0);
    }

    private void showPage() {
        String id = tabModel.selectedId();
        setVisible(devicesWidgets, !detail); setVisible(settingsWidgets, detail && "controls".equals(id)); setVisible(filterWidgets, detail && "filters".equals(id));
        back.visible = detail; tabs.visible = detail;
        if (getFocused() instanceof AbstractWidget focused && !focused.visible) setFocused(null);
        refresh();
    }
    private void setVisible(List<AbstractWidget> widgets, boolean visible) { for (var w : widgets) { w.visible = visible; w.active = visible; } }
    private void submitDraft(DraftFieldModel.Intent intent) {
        var d = selectedDevice();
        if (d == null || pending >= 0 || !d.id().equals(numericDevice)) {
            DraftFieldModel intendedModel = numericDevice == null ? null : numericModels.get(numericDevice);
            if (intendedModel != null) intendedModel.cancelPending(intent.requestId());
            return;
        }
        try {
            double value = Double.parseDouble(intent.value());
            if (!AirAlarmNumericBounds.valid(d.kind(), value)) { numericModel(d).cancelPending(intent.requestId()); replaceNumericField(); localMessage = d.kind() == VentilationDeviceConfig.Kind.VENT ? "Enter 0–4500 kPa" : "Enter 0–0.2 m³/s"; refresh(); return; }
            draftIntent = intent;
            draftTarget = d.id();
            send(d.kind() == VentilationDeviceConfig.Kind.VENT ? AirAlarmControlRequest.Operation.SET_EXTERNAL_PRESSURE_BOUND : AirAlarmControlRequest.Operation.SET_VOLUME_RATE, false, 0, value);
            draftProtocolRequest = pending;
        } catch (NumberFormatException ex) { numericModel(d).cancelPending(intent.requestId()); replaceNumericField(); localMessage = "Enter a valid number"; refresh(); }
    }
    private void refreshList() {
        if (state == null || deviceList == null) return;
        List<FilteredListModel.Entry> entries = new ArrayList<>();
        for (var d : state.devices()) entries.add(new FilteredListModel.Entry(d.id().toString(), (d.linked() ? "[L] " : "[ ] ") + d.kind().name() + " " + d.pos().toShortString() + (d.online() ? "" : " offline")));
        deviceList.snapshot(listRevision, entries);
    }
    private AirAlarmControlSnapshot.Device selectedDevice() { if (state != null && selected != null) for (var d : state.devices()) if (selected.equals(d.id())) return d; return null; }
    private DraftFieldModel numericModel(AirAlarmControlSnapshot.Device d) {
        if (d == null) return DraftFieldModel.number(12, BigDecimal.ZERO, new BigDecimal("4500"));
        DraftFieldModel model = numericModels.get(d.id());
        if (model == null) {
            if (numericModels.size() >= 32) numericModels.remove(numericModels.keySet().iterator().next());
            model = d.kind() == VentilationDeviceConfig.Kind.VENT
                    ? DraftFieldModel.number(12, BigDecimal.ZERO, new BigDecimal("4500"))
                    : DraftFieldModel.number(12, BigDecimal.ZERO, new BigDecimal("0.2"));
            model.snapshot(d.revision(), numericText(d)); numericModels.put(d.id(), model);
        }
        return model;
    }
    private void replaceNumericField() {
        if (numeric == null) return;
        removeWidget(numeric); removeWidget(numeric.editBox()); settingsWidgets.remove(numeric); settingsWidgets.remove(numeric.editBox());
        var editBounds = layout.numericEdit();
        numeric = new MachineDraftField(editBounds.x(), editBounds.y(), editBounds.width(), layout.numericSubmit().width(), 12, Component.literal("Submit"), numericModel(selectedDevice()), this::submitDraft);
        numericDevice = selectedDevice() == null ? null : selectedDevice().id();
        settingsWidgets.add(addRenderableWidget(numeric)); settingsWidgets.add(addRenderableWidget(numeric.editBox()));
    }
    private void send(AirAlarmControlRequest.Operation op, boolean desired, int index) { send(op, desired, index, 0); }
    private void send(AirAlarmControlRequest.Operation op, boolean desired, int index, double value) {
        var d = selectedDevice(); if ((pending >= 0 && !(op == AirAlarmControlRequest.Operation.QUERY && draftIntent != null)) || (op != AirAlarmControlRequest.Operation.QUERY && d == null)) return;
        long requestId = nextRequest++;
        if (op == AirAlarmControlRequest.Operation.QUERY) latestQueryRequest = requestId;
        if (draftIntent == null || op != AirAlarmControlRequest.Operation.QUERY) { pending = requestId; pendingTicks = 0; pendingOperation = op; }
        PacketDistributor.sendToServer(new AirAlarmControlRequest(menu.containerId, menu.session(), requestId, d == null ? QUERY_ID : d.id(), d == null ? 0 : d.revision(), op, desired, index, value)); refresh();
    }
    public void handleResponse(AirAlarmControlSnapshot response) {
        boolean commandResponse = draftIntent != null && response.requestId() == draftProtocolRequest;
        boolean currentQuery = response.requestId() == latestQueryRequest;
        boolean matchedPendingCommand = response.requestId() == pending && pendingOperation != null
                && pendingOperation != AirAlarmControlRequest.Operation.QUERY;
        if (response.containerId() != menu.containerId || !response.session().equals(menu.session())
                || (response.requestId() != pending && !commandResponse && !currentQuery)) return;
        boolean commandPending = draftIntent != null && response.requestId() == draftProtocolRequest
                && response.accepted();
        if (!commandPending && response.requestId() == pending) { pending = -1; pendingTicks = 0; }
        if (!response.accepted() && response.devices().isEmpty()) {
            state = null; refresh();
            if (AirAlarmResponseQueryPolicy.shouldQuery(currentQuery, matchedPendingCommand)) send(AirAlarmControlRequest.Operation.QUERY, false, 0);
            return;
        }
        state = response; listRevision++; localMessage = null;
        if (selected != null && selectedDevice() == null) { selected = null; detail = false; localMessage = "Selected device is no longer available"; }
        if (draftIntent != null) {
            var intended = response.devices().stream().filter(d -> d.id().equals(draftTarget)).findFirst().orElse(null);
            DraftFieldModel model = numericModels.get(draftTarget);
            boolean rejected = commandResponse && !response.accepted();
            boolean observed = intended != null && intended.revision() > draftIntent.baseRevision();
            if (model != null && (rejected || observed)) {
                boolean fulfilled = observed && matchesDraft(draftIntent, intended);
                model.resolve(draftIntent.requestId(), intended == null ? model.revision() : intended.revision(),
                        intended == null ? model.authority() : numericText(intended), fulfilled);
                draftIntent = null; draftTarget = null; draftProtocolRequest = -1;
                pending = -1; pendingTicks = 0;
            }
        }
        if (numeric != null) { var device = selectedDevice(); if (device != null && device.linked()) numericModel(device).snapshot(device.revision(), numericText(device)); }
        refresh(); if (AirAlarmResponseQueryPolicy.shouldQuery(currentQuery, matchedPendingCommand)) send(AirAlarmControlRequest.Operation.QUERY, false, 0);
    }
    private static boolean matchesDraft(DraftFieldModel.Intent intent, AirAlarmControlSnapshot.Device device) {
        try {
            return new BigDecimal(intent.value()).compareTo(new BigDecimal(numericText(device))) == 0;
        } catch (NumberFormatException ex) { return false; }
    }
    private void refresh() {
        if (tabs == null) return; boolean ready = state != null && pending < 0; refreshList(); var d = selectedDevice();
        boolean devicesPage = !detail, settingsPage = detail && "controls".equals(tabModel.selectedId()), filtersPage = detail && "filters".equals(tabModel.selectedId());
        for (AbstractWidget w : devicesWidgets) { w.visible = devicesPage; w.active = devicesPage && ready; }
        for (AbstractWidget w : settingsWidgets) { w.visible = settingsPage; w.active = settingsPage && ready; }
        for (AbstractWidget w : filterWidgets) { w.visible = filtersPage; w.active = filtersPage && ready; }
        configure.active = devicesPage && ready && d != null;
        enabled.visible = settingsPage && d != null; enabled.active = settingsPage && ready && state.powered() && d != null && d.linked() && d.online();
        if (d != null) enabled.setMessage(Component.literal("Enabled: " + (d.enabled() ? "Yes" : "No")));
        mode.visible = settingsPage && d != null && d.kind() == VentilationDeviceConfig.Kind.SCRUBBER; mode.active = enabled.active && mode.visible;
        if (mode.visible) mode.setMessage(Component.literal("Mode: " + VentilationDeviceConfig.ScrubberMode.values()[d.mode()]));
        if (numeric != null) {
            numeric.refreshState();
            boolean available = settingsPage && ready && state.powered() && d != null && d.linked() && d.online();
            numeric.visible = settingsPage && d != null && d.linked() && d.online();
            numeric.editBox().visible = numeric.visible;
            numeric.active = numeric.active && available;
            numeric.editBox().active = available && numericModel(d).pending() == null;
            numeric.editBox().setEditable(available && numericModel(d).pending() == null);
        }
        for (int i = 0; i < filters.length; i++) { filters[i].visible = filtersPage && d != null && d.kind() == VentilationDeviceConfig.Kind.SCRUBBER; filters[i].active = filters[i].visible && ready && state.powered() && d.linked() && d.online(); if (d != null) filters[i].setMessage(Component.literal(GasType.values()[i].id() + ((d.filterBits() & (1 << i)) != 0 ? "+" : "-"))); }
    }
    private static String numericText(AirAlarmControlSnapshot.Device d) { return String.format(java.util.Locale.ROOT, "%.3f", d.kind() == VentilationDeviceConfig.Kind.VENT ? d.externalPressureBoundKPa() : d.volumeRateM3PerSecond()); }
    @Override public void containerTick() { super.containerTick(); if (pending >= 0 && ++pendingTicks >= 100) { if (draftIntent != null) { DraftFieldModel model = numericModels.get(draftTarget); if (model != null) model.cancelPending(draftIntent.requestId()); draftIntent = null; draftTarget = null; draftProtocolRequest = -1; } pending = -1; localMessage = "Request timed out"; refresh(); } }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MachineWindowRenderer.window(g, font, winX, winY, winW, winH, Component.literal(detail ? "Air Alarm — Configure" : "Air Alarm — Devices"));
        var inset = layout.inset(); MachineWindowRenderer.insetPanel(g, inset.x(), inset.y(), inset.width(), inset.height());
        String pageId = tabModel.selectedId();
        if (detail) { var d = selectedDevice(); String identity = d == null ? "Device unavailable" : d.kind().name() + " — " + d.pos().toShortString() + (!d.online() ? " (offline)" : !d.linked() ? " (unlinked; pair first)" : ""); g.enableScissor(winX+96,winY+30,winX+winW-8,winY+52); try { g.drawString(font, identity, winX+96,winY+34,MachineWindowRenderer.TEXT,true); } finally { g.disableScissor(); } if ("controls".equals(pageId)) g.drawString(font,d != null && d.kind()==VentilationDeviceConfig.Kind.VENT ? "Pressure bound (kPa)" : "Volume rate (m³/s)",winX+8,winY+130,MachineWindowRenderer.MUTED_TEXT,true); }
        String message = viewportTooSmall ? "Viewport too small — resize to use controls" : localMessage != null ? localMessage : !detail ? "Configure device list with a Network Configurator." : state == null ? "Waiting for alarm" : state.powered() ? "Power: 100 W" : "Power unavailable (read-only)";
        var footer = viewportTooSmall ? layout.fallback() : layout.footer();
        g.enableScissor(footer.x(), footer.y(), footer.x() + footer.width(), footer.y() + footer.height());
        try { MachineWindowRenderer.footer(g, font, footer.x(), footer.y(), footer.width(), footer.height(), Component.literal(message)); }
        finally { g.disableScissor(); }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) { }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) { renderBackground(g, mouseX, mouseY, partialTick); super.render(g, mouseX, mouseY, partialTick); }
}

