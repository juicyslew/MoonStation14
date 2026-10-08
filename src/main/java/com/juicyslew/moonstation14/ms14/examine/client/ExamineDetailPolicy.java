package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineResponse;
import net.minecraft.world.phys.Vec3;

/** Local fail-closed crop of detail rows, always measured against the response's own selected target. */
public final class ExamineDetailPolicy {
    private ExamineDetailPolicy() { }
    public static boolean mayShow(ExamineResponse response, ExamineSelector.Candidate selected,
                                  Vec3 actorEye, boolean actorAlive) {
        return response != null && response.detailsAllowed() && actorAlive && selected != null
                && selected.target().equals(response.target()) && actorEye != null
                && actorEye.distanceTo(selected.hitPoint()) <= 3.0;
    }

    /** Local range/role crop used by both live rendering and refresh transition detection. */
    public static boolean locallyAllowed(com.juicyslew.moonstation14.ms14.examine.ExaminationPolicy.Examiner examiner,
                                         com.juicyslew.moonstation14.ms14.examine.ExaminationPolicy.State state,
                                         boolean ghost, double distance) {
        return examiner != null && state != null && Double.isFinite(distance)
                && com.juicyslew.moonstation14.ms14.examine.ExaminationPolicy.detailsAllowed(
                examiner, state, ghost, distance, true, true);
    }
}
