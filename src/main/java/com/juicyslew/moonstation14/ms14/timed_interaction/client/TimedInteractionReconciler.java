package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Engine-free state owner for timed snapshot acceptance and progress-only local predictions. */
public final class TimedInteractionReconciler {
    private record Prediction(UUID body, ResourceLocation dimension, int duration, long created) { }
    private final Map<Integer, TimedInteractionSnapshot> snapshots = new HashMap<>();
    private final Map<Long, Prediction> predictions = new HashMap<>();

    public record Acceptance(boolean accepted, boolean refreshInventory) { }
    public record Provisional(int durationTicks, long elapsedTicks) { }

    public Acceptance accept(TimedInteractionSnapshot incoming, ResourceLocation dimension,
                             UUID entityBody, UUID ownedBody, ResourceLocation ownedDimension) {
        if (incoming == null || dimension == null || !incoming.dimension().equals(dimension)) return new Acceptance(false, false);
        if (entityBody != null && !entityBody.equals(incoming.bodyUuid())) return new Acceptance(false, false);
        TimedInteractionSnapshot old = snapshots.get(incoming.bodyEntityId());
        if (old != null && old.bodyUuid().equals(incoming.bodyUuid())
                && (incoming.revision() < old.revision()
                || incoming.revision() == old.revision() && incoming.serverTick() < old.serverTick()))
            return new Acceptance(false, false);
        if (old != null && !old.bodyUuid().equals(incoming.bodyUuid()) && entityBody == null)
            return new Acceptance(false, false);
        boolean refresh = TimedInteractionInventoryRefreshPolicy.shouldRefresh(old, incoming, ownedBody, ownedDimension);
        incoming.views().forEach(view -> {
            Prediction prediction = predictions.get(view.originatingActionSequence());
            if (prediction != null && prediction.dimension().equals(incoming.dimension())
                    && TimedInteractionPredictionIdentity.matches(prediction.body(),
                    view.originatingActionSequence(), incoming.bodyUuid(), view.originatingActionSequence()))
                predictions.remove(view.originatingActionSequence());
        });
        snapshots.put(incoming.bodyEntityId(), incoming);
        return new Acceptance(true, refresh);
    }

    public void predictStart(long sequence, UUID body, ResourceLocation dimension, int duration, long now) {
        if (body == null || dimension == null || sequence <= 0 || duration <= 0) return;
        TimedInteractionSnapshot auth = snapshots.values().stream().filter(s -> s.bodyUuid().equals(body)).findFirst().orElse(null);
        if (auth != null && auth.views().stream().anyMatch(v -> v.phase() == TimedOperation.Phase.ACTIVE)) return;
        predictions.put(sequence, new Prediction(body, dimension, duration, now));
    }

    public void actionResult(long sequence, boolean accepted) { if (!accepted) predictions.remove(sequence); }

    public Map<Long, Provisional> provisionalView(UUID body, long now) {
        Map<Long, Provisional> result = new HashMap<>();
        predictions.forEach((sequence, p) -> { if (p.body().equals(body)) result.put(sequence, new Provisional(p.duration(), now - p.created())); });
        return Map.copyOf(result);
    }

    public void tick(long now, UUID ownedBody) {
        predictions.entrySet().removeIf(e -> now - e.getValue().created() > 80
                || !e.getValue().body().equals(ownedBody));
    }

    public void clear() { snapshots.clear(); predictions.clear(); }
    public void clearPredictions() { predictions.clear(); }
    public Map<Integer, TimedInteractionSnapshot> snapshots() { return Map.copyOf(snapshots); }
    public TimedInteractionSnapshot snapshot(int entityId) { return snapshots.get(entityId); }
    public void forgetSnapshot(int entityId) { snapshots.remove(entityId); }
}
