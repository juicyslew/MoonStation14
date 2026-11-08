package com.juicyslew.moonstation14.ms14.combat.throwing;

import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;
import net.neoforged.bus.api.Event;

/** Posted after a thrown item has become attached to its contact target. */
public final class ThrownItemEmbeddedEvent extends Event {
    private final com.juicyslew.moonstation14.entities.LooseItemEntity item;
    private final LivingEntity target;
    private final ThrownItemProfile profile;
    private final UUID throwerUUID;
    public ThrownItemEmbeddedEvent(com.juicyslew.moonstation14.entities.LooseItemEntity item,
            LivingEntity target, ThrownItemProfile profile, UUID throwerUUID) {
        this.item=item; this.target=target; this.profile=profile; this.throwerUUID=throwerUUID;
    }
    public com.juicyslew.moonstation14.entities.LooseItemEntity item(){return item;}
    public LivingEntity target(){return target;}
    public ThrownItemProfile profile(){return profile;}
    public UUID throwerUUID(){return throwerUUID;}
}
