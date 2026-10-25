package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Authenticated ordinary block-use actions for the first tray/plant lifecycle slice. */
public final class BotanyHandUseService {
    private BotanyHandUseService() { }

    public static BodyBlockUseResult.Outcome use(ServerPlayer actor, BodyBlockUseRequest request,
                                                   BodyHandRequestService.BlockUseAdmission admission,
                                                   HydroponicsTrayBlockEntity tray, ItemStack held) {
        if (actor == null || request == null || admission == null || tray == null
                || request.intent() != BodyBlockUseRequest.Intent.USE) return BodyBlockUseResult.Outcome.DENIED;
        var authority = admission.authority();
        var body = authority.body();
        var hands = admission.hands();
        if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !CharacterControlSystem.canAct(body)
                || !HandActorAuthority.revalidate(actor, authority)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(body) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand())
                || !BodyHandRequestService.blockUseHandStateMatches(request.expectedToken(),
                    hands.token(request.handId()).isPresent(), hands.stackCopy(request.handId()).isPresent(),
                    hands.token(request.handId()).orElse(null))
                || !tray.isLoadedValid() || level.getBlockEntity(admission.hit().pos()) != tray
                || !tray.getBlockPos().equals(admission.hit().pos()) || !level.mayInteract(actor, tray.getBlockPos()))
            return BodyBlockUseResult.Outcome.DENIED;

        if (held != null && !held.isEmpty() && held.getItem() instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) {
            var result = BotanyLiquidUseService.water(actor, authority, request.handId(), request.expectedToken(),
                    request.expectedRevision(), tray);
            return result == com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction.Result.SUCCESS
                    ? BodyBlockUseResult.Outcome.PLACED : BodyBlockUseResult.Outcome.DENIED;
        }

        var plant = tray.plantState();
        if ((request.intent() == BodyBlockUseRequest.Intent.USE
                || request.intent() == BodyBlockUseRequest.Intent.ACTIVATE_WORLD)
                && (held == null || held.isEmpty()) && plant != null && plant.seed() != null) {
            if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(body)
                    || !tray.isLoadedValid() || level.getBlockEntity(admission.hit().pos()) != tray
                    || !level.mayInteract(actor, tray.getBlockPos())) return BodyBlockUseResult.Outcome.DENIED;
            if (plant.dead()) { tray.commitClear(); return BodyBlockUseResult.Outcome.PLACED; }
            SeedProfile seed = plant.effectiveProfile();
            if (seed == null) seed = ModSeedProfiles.serverLookup(plant.seed());
            if (seed == null || seed.lifecycle().ligneous() || !plant.ready()) return BodyBlockUseResult.Outcome.DENIED;
            return harvestReady(actor, request, admission, tray, plant, seed, authority, hands, level);
        }
        var otherAction = nonHarvestActions(actor, request, admission, tray, held, authority, hands, level, plant);
        if (otherAction != BodyBlockUseResult.Outcome.DENIED) return otherAction;
        if (held != null && !held.isEmpty()) {
            var resolvedTool = ToolQualities.profile(held);
            if (resolvedTool != null && resolvedTool.botanySampleTaker() != null)
                return BodyBlockUseResult.Outcome.DENIED;
            if (ToolQualities.isSharp(held)
                    && (request.intent() == BodyBlockUseRequest.Intent.USE || request.intent() == BodyBlockUseRequest.Intent.ACTIVATE_WORLD)
                    && plant != null && plant.seed() != null) {
                if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(body)
                        || !tray.isLoadedValid() || level.getBlockEntity(admission.hit().pos()) != tray
                        || !level.mayInteract(actor, tray.getBlockPos())) return BodyBlockUseResult.Outcome.DENIED;
                if (plant.dead()) { tray.commitClear(); return BodyBlockUseResult.Outcome.PLACED; }
                SeedProfile seed = plant.effectiveProfile();
                if (seed == null) seed = ModSeedProfiles.serverLookup(plant.seed());
                if (seed == null || !plant.ready()) return BodyBlockUseResult.Outcome.DENIED;
                return harvestReady(actor, request, admission, tray, plant, seed, authority, hands, level);
            }
        }
        return BodyBlockUseResult.Outcome.DENIED;
    }

    private static BodyBlockUseResult.Outcome harvestReady(ServerPlayer actor, BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admission, HydroponicsTrayBlockEntity tray, PlantInstance plant,
            SeedProfile seed, HandActorAuthority.Snapshot authority,
            com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands, ServerLevel level) {
            var body = authority.body();
            final SeedProfile harvestSeed = seed;
            var seedId = plant.seed(); long revision = plant.revision();
            int count = PlantMetabolismEvaluator.previewHarvestYield(plant, seed, 1.0f);
            // Source zero-yield harvests still execute AfterHarvest (which may alter
            // readiness/repeat state), but must not create a synthetic world item.
            if (count == 0) {
                if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(body)
                        || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                        || !hands.compatible(body) || hands.revision() != request.expectedRevision()
                        || !hands.activeHand().equals(request.handId())
                        || !java.util.Objects.equals(hands.token(request.handId()).orElse(null), request.expectedToken())
                        || !tray.isLoadedValid() || level.getBlockEntity(admission.hit().pos()) != tray
                        || tray.plantState() != plant || plant.revision() != revision || !plant.ready() || plant.dead())
                    return BodyBlockUseResult.Outcome.DENIED;
                return tray.commitHarvest(plant, revision, harvestSeed, 0)
                        ? BodyBlockUseResult.Outcome.PLACED : BodyBlockUseResult.Outcome.DENIED;
            }
            var outputs = new java.util.ArrayList<ItemStack>(count);
            try {
                for (int i = 0; i < count; i++) outputs.add(plant.seedReference() == null
                        ? ProduceFactory.createHarvest(plant.genetics() == null ? new GeneticValue(seedId, harvestSeed) : plant.genetics(), harvestSeed.lifecycle().potency())
                        : ProduceFactory.createHarvest(plant.seedReference(), harvestSeed.lifecycle().potency()));
            } catch (RuntimeException failure) { return BodyBlockUseResult.Outcome.DENIED; }
            var published = FreshWorldOutputBatch.publishBodyHarvest(level, body, outputs,
                    () -> HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(body)
                            && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                            && hands.compatible(body) && hands.revision() == request.expectedRevision()
                            && hands.activeHand().equals(request.handId())
                            && java.util.Objects.equals(hands.token(request.handId()).orElse(null), request.expectedToken())
                            && tray.isLoadedValid() && level.getBlockEntity(admission.hit().pos()) == tray
                            && tray.plantState() == plant && plant.revision() == revision && plant.ready() && !plant.dead(),
                    () -> { if (!tray.commitHarvest(plant, revision, harvestSeed, count)) throw new IllegalStateException("harvest source changed"); });
            return published == BodyHandItemTransfer.Result.SUCCESS ? BodyBlockUseResult.Outcome.PLACED
                    : published == BodyHandItemTransfer.Result.RECOVERY_REQUIRED
                    ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
    }

    private static BodyBlockUseResult.Outcome nonHarvestActions(ServerPlayer actor, BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admission, HydroponicsTrayBlockEntity tray, ItemStack held,
            HandActorAuthority.Snapshot authority, com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands,
            ServerLevel level, PlantInstance plant) {
        var body = authority.body();
        if (held != null && !held.isEmpty()) {
            // Capability is resolved through the inherited tool-profile catalog.
            if (ToolQualities.profile(held) != null
                    && ToolQualities.profile(held).botanySwab() != null)
                return BotanySwabHandUseService.start(actor, request, admission, tray, held);
            PlantSeedReference carriedReference = held.get(ModDataComponents.PLANT_SEED_REFERENCE.get());
            var packet = GeneticCarriers.resolveSeedPacket(held);
            GeneticValue carried = packet == null ? null : packet.genetics();
            Float healthOverride = packet == null ? null : packet.healthOverride();
            var seedId = carried == null ? null : carried.species();
            SeedProfile seed = packet == null ? null : packet.profile();
            if (seed != null) {
                if (plant == null || plant.seed() != null) return BodyBlockUseResult.Outcome.DENIED;
                if (!HandActorAuthority.revalidate(actor, authority) || !tray.isLoadedValid()
                        || level.getBlockEntity(admission.hit().pos()) != tray) return BodyBlockUseResult.Outcome.DENIED;
                var consumed = BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, authority,
                        request.handId(), request.expectedToken(), request.expectedRevision(), held);
                if (consumed != BodyHandItemTransfer.Result.SUCCESS) return BodyBlockUseResult.Outcome.DENIED;
                if (carriedReference != null) tray.commitPlant(carriedReference, level.getGameTime(), healthOverride);
                else tray.commitPlant(new PlantSeedReference(carried == null ? new GeneticValue(seedId, seed) : carried, false),
                        level.getGameTime(), healthOverride);
                return BodyBlockUseResult.Outcome.PLACED;
            }
            if (ToolQualities.hasHoe(held) && plant != null && plant.weeds() > 0) {
                if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(body)
                        || !tray.isLoadedValid() || level.getBlockEntity(admission.hit().pos()) != tray
                        || !level.mayInteract(actor, tray.getBlockPos())) return BodyBlockUseResult.Outcome.DENIED;
                plant.soil(plant.water(), plant.nutrition(), 0, plant.soilToxins()); tray.commitMutation();
                return BodyBlockUseResult.Outcome.PLACED;
            }
            if (ToolQualities.hasShovel(held) && plant != null && plant.seed() != null) {
                if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(body)
                        || !tray.isLoadedValid() || level.getBlockEntity(admission.hit().pos()) != tray
                        || !level.mayInteract(actor, tray.getBlockPos())) return BodyBlockUseResult.Outcome.DENIED;
                tray.commitClear(); return BodyBlockUseResult.Outcome.PLACED;
            }
            var toolProfile = ToolQualities.profile(held);
            if (toolProfile != null && toolProfile.botanySampleTaker() != null)
                return sample(actor, request, admission, tray, held, authority, hands, level, plant);
        }
        return BodyBlockUseResult.Outcome.DENIED;
    }

    private static BodyBlockUseResult.Outcome sample(ServerPlayer actor, BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admission, HydroponicsTrayBlockEntity tray, ItemStack held,
            HandActorAuthority.Snapshot authority, com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands,
            ServerLevel level, PlantInstance plant) {
        var hand = request.handId();
        var profile = ToolQualities.profile(held);
        var heldNow = hands.stackCopy(hand).orElse(null);
        if (profile == null || profile.botanySampleTaker() == null || heldNow == null || heldNow.isEmpty()
                || !ItemStack.isSameItemSameComponents(held, heldNow) || held.getCount() != heldNow.getCount()
                || plant == null || plant.seed() == null || plant.seedReference() == null) return BodyBlockUseResult.Outcome.DENIED;
        var reference = plant.seedReference();
        var geneticValue = reference.current();
        var seed = geneticValue.profile();
        if (seed == null) return BodyBlockUseResult.Outcome.DENIED;
        if (!PlantSamplingPlan.isEligible(plant)) return BodyBlockUseResult.Outcome.DENIED;
        var placement = tray.placementIdentity();
        long plantRevision = plant.revision();
        java.util.function.BooleanSupplier current = () -> HandActorAuthority.revalidate(actor, authority)
                && CharacterControlSystem.canAct(authority.body())
                && authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                && hands.compatible(authority.body()) && hands.revision() == request.expectedRevision()
                && hands.activeHand().equals(hand)
                && java.util.Objects.equals(hands.token(hand).orElse(null), request.expectedToken())
                && ToolQualities.profile(held) == profile
                && hands.stackCopy(hand).filter(stack -> ItemStack.isSameItemSameComponents(held, stack)
                        && stack.getCount() == held.getCount()).isPresent()
                && tray.isLoadedValid() && tray.placementIdentity().equals(placement)
                && level.getBlockEntity(admission.hit().pos()) == tray && tray.getBlockPos().equals(admission.hit().pos())
                && level.mayInteract(actor, tray.getBlockPos()) && tray.plantState() == plant
                && plant.revision() == plantRevision && plant.seedReference() == reference
                && reference.current() == geneticValue && PlantSamplingPlan.isEligible(plant);
        if (!current.getAsBoolean()) return BodyBlockUseResult.Outcome.DENIED;
        int damageRoll = level.random.nextInt(3, 5);
        var plan = PlantSamplingPlan.prepare(plant, damageRoll);
        if (plan == null) return BodyBlockUseResult.Outcome.DENIED;
        ItemStack packet = GeneticCarriers.createSeedPacket(reference, plan.packetHealthOverride());
        if (packet.isEmpty()) return BodyBlockUseResult.Outcome.DENIED;
        var audio = PlantScreamAudio.prepare(seed);
        long[] committedRevision = {-1};
        boolean[] sourceCommitted = {false};
        var result = FreshWorldOutputBatch.publishBodyHarvest(level, authority.body(), java.util.List.of(packet), current,
                () -> {
                    if (!tray.commitSamplingHealth(placement, plant, plantRevision, reference,
                            plant.health() - plan.healthLoss())) throw new IllegalStateException("sampling source changed");
                    committedRevision[0] = plant.revision();
                    sourceCommitted[0] = true;
                }, (entity, stack) -> BodyHandItemTransfer.pickupTrustedCreated(authority, entity, stack,
                        () -> HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(authority.body())
                                && tray.isLoadedValid() && tray.placementIdentity().equals(placement)
                                && tray.plantState() == plant && plant.seedReference() == reference
                                && plant.revision() == committedRevision[0]));
        if (!sourceCommitted[0]) return result == BodyHandItemTransfer.Result.RECOVERY_REQUIRED
                ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;

        boolean finalized;
        try {
            try { actor.sendSystemMessage(net.minecraft.network.chat.Component.literal("You collect a seed sample.")); }
            catch (RuntimeException ignored) { }
            PlantScreamAudio.play(level, tray.getBlockPos(), audio);
        } finally {
            boolean sampledRoll = level.random.nextFloat() < .3f;
            finalized = tray.finishSampling(placement, plant, committedRevision[0], reference, sampledRoll,
                    seed.biology().endurance());
        }
        if (!finalized) return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
        return result == BodyHandItemTransfer.Result.RECOVERY_REQUIRED
                ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.PLACED;
    }
}
