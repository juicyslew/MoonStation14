package com.juicyslew.moonstation14.ms14.animals.flight;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.FlightComponent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/** Shared capability resolver and state transition API for NPC behavior, player input, and presentation. */
public final class FlightSystem {
    private static final com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep WORLD_STEP =
            new com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep();
    private FlightSystem() { }

    @net.neoforged.fml.common.EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
    public static final class Hooks {
        private Hooks() { }
        @net.neoforged.bus.api.SubscribeEvent
        public static void died(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
            LivingEntity body = event.getEntity();
            if (body.level() instanceof net.minecraft.server.level.ServerLevel && capability(body).isPresent())
                setState(body, FlightState.GROUNDED);
        }
    }

    public static Optional<FlightComponent> capability(LivingEntity entity) {
        if (entity == null || entity.isRemoved()) return Optional.empty();
        var resolved = entity.level().isClientSide()
                ? CharacterIdentitySystem.projectForActor(entity)
                : CharacterIdentitySystem.resolveForActor(entity);
        return resolved.flatMap(data -> data.component(FlightComponent.class));
    }
    public static boolean isFlying(LivingEntity entity) {
        return isActivePoweredFlight(entity);
    }
    /** Flight state is active only while the same environmental admission gates as takeoff hold. */
    public static boolean isActivePoweredFlight(LivingEntity entity) {
        return entity != null && state(entity) != FlightState.GROUNDED && takeoffAllowed(entity,
                entity.level().isClientSide(), false);
    }
    /** Drops stale powered-flight state when its eligibility is lost, leaving ordinary gravity in charge. */
    public static boolean normalizeFlightEligibility(LivingEntity entity) {
        if (entity == null) return false;
        if (state(entity) == FlightState.GROUNDED) return false;
        if (isActivePoweredFlight(entity)) return true;
        if (entity.level().isClientSide()) setPredictedState(entity, FlightState.GROUNDED);
        else if (hasServerAuthority(entity)) setState(entity, FlightState.GROUNDED);
        return false;
    }
    public static FlightState state(LivingEntity entity) {
        var attachment = entity.getExistingDataOrNull(ModDataAttachments.FLIGHT.get());
        return attachment == null ? FlightState.GROUNDED : attachment.state();
    }
    public static boolean requestTakeoff(LivingEntity entity) {
        if (!hasServerAuthority(entity) || !takeoffAllowed(entity, false, false)) return false;
        setState(entity, FlightState.FLYING);
        return true;
    }
    /** Portable throw handoff: applies ordinary takeoff gates while exempting only this lease's ownership. */
    public static boolean recoverPortableThrow(LivingEntity entity) {
        if (!hasServerAuthority(entity) || !(entity instanceof net.minecraft.world.entity.Mob mob)
                || !takeoffAllowed(mob, false, true)) return false;
        setState(entity, FlightState.FLYING);
        entity.fallDistance = 0;
        return true;
    }
    /** Client-only local prediction; authoritative attachment synchronization remains server-owned. */
    public static boolean predictTakeoff(LivingEntity entity) {
        if (entity == null || !entity.level().isClientSide() || !takeoffAllowed(entity, true, false))
            return false;
        setPredictedState(entity, FlightState.FLYING);
        return true;
    }
    public static boolean requestLanding(LivingEntity entity) {
        if (!hasServerAuthority(entity) || !landingAllowed(entity, false)) return false;
        setState(entity, FlightState.LANDING);
        return true;
    }
    /** Client-only local prediction for an already flying, otherwise valid body. */
    public static boolean predictLanding(LivingEntity entity) {
        if (entity == null || !entity.level().isClientSide() || !landingAllowed(entity, true))
            return false;
        setPredictedState(entity, FlightState.LANDING);
        return true;
    }

    /** Shared natural-flight movement tick; NPC intent transitions belong to the NPC behavior motor. */
    public static void tick(net.minecraft.server.level.ServerLevel level) {
        if (level == null || level.getServer() == null || !level.getServer().isSameThread()) return;
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof net.minecraft.world.entity.Mob mob) || capability(mob).isEmpty()
                    || mob instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob owner
                        && owner.moonstation14$isMovementOwned()
                    || com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.active(mob)
                    || com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.alreadyAdvancedThisTick(mob)) continue;
            // NPC intent/altitude transitions belong to the NPC behavior motor; this tick only owns
            // autonomous movement for ordinary non-NPC flyers.
            if (mob instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven driven
                    && driven.moonstation14$isNpcMovementDriven()) continue;
            if (isFlying(mob)) WORLD_STEP.stepNaturalFlight(mob);
        }
    }
    public static void setState(LivingEntity entity, FlightState state) {
        if (!hasServerAuthority(entity) || state == null) return;
        writeState(entity, state);
    }
    /** Client-only prediction of landing completion; later server attachment sync reconciles this value. */
    public static boolean predictGrounded(LivingEntity entity) {
        if (entity == null || !entity.level().isClientSide() || capability(entity).isEmpty()) return false;
        setPredictedState(entity, FlightState.GROUNDED);
        return true;
    }
    /** Rebuilds client prediction from an authoritative snapshot; the resolved flight component gates attachment creation. */
    public static boolean reconcilePredictedState(LivingEntity entity, FlightState state) {
        if (entity == null || state == null || !entity.level().isClientSide() || capability(entity).isEmpty()) return false;
        setPredictedState(entity, state);
        return true;
    }
    private static void setPredictedState(LivingEntity entity, FlightState state) {
        writeState(entity, state);
    }
    private static void writeState(LivingEntity entity, FlightState state) {
        var attachment = entity.getData(ModDataAttachments.FLIGHT.get());
        if (attachment.state() != state) {
            attachment.set(state);
            entity.setData(ModDataAttachments.FLIGHT.get(), attachment);
        }
    }
    private static boolean takeoffAllowed(LivingEntity entity, boolean prediction, boolean exemptExternalOwnership) {
        if (entity == null) return false;
        boolean client = entity.level().isClientSide();
        boolean capable = capability(entity).isPresent();
        boolean alive = !entity.isRemoved() && entity.isAlive();
        boolean incapacitated = com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(entity);
        boolean passenger = entity.isPassenger() || entity.getVehicle() != null;
        boolean wet = entity.isInWaterOrBubble() || entity.isInLava();
        boolean external = !exemptExternalOwnership && entity instanceof net.minecraft.world.entity.Mob mob
                && com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.active(mob);
        boolean contained = !prediction && contained(entity);
        return FlightTransitionPolicy.takeoff(client, prediction, capable, alive, !incapacitated,
                !passenger, !wet, !external, !contained);
    }
    private static boolean landingAllowed(LivingEntity entity, boolean prediction) {
        boolean client = entity.level().isClientSide();
        boolean capable = capability(entity).isPresent();
        boolean alive = !entity.isRemoved() && entity.isAlive();
        boolean mobile = !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(entity);
        boolean flying = state(entity) != FlightState.GROUNDED && takeoffAllowed(entity, prediction, false);
        return FlightTransitionPolicy.landing(client, prediction, capable, alive, mobile, flying, true);
    }
    private static boolean contained(LivingEntity entity) {
        return entity.level() instanceof net.minecraft.server.level.ServerLevel level
                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                    .ownerOf(entity).isPresent();
    }
    private static boolean hasServerAuthority(LivingEntity entity) {
        return entity != null && entity.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getServer() != null && level.getServer().isSameThread();
    }
}
