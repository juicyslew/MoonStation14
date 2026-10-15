package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pure client-side state for independently placed storage popups. Contains no game authority. */
public final class StoragePopupWorkspace {
    public static final int MAX_WINDOWS = 3;

    public enum HostKind { BAG, BELT, HELD, WORLD }
    public enum PreviewFlag { PERMITTED, OUT_OF_BOUNDS, OVERLAP, UNAVAILABLE }
    public enum PreviewColor { GREEN, RED }
    public enum OpenResult { OPENED, FOCUSED, DENIED }

    public record HostKey(HostKind kind, String token, UUID worldId) {
        public HostKey {
            Objects.requireNonNull(kind);
            if (kind == HostKind.WORLD) {
                Objects.requireNonNull(worldId, "worldId");
                if (token != null) throw new IllegalArgumentException("World key cannot have worn token");
            } else if (token == null || token.isBlank() || worldId != null) {
                throw new IllegalArgumentException("Worn host requires token");
            }
        }
        public static HostKey worn(HostKind kind, String token) {
            if (kind != HostKind.BAG && kind != HostKind.BELT) throw new IllegalArgumentException("Not worn");
            return new HostKey(kind, token, null);
        }
        public static HostKey held(String token) { return new HostKey(HostKind.HELD, token, null); }
        public static HostKey world(UUID id) { return new HostKey(HostKind.WORLD, null, id); }
    }

    public record Point(int x, int y) { }
    /** Non-overlapping visible pieces of a window after higher-z outer bounds occlude it. */
    public List<StoragePopupLayout.Rect> uncovered(StoragePopupLayout.Rect bounds, Window target) {
        List<StoragePopupLayout.Rect> pieces = new ArrayList<>();
        if (bounds.width() <= 0 || bounds.height() <= 0 || target == null || !target.visible()) return pieces;
        pieces.add(bounds);
        for (Window higher : windows.values()) {
            if (!higher.visible() || higher.z() <= target.z()) continue;
            var cover = new StoragePopupLayout.Rect(higher.x(), higher.y(), higher.width(), higher.height());
            List<StoragePopupLayout.Rect> next = new ArrayList<>();
            for (var piece : pieces) subtract(piece, cover, next);
            pieces = next;
        }
        return List.copyOf(pieces);
    }
    private static void subtract(StoragePopupLayout.Rect source, StoragePopupLayout.Rect cover,
                                 List<StoragePopupLayout.Rect> result) {
        if (!source.intersects(cover)) { result.add(source); return; }
        int left=Math.max(source.x(), cover.x()), right=Math.min(source.x()+source.width(), cover.x()+cover.width());
        int top=Math.max(source.y(), cover.y()), bottom=Math.min(source.y()+source.height(), cover.y()+cover.height());
        if (top > source.y()) result.add(new StoragePopupLayout.Rect(source.x(), source.y(), source.width(), top-source.y()));
        if (bottom < source.y()+source.height()) result.add(new StoragePopupLayout.Rect(source.x(), bottom, source.width(), source.y()+source.height()-bottom));
        if (left > source.x()) result.add(new StoragePopupLayout.Rect(source.x(), top, left-source.x(), bottom-top));
        if (right < source.x()+source.width()) result.add(new StoragePopupLayout.Rect(right, top, source.x()+source.width()-right, bottom-top));
    }
    public record Drag(GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
                       GridGeometry.Cell proposedAnchor, GridGeometry.Rotation proposedRotation,
                       String sourceToken, boolean fromHand, String sourceItemId, int sourceCount) {
        public Drag(GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
                    GridGeometry.Cell proposedAnchor, GridGeometry.Rotation proposedRotation) {
            this(oldAnchor, oldRotation, proposedAnchor, proposedRotation, null, false, null, 0);
        }
    }
    public record Preview(PreviewFlag flag, PreviewColor color, boolean maySendAction) { }

    public static final class Window {
        private HostKey key;
        private int x, y, preferredX, preferredY, preferredWidth, preferredHeight, width, height, scroll;
        private int z;
        private String selectedChild;
        private Drag drag;
        private boolean visible = true, provisional;
        private Window(HostKey key, int x, int y, int width, int height, int z, boolean provisional) {
            this.key = key; this.preferredX = x; this.preferredY = y;
            this.preferredWidth = width; this.preferredHeight = height;
            this.z = z; this.provisional = provisional;
        }
        public HostKey key() { return key; }
        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int height() { return height; }
        public int scroll() { return scroll; }
        public int z() { return z; }
        public String selectedChild() { return selectedChild; }
        public Drag drag() { return drag; }
        public boolean visible() { return visible; }
        public boolean provisional() { return provisional; }
    }

    private final Map<HostKey, Window> windows = new LinkedHashMap<>();
    private UUID body;
    private long epoch;
    private int screenWidth, screenHeight;
    private boolean pointerMode;

    public StoragePopupWorkspace(int screenWidth, int screenHeight) { resize(screenWidth, screenHeight); }

