package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import java.util.UUID;

/** Server-only admission for guns owned by the body's active LiveHands slot. */
public final class GunWeaponResolver {
    public record Selection(UUID body, long handRevision, String activeHand, String itemToken, GunProfile profile) { }
    private GunWeaponResolver() { }

    public static Optional<Selection> resolve(LivingEntity body) {
        if (body == null || !(body.level() instanceof ServerLevel)) return Optional.empty();
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body)) return Optional.empty();
        String hand = hands.activeHand();
        String token = hands.token(hand).orElse(null);
        if (token == null) return Optional.empty();
        ItemStack stack = LiveOwnedItemAccess.read(body, new LiveOwnedItemAccess.Binding(
                LiveOwnedItemAccess.Location.HELD, hand, hands.revision(), token)).map(LiveOwnedItemAccess.OwnerView::stack).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) return Optional.empty();
        GunProfile profile = ModGunProfiles.serverCatalog().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return profile == null ? Optional.empty() : Optional.of(new Selection(body.getUUID(), hands.revision(), hand, token, profile));
    }

    public static boolean revalidate(LivingEntity body, Selection selection) {
        return body != null && selection != null && body.getUUID().equals(selection.body())
                && resolve(body).filter(selection::equals).isPresent();
    }

    /** Reads components only through the selected, revision/token-bound held owner. */
    public static Optional<LiveOwnedItemAccess.OwnerView> readOwned(LivingEntity body, Selection selection) {
        if (body == null || selection == null || !revalidate(body, selection)) return Optional.empty();
        return LiveOwnedItemAccess.read(body, new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD,
                selection.activeHand(), selection.handRevision(), selection.itemToken()))
                ;
    }
}
