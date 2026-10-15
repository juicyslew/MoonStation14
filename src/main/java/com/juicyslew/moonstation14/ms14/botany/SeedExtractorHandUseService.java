package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Extracts seeds from one admitted produce unit; fresh outputs become world owners before consumption. */
public final class SeedExtractorHandUseService {
    private SeedExtractorHandUseService() { }

    public static BodyBlockUseResult.Outcome use(ServerPlayer actor, BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admission, SeedExtractorBlockEntity machine, ItemStack held) {
        if (actor == null || request == null || admission == null || machine == null || held == null || held.isEmpty()
                || request.intent() != BodyBlockUseRequest.Intent.USE
                || !(admission.authority().body().level() instanceof ServerLevel level))
            return BodyBlockUseResult.Outcome.DENIED;
        var authority = admission.authority(); var body = authority.body(); var hands = admission.hands();
        var placementIdentity = machine.placementIdentity();
        var profile = machine.resolvedProfile();
        if (!level.getServer().isSameThread() || !machine.isLoadedValid() || !machine.isPowered()
                || level.getBlockEntity(admission.hit().pos()) != machine || !level.mayInteract(actor, machine.getBlockPos())
                || !CharacterControlSystem.canAct(body) || !HandActorAuthority.revalidate(actor, authority)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands || !hands.compatible(body)
                || hands.revision() != request.expectedRevision() || !hands.activeHand().equals(request.handId())
                || !BodyHandRequestService.blockUseHandStateMatches(request.expectedToken(),
                    hands.token(request.handId()).isPresent(), hands.stackCopy(request.handId()).isPresent(),
                    hands.token(request.handId()).orElse(null)) || profile == null || !profile.extractor()
                || profile.minimumPacketYield() < 1 || profile.maximumPacketYield() > 64
                || placementIdentity == null) return BodyBlockUseResult.Outcome.DENIED;

        var produceProfiles = ModProduceProfiles.serverCatalog();
        var produce = produceProfiles == null ? null : produceProfiles.get(BuiltInRegistries.ITEM.getKey(held.getItem()));
        if (produce == null) return BodyBlockUseResult.Outcome.DENIED;
        var instance = held.get(ModDataComponents.PRODUCE_INSTANCE.get());
        var seedId = instance == null ? produce.seed() : instance.seed();
        var seeds = ModSeedProfiles.serverCatalog();
        var seed = seeds == null || seedId == null ? null : seeds.get(seedId);
        if (seed == null || instance != null && Float.compare(instance.baselinePotency(), produceLifecyclePotency(produce, seed)) != 0)
            return BodyBlockUseResult.Outcome.DENIED;
        var packet = BuiltInRegistries.ITEM.get(seed.packet());
        if (packet == null || packet == net.minecraft.world.item.Items.AIR
                || !BuiltInRegistries.ITEM.getKey(packet).equals(seed.packet())) return BodyBlockUseResult.Outcome.DENIED;
        int count = level.random.nextInt(profile.minimumPacketYield(), profile.maximumPacketYield() + 1);
        var outputs = new java.util.ArrayList<ItemStack>(count);
        for (int i = 0; i < count; i++) outputs.add(new ItemStack(packet));
        var token = request.expectedToken(); long revision = request.expectedRevision();
        var result = FreshWorldOutputBatch.publish(level, body, machine.getBlockPos(), outputs,
                () -> HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(body)
                        && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands && hands.compatible(body)
                        && hands.revision() == revision && hands.activeHand().equals(request.handId())
                        && java.util.Objects.equals(hands.token(request.handId()).orElse(null), token)
                        && hands.stackCopy(request.handId()).filter(s -> ItemStack.isSameItemSameComponents(s, held)
                                && s.getCount() == held.getCount()).isPresent()
                         && machine.isLoadedValid() && machine.isPowered() && placementIdentity.equals(machine.placementIdentity())
                        && machine.resolvedProfile() == profile && level.getBlockEntity(machine.getBlockPos()) == machine,
                () -> {
                    var consumed = BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, authority, request.handId(), token, revision, held);
                    if (consumed != BodyHandItemTransfer.Result.SUCCESS) throw new IllegalStateException("extractor source consume failed: " + consumed);
                }, (entity, expected) -> {
                    return BodyHandItemTransfer.pickupTrustedCreated(authority, entity, expected,
                                () -> machine.isLoadedValid() && machine.isPowered() && placementIdentity.equals(machine.placementIdentity()));
                });
        return result == BodyHandItemTransfer.Result.SUCCESS ? BodyBlockUseResult.Outcome.PLACED
                : result == BodyHandItemTransfer.Result.RECOVERY_REQUIRED ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED
                : BodyBlockUseResult.Outcome.DENIED;
    }

    private static float produceLifecyclePotency(ProduceProfile produce, SeedProfile seed) {
        return seed.lifecycle().potency();
    }
}
