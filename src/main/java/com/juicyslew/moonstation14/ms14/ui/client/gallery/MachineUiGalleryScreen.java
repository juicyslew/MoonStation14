package com.juicyslew.moonstation14.ms14.ui.client.gallery;

import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.collection.QueueWidget;
import com.juicyslew.moonstation14.ms14.ui.client.collection.SampleViewportWidget;
import com.juicyslew.moonstation14.ms14.ui.client.collection.SlotGridWidget;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineDraftField;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineSearchList;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineTabs;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.model.BoundedViewportModel;
import com.juicyslew.moonstation14.ms14.ui.model.DraftFieldModel;
import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import com.juicyslew.moonstation14.ms14.ui.model.OptionModel;
import com.juicyslew.moonstation14.ms14.ui.model.QueueIntentModel;
import com.juicyslew.moonstation14.ms14.ui.model.SlotGridModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Manual widget showcase, not a machine screen. Every snapshot and acknowledgement is local demo data. */
public final class MachineUiGalleryScreen extends Screen {
    private static final Component TITLE = Component.literal("MACHINE UI GALLERY - LOCAL DEMO");
    private static final List<OptionModel.Option> PAGES = List.of(
            new OptionModel.Option("search", "Search + field"),
            new OptionModel.Option("collections", "Slots + queue"),
            new OptionModel.Option("viewport", "Sample points"));
    private static final List<FilteredListModel.Entry> EXAMPLES = List.of(
            new FilteredListModel.Entry("oxygen", "Oxygen"),
            new FilteredListModel.Entry("nitrogen", "Nitrogen"),
            new FilteredListModel.Entry("carbon", "Carbon dioxide"),
            new FilteredListModel.Entry("plasma", "Plasma"),
            new FilteredListModel.Entry("water", "Water"),
            new FilteredListModel.Entry("iron", "Iron"),
            new FilteredListModel.Entry("glass", "Glass"),
            new FilteredListModel.Entry("silicon", "Silicon"),
            new FilteredListModel.Entry("gold", "Gold"),
            new FilteredListModel.Entry("silver", "Silver"));
    private static final List<SlotGridModel.Slot> SLOTS = List.of(
            new SlotGridModel.Slot("one", "Fe", 4), new SlotGridModel.Slot("two", "Si", 2),
            new SlotGridModel.Slot("three", "Au", 1), new SlotGridModel.Slot("four", "O2", 3),
            new SlotGridModel.Slot("five", "H2O", 1), new SlotGridModel.Slot("six", "C", 8),
            new SlotGridModel.Slot("seven", "Ag", 2), new SlotGridModel.Slot("eight", "N2", 1));
    private static final List<QueueIntentModel.Entry> QUEUE = List.of(
            new QueueIntentModel.Entry("q1", "Cut iron"), new QueueIntentModel.Entry("q2", "Polish glass"),
            new QueueIntentModel.Entry("q3", "Weld frame"), new QueueIntentModel.Entry("q4", "Inspect seal"));
    private static final List<QueueIntentModel.Entry> PRESETS = List.of(
            new QueueIntentModel.Entry("p1", "Frame"), new QueueIntentModel.Entry("p2", "Window"));
    private static final List<BoundedViewportModel.Point> POINTS = List.of(
            new BoundedViewportModel.Point("a", 12, 22), new BoundedViewportModel.Point("b", 28, 44),
            new BoundedViewportModel.Point("c", 45, 67), new BoundedViewportModel.Point("d", 63, 31),
            new BoundedViewportModel.Point("e", 78, 75), new BoundedViewportModel.Point("f", 90, 54));

