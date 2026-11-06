package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Trusted GameTest item issuance through the normal live-hand commit and owner-query seams. */
public final class FixtureHeldTool {
    private FixtureHeldTool() { }

    public record Issued(String hand, String token, long revision, ItemStack stack) { }

    public static Issued issue(ServerPlayer actor, String hand, ItemStack stack) {
        if (actor == null || hand == null || hand.isBlank() || stack == null || stack.isEmpty())
            throw new IllegalArgumentException("fixture tool issuance requires actor, hand, and nonempty stack");
        var authority = HandActorAuthority.resolve(actor).orElseThrow(
                () -> new IllegalStateException("fixture tool owner has no live body authority"));
        var body = authority.body();
        if (!HandActorAuthority.revalidate(actor, authority))
            throw new IllegalStateException("fixture tool owner authority changed");
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (current == null || !current.compatible(body) || !current.handIds().contains(hand)
                || current.stackCopy(hand).isPresent() || current.token(hand).isPresent())
            throw new IllegalStateException("fixture tool requires an empty issued hand");
        String token = UUID.randomUUID().toString();
        LiveHands held = current.putWhole(current.revision(), hand, new ItemToken(token), stack).state();
        if (!LiveHandsCommitGateway.commitTrusted(body, current, held))
            throw new IllegalStateException("trusted fixture hand commit rejected");
        BodyHandStateService.requireFreshQuery(actor);
        LiveHands committed = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (committed != held || !committed.compatible(body) || committed.revision() != held.revision()
                || !committed.activeHand().equals(hand) || !committed.token(hand).filter(token::equals).isPresent()
                || !committed.stackCopy(hand).filter(value -> ItemStack.isSameItemSameComponents(value, stack)).isPresent()
                || !HandActorAuthority.revalidate(actor, authority))
            throw new IllegalStateException("issued tool did not survive owner-authorized publication");
        return new Issued(hand, token, committed.revision(), stack.copy());
    }
}
