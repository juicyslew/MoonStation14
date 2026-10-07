package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridAction;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** Transparent pointer layer. Windows are snapshot presentations, never client inventories. */
public final class StoragePopupScreen extends net.minecraft.client.gui.screens.Screen implements com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow {
    private static final int CELL = StoragePopupLayout.CELL_SIZE;
    private final StoragePopupWorkspace workspace;
    private StoragePopupWorkspace.HostKey moving, dragging;
    private int grabX, grabY;
    private double dragStartX, dragStartY;
    private int cellOffsetX, cellOffsetY;
    private boolean itemMoved;
    private boolean fromHand;
    private PopupActionPlan.CrossSource crossSource;
    private PopupActionPlan.WorldCrossSource worldCrossSource;
    private HandEquipmentGesture.Drag equipmentDrag;
    private HandEquipmentGesture.Drag handGridDrag;
    private boolean handGridWasInactive;
    private String inactiveClickHand;
    private double inactiveClickX, inactiveClickY;
    private int handGridRotation;
    private HandBarLayout.Layout.Tile menuTile;
    private HandEquipmentGesture.Drag menuSource;
    private int menuX, menuY;
    private static final int MENU_WIDTH = 106, MENU_ROW = 19;
    private final PopupMovementIntent movement = new PopupMovementIntent();
    private final Set<Integer> actionKeys = new HashSet<>();

    public StoragePopupScreen(StoragePopupWorkspace workspace) {
        super(Component.translatable("gui.moonstation14.popup.pointer"));
        this.workspace = workspace;
    }

