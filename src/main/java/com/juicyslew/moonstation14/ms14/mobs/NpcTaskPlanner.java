package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

/** Small ordered task selector; world admission and navigation remain owned by their existing services. */
public final class NpcTaskPlanner {
    private NpcTaskPlanner() { }

    public enum Need { FOOD, DRINK, CLEAN, NONE }

    public record Candidate(Need kind, double distance, double value, boolean livingMob,
                            boolean accessible, boolean supported) {
        public Candidate {
            if (kind == null || !Double.isFinite(distance) || distance < 0 || !Double.isFinite(value) || value < 0)
                throw new IllegalArgumentException("invalid NPC candidate");
        }
    }

    public record Selection(NpcBehaviorComponent.Task task, Candidate target) { }

    /** Food precedes drink exactly as the recursively expanded mouse compound does. */
    public static Optional<Selection> select(NpcBehaviorComponent policy, float hunger, float thirst,
                                              float peckish, float parched, List<Candidate> candidates) {
        for (NpcBehaviorComponent.Task task : policy.tasks()) {
            Need need = switch (task) {
                case FOOD -> hunger <= peckish ? Need.FOOD : null;
                case DRINK -> thirst <= parched ? Need.DRINK : null;
                case CLEAN -> Need.CLEAN;
                case IDLE -> Need.NONE;
                case RETALIATE -> null;
            };
            if (need == null) continue;
            if (need == Need.NONE) return Optional.of(new Selection(task, null));
            Optional<Candidate> target = candidates.stream().filter(candidate -> candidate.kind() == need
                            && candidate.supported() && candidate.accessible() && !candidate.livingMob()
                            && (need == Need.CLEAN || validValue(candidate)) && candidate.distance() < policy.visionRange())
                    .max(Comparator.comparingDouble(candidate -> utility(candidate, policy.visionRange())));
            if (target.isPresent()) return Optional.of(new Selection(task, target.get()));
        }
        return policy.tasks().contains(NpcBehaviorComponent.Task.IDLE)
                ? Optional.of(new Selection(NpcBehaviorComponent.Task.IDLE, null)) : Optional.empty();
    }

    /** Utility's target-distance decay and alive penalty; inaccessible sources are excluded before scoring. */
    public static double utility(Candidate candidate, double vision) {
        if (!candidate.accessible() || !candidate.supported() || candidate.livingMob()
                || !validValue(candidate) || vision <= 0 || candidate.distance() >= vision)
            return Double.NEGATIVE_INFINITY;
        return Math.pow(1d - candidate.distance() / vision, .25d);
    }

    /** Picks among a small nearest-distance food band; eligibility is applied before shortlist construction. */
    public static Optional<Candidate> selectNearbyFood(List<Candidate> candidates, double vision, double randomUnit) {
        List<Candidate> eligible = candidates.stream().filter(candidate -> candidate.kind() == Need.FOOD
                        && Double.isFinite(utility(candidate, vision)))
                .sorted(Comparator.comparingDouble(Candidate::distance)).limit(64).toList();
        if (eligible.isEmpty()) return Optional.empty();
        double nearest = eligible.get(0).distance();
        List<Candidate> shortlist = new ArrayList<>();
        for (Candidate candidate : eligible) {
            if (candidate.distance() > nearest + 2d || shortlist.size() == 8) break;
            shortlist.add(candidate);
        }
        int index = Math.min(shortlist.size() - 1, (int) (Math.max(0d, Math.min(Math.nextDown(1d), randomUnit)) * shortlist.size()));
        return Optional.of(shortlist.get(index));
    }

    private static boolean validValue(Candidate candidate) {
        return candidate.kind() == Need.DRINK ? candidate.value() > 1d : candidate.value() > 0d;
    }
}
