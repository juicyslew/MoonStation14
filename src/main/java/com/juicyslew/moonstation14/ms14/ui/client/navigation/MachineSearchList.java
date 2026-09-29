package com.juicyslew.moonstation14.ms14.ui.client.navigation;

import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Bounded viewport plus independent vanilla search field. Register both this and searchBox() with the screen. */
public final class MachineSearchList extends AbstractButton {
    public static final int MAX_VISIBLE_ROWS = 24;
    public static final int ROW_HEIGHT = 18;
    private final FilteredListModel model;
    private final EditBox search;
    private final Consumer<String> onActivate;
    private final LabelCache labels;
    private final Map<String, Tooltip> tooltips = new HashMap<>();
    private String tooltipId;

    /** pageSize must equal the model's page size. Height applies to rows only; place searchBox() above. */
    public MachineSearchList(int x, int y, int width, int pageSize, Component title,
                             FilteredListModel model, Consumer<String> onActivate) {
        super(x, y, width, viewportHeight(pageSize), title);
        if (width < 12) throw new IllegalArgumentException("invalid list size");
        this.model = java.util.Objects.requireNonNull(model);
        if (model.pageSize() != pageSize) throw new IllegalArgumentException("model page size differs from list page size");
        labels = new LabelCache(model);
        this.onActivate = onActivate;
        search = new EditBox(Minecraft.getInstance().font, x, y - 22, width, 18, Component.literal("Search"));
        search.setMaxLength(128);
        search.setResponder(value -> {
            model.filter(value);
            updateNarrationLabel();
        });
    }

    /** Validated row-only viewport extent, independent of the Minecraft client. */
    public static int viewportHeight(int pageSize) {
        if (pageSize < 1 || pageSize > MAX_VISIBLE_ROWS) throw new IllegalArgumentException("invalid page size");
        return pageSize * ROW_HEIGHT;
    }

    private int renderedCount() { return Math.min(model.visibleCount(), getHeight() / ROW_HEIGHT); }

    public EditBox searchBox() { return search; }
    public String selectedId() { return model.selectedId(); }
    public int visibleCount() { return model.visibleCount(); }

    /** Only authoritative snapshot changes rebuild cached labels; filtering is handled by the model. */
    public void snapshot(long revision, List<FilteredListModel.Entry> entries) {
        if (!labels.snapshot(revision, entries)) return;
        tooltips.clear();
        for (FilteredListModel.Entry entry : entries) {
            tooltips.put(entry.id(), Tooltip.create(labels.get(entry.id())));
        }
        tooltipId = null;
        setTooltip(null);
        updateNarrationLabel();
    }

    /** Pure snapshot-owned label cache: filtering and scrolling never rebuild display components. */
    static final class LabelCache {
        private final FilteredListModel model;
        private final Map<String, Component> labels = new HashMap<>();

        LabelCache(FilteredListModel model) { this.model = model; }

        boolean snapshot(long revision, List<FilteredListModel.Entry> entries) {
            long old = model.revision();
            model.snapshot(revision, entries);
            if (old == model.revision()) return false;
            labels.clear();
            for (FilteredListModel.Entry entry : entries)
                labels.put(entry.id(), Component.literal(entry.label()));
            return true;
        }

        Component get(String id) { return labels.get(id); }
    }

    private void updateNarrationLabel() {
        Component selected = labels.get(model.selectedId());
        setMessage(selected == null ? search.getMessage() : selected);
    }

    @Override public void onPress() {
        if (model.selectedId() != null) onActivate.accept(model.selectedId());
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int row = NavigationHit.row((int) mouseX, (int) mouseY, getX(), getY(), getWidth(), ROW_HEIGHT,
                    renderedCount());
            if (row >= 0) {
                model.select(model.visibleAt(row).id());
                updateNarrationLabel();
            }
            else return false; // blank rows cannot activate an earlier selection
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
        model.scroll(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isFocused() && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            model.move(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
            updateNarrationLabel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), width = getWidth();
        if (width <= 0) return;
        var font = Minecraft.getInstance().font;
        graphics.fill(x, y, x + width, y + getHeight(), 0xff11191d);
        int count = renderedCount();
        int hovered = NavigationHit.row(mouseX, mouseY, x, y, width, ROW_HEIGHT, count);
        String hoveredId = null;
        graphics.enableScissor(x, y, x + width, y + getHeight());
        try {
            for (int row = 0; row < count; row++) {
                var entry = model.visibleAt(row);
                int top = y + row * ROW_HEIGHT;
                boolean selected = entry.id().equals(model.selectedId());
                graphics.fill(x + 1, top + 1, x + width - 1, top + ROW_HEIGHT - 1,
                        selected ? 0xff496b75 : row == hovered ? 0xff35474e : 0xff202e35);
                Component label = labels.get(entry.id());
                if (label != null) {
                    graphics.drawString(font, label, x + 5, top + (ROW_HEIGHT - font.lineHeight) / 2,
                             selected ? 0xffe5ecee : 0xffb0bec2, true);
                    if (row == hovered && font.width(label) > width - 10) hoveredId = entry.id();
                }
            }
        } finally { graphics.disableScissor(); }
        if (!java.util.Objects.equals(hoveredId, tooltipId)) {
            tooltipId = hoveredId;
            setTooltip(hoveredId == null ? null : tooltips.get(hoveredId));
        }
    }
}
