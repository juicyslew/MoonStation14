package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import java.util.Objects;
import java.util.function.Consumer;

/** Minimal server firing lifecycle events, scoped to the gun first and owning user second. */
public final class GunAttackEvents {
    private GunAttackEvents() { }
    /** Source order: gun listeners run first; cancellation prevents the user's listener stage. */
    public static boolean dispatchShotAttempted(LivingEntity user, OwnedGunBinding gun, Consumer<ShotAttemptedEvent> dispatcher) {
        var gunEvent = new ShotAttemptedEvent(user, gun, Scope.GUN);
        dispatcher.accept(gunEvent);
        if (gunEvent.isCanceled()) return false;
        var userEvent = new ShotAttemptedEvent(user, gun, Scope.USER);
        dispatcher.accept(userEvent);
        return !userEvent.isCanceled();
    }
    /** Post-cooldown ShootAttempt is gun-scoped only in the source system. */
    public static boolean dispatchAttemptShoot(LivingEntity user, OwnedGunBinding gun, long nextFireTick,
                                                Consumer<AttemptShootEvent> dispatcher) {
        var event = new AttemptShootEvent(user, gun, Scope.GUN, nextFireTick);
        dispatcher.accept(event);
        return !event.isCanceled();
    }
    public enum Scope { GUN, USER }
    public record OwnedGunBinding(LiveOwnedItemAccess.Binding binding, GunWeaponResolver.Selection selection) {
        public OwnedGunBinding { Objects.requireNonNull(binding); Objects.requireNonNull(selection); }
    }
    public static final class ShotAttemptedEvent extends Event implements ICancellableEvent {
        private final LivingEntity user;
        private final OwnedGunBinding gun;
        private final Scope scope;
        public ShotAttemptedEvent(LivingEntity user, OwnedGunBinding gun, Scope scope) {
            this.user = Objects.requireNonNull(user); this.gun = Objects.requireNonNull(gun); this.scope = Objects.requireNonNull(scope);
        }
        public LivingEntity user() { return user; }
        public OwnedGunBinding gun() { return gun; }
        public Scope scope() { return scope; }
    }
    public static final class AttemptShootEvent extends Event implements ICancellableEvent {
        private final LivingEntity user;
        private final OwnedGunBinding gun;
        private final Scope scope;
        private final long nextFireTick;
        public AttemptShootEvent(LivingEntity user, OwnedGunBinding gun, Scope scope, long nextFireTick) {
            this.user = Objects.requireNonNull(user); this.gun = Objects.requireNonNull(gun); this.scope = Objects.requireNonNull(scope);
            this.nextFireTick = nextFireTick;
        }
        public LivingEntity user() { return user; }
        public OwnedGunBinding gun() { return gun; }
        public Scope scope() { return scope; }
        public long nextFireTick() { return nextFireTick; }
    }
    public static final class EmptyGunShotEvent extends Event {
        private final LivingEntity user;
        private final OwnedGunBinding gun;
        public EmptyGunShotEvent(LivingEntity user, OwnedGunBinding gun) { this.user = Objects.requireNonNull(user); this.gun = Objects.requireNonNull(gun); }
        public LivingEntity user() { return user; }
        public OwnedGunBinding gun() { return gun; }
    }
    public static final class GunShotEvent extends Event {
        private final LivingEntity user;
        private final OwnedGunBinding gun;
        public GunShotEvent(LivingEntity user, OwnedGunBinding gun) { this.user = Objects.requireNonNull(user); this.gun = Objects.requireNonNull(gun); }
        public LivingEntity user() { return user; }
        public OwnedGunBinding gun() { return gun; }
    }
}
