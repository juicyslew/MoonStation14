package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
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
        return fromOwner(BodyContainmentService.forLevel(level).ownerOf(entity));
    }

    public static Selection fromOwner(Optional<ContainedBodyOwner> owner) {
        if (owner == null || owner.isEmpty()) return new Selection(false, Optional.empty());
        return new Selection(true, owner.get().airSource());
    }
}