    private final OptionModel pages = new OptionModel(3);
    private final DraftFieldModel draftModel = DraftFieldModel.number(6, BigDecimal.ZERO, new BigDecimal("100"));
    private final SlotGridModel slotModel = new SlotGridModel(16);
    private final QueueIntentModel queueModel = new QueueIntentModel(16);
    private final BoundedViewportModel viewportModel = new BoundedViewportModel(16, 100, 100);
    private final List<AbstractWidget> searchWidgets = new ArrayList<>();
    private final List<AbstractWidget> collectionWidgets = new ArrayList<>();
    private final List<AbstractWidget> viewportWidgets = new ArrayList<>();
    private MachineDraftField draft;
    private MachineSearchList searchList;
    private Button accept;
    private Button reject;
    private DraftFieldModel.Intent pending;
    private String status = "LOCAL DEMO - no machine connected";
    private Component footerStatus = Component.empty();
    private int x, y, windowWidth, windowHeight;

    public MachineUiGalleryScreen() {
        super(TITLE);
        pages.snapshot(0, PAGES);
        draftModel.snapshot(0, "50");
        slotModel.snapshot(0, SLOTS);
        queueModel.snapshot(0, QUEUE, PRESETS);
        viewportModel.snapshot(0, POINTS);
    }

    @Override protected void init() {
        if (searchList != null) searchQuery = searchList.searchBox().getValue();
        super.init();
        searchWidgets.clear();
        collectionWidgets.clear();
        viewportWidgets.clear();
        windowWidth = Math.min(460, Math.max(12, width - 12));
        windowHeight = Math.min(330, Math.max(12, height - 12));
        refreshFooterStatus();
        x = (width - windowWidth) / 2;
        y = (height - windowHeight) / 2;
        int contentX = x + 12;
        int contentWidth = Math.max(12, windowWidth - 24);
        int bodyY = y + 83;
        int bodyHeight = Math.max(28, windowHeight - 119);

        addRenderableWidget(WindowChromeButtons.close(x, y, windowWidth, this::onClose));
        addRenderableWidget(WindowChromeButtons.help(x, y, windowWidth,
                () -> setStatus("LOCAL DEMO: tabs, search, drafts, queue, slots, pan/zoom")));
        addRenderableWidget(new MachineTabs(contentX, y + 32, contentWidth, 22,
                Component.literal("Gallery pages"), pages, ignored -> showPage()));

        int rows = Math.clamp((bodyHeight - 68) / MachineSearchList.ROW_HEIGHT, 1, 8);
        // Preserve typed query across Minecraft's resize/init cycle.
        searchList = addPageWidget(searchWidgets, new MachineSearchList(contentX, bodyY + 22,
                contentWidth, rows, Component.literal("Examples"), newSearchModel(rows),
                id -> setStatus("LOCAL DEMO selected: " + id)));
        searchList.snapshot(0, EXAMPLES);
        addPageWidget(searchWidgets, searchList.searchBox());
        searchList.searchBox().setValue(searchQuery);
        int fieldY = bodyY + 22 + MachineSearchList.viewportHeight(rows) + 8;
        int fieldWidth = Math.max(12, contentWidth - 110);
        draft = addPageWidget(searchWidgets, new MachineDraftField(contentX, fieldY, fieldWidth, 76, 6,
                Component.literal("DEMO send"), draftModel, intent -> {
                    pending = intent;
                    setStatus("LOCAL DEMO pending " + intent.value() + " - choose confirm/reject");
                    updateAckButtons();
                }));
        addPageWidget(searchWidgets, draft.editBox());
        int ackY = fieldY + 23;
        accept = addPageWidget(searchWidgets, Button.builder(Component.literal("DEMO confirm"), button -> resolve(true))
                .bounds(contentX, ackY, Math.min(95, contentWidth / 2), 20).build());
        reject = addPageWidget(searchWidgets, Button.builder(Component.literal("DEMO reject"), button -> resolve(false))
                .bounds(contentX + Math.min(95, contentWidth / 2) + 4, ackY, Math.min(95, contentWidth / 2 - 4), 20).build());
        updateAckButtons();

        int leftWidth = Math.max(28, contentWidth / 2 - 5);
        SlotGridWidget slots = addPageWidget(collectionWidgets,
                new SlotGridWidget(contentX, bodyY + 20, leftWidth, bodyHeight - 20, slotModel));
        // Deliberately do not install a drag owner or enable dragging: no inventory transfer.
        QueueWidget queue = addPageWidget(collectionWidgets,
                new QueueWidget(contentX + leftWidth + 10, bodyY + 20,
                        contentWidth - leftWidth - 10, bodyHeight - 20, queueModel));
        queue.setIntentOwner(intent -> setStatus("NOT SERVER - DEMO intent " + intent.action() + " " + intent.id()));

        addPageWidget(viewportWidgets, new SampleViewportWidget(contentX, bodyY + 20,
                contentWidth, bodyHeight - 20, viewportModel, 100, 100));
        showPage();
        updateAckButtons();
    }

