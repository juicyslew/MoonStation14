package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlRequest;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmMenu;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmNumericBounds;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmResponseQueryPolicy;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmMode;
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
    private static final List<OptionModel.Option> TABS = List.of(
            new OptionModel.Option("alarm", Component.translatable("ui.moonstation14.air_alarm.tab.alarm").getString()),
            new OptionModel.Option("controls", Component.translatable("ui.moonstation14.air_alarm.tab.controls").getString()),
            new OptionModel.Option("filters", Component.translatable("ui.moonstation14.air_alarm.tab.filters").getString()));
    private final OptionModel tabModel = new OptionModel(3);
    private final Map<UUID, DraftFieldModel> numericModels = new LinkedHashMap<>();
    private final List<AbstractWidget> devicesWidgets = new ArrayList<>(), settingsWidgets = new ArrayList<>(), filterWidgets = new ArrayList<>(), alarmWidgets = new ArrayList<>();
    private AirAlarmControlSnapshot state;
    private long nextRequest, pending = -1;
    private long listRevision;
    private int pendingTicks;
    private AirAlarmControlRequest.Operation pendingOperation;
    private UUID selected, numericDevice;
    private Component localMessage;
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
    private final Button[] alarmModes = new Button[AirAlarmMode.values().length];
    private Button autoMode;

    public AirAlarmScreen(AirAlarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = AirAlarmLayout.WIDTH; imageHeight = AirAlarmLayout.HEIGHT;
    }

    @Override protected void init() {
        super.init();
        devicesWidgets.clear(); settingsWidgets.clear(); filterWidgets.clear(); alarmWidgets.clear();
        layout = AirAlarmLayout.forViewport(width, height);
        winW = layout.width(); winH = layout.height(); viewportTooSmall = layout.tooSmall();
        imageWidth = winW; imageHeight = winH;
        leftPos = winX = (width - winW) / 2; topPos = winY = (height - winH) / 2;
        inventoryLabelY = winH + 20;
        if (viewportTooSmall || winW < 304 || winH < 224) { send(AirAlarmControlRequest.Operation.QUERY, false, 0); return; }
        var br = layout.detailBack();
        back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> { detail = false; showPage(); }).bounds(br.x(), br.y(), br.width(), br.height()).build()); back.visible = false;
        var closeBounds = layout.close();
        var close = WindowChromeButtons.close(winX, winY, winW, this::onClose);
        close.setPosition(closeBounds.x(), closeBounds.y());
        close.setWidth(closeBounds.width()); close.setHeight(closeBounds.height());
        addRenderableWidget(close);
        deviceList = new MachineSearchList(layout.deviceRows().x(), layout.deviceRows().y(), layout.deviceRows().width(), 4, Component.translatable("ui.moonstation14.air_alarm.devices"),
                new FilteredListModel(32, 4), id -> { try { selected = UUID.fromString(id); replaceNumericField(); refresh(); } catch (IllegalArgumentException ignored) { } });
        devicesWidgets.add(addRenderableWidget(deviceList)); devicesWidgets.add(addRenderableWidget(deviceList.searchBox()));
        var refreshBounds = layout.refresh();
        Button refresh = Button.builder(Component.translatable("ui.moonstation14.air_alarm.refresh"), b -> send(AirAlarmControlRequest.Operation.QUERY, false, 0))
                .bounds(refreshBounds.x(), refreshBounds.y(), refreshBounds.width(), refreshBounds.height()).build();
        devicesWidgets.add(addRenderableWidget(refresh));
        var cr = layout.configure(); configure = addRenderableWidget(Button.builder(Component.translatable("ui.moonstation14.air_alarm.open_controller"), b -> { detail = true; showPage(); }).bounds(cr.x(), cr.y(), cr.width(), cr.height()).build()); devicesWidgets.add(configure);
        var tr = layout.detailTabs(); tabs = addRenderableWidget(new MachineTabs(tr.x(), tr.y(), tr.width(), tr.height(), Component.translatable("ui.moonstation14.air_alarm.device_configuration"), tabModel, ignored -> showPage())); tabs.snapshot(0, TABS); tabs.visible = false;
        for (AirAlarmMode alarmMode : AirAlarmMode.values()) {
            int index = alarmMode.ordinal(); var r = layout.alarmMode(index);
            var button = Button.builder(modeLabel(alarmMode), b -> send(AirAlarmControlRequest.Operation.SET_ALARM_MODE,
                    false, alarmMode.ordinal())).bounds(r.x(),r.y(),r.width(),r.height()).build();
            alarmModes[index] = addRenderableWidget(button); alarmWidgets.add(button);
        }
        var autoBounds = layout.autoMode();
        autoMode = addRenderableWidget(Button.builder(Component.translatable("ui.moonstation14.air_alarm.auto_on"), b -> {
            if (state != null) send(AirAlarmControlRequest.Operation.SET_AUTO_MODE, !state.autoMode(), 0);
        }).bounds(autoBounds.x(), autoBounds.y(), autoBounds.width(), autoBounds.height()).build()); alarmWidgets.add(autoMode);
        var enabledBounds = layout.enabled(); var modeBounds = layout.mode();
        enabled = addRenderableWidget(Button.builder(Component.translatable("ui.moonstation14.air_alarm.enabled"), b -> { var d = selectedDevice(); if (d != null) send(AirAlarmControlRequest.Operation.SET_ENABLED, !d.enabled(), 0); }).bounds(enabledBounds.x(), enabledBounds.y(), enabledBounds.width(), enabledBounds.height()).build()); settingsWidgets.add(enabled);
        mode = addRenderableWidget(Button.builder(Component.translatable("ui.moonstation14.air_alarm.mode_label"), b -> { var d = selectedDevice(); if (d != null) send(AirAlarmControlRequest.Operation.SET_SCRUBBER_MODE, false, (d.mode() + 1) % VentilationDeviceConfig.ScrubberMode.values().length); }).bounds(modeBounds.x(), modeBounds.y(), modeBounds.width(), modeBounds.height()).build()); settingsWidgets.add(mode);
        var editBounds = layout.numericEdit();
        numeric = new MachineDraftField(editBounds.x(), editBounds.y(), editBounds.width(), layout.numericSubmit().width(), 12, Component.translatable("ui.moonstation14.air_alarm.submit"), numericModel(selectedDevice()),
                intent -> submitDraft(intent));
        numericDevice = selectedDevice() == null ? null : selectedDevice().id();
        settingsWidgets.add(addRenderableWidget(numeric)); settingsWidgets.add(addRenderableWidget(numeric.editBox()));
        int gas = 0;
        for (GasType type : GasType.values()) {
            final int n = gas; var bounds = layout.gas(gas); Button button = Button.builder(gasName(type), b -> { var d = selectedDevice(); if (d != null) send(AirAlarmControlRequest.Operation.SET_FILTER_GAS, (d.filterBits() & (1 << n)) == 0, n); })
                    .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build();
            filters[gas++] = button; filterWidgets.add(addRenderableWidget(button));
        }
        showPage(); refresh(); send(AirAlarmControlRequest.Operation.QUERY, false, 0);
    }

    private void showPage() {
        String id = tabModel.selectedId();
        setVisible(devicesWidgets, !detail); setVisible(alarmWidgets, detail && "alarm".equals(id)); setVisible(settingsWidgets, detail && "controls".equals(id)); setVisible(filterWidgets, detail && "filters".equals(id));
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
            if (!AirAlarmNumericBounds.valid(d.kind(), value)) { numericModel(d).cancelPending(intent.requestId()); replaceNumericField(); localMessage = Component.translatable(d.kind() == VentilationDeviceConfig.Kind.VENT ? "ui.moonstation14.air_alarm.invalid_pressure" : "ui.moonstation14.air_alarm.invalid_rate"); refresh(); return; }
            draftIntent = intent;
            draftTarget = d.id();
            send(d.kind() == VentilationDeviceConfig.Kind.VENT ? AirAlarmControlRequest.Operation.SET_EXTERNAL_PRESSURE_BOUND : AirAlarmControlRequest.Operation.SET_VOLUME_RATE, false, 0, value);
            draftProtocolRequest = pending;
        } catch (NumberFormatException ex) { numericModel(d).cancelPending(intent.requestId()); replaceNumericField(); localMessage = Component.translatable("ui.moonstation14.air_alarm.invalid_number"); refresh(); }
    }
    private void refreshList() {
        if (state == null || deviceList == null) return;
        List<FilteredListModel.Entry> entries = new ArrayList<>();
        for (var d : state.devices()) entries.add(new FilteredListModel.Entry(d.id().toString(), (d.linked() ? "[L] " : "[ ] ") + deviceName(d.kind()).getString() + " " + d.pos().toShortString() + (d.online() ? "" : " " + Component.translatable("ui.moonstation14.air_alarm.offline").getString())));
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
        numeric = new MachineDraftField(editBounds.x(), editBounds.y(), editBounds.width(), layout.numericSubmit().width(), 12, Component.translatable("ui.moonstation14.air_alarm.submit"), numericModel(selectedDevice()), this::submitDraft);
        numericDevice = selectedDevice() == null ? null : selectedDevice().id();
        settingsWidgets.add(addRenderableWidget(numeric)); settingsWidgets.add(addRenderableWidget(numeric.editBox()));
    }
    private void send(AirAlarmControlRequest.Operation op, boolean desired, int index) { send(op, desired, index, 0); }
    private void send(AirAlarmControlRequest.Operation op, boolean desired, int index, double value) {
        var d = selectedDevice(); boolean controllerIntent = op == AirAlarmControlRequest.Operation.SET_ALARM_MODE || op == AirAlarmControlRequest.Operation.SET_AUTO_MODE;
        if ((pending >= 0 && !(op == AirAlarmControlRequest.Operation.QUERY && draftIntent != null)) || (op != AirAlarmControlRequest.Operation.QUERY && !controllerIntent && d == null)) return;
        long requestId = nextRequest++;
        if (op == AirAlarmControlRequest.Operation.QUERY) latestQueryRequest = requestId;
        if (draftIntent == null || op != AirAlarmControlRequest.Operation.QUERY) { pending = requestId; pendingTicks = 0; pendingOperation = op; }
        PacketDistributor.sendToServer(buildIntent(menu.containerId, menu.session(), requestId, op, desired, index,
                value, state, d)); refresh();
    }
    static AirAlarmControlRequest buildIntent(int container, UUID session, long requestId,
            AirAlarmControlRequest.Operation op, boolean desired, int index, double value,
            AirAlarmControlSnapshot snapshot, AirAlarmControlSnapshot.Device device) {
        boolean controller = op == AirAlarmControlRequest.Operation.SET_ALARM_MODE || op == AirAlarmControlRequest.Operation.SET_AUTO_MODE;
        long revision = controller && snapshot != null ? snapshot.controllerStateRevision()
                : device == null ? 0 : device.revision();
        return new AirAlarmControlRequest(container, session, requestId,
                controller || device == null ? QUERY_ID : device.id(), revision, op, desired, index, value);
    }
    static boolean correlatedResponse(int container, UUID session, long pendingRequest, long draftRequest,
                                      long queryRequest, AirAlarmControlSnapshot response) {
        return response != null && response.containerId() == container && response.session().equals(session)
                && (response.requestId() == pendingRequest || response.requestId() == draftRequest
                    || response.requestId() == queryRequest);
    }
    public void handleResponse(AirAlarmControlSnapshot response) {
        boolean commandResponse = draftIntent != null && response.requestId() == draftProtocolRequest;
        boolean currentQuery = response.requestId() == latestQueryRequest;
        boolean matchedPendingCommand = response.requestId() == pending && pendingOperation != null
                && pendingOperation != AirAlarmControlRequest.Operation.QUERY;
        if (!correlatedResponse(menu.containerId, menu.session(), pending,
                commandResponse ? draftProtocolRequest : Long.MIN_VALUE,
                currentQuery ? latestQueryRequest : Long.MIN_VALUE, response)) return;
        boolean commandPending = draftIntent != null && response.requestId() == draftProtocolRequest
                && response.accepted();
        if (!commandPending && response.requestId() == pending) { pending = -1; pendingTicks = 0; }
        if (!response.accepted() && response.devices().isEmpty()) {
            state = null; refresh();
            if (AirAlarmResponseQueryPolicy.shouldQuery(currentQuery, matchedPendingCommand)) send(AirAlarmControlRequest.Operation.QUERY, false, 0);
            return;
        }
        state = response; listRevision++; localMessage = null;
        if (selected != null && selectedDevice() == null) { selected = null; detail = false; localMessage = Component.translatable("ui.moonstation14.air_alarm.selection_unavailable"); }
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
        for (AbstractWidget w : alarmWidgets) { w.visible = detail && "alarm".equals(tabModel.selectedId()); w.active = w.visible && ready && state.powered(); }
        for (int i=0;i<alarmModes.length;i++) if (state != null)
            alarmModes[i].setMessage(modeLabel(AirAlarmMode.values()[i]).copy()
                    .append(state.currentMode() == AirAlarmMode.values()[i] ? " ✓" : ""));
        if (autoMode != null && state != null) autoMode.setMessage(Component.translatable(state.autoMode() ? "ui.moonstation14.air_alarm.auto_on" : "ui.moonstation14.air_alarm.auto_off"));
        for (AbstractWidget w : settingsWidgets) { w.visible = settingsPage; w.active = settingsPage && ready; }
        for (AbstractWidget w : filterWidgets) { w.visible = filtersPage; w.active = filtersPage && ready; }
        configure.active = devicesPage && ready;
        enabled.visible = settingsPage && d != null; enabled.active = settingsPage && ready && state.powered() && d != null && d.linked() && d.online();
        if (d != null) enabled.setMessage(Component.translatable(d.enabled() ? "ui.moonstation14.air_alarm.enabled_yes" : "ui.moonstation14.air_alarm.enabled_no"));
        mode.visible = settingsPage && d != null && d.kind() == VentilationDeviceConfig.Kind.SCRUBBER; mode.active = enabled.active && mode.visible;
        if (mode.visible) mode.setMessage(Component.translatable("ui.moonstation14.air_alarm.scrubber_mode",
                Component.translatable("ui.moonstation14.air_alarm.scrubber." + VentilationDeviceConfig.ScrubberMode.values()[d.mode()].name().toLowerCase(java.util.Locale.ROOT))));
        if (numeric != null) {
            numeric.refreshState();
            boolean available = settingsPage && ready && state.powered() && d != null && d.linked() && d.online();
            numeric.visible = settingsPage && d != null && d.linked() && d.online();
            numeric.editBox().visible = numeric.visible;
            numeric.active = numeric.active && available;
            numeric.editBox().active = available && numericModel(d).pending() == null;
            numeric.editBox().setEditable(available && numericModel(d).pending() == null);
        }
        for (int i = 0; i < filters.length; i++) { filters[i].visible = filtersPage && d != null && d.kind() == VentilationDeviceConfig.Kind.SCRUBBER; filters[i].active = filters[i].visible && ready && state.powered() && d.linked() && d.online(); if (d != null) filters[i].setMessage(gasName(GasType.values()[i]).copy().append((d.filterBits() & (1 << i)) != 0 ? " +" : " −")); }
    }
    private static String numericText(AirAlarmControlSnapshot.Device d) { return String.format(java.util.Locale.ROOT, "%.3f", d.kind() == VentilationDeviceConfig.Kind.VENT ? d.externalPressureBoundKPa() : d.volumeRateM3PerSecond()); }
    private static Component modeLabel(AirAlarmMode mode) {
        return Component.translatable("ui.moonstation14.air_alarm.mode." + mode.name().toLowerCase(java.util.Locale.ROOT));
    }
    private static Component gasName(GasType gas) { return Component.translatable("ui.moonstation14.gas." + gas.id()); }
    private static Component deviceName(VentilationDeviceConfig.Kind kind) {
        return Component.translatable(kind == VentilationDeviceConfig.Kind.VENT
                ? "block.moonstation14.gas_vent" : "block.moonstation14.gas_scrubber");
    }
    private AirAlarmControlSnapshot.MonitorReading selectedReading() {
        if (state == null || selected == null) return null;
        for (var reading : state.readings()) if (selected.equals(reading.sourceId())) return reading;
        return null;
    }
    private String summaryReadingStatus() {
        if (state.readings().isEmpty()) return "ui.moonstation14.air_alarm.summary_no_sources";
        if (state.readings().stream().anyMatch(r -> !r.valid())) return "ui.moonstation14.air_alarm.summary_unavailable";
        if (state.readings().stream().anyMatch(r -> r.valid() && r.stale())) return "ui.moonstation14.air_alarm.summary_cached";
        return "ui.moonstation14.air_alarm.summary_current";
    }
    @Override public void containerTick() { super.containerTick(); if (pending >= 0 && ++pendingTicks >= 100) { if (draftIntent != null) { DraftFieldModel model = numericModels.get(draftTarget); if (model != null) model.cancelPending(draftIntent.requestId()); draftIntent = null; draftTarget = null; draftProtocolRequest = -1; } pending = -1; localMessage = Component.translatable("ui.moonstation14.air_alarm.request_timeout"); refresh(); } }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MachineWindowRenderer.window(g, font, winX, winY, winW, winH, Component.translatable("ui.moonstation14.air_alarm.title"));
        var inset = layout.inset(); MachineWindowRenderer.insetPanel(g, inset.x(), inset.y(), inset.width(), inset.height());
        String pageId = tabModel.selectedId();
        if (state != null && !detail) {
            var r = layout.summary();
            Component summary = Component.translatable("ui.moonstation14.air_alarm.summary",
                    Component.translatable("ui.moonstation14.air_alarm.severity." + state.aggregateSeverity().name().toLowerCase(java.util.Locale.ROOT)),
                    modeLabel(state.currentMode()), Component.translatable(state.autoMode() ? "ui.moonstation14.air_alarm.auto_short" : "ui.moonstation14.air_alarm.manual_short"),
                    Component.translatable(summaryReadingStatus()));
            g.enableScissor(r.x(),r.y(),r.right(),r.bottom()); try { g.drawString(font,summary,r.x(),r.y(),MachineWindowRenderer.TEXT,true); } finally { g.disableScissor(); }
        }
        if (detail) {
            var d = selectedDevice(); String identity = d == null ? Component.translatable("ui.moonstation14.air_alarm.device_unavailable").getString()
                    : deviceName(d.kind()).getString() + " — " + d.pos().toShortString() + (!d.online() ? " ("+Component.translatable("ui.moonstation14.air_alarm.offline").getString()+")" : "");
            g.enableScissor(winX+96,winY+30,winX+winW-8,winY+52); try { g.drawString(font, identity, winX+96,winY+34,MachineWindowRenderer.TEXT,true); } finally { g.disableScissor(); }
            if ("controls".equals(pageId)) g.drawString(font,Component.translatable(d != null && d.kind()==VentilationDeviceConfig.Kind.VENT ? "ui.moonstation14.air_alarm.pressure_bound" : "ui.moonstation14.air_alarm.volume_rate"),winX+8,winY+130,MachineWindowRenderer.MUTED_TEXT,true);
            if ("alarm".equals(pageId)) renderAlarmDetails(g);
        }
        Component message = viewportTooSmall ? Component.translatable("ui.moonstation14.air_alarm.too_small")
                : localMessage != null ? localMessage : !detail ? Component.translatable("ui.moonstation14.air_alarm.membership_configurator")
                : state == null ? Component.translatable("ui.moonstation14.air_alarm.waiting")
                : state.powered() ? Component.translatable("ui.moonstation14.air_alarm.powered")
                : Component.translatable("ui.moonstation14.air_alarm.power_unavailable");
        var footer = viewportTooSmall ? layout.fallback() : layout.footer();
        g.enableScissor(footer.x(), footer.y(), footer.x() + footer.width(), footer.y() + footer.height());
        try { MachineWindowRenderer.footer(g, font, footer.x(), footer.y(), footer.width(), footer.height(), message); }
        finally { g.disableScissor(); }
    }
    private void renderAlarmDetails(GuiGraphics g) {
        var reading = selectedReading();
        if (reading == null || !reading.valid()) {
            g.drawString(font, Component.translatable("ui.moonstation14.air_alarm.reading_unavailable"), winX+8,winY+126,MachineWindowRenderer.MUTED_TEXT,true);
            return;
        }
        var stats = layout.monitorStats();
        String values = Component.translatable("ui.moonstation14.air_alarm.reading_stats", format(reading.pressureKpa()), format(reading.temperatureKelvin()), format(reading.totalMoles())).getString();
        g.enableScissor(stats.x(),stats.y(),stats.right(),stats.bottom()); try {
            if (reading.stale()) {
                g.drawString(font,Component.translatable("ui.moonstation14.air_alarm.cached_reading"),stats.x(),stats.y(),MachineWindowRenderer.MUTED_TEXT,true);
                g.drawString(font,values,stats.x(),stats.y()+10,MachineWindowRenderer.TEXT,true);
            } else g.drawString(font,values,stats.x(),stats.y(),MachineWindowRenderer.TEXT,true);
        } finally { g.disableScissor(); }
        for (int i=0;i<GasType.values().length;i++) {
            var r=layout.gasReading(i); double moles=reading.gasMoles().get(i); double fraction=reading.totalMoles()>0?moles/reading.totalMoles():0;
            String text=Component.translatable("ui.moonstation14.air_alarm.gas_reading",gasName(GasType.values()[i]),format(moles),format(fraction*100)).getString();
            g.enableScissor(r.x(),r.y(),r.right(),r.bottom()); try { g.drawString(font,text,r.x(),r.y(),MachineWindowRenderer.TEXT,true); } finally { g.disableScissor(); }
        }
    }
    private static String format(double value) { return String.format(java.util.Locale.ROOT,"%.2f",value); }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) { }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) { renderBackground(g, mouseX, mouseY, partialTick); super.render(g, mouseX, mouseY, partialTick); }
}
