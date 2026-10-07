package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Timed core of a disposal-unit self-entry; caller supplies fresh actor authority each advance. */
final class DisposalUnitSelfEntryAttempt {
    enum Result { WAIT, CANCELLED, ENTERED, FAILED }
    enum Reason { NONE, AUTHORITY_CHANGED, UNIT_CHANGED, BODY_CHANGED, CANNOT_ACT, ALREADY_CONTAINED, TOO_FAR, MOVED, INSERT_FAILED }
    record Outcome(Result result, Reason reason) { }

    private final DisposalUnitBlockEntity unit;
    private final UUID unitIdentity;
    private final LivingEntity body;
    private final UUID bodyIdentity;
    private final Vec3 startPosition;
    private final long startedWorldTick;
    private final long requiredTicks;
    private Outcome terminal;

    DisposalUnitSelfEntryAttempt(DisposalUnitBlockEntity unit, LivingEntity body, long startedWorldTick) {
        this.unit = unit;
        this.unitIdentity = unit.identity();
        this.body = body;
        this.bodyIdentity = body.getUUID();
        this.startPosition = body.position();
        this.startedWorldTick = startedWorldTick;
        var prototype = unit.prototype();
        if (prototype == null) throw new IllegalArgumentException("unit has no resolved prototype");
        this.requiredTicks = com.juicyslew.moonstation14.ms14.disposal.domain.DisposalUnitTimingKernel.ticks(prototype.entrySeconds());
    }
    DisposalUnitBlockEntity unit() { return unit; }
    LivingEntity body() { return body; }
    long startedWorldTick() { return startedWorldTick; }
    long requiredTicks() { return requiredTicks; }

    Outcome advance(long now, boolean freshActorContext) {
        if (terminal != null) return terminal;
        Reason reason = Reason.NONE;
        if (!freshActorContext) reason = Reason.AUTHORITY_CHANGED;
        else if (!body.getUUID().equals(bodyIdentity)) reason = Reason.BODY_CHANGED;
        else if (!unit.isValid() || !unit.identity().equals(unitIdentity) || unit.getLevel() != body.level()
                || body.level().getBlockEntity(unit.getBlockPos()) != unit) reason = Reason.UNIT_CHANGED;
        else if (!CharacterControlSystem.canAct(body)) reason = Reason.CANNOT_ACT;
        else if (unit.bodyIds().contains(bodyIdentity) || BodyContainmentService.forLevel((ServerLevel) body.level()).ownerOf(body).isPresent()) reason = Reason.ALREADY_CONTAINED;
        else if (body.distanceToSqr(unit.getBlockPos().getX()+.5, unit.getBlockPos().getY()+.5, unit.getBlockPos().getZ()+.5) > 36) reason = Reason.TOO_FAR;
        else if (!DisposalUnitEntryPolicy.withinMovementThreshold(startPosition, body.position())) reason = Reason.MOVED;
        if (reason != Reason.NONE) return terminal = new Outcome(Result.CANCELLED, reason);
        if (now - startedWorldTick < requiredTicks) return new Outcome(Result.WAIT, Reason.NONE);
        boolean inserted = unit.insertBody(body);
        boolean bound = unit.bodyIds().contains(bodyIdentity)
                && BodyContainmentService.forLevel((ServerLevel) body.level()).ownerOf(body).orElse(null) == unit;
        return terminal = inserted && bound ? new Outcome(Result.ENTERED, Reason.NONE) : new Outcome(Result.FAILED, Reason.INSERT_FAILED);
    }
}
