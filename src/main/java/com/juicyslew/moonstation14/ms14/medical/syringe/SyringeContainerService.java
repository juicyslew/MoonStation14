package com.juicyslew.moonstation14.ms14.medical.syringe;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.custom.SyringeItem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.loose_objects.interaction.LiquidInteractionCapability;
import com.juicyslew.moonstation14.ms14.medical.InjectorModeData;
import com.juicyslew.moonstation14.ms14.medical.ModMedicalItems;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.*;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Authenticated syringe transfers to/from loose reagent containers. */
public final class SyringeContainerService {
    private static final String KIND = "syringe-container";
    private SyringeContainerService() { }

    public static BodyLiquidInteraction.Result useContainer(ServerPlayer actor, String handId, String token,
            long revision, long sequence, UUID worldTargetId) {
        HandActorAuthority.Snapshot authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null || worldTargetId == null || !CharacterControlSystem.canAct(authority.body())) return BodyLiquidInteraction.Result.DENIED;
        var body = authority.body();
        BooleanSupplier authorityValid = () -> HandActorAuthority.revalidate(actor, authority);
        if (!(body.level() instanceof ServerLevel level) || !authorityValid.getAsBoolean()) return BodyLiquidInteraction.Result.DENIED;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body) || hands.revision() != revision || !handId.equals(hands.activeHand())
                || !hands.token(handId).filter(token::equals).isPresent()) return BodyLiquidInteraction.Result.DENIED;
        ItemStack syringe = hands.stackCopy(handId).orElse(ItemStack.EMPTY);
        if (syringe.isEmpty() || syringe.getCount() != 1 || !(syringe.getItem() instanceof SyringeItem syringeItem)) return BodyLiquidInteraction.Result.DENIED;
        Entity found = level.getEntity(worldTargetId);
        if (!(found instanceof ItemEntity world) || !(world instanceof com.juicyslew.moonstation14.entities.LooseItemEntity)
                || world.getTarget() != null || world.hasPickUpDelay() || world.getItem().isEmpty() || world.getItem().getCount() != 1
                || !(world.getItem().getItem() instanceof IReagentTrait)) return BodyLiquidInteraction.Result.DENIED;
        if (BodyItemTargetProbe.probeSelected(level, body, worldTargetId, BodyItemTargetProbe.SYRINGE_CONTAINER_REACH) == null) return BodyLiquidInteraction.Result.DENIED;

        var policy = ModMedicalItems.require(ModMedicalItems.catalog(level), SyringeItem.PROTOTYPE_ID).syringePolicy().orElse(null);
        var data = syringeItem.dataOrInitial(syringe, level);
        if (policy == null || !policy.allowedModes().contains(data.mode())) return BodyLiquidInteraction.Result.DENIED;
        var mode = PrototypeRuntime.serverInjectorModes().get(data.mode());
        if (mode == null || !mode.transferAmounts().contains(data.transferAmountUnits())) return BodyLiquidInteraction.Result.DENIED;
        int dose;
        try { dose = Math.toIntExact(ReagentUnits.fromFloat(data.transferAmountUnits())); } catch (RuntimeException ex) { return BodyLiquidInteraction.Result.DENIED; }
        if (dose <= 0 || dose > ReagentUnits.fromFloat(policy.capacityUnits())) return BodyLiquidInteraction.Result.DENIED;
        boolean draw = mode.kind() == InjectorModeData.Kind.DRAW;
        Object source = draw ? world.getItem().copy() : syringe.copy();
        Object destination = draw ? syringe.copy() : world.getItem().copy();
        var sourceBridge = ReagentSystem.bridgeForHolder(source);
        var destinationBridge = ReagentSystem.bridgeForHolder(destination);
        if (sourceBridge == null || destinationBridge == null) return BodyLiquidInteraction.Result.DENIED;
        var src = com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(source, sourceBridge);
        var dst = com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(destination, destinationBridge);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(src.getMap(), level, "syringe source")
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(dst.getMap(), level, "syringe destination")) return BodyLiquidInteraction.Result.DENIED;
        var destinationTrait = (IReagentTrait)((ItemStack)destination).getItem();
        LiquidInteractionCapability.TransferPlan plan;
        try { plan = LiquidInteractionCapability.plan(ReagentUnits.fromMap(src.getMap()), ReagentUnits.fromMap(dst.getMap()),
                ReagentUnits.fromFloat(destinationTrait.getCapacity((ItemStack)destination, level)), dose); }
        catch (RuntimeException ex) { return BodyLiquidInteraction.Result.DENIED; }
        if (plan.transferredCents() <= 0) return BodyLiquidInteraction.Result.DENIED;
        ItemStack expectedTarget = world.getItem().copy();
        int ticks;
        try { ticks = SyringeTiming.ticks(mode, ReagentUnits.toFloat(plan.transferredCents()), true, SyringeTiming.TargetPosture.STANDING); }
        catch (RuntimeException ex) { return BodyLiquidInteraction.Result.DENIED; }
        if (ticks <= 0) return BodyLiquidInteraction.fillDoseOwned(body, worldTargetId, handId, token, revision, draw, dose, authorityValid);
        AtomicBoolean completed = new AtomicBoolean();
        var result = TimedInteractionSystem.start(body, actor, sequence, KIND, token + ":" + worldTargetId, ticks,
                true, true, 1.0, policy.movementThreshold(), () -> ticks,
                () -> authorityValid.getAsBoolean() && !completed.get() && liveProof(body, level, handId, token, revision, syringe, worldTargetId, expectedTarget, data.mode(), data.transferAmountUnits(), policy.needHand()),
                () -> {
                    if (!completed.compareAndSet(false, true)) return TimedInteractionSystem.Completion.STOP;
                    var transfer = BodyLiquidInteraction.fillDoseOwned(body, worldTargetId, handId, token, revision, draw, dose, authorityValid);
                    return transfer == BodyLiquidInteraction.Result.SUCCESS ? TimedInteractionSystem.Completion.STOP
                            : transfer == BodyLiquidInteraction.Result.RECOVERY_REQUIRED ? TimedInteractionSystem.Completion.RECOVERY_REQUIRED : TimedInteractionSystem.Completion.STOP;
                });
        return result == TimedInteractionEngine.StartResult.STARTED ? BodyLiquidInteraction.Result.SUCCESS : BodyLiquidInteraction.Result.DENIED;
    }

    private static boolean liveProof(net.minecraft.world.entity.LivingEntity body, ServerLevel level, String hand,
            String token, long revision, ItemStack expected, UUID target, ItemStack expectedTarget, ResourceLocation initialMode, float dose, boolean needHand) {
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack stack = current == null ? null : current.stackCopy(hand).orElse(null);
        if (current == null || current.revision() != revision || !current.activeHand().equals(hand)
                || !current.token(hand).filter(token::equals).isPresent() || stack == null || !ItemStack.matches(stack, expected)
                || stack.get(ModDataComponents.SYRINGE.get()) != null && !stack.get(ModDataComponents.SYRINGE.get()).mode().equals(initialMode)
                || stack.get(ModDataComponents.SYRINGE.get()) != null && stack.get(ModDataComponents.SYRINGE.get()).transferAmountUnits() != dose
                 || needHand && stack.isEmpty() || !BodyItemTargetProbe.loadedReachableItem(level, body, target, BodyItemTargetProbe.SYRINGE_CONTAINER_REACH)) return false;
        if (!CharacterControlSystem.canAct(body)) return false;
        Entity found = level.getEntity(target);
        return found instanceof ItemEntity item && item instanceof com.juicyslew.moonstation14.entities.LooseItemEntity
                && !item.hasPickUpDelay() && item.getTarget() == null && ItemStack.matches(item.getItem(), expectedTarget)
                && item.getItem().getItem() instanceof IReagentTrait;
    }
}
