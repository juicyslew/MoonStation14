package com.juicyslew.moonstation14.ms14.singularity.containment;

import java.util.*;

/** Per-level recipient delivery state; stores exactly the last snapshot sent to each observer. */
public final class FieldFaceRecipientSnapshots {
    private final Map<UUID,List<FieldFaceSnapshotPayload.Face>> snapshots=new HashMap<>();
    public Optional<List<FieldFaceSnapshotPayload.Face>> update(UUID recipient, boolean relevant,
            List<FieldFaceSnapshotPayload.Face> authoritativeFaces) {
        List<FieldFaceSnapshotPayload.Face> desired=relevant?List.copyOf(authoritativeFaces):List.of();
        List<FieldFaceSnapshotPayload.Face> previous=snapshots.put(recipient,desired);
        return previous==null||!previous.equals(desired)?Optional.of(desired):Optional.empty();
    }
    public void remove(UUID recipient){snapshots.remove(recipient);}
    public void clear(){snapshots.clear();}
}
