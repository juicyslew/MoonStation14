package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Production ordering/cap policy, exposed as a pure helper for deterministic tests. */
public final class ExamineCandidateOrdering {
    private ExamineCandidateOrdering() { }
    public static List<ExamineSelector.Candidate> nearest(List<ExamineSelector.Candidate> input, int maximum) {
        if (input == null || input.isEmpty() || maximum <= 0) return List.of();
        ArrayList<ExamineSelector.Candidate> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparingDouble(ExamineSelector.Candidate::distance)
                .thenComparing(c -> c.target().kind().ordinal())
                .thenComparing(c -> switch (c.target().kind()) {
                    case BLOCK -> c.target().blockPos().toShortString();
                    case ENTITY -> c.target().entityUuid().toString();
                    case SCENE -> c.target().sceneId().toString();
                }));
        return List.copyOf(sorted.subList(0, Math.min(maximum, sorted.size())));
    }
}
