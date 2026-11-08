package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

/** Pure identity/admission predicates used at session entry and periodic revalidation. */
public final class GasAnalyzerSessionPolicy {
    private GasAnalyzerSessionPolicy() { }
    public record HandSource(String token, boolean analyzerStack) { }
    public static boolean exactHeldSource(String expectedToken, String actualToken, boolean analyzerStack) {
        return expectedToken != null && expectedToken.equals(actualToken) && analyzerStack;
    }
    public static boolean sourceHeldInEitherHand(String expectedToken, java.util.Collection<HandSource> hands) {
        return expectedToken != null && hands != null && hands.stream().anyMatch(hand ->
                exactHeldSource(expectedToken, hand.token(), hand.analyzerStack()));
    }
    public static boolean sameBody(UUID body, long epoch, ResourceLocation dimension,
                                   UUID currentBody, long currentEpoch, ResourceLocation currentDimension) {
        return body != null && body.equals(currentBody) && epoch > 0 && epoch == currentEpoch
                && Objects.equals(dimension, currentDimension);
    }
    public static boolean controlCurrent(UUID expectedSession, long expectedGeneration, long expectedEpoch,
                                         long lastSequence, GasAnalyzerControl control) {
        return control != null && Objects.equals(expectedSession, control.session())
                && expectedGeneration == control.generation() && expectedEpoch == control.epoch()
                && control.sequence() > lastSequence;
    }
    public static boolean validDistance(int distance, GasAnalyzerProfile profile) {
        return profile != null && distance >= 0 && distance <= profile.maximumRange();
    }
    /** Shared server-tick refresh gate: changed selection is immediate, otherwise retain profile cadence. */
    public static boolean needsRefresh(long now,long nextUpdate,GasAnalyzerCellSelection.Result previous,
                                       GasAnalyzerCellSelection.Result current) {
        return !sameSelection(previous,current)||now>=nextUpdate;
    }
    public static boolean sameSelection(GasAnalyzerCellSelection.Result a,GasAnalyzerCellSelection.Result b) {
        return a!=null&&b!=null&&a.status()==b.status()&&Objects.equals(a.cell(),b.cell());
    }
}
