package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataAttachments;
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
            SeedProfile seed = ModSeedProfiles.serverLookup(plant.seed());
            if (seed == null || !plant.ready()) return BodyBlockUseResult.Outcome.DENIED;
            var seedId = plant.seed(); long revision = plant.revision();
            int count = PlantMetabolismEvaluator.previewHarvestYield(plant, seed, 1.0f);
            var outputs = new java.util.ArrayList<ItemStack>(count);
            try {
                for (int i = 0; i < count; i++) outputs.add(ProduceFactory.createHarvest(seedId, seed, seed.lifecycle().potency()));
            } catch (RuntimeException failure) { return BodyBlockUseResult.Outcome.DENIED; }
            var published = FreshWorldOutputBatch.publishBodyHarvest(level, body, outputs,
                    () -> HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(body)
                            && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                            && hands.compatible(body) && hands.revision() == request.expectedRevision()
                            && hands.activeHand().equals(request.handId())
                            && java.util.Objects.equals(hands.token(request.handId()).orElse(null), request.expectedToken())
                            && tray.isLoadedValid() && level.getBlockEntity(admission.hit().pos()) == tray
                            && tray.plantState() == plant && plant.revision() == revision && plant.ready() && !plant.dead(),
                    () -> { if (!tray.commitHarvest(plant, revision, seed)) throw new IllegalStateException("harvest source changed"); });
            return published == BodyHandItemTransfer.Result.SUCCESS ? BodyBlockUseResult.Outcome.PLACED
                    : published == BodyHandItemTransfer.Result.RECOVERY_REQUIRED
                    ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
        }
        if (held != null && !held.isEmpty()) {
            var seedId = ModSeedProfiles.serverCatalog().asMap().entrySet().stream()
                    .filter(entry -> entry.getValue().packet().equals(BuiltInRegistries.ITEM.getKey(held.getItem())))
                    .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
            SeedProfile seed = seedId == null ? null : ModSeedProfiles.serverLookup(seedId);
            if (seed != null) {
                if (plant == null || plant.seed() != null) return BodyBlockUseResult.Outcome.DENIED;
                if (!HandActorAuthority.revalidate(actor, authority) || !tray.isLoadedValid()
                        || level.getBlockEntity(admission.hit().pos()) != tray) return BodyBlockUseResult.Outcome.DENIED;
                var consumed = BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, authority,
                        request.handId(), request.expectedToken(), request.expectedRevision(), held);
                if (consumed != BodyHandItemTransfer.Result.SUCCESS) return BodyBlockUseResult.Outcome.DENIED;
                tray.commitPlant(seedId, seed, level.getGameTime());
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
        }
        return BodyBlockUseResult.Outcome.DENIED;
    }
}
