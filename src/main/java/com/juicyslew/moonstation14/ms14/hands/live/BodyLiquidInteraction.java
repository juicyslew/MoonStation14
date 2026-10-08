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
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;

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

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

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
        ItemStack source = reverse ? originalWorld : held, destination = reverse ? held : originalWorld;
        IReagentTrait sourceTrait = (IReagentTrait) source.getItem(), destinationTrait = (IReagentTrait) destination.getItem();
        Object sourceHolder = source, destinationHolder = destination;
        var sourceBridge = ReagentSystem.bridgeForHolder(sourceHolder);
        var destinationBridge = ReagentSystem.bridgeForHolder(destinationHolder);
        if (sourceBridge == null || destinationBridge == null) return Result.DENIED;
        ReagentAttachment src = MS14Provider.getDetached(sourceHolder, sourceBridge), dst = MS14Provider.getDetached(destinationHolder, destinationBridge);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(src.getMap(), level, "liquid source")
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(dst.getMap(), level, "liquid destination")) return Result.DENIED;
        LiquidInteractionCapability.TransferPlan plan;
        try { plan = doseCents > 0
                ? LiquidInteractionCapability.plan(ReagentUnits.fromMap(src.getMap()), ReagentUnits.fromMap(dst.getMap()),
                    ReagentUnits.fromFloat(destinationTrait.getCapacity(destination, level)), doseCents)
                : LiquidInteractionCapability.plan(ReagentUnits.fromMap(src.getMap()), ReagentUnits.fromMap(dst.getMap()),
                    ReagentUnits.fromFloat(destinationTrait.getCapacity(destination, level))); } catch (RuntimeException ex) { return Result.DENIED; }
        if (plan.transferredCents() <= 0) return Result.DENIED;
        ItemStack stagedHeld = held.copy(), stagedWorld = originalWorld.copy();
        ItemStack stagedSource = reverse ? stagedWorld : stagedHeld, stagedDestination = reverse ? stagedHeld : stagedWorld;
        try {
            if (!publishStackReagents(stagedSource, stagedSource, plan.sourceAfter())
                    || !publishStackReagents(stagedDestination, stagedDestination, plan.destinationAfter())
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
        boolean heldFirst = !reverse;
        if (!valid.getAsBoolean() || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || level.getEntity(world.getUUID()) != world || !same(originalWorld, world.getItem())) return Result.DENIED;
        if (heldFirst) {
            try { LiveHandsCommitGateway.commitOwned(body, next); } catch (RuntimeException ignored) { }
            LiveHands now = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (now != next) return now == before ? Result.DENIED : Result.RECOVERY_REQUIRED;
            try { world.setItem(stagedWorld); } catch (RuntimeException ignored) { }
            if (same(stagedWorld, world.getItem())) return Result.SUCCESS;
            if (same(originalWorld, world.getItem())) {
                if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != next) return Result.RECOVERY_REQUIRED;
                LiveHands rollback = next.replaceOwnedComponents(next.revision(), hand, itemToken, held).orElse(null);
                if (rollback == null) return Result.RECOVERY_REQUIRED;
                try { LiveHandsCommitGateway.commitOwned(body, rollback); }
                catch (RuntimeException ex) { return Result.RECOVERY_REQUIRED; }
                LiveHands restored = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                return restored == rollback && restored.revision() == next.revision() + 1
                        && restored.stackCopy(hand).filter(s -> same(held, s)).isPresent()
                        ? Result.DENIED : Result.RECOVERY_REQUIRED;
            }
            return Result.RECOVERY_REQUIRED;
        }
        try { world.setItem(stagedWorld); } catch (RuntimeException ignored) { }
        if (!same(stagedWorld, world.getItem())) return same(originalWorld, world.getItem()) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        try { LiveHandsCommitGateway.commitOwned(body, next); } catch (RuntimeException ignored) { }
        LiveHands now = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (now == next) return Result.SUCCESS;
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
                () -> HandActorAuthority.revalidate(actor, authority), false, 0);
    }

    /** Body-owned route for trusted callers; world sipping does not require an occupied hand. */
    static Result sipOwned(LivingEntity body, UUID worldTargetId, String hand, String token, long revision,
                           BooleanSupplier authorityValid) {
        return sipBody(body, worldTargetId, hand, token, revision, authorityValid, false, 0);
    }

    /** Trusted server-thread completion seam for a previously-timed sip. */
    public static Result sipTimedOwned(LivingEntity body, UUID worldTargetId, String hand, String token,
                                       long currentRevision, int doseCents, BooleanSupplier authorityValid) {
        if (doseCents <= 0) return Result.DENIED;
        return sipBody(body, worldTargetId, hand, token, currentRevision, authorityValid, true, doseCents);
    }

    private static Result sipBody(LivingEntity body, @Nullable UUID worldTargetId, String hand,
                                   @Nullable String token, long revision, BooleanSupplier authorityValid,
                                   boolean timed, int doseCents) {
        return LiveHandsCommitGateway.batch(() -> sipTransaction(body, worldTargetId, hand, token, revision,
                authorityValid, timed, doseCents));
    }

    private static Result sipTransaction(LivingEntity body, @Nullable UUID worldTargetId, String hand,
                                   @Nullable String token, long revision, BooleanSupplier authorityValid,
                                   boolean timed, int doseCents) {
        if (body == null || hand == null || authorityValid == null) return Result.DENIED;
        if (!(body.level() instanceof ServerLevel level) || level.getServer() == null || !level.getServer().isSameThread()
                || !CharacterControlSystem.canAct(body) || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !authorityValid.getAsBoolean()) return Result.DENIED;
        var character = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(body).orElse(null);
        if (character == null || character.component(MouthComponent.class).isEmpty() || !StomachSystem.isEligible(body)
                || BodySystem.current(body).isEmpty()) return Result.DENIED;
        long now = level.getServer().overworld().getGameTime();
        if (!timed && now < SIP_COOLDOWN.getOrDefault(body.getUUID(), 0L)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision
                || !timed && !before.activeHand().equals(hand)) return Result.DENIED;
        ItemStack held = before.stackCopy(hand).orElse(null);
        boolean worldSource = worldTargetId != null;
        ItemToken ownedToken = null;
        if (worldSource) {
            if (!timed && (held != null || before.reservation(hand).isPresent())) return Result.DENIED;
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
        if (plan.transferredCents() == 0) return Result.DENIED;
        Map<ResourceKey<ReagentData>, Long> expectedStomach = plan.destinationAfter();
        ItemStack stagedSource = null;
        if (world == null) {
            ItemStack replacement = held.copy(); if (!publishStackReagents(replacement, replacement, plan.sourceAfter())) return Result.DENIED;
            stagedSource = replacement;
            LiveHands next = before.replaceOwnedComponents(revision, hand, ownedToken, replacement).orElse(null);
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
                    ? committedHands != null && committedHands.stackCopy(hand).filter(s -> same(committedSource, s)).isPresent()
                        && committedHands.token(hand).filter(token::equals).isPresent()
                    : sipStillValid(body, level, before, revision, hand, token, held, authorityValid, timed);
            if (!sourceOwned
                    || !stomachUnchanged(body, stomachAttachment, stomachBefore)
                    || world != null && (level.getEntity(world.getUUID()) != world || !same(stagedSource, world.getItem()))
                    || !authorityValid.getAsBoolean())
                return compensateSip(body, level, world, oldWorldStack, stagedSource, before, revision, hand, ownedToken, held, token, stomachAttachment, stomachBefore);
            MS14Provider.updateIfChanged(body, MS14Bridges.STOMACH, stomachBefore, new ReagentAttachment(toFloat(expectedStomach)));
        } catch (RuntimeException ignored) { }
        ReagentAttachment after;
        try { after = MS14Provider.getDetached(body, MS14Bridges.STOMACH); }
        catch (RuntimeException ex) { return Result.RECOVERY_REQUIRED; }
        if (!ReagentUnits.fromMap(after.getMap()).equals(expectedStomach)) {
            if (stomachUnchanged(body, stomachAttachment, stomachBefore))
                return compensateSip(body, level, world, oldWorldStack, stagedSource, before, revision, hand, ownedToken, held, token, stomachAttachment, stomachBefore);
            return Result.RECOVERY_REQUIRED;
        }
        EntityActivitySystem.update(body, EntityActivity.REAGENT_METABOLISM, true);
        if (!timed) SIP_COOLDOWN.put(body.getUUID(), now + 20L);
        float pitch = 0.75F + body.getRandom().nextFloat() * 0.5F;
        level.playSound(null, body.getX(), body.getY(), body.getZ(), ModSounds.DRINK.get(), SoundSource.PLAYERS,
                0.7943282F, pitch);
        return Result.SUCCESS;
    }

    private static boolean stomachUnchanged(LivingEntity body, Object attachment, Object snapshot) {
        if (body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()) != attachment) return false;
        ReagentAttachment current = MS14Provider.getDetached(body, MS14Bridges.STOMACH);
        return MS14Provider.snapshot(current).equals(snapshot);
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
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || before.revision() != revision || !timed && !before.activeHand().equals(hand)) return false;
        if (token == null) return before.stackCopy(hand).isEmpty() && before.reservation(hand).isEmpty();
        return before.token(hand).filter(token::equals).isPresent() && before.stackCopy(hand).filter(s -> same(held, s)).isPresent();
    }

    private static boolean baseValid(LivingEntity body, ServerLevel level, HandActorAuthority.Snapshot auth, ServerPlayer actor) {
        return level.getServer() != null && level.getServer().isSameThread() && CharacterControlSystem.canAct(body)
                && !body.isRemoved() && body.isAlive() && body.isAddedToLevel() && body.level() == level
                && level.getEntity(body.getUUID()) == body && HandActorAuthority.revalidate(actor, auth);
    }
    private static boolean validAgain(ServerPlayer actor, HandActorAuthority.Snapshot auth, LivingEntity body, ServerLevel level,
                                      LiveHands before, long revision, String hand, String token, ItemStack old) {
        return baseValid(body, level, auth, actor) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                && before.compatible(body) && before.revision() == revision && before.token(hand).filter(token::equals).isPresent()
                && before.stackCopy(hand).filter(s -> same(old,s)).isPresent();
    }
    private static boolean reagentStack(ItemStack s) { return s != null && !s.isEmpty() && s.getItem() instanceof IReagentTrait; }
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
}
