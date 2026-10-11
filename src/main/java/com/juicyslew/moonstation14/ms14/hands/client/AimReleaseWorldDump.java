package com.juicyslew.moonstation14.ms14.hands.client;

import java.util.Objects;
import java.util.UUID;

/** Stateful client intent for the optional loose-container aim-and-release gesture. */
public final class AimReleaseWorldDump {
    public enum Press { CAPTURE, ROUTE_ORIGINAL, IGNORE }
    public enum Release { DUMP, ORIGINAL_ALTERNATE_USE, CANCEL, IGNORE }

    /** Physical mapping gate shared by the live mouse/key listeners and routing tests. */
    public static boolean configuredBindingMatches(boolean mappingMatches, boolean modifierMatches,
                                                    boolean legacyAlternateKeyboardEvent) {
        return mappingMatches && modifierMatches && !legacyAlternateKeyboardEvent;
    }

    private UUID source;
    private UUID gestureId;
    private UUID body;
    private long epoch;
    private boolean aimedElsewhere;
    private boolean modifierUsed;

    /** The live mouse adapter must consume captured presses before examine or vanilla use routing. */
    public static boolean consumesWorldPress(Press press) { return press == Press.CAPTURE; }
    private boolean consumeMouseUp;

    public Press press(boolean alternateAction, boolean pointerOwned, boolean eligibleSource, UUID sourceId,
                       UUID bodyId, long bodyEpoch) {
        if (!alternateAction || pointerOwned) return Press.IGNORE;
        if (!eligibleSource || sourceId == null || bodyId == null || bodyEpoch == 0) return Press.ROUTE_ORIGINAL;
        source = sourceId;
        gestureId = UUID.randomUUID();
        body = bodyId;
        epoch = bodyEpoch;
        aimedElsewhere = false;
        modifierUsed = false;
        consumeMouseUp = true;
        return Press.CAPTURE;
    }

    /**
     * Called with the logical ray destination. This client has no exposed raw-pointer-drag
     * delta hook under locked-camera input, so source-to-different-world-target is the
     * intentional perspective-control adaptation; it has no hold timer or angle threshold.
     */
    public void aim(UUID currentWorldTarget) {
        if (source != null && currentWorldTarget != null && !source.equals(currentWorldTarget)) aimedElsewhere = true;
    }

    public Release release(UUID target, boolean validMachine, UUID currentlyAimedSource,
                           UUID currentCapturedSource, UUID currentBody, long currentEpoch) {
        if (source == null) return Release.IGNORE;
        if (!Objects.equals(source, currentCapturedSource) || !Objects.equals(body, currentBody) || epoch != currentEpoch)
            return finish(Release.CANCEL);
        if (modifierUsed) return finish(Release.CANCEL);
        if (validMachine && target != null && aimedElsewhere) return finish(Release.DUMP);
        if (source.equals(target) && source.equals(currentlyAimedSource) && !aimedElsewhere)
            return finish(Release.ORIGINAL_ALTERNATE_USE);
        return finish(Release.CANCEL);
    }

    public void modifierPressed() { if (source != null) modifierUsed = true; }
    public Release escape() { return source == null ? Release.IGNORE : finish(Release.CANCEL); }
    public Release contextLost() { return source == null ? Release.IGNORE : finish(Release.CANCEL); }
    public void clear() { finish(Release.CANCEL); consumeMouseUp = false; }
    public boolean consumeMouseUp() { boolean consume = consumeMouseUp; consumeMouseUp = false; return consume; }
    public UUID source() { return source; }
    public UUID gestureId() { return gestureId; }
    public boolean active() { return source != null; }

    private Release finish(Release result) {
        source = body = gestureId = null;
        epoch = 0;
        aimedElsewhere = modifierUsed = false;
        return result;
    }
}
