package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Objects;

/** Authenticated one-use conversion of a held item into fresh world outputs. */
public final class HeldItemConversionService {
    private HeldItemConversionService() { }

    /** Null means this resolved item has no conversion capability; callers may continue other use handlers. */
    public static BodyHandActionResult.Reason tryUse(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands hands, BodyHandActionRequest request, ItemStack held) {
        if (actor == null || authority == null || hands == null || request == null || held == null || held.isEmpty())
            return BodyHandActionResult.Reason.DENIED;
        var itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        var profile = ModItemProcessingProfiles.serverLookup(itemId);
        if (profile == null || profile.spawnItemsOnUse() == null) return null;
        var conversion = profile.spawnItemsOnUse();
        var body = authority.body();
        if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || request.action() != BodyHandActionRequest.Action.USE_IN_HAND
                || !CharacterControlSystem.canAct(body) || !HandActorAuthority.revalidate(actor, authority)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(body) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand())
                || !BodyHandRequestService.blockUseHandStateMatches(request.expectedToken(),
                    hands.token(request.handId()).isPresent(), hands.stackCopy(request.handId()).isPresent(),
                    hands.token(request.handId()).orElse(null))
                || !matchesHeld(hands, request, held) || !currentProfile(itemId, profile, conversion))
            return BodyHandActionResult.Reason.DENIED;

        var outputs = prepareOutputs(conversion);
        if (outputs == null || outputs.isEmpty()) return BodyHandActionResult.Reason.DENIED;
        long revision = request.expectedRevision();
        String token = request.expectedToken();
        ItemStack expected = held.copy();
        var outputIndex = new java.util.concurrent.atomic.AtomicInteger();
        var result = FreshWorldOutputBatch.publishAtSourcePosition(level, body.position(), outputs,
                () -> current(actor, authority, hands, request, expected, profile, conversion, itemId),
                () -> BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, authority,
                        request.handId(), token, revision, expected),
                (entity, expectedOutput) -> {
                    // Only the final output attempts hand pickup; failure there leaves the published item in-world.
                    if (outputIndex.getAndIncrement() == outputs.size() - 1)
                        return BodyHandItemTransfer.pickupTrustedCreated(authority, entity, expectedOutput,
                                () -> HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(body));
                    return BodyHandItemTransfer.Result.SUCCESS;
                });
        if (result == BodyHandItemTransfer.Result.SUCCESS) playSound(level, body, conversion);
        return switch (result) {
            case SUCCESS -> BodyHandActionResult.Reason.OK;
            case DENIED -> BodyHandActionResult.Reason.DENIED;
            case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        };
    }

    static java.util.List<ItemStack> prepareOutputs(ItemProcessingProfile.SpawnItemsOnUse conversion) {
        if (conversion == null) return null;
        try { conversion.validate(); }
        catch (RuntimeException invalid) { return null; }
        var ids = new ArrayList<net.minecraft.resources.ResourceLocation>();
        for (var output : conversion.outputs()) {
            if (output == null || output.count() <= 0 || (long) ids.size() + output.count() > 65535) return null;
            for (int i = 0; i < output.count(); i++) ids.add(output.output());
        }
        var stacks = FreshItemOutputStacks.build(ids);
        return stacks == null || stacks.isEmpty() ? null : stacks;
    }

    private static boolean matchesHeld(LiveHands hands, BodyHandActionRequest request, ItemStack expected) {
        var current = hands.stackCopy(request.handId()).orElse(null);
        return current != null && ItemStack.isSameItemSameComponents(current, expected)
                && current.getCount() == expected.getCount();
    }

    private static boolean currentProfile(net.minecraft.resources.ResourceLocation itemId, ItemProcessingProfile profile,
            ItemProcessingProfile.SpawnItemsOnUse conversion) {
        var catalog = ModItemProcessingProfiles.serverCatalog();
        return catalog != null && catalog.get(itemId) == profile && profile.spawnItemsOnUse() == conversion;
    }

    private static boolean current(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands hands,
            BodyHandActionRequest request, ItemStack held, ItemProcessingProfile profile,
            ItemProcessingProfile.SpawnItemsOnUse conversion, net.minecraft.resources.ResourceLocation itemId) {
        var body = authority.body();
        return HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(body)
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands && hands.compatible(body)
                && hands.revision() == request.expectedRevision() && request.handId().equals(hands.activeHand())
                && Objects.equals(hands.token(request.handId()).orElse(null), request.expectedToken())
                && matchesHeld(hands, request, held) && currentProfile(itemId, profile, conversion);
    }

    private static void playSound(ServerLevel level, net.minecraft.world.entity.LivingEntity body,
            ItemProcessingProfile.SpawnItemsOnUse conversion) {
        if (conversion.sound() == null) return;
        try {
            var sound = BuiltInRegistries.SOUND_EVENT.get(conversion.sound());
            if (sound != null && BuiltInRegistries.SOUND_EVENT.getKey(sound).equals(conversion.sound()))
                PositionalSoundRouter.play(level, conversion.sound(), net.minecraft.sounds.SoundSource.PLAYERS,
                        body.getX(), body.getY(), body.getZ(), 1f, 1f);
        } catch (RuntimeException ignored) { /* committed conversion remains successful if its cue is unavailable */ }
    }
}
