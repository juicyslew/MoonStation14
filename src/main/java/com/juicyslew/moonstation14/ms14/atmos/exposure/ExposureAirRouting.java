package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.disposal.containment.DisposalContainmentService;
import com.juicyslew.moonstation14.ms14.disposal.containment.DisposalContainmentOwner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/** Exact disposal-membership selection shared by physiology exposure consumers. */
public final class ExposureAirRouting {
    private ExposureAirRouting() { }
    public record Selection(boolean contained, Optional<ExposureAirSource> source) { }

    public static Selection select(LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level))
            return new Selection(false, Optional.empty());
        return fromOwner(DisposalContainmentService.forLevel(level).ownerOf(entity));
    }

    public static Selection fromOwner(Optional<DisposalContainmentOwner> owner) {
        if (owner == null || owner.isEmpty()) return new Selection(false, Optional.empty());
        return new Selection(true, owner.get().airSource());
    }
}
