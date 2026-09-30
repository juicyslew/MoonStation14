package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import net.minecraft.network.chat.Component;

/** Snapshot-only presentation. No carrier menu or stack slots are exposed. */
public final class BodyInventoryScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int CONTENT_HEIGHT = 226;
    private net.minecraft.client.gui.components.Button first, second, insert, extract;
    private final net.minecraft.client.gui.components.Button[][] equipment = new net.minecraft.client.gui.components.Button[2][4];
    private String firstId, secondId;
    private int scroll;

    public BodyInventoryScreen() {
        super(Component.translatable("gui.moonstation14.inventory.title"));
    }

    @Override protected void init() {
        int w = Math.max(80, Math.min(260, width - 12));
        int x = (width - w) / 2;
        first = addRenderableWidget(button("gui.moonstation14.inventory.select_first",
                b -> BodyHandClient.selectInventoryHand(firstId), x, 20, w));
        second = addRenderableWidget(button("gui.moonstation14.inventory.select_second",
                b -> BodyHandClient.selectInventoryHand(secondId), x, 44, w));
        insert = addRenderableWidget(button("gui.moonstation14.inventory.insert",
                b -> BodyHandClient.inventoryPouch(BodyHandActionRequest.Action.INSERT_POUCH), x, 78, w / 2 - 2));
        extract = addRenderableWidget(button("gui.moonstation14.inventory.extract",
                b -> BodyHandClient.inventoryPouch(BodyHandActionRequest.Action.EXTRACT_POUCH), x + w / 2 + 2, 78, w - w / 2 - 2));
        for (int row = 0; row < 2; row++) {
            boolean belt = row == 0;
            BodyHandActionRequest.Action[] actions = belt
                    ? new BodyHandActionRequest.Action[] { BodyHandActionRequest.Action.EQUIP_BELT,
                            BodyHandActionRequest.Action.UNEQUIP_BELT, BodyHandActionRequest.Action.STORE_BELT,
                            BodyHandActionRequest.Action.TAKE_BELT }
                    : new BodyHandActionRequest.Action[] { BodyHandActionRequest.Action.EQUIP_BACK,
                            BodyHandActionRequest.Action.UNEQUIP_BACK, BodyHandActionRequest.Action.STORE_BACK,
                            BodyHandActionRequest.Action.TAKE_BACK };
            String[] labels = { "equip", "unequip", "store", "take" };
            for (int i = 0; i < 4; i++) {
                var action = actions[i];
                int left = x + i * w / 4;
                int right = x + (i + 1) * w / 4;
                equipment[row][i] = addRenderableWidget(button("gui.moonstation14.inventory." + labels[i],
                        b -> BodyHandClient.inventoryEquipment(action), left, 137 + row * 55, right - left - 2));
            }
        }
        updateButtons();
    }

    private net.minecraft.client.gui.components.Button button(String label,
            net.minecraft.client.gui.components.Button.OnPress press, int x, int y, int w) {
        return net.minecraft.client.gui.components.Button.builder(Component.translatable(label), press).bounds(x, y, w, 20).build();
    }

    private int viewportBottom() { return Math.max(18, height - 4); }
    private int maxScroll() { return Math.max(0, CONTENT_HEIGHT - viewportBottom()); }

    private void place(net.minecraft.client.gui.components.Button button, int y) {
        button.setY(y - scroll);
        button.visible = button.getY() >= 18 && button.getY() + button.getHeight() <= viewportBottom();
    }

    private void label(net.minecraft.client.gui.components.Button button, String text) {
        button.setMessage(Component.literal(font.plainSubstrByWidth(text, Math.max(0, button.getWidth() - 8))));
    }

    private BodyHandClientPolicy.InventoryView updateButtons() {
        var view = BodyHandClient.inventoryView();
        firstId = view == null ? null : view.first().id();
        secondId = view == null ? null : view.second().id();
        label(first, view == null ? "—" : handLabel(view.first(), view.activeHand()));
        label(second, view == null ? "—" : handLabel(view.second(), view.activeHand()));
        place(first, 20);
        place(second, 44);
        first.active = view != null && !firstId.equals(view.activeHand());
        second.active = view != null && !secondId.equals(view.activeHand());
        place(insert, 78);
        place(extract, 78);
        label(insert, text("insert"));
        label(extract, text("extract"));
        insert.active = view != null && view.pouch() != null && view.pouch().canInsert();
        extract.active = view != null && view.pouch() != null && view.pouch().canExtract();
        for (int row = 0; row < 2; row++) {
            var slot = view == null ? null : row == 0 ? view.belt() : view.back();
            for (int i = 0; i < 4; i++) {
                net.minecraft.client.gui.components.Button button = equipment[row][i];
                place(button, 137 + row * 55);
                label(button, text(new String[] { "equip", "unequip", "store", "take" }[i]));
                button.active = slot != null && switch (i) {
                    case 0 -> slot.canEquip();
                    case 1 -> slot.canUnequip();
                    case 2 -> slot.canStore();
                    default -> slot.canTake();
                };
            }
        }
        return view;
    }

    @Override public void tick() { updateButtons(); }

    @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var view = updateButtons();
        graphics.enableScissor(0, 18, width, viewportBottom());
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2;
        int maxWidth = Math.max(0, width - 16);
        if (view == null) {
            line(graphics, Component.translatable("gui.moonstation14.inventory.waiting").getString(), x, 66, maxWidth);
        } else {
            var pouch = view.pouch();
            String contents = pouch == null ? text("no_pouch")
                    : pouch.pouch().childToken() == null ? text("empty")
                    : pouch.pouch().childItemId() + " x" + pouch.pouch().childCount();
            line(graphics, text("pouch") + ": " + contents, x, 102, maxWidth);
            equipmentLine(graphics, x, 119, maxWidth, "belt", view.belt());
            equipmentLine(graphics, x, 174, maxWidth, "back", view.back());
        }
        graphics.disableScissor();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(title.getString(), maxWidth), x, 4, 0xffffff);
    }

    private void equipmentLine(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int maxWidth, String name,
                               BodyHandClientPolicy.EquipmentView view) {
        String label = text(name);
        String item = view == null ? text("no_pouch") : view.slot().token() == null ? text("empty")
                : view.slot().itemId() + " x" + view.slot().count();
        String child = view == null || view.slot().token() == null ? "" : text("cell") + ": "
                + (view.slot().childToken() == null ? text("empty")
                : view.slot().childItemId() + " x" + view.slot().childCount());
        line(graphics, label + ": " + item, x, y, maxWidth);
        if (!child.isEmpty()) line(graphics, child, x, y + 11, maxWidth);
    }

    private String text(String key) { return Component.translatable("gui.moonstation14.inventory." + key).getString(); }

    private String handLabel(BodyHandStateSnapshot.Hand hand, String active) {
        return hand.id() + (hand.id().equals(active) ? " *: " : ": ")
                + (hand.token() == null ? text("empty") : hand.itemId() + " x" + hand.count());
    }

    private void line(net.minecraft.client.gui.GuiGraphics graphics, String text, int x, int y, int maxWidth) {
        int screenY = y - scroll;
        if (screenY >= 18 && screenY + font.lineHeight <= viewportBottom())
            graphics.drawCenteredString(font, font.plainSubstrByWidth(text, maxWidth), x, screenY, 0xffffff);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(deltaY) * 20));
        updateButtons();
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (BodyHandClient.inventoryKey(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
