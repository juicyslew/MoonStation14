package com.juicyslew.moonstation14.ms14.ui.client.navigation;

import com.juicyslew.moonstation14.ms14.ui.model.DraftFieldModel;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Vanilla EditBox and submit button; register both this and editBox() with the screen. */
public final class MachineDraftField extends AbstractButton {
    private final DraftFieldModel model;
    private final EditBox edit;
    private final Consumer<DraftFieldModel.Intent> onSubmit;
    private boolean syncing;

    /** maxLength must match the model's bound. Button sits right of the EditBox; callback owns transport. */
    public MachineDraftField(int x, int y, int fieldWidth, int buttonWidth, int maxLength,
                             Component label, DraftFieldModel model, Consumer<DraftFieldModel.Intent> onSubmit) {
        super(x + fieldWidth + 4, y, buttonWidth, 20, label);
        if (fieldWidth < 12 || buttonWidth < 12 || maxLength < 1 || maxLength > 1024)
            throw new IllegalArgumentException("invalid field size");
        this.model = model;
        this.onSubmit = onSubmit;
        edit = new EditBox(Minecraft.getInstance().font, x, y, fieldWidth, 20, label);
        edit.setMaxLength(maxLength);
        edit.setResponder(value -> {
            if (!syncing) { model.edit(value); updateState(); }
        });
        updateState();
    }

    public EditBox editBox() { return edit; }
    public DraftFieldModel.State state() { return model.state(); }

    public void snapshot(long revision, String value) {
        model.snapshot(revision, value);
        updateState();
    }

    /** Only a matching explicit acknowledgement can clear pending. */
    public boolean resolve(long requestId, long revision, String value, boolean accepted) {
        boolean resolved = model.resolve(requestId, revision, value, accepted);
        if (resolved) updateState();
        return resolved;
    }

    public void discard() { model.discard(); updateState(); }

    private void updateState() {
        if (!edit.getValue().equals(model.draft())) {
            syncing = true;
            try { edit.setValue(model.draft()); }
            finally { syncing = false; }
        }
        edit.setEditable(model.pending() == null);
        active = NavigationHit.canSubmit(model.valid(), model.pending() != null, model.revision(),
                model.draft(), model.authority());
    }

    @Override public void onPress() {
        if (!active) return;
        DraftFieldModel.Intent intent = model.submit();
        if (intent != null) {
            updateState();
            onSubmit.accept(intent);
        }
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        graphics.fill(x, y, x + w, y + h, isHoveredOrFocused() ? 0xffd2e2e7 : 0xff11191d);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, active ? 0xff496b75 : 0xff35474e);
        graphics.enableScissor(x + 3, y + 1, x + w - 3, y + h - 1);
        try {
            graphics.drawString(Minecraft.getInstance().font, getMessage(), x + 4, y + (h - 8) / 2,
                     active ? 0xffe5ecee : 0xffb0bec2, true);
        } finally { graphics.disableScissor(); }
    }
}
