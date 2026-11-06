package com.juicyslew.moonstation14.ms14.shuttle.sable;

import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable bounded physical-input capture used by the real impact probe and its pure comparison tests. */
public record HullPhysicalSnapshot(List<Cell> cells) {
    public static final int MAX_CELLS = 8 * 7 * 13;

    public HullPhysicalSnapshot {
        cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        if (cells.isEmpty() || cells.size() > MAX_CELLS) throw new IllegalArgumentException("physical snapshot cell count out of bounds");
        if (cells.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("physical snapshot contains null cell");
        var offsets = new HashSet<Offset>(cells.size());
        for (Cell cell : cells)
            if (!offsets.add(new Offset(cell.x(),cell.y(),cell.z())))
                throw new IllegalArgumentException("physical snapshot contains duplicate relative cell coordinates: "
                        +cell.x()+","+cell.y()+","+cell.z());
    }

    /** Returns a bounded first difference, or null when all normalized physical inputs agree. */
    public String mismatch(HullPhysicalSnapshot other) {
        if (other == null || cells.size() != other.cells.size())
            return "cell-count expected=" + cells.size() + " actual=" + (other == null ? "null" : other.cells.size());
        for (int i = 0; i < cells.size(); i++) {
            Cell expected = cells.get(i), actual = other.cells.get(i);
            if (!expected.equals(actual)) return "offset=" + expected.x() + "," + expected.y() + "," + expected.z()
                    + " expected=" + expected + " actual=" + actual;
        }
        return null;
    }

    public int fingerprint() { return cells.hashCode(); }

    /** Deterministic translation-independent digest of the complete canonical input record. */
    public String sha256Fingerprint() {
        try {
            var bytes=new ByteArrayOutputStream();
            var output=new DataOutputStream(bytes);
            var ordered=cells.stream().sorted(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y)
                    .thenComparingInt(Cell::z)).toList();
            output.writeInt(ordered.size());
            for(Cell cell:ordered) {
                output.writeInt(cell.x()); output.writeInt(cell.y()); output.writeInt(cell.z());
                byte[] state=cell.state().getBytes(StandardCharsets.UTF_8);
                output.writeInt(state.length); output.write(state);
                output.writeInt(cell.boxes().size());
                for(Box box:cell.boxes()) {
                    output.writeLong(Double.doubleToLongBits(box.minX()));
                    output.writeLong(Double.doubleToLongBits(box.minY()));
                    output.writeLong(Double.doubleToLongBits(box.minZ()));
                    output.writeLong(Double.doubleToLongBits(box.maxX()));
                    output.writeLong(Double.doubleToLongBits(box.maxY()));
                    output.writeLong(Double.doubleToLongBits(box.maxZ()));
                }
            }
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return HexFormat.of().formatHex(digest);
        } catch(IOException|NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("required SHA-256 snapshot fingerprint unavailable",impossible);
        }
    }

    public static Cell cell(int x,int y,int z,String state,List<Box> boxes) {
        return new Cell(x,y,z,state,canonicalBoxes(boxes));
    }

    public static Cell cellAtWorld(int worldX,int worldY,int worldZ,int originX,int originY,int originZ,
                                   String state,List<Box> boxes) {
        return cell(Math.subtractExact(worldX,originX),Math.subtractExact(worldY,originY),
                Math.subtractExact(worldZ,originZ),state,boxes);
    }

    public static List<Box> canonicalBoxes(List<Box> boxes) {
        var result = new ArrayList<>(Objects.requireNonNull(boxes, "boxes"));
        result.sort(Comparator.comparingDouble(Box::minX).thenComparingDouble(Box::minY)
                .thenComparingDouble(Box::minZ).thenComparingDouble(Box::maxX)
                .thenComparingDouble(Box::maxY).thenComparingDouble(Box::maxZ));
        return List.copyOf(result);
    }

    public record Cell(int x,int y,int z,String state,List<Box> boxes) {
        public Cell {
            Objects.requireNonNull(state, "state");
            boxes = canonicalBoxes(boxes);
        }
    }

    private record Offset(int x,int y,int z) { }

    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        public Box {
            if (!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)
                    ||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)
                    ||minX>maxX||minY>maxY||minZ>maxZ)
                throw new IllegalArgumentException("invalid physical collision box");
        }
    }
}
