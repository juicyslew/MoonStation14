package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

/** Snapshot-only hand and equipment view. Grid cells are nominations, not inventory slots. */
public final class BodyInventoryScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int CONTENT_HEIGHT = 390;
    private static final int[] TOP = { 90, 245 };
    private net.minecraft.client.gui.components.Button first, second;
    private final net.minecraft.client.gui.components.Button[][] equipment = new net.minecraft.client.gui.components.Button[2][4];
    private final net.minecraft.client.gui.components.Button[] rotate = new net.minecraft.client.gui.components.Button[2],
            gridStore = new net.minecraft.client.gui.components.Button[2], gridTake = new net.minecraft.client.gui.components.Button[2];
    private String firstId, secondId, selectionHost, selectionToken;
    private long selectionEpoch = -1;
    private int selectedRow = -1, cellX = -1, cellY = -1, rotation, scroll;
    private boolean placement;

    public BodyInventoryScreen() { super(Component.translatable("gui.moonstation14.inventory.title")); }

    @Override protected void init() {
        int w = Math.max(80, Math.min(260, width - 12)), x = (width - w) / 2;
        int handGap = 4, handWidth = Math.max(1, (w - handGap) / 2);
        first = addRenderableWidget(button("select_first", b -> BodyHandClient.selectInventoryHand(firstId), x, 20, handWidth));
        second = addRenderableWidget(button("select_second", b -> BodyHandClient.selectInventoryHand(secondId), x + handWidth + handGap, 20, w - handWidth - handGap));
        first.setHeight(42);
        second.setHeight(42);
        for (int row = 0; row < 2; row++) {
            final int index = row;
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
                int left = x + i * w / 4, right = x + (i + 1) * w / 4;
                equipment[row][i] = addRenderableWidget(button(labels[i], b -> BodyHandClient.inventoryEquipment(action),
                        left, TOP[row] + 18, right - left - 2));
            }
            rotate[row] = addRenderableWidget(button("rotate", b -> rotation = (rotation + 90) % 360,
                    x, TOP[row] + 117, w / 3 - 2));
            gridStore[row] = addRenderableWidget(button("grid_store", b -> sendGrid(index, true),
                    x + w / 3, TOP[row] + 117, w / 3 - 2));
            gridTake[row] = addRenderableWidget(button("grid_take", b -> sendGrid(index, false),
                    x + 2 * w / 3, TOP[row] + 117, w - 2 * w / 3));
        }
        updateButtons();
    }

    private net.minecraft.client.gui.components.Button button(String label, net.minecraft.client.gui.components.Button.OnPress press, int x, int y, int w) {
        return net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.moonstation14.inventory." + label), press).bounds(x, y, w, 20).build();
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

    private BodyHandClientPolicy.GridView grid(BodyHandClientPolicy.InventoryView view, int row) {
        return view == null ? null : row == 0 ? view.beltGrid() : view.backGrid();
    }

    private void clearSelection() {
        selectedRow = -1;
        selectionHost = selectionToken = null;
        cellX = cellY = -1;
        placement = false;
    }

    private BodyHandClientPolicy.InventoryView updateButtons() {
        var view = BodyHandClient.inventoryPresentation();
        var settled = BodyHandClient.inventoryView();
        firstId = view == null ? null : view.first().id();
        secondId = view == null ? null : view.second().id();
        var proof = settled == null ? null : BodyHandClient.inventoryProof();
        if (proof != null && proof.epoch() != selectionEpoch) {
            selectionEpoch = proof.epoch();
        }
        if (view != null && selectedRow >= 0 && (grid(view, selectedRow) == null
                || !grid(view, selectedRow).host().hostToken().equals(selectionHost)
                || placement && !selectionToken.equals((view.first().id().equals(view.activeHand()) ? view.first() : view.second()).token())
                || !placement && grid(view, selectedRow).host().entries().stream().noneMatch(e -> e.token().equals(selectionToken)))) {
            clearSelection();
        }
        label(first, view == null ? "—" : handLabel(view.first(), view.activeHand(), first.getWidth()));
        label(second, view == null ? "—" : handLabel(view.second(), view.activeHand(), second.getWidth()));
        place(first, 20); place(second, 20);
        first.active = settled != null && !firstId.equals(settled.activeHand());
        second.active = settled != null && !secondId.equals(settled.activeHand());
        for (int row = 0; row < 2; row++) {
            var slot = settled == null ? null : row == 0 ? settled.belt() : settled.back();
            for (int i = 0; i < 4; i++) {
                var button = equipment[row][i];
                place(button, TOP[row] + 18);
                button.active = slot != null && switch (i) {
                    case 0 -> slot.canEquip();
                    case 1 -> slot.canUnequip();
                    case 2 -> slot.canStore();
                    default -> slot.canTake();
                };
            }
            var g = grid(view, row);
            var active = view == null ? null : view.first().id().equals(view.activeHand()) ? view.first() : view.second();
            boolean selected = g != null && selectedRow == row && selectionHost.equals(g.host().hostToken());
            var settledGrid = grid(settled, row);
            var settledActive = settled == null ? null : settled.first().id().equals(settled.activeHand()) ? settled.first() : settled.second();
            boolean store = settledGrid != null && selected && placement && settledActive != null && selectionToken.equals(settledActive.token())
                    && settledGrid.proposed(settledActive.itemId(), settledActive.token(), rotation, cellX, cellY) != null;
            boolean take = settledGrid != null && selected && !placement && settledGrid.canTake() && settledGrid.entryAt(cellX, cellY) != null
                    && selectionToken.equals(settledGrid.entryAt(cellX, cellY).token());
            place(rotate[row], TOP[row] + 117);
            place(gridStore[row], TOP[row] + 117);
            place(gridTake[row], TOP[row] + 117);
            rotate[row].active = settledGrid != null && selected && placement && settledGrid.canStore();
            label(rotate[row], text("rotate") + " " + rotation + "°");
            gridStore[row].active = store;
            gridTake[row].active = take;
        }
        return view;
    }

    private void sendGrid(int row, boolean store) {
        var view = updateButtons();
        if (view == null || selectedRow != row || (store ? !gridStore[row].active : !gridTake[row].active)) return;
        BodyHandClient.inventoryGrid(row == 0, store, selectionToken, cellX, cellY, rotation);
    }

    @Override public void tick() { updateButtons(); }

    @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var view = updateButtons();
        graphics.enableScissor(0, 18, width, viewportBottom());
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2, maxWidth = Math.max(0, width - 16);
        if (view == null) {
            line(graphics, "…", 66);
        } else {
            renderHandIcon(graphics, view.first(), first);
            renderHandIcon(graphics, view.second(), second);
            for (int row = 0; row < 2; row++) renderGrid(graphics, view, row);
            if (BodyHandClient.inventoryView() == null) graphics.drawString(font, "…", width - 18, 6, 0x999999, false);
        }
        graphics.disableScissor();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(title.getString(), maxWidth), center, 4, 0xffffff);
    }

    private int cellSize() { return Math.max(6, Math.min(16, (width - 8) / 8)); }
    private int gridLeft(int columns) { return (width - columns * cellSize()) / 2; }

    private void renderGrid(net.minecraft.client.gui.GuiGraphics graphics, BodyHandClientPolicy.InventoryView view, int row) {
        var slot = row == 0 ? view.belt() : view.back();
        var g = grid(view, row);
        int top = TOP[row];
        String name = text(row == 0 ? "belt" : "back");
        String host = slot == null ? text("grid_unavailable") : slot.slot().token() == null ? text("empty")
                : slot.slot().itemId() + " x" + slot.slot().count();
        line(graphics, name + ": " + host, top);
        if (g == null) {
            line(graphics, slot != null && slot.slot().childToken() != null
                    ? text("cell") + ": " + slot.slot().childItemId() + " x" + slot.slot().childCount()
                    : text("grid_unavailable"), top + 51);
            return;
        }
        int size = cellSize(), left = gridLeft(g.host().width()), startY = top + 43 - scroll;
        java.util.List<GridGeometry.Cell> preview = java.util.List.of();
        if (selectedRow == row && placement) {
            var active = view.first().id().equals(view.activeHand()) ? view.first() : view.second();
            if (active.token() != null && g.proposed(active.itemId(), active.token(), rotation, cellX, cellY) != null) {
                var id = ResourceLocation.tryParse(active.itemId());
                if (id != null) preview = new GridGeometry.Placement<>(active.token(), ItemFootprints.forId(id),
                        new GridGeometry.Cell(cellX, cellY), GridGeometry.Rotation.values()[rotation / 90]).occupiedCells();
            }
        }
        for (int y = 0; y < g.host().height(); y++) for (int x = 0; x < g.host().width(); x++) {
            int px = left + x * size, py = startY + y * size;
            if (px < 0 || px + size > width || py < 18 || py + size > viewportBottom()) continue;
            var entry = g.entryAt(x, y);
            boolean highlighted = selectedRow == row && selectionToken != null && entry != null
                    && entry.token().equals(selectionToken) && !placement;
            boolean target = preview.contains(new GridGeometry.Cell(x, y));
            int color = highlighted ? 0xffe0bc52 : entry != null ? 0xff527c99 : target ? 0xff5b994e : 0xff343b42;
            graphics.fill(px, py, px + size - 1, py + size - 1, color);
            if (entry != null && entry.x() == x && entry.y() == y)
                graphics.drawString(font, font.plainSubstrByWidth(entry.itemId().substring(entry.itemId().indexOf(':') + 1), size - 3),
                        px + 2, py + 3, 0xffffffff, false);
        }
        String detail = selectedRow != row ? text("grid_hint") : placement
                ? text("grid_placement") + " (" + cellX + "," + cellY + ") " + rotation + "°"
                : g.entryAt(cellX, cellY) == null ? text("grid_hint")
                : g.entryAt(cellX, cellY).itemId() + " x" + g.entryAt(cellX, cellY).count();
        line(graphics, detail, top + 109);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            var view = updateButtons();
            if (view != null) for (int row = 0; row < 2; row++) {
                var g = grid(view, row);
                if (g == null) continue;
                int size = cellSize(), left = gridLeft(g.host().width()), top = TOP[row] + 43 - scroll;
                if (mouseX < left || mouseX >= left + g.host().width() * size || mouseY < Math.max(18, top)
                        || mouseY >= Math.min(viewportBottom(), top + g.host().height() * size)) continue;
                int x = ((int) mouseX - left) / size, y = ((int) mouseY - top) / size;
                var entry = g.entryAt(x, y);
                var active = view.first().id().equals(view.activeHand()) ? view.first() : view.second();
                clearSelection();
                if (entry != null) {
                    selectedRow = row; selectionHost = g.host().hostToken(); selectionToken = entry.token();
                    cellX = entry.x(); cellY = entry.y();
                } else if (active.token() != null && g.canStore()) {
                    selectedRow = row; selectionHost = g.host().hostToken(); selectionToken = active.token();
                    cellX = x; cellY = y; placement = true;
                }
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String text(String key) { return Component.translatable("gui.moonstation14.inventory." + key).getString(); }

    private String handLabel(BodyHandStateSnapshot.Hand hand, String active, int width) {
        String item = hand.token() == null ? text("empty") : hand.itemId().substring(hand.itemId().indexOf(':') + 1) + " x" + hand.count();
        return font.plainSubstrByWidth(hand.id() + (hand.id().equals(active) ? " * " : " ") + item,
                Math.max(0, width - 12));
    }

    private void renderHandIcon(net.minecraft.client.gui.GuiGraphics graphics, BodyHandStateSnapshot.Hand hand,
            net.minecraft.client.gui.components.Button button) {
        if (hand.token() == null || button.getY() < 18 || button.getY() + button.getHeight() > viewportBottom()) return;
        ResourceLocation id = ResourceLocation.tryParse(hand.itemId());
        if (id == null) return;
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return;
        // This stack exists only to render the snapshot's registered item icon; it is never used as inventory state.
        var displayOnly = new net.minecraft.world.item.ItemStack(item);
        graphics.renderItem(displayOnly, button.getX() + 5, button.getY() + 13);
        graphics.drawString(font, Integer.toString(hand.count()), button.getX() + 21, button.getY() + 30, 0xffffff, true);
    }

    private void line(net.minecraft.client.gui.GuiGraphics graphics, String text, int y) {
        int screenY = y - scroll;
        if (screenY >= 18 && screenY + font.lineHeight <= viewportBottom())
            graphics.drawCenteredString(font, font.plainSubstrByWidth(text, Math.max(0, width - 16)), width / 2, screenY, 0xffffff);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(deltaY) * 20));
        updateButtons();
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (BodyHandClient.inventoryKey(keyCode, scanCode)) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
