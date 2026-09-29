package com.juicyslew.moonstation14.ms14.storage.grid;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure, bounded geometry for a masked storage grid. This class knows nothing about game items. */
public final class GridGeometry {
    /** Provisional safety limits; owner approval is still pending. */
    public static final int MAX_WIDTH = 16;
    public static final int MAX_HEIGHT = 16;
    public static final int MAX_CELLS = 256;

    private GridGeometry() {
    }

    public record Cell(int x, int y) {
    }

    public enum Rotation {
        DEG_0(0), DEG_90(90), DEG_180(180), DEG_270(270);

        private final int degrees;

        Rotation(int degrees) {
            this.degrees = degrees;
        }

        public int degrees() {
            return degrees;
        }
    }

    /** A rectangular domain with an explicit set of cells where storage is allowed. */
    public static final class StorageMask {
        private final int width;
        private final int height;
        private final Set<Cell> cells;

        private StorageMask(int width, int height, Set<Cell> cells) {
            this.width = width;
            this.height = height;
            this.cells = Set.copyOf(cells);
        }

        public static StorageMask of(int width, int height, Collection<Cell> approvedCells) {
            Objects.requireNonNull(approvedCells, "approvedCells");
            validateDimensions(width, height);
            if (approvedCells.size() > MAX_CELLS)
                throw new IllegalArgumentException("Storage mask exceeds cell limit");
            Set<Cell> cells = new HashSet<>();
            int count = 0;
            for (Cell cell : approvedCells) {
                if (count++ >= MAX_CELLS)
                    throw new IllegalArgumentException("Storage mask exceeds cell limit");
                Objects.requireNonNull(cell, "approved cell");
                validateDomainCell(width, height, cell);
                if (!cells.add(cell))
                    throw new IllegalArgumentException("Duplicate storage cell: " + cell);
            }
            return new StorageMask(width, height, cells);
        }

        public static StorageMask rectangle(int width, int height) {
            validateDimensions(width, height);
            List<Cell> cells = new ArrayList<>(width * height);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    cells.add(new Cell(x, y));
            return of(width, height, cells);
        }

        public int width() { return width; }
        public int height() { return height; }
        public Set<Cell> cells() { return cells; }
        public boolean contains(Cell cell) { return cells.contains(cell); }
    }

    /** A normalized (minimum x/y are zero) occupied-cell shape and its four cardinal forms. */
    public static final class Footprint {
        private final List<Cell> cells;
        private final Map<Rotation, List<Cell>> rotations;

        private Footprint(List<Cell> cells) {
            this.cells = List.copyOf(cells);
            Map<Rotation, List<Cell>> rotated = new LinkedHashMap<>();
            for (Rotation rotation : Rotation.values())
                rotated.put(rotation, rotate(this.cells, rotation));
            this.rotations = Collections.unmodifiableMap(rotated);
        }

        public static Footprint of(Collection<Cell> inputCells) {
            Objects.requireNonNull(inputCells, "inputCells");
            if (inputCells.isEmpty() || inputCells.size() > MAX_CELLS)
                throw new IllegalArgumentException("Footprint must contain 1.." + MAX_CELLS + " cells");
            Set<Cell> unique = new HashSet<>();
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int count = 0;
            for (Cell cell : inputCells) {
                if (count++ >= MAX_CELLS)
                    throw new IllegalArgumentException("Footprint exceeds cell limit");
                Objects.requireNonNull(cell, "footprint cell");
                if (!unique.add(cell))
                    throw new IllegalArgumentException("Duplicate footprint cell: " + cell);
                minX = Math.min(minX, cell.x());
                minY = Math.min(minY, cell.y());
                maxX = Math.max(maxX, cell.x());
                maxY = Math.max(maxY, cell.y());
            }
            // Widen before subtraction so hostile integer extremes cannot wrap.
            long spanX = (long) maxX - minX + 1;
            long spanY = (long) maxY - minY + 1;
            if (spanX > MAX_WIDTH || spanY > MAX_HEIGHT)
                throw new IllegalArgumentException("Footprint bounds exceed provisional limits");
            List<Cell> normalized = new ArrayList<>(unique.size());
            for (Cell cell : unique)
                normalized.add(new Cell((int) ((long) cell.x() - minX), (int) ((long) cell.y() - minY)));
            normalized.sort(CELL_ORDER);
            return new Footprint(normalized);
        }

