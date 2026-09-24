package com.juicyslew.moonstation14.ms14.stomach;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Routes non-digestion stomach reagents into the shared body reagent compartment. */
public final class StomachDigestionTransfer {
    public static final float TRANSFER_RATE = 0.25f;
    public static final float TRANSFER_EFFICACY = 0.5f;

    private StomachDigestionTransfer() { }

    /**
     * Transfers up to 25 cents from each applicable stomach reagent. Efficacy is floored
     * in cents, so an odd source amount intentionally loses its final half-cent (e.g.
     * 25 source cents produces 12 body cents). A source amount producing zero body cents
     * is retained as terminal residue for a later wash/flush pathway.
     */
    public static void transfer(ReagentAttachment stomach, ReagentAttachment body,
                                Map<ResourceLocation, ReagentData> catalog, float bodyCapacity) {
        Objects.requireNonNull(stomach, "stomach");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(catalog, "catalog");
        if (!Float.isFinite(bodyCapacity) || bodyCapacity < 0f) {
            throw new IllegalArgumentException("body capacity must be finite and nonnegative");
        }

        Map<ResourceKey<ReagentData>, Long> sourceSnapshot = stomach.snapshotUnits();
        List<ResourceKey<ReagentData>> candidates = new ArrayList<>(sourceSnapshot.keySet());
        candidates.sort(Comparator.comparing(key -> key.location().toString()));
        long capacityCents = ReagentUnits.fromFloat(bodyCapacity);

        for (ResourceKey<ReagentData> key : candidates) {
            long available = stomach.snapshotUnits().getOrDefault(key, 0L);
            if (available <= 0) continue;
            ReagentData prototype = catalog.get(key.location());
            if (prototype == null || prototype.metabolisms().containsKey(MetabolismStage.DIGESTION)) continue;

            long requestedSource = Math.min(available, 25L);
            long requestedBody = requestedSource / 2;
            if (requestedBody == 0) continue;

            ReagentAttachment stagedBody = new ReagentAttachment(body.toComponent());
            long bodyBefore = stagedBody.totalUnits();
            if (bodyBefore > capacityCents) throw new IllegalArgumentException("stored total exceeds capacity");
            long free = capacityCents - bodyBefore;
            // floor(source / 2) <= free iff source <= 2*free+1. Keep the
            // largest feasible source, bounded by this tick's request.
            long sourceRemoval = Math.min(requestedSource, free * 2 + 1);
            long bodyProduct = sourceRemoval / 2;
            if (bodyProduct == 0) continue;
            long admitted = stagedBody.admitUnits(key, bodyProduct, capacityCents);
            if (admitted != bodyProduct) throw new IllegalStateException("staged body admission changed");

            ReagentAttachment stagedStomach = new ReagentAttachment(stomach.toComponent());
            if (stagedStomach.removeUnits(key, sourceRemoval) != sourceRemoval) continue;

            // Commit body first. If capacity validation fails, the source has not changed.
            long committed = body.admitUnits(key, admitted, capacityCents);
            if (committed != admitted) {
                throw new IllegalStateException("body transfer admission changed after preview");
            }
            long removed = stomach.removeUnits(key, sourceRemoval);
            if (removed != sourceRemoval) {
                throw new IllegalStateException("stomach transfer source changed after preview");
            }
        }
    }
}
