package com.juicyslew.moonstation14.ms14.ui.client.collection;

import com.juicyslew.moonstation14.ms14.ui.model.QueueIntentModel;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Bounded queue list and preset picker. No command changes the snapshot locally. */
public final class QueueWidget extends AbstractWidget {
    private static final int ROW = 20;
    private final QueueIntentModel model;
    private Consumer<QueueIntentModel.Intent> owner;
    private String selectedId;
    private String presetId;
    private String draggedId;
    private long dragRevision = -1;
    private int scroll;
    private long cachedRevision = -1;
    private Component[] queueLabels = new Component[0];
    private Component[] presetLabels = new Component[0];

    public QueueWidget(int x, int y, int width, int height, QueueIntentModel model) {
        super(x, y, width, height, Component.literal("Queue and presets"));
        this.model = Objects.requireNonNull(model);
    }

    /** Null disables all commands, including reordering (the default). */
    public void setIntentOwner(Consumer<QueueIntentModel.Intent> owner) {
        this.owner = owner;
        cancelDrag();
    }

    public String selectedId() { return selectedId; }
    public String selectedPresetId() { return presetId; }
    public String dragPreviewId() { return draggedId; }
    public void cancelDrag() { draggedId = null; dragRevision = -1; }

    private int rows() { return Math.max(0, (getHeight() - ROW) / ROW); }
    private int maxScroll() { return Math.max(0, model.queueSize() - rows()); }

    private void sync() {
        if (cachedRevision == model.revision()) return;
        cachedRevision = model.revision();
        queueLabels = new Component[model.queueSize()];
        presetLabels = new Component[model.presetSize()];
        boolean selectedFound = false;
        boolean presetFound = false;
        for (int i = 0; i < queueLabels.length; i++) {
            QueueIntentModel.Entry entry = model.queuedAt(i);
            queueLabels[i] = Component.literal(entry.label());
            if (entry.id().equals(selectedId)) selectedFound = true;
        }
        for (int i = 0; i < presetLabels.length; i++) {
            QueueIntentModel.Entry entry = model.presetAt(i);
            presetLabels[i] = Component.literal(entry.label());
            if (entry.id().equals(presetId)) presetFound = true;
        }
        if (!selectedFound) selectedId = null;
        if (!presetFound) presetId = model.presetSize() == 0 ? null : model.presetAt(0).id();
        scroll = Math.min(scroll, maxScroll());
        cancelDrag();
    }

    private int rowAt(double mx, double my) {
        if (!isMouseOver(mx, my) || my < getY() + ROW) return -1;
        int row = ((int) my - getY() - ROW) / ROW;
        int index = scroll + row;
        return row < rows() && index < model.queueSize() ? index : -1;
    }

    private int indexOf(String id, boolean preset) {
        int size = preset ? model.presetSize() : model.queueSize();
        for (int i = 0; i < size; i++) {
            String entryId = preset ? model.presetAt(i).id() : model.queuedAt(i).id();
            if (entryId.equals(id)) return i;
        }
        return -1;
    }

    private void emit(QueueIntentModel.Intent intent) { if (owner != null && intent != null) owner.accept(intent); }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sync();
        if (getWidth() <= 0 || getHeight() <= 0) return;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xff111c22);
        graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        try {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + ROW, 0xff354d56);
            int preset = indexOf(presetId, true);
            if (preset >= 0) graphics.drawString(Minecraft.getInstance().font, presetLabels[preset], getX() + 5, getY() + 6, 0xffe3eded, true);
            // Two small preset controls: left/right change selection, middle submits apply intent.
            graphics.drawString(Minecraft.getInstance().font, "<    >", getX() + getWidth() - 55, getY() + 6, 0xffe3eded, true);
            int hovered = rowAt(mouseX, mouseY);
            for (int i = scroll, end = Math.min(model.queueSize(), scroll + rows()); i < end; i++) {
                int y = getY() + ROW + (i - scroll) * ROW;
                String id = model.queuedAt(i).id();
                graphics.fill(getX() + 1, y, getX() + getWidth() - 1, y + ROW - 1,
                        id.equals(selectedId) ? 0xff486a74 : i == hovered ? 0xff38525c : 0xff22353d);
                if (id.equals(draggedId)) graphics.fill(getX() + 1, y + ROW - 2, getX() + getWidth() - 1, y + ROW, 0xffc3e4df);
                graphics.drawString(Minecraft.getInstance().font, queueLabels[i], getX() + 5, y + 6, 0xffe3eded, true);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void cyclePreset(int delta) {
        if (model.presetSize() == 0) return;
        int current = indexOf(presetId, true);
        int next = Math.floorMod(current + delta, model.presetSize());
        presetId = model.presetAt(next).id();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        sync();
        if (!active || !visible || button != 0 || !isMouseOver(mx, my)) return false;
        setFocused(true);
        if (my < getY() + ROW) {
            if (mx >= getX() + getWidth() - 55 && mx < getX() + getWidth() - 38) cyclePreset(-1);
            else if (mx >= getX() + getWidth() - 23) cyclePreset(1);
            else emit(model.applyPreset(presetId));
        } else {
            int row = rowAt(mx, my);
            if (row >= 0) {
                selectedId = model.queuedAt(row).id();
                if (owner != null) { draggedId = selectedId; dragRevision = model.revision(); }
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0 || draggedId == null) return false;
        sync();
        int row = rowAt(mx, my);
        if (owner != null && dragRevision == model.revision()) {
            if (row >= 0) emit(model.moveBefore(draggedId, model.queuedAt(row).id()));
            else if (isMouseOver(mx, my) && my >= getY() + ROW) emit(model.moveBefore(draggedId, null));
        }
        cancelDrag();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!isMouseOver(mx, my) || vertical == 0) return false;
        sync();
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(vertical)));
        cancelDrag();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused()) return false;
        sync();
        if (key == 256) { if (draggedId == null) return false; cancelDrag(); return true; }
        if (key == 263 || key == 262) { cyclePreset(key == 263 ? -1 : 1); return true; }
        if (key == 264 || key == 265) {
            if (model.queueSize() == 0) return false;
            int current = indexOf(selectedId, false);
            int next = Math.max(0, Math.min(model.queueSize() - 1, current < 0 ? 0 : current + (key == 264 ? 1 : -1)));
            selectedId = model.queuedAt(next).id();
            scroll = Math.max(0, Math.min(maxScroll(), Math.max(next - rows() + 1, Math.min(scroll, next))));
            return true;
        }
        if (key == 257 || key == 32) {
            if (draggedId != null) {
                if (dragRevision == model.revision()) emit(model.moveBefore(draggedId, selectedId));
                cancelDrag();
            } else emit(model.applyPreset(presetId));
            return true;
        }
        if (key == 261) { emit(model.remove(selectedId)); return true; }
        if (key == 69) { emit(model.enqueue(presetId)); return true; }
        if (key == 82 && owner != null && selectedId != null) {
            draggedId = selectedId;
            dragRevision = model.revision();
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
