package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.components.MouthComponent;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.loose_objects.interaction.LiquidInteractionCapability;
import com.juicyslew.moonstation14.ms14.reagent.*;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Authenticated body-owned liquid interaction entry points. */
public final class BodyLiquidInteraction {
    private BodyLiquidInteraction() { }

    private static final ConcurrentHashMap<UUID, Long> SIP_COOLDOWN = new ConcurrentHashMap<>();

    public enum Result { SUCCESS, DENIED, CANNOT_INGEST, RECOVERY_REQUIRED }

    /** Narrow CAS contract for an authoritative non-item solution recipient. Values are detached. */
    public interface WorldSolutionRecipient {
        ReagentAttachment solutionSnapshot();
        int capacityCents();
        int refillMaximumCents();
        boolean matches(ReagentAttachment expected);
        boolean replace(ReagentAttachment expected, ReagentAttachment replacement);
        boolean validPosition();
        net.minecraft.world.phys.Vec3 position();
    }

    /** CAS adapter for an entity-owned source solution; the held container remains the sole recipient. */
    public interface EntitySolutionSource {
        ReagentAttachment snapshot();
        boolean matches(ReagentAttachment expected);
        boolean replace(ReagentAttachment expected, ReagentAttachment replacement);
        boolean valid();
    }

