package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Objects;

/** Authenticated timed consumer of resolved SliceableFood composition on loose produce. */
public final class LooseFoodSlicingService {
    private static final String KIND = "food_slicing";
    private LooseFoodSlicingService() { }

    /** Null means the selected source has no slicing composition. */
    public static BodyHandActionResult.Reason use(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands hands, BodyHandActionRequest request, LooseItemEntity target, ItemStack held) {
        if (actor == null || authority == null || hands == null || request == null || target == null || held == null)
            return BodyHandActionResult.Reason.DENIED;
        var body = authority.body();
        var level = body.level() instanceof ServerLevel server ? server : null;
        var id = BuiltInRegistries.ITEM.getKey(target.getItem().getItem());
        var catalog = ModItemProcessingProfiles.serverCatalog();
        var processing = id == null || catalog == null ? null : catalog.get(id);
        if (processing == null || processing.sliceableFood() == null) return null;
        var slice = processing.sliceableFood();
        var targetId = target.getUUID();
        String sourceKey = targetId.toString();
        if (TimedInteractionSystem.cancelDuplicate(body, KIND, sourceKey)) return BodyHandActionResult.Reason.OK;
        var proof = LooseItemProcessingProof.capture(actor, authority, hands, request, target, held,
                BodyHandActionRequest.Action.USE_TARGET, tool -> tool.knife() != null);
        if (proof == null) return BodyHandActionResult.Reason.DENIED;
        if (level == null || !level.getServer().isSameThread() || request.action() != BodyHandActionRequest.Action.USE_TARGET
                || request.expectedRevision() != hands.revision() || !request.handId().equals(hands.activeHand())
                || !Objects.equals(request.expectedToken(), hands.token(request.handId()).orElse(null))
                || !hands.compatible(body) || !CharacterControlSystem.canAct(body)
                || !HandActorAuthority.revalidate(actor, authority) || target.getItem().getCount() != 1
                || ToolQualities.profile(held) == null || ToolQualities.profile(held).knife() == null)
            return BodyHandActionResult.Reason.DENIED;
        var outputItem = BuiltInRegistries.ITEM.get(slice.output());
        var outputIngestion = ModIngestionProfiles.serverLookup(slice.output());
        if (outputItem == null || outputItem == net.minecraft.world.item.Items.AIR
                || !(outputItem instanceof IReagentTrait) || outputIngestion == null
                || outputIngestion.edible().kind() != IngestionProfile.Kind.FOOD
                || com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.serverLookup(slice.output()) == null) return BodyHandActionResult.Reason.DENIED;
        var sourceIngestion = ModIngestionProfiles.serverLookup(id);
        if (sourceIngestion == null || sourceIngestion.edible().kind() != IngestionProfile.Kind.FOOD) return BodyHandActionResult.Reason.DENIED;
        var sourceTrait = target.getItem().getItem() instanceof IReagentTrait trait ? trait : null;
        var sourceBridge = sourceTrait == null ? null : ReagentSystem.bridgeForHolder(target.getItem());
        var sourceFoodSolution = com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.resolve(id, level);
        var sourceSolutionName = sourceTrait == null ? null : sourceTrait.solutionName(target.getItem(), level);
        if (sourceBridge == null || sourceFoodSolution == null || sourceSolutionName == null
                || sourceSolutionName.isBlank() || !sourceSolutionName.equals(sourceFoodSolution.solutionName()))
            return BodyHandActionResult.Reason.DENIED;
        var sourceOrigin = target.position();
        var sourceStack = target.getItem().copy();
        var heldStack = held.copy();
        var sourceSolution = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.serverLookup(slice.output());
        int ticks = Math.max(1, (int) Math.ceil(slice.delaySeconds() * 20));
        var result = TimedInteractionSystem.start(body, actor, request.sequence(), KIND, sourceKey, ticks,
                true, true, 0.3, 0.3, () -> ticks, () -> current(proof, processing,
                 sourceIngestion, outputIngestion, sourceBridge, sourceTrait, sourceFoodSolution, sourceSolutionName),
                () -> complete(actor, authority, hands, request, target, targetId, sourceOrigin,
                         sourceStack, heldStack, processing, sourceIngestion, outputIngestion, sourceBridge, sourceTrait, sourceFoodSolution, sourceSolutionName, proof,
                         slice.count(), sourceSolution.capacityCents(), outputItem),
                java.util.Optional.of(target.blockPosition()), () -> { });
        return result == TimedInteractionEngine.StartResult.STARTED
                || result == TimedInteractionEngine.StartResult.DUPLICATE_CANCELLED
                ? BodyHandActionResult.Reason.OK : BodyHandActionResult.Reason.DENIED;
    }

