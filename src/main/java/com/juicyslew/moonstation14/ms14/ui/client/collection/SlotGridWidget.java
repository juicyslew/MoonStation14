package com.juicyslew.moonstation14.ms14.ui.client.collection;

import com.juicyslew.moonstation14.ms14.ui.model.SlotGridModel;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Snapshot-only inventory grid. The optional icon painter never owns or moves an item. */
public final class SlotGridWidget extends AbstractWidget {
    @FunctionalInterface
    public interface IconPainter {
        void paint(GuiGraphics graphics, SlotGridModel.Slot slot, int x, int y, int size);
    }

    private static final int CELL = 28;
    private final SlotGridModel model;
    private IconPainter icons;
    private boolean dragEnabled;
    private String selectedId;
    private String draggedId;
    private long dragRevision = -1;
    private int scrollRow;
    private long cachedRevision = -1;
    private Component[] labels = new Component[0];

    public SlotGridWidget(int x, int y, int width, int height, SlotGridModel model) {
        super(x, y, width, height, Component.literal("Inventory slots"));
        this.model = Objects.requireNonNull(model);
    }

    /** Owner must also install SlotGridModel.dragOwner. Disabled by default. */
    public void setDragEnabled(boolean enabled) {
        dragEnabled = enabled;
        if (!enabled) cancelDrag();
    }

    public void setIconPainter(IconPainter painter) { icons = painter; }
    public String selectedId() { return selectedId; }
    public String dragPreviewId() { return draggedId; }
    public void cancelDrag() { draggedId = null; dragRevision = -1; }

    private int columns() { return Math.max(1, getWidth() / CELL); }
    private int visibleRows() { return Math.max(1, (getHeight() + CELL - 1) / CELL); }

    private void sync() {
        if (cachedRevision == model.revision()) return;
        cachedRevision = model.revision();
        labels = new Component[model.size()];
        boolean found = false;
        for (int i = 0; i < labels.length; i++) {
            SlotGridModel.Slot slot = model.at(i);
            labels[i] = Component.literal(slot.display() + (slot.count() > 1 ? " ×" + slot.count() : ""));
            if (slot.id().equals(selectedId)) found = true;
        }
        if (!found) selectedId = null;
        cancelDrag();
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    private int maxScroll() { return Math.max(0, (model.size() + columns() - 1) / columns() - visibleRows()); }

    private int indexAt(double mx, double my) {
        if (!isMouseOver(mx, my)) return -1;
        int col = ((int) mx - getX()) / CELL;
        if (col >= columns()) return -1;
        int index = (scrollRow + ((int) my - getY()) / CELL) * columns() + col;
        return index < model.size() ? index : -1;
    }

    private int selectedIndex() {
        for (int i = 0; i < model.size(); i++) if (model.at(i).id().equals(selectedId)) return i;
        return -1;
    }

    private void reveal(int index) {
        int row = index / columns();
        scrollRow = Math.max(0, Math.min(maxScroll(), Math.max(row - visibleRows() + 1, Math.min(scrollRow, row))));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sync();
        if (getWidth() <= 0 || getHeight() <= 0) return;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xff111c22);
        graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        try {
            int first = scrollRow * columns();
            int end = Math.min(model.size(), first + visibleRows() * columns());
            int hovered = indexAt(mouseX, mouseY);
            for (int i = first; i < end; i++) {
                SlotGridModel.Slot slot = model.at(i);
                int x = getX() + (i % columns()) * CELL;
                int y = getY() + (i / columns() - scrollRow) * CELL;
                boolean selected = slot.id().equals(selectedId);
                graphics.fill(x, y, x + CELL - 1, y + CELL - 1,
                        selected ? 0xff94b8be : i == hovered ? 0xff6d8990 : 0xff3f555c);
                graphics.fill(x + 1, y + 1, x + CELL - 2, y + CELL - 2,
                        slot.id().equals(draggedId) ? 0xff415b67 : 0xff1e3037);
                if (icons != null) icons.paint(graphics, slot, x + 3, y + 3, 20);
                else graphics.drawString(Minecraft.getInstance().font, labels[i], x + 3, y + 9, 0xffe1e9ea, true);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        sync();
        if (!active || !visible || button != 0) return false;
        int index = indexAt(mx, my);
        if (index < 0) return false;
        setFocused(true);
        selectedId = model.at(index).id();
        if (dragEnabled) { draggedId = selectedId; dragRevision = model.revision(); }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0 || draggedId == null) return false;
        sync();
        int index = indexAt(mx, my);
        if (dragEnabled && index >= 0 && dragRevision == model.revision())
            model.drag(draggedId, model.at(index).id());
        cancelDrag();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!isMouseOver(mx, my) || vertical == 0) return false;
        sync();
        scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(vertical)));
        cancelDrag();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused()) return false;
        sync();
        if (key == 256) { if (draggedId == null) return false; cancelDrag(); return true; }
        int current = selectedIndex();
        int delta = switch (key) { case 262 -> 1; case 263 -> -1; case 264 -> columns(); case 265 -> -columns(); default -> 0; };
        if (delta != 0 && model.size() > 0) {
            int next = Math.max(0, Math.min(model.size() - 1, current < 0 ? 0 : current + delta));
            selectedId = model.at(next).id();
            reveal(next);
            return true;
        }
        if ((key == 257 || key == 32) && current >= 0 && dragEnabled) {
            if (draggedId == null) { draggedId = selectedId; dragRevision = model.revision(); }
            else { if (dragRevision == model.revision()) model.drag(draggedId, selectedId); cancelDrag(); }
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