    private FilteredListModel newSearchModel(int rows) {
        FilteredListModel model = new FilteredListModel(16, rows);
        // Each resized/reopened list receives its snapshot through the widget, so its labels are cached too.
        return model;
    }

    private String searchQuery = "";

    private void setStatus(String value) {
        status = value;
        refreshFooterStatus();
    }

    private void refreshFooterStatus() {
        footerStatus = Component.literal(font.plainSubstrByWidth(status, windowWidth - 28));
    }

    private <T extends AbstractWidget> T addPageWidget(List<AbstractWidget> page, T widget) {
        page.add(widget);
        return addRenderableWidget(widget);
    }

    private void showPage() {
        String selected = pages.selectedId();
        setPageVisible(searchWidgets, "search".equals(selected));
        setPageVisible(collectionWidgets, "collections".equals(selected));
        setPageVisible(viewportWidgets, "viewport".equals(selected));
        if (draft != null) draft.active = "search".equals(selected) && draftModel.valid()
                && draftModel.pending() == null && !draftModel.draft().equals(draftModel.authority());
        if (accept != null) updateAckButtons();
        if (getFocused() instanceof AbstractWidget focused && !focused.visible) setFocused(null);
    }

    private void setPageVisible(List<AbstractWidget> widgets, boolean visible) {
        for (AbstractWidget widget : widgets) {
            widget.visible = visible;
            // Draft submission and acknowledgement depend on model state, not just the selected page.
            widget.active = visible && widget != draft && widget != accept && widget != reject;
        }
    }

    private void updateAckButtons() {
        accept.active = accept.visible && pending != null;
        reject.active = reject.visible && pending != null;
    }

    private void resolve(boolean accepted) {
        if (pending == null) return;
        String value = accepted ? pending.value() : draftModel.authority();
        draft.resolve(pending.requestId(), draftModel.revision() + 1, value, accepted);
        setStatus(accepted ? "LOCAL DEMO confirmed " + value : "LOCAL DEMO rejected; restored " + value);
        pending = null;
        updateAckButtons();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xbb000000);
        MachineWindowRenderer.window(graphics, font, x, y, windowWidth, windowHeight, TITLE);
        String page = pages.selectedId();
        String hint = switch (page) {
            case "search" -> "LOCAL: search/scroll; enter a number 0-100";
            case "collections" -> "LOCAL: slots read-only; queue intents NOT SERVER";
            default -> "LOCAL: drag to pan; wheel or +/- to zoom";
        };
        graphics.drawString(font, hint, x + 12, y + 64, MachineWindowRenderer.TEXT, true);
        if ("collections".equals(page)) {
            graphics.drawString(font, "DEMO SLOTS", x + 12, y + 83, MachineWindowRenderer.TEXT, true);
            graphics.drawString(font, "DEMO QUEUE", x + 17 + Math.max(28, (windowWidth - 24) / 2 - 5), y + 83,
                    MachineWindowRenderer.TEXT, true);
        } else if ("viewport".equals(page)) {
            graphics.drawString(font, "DEMO SAMPLE POINTS (not telemetry)", x + 12, y + 83,
                    MachineWindowRenderer.TEXT, true);
        }
        MachineWindowRenderer.footer(graphics, font, x + 8, y + windowHeight - 27,
                windowWidth - 16, 20, footerStatus);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
