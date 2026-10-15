package com.juicyslew.moonstation14.ms14.construction.domain;

import java.util.List;

/** Converts the resolved per-item graph path to scheduler ticks without inventing a minimum delay. */
public final class CraftWorkSchedule {
    private CraftWorkSchedule() { }
    public static int durationTicks(List<ConstructionGraph.Step> steps) {
        double seconds=steps.stream().mapToDouble(ConstructionGraph.Step::seconds).sum();
        if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Invalid resolved work duration");
        if(seconds==0)return 0;
        return (int)Math.min(Integer.MAX_VALUE,Math.max(1,Math.ceil(seconds*20)));
    }
}

