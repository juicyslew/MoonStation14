package com.juicyslew.moonstation14.ms14.character.stamina;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import java.util.Objects;

/** Extensible server event for stamina resistance/cancellation; never routed through health damage. */
public final class BeforeStaminaDamageEvent extends Event implements ICancellableEvent {
    private final LivingEntity target;
    private final float originalAmount;
    private float amount;
    public BeforeStaminaDamageEvent(LivingEntity target, float amount) {
        this.target = Objects.requireNonNull(target, "target");
        this.originalAmount = amount;
        this.amount = amount;
    }
    public LivingEntity target() { return target; }
    public float originalAmount() { return originalAmount; }
    public float amount() { return amount; }
    public void setAmount(float value) { amount = value; }
}