    private static TimedInteractionSystem.Completion complete(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands hands, BodyHandActionRequest request, LooseItemEntity target, java.util.UUID targetId,
            net.minecraft.world.phys.Vec3 origin, ItemStack sourceStack, ItemStack heldStack,
            ItemProcessingProfile expected, IngestionProfile sourceIngestion, IngestionProfile outputIngestion,
            com.juicyslew.moonstation14.util.SystemLink<com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment, com.juicyslew.moonstation14.ms14.reagent.ReagentComponent> bridge,
              IReagentTrait trait, com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile foodSolution,
              String solutionName, LooseItemProcessingProof proof, int count, long capacity, net.minecraft.world.item.Item outputItem) {
        if (!current(proof, expected, sourceIngestion, outputIngestion, bridge, trait, foodSolution, solutionName))
            return TimedInteractionSystem.Completion.DENIED;
        try {
            var current = MS14Provider.getDetached(target.getItem(), bridge);
            var plans = SlicingPlan.prepare(ReagentUnits.fromMap(current.getMap()), count, capacity);
            var outputs = new ArrayList<ItemStack>(count + sourceIngestion.edible().trash().size());
            for (var contents : plans) {
                var stack = new ItemStack(outputItem);
                if (!BodyLiquidInteraction.publishStackReagents(stack, contents)) return TimedInteractionSystem.Completion.DENIED;
                outputs.add(stack);
            }
            var trash = FreshItemOutputStacks.build(sourceIngestion.edible().trash());
            if (trash == null) return TimedInteractionSystem.Completion.DENIED;
            outputs.addAll(trash);
            var outputIndex = new java.util.concurrent.atomic.AtomicInteger();
            var published = FreshWorldOutputBatch.publishAtSourcePosition((ServerLevel) authority.body().level(), origin,
                     outputs, () -> current(proof, expected, sourceIngestion, outputIngestion, bridge, trait, foodSolution, solutionName), () -> {
                        target.discard();
                        return com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS;
                    }, (entity, stack) -> {
                        if (outputIndex.getAndIncrement() < count) {
                            try {
                                var random = ((ServerLevel) authority.body().level()).random;
                                double angle = random.nextDouble() * Math.PI * 2.0;
                                double speed = (2.0 + random.nextDouble() * .5) / 20.0;
                                entity.setDeltaMovement(Math.cos(angle) * speed, 0, Math.sin(angle) * speed);
                                entity.hasImpulse = true;
                                entity.hurtMarked = true;
                            } catch (RuntimeException ignored) { /* best-effort motion cannot undo committed outputs */ }
                        }
                        return com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS;
                    });
            if (published == com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS) {
                try {
                    PositionalSoundRouter.play((ServerLevel) authority.body().level(),
                            net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(
                                    com.juicyslew.moonstation14.sounds.ModSounds.FOOD_CHOP.get()),
                            net.minecraft.sounds.SoundSource.PLAYERS, origin.x, origin.y, origin.z,
                            (float) Math.pow(10.0, -2.0 / 20.0), 1.0f);
                } catch (RuntimeException ignored) { /* committed publication must not be reported as a failed source transaction */ }
                return TimedInteractionSystem.Completion.STOP;
            }
            return switch (published) {
                case SUCCESS -> throw new IllegalStateException("successful batch handled above");
                case DENIED -> TimedInteractionSystem.Completion.DENIED;
                case RECOVERY_REQUIRED -> TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
            };
        } catch (RuntimeException failure) { return TimedInteractionSystem.Completion.DENIED; }
    }

    private static boolean current(LooseItemProcessingProof proof,
            ItemProcessingProfile expected, IngestionProfile sourceIngestion, IngestionProfile outputIngestion,
            com.juicyslew.moonstation14.util.SystemLink<com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment, com.juicyslew.moonstation14.ms14.reagent.ReagentComponent> bridge,
            IReagentTrait trait, com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile foodSolution,
            String solutionName) {
        if (!proof.current()) return false;
        var target = proof.source();
        var level = (ServerLevel) proof.authority().body().level();
        var profiles = ModItemProcessingProfiles.serverCatalog();
        var id = BuiltInRegistries.ITEM.getKey(target.getItem().getItem());
        if (profiles == null || id == null || profiles.get(id) != expected || proof.profile() != expected
                || ModIngestionProfiles.serverLookup(id) != sourceIngestion
                || ModIngestionProfiles.serverLookup(expected.sliceableFood().output()) != outputIngestion
                || !(target.getItem().getItem() instanceof IReagentTrait currentTrait) || currentTrait != trait
                || com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.resolve(id, level) != foodSolution
                || !solutionName.equals(currentTrait.solutionName(target.getItem(), level))
                || ReagentSystem.bridgeForHolder(target.getItem()) != bridge) return false;
        return true;
    }
}
