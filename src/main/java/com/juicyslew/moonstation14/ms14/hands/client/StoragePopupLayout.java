package com.juicyslew.moonstation14.ms14.hands.client;

/** Immutable pixel geometry for compact storage popups. Coordinates are GUI-scaled. */
public record StoragePopupLayout(Kind kind, int x, int y, int columns, int rows) {
    public static final int CELL_SIZE = 10;
    public static final int MAX_COLUMNS = 8;
    public static final int MAX_ROWS = 4;
    public static final int LEFT_STRIP_WIDTH = 10;

    public enum Kind {
        BAG(7, 4), BELT(8, 2), HELD(7, 4), WORLD(8, 4);
        private final int columns;
        private final int rows;
        Kind(int columns, int rows) { this.columns = columns; this.rows = rows; }
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
        public boolean intersects(Rect other) {
            return other != null && width > 0 && height > 0 && other.width > 0 && other.height > 0
                    && x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    public record Cell(int column, int row) { }

    public StoragePopupLayout {
        if (kind == null || columns < 1 || columns > MAX_COLUMNS || rows < 1 || rows > MAX_ROWS)
            throw new IllegalArgumentException("Invalid storage popup dimensions");
    }

    public static StoragePopupLayout at(Kind kind, int x, int y) {
        if (kind == null) throw new IllegalArgumentException("kind cannot be null");
        return new StoragePopupLayout(kind, x, y, kind.columns, kind.rows);
    }

    public static int outerWidth(int columns) { return columns * CELL_SIZE + LEFT_STRIP_WIDTH + 1 + 3; }
    public static int outerHeight(int rows) { return rows * CELL_SIZE + 4; }
    public Rect outerBounds() { return new Rect(x, y, outerWidth(columns), outerHeight(rows)); }
    public Rect closeBounds() { return new Rect(x + 1, y + 1, 8, 8); }
    public Rect gripBounds() { return new Rect(x + 2, y + 11, 6, 10); }
    public Rect dragBounds() { return new Rect(x, y, LEFT_STRIP_WIDTH, outerHeight(rows)); }
    public Rect gridBounds() { return new Rect(x + LEFT_STRIP_WIDTH + 1, y + 2, columns * CELL_SIZE, rows * CELL_SIZE); }
    public Rect outlineBounds() {
        Rect grid = gridBounds();
        return new Rect(grid.x - 1, grid.y - 1, grid.width + 2, grid.height + 2);
    }

    /** Visible portion of the exact grid, excluding the popup border and clipped viewport. */
    public Rect visibleGrid(int visibleWidth, int visibleHeight) {
        Rect grid = gridBounds();
        int right = Math.min(grid.x + grid.width, x + visibleWidth - 1);
        int bottom = Math.min(grid.y + grid.height, y + visibleHeight - 1);
        return new Rect(grid.x, grid.y, Math.max(0, right - grid.x), Math.max(0, bottom - grid.y));
    }

    public Cell visibleCellAt(int px, int py, int visibleWidth, int visibleHeight) {
        return visibleGrid(visibleWidth, visibleHeight).contains(px, py) ? cellAt(px, py) : null;
    }

    /** Rank from the authoritative ordered entry list: 2 gold, 1 silver, 0 unmarked. */
    public static int rank(int size, int index) {
        return index == size - 1 && size > 0 ? 2 : index == size - 2 && size > 1 ? 1 : 0;
    }

    /** Returns null for the strip, border, or any point outside the grid. */
    public Cell cellAt(int px, int py) {
        Rect grid = gridBounds();
        if (!grid.contains(px, py)) return null;
        return new Cell((px - grid.x) / CELL_SIZE, (py - grid.y) / CELL_SIZE);
    }
    public Cell cellFromMouse(int px, int py) { return cellAt(px, py); }
    public boolean hitClose(int px, int py) { return closeBounds().contains(px, py); }
    public boolean hitGrip(int px, int py) { return gripBounds().contains(px, py); }
    public boolean hitDrag(int px, int py) { return dragBounds().contains(px, py) && !hitClose(px, py); }

    /** Clamps the anchor when possible; undersized viewports pin it to zero and allow clipping. */
    public static int[] clampDragAnchor(int x, int y, int viewportWidth, int viewportHeight,
                                        int columns, int rows) {
        if (columns < 1 || columns > MAX_COLUMNS || rows < 1 || rows > MAX_ROWS)
            throw new IllegalArgumentException("Invalid storage popup dimensions");
        return new int[]{clamp(x, outerWidth(columns), viewportWidth), clamp(y, outerHeight(rows), viewportHeight)};
    }
    public StoragePopupLayout clampDragAnchor(int viewportWidth, int viewportHeight) {
        int[] anchor = clampDragAnchor(x, y, viewportWidth, viewportHeight, columns, rows);
        return new StoragePopupLayout(kind, anchor[0], anchor[1], columns, rows);
    }
    private static int clamp(int coordinate, int size, int viewport) {
        return Math.max(0, Math.min(coordinate, Math.max(0, viewport - size)));
    }
}