    public void bind(UUID bodyId, long authorityEpoch) {
        if (bodyId == null || authorityEpoch <= 0) { clear(); body = null; epoch = 0; return; }
        if (!bodyId.equals(body) || epoch != authorityEpoch) { clear(); body = bodyId; epoch = authorityEpoch; }
    }
    public void clear() { windows.clear(); pointerMode = false; }
    public boolean pointerMode() { return pointerMode; }
    public void pointerMode(boolean enabled) { pointerMode = enabled; }
    public List<Window> windows() { return List.copyOf(windows.values()); }
    public Window window(HostKey key) { return windows.get(key); }
    /** Rebind an open popup to a newly authoritative host identity without changing user layout. */
    public boolean rekey(HostKey oldHostKey, HostKey newHostKey) {
        Objects.requireNonNull(oldHostKey);
        Objects.requireNonNull(newHostKey);
        Window moving = windows.get(oldHostKey);
        if (moving == null) return false;
        if (oldHostKey.equals(newHostKey)) {
            moving.provisional = true;
            moving.drag = null;
            return true;
        }
        // If identities converge, the rehosted window is the same logical item: keep its
        // geometry, z-order and selection, replacing the stale destination entry.
        windows.remove(newHostKey);
        windows.remove(oldHostKey);
        moving.key = newHostKey;
        moving.provisional = true;
        moving.drag = null;
        windows.put(newHostKey, moving);
        return true;
    }
    /** Update authoritative outer dimensions while retaining user placement and z-order. */
    public void resizeWindow(HostKey key, int width, int height) {
        Window window = windows.get(key);
        if (window == null || width <= 0 || height <= 0) return;
        window.preferredWidth = width;
        window.preferredHeight = height;
        clamp(window);
    }
    /** Full outer bounds occlude lower windows, including their grids and controls. */
    public Window topmost(double x, double y) {
        return windows.values().stream().filter(w -> w.visible && x >= w.x && y >= w.y
                && x < w.x + w.width && y < w.y + w.height)
                .max(Comparator.comparingInt(Window::z)).orElse(null);
    }
    public void raise(Window window) { if (window != null && windows.get(window.key)==window && window.visible) focus(window); }

    public OpenResult open(HostKey key, boolean trustedSnapshot, int x, int y, int width, int height) {
        Objects.requireNonNull(key);
        Window existing = windows.get(key);
        if (existing != null && existing.visible) { focus(existing); return OpenResult.FOCUSED; }
        if (visibleWindowCount() >= MAX_WINDOWS || (key.kind() == HostKind.WORLD && !trustedSnapshot))
            return OpenResult.DENIED;
        if (existing != null) { existing.visible = true; focus(existing); return OpenResult.FOCUSED; }
        Window created = new Window(key, x, y, width, height, 0, !trustedSnapshot);
        created.z=PopupSiblingOrder.front();
        clamp(created); windows.put(key, created); return OpenResult.OPENED;
    }
    private int visibleWindowCount() {
        int count = 0;
        for (Window window : windows.values()) if (window.visible) count++;
        return count;
    }
    public boolean toggle(HostKey key, boolean trustedSnapshot, int x, int y, int width, int height) {
        Window existing = windows.get(key);
        if (existing != null && existing.visible) { close(key); return false; }
        open(key, trustedSnapshot, x, y, width, height); return window(key) != null && window(key).visible;
    }
    public void close(HostKey key) {
        Window w = windows.get(key);
        if (w == null) return;
        if (key.kind() == HostKind.BAG || key.kind() == HostKind.BELT) {
            w.visible = false; w.drag = null; w.selectedChild = null;
        } else windows.remove(key);
    }
    public void focus(HostKey key) { Window window = windows.get(key); if (window != null && window.visible) focus(window); }
    private void focus(Window window) { window.z = PopupSiblingOrder.front(); }
    public void place(HostKey key, int x, int y) { Window w = windows.get(key); if (w != null) { w.preferredX=x; w.preferredY=y; clamp(w); } }
    public void scroll(HostKey key, int amount) { Window w=windows.get(key); if (w != null) w.scroll=Math.max(0, amount); }
    public void select(HostKey key, String childToken) { Window w=windows.get(key); if (w != null) w.selectedChild=childToken; }
    public void beginDrag(HostKey key, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
                          GridGeometry.Cell proposal, GridGeometry.Rotation proposedRotation) {
        Window w = windows.get(key);
        beginDrag(key, oldAnchor, oldRotation, proposal, proposedRotation,
                w == null ? null : w.selectedChild, false, null, 0);
    }
    public void beginDrag(HostKey key, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
                          GridGeometry.Cell proposal, GridGeometry.Rotation proposedRotation,
                          String sourceToken, boolean fromHand) {
        beginDrag(key, oldAnchor, oldRotation, proposal, proposedRotation, sourceToken, fromHand, null, 0);
    }
    public void beginDrag(HostKey key, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
                          GridGeometry.Cell proposal, GridGeometry.Rotation proposedRotation,
                          String sourceToken, boolean fromHand, String itemId, int count) {
        Window w=windows.get(key); if (w != null) w.drag=new Drag(oldAnchor, oldRotation, proposal, proposedRotation, sourceToken, fromHand,itemId,count);
    }
    public void updateDrag(HostKey key, GridGeometry.Cell proposal, GridGeometry.Rotation rotation) {
        Window w=windows.get(key); if (w != null && w.drag != null)
            w.drag=new Drag(w.drag.oldAnchor(), w.drag.oldRotation(), proposal, rotation,
                    w.drag.sourceToken(), w.drag.fromHand(), w.drag.sourceItemId(), w.drag.sourceCount());
    }
    public void clearDrag(HostKey key) { Window w=windows.get(key); if (w != null) w.drag=null; }
    /** A confirmed body snapshot is the authority for whether a captured hand source still exists. */
    public void validateHandSources(List<String> heldTokens) {
        for (Window w : windows.values())
            if (w.drag != null && w.drag.fromHand() && !heldTokens.contains(w.drag.sourceToken())) w.drag = null;
    }
    public void validateHandSourceProof(java.util.Collection<com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Hand> hands) {
        for (Window w : windows.values()) {
            var drag=w.drag;
            if (drag == null || !drag.fromHand()) continue;
            boolean exact=hands.stream().anyMatch(h -> java.util.Objects.equals(h.token(),drag.sourceToken())
                    && (drag.sourceItemId()==null || java.util.Objects.equals(h.itemId(),drag.sourceItemId())
                    && h.count()==drag.sourceCount()));
            if(!exact) w.drag=null;
        }
    }