        public List<Cell> cells() { return cells; }
        public List<Cell> cells(Rotation rotation) { return rotations.get(Objects.requireNonNull(rotation)); }
        public Map<Rotation, List<Cell>> rotations() { return rotations; }
    }

    /** Immutable placement value: anchor is the translated origin of the normalized shape. */
    public record Placement<E>(E token, Footprint footprint, Cell anchor, Rotation rotation) {
        public Placement {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(footprint, "footprint");
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(rotation, "rotation");
        }

        public List<Cell> occupiedCells() {
            List<Cell> translated = new ArrayList<>(footprint.cells(rotation).size());
            for (Cell cell : footprint.cells(rotation))
                translated.add(translate(anchor, cell));
            return List.copyOf(translated);
        }
    }

    public enum Rejection { NONE, INVALID_PLACEMENT, TOKEN_ALREADY_PRESENT, TOKEN_NOT_PRESENT, NO_SPACE }

    /** Every rejected operation returns the exact original snapshot. */
    public record Mutation<E>(Snapshot<E> snapshot, Rejection rejection, Placement<E> placement) {
        public Mutation {
            Objects.requireNonNull(snapshot);
            Objects.requireNonNull(rejection);
        }
        public boolean accepted() { return rejection == Rejection.NONE; }
    }

    public static final class Snapshot<E> {
        private final StorageMask storage;
        private final Map<E, Placement<E>> placements;
        private final BitSet occupiedCells;

        private Snapshot(StorageMask storage, Map<E, Placement<E>> placements) {
            this.storage = storage;
            this.placements = Collections.unmodifiableMap(new LinkedHashMap<>(placements));
            this.occupiedCells = new BitSet(storage.width * storage.height);
            for (Placement<E> placement : placements.values())
                for (Cell cell : placement.occupiedCells())
                    occupiedCells.set(cell.y() * storage.width + cell.x());
        }

        public static <E> Snapshot<E> empty(StorageMask storage) {
            return new Snapshot<>(Objects.requireNonNull(storage), Map.of());
        }

        public StorageMask storage() { return storage; }
        public Map<E, Placement<E>> placements() { return placements; }
        public Placement<E> placement(E token) { return placements.get(token); }

        public boolean isOccupied(Cell cell) {
            return cell != null && cell.x() >= 0 && cell.y() >= 0 && cell.x() < storage.width && cell.y() < storage.height
                    && occupiedCells.get(cell.y() * storage.width + cell.x());
        }

        public Mutation<E> place(E token, Footprint footprint, Cell anchor, Rotation rotation) {
            if (token == null || footprint == null || anchor == null || rotation == null)
                return rejected(Rejection.INVALID_PLACEMENT, null);
            if (placements.containsKey(token))
                return rejected(Rejection.TOKEN_ALREADY_PRESENT, null);
            Placement<E> candidate;
            try {
                candidate = new Placement<>(token, footprint, anchor, rotation);
                if (!fits(candidate)) return rejected(Rejection.INVALID_PLACEMENT, candidate);
            } catch (ArithmeticException | NullPointerException exception) {
                return rejected(Rejection.INVALID_PLACEMENT, null);
            }
            for (Cell cell : candidate.occupiedCells())
                if (occupiedCells.get(cell.y() * storage.width + cell.x()))
                    return rejected(Rejection.INVALID_PLACEMENT, candidate);
            Map<E, Placement<E>> next = new LinkedHashMap<>(placements);
            next.put(token, candidate);
            return new Mutation<>(new Snapshot<>(storage, next), Rejection.NONE, candidate);
        }

