package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;

import java.util.List;
import java.util.Objects;

/** Stable tuple-based Shift+scroll candidate cycling; a changed visible set resets to nearest. */
public final class ExamineCandidateCycle {
    private List<ExamineTarget> last = List.of();
    private ExamineTarget selected;
    private ExamineTarget anchor;
    private boolean manualChoice;

    public ExamineTarget update(List<ExamineSelector.Candidate> candidates, int scrollDelta) {
        return update(candidates, null, null, scrollDelta);
    }

    /** Update using logical transparent anchor identity; geometry and candidate ordering may vary frame to frame. */
    public ExamineTarget update(List<ExamineSelector.Candidate> candidates, ExamineTarget transparentAnchor,
                                ExamineTarget preferredTarget, int scrollDelta) {
        List<ExamineSelector.Candidate> available = candidates == null ? List.of() : candidates;
        List<ExamineTarget> current = available.stream().map(ExamineSelector.Candidate::target).distinct().toList();
        if (current.isEmpty()) { clear(); return null; }
        boolean anchorChanged = !Objects.equals(anchor, transparentAnchor);
        boolean selectionMissing = selected != null && !current.contains(selected);
        boolean reset = anchorChanged || selectionMissing;
        boolean wasManual = manualChoice;
        if (reset) manualChoice = false;
        if (reset || selected == null) {
            selected = preferredOpaque(available, preferredTarget);
        } else if (!wasManual && scrollDelta == 0) {
            selected = preferredOpaque(available, preferredTarget);
        }
        if (!reset && current.size() > 1 && scrollDelta != 0) {
            int index = current.indexOf(selected);
            selected = current.get(Math.floorMod(index - Integer.signum(scrollDelta), current.size()));
        }
        if (!reset && scrollDelta != 0) manualChoice = true;
        last = List.copyOf(current);
        anchor = transparentAnchor;
        return selected;
    }

    public void clear() { last = List.of(); selected = null; anchor = null; manualChoice = false; }
    public void resetAutomatic() { manualChoice = false; selected = null; }
    public ExamineTarget selected() { return selected; }

    private static ExamineTarget preferredOpaque(List<ExamineSelector.Candidate> candidates, ExamineTarget preferred) {
        if (preferred != null && candidates.stream().anyMatch(c -> c.target().equals(preferred) && !c.transparent()))
            return preferred;
        return candidates.stream().filter(c -> !c.transparent()).map(ExamineSelector.Candidate::target)
                .findFirst().orElse(candidates.getFirst().target());
    }
}
