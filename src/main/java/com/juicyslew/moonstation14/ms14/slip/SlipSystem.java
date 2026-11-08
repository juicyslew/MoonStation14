package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.NoSlipComponent;
import com.juicyslew.moonstation14.ms14.character.components.StandingStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.StunnableComponent;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.juicyslew.moonstation14.ms14.standing.PostureAttachment;
import com.juicyslew.moonstation14.ms14.standing.PostureData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** Authoritative contact admission and response for populated puddles. */
public final class SlipSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlipSystem.class);
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> KNOCKDOWN =
            ModStatusEffects.createKey("knockdown");
    private static final List<Predicate<SlipAttempt>> ATTEMPT_GATES = new CopyOnWriteArrayList<>();
    private static final List<java.util.function.Consumer<SlipEvent>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final int MAX_HOOKS = 64;
    // Fail closed for malformed/custom entity AABBs. A normal .6x1.8 body overlaps
    // at most 2x2x3 cells; 16 permits boundary rounding without unbounded traversal.

    private SlipSystem() { }

    /** Extension seams mirror target SlipAttemptEvent and source SlipCausingAttemptEvent. */
    public static boolean addAttemptGate(Predicate<SlipAttempt> gate) {
        if (ATTEMPT_GATES.size() >= MAX_HOOKS) return false;
        return ATTEMPT_GATES.add(java.util.Objects.requireNonNull(gate));
    }

    /** Removes a temporary attempt gate after its owning test or extension is finished. */
    public static boolean removeAttemptGate(Predicate<SlipAttempt> gate) {
        return ATTEMPT_GATES.remove(gate);
    }
    /** Slip-event consumers (including future reactive chemistry) run before movement/status. */
    public static boolean addListener(java.util.function.Consumer<SlipEvent> listener) {
        if (LISTENERS.size() >= MAX_HOOKS) return false;
        return LISTENERS.add(java.util.Objects.requireNonNull(listener));
    }

    /** Removes a temporary observer after its owning test or extension is finished. */
    public static boolean removeListener(java.util.function.Consumer<SlipEvent> listener) {
        return LISTENERS.remove(listener);
    }

    public static void onPuddleContact(ServerLevel level, BlockPos position, Entity entity) {
        // Player velocity is not authoritative for ordinary network movement. Players are
        // admitted only from the accepted ServerGamePacketListener movement boundary below.
        if (entity instanceof ServerPlayer) return;
        // Entity.move calls BlockState.entityInside inline. An owned harness mob's motor
        // publishes its accepted velocity only after that call returns, so defer contact
        // until the owned movement boundary below.
        if (entity instanceof Mob mob && mob instanceof MindControlledMob owner
                && owner.moonstation14$isMovementOwned()) return;
        if (level.isClientSide || !(entity instanceof LivingEntity target) || !entity.isAlive()
                || entity.level() != level || !level.hasChunkAt(position)
                || !level.getBlockState(position).is(ModBlocks.PUDDLE.get())
                || !(level.getBlockEntity(position) instanceof PuddleBlockEntity puddle)) return;
        Vec3 velocity = target.getDeltaMovement();
        SharedPuddleContactQuery.Result result = SharedPuddleContactQuery.query(target, target.getBoundingBox());
        if (!result.available()) return;
        for (SharedPuddleContactQuery.Contact contact : result.contacts()) {
            if (contact.sourcePosition().equals(position))
                admitContact(level, contact, target, velocity, velocity.length() * 20d);
        }
    }

    /**
     * Server packet boundary entry: displacement is read from ServerPlayer's accepted
     * known movement, never from the untrusted proposed packet destination.
     */
    public static void onAcceptedPlayerMovement(ServerPlayer player, Vec3 acceptedDisplacement) {
        if (player.level().isClientSide || !(player.level() instanceof ServerLevel level)
                || player.isPassenger() || !player.isAlive() || acceptedDisplacement == null) return;
        double lengthSqr = acceptedDisplacement.lengthSqr();
        // Reject rotation-only packets and discontinuous corrections/teleports.
        if (!Double.isFinite(lengthSqr) || lengthSqr <= 1.0e-8d || lengthSqr > 2.25d) return;

        admitEndpointContacts(level, player, acceptedDisplacement, acceptedDisplacement, lengthSqr);
    }

    /** Server-authoritative post-motor contact entry for movement-owned mobs only. */
    public static void onAcceptedHarnessMovement(Mob body, Vec3 acceptedDisplacement) {
        if (!(body instanceof MindControlledMob owner) || !owner.moonstation14$isMovementOwned()
                || body.level().isClientSide || !(body.level() instanceof ServerLevel level)
                || body.isPassenger() || !body.isAlive() || acceptedDisplacement == null) return;
        double lengthSqr = acceptedDisplacement.lengthSqr();
        if (!Double.isFinite(lengthSqr) || lengthSqr <= 1.0e-8d || lengthSqr > 2.25d) return;
        admitEndpointContacts(level, body, acceptedDisplacement, body.getDeltaMovement(), lengthSqr);
        ItemStepContactSystem.scan(level, body, body.getBoundingBox(), body.getDeltaMovement(),
                Math.sqrt(lengthSqr) * 20d, context -> {
                    com.juicyslew.moonstation14.ms14.loose_objects.ItemStepDamageSystem.onContact(context);
                    onItemContact(context);
                });
    }

    private static void admitEndpointContacts(ServerLevel level, LivingEntity body,
                                              Vec3 acceptedDisplacement, Vec3 launchVelocity, double lengthSqr) {
        SharedPuddleContactQuery.Result result = SharedPuddleContactQuery.query(body, body.getBoundingBox());
        if (result.available()) {
            StepContactEpisodes.reconcile(level, body, body.getBoundingBox(), result);
            for (SharedPuddleContactQuery.Contact contact : result.contacts())
                admitContact(level, contact, body, launchVelocity, Math.sqrt(lengthSqr) * 20d);
        }
    }

    private static void admitContact(ServerLevel level, SharedPuddleContactQuery.Contact contact, LivingEntity target,
                                     Vec3 velocity, double speed) {
        if (level.isClientSide || !target.isAlive() || target.level() != level) return;
        BlockPos sourcePosition = contact.sourcePosition();
        BlockPos position = sourcePosition;
        PuddleBlockEntity puddle = contact.source();
        UUID actorId = target.getUUID();
        UUID frameId = contact.frameId();
        if (StepContactEpisodes.containsPuddle(level, actorId, frameId, sourcePosition)) return;

        CharacterData character = ActiveCharacterPolicy.resolveActor(target).orElse(null);
        if (character == null || character.component(StunnableComponent.class).isEmpty()
                || character.component(StandingStateComponent.class).filter(StandingStateComponent::standingEligible).isEmpty()
                || character.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).isPresent()
                || !(target instanceof IStatusEffectTrait statusTrait)) return;
        if (!Double.isFinite(speed)) return;

        ReagentAttachment solution = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(solution.getMap(), level,
                "puddle slip " + position)) return;
        SlipperySolution.Outcome profile;
        try {
            profile = SlipperySolution.calculate(ReagentUnits.fromMap(solution.getMap()),
                    PrototypeRuntime.serverReagents());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not calculate puddle slip profile at {}: {}", position, exception.getMessage());
            return;
        }
        if (!profile.slippery() || profile.slipperyCents() <= SlipperySolution.LOW_VOLUME_CUTOFF_CENTS
                || speed < profile.requiredSlipSpeedBlocksPerSecond()
                || contact.triggerRatio() < MIN_INTERSECTION_RATIO) return;

        // StepTriggerSystem latches once speed/contact/CanSlip are satisfied. The later
        // slippery attempt, target NoSlip, and knockdown checks do not undo that latch.
        if (!StepContactEpisodes.latchPuddle(level, actorId, frameId, sourcePosition)) return;
        if (character.component(NoSlipComponent.class).isPresent()) return;
        if (character.component(NoSlipComponent.class).isPresent()
                || com.juicyslew.moonstation14.ms14.equipment.EquipmentNoSlipPolicy.hasWornFeetNoSlip(target)) return;
        boolean knockedDown = StatusEffectSystem.hasStatus(statusTrait.toHandleSelf(), level, KNOCKDOWN);
        if (knockedDown && !profile.superSlippery()) return;
        SlipAttempt attempt = new SlipAttempt(level, position.immutable(), puddle, target, profile, knockedDown);
        for (Predicate<SlipAttempt> gate : ATTEMPT_GATES) {
            try {
                if (!gate.test(attempt)) return;
            } catch (RuntimeException exception) {
                LOGGER.error("Slip attempt gate failed closed at {}", position, exception);
                return;
            }
        }

        boolean wasSliding = isSliding(target);
        SlipEvent event = new SlipEvent(level, position.immutable(), puddle, target, profile, wasSliding);
        for (java.util.function.Consumer<SlipEvent> listener : LISTENERS) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                LOGGER.error("Slip event listener failed at {}", position, exception);
            }
        }

        boolean proneEligible = character.component(StandingStateComponent.class).map(StandingStateComponent::proneEligible).orElse(false);
        SlipResponseData responseProfile = SlipResponseData.puddle(profile.stunSeconds(),
                profile.knockdownSeconds(), profile.launchVelocityMultiplier(), profile.friction(), true);
        respond(new SlipResponse(level, null, target, responseProfile, velocity, wasSliding, knockedDown,
                proneEligible),
                statusTrait);
    }

    private static void onItemContact(ItemStepContactSystem.ContactContext context) {
        LivingEntity target = context.target();
        if (!(target instanceof IStatusEffectTrait statusTrait)) return;
        CharacterData character = ActiveCharacterPolicy.resolveActor(target).orElse(null);
        if (character == null || character.component(StunnableComponent.class).isEmpty()
                || character.component(StandingStateComponent.class).filter(StandingStateComponent::standingEligible).isEmpty()) return;
        SlipResponseData response = context.slipResponse();
        if (response == null) return;
        boolean down = StatusEffectSystem.hasStatus(statusTrait.toHandleSelf(), context.level(), KNOCKDOWN);
        if (character.component(NoSlipComponent.class).isPresent()
                || com.juicyslew.moonstation14.ms14.equipment.EquipmentNoSlipPolicy.hasWornFeetNoSlip(target)
                || character.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).isPresent()
                || (down && !response.superSlippery())) return;
        respond(new SlipResponse(context.level(), context.source(), target, response, context.velocity(),
                isSliding(target), down, character.component(StandingStateComponent.class)
                .map(StandingStateComponent::proneEligible).orElse(false)), statusTrait);
    }

    public static double contactIntersectionRatio(AABB target, AABB source) {
        return ItemStepContactGeometry.intersectionRatio(target, source);
    }

    /** Minecraft item adaptation: use the same lower 0.25-block band as sliding contact. */
    static AABB itemContactFeetBand(AABB bounds) {
        return ItemStepContactGeometry.feetBand(bounds);
    }

    /** Shared response seam: sources are entities, while puddle admission/events remain block-specific. */
    static void respond(SlipResponse response, IStatusEffectTrait statusTrait) {
        LivingEntity target = response.target();
        ServerLevel level = response.level();
        SlipResponseData profile = response.profile();
        // Preserve source order: publish movement/sliding after SlipEvent, before down-only effects.
        if (!response.wasSliding() && profile.requiresContact() && profile.affectsSliding()
                && profile.knockdownSeconds() > 0d && response.proneEligible()) {
            SlidingAttachment sliding = new SlidingAttachment();
            sliding.setSliding(true);
            MS14Provider.update(target, MS14Bridges.SLIDING, sliding);
        }
        if (!response.wasSliding()) {
            target.setDeltaMovement(response.velocity().scale(profile.launchVelocityMultiplier()));
            target.hurtMarked = true;
        }
        boolean newlyDown = !response.alreadyKnockedDown();
        if (newlyDown) {
            // DropHandItemsEvent is source-directed upstream behavior. Do not drop the victim's hands.
            if (response.source() instanceof LivingEntity source) {
                com.juicyslew.moonstation14.ms14.mobs.MedicalIncapacitationEffects.dropSlipSourceHands(source);
            }
            CharacterControlSystem.updateStun(target, StatusEffectSystem.secondsToTicks((float) profile.stunSeconds()));
            if (profile.frictionDurationSeconds() > 0d && Math.abs(profile.frictionFactor() - 1d) > 1.0e-6d) StatusEffectSystem.apply(statusTrait.toHandleSelf(), level,
                    com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects.createKey("slip_friction"),
                    StatusEffectOperation.UPDATE, java.util.OptionalInt.of(StatusEffectSystem.secondsToTicks((float) profile.frictionDurationSeconds())), 0,
                    new com.juicyslew.moonstation14.ms14.status_effect.StatusEffectPayload.FrictionModifier(profile.frictionFactor()));
            if (profile.staminaDamage() > 0d)
                com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem.takeStaminaDamage(target, (float) profile.staminaDamage());
            com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter.play(level, profile.soundEvent(),
                    SoundSource.PLAYERS, target.getX(), target.getY(), target.getZ(),
                    (float) Math.pow(10d, profile.soundVolumeDb() / 20d), 1.0F);
        }
        if (response.proneEligible() && profile.knockdownSeconds() > 0d) {
            int duration = StatusEffectSystem.secondsToTicks((float) profile.knockdownSeconds());
            StatusEffectSystem.apply(statusTrait.toHandleSelf(), level, KNOCKDOWN,
                    StatusEffectOperation.UPDATE, java.util.OptionalInt.of(duration), 0);
        }
    }

    record SlipResponse(ServerLevel level, Entity source, LivingEntity target,
                                SlipResponseData profile, Vec3 velocity, boolean wasSliding,
                                boolean alreadyKnockedDown, boolean proneEligible) { }

    static SlipResponseData looseItemResponse(double stunSeconds, double knockdownSeconds,
                                                  double launchMultiplier, double frictionFactor,
                                                  boolean superSlippery) {
        return new SlipResponseData(stunSeconds, knockdownSeconds, launchMultiplier,
                frictionFactor, .5d, 25d, false, true, superSlippery);
    }

    /** Exit reconciliation for the repeated vanilla entityInside callback. */
    public static void reconcileTarget(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!entity.isAlive()) {
            clearSliding(entity);
            StepContactEpisodes.clearActor(level, entity.getUUID());
            return;
        }
        if (entity instanceof LivingEntity living && isSliding(entity)) {
            boolean knockedDown = living instanceof IStatusEffectTrait trait
                    && StatusEffectSystem.hasStatus(trait.toHandleSelf(), level, KNOCKDOWN);
            if (!knockedDown || !SlidingFrictionSystem.hasQualifyingContact(living)) clearSliding(entity);
        }
        if (entity instanceof LivingEntity living) {
            SharedPuddleContactQuery.Result query = SharedPuddleContactQuery.query(living, entity.getBoundingBox());
            StepContactEpisodes.reconcile(level, entity, entity.getBoundingBox(), query);
        }
    }

    private static final double MIN_INTERSECTION_RATIO = 0.3d;

    /**
     * Applies the pinned SS14 step-trigger area ratio to a virtual 0.8x0.8 XZ
     * fixture centered on the block. The local puddle shape is retained only as
     * the Minecraft adapter's vertical-contact requirement; its render/collision
     * footprint does not define the trigger area. SS14's 2D ratio is
     * max(intersection / target area, intersection / source area). Other target
     * AABBs can still produce different results from SS14 physics.
     */
    public static double intersectionRatio(AABB target, AABB contactShape, BlockPos blockPos) {
        if (target.maxY <= contactShape.minY || target.minY >= contactShape.maxY) return 0d;
        double sourceMinX = blockPos.getX() + 0.1d;
        double sourceMaxX = blockPos.getX() + 0.9d;
        double sourceMinZ = blockPos.getZ() + 0.1d;
        double sourceMaxZ = blockPos.getZ() + 0.9d;
        return contactIntersectionRatio(target, new AABB(sourceMinX, contactShape.minY, sourceMinZ,
                sourceMaxX, contactShape.maxY, sourceMaxZ));
    }

    /** Read-only on either logical side; absent attachment means not sliding. */
    public static boolean isSliding(Entity entity) {
        SlidingAttachment attachment = entity.getExistingDataOrNull(ModDataAttachments.SLIDING.get());
        return attachment != null && attachment.sliding();
    }

    private static void clearSliding(Entity entity) {
        MS14Provider.remove(entity, MS14Bridges.SLIDING);
    }

    /** Nonpersistent state has no trustworthy provenance after transfer/reload; fail neutral. */
    public static void onEntityJoin(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            StepContactEpisodes.clearActor(level, entity.getUUID());
            clearSliding(entity);
        }
    }

    /** Drops only this actor's volatile contact episodes when it leaves a server level. */
    public static void onEntityLeave(ServerLevel level, Entity entity) {
        if (entity instanceof LivingEntity) StepContactEpisodes.clearActor(level, entity.getUUID());
    }

    public record SlipAttempt(ServerLevel level, BlockPos sourcePosition, PuddleBlockEntity source,
                               LivingEntity target, SlipperySolution.Outcome solution,
                               boolean alreadyKnockedDown) { }

}
