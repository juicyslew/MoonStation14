package com.juicyslew.moonstation14.ms14.disposal.ui;

/** Small deterministic gate shared by the timed server lifecycle and its focused tests. */
public final class DisposalBuildActionPolicy {
    private DisposalBuildActionPolicy() { }
    public static boolean mayComplete(long elapsedTicks, boolean sessionValid, boolean stationary,
                                       boolean hostExposed, boolean recipeResolved, boolean exactSource) {
        return mayComplete(elapsedTicks,20,sessionValid,stationary,hostExposed,recipeResolved,exactSource);
    }
    public static boolean mayComplete(long elapsedTicks,long requiredTicks,boolean sessionValid,boolean stationary,
                                      boolean hostExposed,boolean recipeResolved,boolean exactSource){
        return requiredTicks>=0&&elapsedTicks>=requiredTicks&&sessionValid&&stationary&&hostExposed&&recipeResolved&&exactSource;
    }
}

