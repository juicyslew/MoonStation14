package com.juicyslew.moonstation14.ms14.ui.client.navigation;

import com.juicyslew.moonstation14.ms14.ui.model.OptionModel;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A focusable tab/category strip. Call snapshot when options change; callback is local navigation only. */
public final class MachineTabs extends AbstractButton {
    private final OptionModel model;
    private final Consumer<String> onSelection;
    private Component[] labels = new Component[0];
    private Tooltip[] tooltips = new Tooltip[0];
    private int cursor;
    private int firstTab;
    private int tooltipIndex = -2;

    public MachineTabs(int x, int y, int width, int height, Component title,
                       OptionModel model, Consumer<String> onSelection) {
        super(x, y, width, height, title);
        if (width < 12 || height < 12) throw new IllegalArgumentException("tab strip too small");
        this.model = model;
        this.onSelection = onSelection;
        refresh();
    }

    /** Rebuilds cached labels only after the model's revision changes. */
    public void refresh() {
        labels = new Component[model.size()];
        tooltips = new Tooltip[model.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Component.literal(model.at(i).label());
            tooltips[i] = Tooltip.create(labels[i]);
            if (model.at(i).id().equals(model.selectedId())) cursor = i;
        }
        cursor = Math.min(cursor, Math.max(0, labels.length - 1));
        keepCursorVisible();
        active = labels.length > 0;
        if (active) setMessage(labels[cursor]);
        tooltipIndex = -2;
        setTooltip(null);
    }

    public void snapshot(long revision, java.util.List<OptionModel.Option> options) {
        long old = model.revision();
        model.snapshot(revision, options);
        if (model.revision() != old) refresh();
    }

    public String selectedId() { return model.selectedId(); }

    private int visibleTabs() { return Math.min(labels.length, Math.max(1, getWidth() / 68)); }

    private void keepCursorVisible() {
        int visible = visibleTabs();
        if (cursor < firstTab) firstTab = cursor;
        if (cursor >= firstTab + visible) firstTab = cursor - visible + 1;
        firstTab = Math.max(0, Math.min(firstTab, labels.length - visible));
    }

    private void selectCursor() {
        if (labels.length == 0) return;
        String id = model.at(cursor).id();
        if (!id.equals(model.selectedId()) && model.select(id)) onSelection.accept(id);
        setMessage(labels[cursor]);
    }

    @Override public void onPress() { selectCursor(); }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && active) {
            int index = NavigationHit.tab((int) mouseX, (int) mouseY, getX(), getY(), getWidth(), getHeight(), visibleTabs());
            if (index >= 0) cursor = firstTab + index;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isFocused() && active && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
            cursor = Math.max(0, Math.min(labels.length - 1, cursor + (keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1)));
            keepCursorVisible();
            setMessage(labels[cursor]);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers); // vanilla Enter/Space activation
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0 || labels.length == 0) return false;
        cursor = Math.max(0, Math.min(labels.length - 1, cursor + (scrollY > 0 ? -1 : 1)));
        keepCursorVisible();
        setMessage(labels[cursor]);
        return true;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int count = visibleTabs();
        if (count == 0 || getWidth() <= 0 || getHeight() <= 0) return;
        int hovered = NavigationHit.tab(mouseX, mouseY, getX(), getY(), getWidth(), getHeight(), count);
        int tooltip = -1;
        var font = Minecraft.getInstance().font;
        for (int slot = 0; slot < count; slot++) {
            int i = firstTab + slot;
            int left = getX() + (int) ((long) getWidth() * slot / count);
            int right = getX() + (int) ((long) getWidth() * (slot + 1) / count);
            if (right <= left) continue;
            boolean selected = model.at(i).id().equals(model.selectedId());
            graphics.fill(left, getY(), right, getY() + getHeight(), selected ? 0xff547682 : 0xff202e35);
            graphics.fill(left, getY() + getHeight() - 2, right, getY() + getHeight(),
                    selected ? 0xff9ce1d0 : 0xff11191d);
            if (slot == hovered || (isFocused() && i == cursor))
                graphics.fill(left, getY(), right, getY() + 1, 0xffd2e2e7);
            if (right - left > 4) {
                graphics.enableScissor(left + 2, getY() + 1, right - 2, getY() + getHeight() - 2);
                try {
                    graphics.drawString(font, labels[i], left + 5, getY() + (getHeight() - font.lineHeight) / 2,
                             selected ? 0xffe5ecee : 0xffb0bec2, true);
                } finally { graphics.disableScissor(); }
                if (slot == hovered && font.width(labels[i]) > right - left - 10) tooltip = i;
            }
        }
        if (tooltip != tooltipIndex) {
            tooltipIndex = tooltip;
            setTooltip(tooltip < 0 ? null : tooltips[tooltip]);
        }
    }
}
