package com.juicyslew.moonstation14.ms14.explosion.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure bounded planner using cardinal 3D shells and shared half-slope steps. It is not exact SS14 2D parity
 * (diagonals are absent). Solid tolerance is caller supplied damage tolerance, not block hardness. No chunks load.
 */
public final class BlastPlanner {
    /** Smallest accepted per-cell increment; keeps half-slope and termination arithmetic meaningful. */
    public static final double MIN_INTENSITY = 1e-12;
    public static final int MAX_VISITED = 256;
    public static final int MAX_EXPANSION_ITERATIONS = 32;
    public static final double REFERENCE_BUDGET = 120.0;
    public static final double REFERENCE_SLOPE = 3.0;
    public static final double REFERENCE_CAP = 10.0;
    private static final int[][] FACES = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

    private BlastPlanner() { }
    public enum Cell { OPEN, SOLID, UNKNOWN }
    public enum Status { COMPLETE, VISIT_CAP, ITERATION_CAP, INVALID_SOURCE }
    @FunctionalInterface public interface Sampler { Cell sample(Pos position); }
    /** Solid tolerance is finite positive damage to breach, or positive infinity for immune solids. */
    @FunctionalInterface public interface ToleranceSampler { Sample sample(Pos position); }
    public record Sample(Cell cell, double tolerance) {
        public Sample {
            if (cell == null) throw new IllegalArgumentException("Cell cannot be null");
            if (cell == Cell.SOLID && (Double.isNaN(tolerance) || tolerance <= 0 || tolerance == Double.NEGATIVE_INFINITY))
                throw new IllegalArgumentException("Solid tolerance must be positive or positive infinity");
        }
        public static Sample open() { return new Sample(Cell.OPEN, Double.POSITIVE_INFINITY); }
        public static Sample unknown() { return new Sample(Cell.UNKNOWN, Double.POSITIVE_INFINITY); }
        public static Sample solid(double tolerance) { return new Sample(Cell.SOLID, tolerance); }
    }
    public record Pos(int x, int y, int z) { }
    public record Proposal(double totalIntensity, double slope, double maxCellIntensity) {
        public Proposal {
            if (!Double.isFinite(totalIntensity) || totalIntensity <= 0 || !Double.isFinite(slope) || slope <= 0
                    || !Double.isFinite(maxCellIntensity) || maxCellIntensity <= 0)
                throw new IllegalArgumentException("Intensity, slope and cap must be finite and positive");
            if (totalIntensity > 1_000_000 || slope > 1_000_000 || maxCellIntensity > 1_000_000)
                throw new IllegalArgumentException("Proposal exceeds hard numeric bounds");
            if (slope / 2 < MIN_INTENSITY || maxCellIntensity < MIN_INTENSITY)
                throw new IllegalArgumentException("Slope step and cell cap must be at least the minimum intensity");
        }
    }
    public record Plan(Status status, Map<Pos, Double> intensities, Set<Pos> solids, Set<Pos> breached,
                       int inspected, int visited, int expansionIterations, double intensityUsed) {
        public Plan {
            intensities = Collections.unmodifiableMap(new LinkedHashMap<>(intensities));
            solids = Collections.unmodifiableSet(new LinkedHashSet<>(solids));
            breached = Collections.unmodifiableSet(new LinkedHashSet<>(breached));
        }
        public boolean successful() { return status == Status.COMPLETE; }
    }

    public static Plan plan(Pos source, Proposal proposal, Sampler sampler) {
        if (sampler == null) throw new IllegalArgumentException("Arguments cannot be null");
        return planWithTolerance(source, proposal, p -> {
            Cell cell = sampler.sample(p);
            return cell == Cell.SOLID ? Sample.solid(Double.POSITIVE_INFINITY)
                    : cell == Cell.OPEN ? Sample.open() : Sample.unknown();
        });
    }

