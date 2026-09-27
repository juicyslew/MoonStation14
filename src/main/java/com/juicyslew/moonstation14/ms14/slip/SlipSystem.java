package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
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
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** Authoritative contact admission and response for populated puddles. */
public final class SlipSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlipSystem.class);
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> KNOCKDOWN =
            ModStatusEffects.createKey("knockdown");
    // Only currently-contacting actors are retained. Level keys are weak and actor keys
    // are UUIDs, so this cache cannot keep either a level or an entity alive.
    private static final Map<ServerLevel, LevelContacts> CONTACTS = new WeakHashMap<>();
    private static final List<Predicate<SlipAttempt>> ATTEMPT_GATES = new CopyOnWriteArrayList<>();
    private static final List<java.util.function.Consumer<SlipEvent>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final int MAX_HOOKS = 64;
    private static final int MAX_ACTOR_CONTACTS = 32;
    // Emergency level-wide ceiling in addition to the actor-local bound. Reaching
    // either ceiling rejects new admissions; it never evicts and rearms an episode.
    private static final int MAX_LEVEL_CONTACTS = 8192;
    // Fail closed for malformed/custom entity AABBs. A normal .6x1.8 body overlaps
    // at most 2x2x3 cells; 16 permits boundary rounding without unbounded traversal.
    private static final int MAX_ENDPOINT_CELLS = 16;
    private static final double MAX_BODY_WIDTH = 1.5d;
    private static final double MAX_BODY_HEIGHT = 3d;

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
        admitContact(level, position, target, velocity, velocity.length() * 20d, target.getBoundingBox(), puddle);
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
    }

    private static void admitEndpointContacts(ServerLevel level, LivingEntity body,
                                              Vec3 acceptedDisplacement, Vec3 launchVelocity, double lengthSqr) {
        AABB bounds = body.getBoundingBox();
        int minX = net.minecraft.util.Mth.floor(bounds.minX);
        int minY = net.minecraft.util.Mth.floor(bounds.minY);
        int minZ = net.minecraft.util.Mth.floor(bounds.minZ);
        int maxX = net.minecraft.util.Mth.floor(Math.nextDown(bounds.maxX));
        int maxY = net.minecraft.util.Mth.floor(Math.nextDown(bounds.maxY));
        int maxZ = net.minecraft.util.Mth.floor(Math.nextDown(bounds.maxZ));
        long cells = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (!finite(bounds) || bounds.getXsize() > MAX_BODY_WIDTH || bounds.getZsize() > MAX_BODY_WIDTH
                || bounds.getYsize() > MAX_BODY_HEIGHT || cells <= 0 || cells > MAX_ENDPOINT_CELLS) return;
        reconcileContacts(level, body, bounds);
        for (BlockPos position : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (level.hasChunkAt(position) && level.getBlockState(position).is(ModBlocks.PUDDLE.get())
                    && level.getBlockEntity(position) instanceof PuddleBlockEntity puddle) {
                admitContact(level, position.immutable(), body, launchVelocity,
                        Math.sqrt(lengthSqr) * 20d, bounds, puddle);
            }
        }
    }

    private static boolean finite(AABB bounds) {
        return Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ);
    }

    private static void admitContact(ServerLevel level, BlockPos position, LivingEntity target,
                                     Vec3 velocity, double speed, AABB bounds, PuddleBlockEntity puddle) {
        if (level.isClientSide || !target.isAlive() || target.level() != level) return;
        BlockPos sourcePosition = position.immutable();
        UUID actorId = target.getUUID();
        synchronized (CONTACTS) {
            ActorContacts contacts = actorContacts(level, actorId, false);
            if (contacts != null && contacts.sources.containsKey(sourcePosition)) return;
        }

        CharacterData character = ActiveCharacterPolicy.resolveActor(target).orElse(null);
        if (character == null || !character.slipData().canReceiveStun()
                || !character.slipData().standingEligible()
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
                || intersectionRatio(bounds, puddle.getBlockState().getShape(level, position,
                CollisionContext.empty()).bounds().move(position), position) < MIN_INTERSECTION_RATIO) return;

        // StepTriggerSystem latches once speed/contact/CanSlip are satisfied. The later
        // slippery attempt, target NoSlip, and knockdown checks do not undo that latch.
        if (!latch(level, actorId, sourcePosition)) return;
        if (character.slipData().noSlip()) return;
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

        // SS14 only plays feedback and applies a new stun when the target was not
        // already knocked down. Super-slippery sources may still admit an event
        // while knocked down, and still refresh the knockdown below.
        if (!knockedDown) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 0.35F, 1.0F);
            CharacterControlSystem.applyStun(target, (float) profile.stunSeconds());
        }

        // The listener observes the pre-slip value; publish only the real transition afterward.
        if (!wasSliding && profile.knockdownSeconds() > 0d && character.slipData().proneEligible()) {
            SlidingAttachment sliding = new SlidingAttachment();
            sliding.setSliding(true);
            MS14Provider.update(target, MS14Bridges.SLIDING, sliding);
        }

        // SS14 does not reapply launch while sliding; preserve the current
        // physical velocity even when another source admits a slip event.
        if (!wasSliding) {
            target.setDeltaMovement(velocity.scale(profile.launchVelocityMultiplier()));
            target.hurtMarked = true;
        }
        if (character.slipData().proneEligible() && profile.knockdownSeconds() > 0d) {
            int duration = StatusEffectSystem.secondsToTicks((float) profile.knockdownSeconds());
            StatusEffectSystem.apply(statusTrait.toHandleSelf(), level, KNOCKDOWN,
                    StatusEffectOperation.SET, java.util.OptionalInt.of(duration), 0);
        }
    }

    /** Exit reconciliation for the repeated vanilla entityInside callback. */
    public static void reconcileTarget(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!entity.isAlive()) {
            clearSliding(entity);
            clearActorContacts(level, entity.getUUID());
            return;
        }
        if (entity instanceof LivingEntity living && isSliding(entity)) {
            boolean knockedDown = living instanceof IStatusEffectTrait trait
                    && StatusEffectSystem.hasStatus(trait.toHandleSelf(), level, KNOCKDOWN);
            if (!knockedDown || !SlidingFrictionSystem.hasQualifyingContact(living)) clearSliding(entity);
        }
        reconcileContacts(level, entity, entity.getBoundingBox());
    }

    private static ActorContacts actorContacts(ServerLevel level, UUID actorId, boolean create) {
        LevelContacts state = CONTACTS.get(level);
        if (state == null && create) {
            state = new LevelContacts();
            CONTACTS.put(level, state);
        }
        if (state == null) return null;
        ActorContacts result = state.actors.get(actorId);
        if (result == null && create) {
            result = new ActorContacts();
            state.actors.put(actorId, result);
        }
        return result;
    }

    private static boolean latch(ServerLevel level, UUID actorId, BlockPos position) {
        synchronized (CONTACTS) {
            LevelContacts state = CONTACTS.get(level);
            ActorContacts contacts = actorContacts(level, actorId, false);
            if (contacts != null && contacts.sources.containsKey(position)) return false;
            // Emergency bound only: never evict/rearm existing episodes to admit a new one.
            if ((contacts != null && contacts.sources.size() >= MAX_ACTOR_CONTACTS)
                    || (state != null && state.latchCount >= MAX_LEVEL_CONTACTS)) {
                if (!contactsCapLogged) {
                    contactsCapLogged = true;
                    LOGGER.warn("Puddle slip contact capacity reached (actor {}, level {}); new contacts fail closed",
                            MAX_ACTOR_CONTACTS, MAX_LEVEL_CONTACTS);
                }
                return false;
            }
            contacts = actorContacts(level, actorId, true);
            contacts.sources.put(position, level.getGameTime());
            CONTACTS.get(level).latchCount++;
            return true;
        }
    }

    private static boolean contactsCapLogged;

    private static void reconcileContacts(ServerLevel level, Entity entity, AABB bounds) {
        synchronized (CONTACTS) {
            LevelContacts state = CONTACTS.get(level);
            if (state == null) return;
            ActorContacts contacts = state.actors.get(entity.getUUID());
            if (contacts == null) return;
            int previousCount = contacts.sources.size();
            contacts.sources.entrySet().removeIf(entry -> {
                BlockPos position = entry.getKey();
                if (!level.hasChunkAt(position) || !level.getBlockState(position).is(ModBlocks.PUDDLE.get())) return true;
                AABB shape = level.getBlockState(position).getShape(level, position, CollisionContext.empty())
                        .bounds().move(position);
                return !bounds.intersects(shape);
            });
            state.latchCount -= previousCount - contacts.sources.size();
            if (contacts.sources.isEmpty()) state.actors.remove(entity.getUUID());
            if (state.actors.isEmpty()) CONTACTS.remove(level);
        }
    }

    private static void clearActorContacts(ServerLevel level, UUID actorId) {
        synchronized (CONTACTS) {
            LevelContacts state = CONTACTS.get(level);
            if (state == null) return;
            ActorContacts removed = state.actors.remove(actorId);
            if (removed != null) state.latchCount -= removed.sources.size();
            if (state.actors.isEmpty()) CONTACTS.remove(level);
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
        double targetArea = Math.max(0d, target.getXsize()) * Math.max(0d, target.getZsize());
        double sourceArea = (sourceMaxX - sourceMinX) * (sourceMaxZ - sourceMinZ);
        if (targetArea <= 0d || sourceArea <= 0d) return 0d;
        double overlapX = Math.max(0d, Math.min(target.maxX, sourceMaxX) - Math.max(target.minX, sourceMinX));
        double overlapZ = Math.max(0d, Math.min(target.maxZ, sourceMaxZ) - Math.max(target.minZ, sourceMinZ));
        double overlapArea = overlapX * overlapZ;
        return Math.max(overlapArea / targetArea, overlapArea / sourceArea);
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
            clearActorContacts(level, entity.getUUID());
            clearSliding(entity);
        }
    }

    /** Drops only this actor's volatile contact episodes when it leaves a server level. */
    public static void onEntityLeave(ServerLevel level, Entity entity) {
        if (entity instanceof LivingEntity) clearActorContacts(level, entity.getUUID());
    }

    public record SlipAttempt(ServerLevel level, BlockPos sourcePosition, PuddleBlockEntity source,
                              LivingEntity target, SlipperySolution.Outcome solution,
                              boolean alreadyKnockedDown) { }

    private static final class ActorContacts {
        private final Map<BlockPos, Long> sources = new java.util.HashMap<>();
    }

    private static final class LevelContacts {
        private final Map<UUID, ActorContacts> actors = new java.util.HashMap<>();
        private int latchCount;
    }
}
