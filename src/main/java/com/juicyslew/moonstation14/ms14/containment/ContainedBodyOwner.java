package com.juicyslew.moonstation14.ms14.containment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/** Exact runtime owner of a contained body; implementations are unit or transit-holder runtimes. */
public interface ContainedBodyOwner {
    enum Stage { STATIONARY, TRANSIT, /** Legacy stationary owner name retained for disposal contracts. */ UNIT }

    UUID identity();
    ServerLevel level();
    Vec3 position();
    /** Position assigned to a particular contained body's feet; transit owners may align its eyes to the bore. */
    default Vec3 positionFor(LivingEntity body) { return position(); }
    /** Shared Minecraft camera viewpoint for occupants whose native eye heights differ. */
    static Vec3 positionForViewpoint(Vec3 viewpoint, LivingEntity body) {
        return positionForViewpoint(viewpoint, body.getEyeHeight());
    }
    static Vec3 positionForViewpoint(Vec3 viewpoint, double eyeHeight) {
        return viewpoint.add(0, net.minecraft.world.entity.player.Player.DEFAULT_EYE_HEIGHT - eyeHeight, 0);
    }
    Stage stage();
    boolean isValid();

    /** Commit a prepared whole-stack admission after its exact body hand has been cleared. */
    default boolean acceptDroppedItem(com.juicyslew.moonstation14.ms14.hands.ItemToken token,
                                      com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation source,
                                      net.minecraft.world.item.ItemStack stack) { return false; }


    /** Immutable presentation observation for the exact body/owner relation. */
    default ContainedBodyView view() {
        return new ContainedBodyView(identity(), java.util.Optional.empty(), true);
    }
    /** Body-specific presentation; custody identity remains the owner's exact ledger token. */
    default ContainedBodyView viewFor(LivingEntity body) { return view(); }

    /** Preserve unrelated payloads before a contained body detonates; fixture owners own nothing by default. */
    default void preserveContentsBeforeExplosion() { }

    /**
     * Finite air carried by this owner, when available. An empty result is deliberately
     * distinct from vacuum: a contained body with no sample must not fall back to the room.
     */
    default java.util.Optional<com.juicyslew.moonstation14.ms14.atmos.exposure.ExposureAirSource> airSource() {
        return java.util.Optional.empty();
    }

    /** Called for a voluntary movement escape request; timing and eligibility belong to the owner. */
    boolean tryEscape(LivingEntity body);

    /** Controlled-input provenance; autonomous and legacy callers use sequence zero by default. */
    default boolean tryEscape(LivingEntity body, long originSequence) { return tryEscape(body); }
}