    static void drawWindows(net.minecraft.client.gui.GuiGraphics g, StoragePopupWorkspace workspace, int width, int height) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        workspace.resize(Math.max(1, width), Math.max(1, height));
        var view = BodyHandClient.inventoryPresentation();
        for (var window : workspace.windows().stream().filter(StoragePopupWorkspace.Window::visible)
                .sorted(Comparator.comparingInt(StoragePopupWorkspace.Window::z)).toList()) {
            var layout = layout(window);
            var bounds = new StoragePopupLayout.Rect(window.x(), window.y(), window.width(), window.height());
            var visiblePieces = workspace.uncovered(bounds, window);
            if (visiblePieces.isEmpty()) continue;
            // Draw the complete window once per uncovered piece. Nested grid scissors intersect
            // this outer clip, including item sprites whose depth could otherwise leak through.
            for (var piece : visiblePieces) {
                g.enableScissor(piece.x(), piece.y(), piece.x() + piece.width(), piece.y() + piece.height());
                drawWindowContents(g, mc, workspace, window, view, layout);
                g.disableScissor();
            }
        }
    }

    private static void drawWindowContents(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.Minecraft mc,
                                           StoragePopupWorkspace workspace, StoragePopupWorkspace.Window window,
                                           BodyHandClientPolicy.InventoryView view, StoragePopupLayout layout) {
            if (window.key().kind() == StoragePopupWorkspace.HostKind.WORLD) {
                drawWorldWindow(g, mc, window);
                return;
            }
            var grid = grid(view, window.key(), true);
            boolean trusted = grid != null && !window.provisional();
            window(g, layout, window.width(), window.height(), grid == null || window.provisional());
            if (grid == null) {
                // A refresh temporarily removes actionable inventory proof, not the user's
                // in-progress proposal. Keep rendering the captured child at its proposed
                // location; unavailable geometry is deliberately tinted as non-actionable.
                var drag = window.drag();
                var source = drag == null ? null : StorageDragPreviewPolicy.resolve(drag.sourceToken(), List.of(), List.of(),
                        view, BodyHandClient.inventoryView());
                if (drag != null && source != null) {
                    var id = ResourceLocation.tryParse(source.itemId());
                    if (id != null) {
                        var bounds = layout.gridBounds();
                        var visible = layout.visibleGrid(window.width(), window.height());
                        g.enableScissor(visible.x(), visible.y(), visible.x() + visible.width(), visible.y() + visible.height());
                        var footprint = source.footprint();
                        for (var cell : new GridGeometry.Placement<>(drag.sourceToken(), footprint,
                                drag.proposedAnchor(), drag.proposedRotation()).occupiedCells()) {
                            int px = bounds.x() + cell.x() * CELL, py = bounds.y() + cell.y() * CELL;
                            if (cell.x() >= 0 && cell.y() >= 0 && cell.x() < layout.columns() && cell.y() < layout.rows())
                                g.fill(px, py, px + CELL - 1, py + CELL - 1, 0x99da4545);
                        }
                        if (drag.proposedAnchor().x() >= 0 && drag.proposedAnchor().y() >= 0
                                && drag.proposedAnchor().x() < layout.columns() && drag.proposedAnchor().y() < layout.rows())
                            GridEntrySpriteRenderer.draw(g, source.itemId(), source.count(), footprint, drag.proposedRotation(),
                                    bounds.x() + drag.proposedAnchor().x() * CELL, bounds.y() + drag.proposedAnchor().y() * CELL,
                                    source.presentation());
                        g.disableScissor();
                    }
                }
                return;
            }
            var bounds = layout.gridBounds();
            int left = bounds.x(), top = bounds.y();
            int columns = grid.host().width(), rows = grid.host().height();
            var visible = layout.visibleGrid(window.width(), window.height());
            if (visible.width() == 0 || visible.height() == 0) return;
            g.enableScissor(visible.x(), visible.y(), visible.x() + visible.width(), visible.y() + visible.height());
            for (int cy = 0; cy < rows; cy++) for (int cx = 0; cx < columns; cx++) {
                int px = left + cx * CELL, py = top + cy * CELL;
                g.fill(px, py, px + CELL - 1, py + CELL - 1,
                        grid.entryAt(cx, cy) == null ? 0xff26363d : 0xff3d5962);
            }
            for (var entry : grid.host().entries()) {
                var id = ResourceLocation.tryParse(entry.itemId());
                if (id != null) GridEntrySpriteRenderer.draw(g, entry.itemId(), entry.count(),
                        ItemFootprints.clientForId(id), GridGeometry.Rotation.values()[entry.rotation() / 90],
                        left + entry.x() * CELL, top + entry.y() * CELL, entry.presentation());
            }
            if (grid != null) {
                var ordered = grid.host().entries();
                for (int i = Math.max(0, ordered.size() - 2); i < ordered.size(); i++) {
                    var entry = ordered.get(i);
                    // An optimistic presentation must never nominate a different rank or position.
                    if (grid.host().entries().contains(entry))
                        drawRank(g, left + entry.x() * CELL, top + entry.y() * CELL,
                                StoragePopupLayout.rank(ordered.size(), i));
                }
            }
            var drag = window.drag();
            String dragToken = drag == null ? null : drag.sourceToken();
            if (drag != null && dragToken != null) {
                var source = StorageDragPreviewPolicy.resolve(dragToken, grid.host().entries(),
                        BodyHandClient.popupGrid(BodyHandClient.inventoryView(), window.key(), false) == null ? List.of()
                                : BodyHandClient.popupGrid(BodyHandClient.inventoryView(), window.key(), false).host().entries(),
                        view, BodyHandClient.inventoryView());
                var entry = grid.host().entries().stream().filter(e -> e.token().equals(dragToken)).findFirst().orElse(null);
                if (source != null) {
                    String item = source.itemId();
                    var id = ResourceLocation.tryParse(item);
                    if (id != null) {
                        var footprint = source.footprint();
                        var preview = StoragePopupWorkspace.preview(grid.occupancy(), entry == null ? null : entry.token(),
                                footprint, drag.proposedAnchor(), drag.proposedRotation(), trusted && !window.provisional() && source.actionable());
                        var placement = new GridGeometry.Placement<>(dragToken, footprint,
                                drag.proposedAnchor(), drag.proposedRotation());
                        for (var cell : placement.occupiedCells()) {
                            if (cell.x() < 0 || cell.y() < 0 || cell.x() >= columns || cell.y() >= rows) continue;
                            int px = left + cell.x() * CELL, py = top + cell.y() * CELL;
                            g.fill(px, py, px + CELL - 1, py + CELL - 1,
                                    preview.maySendAction() ? 0x9958b867 : 0x99da4545);
                        }
                        if (drag.proposedAnchor().x() >= 0 && drag.proposedAnchor().y() >= 0
                                && drag.proposedAnchor().x() < columns && drag.proposedAnchor().y() < rows)
                            GridEntrySpriteRenderer.draw(g, item, source.count(),
                                    footprint, drag.proposedRotation(), left + drag.proposedAnchor().x() * CELL,
                                    top + drag.proposedAnchor().y() * CELL, source.presentation());
                    }
                }
            }
            if (!trusted)
                g.fill(visible.x(), visible.y(), visible.x() + visible.width(), visible.y() + visible.height(), 0x77090f12);
            g.disableScissor();
    }


    private static void drawWorldWindow(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.Minecraft mc,
                                        StoragePopupWorkspace.Window window) {
        var view = BodyHandClient.worldGrid(window.key().worldId());
        var layout = layout(window);
        window(g, layout, window.width(), window.height(), view == null || view.stale());
        if (view == null) return;
        var bounds = layout.gridBounds();
        int left = bounds.x(), top = bounds.y();
        var visible = layout.visibleGrid(window.width(), window.height());
        if (visible.width() == 0 || visible.height() == 0) return;
        g.enableScissor(visible.x(), visible.y(), visible.x() + visible.width(), visible.y() + visible.height());
        for (int cy = 0; cy < view.height(); cy++) for (int cx = 0; cx < view.width(); cx++) {
            int px = left + cx * CELL, py = top + cy * CELL;
            g.fill(px, py, px + CELL - 1, py + CELL - 1,
                    view.occupied(cx, cy) ? 0xff3d5962 : 0xff26363d);
        }
        for (var entry : view.entries()) {
            var id = ResourceLocation.tryParse(entry.itemId());
            if (id != null) GridEntrySpriteRenderer.draw(g, entry.itemId(), entry.count(),
                    ItemFootprints.clientForId(id), GridGeometry.Rotation.values()[entry.rotation() / 90],
                    left + entry.x() * CELL, top + entry.y() * CELL, entry.presentation());
        }
        var ordered = view.entries();
        for (int i = Math.max(0, ordered.size() - 2); i < ordered.size(); i++) {
            var entry = ordered.get(i);
            drawRank(g, left + entry.x() * CELL, top + entry.y() * CELL,
                    StoragePopupLayout.rank(ordered.size(), i));
        }
        var drag = window.drag();
        String dragToken = drag == null ? null : drag.sourceToken();
        if (drag != null && dragToken != null) {
            var handView = BodyHandClient.inventoryPresentation();
            var source = StorageDragPreviewPolicy.resolve(dragToken, List.of(), List.of(), handView,
                    BodyHandClient.inventoryView());
            var entry = view.entries().stream().filter(e -> e.token().equals(dragToken)).findFirst().orElse(null);
            if (entry != null) source = new StorageDragPreviewPolicy.Source(entry.itemId(), entry.count(),
                    ItemFootprints.clientForId(ResourceLocation.parse(entry.itemId())), "entry:" + entry.token(), BodyHandClient.worldActionable(view.target()), entry.presentation());
            String item = source == null ? null : source.itemId();
            var id = ResourceLocation.tryParse(item == null ? "" : item);
            if (id != null) {
                var footprint = source.footprint();
                boolean actionable = source.actionable() && (entry != null ? BodyHandClient.worldActionable(view.target())
                        : BodyHandClient.worldStoreEligible(view.target(), dragToken));
                var preview = StoragePopupWorkspace.preview(view.geometry(), entry == null ? null : entry.token(), footprint,
                        drag.proposedAnchor(), drag.proposedRotation(), actionable);
                for (var cell : new GridGeometry.Placement<>(dragToken, footprint,
                        drag.proposedAnchor(), drag.proposedRotation()).occupiedCells()) {
                    if (cell.x() < 0 || cell.y() < 0 || cell.x() >= view.width() || cell.y() >= view.height()) continue;
                    int px = left + cell.x() * CELL, py = top + cell.y() * CELL;
                    g.fill(px, py, px + CELL - 1, py + CELL - 1,
                            preview.maySendAction() ? 0x9958b867 : 0x99da4545);
                }
                if (drag.proposedAnchor().x() >= 0 && drag.proposedAnchor().y() >= 0
                        && drag.proposedAnchor().x() < view.width() && drag.proposedAnchor().y() < view.height())
                    GridEntrySpriteRenderer.draw(g, item, source.count(), footprint,
                            drag.proposedRotation(), left + drag.proposedAnchor().x() * CELL, top + drag.proposedAnchor().y() * CELL,
                            source.presentation());
            }
        }
        if (view.stale())
            g.fill(visible.x(), visible.y(), visible.x() + visible.width(), visible.y() + visible.height(), 0x77090f12);
        g.disableScissor();
    }

    // Same pixel palette as the Machine UI kit; kept local because this package is
    // intentionally not permitted to load client UI classes from server-owned source paths.
    private static void window(net.minecraft.client.gui.GuiGraphics g, StoragePopupLayout layout, int w, int h, boolean pending) {
        int x = layout.x(), y = layout.y();
        g.fill(x, y, x + w, y + h, 0xff11191d);
        if (w > 2 && h > 2) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, pending ? 0xff816347 : 0xff60747c);
        if (w > 4 && h > 4) g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xff35474e);
        var outline = layout.outlineBounds();
        g.enableScissor(x, y, x + w, y + h);
        inset(g, outline.x(), outline.y(), outline.width(), outline.height());
        var close = layout.closeBounds();
        g.pose().pushPose();
        g.pose().translate(close.x() + 1, close.y() + 1, 0);
        g.pose().scale(.75f, .75f, 1);
        g.drawString(net.minecraft.client.Minecraft.getInstance().font, "×", 0, 0, 0xffffffff, false);
        g.pose().popPose();
        var grip = layout.gripBounds();
        for (int row = 0; row < 4; row++) g.fill(grip.x(), grip.y() + 1 + row * 2,
                grip.x() + grip.width(), grip.y() + 2 + row * 2, 0xffaab9bd);
        g.disableScissor();
    }

    private static StoragePopupLayout layout(StoragePopupWorkspace.Window window) {
        int columns = window.key().kind() == StoragePopupWorkspace.HostKind.BELT ? 8 : 7;
        int rows = window.key().kind() == StoragePopupWorkspace.HostKind.BELT ? 2 : 4;
        if (window.key().kind() == StoragePopupWorkspace.HostKind.WORLD) {
            var view = BodyHandClient.worldGrid(window.key().worldId());
            if (view != null) { columns = view.width(); rows = view.height(); }
        } else {
            var grid = grid(BodyHandClient.inventoryPresentation(), window.key(), true);
            if (grid != null) { columns = grid.host().width(); rows = grid.host().height(); }
        }
        return new StoragePopupLayout(StoragePopupLayout.Kind.valueOf(window.key().kind().name()),
                window.x(), window.y(), columns, rows);
    }

    private static void drawRank(net.minecraft.client.gui.GuiGraphics g, int cellX, int cellY, int rank) {
        int color = rank == 2 ? 0xffffd35a : 0xffc4d1db;
        int x = cellX + 2, y = cellY + 2;
        g.fill(x + 1, y, x + 2, y + 4, color);
        g.fill(x, y + 1, x + 3, y + 2, color);
    }

    private static void inset(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xff11191d);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xff141f24);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xff090f12);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xff60747c);
    }

    private static void sprite(net.minecraft.client.gui.GuiGraphics g, String itemId, int count, int x, int y) {
        sprite(g, itemId, count, null, x, y);
    }

    private static void sprite(net.minecraft.client.gui.GuiGraphics g, String itemId, int count,
                               com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation presentation, int x, int y) {
        var id = ResourceLocation.tryParse(itemId);
        if (id == null) return;
        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return;
        ItemUiRenderer.render(g, itemId, count, presentation, x, y);
        if (count > 1) g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                Integer.toString(count), x + 12, y + 10, 0xffffffff, true);
    }

    private static BodyHandClientPolicy.GridView grid(BodyHandClientPolicy.InventoryView view, StoragePopupWorkspace.HostKey key, boolean presentation) {
        if (view == null || key.kind() == StoragePopupWorkspace.HostKind.WORLD) return null;
        var result = BodyHandClient.popupGrid(view, key, presentation);
        return result != null && result.host().hostToken().equals(key.token()) ? result : null;
    }

    @Override public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Deliberately no super.render/background: the world and hand bar stay visible.
        drawWindows(g, workspace, width, height);
        var wire = com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.active();
        if (wire != null) { wire.workspaceSize(width, height); wire.render(g, mouseX, mouseY, partialTick); }
        drawCrossPreview(g, mouseX, mouseY);
        var bar = HandBarLayout.calculate(width, height);
        var display = BodyHandClient.inventoryPresentation();
        if (bar != null && display != null) {
            var inactive = display.activeHand().equals(display.first().id()) ? bar.rightHand() : bar.leftHand();
            g.fill(inactive.x(), inactive.y(), inactive.x() + inactive.width(), inactive.y() + inactive.height(), 0x88090f12);
            if (equipmentDrag != null) {
                var target = bar.tileAt(mouseX, mouseY);
                boolean valid = HandEquipmentGesture.plan(equipmentDrag, target, BodyHandClient.inventoryView(),
                        BodyHandClient.inventoryProof()) != null;
                HandBarLayout.Rect rect = target == HandBarLayout.Layout.Tile.FIRST ? bar.leftHand()
                        : target == HandBarLayout.Layout.Tile.SECOND ? bar.rightHand()
                        : target == HandBarLayout.Layout.Tile.BACK ? bar.back()
                        : target == HandBarLayout.Layout.Tile.BELT ? bar.belt() : null;
                if (rect != null) g.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(),
                        valid ? 0x8858b867 : 0x88da4545);
                var held = BodyHandClient.inventoryPresentation();
                var heldItem = held == null ? null : equipmentDrag.handId().equals(held.first().id()) ? held.first() : held.second();
                sprite(g, equipmentDrag.itemId(), equipmentDrag.count(), heldItem == null ? null : heldItem.presentation(), mouseX - 8, mouseY - 8);
                g.drawString(font, Component.translatable(valid ? "gui.moonstation14.popup.release"
                        : "gui.moonstation14.popup.cancel"), mouseX + 12, mouseY + 8,
                        valid ? 0xff8ce39a : 0xffe99898, true);
            }
            if (handGridDrag != null) {
                var w = hit(mouseX, mouseY);
                if (w != null && visibleCell(w, mouseX, mouseY) != null) {
                    var view = BodyHandClient.inventoryPresentation();
                    var hand = view == null ? null : handGridDrag.handId().equals(view.first().id()) ? view.first() : view.second();
                    var world = w.key().kind() == StoragePopupWorkspace.HostKind.WORLD ? BodyHandClient.worldGrid(w.key().worldId()) : null;
                    var grid = grid(view, w.key(), true);
                    if (hand != null && (grid != null || world != null)) {
                        var anchor = dragCell(w, mouseX, mouseY);
                        var id = ResourceLocation.tryParse(handGridDrag.itemId());
                        boolean actionable = world != null
                                ? BodyHandClient.worldStoreEligible(world.target(), handGridDrag.token())
                                : BodyHandClient.inventoryView() != null && !w.provisional();
                        var preview = id == null ? null : StoragePopupWorkspace.preview(world != null ? world.geometry() : grid.occupancy(), null,
                                ItemFootprints.clientForId(id), anchor, GridGeometry.Rotation.values()[handGridRotation],
                                actionable);
                        var bounds = layout(w).gridBounds();
                        var left = bounds.x(); var top = bounds.y();
                        sprite(g, handGridDrag.itemId(), handGridDrag.count(), hand.presentation(), mouseX - 8, mouseY - 8);
                        var footprint = id == null ? null : ItemFootprints.clientForId(id);
                        var visible = layout(w).visibleGrid(w.width(), w.height());
                        for (var piece : workspace.uncovered(visible, w)) {
                            g.enableScissor(piece.x(), piece.y(), piece.x() + piece.width(), piece.y() + piece.height());
                            if (footprint != null) for (var cell : new GridGeometry.Placement<>(handGridDrag.token(), footprint,
                                    anchor, GridGeometry.Rotation.values()[handGridRotation]).occupiedCells()) {
                                int px = left + cell.x() * CELL, py = top + cell.y() * CELL;
                                if (cell.x() >= 0 && cell.y() >= 0 && cell.x() < (world != null ? world.width() : grid.host().width())
                                        && cell.y() < (world != null ? world.height() : grid.host().height()))
                                    g.fill(px, py, px + CELL - 1, py + CELL - 1, preview != null && preview.maySendAction() ? 0x9958b867 : 0x99da4545);
                            }
                            g.disableScissor();
                        }
                    }
                } else {
                    var held = BodyHandClient.inventoryPresentation();
                    var hand = held == null ? null : handGridDrag.handId().equals(held.first().id()) ? held.first() : held.second();
                    sprite(g, handGridDrag.itemId(), handGridDrag.count(), hand == null ? null : hand.presentation(), mouseX - 8, mouseY - 8);
                }
            }
        }
        if (menuTile != null) {
            boolean worn = menuTile == HandBarLayout.Layout.Tile.BACK || menuTile == HandBarLayout.Layout.Tile.BELT;
            inset(g, menuX, menuY, MENU_WIDTH, MENU_ROW * 2 + 4);
            g.drawString(font, Component.translatable("gui.moonstation14.popup.open"), menuX + 7, menuY + 7, 0xffe5ecee, false);
            g.drawString(font, Component.translatable(worn ? "gui.moonstation14.popup.remove" : "gui.moonstation14.popup.equip"), menuX + 7,
                    menuY + MENU_ROW + 7, BodyHandClient.inventoryView() == null ? 0xff60747c : 0xffe5ecee, false);
        }
    }

    private void drawCrossPreview(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY) {
        if (dragging == null || fromHand || !itemMoved) return;
        if (drawWorldCrossPreview(g, mouseX, mouseY)) return;
        if (crossSource == null) return;
        var target = PopupActionPlan.crossTarget(workspace, crossSource, mouseX, mouseY);
        if (target == null) return;
        var sourceWindow = workspace.window(dragging);
        if (sourceWindow == null || !sourceWindow.visible() || sourceWindow.provisional()) return;
        var proof = BodyHandClient.inventoryProof();
        var anchor = itemCell(target, mouseX, mouseY);
        var drag = sourceWindow.drag();
        if (drag == null) return;
        var id = ResourceLocation.tryParse(crossSource.itemId());
        if (id == null) return;
        var footprint = ItemFootprints.clientForId(id);
        var rotation = drag.proposedRotation();
        boolean permitted = PopupActionPlan.crossRequest(crossSource, target.key(), proof, anchor, rotation, 0, 1) != null;
        var layout = layout(target);
        var bounds = layout.gridBounds();
        var visible = layout.visibleGrid(target.width(), target.height());
        for (var piece : workspace.uncovered(visible, target)) {
            g.enableScissor(piece.x(), piece.y(), piece.x() + piece.width(), piece.y() + piece.height());
            for (var cell : new GridGeometry.Placement<>(crossSource.child(), footprint, anchor, rotation).occupiedCells()) {
                if (cell.x() < 0 || cell.y() < 0 || cell.x() >= layout.columns() || cell.y() >= layout.rows()) continue;
                int px = bounds.x() + cell.x() * CELL, py = bounds.y() + cell.y() * CELL;
                g.fill(px, py, px + CELL - 1, py + CELL - 1, permitted ? 0x9958b867 : 0x99da4545);
            }
            if (anchor.x() >= 0 && anchor.y() >= 0 && anchor.x() < layout.columns() && anchor.y() < layout.rows())
                GridEntrySpriteRenderer.draw(g, crossSource.itemId(), crossSource.count(), footprint, rotation,
                        bounds.x() + anchor.x() * CELL, bounds.y() + anchor.y() * CELL, crossSource.presentation());
            g.disableScissor();
        }
    }

    private PopupActionPlan.WorldCrossSource worldCrossSource(StoragePopupWorkspace.Window target) {
        if (worldCrossSource != null) return worldCrossSource;
        if (crossSource == null || target == null || target.key().kind() != StoragePopupWorkspace.HostKind.WORLD) return null;
        return PopupActionPlan.bodyWorldSource(BodyHandClient.inventoryProof(),
                BodyHandClient.worldGrid(target.key().worldId()), crossSource);
    }

    private boolean drawWorldCrossPreview(net.minecraft.client.gui.GuiGraphics g, int x, int y) {
        if (worldCrossSource != null && worldCrossSource.host().kind() == StoragePopupWorkspace.HostKind.WORLD) {
            var target = PopupActionPlan.worldWorldTarget(workspace, worldCrossSource, x, y);
            if (target == null) return false;
            var sourceWindow = workspace.window(dragging);
            var sourceView = BodyHandClient.worldGrid(worldCrossSource.worldTarget());
            var destination = BodyHandClient.worldGrid(target.key().worldId());
            var drag = sourceWindow == null ? null : sourceWindow.drag();
            if (drag == null) return true;
            var anchor = itemCell(target, x, y);
            var plan = PopupActionPlan.worldWorldPlan(worldCrossSource, sourceView, destination,
                    BodyHandClient.inventoryProof(), anchor, drag.proposedRotation(),
                    sourceWindow.visible() && !sourceWindow.provisional() && !target.provisional()
                            && BodyHandClient.worldActionable(worldCrossSource.worldTarget())
                            && BodyHandClient.worldActionable(target.key().worldId()), 0);
            var id = ResourceLocation.tryParse(worldCrossSource.itemId());
            if (id == null) return true;
            var bounds = layout(target).gridBounds();
            var visible = layout(target).visibleGrid(target.width(), target.height());
            for (var piece : workspace.uncovered(visible, target)) {
                g.enableScissor(piece.x(), piece.y(), piece.x() + piece.width(), piece.y() + piece.height());
                for (var cell : new GridGeometry.Placement<>(worldCrossSource.child(), ItemFootprints.clientForId(id),
                    anchor, drag.proposedRotation()).occupiedCells()) {
                if (cell.x() < 0 || cell.y() < 0 || cell.x() >= layout(target).columns()
                        || cell.y() >= layout(target).rows()) continue;
                int px = bounds.x() + cell.x() * CELL, py = bounds.y() + cell.y() * CELL;
                    g.fill(px, py, px + CELL - 1, py + CELL - 1, plan != null ? 0x9958b867 : 0x99da4545);
                }
                g.disableScissor();
            }
            return true;
        }
        var source = worldCrossSource != null ? worldCrossSource
                : crossSource == null ? null : worldCrossSource(hit(x, y));
        if (source == null) return false;
        var target = PopupActionPlan.worldCrossTarget(workspace, source, x, y);
        if (target == null) return false;
        var sourceWindow = workspace.window(dragging);
        if (sourceWindow == null || sourceWindow.drag() == null || !sourceWindow.visible()) return true;
        var world = BodyHandClient.worldGrid(source.worldTarget());
        var plan = PopupActionPlan.worldCrossPlan(source, target.key(), BodyHandClient.inventoryProof(), world,
                !sourceWindow.provisional() && BodyHandClient.worldActionable(source.worldTarget()), itemCell(target, x, y),
                sourceWindow.drag().proposedRotation(), 0);
        var item = ResourceLocation.tryParse(source.itemId());
        if (item == null) return true;
        var layout = layout(target);
        var bounds = layout.gridBounds();
        var visible = layout.visibleGrid(target.width(), target.height());
        var anchor = itemCell(target, x, y);
        for (var piece : workspace.uncovered(visible, target)) {
            g.enableScissor(piece.x(), piece.y(), piece.x() + piece.width(), piece.y() + piece.height());
            for (var cell : new GridGeometry.Placement<>(source.child(), ItemFootprints.clientForId(item), anchor,
                sourceWindow.drag().proposedRotation()).occupiedCells()) {
            if (cell.x() < 0 || cell.y() < 0 || cell.x() >= layout.columns() || cell.y() >= layout.rows()) continue;
            int px = bounds.x() + cell.x() * CELL, py = bounds.y() + cell.y() * CELL;
                g.fill(px, py, px + CELL - 1, py + CELL - 1, plan != null ? 0x9958b867 : 0x99da4545);
            }
            g.disableScissor();
        }
        return true;
    }

    private StoragePopupWorkspace.Window hit(double x, double y) {
        return workspace.topmost(x, y);
    }

    private StoragePopupLayout.Cell visibleCell(StoragePopupWorkspace.Window w, double x, double y) {
        return layout(w).visibleCellAt((int) x, (int) y, w.width(), w.height());
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var wire = com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.active();
        if (wire != null && wire.contains(mouseX, mouseY)) return wire.workspaceMouseClicked(mouseX, mouseY, button);
        if (button == 1 && rotateCurrent(mouseX, mouseY)) return true;
        if (button == 0 && moving == null && dragging == null && handGridDrag == null
                && equipmentDrag == null && hit(mouseX, mouseY) == null
                && (menuTile == null || mouseX < menuX || mouseX >= menuX + MENU_WIDTH
                || mouseY < menuY || mouseY >= menuY + MENU_ROW * 2 + 4)) {
            var bar = HandBarLayout.calculate(width, height);
            if (bar == null || bar.tileAt((int) mouseX, (int) mouseY) == null) {
                menuTile = null;
                menuSource = null;
                if (wire != null) {
                    BodyHandClient.gameplayWindowMouseUse(button);
                    return true;
                }
                BodyHandClient.dismissPopupPointerMode();
                return true;
            }
        }
        if (menuTile != null) {
            var tile = menuTile;
            var source = menuSource;
            menuTile = null;
            menuSource = null;
            boolean worn = tile == HandBarLayout.Layout.Tile.BACK || tile == HandBarLayout.Layout.Tile.BELT;
            if (button == 0 && mouseX >= menuX && mouseX < menuX + MENU_WIDTH && mouseY >= menuY
                    && mouseY < menuY + MENU_ROW * 2 + 4) {
                if (mouseY < menuY + MENU_ROW + 2) {
                    if (worn) BodyHandClient.togglePopup(tile == HandBarLayout.Layout.Tile.BACK
                            ? StoragePopupWorkspace.HostKind.BAG : StoragePopupWorkspace.HostKind.BELT);
                    else BodyHandClient.toggleHeldPopup(source.token());
                }
                else if (!worn) BodyHandClient.popupEquipment(source,
                        "moonstation14:bag".equals(source.itemId()) ? HandBarLayout.Layout.Tile.BACK
                                : HandBarLayout.Layout.Tile.BELT);
                else {
                    var view = BodyHandClient.inventoryView();
                    if (view != null) BodyHandClient.popupEquipment(source,
                            view.activeHand().equals(view.first().id()) ? HandBarLayout.Layout.Tile.FIRST
                                    : HandBarLayout.Layout.Tile.SECOND);
                }
                return true;
            }
        }
        var w = hit(mouseX, mouseY);
        if (w == null) {
            var bar = HandBarLayout.calculate(width, height);
            if (bar != null) {
                var tile = bar.tileAt((int) mouseX, (int) mouseY);
                var view = BodyHandClient.inventoryView();
                if (tile != null && view != null) {
                    if (button == 1) {
                        var proof = BodyHandClient.inventoryProof();
                        var candidate = HandEquipmentGesture.begin(tile, view, proof);
                        if (candidate == null && proof != null && (tile == HandBarLayout.Layout.Tile.FIRST
                                || tile == HandBarLayout.Layout.Tile.SECOND)) {
                            var hand = tile == HandBarLayout.Layout.Tile.FIRST ? view.first() : view.second();
                            if (hand.token() != null && hand.count() == 1 && ("moonstation14:bag".equals(hand.itemId())
                                    || "moonstation14:belt".equals(hand.itemId())))
                                candidate = new HandEquipmentGesture.Drag(tile, hand.id(), hand.token(), hand.itemId(),
                                        hand.count(), proof.accountId(), proof.bodyId(), proof.epoch(), proof.revision());
                        }
                        if (candidate != null) {
                            menuTile = tile;
                            menuSource = candidate;
                            menuX = Math.min((int) mouseX, width - MENU_WIDTH);
                            menuY = Math.min((int) mouseY, height - MENU_ROW * 2 - 4);
                        }
                    } else if (button == 0) {
                        if (tile == HandBarLayout.Layout.Tile.FIRST || tile == HandBarLayout.Layout.Tile.SECOND) {
                            var hand = tile == HandBarLayout.Layout.Tile.FIRST ? view.first() : view.second();
                            if (!hand.id().equals(view.activeHand())) {
                                var proof = BodyHandClient.inventoryProof();
                                handGridDrag = hand.token() != null ? HandEquipmentGesture.beginAny(tile, view, proof) : null;
                                if (handGridDrag != null) { dragStartX = mouseX; dragStartY = mouseY; handGridRotation = 0; handGridWasInactive = true; }
                                else { inactiveClickHand = hand.id(); inactiveClickX = mouseX; inactiveClickY = mouseY; }
                            }
                            else {
                                var proof = BodyHandClient.inventoryProof();
                                equipmentDrag = HandEquipmentGesture.begin(tile, view, proof);
                                if (equipmentDrag == null) handGridDrag = HandEquipmentGesture.beginAny(tile, view, proof);
                            }
                        } else equipmentDrag = HandEquipmentGesture.begin(tile, view, BodyHandClient.inventoryProof());
                    }
                }
            }
            // Never pass a pointer click through to world pickup or block interaction.
            return true;
        }
        if (button != 0) return true;
        workspace.focus(w.key());
        var popup = layout(w);
        if (popup.hitClose((int) mouseX, (int) mouseY)) {
                if (w.key().kind() == StoragePopupWorkspace.HostKind.WORLD) BodyHandClient.closeWorld(w.key().worldId());
                else BodyHandClient.closePopup(w.key());
            return true;
        }
        if (popup.hitDrag((int) mouseX, (int) mouseY)) {
            moving = w.key(); grabX = (int) mouseX - w.x(); grabY = (int) mouseY - w.y();
            return true;
        }
        var cell = visibleCell(w, mouseX, mouseY);
        if (cell == null) return true;
        if (w.key().kind() == StoragePopupWorkspace.HostKind.WORLD) {
            var world = BodyHandClient.worldGrid(w.key().worldId());
            if (world == null || !BodyHandClient.worldActionable(world.target())) return true;
            int cx = cell.column(), cy = cell.row();
            if (cx >= world.width() || cy >= world.height()) return true;
            var entry = world.entryAt(cx, cy);
            if (entry != null) {
                workspace.select(w.key(), entry.token()); dragging = w.key(); fromHand = false;
                crossSource = null;
                worldCrossSource = PopupActionPlan.worldCrossSource(BodyHandClient.inventoryProof(), world, entry);
                beginItemPointer(mouseX, mouseY, cx - entry.x(), cy - entry.y());
                workspace.beginDrag(w.key(), new GridGeometry.Cell(entry.x(), entry.y()),
                        GridGeometry.Rotation.values()[entry.rotation() / 90], new GridGeometry.Cell(entry.x(), entry.y()),
                        GridGeometry.Rotation.values()[entry.rotation() / 90], entry.token(), false);
            } else {
                var view = BodyHandClient.inventoryView();
                var hand = view == null ? null : view.activeHand().equals(view.first().id()) ? view.first() : view.second();
                if (hand != null && hand.token() != null) {
                    workspace.select(w.key(), hand.token()); dragging = w.key(); fromHand = true;
                    crossSource = null;
                    worldCrossSource = null;
                    beginItemPointer(mouseX, mouseY, 0, 0);
                    workspace.beginDrag(w.key(), null, null, new GridGeometry.Cell(cx, cy), GridGeometry.Rotation.DEG_0, hand.token(), true);
                }
            }
            return true;
        }
        var grid = grid(BodyHandClient.inventoryView(), w.key(), false);
        if (grid == null) return true;
        int cx = cell.column(), cy = cell.row();
        if (cx >= grid.host().width() || cy >= grid.host().height()) return true;
        var entry = grid.entryAt(cx, cy);
        var view = BodyHandClient.inventoryView();
        var active = BodyHandClient.popupSourceHand(view, w.key());
        if (entry != null) {
            workspace.select(w.key(), entry.token());
            dragging = w.key(); fromHand = false;
            crossSource = PopupActionPlan.crossSource(BodyHandClient.inventoryProof(), w.key(), entry);
            worldCrossSource = null;
            beginItemPointer(mouseX, mouseY, cx - entry.x(), cy - entry.y());
            workspace.beginDrag(w.key(), new GridGeometry.Cell(entry.x(), entry.y()),
                    GridGeometry.Rotation.values()[entry.rotation() / 90], new GridGeometry.Cell(entry.x(), entry.y()),
                    GridGeometry.Rotation.values()[entry.rotation() / 90], entry.token(), false);
        } else if (grid.canStore() && active != null && active.token() != null) {
            workspace.select(w.key(), active.token()); dragging = w.key(); fromHand = true;
            crossSource = null;
            worldCrossSource = null;
            beginItemPointer(mouseX, mouseY, 0, 0);
            workspace.beginDrag(w.key(), null, null, new GridGeometry.Cell(cx, cy), GridGeometry.Rotation.DEG_0, active.token(), true);
        }
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        var wire = com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.active();
        if (wire != null && wire.workspaceMouseDragged(x, y, button, dx, dy)) return true;
        if (dragging != null) updateItemPointer(x, y, leftButtonDown());
        if (button != 0) return true;
        if (moving != null) workspace.place(moving, (int) x - grabX, (int) y - grabY);
        if (dragging != null) {
            var w = workspace.window(dragging);
            if (w != null && w.drag() != null && (itemMoved || PopupActionPlan.moved(dragStartX, dragStartY, x, y))) {
                itemMoved = true;
                workspace.updateDrag(dragging, itemCell(w, x, y), w.drag().proposedRotation());
            }
        }
        return true;
    }

    @Override public void mouseMoved(double x, double y) {
        if (dragging != null) updateItemPointer(x, y, leftButtonDown());
    }

    private boolean leftButtonDown() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private void updateItemPointer(double x, double y, boolean leftDown) {
        if (!leftDown || dragging == null) return;
        var w = workspace.window(dragging);
        if (w == null || w.drag() == null) return;
        if (itemMoved || PopupActionPlan.moved(dragStartX, dragStartY, x, y)) {
            itemMoved = true;
            workspace.updateDrag(dragging, itemCell(w, x, y), w.drag().proposedRotation());
        }
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        var wire = com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.active();
        if (wire != null && wire.workspaceMouseReleased(x, y, button)) return true;
        if (button != 0) return true;
        if (inactiveClickHand != null) {
            var selected = inactiveClickHand;
            inactiveClickHand = null;
            if (!PopupActionPlan.moved(inactiveClickX, inactiveClickY, x, y))
                BodyHandClient.selectInventoryHand(selected);
            return true;
        }
        if (dragging != null) updateItemPointer(x, y, true);
        if (button == 0 && handGridDrag != null) {
            var source = handGridDrag; handGridDrag = null;
            var w = hit(x, y);
            var bar = HandBarLayout.calculate(width, height);
            var releasedTile = w == null && bar != null ? bar.tileAt((int) x, (int) y) : null;
            if (handGridWasInactive && releasedTile == source.source()
                    && !PopupActionPlan.moved(dragStartX, dragStartY, x, y)) {
                handGridWasInactive = false;
                BodyHandClient.selectInventoryHand(source.handId());
                return true;
            }
            handGridWasInactive = false;
            if (releasedTile != null && releasedTile != source.source()) {
                if (releasedTile == HandBarLayout.Layout.Tile.BACK || releasedTile == HandBarLayout.Layout.Tile.BELT)
                    BodyHandClient.popupSmartStore(source, releasedTile);
                else BodyHandClient.popupEquipment(source, releasedTile);
                return true;
            }
            if (w != null && visibleCell(w, x, y) != null) {
                var anchor = dragCell(w, x, y);
                if (w.key().kind() == StoragePopupWorkspace.HostKind.WORLD) {
                    var proof = BodyHandClient.inventoryProof();
                    if (proof != null && proof.accountId().equals(source.accountId()) && proof.bodyId().equals(source.bodyId())
                            && proof.epoch() == source.epoch() && proof.revision() == source.revision())
                        BodyHandClient.worldAction(w.key(), BodyWorldGridAction.Mode.STORE, source.token(),
                                new StoragePopupWorkspace.Drag(null, null, anchor, GridGeometry.Rotation.values()[handGridRotation]));
                } else BodyHandClient.popupHandGridDrop(w.key(), source, anchor, GridGeometry.Rotation.values()[handGridRotation]);
            }
            return true;
        }
        if (button == 0 && equipmentDrag != null) {
            var drag = equipmentDrag; equipmentDrag = null;
            var bar = HandBarLayout.calculate(width, height);
            var target = hit(x, y) == null && bar != null ? bar.tileAt((int) x, (int) y) : null;
            if (target != drag.source()) BodyHandClient.popupEquipment(drag, target);
            else if (target == HandBarLayout.Layout.Tile.BACK || target == HandBarLayout.Layout.Tile.BELT)
                BodyHandClient.togglePopup(target == HandBarLayout.Layout.Tile.BACK
                        ? StoragePopupWorkspace.HostKind.BAG : StoragePopupWorkspace.HostKind.BELT);
            return true;
        }
        moving = null;
        if (dragging != null) {
            var key = dragging; dragging = null;
            var source = crossSource; crossSource = null;
            var worldSource = worldCrossSource; worldCrossSource = null;
            var w = workspace.window(key);
            if (w != null && w.drag() != null && itemMoved)
                workspace.updateDrag(key, itemCell(w, x, y), w.drag().proposedRotation());
            var target = w == null || fromHand || !itemMoved ? null : hit(x, y);
            var visibleWorldDestination = target != null && target.key().kind() == StoragePopupWorkspace.HostKind.WORLD
                    && visibleCell(target, x, y) != null;
            var worldWorld = worldSource != null && worldSource.host().kind() == StoragePopupWorkspace.HostKind.WORLD
                    && visibleWorldDestination
                    ? PopupActionPlan.worldWorldPlan(worldSource, BodyHandClient.worldGrid(worldSource.worldTarget()),
                    BodyHandClient.worldGrid(target.key().worldId()), BodyHandClient.inventoryProof(),
                    itemCell(target, x, y), w == null || w.drag() == null ? null : w.drag().proposedRotation(),
                    w != null && target != null && !w.provisional() && !target.provisional()
                            && BodyHandClient.worldActionable(worldSource.worldTarget())
                            && BodyHandClient.worldActionable(target.key().worldId()), 0) : null;
            var transfer = worldSource != null ? worldSource
                    : source != null && target != null && target.key().kind() == StoragePopupWorkspace.HostKind.WORLD
                    ? PopupActionPlan.bodyWorldSource(BodyHandClient.inventoryProof(),
                    BodyHandClient.worldGrid(target.key().worldId()), source) : null;
            var crossTarget = transfer == null || worldWorld != null ? null : PopupActionPlan.worldCrossTarget(workspace, transfer, x, y);
            // A grid child released over either hand tile is an explicit TAKE destination,
            // independent of whether this host is worn, held, or in the world.
            var destinationBar = HandBarLayout.calculate(width, height);
            var destinationTile = destinationBar == null || hit(x, y) != null ? null : destinationBar.tileAt((int) x, (int) y);
            var destinationView = BodyHandClient.inventoryView();
            var destination = destinationView == null || destinationTile == null ? null
                    : destinationTile == HandBarLayout.Layout.Tile.FIRST ? destinationView.first()
                    : destinationTile == HandBarLayout.Layout.Tile.SECOND ? destinationView.second() : null;
            var childGrid = !fromHand && w != null && w.drag() != null
                    ? grid(destinationView, key, false) : null;
            var childEntry = childGrid == null ? null : childGrid.host().entries().stream()
                    .filter(e -> e.token().equals(w.drag().sourceToken())).findFirst().orElse(null);
            if (destination != null && destination.token() == null && childEntry != null) {
                if (key.kind() == StoragePopupWorkspace.HostKind.WORLD)
                    BodyHandClient.worldAction(key, BodyWorldGridAction.Mode.TAKE, childEntry.token(), w.drag(), destination.id());
                else BodyHandClient.popupGridTake(key, childEntry, destination.id());
            } else if (w != null && w.drag() != null && worldWorld != null && itemMoved && !fromHand) {
                BodyHandClient.popupCrossWorldWorld(worldWorld);
            } else if (w != null && w.drag() != null && crossTarget != null && itemMoved && !fromHand) {
                BodyHandClient.popupCrossWorld(transfer, crossTarget.key(), itemCell(crossTarget, x, y), w.drag().proposedRotation());
            } else if (w != null && w.drag() != null && key.kind() == StoragePopupWorkspace.HostKind.WORLD
                    && PopupActionPlan.allowWorldGridFallback(key, target == null ? null : target.key())) {
                var drag = w.drag();
                var bar = HandBarLayout.calculate(width, height);
                var tile = bar == null || hit(x, y) != null ? null : bar.tileAt((int) x, (int) y);
                if (!fromHand && (tile == HandBarLayout.Layout.Tile.FIRST || tile == HandBarLayout.Layout.Tile.SECOND)) {
                    var targetView = BodyHandClient.inventoryView();
                    var legacyDestination = targetView == null ? null : tile == HandBarLayout.Layout.Tile.FIRST
                            ? targetView.first() : targetView.second();
                    if (legacyDestination != null && legacyDestination.token() == null) {
                        if (key.kind() == StoragePopupWorkspace.HostKind.WORLD)
                            BodyHandClient.worldAction(key, BodyWorldGridAction.Mode.TAKE, drag.sourceToken(), drag, legacyDestination.id());
                        else {
                            var sourceGrid = grid(BodyHandClient.inventoryView(), key, false);
                            var child = sourceGrid == null ? null : sourceGrid.host().entries().stream()
                                    .filter(e -> e.token().equals(drag.sourceToken())).findFirst().orElse(null);
                             if (child != null) BodyHandClient.popupGridTake(key, child, legacyDestination.id());
                        }
                    }
                }
                if (tile == null && hit(x, y) == w && visibleCell(w, x, y) != null) {
                    var intent = PopupActionPlan.gridIntent(drag, fromHand, itemMoved, button);
                    if (intent != PopupActionPlan.GridIntent.CANCEL)
                        BodyHandClient.worldAction(key, intent == PopupActionPlan.GridIntent.STORE ? BodyWorldGridAction.Mode.STORE
                                : intent == PopupActionPlan.GridIntent.TAKE ? BodyWorldGridAction.Mode.TAKE
                                : BodyWorldGridAction.Mode.MOVE, drag.sourceToken(), drag);
                }
            } else if (w != null && w.drag() != null && !fromHand && itemMoved && source != null
                    && PopupActionPlan.crossTarget(workspace, source, x, y) != null) {
                var bodyTarget = PopupActionPlan.crossTarget(workspace, source, x, y);
                BodyHandClient.popupCrossBody(source, bodyTarget.key(), itemCell(bodyTarget, x, y), w.drag().proposedRotation());
            } else if (w != null && w.drag() != null && hit(x, y) == w && visibleCell(w, x, y) != null) {
                var drag = w.drag();
                var intent = PopupActionPlan.gridIntent(drag, fromHand, itemMoved, button);
                if (intent == PopupActionPlan.GridIntent.TAKE) {
                    var view = BodyHandClient.inventoryView();
                    var active = BodyHandClient.popupSourceHand(view, key);
                    var grid = grid(view, key, false);
                    var entry = grid == null ? null : grid.host().entries().stream()
                            .filter(e -> e.token().equals(w.drag().sourceToken())).findFirst().orElse(null);
                    if (active != null && active.token() == null && grid != null && grid.canTake() && entry != null)
                        BodyHandClient.popupGridTake(key, entry);
                } else if (intent == PopupActionPlan.GridIntent.MOVE || intent == PopupActionPlan.GridIntent.STORE)
                    BodyHandClient.popupGridDrop(key, drag.sourceToken(), drag, fromHand);
            }
            workspace.clearDrag(key);
        }
        itemMoved = false;
        return true;
    }

    private void beginItemPointer(double x, double y, int offsetX, int offsetY) {
        dragStartX = x; dragStartY = y;
        cellOffsetX = offsetX; cellOffsetY = offsetY;
        itemMoved = false;
    }

    private GridGeometry.Cell itemCell(StoragePopupWorkspace.Window w, double x, double y) {
        return PopupActionPlan.grabbedAnchor(dragCell(w, x, y), cellOffsetX, cellOffsetY);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!BodyHandClient.hasCommittedBody()) { clearMovement(); return true; }
        var wire = com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.active();
        if (wire != null && key == GLFW.GLFW_KEY_ESCAPE) { wire.onClose(); return true; }
        if (BodyHandClient.popupAction(key, scan, modifiers, actionKeys.add(scan != 0 ? -scan : key))) return true;
        if (key == GLFW.GLFW_KEY_R) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            var window = mc.getWindow();
            double x = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
            double y = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
            rotateCurrent(x, y);
            return true;
        }
        var selectedWorld = workspace.windows().stream().filter(w -> w.visible()
                && w.key().kind() == StoragePopupWorkspace.HostKind.WORLD && w.selectedChild() != null)
                .max(Comparator.comparingInt(StoragePopupWorkspace.Window::z)).orElse(null);
        if (selectedWorld != null && BodyHandClient.worldActionable(selectedWorld.key().worldId())) {
            var world = BodyHandClient.worldGrid(selectedWorld.key().worldId());
            var entry = world.entries().stream().filter(e -> e.token().equals(selectedWorld.selectedChild())).findFirst().orElse(null);
            if (entry != null && selectedWorld.drag() == null && (key == GLFW.GLFW_KEY_ENTER
                    || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN))
                workspace.beginDrag(selectedWorld.key(), new GridGeometry.Cell(entry.x(), entry.y()),
                        GridGeometry.Rotation.values()[entry.rotation() / 90], new GridGeometry.Cell(entry.x(), entry.y()),
                        GridGeometry.Rotation.values()[entry.rotation() / 90], entry.token(), false);
            var drag = selectedWorld.drag();
            if (drag != null) {
                if (key == GLFW.GLFW_KEY_ENTER) {
                    boolean take = drag.oldAnchor().equals(drag.proposedAnchor()) && drag.oldRotation() == drag.proposedRotation();
                    BodyHandClient.worldAction(selectedWorld.key(), take ? BodyWorldGridAction.Mode.TAKE
                            : BodyWorldGridAction.Mode.MOVE, drag.sourceToken(), drag);
                    workspace.clearDrag(selectedWorld.key()); return true;
                }
                int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
                int dy = key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
                if (dx != 0 || dy != 0) {
                    workspace.updateDrag(selectedWorld.key(), new GridGeometry.Cell(drag.proposedAnchor().x() + dx,
                            drag.proposedAnchor().y() + dy), drag.proposedRotation()); return true;
                }
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (dragging != null) { workspace.clearDrag(dragging); dragging = null; crossSource = null; worldCrossSource = null; itemMoved = false; return true; }
            if (selectedWorld != null && selectedWorld.drag() != null) { workspace.clearDrag(selectedWorld.key()); return true; }
            BodyHandClient.dismissPopupPointerMode(); return true;
        }
        return true;
    }

    private boolean rotateCurrent(double x, double y) {
        if (handGridDrag != null) {
            handGridRotation = (handGridRotation + 1) % 4;
            return true;
        }
        if (dragging != null) {
            updateItemPointer(x, y, true);
            var w = workspace.window(dragging);
            if (w != null && w.drag() != null) {
                workspace.updateDrag(dragging, w.drag().proposedAnchor(),
                        GridGeometry.Rotation.values()[(w.drag().proposedRotation().ordinal() + 1) % 4]);
            }
            return true;
        }
        return rotateSelectedWorld();
    }

    /** Rotate the selected world-grid child even when selection came from keyboard navigation. */
    private boolean rotateSelectedWorld() {
        var selected = workspace.windows().stream().filter(w -> w.visible()
                && w.key().kind() == StoragePopupWorkspace.HostKind.WORLD && w.selectedChild() != null)
                .max(Comparator.comparingInt(StoragePopupWorkspace.Window::z)).orElse(null);
        if (selected == null || !BodyHandClient.worldActionable(selected.key().worldId())) return false;
        var world = BodyHandClient.worldGrid(selected.key().worldId());
        if (world == null) return false;
        var entry = world.entries().stream().filter(e -> e.token().equals(selected.selectedChild())).findFirst().orElse(null);
        if (entry == null) return false;
        var drag = selected.drag();
        if (drag == null) {
            var anchor = new GridGeometry.Cell(entry.x(), entry.y());
            var rotation = GridGeometry.Rotation.values()[entry.rotation() / 90];
            workspace.beginDrag(selected.key(), anchor, rotation, anchor, rotation, entry.token(), false);
            drag = selected.drag();
        }
        workspace.updateDrag(selected.key(), drag.proposedAnchor(),
                GridGeometry.Rotation.values()[(drag.proposedRotation().ordinal() + 1) % 4]);
        return true;
    }

    private static GridGeometry.Cell dragCell(StoragePopupWorkspace.Window w, double x, double y) {
        var grid = layout(w).gridBounds();
        return new GridGeometry.Cell(Math.floorDiv((int) x - grid.x(), CELL),
                Math.floorDiv((int) y - grid.y(), CELL));
    }

    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        actionKeys.remove(scan != 0 ? -scan : key);
        return true;
    }

    @Override public void tick() {
        super.tick();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_FOCUSED) == org.lwjgl.glfw.GLFW.GLFW_FALSE
                || !BodyHandClient.hasCommittedBody()) { clearMovement(); actionKeys.clear(); cancelPointer(); }
    }

    @Override public void removed() {
        clearMovement();
        cancelPointer();
        super.removed();
    }

    @Override public void onClose() {
        BodyHandClient.dismissPopupPointerMode();
    }

    private void cancelPointer() {
        if (dragging != null) workspace.clearDrag(dragging);
        dragging = moving = null;
        crossSource = null;
        worldCrossSource = null;
        equipmentDrag = handGridDrag = null;
        handGridWasInactive = false;
        inactiveClickHand = null;
        itemMoved = false;
        handGridRotation = 0;
        menuTile = null;
        menuSource = null;
    }

    @Override public PopupMovementIntent.State movementIntent() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        boolean focused = mc.screen == this && BodyHandClient.hasCommittedBody()
                && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        if (!focused) return movement.sample(false, false, false, false, false, false, false, false);
        var o = mc.options;
        return movement.sample(true, held(o.keyUp, window), held(o.keyDown, window),
                held(o.keyLeft, window), held(o.keyRight, window), held(o.keyJump, window),
                held(o.keyShift, window), held(o.keySprint, window));
    }

    @Override public boolean capturesKeyboard() { return false; }

    private static boolean held(net.minecraft.client.KeyMapping mapping, long window) {
        var bound = mapping.getKey();
        if (bound.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) return false;
        int key = bound.getType() == com.mojang.blaze3d.platform.InputConstants.Type.SCANCODE
                ? scanKey(bound.getValue()) : bound.getValue();
        if (key < 0 || key > GLFW.GLFW_KEY_LAST || GLFW.glfwGetKey(window, key) != GLFW.GLFW_PRESS) return false;
        int mods = (down(window, GLFW.GLFW_KEY_LEFT_SHIFT) || down(window, GLFW.GLFW_KEY_RIGHT_SHIFT) ? GLFW.GLFW_MOD_SHIFT : 0)
                | (down(window, GLFW.GLFW_KEY_LEFT_CONTROL) || down(window, GLFW.GLFW_KEY_RIGHT_CONTROL) ? GLFW.GLFW_MOD_CONTROL : 0)
                | (down(window, GLFW.GLFW_KEY_LEFT_ALT) || down(window, GLFW.GLFW_KEY_RIGHT_ALT) ? GLFW.GLFW_MOD_ALT : 0)
                | (down(window, GLFW.GLFW_KEY_LEFT_SUPER) || down(window, GLFW.GLFW_KEY_RIGHT_SUPER) ? GLFW.GLFW_MOD_SUPER : 0);
        // The modifier key itself is not an alt/shift chord merely because it is pressed.
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) mods &= ~GLFW.GLFW_MOD_SHIFT;
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) mods &= ~GLFW.GLFW_MOD_CONTROL;
        if (key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT) mods &= ~GLFW.GLFW_MOD_ALT;
        int required = BodyHandClient.modifierMask(mapping);
        return required == 0 || PopupMovementIntent.modifiersMatch(required, mods);
    }

    private static boolean down(long window, int key) { return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS; }

    private static int scanKey(int scan) {
        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++)
            if (GLFW.glfwGetKeyScancode(key) == scan) return key;
        return -1;
    }

    private void clearMovement() { movement.clear(); }

    @Override public boolean isPauseScreen() { return false; }
}
