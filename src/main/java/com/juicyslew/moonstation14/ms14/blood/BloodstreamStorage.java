package com.juicyslew.moonstation14.ms14.blood;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.atomic.AtomicBoolean;

/** Owns living reagent routing and one-time migration from the former generic living store. */
public final class BloodstreamStorage {
    private static final AtomicBoolean WARNED_CONFLICT = new AtomicBoolean();

    private BloodstreamStorage() { }

    /**
     * Reconciles old living REAGENT saves without merging solutions. Conflicting populated
     * stores, or a populated legacy store beside an initialized empty stream, fail closed.
     */
    public static boolean reconcile(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return false;
        // A legacy generic solution is not evidence that an entity has blood.
        // Preserve it untouched unless current prototype identity/host mapping opts
        // this living entity into the bloodstream compartment.
        if (BloodSystem.resolvePolicy(entity).isEmpty()) return false;
        if (!BloodSystem.reconcileLegacyPigGuard(entity)) return false;
        boolean hasLegacy = entity.hasData(ModDataAttachments.REAGENT.get());
        boolean hasBloodstream = entity.hasData(ModDataAttachments.BLOODSTREAM.get());
        if (!hasLegacy) return true;

        ReagentAttachment legacy = MS14Provider.getDetached(entity, MS14Bridges.REAGENT);
        ReagentAttachment bloodstream = MS14Provider.getDetached(entity, MS14Bridges.BLOODSTREAM);
        if (!legacy.isEmpty() && !bloodstream.isEmpty()
                && !legacy.snapshotUnits().equals(bloodstream.snapshotUnits())) {
            if (WARNED_CONFLICT.compareAndSet(false, true)) {
                MoonStation14.LOGGER.error("Living entity {} has populated legacy REAGENT and BLOODSTREAM stores; " +
                        "preserving both and disabling reagent routing to avoid loss or duplication", entity.getUUID());
            }
            return false;
        }

        if (!legacy.isEmpty() && hasBloodstream && bloodstream.isEmpty()
                && entity.hasData(ModDataAttachments.BLOOD.get())
                && MS14Provider.getDetached(entity, MS14Bridges.BLOOD).initialized()) {
            // An initialized empty stream may be intentional depletion. Neither store
            // proves which is authoritative, so preserve both rather than resurrecting blood.
            return false;
        }

        // Both stores can be populated when a previous migration committed the
        // bloodstream but was interrupted before deleting the legacy attachment.
        // Only exact cent-for-cent equality proves this is that retry; never merge.

        if (!legacy.isEmpty()) {
            if (!hasBloodstream || bloodstream.isEmpty()) {
                // Attach the exact codec-backed snapshot before removing the old authority.
                MS14Provider.update(entity, MS14Bridges.BLOODSTREAM, legacy);
            }
        }
        entity.removeData(ModDataAttachments.REAGENT.get());
        return true;
    }

    /** Read and migrate without materializing an empty bloodstream. */
    public static ReagentAttachment getDetached(LivingEntity entity) {
        if (!reconcile(entity)) return null;
        return MS14Provider.getDetached(entity, MS14Bridges.BLOODSTREAM);
    }
}