        public Mutation<E> remove(E token) {
            if (token == null || !placements.containsKey(token))
                return rejected(Rejection.TOKEN_NOT_PRESENT, null);
            Map<E, Placement<E>> next = new LinkedHashMap<>(placements);
            Placement<E> removed = next.remove(token);
            return new Mutation<>(new Snapshot<>(storage, next), Rejection.NONE, removed);
        }

        /** Search every domain origin row-major (y then x), then rotations in 0/90/180/270 order. */
        public Mutation<E> firstFit(E token, Footprint footprint) {
            if (token == null || footprint == null)
                return rejected(Rejection.INVALID_PLACEMENT, null);
            if (placements.containsKey(token))
                return rejected(Rejection.TOKEN_ALREADY_PRESENT, null);
            for (int y = 0; y < storage.height; y++)
                for (int x = 0; x < storage.width; x++) {
                    Cell anchor = new Cell(x, y);
                    for (Rotation rotation : Rotation.values()) {
                        Placement<E> candidate = new Placement<>(token, footprint, anchor, rotation);
                        if (fits(candidate) && !overlaps(candidate)) {
                            Map<E, Placement<E>> next = new LinkedHashMap<>(placements);
                            next.put(token, candidate);
                            return new Mutation<>(new Snapshot<>(storage, next), Rejection.NONE, candidate);
                        }
                    }
                }
            return rejected(Rejection.NO_SPACE, null);
        }

        private boolean overlaps(Placement<E> placement) {
            for (Cell cell : placement.occupiedCells())
                if (occupiedCells.get(cell.y() * storage.width + cell.x())) return true;
            return false;
        }

        private boolean fits(Placement<E> placement) {
            for (Cell relative : placement.footprint().cells(placement.rotation())) {
                Cell cell = translate(placement.anchor(), relative);
                if (cell.x() < 0 || cell.y() < 0 || cell.x() >= storage.width() || cell.y() >= storage.height()
                        || !storage.contains(cell)) return false;
            }
            return true;
        }

        private Mutation<E> rejected(Rejection rejection, Placement<E> placement) {
            return new Mutation<>(this, rejection, placement);
        }
    }

    private static final Comparator<Cell> CELL_ORDER = Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x);

    private static Cell translate(Cell anchor, Cell relative) {
        long x = (long) anchor.x() + relative.x();
        long y = (long) anchor.y() + relative.y();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE)
            throw new ArithmeticException("Translated cell overflows integer coordinates");
        return new Cell((int) x, (int) y);
    }

    private static List<Cell> rotate(List<Cell> cells, Rotation rotation) {
        int maxX = cells.stream().mapToInt(Cell::x).max().orElseThrow();
        int maxY = cells.stream().mapToInt(Cell::y).max().orElseThrow();
        List<Cell> result = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            Cell rotated = switch (rotation) {
                case DEG_0 -> cell;
                case DEG_90 -> new Cell(maxY - cell.y(), cell.x());
                case DEG_180 -> new Cell(maxX - cell.x(), maxY - cell.y());
                case DEG_270 -> new Cell(cell.y(), maxX - cell.x());
            };
            result.add(rotated);
        }
        result.sort(CELL_ORDER);
        return List.copyOf(result);
    }

    private static void validateDimensions(int width, int height) {
        if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT
                || (long) width * height > MAX_CELLS)
            throw new IllegalArgumentException("Storage dimensions exceed provisional limits");
    }

    private static void validateDomainCell(int width, int height, Cell cell) {
        if (cell.x() < 0 || cell.y() < 0 || cell.x() >= width || cell.y() >= height)
            throw new IllegalArgumentException("Storage cell outside domain: " + cell);
    }
}
