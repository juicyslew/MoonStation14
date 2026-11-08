package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/** Narrow trusted owner for mutable held/glove melee item state. */
public final class MeleeItemOwner {
    public record OwnerView(LivingEntity body, MeleeWeaponResolver.Selection selection,
            LiveHands hands, ItemToken token, ItemStack stack, LiveOwnedItemAccess.OwnerView owned) {
        public OwnerView { stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    public record Committed(MeleeWeaponResolver.Selection selection, long revision) { }
    private MeleeItemOwner() { }

    public static Optional<OwnerView> read(LivingEntity body, MeleeWeaponResolver.Selection selection) {
        if (!serverThread(body) || selection == null || selection.kind() == MeleeWeaponResolver.Kind.UNARMED
                || !MeleeWeaponResolver.revalidate(body, selection)) return Optional.empty();
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || selection.itemToken().isEmpty()) return Optional.empty();
        var location = selection.kind() == MeleeWeaponResolver.Kind.HELD ? LiveOwnedItemAccess.Location.HELD : LiveOwnedItemAccess.Location.EQUIPMENT;
        String slot = selection.kind() == MeleeWeaponResolver.Kind.HELD ? selection.activeHand() : com.juicyslew.moonstation14.ms14.equipment.EquipmentSlots.GLOVES;
        var owned = LiveOwnedItemAccess.read(body, new LiveOwnedItemAccess.Binding(location, slot, hands.revision(), selection.itemToken().orElseThrow())).orElse(null);
        return owned == null ? Optional.empty() : Optional.of(new OwnerView(body, selection, hands, new ItemToken(selection.itemToken().orElseThrow()), owned.stack(), owned));
    }

    public static Optional<Committed> commitComponents(OwnerView view, ItemStack replacement) {
        if (view == null) return Optional.empty();
        var committed = LiveOwnedItemAccess.commitComponents(view.owned(), replacement,
                () -> MeleeWeaponResolver.revalidate(view.body(), view.selection())).orElse(null);
        if (committed == null) return Optional.empty();
        LiveHands candidate = committed.state();
        MeleeWeaponResolver.Selection refreshed = MeleeWeaponResolver.resolve(view.body()).orElseGet(() ->
                new MeleeWeaponResolver.Selection(view.selection().kind(), view.selection().profile(), view.selection().body(),
                        candidate.revision(), view.selection().activeHand(), view.selection().itemToken()));
        return Optional.of(new Committed(refreshed, candidate.revision()));
    }

    /** Destruction consumes the entire original owned stack, never a single item unit. */
    public static Optional<Committed> removeWhole(OwnerView view) {
        if (view == null || view.selection() == null || !serverThread(view.body())
                || view.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != view.hands()
                || !MeleeWeaponResolver.revalidate(view.body(), view.selection())) return Optional.empty();
        ItemStack expected = view.stack();
        LiveHands candidate;
        if (view.selection().kind() == MeleeWeaponResolver.Kind.HELD) {
            ItemStack current = view.hands().stackCopy(view.selection().activeHand()).orElse(ItemStack.EMPTY);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, expected) || current.getCount() != expected.getCount()
                    || !view.hands().token(view.selection().activeHand()).equals(view.selection().itemToken())) return Optional.empty();
            candidate = view.hands().clearHeldWhole(view.hands().revision(), view.selection().activeHand(), view.token()).orElse(null);
        } else if (view.selection().kind() == MeleeWeaponResolver.Kind.GLOVES) {
            var equipment = LiveHands.equipmentSnapshot(view.body()).orElse(null);
            var declared = HandCapability.resolveEquipmentSlots(view.body()).orElse(null);
            var template = HandCapability.resolveEquipmentTemplate(view.body()).orElse(null);
            String slot = com.juicyslew.moonstation14.ms14.equipment.EquipmentSlots.GLOVES;
            ItemStack current = view.hands().equipmentStack(slot).orElse(ItemStack.EMPTY);
            if (equipment == null || declared == null || template == null || !equipment.persisted()
                    || current.isEmpty() || !ItemStack.isSameItemSameComponents(current, expected) || current.getCount() != expected.getCount()
                    || !equipment.slots().stream().anyMatch(s -> s.id().equals(slot) && s.token().filter(view.token().value()::equals).isPresent())) return Optional.empty();
            var removal = view.hands().removeEquipmentToExternal(view.hands().revision(), declared, template, slot, view.token().value()).orElse(null);
            candidate = removal == null || !removal.drops().isEmpty() ? null : removal.state();
        } else return Optional.empty();
        if (candidate == null) return Optional.empty();
        if (candidate == null) return Optional.empty();
        try { LiveHandsCommitGateway.commitOwned(view.body(), view.hands(), candidate); }
        catch (IllegalStateException rejected) { return Optional.empty(); }
        MeleeWeaponResolver.Selection refreshed = MeleeWeaponResolver.resolve(view.body()).orElse(null);
        return Optional.of(new Committed(refreshed, candidate.revision()));
    }

    private static boolean serverThread(LivingEntity body) {
        return body != null && body.level() instanceof ServerLevel && body.level().getServer() != null
                && body.level().getServer().isSameThread();
    }
}