    /** Apply a fresh host revision without disturbing user state for still-present child tokens. */
    public void snapshot(HostKey key, boolean confirmed, boolean hostPresent, List<String> childTokens) {
        Window w=windows.get(key); if (w == null) return;
        if (!hostPresent) {
            if (confirmed) windows.remove(key);
            else w.provisional = true;
            return;
        }
        String selected=w.selectedChild;
        if (confirmed && selected != null && !childTokens.contains(selected)) w.selectedChild=null;
        if (confirmed && w.drag != null && w.drag.sourceToken() != null
                && !w.drag.fromHand() && !childTokens.contains(w.drag.sourceToken())) w.drag=null;
        w.provisional=!confirmed;
    }

    /** Reconciles a captured grid child against settled source identity; unavailable proof is inert. */
    public record SourceIdentity(String token, String itemId, int count) { }
    public boolean reconcileChildSource(HostKey key, boolean proofAvailable, List<SourceIdentity> entries) {
        Window w=windows.get(key);
        if (!proofAvailable || w == null || w.drag == null || w.drag.fromHand() || w.drag.sourceToken() == null) return true;
        var drag=w.drag;
        boolean exact=entries != null && entries.stream().anyMatch(e -> drag.sourceToken().equals(e.token())
                && drag.sourceItemId() != null && drag.sourceItemId().equals(e.itemId()) && drag.sourceCount() == e.count());
        if (!exact) w.drag=null;
        return exact;
    }
    public void authorityChanged() { clear(); }

    public void resize(int guiWidth, int guiHeight) {
        if (guiWidth <= 0 || guiHeight <= 0) throw new IllegalArgumentException("GUI bounds must be positive");
        screenWidth=guiWidth; screenHeight=guiHeight;
        for (Window window : windows.values()) clamp(window);
    }
    private void clamp(Window w) {
        w.width=Math.max(1, Math.min(w.preferredWidth, screenWidth));
        w.height=Math.max(1, Math.min(w.preferredHeight, screenHeight));
        // Pin oversized windows at the origin so the close/grip strip is not scrolled away.
        w.x=Math.max(0, Math.min(w.preferredX, Math.max(0, screenWidth-w.width)));
        w.y=Math.max(0, Math.min(w.preferredY, Math.max(0, screenHeight-w.height)));
    }

    /** Evaluate a preview on occupancy with the moved child removed. This never mutates source state. */
    public static Preview preview(GridGeometry.Snapshot<String> occupancy, String movedToken,
                                  GridGeometry.Footprint footprint, GridGeometry.Cell anchor,
                                  GridGeometry.Rotation rotation, boolean confirmed) {
        if (!confirmed || occupancy == null || footprint == null || anchor == null || rotation == null)
            return new Preview(PreviewFlag.UNAVAILABLE, PreviewColor.RED, false);
        GridGeometry.Snapshot<String> base = occupancy;
        if (movedToken != null && base.placement(movedToken) != null) base=base.remove(movedToken).snapshot();
        var candidate = new GridGeometry.Placement<>(movedToken == null ? "preview" : movedToken, footprint, anchor, rotation);
        boolean bounds=true;
        for (var cell : candidate.occupiedCells())
            if (cell.x()<0 || cell.y()<0 || cell.x()>=base.storage().width() || cell.y()>=base.storage().height()
                    || !base.storage().contains(cell)) { bounds=false; break; }
        if (!bounds) return new Preview(PreviewFlag.OUT_OF_BOUNDS, PreviewColor.RED, false);
        for (var cell : candidate.occupiedCells()) if (base.isOccupied(cell))
            return new Preview(PreviewFlag.OVERLAP, PreviewColor.RED, false);
        return new Preview(PreviewFlag.PERMITTED, PreviewColor.GREEN, true);
    }
}