    public static Plan planWithTolerance(Pos source, Proposal proposal, ToleranceSampler sampler) {
        if (source == null || proposal == null || sampler == null) throw new IllegalArgumentException("Arguments cannot be null");
        Sample initial = sampler.sample(source);
        if (initial == null || initial.cell() != Cell.OPEN) return new Plan(Status.INVALID_SOURCE, Map.of(), Set.of(), Set.of(), 1, 0, 0, 0);
        double step = proposal.slope() / 2;
        // SS14's single-tile path returns the complete budget before applying maxIntensity.
        if (proposal.totalIntensity() < step) {
            return new Plan(Status.COMPLETE, Map.of(source, proposal.totalIntensity()), Set.of(), Set.of(),
                    1, 1, 0, proposal.totalIntensity());
        }
        double remaining = proposal.totalIntensity();
        LinkedHashMap<Pos, Double> values = new LinkedHashMap<>();
        LinkedHashSet<Pos> solids = new LinkedHashSet<>();
        LinkedHashSet<Pos> breached = new LinkedHashSet<>();
        LinkedHashSet<Pos> released = new LinkedHashSet<>();
        LinkedHashMap<Pos, Double> tolerances = new LinkedHashMap<>();
        LinkedHashSet<Pos> seen = new LinkedHashSet<>();
        List<List<Pos>> shells = new ArrayList<>();
        List<Pos> frontier = new ArrayList<>();
        List<Pos> pendingBreaches = new ArrayList<>();
        values.put(source, Math.min(Math.min(step, proposal.maxCellIntensity()), remaining));
        remaining -= values.get(source);
        seen.add(source);
        frontier.add(source);
        shells.add(new ArrayList<>(frontier));
        int inspected = 1, iterations = 0;

        for (int iteration = 1; iteration <= MAX_EXPANSION_ITERATIONS && remaining > 1e-12; iteration++) {
            iterations = iteration;
            // Increase each discovered shell in order, distributing every increment evenly within that shell.
            for (List<Pos> members : shells) {
                remaining = raise(members, values, remaining, step, proposal.maxCellIntensity());
                if (remaining <= 1e-12) break;
            }
            if (iteration % 2 == 0 && remaining > 1e-12 && (!frontier.isEmpty() || !pendingBreaches.isEmpty())) {
                LinkedHashSet<Pos> candidates = new LinkedHashSet<>();
                List<Pos> parents = new ArrayList<>(frontier);
                parents.addAll(pendingBreaches);
                pendingBreaches.clear();
                for (Pos parent : parents) for (int[] face : FACES) {
                    Pos next = offset(parent, face);
                    if (next != null && !seen.contains(next)) candidates.add(next);
                }
                if (!candidates.isEmpty()) {
                    if (seen.size() + candidates.size() > MAX_VISITED) return failed(Status.VISIT_CAP, inspected, seen.size(), iteration);
                    List<Pos> reached = new ArrayList<>();
                    List<Pos> nextFrontier = new ArrayList<>();
                    for (Pos p : candidates) {
                        seen.add(p);
                        Sample sample = sampler.sample(p); inspected++;
                        if (sample == null || sample.cell() == Cell.UNKNOWN) continue;
                        reached.add(p);
                        if (sample.cell() == Cell.SOLID) { solids.add(p); tolerances.put(p, sample.tolerance()); }
                        else nextFrontier.add(p);
                    }
                    frontier = nextFrontier;
                    if (!reached.isEmpty()) {
                        double amount = Math.min(Math.min(step, proposal.maxCellIntensity()), remaining / reached.size());
                        for (Pos p : reached) values.put(p, amount);
                        remaining -= amount * reached.size();
                        shells.add(new ArrayList<>(reached));
                    }
                } else frontier = List.of();
            }
            for (Pos p : solids) if (!breached.contains(p) && values.get(p) >= tolerances.get(p)) breached.add(p);
            for (Pos p : breached) if (released.add(p)) pendingBreaches.add(p);
            if (remaining <= 1e-12) break;
        }
        double used = values.values().stream().mapToDouble(Double::doubleValue).sum();
        if (remaining > 1e-12 && hasWork(shells, frontier, pendingBreaches, seen, values, proposal.maxCellIntensity()))
            return failed(Status.ITERATION_CAP, inspected, seen.size(), iterations);
        return new Plan(Status.COMPLETE, values, solids, breached, inspected, seen.size(), iterations, used);
    }

    private static double raise(List<Pos> shell, Map<Pos, Double> values, double remaining, double step, double cap) {
        if (shell.isEmpty() || remaining <= 0) return remaining;
        double minRoom = shell.stream().mapToDouble(p -> cap - values.getOrDefault(p, 0.0)).min().orElse(0);
        double add = Math.min(Math.min(step, minRoom), remaining / shell.size());
        for (Pos p : shell) values.put(p, values.get(p) + add);
        return Math.max(0, remaining - add * shell.size());
    }
    private static boolean hasWork(List<List<Pos>> shells, List<Pos> frontier, List<Pos> pendingBreaches, Set<Pos> seen,
                                   Map<Pos, Double> values, double cap) {
        for (List<Pos> shell : shells)
            for (Pos p : shell) if (cap - values.getOrDefault(p, 0.0) > 1e-12) return true;
        List<Pos> parents = new ArrayList<>(frontier);
        parents.addAll(pendingBreaches);
        for (Pos parent : parents) for (int[] face : FACES) {
            Pos next = offset(parent, face);
            if (next != null && !seen.contains(next)) return true;
        }
        return false;
    }
    private static Plan failed(Status status, int inspected, int visited, int iterations) {
        return new Plan(status, Map.of(), Set.of(), Set.of(), inspected, visited, iterations, 0);
    }
    private static Pos offset(Pos p, int[] d) {
        long x=(long)p.x()+d[0], y=(long)p.y()+d[1], z=(long)p.z()+d[2];
        if (x<Integer.MIN_VALUE||x>Integer.MAX_VALUE||y<Integer.MIN_VALUE||y>Integer.MAX_VALUE||z<Integer.MIN_VALUE||z>Integer.MAX_VALUE) return null;
        return new Pos((int)x,(int)y,(int)z);
    }
}

