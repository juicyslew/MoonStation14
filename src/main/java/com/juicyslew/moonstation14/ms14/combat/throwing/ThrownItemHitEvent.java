package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import java.util.UUID;

/** Server-authoritative contact notification, posted after profile damage is committed. */
public final class ThrownItemHitEvent extends Event {
    private final LooseItemEntity item;
    private final LivingEntity target;
    private final Vec3 contactPoint, incomingVelocity;
    private final UUID throwerUUID;
    private final ThrownItemProfile profile;
    public ThrownItemHitEvent(LooseItemEntity item, LivingEntity target, Vec3 contactPoint, Vec3 incomingVelocity,
                              UUID throwerUUID, ThrownItemProfile profile) {
        this.item=item; this.target=target; this.contactPoint=contactPoint; this.incomingVelocity=incomingVelocity;
        this.throwerUUID=throwerUUID; this.profile=profile;
    }
    public LooseItemEntity item(){return item;} public LivingEntity target(){return target;}
    public Vec3 contactPoint(){return contactPoint;} public Vec3 incomingVelocity(){return incomingVelocity;}
    public UUID throwerUUID(){return throwerUUID;} public ThrownItemProfile profile(){return profile;}
}