    /** Transfers an entity solution into the exact active owned hand, with the same staged hand commit used by pours. */
    public static Result transferEntitySource(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            String hand, String token, long revision, EntitySolutionSource source) {
        if (actor == null || authority == null || source == null || hand == null || token == null) return Result.DENIED;
        LivingEntity body = authority.body();
        if (body == null || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !baseValid(body, level, authority, actor) || !source.valid()) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision
                || !before.activeHand().equals(hand) || !before.token(hand).filter(token::equals).isPresent()) return Result.DENIED;
        ItemToken owned;
        try { owned = new ItemToken(token); } catch (RuntimeException ex) { return Result.DENIED; }
        ItemStack held = before.stackCopy(hand).orElse(null);
        if (!reagentStack(held) || !(held.getItem() instanceof IReagentTrait targetPolicy)) return Result.DENIED;
        var refill = targetPolicy.refillableSolution(held, level);
        var receiving = targetPolicy.solutionTransfer(held, level);
        if (refill == null || !refill.enabled() || receiving == null || !receiving.enabled() || !receiving.canReceive()) return Result.DENIED;
        ReagentAttachment sourceBefore = source.snapshot();
        if (sourceBefore == null || !source.valid() || !source.matches(sourceBefore)) return Result.DENIED;
        var targetBridge = ReagentSystem.bridgeForHolder(held);
        if (targetBridge == null) return Result.DENIED;
        ReagentAttachment targetBefore = MS14Provider.getDetached(held, targetBridge);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(sourceBefore.getMap(), level, "entity liquid source")
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(targetBefore.getMap(), level, "held liquid target")) return Result.DENIED;
        long remaining = Math.max(0L, ReagentUnits.fromFloat(targetPolicy.getCapacity(held, level)) - targetBefore.totalUnits());
        long request = Math.min(remaining, refill.maxRefill().map(ReagentUnits::fromFloat).orElse(Long.MAX_VALUE));
        HeldLiquidTransferPlan plan;
        try { plan = HeldLiquidTransferPlan.plan(sourceBefore, targetBefore,
                ReagentUnits.fromFloat(targetPolicy.getCapacity(held, level)), targetPolicy.getCapacity(held, level), request, level); }
        catch (RuntimeException ex) { return Result.DENIED; }
        if (plan.admittedCents() <= 0) return Result.DENIED;
        ItemStack staged = held.copy();
        if (!publishStackReagents(staged, staged, plan.recipientAfter())) return Result.DENIED;
        LiveHands next = before.replaceOwnedComponents(revision, hand, owned, staged).orElse(null);
        BooleanSupplier stillOwned = () -> baseValid(body, level, authority, actor)
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                && before.compatible(body) && before.revision() == revision && before.activeHand().equals(hand)
                && before.token(hand).filter(token::equals).isPresent()
                && before.stackCopy(hand).filter(s -> same(held, s)).isPresent()
                && source.valid() && source.matches(sourceBefore);
        if (next == null || !stillOwned.getAsBoolean() || !source.replace(sourceBefore, plan.sourceAfter())) return Result.DENIED;
        try { LiveHandsCommitGateway.commitOwned(body, next); } catch (RuntimeException ignored) { }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next) {
            playPourReaction(level, body.position(), plan.recipientAfter());
            return Result.SUCCESS;
        }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !source.replace(plan.sourceAfter(), sourceBefore)) return Result.RECOVERY_REQUIRED;
        return Result.DENIED;
    }

    /** Held-source-only transfer. The caller supplies a recipient adapter bound to its admitted proof. */
    public static Result transferWorldRecipient(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                                 String hand, String token, long revision,
                                                 WorldSolutionRecipient recipient) {
        if (actor == null || authority == null || hand == null || token == null || recipient == null) return Result.DENIED;
        LivingEntity body = authority.body();
        if (body == null || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !baseValid(body, level, authority, actor)
                || !recipient.validPosition()) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision
                || !before.activeHand().equals(hand) || !before.token(hand).filter(token::equals).isPresent()) return Result.DENIED;
        ItemToken ownedToken;
        try { ownedToken = new ItemToken(token); } catch (RuntimeException ex) { return Result.DENIED; }
        ItemStack held = before.stackCopy(hand).orElse(null);
        if (!reagentStack(held) || !(held.getItem() instanceof IReagentTrait sourcePolicy)) return Result.DENIED;
        var drain = sourcePolicy.drainableSolution(held, level);
        var sending = sourcePolicy.solutionTransfer(held, level);
        if (drain == null || !drain.enabled() || sending == null || !sending.enabled() || !sending.canSend()) return Result.DENIED;
        var srcBridge = ReagentSystem.bridgeForHolder(held);
        if (srcBridge == null) return Result.DENIED;
        ReagentAttachment source = MS14Provider.getDetached(held, srcBridge);
        ReagentAttachment destination = recipient.solutionSnapshot();
        if (destination == null || !recipient.matches(destination)
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(source.getMap(), level, "liquid source")
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(destination.getMap(), level, "liquid destination")) return Result.DENIED;
        float capacity = ReagentUnits.toFloat(recipient.capacityCents());
        HeldLiquidTransferPlan plan;
        try { plan = HeldLiquidTransferPlan.plan(source, destination, recipient.capacityCents(), capacity,
                Math.min(ReagentUnits.fromFloat(sending.transferAmount()), recipient.refillMaximumCents()), level); }
        catch (RuntimeException ex) { return Result.DENIED; }
        if (plan.admittedCents() <= 0) return Result.DENIED;
        ItemStack staged = held.copy();
        if (!publishStackReagents(staged, staged, plan.sourceAfter())) return Result.DENIED;
        LiveHands next = before.replaceOwnedComponents(revision, hand, ownedToken, staged).orElse(null);
        BooleanSupplier valid = () -> baseValid(body, level, authority, actor) && recipient.validPosition()
                && recipient.matches(destination) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                && before.compatible(body) && before.revision() == revision && before.activeHand().equals(hand)
                && before.token(hand).filter(token::equals).isPresent() && before.stackCopy(hand).filter(s -> same(held, s)).isPresent();
        if (next == null || !valid.getAsBoolean()) return Result.DENIED;
        return LiveHandsCommitGateway.batch(() -> commitHeldFirst(body, next, before, hand, ownedToken, held, staged,
                destination, plan.recipientAfter(), recipient::validPosition, recipient::matches,
                () -> recipient.replace(destination, plan.recipientAfter()), valid,
                () -> playPourReaction(level, recipient.position(), plan.recipientAfter())));
    }

    public static Result fill(ServerPlayer actor, UUID targetId, String hand, String token,
                              long revision, boolean reverse) {
        var auth = HandActorAuthority.resolve(actor);
        if (auth.isEmpty() || targetId == null) return Result.DENIED;
        var authority = auth.orElseThrow();
        return fillOwned(authority.body(), targetId, hand, token, revision, reverse,
                () -> HandActorAuthority.revalidate(actor, authority));
    }

    /** Syringe route uses the same conditional stack transaction while limiting its admitted volume. */
    public static Result fillDoseOwned(LivingEntity body, UUID targetId, String hand, String token, long revision,
                                       boolean reverse, int doseCents, BooleanSupplier authorityValid) {
        if (body == null || hand == null || token == null || doseCents <= 0 || authorityValid == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !authorityValid.getAsBoolean()
                || !CharacterControlSystem.canAct(body) || body.isRemoved() || !body.isAlive()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body || targetId == null) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision
                || !before.activeHand().equals(hand) || !before.token(hand).filter(token::equals).isPresent()) return Result.DENIED;
        ItemToken itemToken;
        try { itemToken = new ItemToken(token); } catch (RuntimeException ex) { return Result.DENIED; }
        ItemStack held = before.stackCopy(hand).orElse(null);
        if (!reagentStack(held) || BodyItemTargetProbe.probeSelected(level, body, targetId, 2.0) == null) return Result.DENIED;
        Entity found = level.getEntity(targetId);
        if (!(found instanceof ItemEntity world) || !(world instanceof LooseItemEntity) || world.getItem().getCount() != 1 || world.getTarget() != null
                || world.hasPickUpDelay() || !reagentStack(world.getItem())) return Result.DENIED;
        return LiveHandsCommitGateway.batch(() -> fillOwnedTransaction(body, level, world, held, before, hand, itemToken, token, revision,
                reverse, authorityValid, doseCents, BodyItemTargetProbe.SYRINGE_CONTAINER_REACH));
    }

    /** Trusted body-owned route, parallel to sipOwned. */
    static Result fillOwned(LivingEntity body, UUID targetId, String hand, String token, long revision,
                            boolean reverse, BooleanSupplier authorityValid) {
        if (body == null || hand == null || authorityValid == null || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread() || !authorityValid.getAsBoolean()
                || !CharacterControlSystem.canAct(body) || body.isRemoved() || !body.isAlive()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body || targetId == null) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision
                || !before.activeHand().equals(hand) || token == null || !before.token(hand).filter(token::equals).isPresent()) return Result.DENIED;
        ItemToken itemToken;
        try { itemToken = new ItemToken(token); } catch (RuntimeException ex) { return Result.DENIED; }
        ItemStack held = before.stackCopy(hand).orElse(null);
        if (!reagentStack(held) || BodyItemTargetProbe.probeSelected(level, body, targetId, BodyItemTargetProbe.MAX_REACH) == null) return Result.DENIED;
        Entity found = level.getEntity(targetId);
        if (!(found instanceof ItemEntity world) || !(world instanceof LooseItemEntity) || world.getTarget() != null
                || world.hasPickUpDelay() || !reagentStack(world.getItem())) return Result.DENIED;
        return LiveHandsCommitGateway.batch(() -> fillOwnedTransaction(body, level, world, held, before, hand, itemToken, token, revision, reverse, authorityValid, 0, BodyItemTargetProbe.MAX_REACH));
    }

    private static Result fillOwnedTransaction(LivingEntity body, ServerLevel level, ItemEntity world, ItemStack held,
                                                LiveHands before, String hand, ItemToken itemToken, String token,
                                                 long revision, boolean reverse, BooleanSupplier valid, int doseCents, double reach) {
        ItemStack originalWorld = world.getItem().copy();
        boolean heldToWorld = doseCents > 0 ? !reverse
                : eligibleTransfer(held, originalWorld, level) || !eligibleTransfer(originalWorld, held, level);
        if (doseCents <= 0 && !eligibleTransfer(heldToWorld ? held : originalWorld, heldToWorld ? originalWorld : held, level)) return Result.DENIED;
        ItemStack source = heldToWorld ? held : originalWorld, destination = heldToWorld ? originalWorld : held;
        IReagentTrait sourceTrait = (IReagentTrait) source.getItem(), destinationTrait = (IReagentTrait) destination.getItem();
        Object sourceHolder = source, destinationHolder = destination;
        var sourceBridge = ReagentSystem.bridgeForHolder(sourceHolder);
        var destinationBridge = ReagentSystem.bridgeForHolder(destinationHolder);
        if (sourceBridge == null || destinationBridge == null) return Result.DENIED;
        IReagentTrait sourcePolicy = sourceTrait, destinationPolicy = destinationTrait;
        var drain = sourcePolicy.drainableSolution(source, level);
        var sourceTransfer = sourcePolicy.solutionTransfer(source, level);
        var refill = destinationPolicy.refillableSolution(destination, level);
        var destinationTransfer = destinationPolicy.solutionTransfer(destination, level);
        ReagentAttachment src = MS14Provider.getDetached(sourceHolder, sourceBridge), dst = MS14Provider.getDetached(destinationHolder, destinationBridge);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(src.getMap(), level, "liquid source")
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(dst.getMap(), level, "liquid destination")) return Result.DENIED;
        HeldLiquidTransferPlan plan;
        float destinationCapacity = destinationTrait.getCapacity(destination, level);
        try { plan = HeldLiquidTransferPlan.plan(src, dst,
                ReagentUnits.fromFloat(destinationCapacity), destinationCapacity,
                doseCents > 0 ? doseCents : transferLimit(sourceTransfer.transferAmount(), refill), level); }
        catch (RuntimeException ex) { return Result.DENIED; }
        if (plan.admittedCents() <= 0) return Result.DENIED;
        ItemStack stagedHeld = held.copy(), stagedWorld = originalWorld.copy();
        ItemStack stagedSource = heldToWorld ? stagedHeld : stagedWorld, stagedDestination = heldToWorld ? stagedWorld : stagedHeld;
        ReagentAttachment reactedDestination = plan.recipientAfter();
        try {
            if (!publishStackReagents(stagedSource, stagedSource, plan.sourceAfter().snapshotUnits())) return Result.DENIED;
            if (!publishStackReagents(stagedDestination, stagedDestination, reactedDestination)
                    || !com.juicyslew.moonstation14.ms14.medical.syringe.SyringeTransferModeTransition.apply(stagedHeld, level))
                return Result.DENIED;
        } catch (RuntimeException ex) { return Result.DENIED; }
        LiveHands next = before.replaceOwnedComponents(revision, hand, itemToken, stagedHeld).orElse(null);
        if (next == null || !valid.getAsBoolean() || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || before.revision() != revision || !before.token(hand).filter(token::equals).isPresent()
                || before.stackCopy(hand).filter(s -> same(held,s)).isEmpty()
                || (reach == BodyItemTargetProbe.MAX_REACH
                    ? BodyItemTargetProbe.probeSelected(level, body, world.getUUID(), reach) == null
                    : !BodyItemTargetProbe.loadedReachableItem(level, body, world.getUUID(), reach))
                || level.getEntity(world.getUUID()) != world || !same(originalWorld, world.getItem())) return Result.RECOVERY_REQUIRED;
        boolean heldFirst = heldToWorld;
        if (!valid.getAsBoolean() || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || level.getEntity(world.getUUID()) != world || !same(originalWorld, world.getItem())) return Result.DENIED;
        if (heldFirst) {
            ReagentAttachment originalRecipient = MS14Provider.getDetached(originalWorld, ReagentSystem.bridgeForHolder(originalWorld));
            return commitHeldFirst(body, next, before, hand, itemToken, held, stagedHeld,
                originalRecipient, reactedDestination, () -> level.getEntity(world.getUUID()) == world,
                expected -> {
                    var currentBridge = ReagentSystem.bridgeForHolder(world.getItem());
                    return currentBridge != null && expected.equals(MS14Provider.getDetached(world.getItem(), currentBridge));
                }, () -> {
                    try { world.setItem(stagedWorld); } catch (RuntimeException ignored) { }
                    return same(stagedWorld, world.getItem());
                }, valid,
                () -> playPourReaction(level, world.position(), reactedDestination));
        }
        try { world.setItem(stagedWorld); } catch (RuntimeException ignored) { }
        if (!same(stagedWorld, world.getItem())) return same(originalWorld, world.getItem()) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        try { LiveHandsCommitGateway.commitOwned(body, next); } catch (RuntimeException ignored) { }
        LiveHands now = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (now == next) {
            playPourReaction(level, heldToWorld ? world.position() : body.position(), reactedDestination);
            return Result.SUCCESS;
        }
        if (now == before) {
            if (level.getEntity(world.getUUID()) != world || !same(stagedWorld, world.getItem())) return Result.RECOVERY_REQUIRED;
            try { world.setItem(originalWorld); } catch (RuntimeException ignored) { }
            return same(originalWorld, world.getItem()) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return Result.RECOVERY_REQUIRED;
    }

    /** Ingests a bounded dose from the selected held or loose reagent container. */
    public static Result sip(ServerPlayer actor, @Nullable UUID worldTargetId, String hand,
                             String token, long revision) {
        var auth = HandActorAuthority.resolve(actor);
        if (auth.isEmpty()) return Result.DENIED;
        var authority = auth.orElseThrow(); LivingEntity body = authority.body();
        return sipBody(body, worldTargetId, hand, token, revision,
                () -> HandActorAuthority.revalidate(actor, authority), false, 0, false, false,
                false, null, -2f, .25f);
    }

    /** Body-owned route for trusted callers; world sipping does not require an occupied hand. */
    static Result sipOwned(LivingEntity body, UUID worldTargetId, String hand, String token, long revision,
                           BooleanSupplier authorityValid) {
        return sipBody(body, worldTargetId, hand, token, revision, authorityValid, false, 0,
                false, false, false, null, -2f, .25f);
    }

    /** Trusted server-thread completion seam for a previously-timed sip. */
    public static Result sipTimedOwned(LivingEntity body, UUID worldTargetId, String hand, String token,
                                       long currentRevision, int doseCents, boolean destroyOnEmpty, boolean food,
                                       BooleanSupplier authorityValid) {
        return sipTimedOwned(body, worldTargetId, hand, token, currentRevision, doseCents, destroyOnEmpty,
                food, authorityValid, null, -2f, .25f);
    }

    /** Timed completion with resolved profile audio; source cleanup and transfer share one transaction. */
    public static Result sipTimedOwned(LivingEntity body, UUID worldTargetId, String hand, String token,
                                       long revision, int doseCents, boolean destroyOnEmpty, boolean food,
                                       BooleanSupplier authorityValid, ResourceLocation sound, float volumeDb,
                                       float pitchVariation) {
        return sipTimedOwned(body, worldTargetId, hand, token, revision, doseCents, destroyOnEmpty, food,
                authorityValid, sound, volumeDb, pitchVariation, ignored -> { });
    }

    /** Timed completion receipt is emitted only after the stomach/source transaction commits. */
    public static Result sipTimedOwned(LivingEntity body, UUID worldTargetId, String hand, String token,
                                       long revision, int doseCents, boolean destroyOnEmpty, boolean food,
                                       BooleanSupplier authorityValid, ResourceLocation sound, float volumeDb,
                                       float pitchVariation, java.util.function.LongConsumer acceptedDose) {
        if (doseCents <= 0) return Result.DENIED;
        return sipBody(body, worldTargetId, hand, token, revision, authorityValid, true, doseCents,
                destroyOnEmpty, food, false, sound, volumeDb, pitchVariation, acceptedDose);
    }

    /** Timed whole-dose edible completion; source removal joins the staged ownership candidate. */
    public static Result consumeTimedEdibleOwned(LivingEntity body, String hand, String token,
                                                  long revision, int doseCents, BooleanSupplier authorityValid,
                                                  ResourceLocation completionSound, float volumeDb, float pitchVariation) {
        if (doseCents <= 0 || token == null) return Result.DENIED;
        return sipBody(body, null, hand, token, revision, authorityValid, true, doseCents,
                false, true, true, completionSound, volumeDb, pitchVariation);
    }

    private static Result sipBody(LivingEntity body, @Nullable UUID worldTargetId, String hand,
                                  @Nullable String token, long revision, BooleanSupplier authorityValid,
                                  boolean timed, int doseCents, boolean destroyOnEmpty, boolean food,
                                  boolean consumeSource, ResourceLocation sound, float volumeDb, float pitchVariation) {
        return sipBody(body, worldTargetId, hand, token, revision, authorityValid, timed, doseCents,
                destroyOnEmpty, food, consumeSource, sound, volumeDb, pitchVariation, ignored -> { });
    }

    private static Result sipBody(LivingEntity body, @Nullable UUID worldTargetId, String hand,
                                  @Nullable String token, long revision, BooleanSupplier authorityValid,
                                  boolean timed, int doseCents, boolean destroyOnEmpty, boolean food,
                                  boolean consumeSource, ResourceLocation sound, float volumeDb, float pitchVariation,
                                  java.util.function.LongConsumer acceptedDose) {
        return LiveHandsCommitGateway.batch(() -> sipTransaction(body, worldTargetId, hand, token, revision,
                authorityValid, timed, doseCents, destroyOnEmpty, food, consumeSource, sound, volumeDb, pitchVariation, acceptedDose));
    }

    private static Result sipTransaction(LivingEntity body, @Nullable UUID worldTargetId, String hand,
                                         @Nullable String token, long revision, BooleanSupplier authorityValid,
                                         boolean timed, int doseCents, boolean destroyOnEmpty, boolean food,
                                         boolean consumeSource, ResourceLocation completionSound,
                                         float volumeDb, float pitchVariation,
                                         java.util.function.LongConsumer acceptedDose) {
        if (body == null || authorityValid == null || acceptedDose == null) return Result.DENIED;
        if (!(body.level() instanceof ServerLevel level) || level.getServer() == null || !level.getServer().isSameThread()
                || !CharacterControlSystem.canAct(body) || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !authorityValid.getAsBoolean()) return Result.DENIED;
        var character = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(body).orElse(null);
        if (character == null || character.component(MouthComponent.class).isEmpty() || !StomachSystem.isEligible(body)
                || BodySystem.current(body).isEmpty()) return Result.DENIED;
        long now = level.getServer().overworld().getGameTime();
        if (!timed && now < SIP_COOLDOWN.getOrDefault(body.getUUID(), 0L)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        boolean handless = timed && hand == null && revision == 0
                && com.juicyslew.moonstation14.ms14.ingestion.OralAccess.handless(body);
        if (handless ? before != null : hand == null || before == null || !before.compatible(body) || before.revision() != revision
                || !timed && !before.activeHand().equals(hand)) return Result.DENIED;
        ItemStack held = handless ? null : before.stackCopy(hand).orElse(null);
        boolean worldSource = worldTargetId != null;
        ItemToken ownedToken = null;
        if (worldSource) {
            if (!handless && !timed && (held != null || before.reservation(hand).isPresent())) return Result.DENIED;
            if (timed && held != null) {
                String currentToken = before.token(hand).orElse(null);
                if (currentToken == null || token != null && !token.equals(currentToken)) return Result.DENIED;
                token = currentToken;
            }
        } else {
            if (!reagentStack(held) || token == null || !before.token(hand).filter(token::equals).isPresent()) return Result.DENIED;
            try { ownedToken = new ItemToken(token); } catch (RuntimeException ex) { return Result.DENIED; }
        }
        ItemStack oldWorldStack = null;
        ItemStack sourceStack = held; ItemEntity world = null;
        if (worldTargetId != null) {
            if (!(timed ? BodyItemTargetProbe.loadedReachableItem(level, body, worldTargetId, BodyItemTargetProbe.MAX_REACH)
                    : BodyItemTargetProbe.probeSelected(level, body, worldTargetId, BodyItemTargetProbe.MAX_REACH) != null)) return Result.DENIED;
            Entity found = level.getEntity(worldTargetId);
            if (!(found instanceof ItemEntity item) || !(item instanceof LooseItemEntity) || item.getTarget() != null || item.hasPickUpDelay()
                    || !reagentStack(item.getItem())) return Result.DENIED;
            world = item; oldWorldStack = item.getItem().copy(); sourceStack = oldWorldStack.copy();
        }
        Object holder = sourceStack;
        var bridge = ReagentSystem.bridgeForHolder(holder); if (bridge == null) return Result.DENIED;
        ReagentAttachment src = MS14Provider.getDetached(holder, bridge), stomach = MS14Provider.getDetached(body, MS14Bridges.STOMACH);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(src.getMap(), level, "sip source")
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(stomach.getMap(), level, "sip stomach")) return Result.DENIED;
        var srcBefore = MS14Provider.snapshot(src); var stomachBefore = MS14Provider.snapshot(stomach);
        var stomachAttachment = body.getExistingDataOrNull(ModDataAttachments.STOMACH.get());
        LiquidInteractionCapability.TransferPlan plan;
        try { plan = timed
                ? LiquidInteractionCapability.plan(ReagentUnits.fromMap(src.getMap()), ReagentUnits.fromMap(stomach.getMap()), LiquidInteractionCapability.STOMACH_CAPACITY_CENTS, doseCents)
                : LiquidInteractionCapability.plan(ReagentUnits.fromMap(src.getMap()), ReagentUnits.fromMap(stomach.getMap()), LiquidInteractionCapability.STOMACH_CAPACITY_CENTS);
        } catch (RuntimeException ex) { return Result.DENIED; }
        if (consumeSource && plan.transferredCents() != doseCents) return Result.CANNOT_INGEST;
        if (plan.transferredCents() == 0) return Result.DENIED;
        boolean removeHeldSource = consumeSource || world == null && destroyOnEmpty
                && plan.sourceAfter().values().stream().mapToLong(Long::longValue).sum() == 0;
        Map<ResourceKey<ReagentData>, Long> expectedStomach = plan.destinationAfter();
        ItemStack stagedSource = null;
        LiveHands consumedCandidate = null;
        if (world == null) {
            LiveHands next;
            if (removeHeldSource) {
                next = before.consumeOne(revision, hand, token, held).orElse(null);
                if (next == null) return Result.DENIED;
                consumedCandidate = next;
            } else {
                ItemStack replacement = held.copy(); if (!publishStackReagents(replacement, replacement, plan.sourceAfter())) return Result.DENIED;
                stagedSource = replacement;
                next = before.replaceOwnedComponents(revision, hand, ownedToken, replacement).orElse(null);
            }
            if (next == null || !sipStillValid(body, level, before, revision, hand, token, held, authorityValid, timed)
                    || !stomachUnchanged(body, stomachAttachment, stomachBefore)) return Result.DENIED;
            try { LiveHandsCommitGateway.commitOwned(body, next); } catch (RuntimeException ignored) { }
            if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != next) {
                if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before) return Result.RECOVERY_REQUIRED;
                return Result.DENIED;
            }
        } else {
            ItemStack reduced = sourceStack.copy();
            if (!publishStackReagents(reduced, reduced, plan.sourceAfter()) || reduced.isEmpty()
                    || !sipStillValid(body, level, before, revision, hand, token, held, authorityValid, timed)
                    || !(timed ? BodyItemTargetProbe.loadedReachableItem(level, body, worldTargetId, BodyItemTargetProbe.MAX_REACH)
                    : BodyItemTargetProbe.probeSelected(level, body, worldTargetId, BodyItemTargetProbe.MAX_REACH) != null)
                    || !same(oldWorldStack, world.getItem()) || !stomachUnchanged(body, stomachAttachment, stomachBefore)
                    || !authorityValid.getAsBoolean()) return Result.DENIED;
            stagedSource = reduced;
            try { world.setItem(reduced); } catch (RuntimeException ignored) { }
            if (!same(reduced, world.getItem())) return same(oldWorldStack, world.getItem()) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        try {
            LiveHands committedHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            ItemStack committedSource = stagedSource;
            boolean sourceOwned = world == null
                    ? removeHeldSource ? committedHands == consumedCandidate && committedHands.stackCopy(hand).isEmpty()
                        && committedHands.token(hand).isEmpty() : committedHands != null && committedHands.stackCopy(hand).filter(s -> same(committedSource, s)).isPresent()
                        && committedHands.token(hand).filter(token::equals).isPresent()
                    : sipStillValid(body, level, before, revision, hand, token, held, authorityValid, timed);
            if (!sourceOwned
                    || !stomachUnchanged(body, stomachAttachment, stomachBefore)
                    || world != null && (level.getEntity(world.getUUID()) != world || !same(stagedSource, world.getItem()))
                    || !authorityValid.getAsBoolean())
                return removeHeldSource ? compensateConsumedSip(body, consumedCandidate, hand, token, held, stomachAttachment, stomachBefore)
                        : compensateSip(body, level, world, oldWorldStack, stagedSource, before, revision, hand, ownedToken, held, token, stomachAttachment, stomachBefore);
            MS14Provider.updateIfChanged(body, MS14Bridges.STOMACH, stomachBefore, new ReagentAttachment(toFloat(expectedStomach)));
        } catch (RuntimeException ignored) { }
        ReagentAttachment after;
        try { after = MS14Provider.getDetached(body, MS14Bridges.STOMACH); }
        catch (RuntimeException ex) { return Result.RECOVERY_REQUIRED; }
        if (!ReagentUnits.fromMap(after.getMap()).equals(expectedStomach)) {
            if (stomachUnchanged(body, stomachAttachment, stomachBefore))
                return removeHeldSource ? compensateConsumedSip(body, consumedCandidate, hand, token, held, stomachAttachment, stomachBefore)
                        : compensateSip(body, level, world, oldWorldStack, stagedSource, before, revision, hand, ownedToken, held, token, stomachAttachment, stomachBefore);
            return Result.RECOVERY_REQUIRED;
        }
        if (world != null && destroyOnEmpty && plan.sourceAfter().values().stream().mapToLong(Long::longValue).sum() == 0)
            world.discard();
        if (timed) acceptedDose.accept(plan.transferredCents());
        EntityActivitySystem.update(body, EntityActivity.REAGENT_METABOLISM, true);
        if (!timed) SIP_COOLDOWN.put(body.getUUID(), now + 20L);
        ResourceLocation selectedSound = completionSound == null
                ? ResourceLocation.fromNamespaceAndPath("moonstation14", food ? "ss14/items/eat" : "ss14/items/drink")
                : completionSound;
        var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(selectedSound).orElse(null);
        if (sound != null) {
            float pitch = 1f + (body.getRandom().nextFloat() * 2f - 1f) * pitchVariation;
            float gain = (float) Math.pow(10.0, volumeDb / 20.0);
            level.playSound(null, body.getX(), body.getY(), body.getZ(), sound, SoundSource.PLAYERS, gain, pitch);
        }
        return Result.SUCCESS;
    }

    private static boolean stomachUnchanged(LivingEntity body, Object attachment, Object snapshot) {
        if (body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()) != attachment) return false;
        ReagentAttachment current = MS14Provider.getDetached(body, MS14Bridges.STOMACH);
        return MS14Provider.snapshot(current).equals(snapshot);
    }

    private static Result compensateConsumedSip(LivingEntity body, LiveHands staged, String hand, String token,
                                                ItemStack original, Object stomachAttachment,
                                                Object stomachSnapshot) {
        if (staged == null || !stomachUnchanged(body, stomachAttachment, stomachSnapshot)) return Result.RECOVERY_REQUIRED;
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (current != staged || current.stackCopy(hand).isPresent() || current.token(hand).isPresent()) return Result.RECOVERY_REQUIRED;
        var restored = current.putWhole(current.revision(), hand, new ItemToken(token), original);
        // Compensation is conservation-only and remains necessary after authority invalidation;
        // it can restore only the exact empty staged hand and cannot initiate any new action.
        if (!restored.succeeded()) return Result.RECOVERY_REQUIRED;
        try { LiveHandsCommitGateway.commitOwned(body, restored.state()); } catch (RuntimeException ignored) { }
        return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == restored.state()
                ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }

    private static Result compensateSip(LivingEntity body, ServerLevel level, ItemEntity world, ItemStack oldWorld, ItemStack staged,
                                        LiveHands before, long revision, String hand, ItemToken ownedToken,
                                        ItemStack held, String token, Object attachment, Object stomachSnapshot) {
        if (!stomachUnchanged(body, attachment, stomachSnapshot)) return Result.RECOVERY_REQUIRED;
        if (world != null) {
            if (!same(world.getItem(), staged)) return Result.RECOVERY_REQUIRED;
            try { world.setItem(oldWorld.copy()); } catch (RuntimeException ignored) { }
            return same(world.getItem(), oldWorld) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (current == before) return Result.DENIED;
        ItemStack reduced = current == null ? null : current.stackCopy(hand).orElse(null);
        if (current == null || !current.token(hand).filter(token::equals).isPresent() || !same(reduced, staged)) return Result.RECOVERY_REQUIRED;
        LiveHands rollback = current.replaceOwnedComponents(current.revision(), hand, ownedToken, held).orElse(null);
        if (rollback == null) return Result.RECOVERY_REQUIRED;
        try { LiveHandsCommitGateway.commitOwned(body, rollback); } catch (RuntimeException ignored) { }
        return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == rollback
                && rollback.revision() == current.revision() + 1 && rollback.stackCopy(hand).filter(s -> same(held,s)).isPresent()
                ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }

    private static boolean sipStillValid(LivingEntity body, ServerLevel level, LiveHands before, long revision,
                                         String hand, String token, ItemStack held, BooleanSupplier authorityValid, boolean timed) {
        if (authorityValid == null || !authorityValid.getAsBoolean() || body.level() != level || body.isRemoved()
                || !body.isAlive() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before) return false;
        if (before == null) return timed && hand == null && revision == 0
                && com.juicyslew.moonstation14.ms14.ingestion.OralAccess.handless(body);
        if (!before.compatible(body) || before.revision() != revision || !timed && !before.activeHand().equals(hand)) return false;
        if (token == null) return before.stackCopy(hand).isEmpty() && before.reservation(hand).isEmpty();
        return before.token(hand).filter(token::equals).isPresent() && before.stackCopy(hand).filter(s -> same(held, s)).isPresent();
    }

    private static boolean baseValid(LivingEntity body, ServerLevel level, HandActorAuthority.Snapshot auth, ServerPlayer actor) {
        return level.getServer() != null && level.getServer().isSameThread() && CharacterControlSystem.canAct(body)
                && !body.isRemoved() && body.isAlive() && body.isAddedToLevel() && body.level() == level
                && level.getEntity(body.getUUID()) == body && HandActorAuthority.revalidate(actor, auth);
    }
    private static Result commitHeldFirst(LivingEntity body, LiveHands next, LiveHands before, String hand,
                                          ItemToken token, ItemStack originalHeld, ItemStack stagedHeld,
                                          ReagentAttachment expectedRecipient, ReagentAttachment replacementRecipient,
                                          BooleanSupplier recipientValid,
                                          java.util.function.Predicate<ReagentAttachment> recipientMatches,
                                          BooleanSupplier recipientCommit, BooleanSupplier valid,
                                          Runnable committedAudio) {
        if (!valid.getAsBoolean() || !recipientValid.getAsBoolean()) return Result.DENIED;
        try { LiveHandsCommitGateway.commitOwned(body, next); } catch (RuntimeException ignored) { }
        LiveHands now = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (now != next) return now == before ? Result.DENIED : Result.RECOVERY_REQUIRED;
        boolean committed = false;
        try { committed = recipientCommit.getAsBoolean(); } catch (RuntimeException ignored) { }
        if (committed && recipientMatches.test(replacementRecipient)) {
            committedAudio.run();
            return Result.SUCCESS;
        }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != next
                || !recipientValid.getAsBoolean() || !recipientMatches.test(expectedRecipient)) return Result.RECOVERY_REQUIRED;
        LiveHands rollback = next.replaceOwnedComponents(next.revision(), hand, token, originalHeld).orElse(null);
        if (rollback == null) return Result.RECOVERY_REQUIRED;
        try { LiveHandsCommitGateway.commitOwned(body, rollback); } catch (RuntimeException ex) { return Result.RECOVERY_REQUIRED; }
        LiveHands restored = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return restored == rollback && restored.revision() == next.revision() + 1
                && restored.stackCopy(hand).filter(s -> same(originalHeld, s)).isPresent()
                ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }
    private static boolean validAgain(ServerPlayer actor, HandActorAuthority.Snapshot auth, LivingEntity body, ServerLevel level,
                                      LiveHands before, long revision, String hand, String token, ItemStack old) {
        return baseValid(body, level, auth, actor) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                && before.compatible(body) && before.revision() == revision && before.token(hand).filter(token::equals).isPresent()
                && before.stackCopy(hand).filter(s -> same(old,s)).isPresent();
    }
    private static boolean reagentStack(ItemStack s) { return s != null && !s.isEmpty() && s.getItem() instanceof IReagentTrait; }
    private static boolean eligibleTransfer(ItemStack source, ItemStack destination, ServerLevel level) {
        if (!reagentStack(source) || !reagentStack(destination)) return false;
        IReagentTrait s = (IReagentTrait) source.getItem(), d = (IReagentTrait) destination.getItem();
        var drain = s.drainableSolution(source, level); var send = s.solutionTransfer(source, level);
        var refill = d.refillableSolution(destination, level); var receive = d.solutionTransfer(destination, level);
        return drain != null && drain.enabled() && send.enabled() && send.canSend()
                && refill.enabled() && receive.enabled() && receive.canReceive()
                && s.solutionName(source, level).equals(drain.solutionName())
                && d.solutionName(destination, level).equals(refill.solutionName());
    }
    private static long transferLimit(float amount, RefillableSolutionPolicy refill) {
        float limit = refill.maxRefill().map(v -> Math.min(v, amount)).orElse(amount);
        return ReagentUnits.fromFloat(limit);
    }
    private static void playPourReaction(ServerLevel level, net.minecraft.world.phys.Vec3 pos, ReagentAttachment reactedRecipient) {
        com.juicyslew.moonstation14.ms14.reagent.ReactionAudio.playCommitted(level, pos, reactedRecipient);
    }
    private static boolean same(ItemStack a, ItemStack b) { return a != null && b != null && a.getCount()==b.getCount() && ItemStack.isSameItemSameComponents(a,b); }
    private static boolean restoreWorld(ItemEntity e, ItemStack s, ServerLevel l) { try { if (l.getEntity(e.getUUID()) != e || !e.getItem().isEmpty()) return false; e.setItem(s.copy()); return same(s,e.getItem()); } catch (RuntimeException ex) { return false; } }
    private static Map<ResourceKey<ReagentData>, Float> toFloat(Map<ResourceKey<ReagentData>, Long> cents) {
        Map<ResourceKey<ReagentData>, Float> result = new LinkedHashMap<>(); cents.forEach((k,v)->result.put(k,ReagentUnits.toFloat(v))); return result;
    }
    private static boolean publishStackReagents(Object holder, ItemStack stack, Map<ResourceKey<ReagentData>, Long> values) {
        if (!(stack.getItem() instanceof IReagentTrait trait)) return false;
        var link = ReagentSystem.bridgeForHolder(holder); if (link == null) return false;
        ReagentAttachment before = MS14Provider.getDetached(holder, link);
        MS14Provider.updateIfChanged(holder, link, MS14Provider.snapshot(before), new ReagentAttachment(toFloat(values)));
        return true;
    }
    private static boolean publishStackReagents(Object holder, ItemStack stack, ReagentAttachment staged) {
        if (!(stack.getItem() instanceof IReagentTrait)) return false;
        var link = ReagentSystem.bridgeForHolder(holder); if (link == null) return false;
        ReagentAttachment before = MS14Provider.getDetached(holder, link);
        MS14Provider.updateIfChanged(holder, link, MS14Provider.snapshot(before), staged);
        return true;
    }
}
