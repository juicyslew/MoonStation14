package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/** One body-owned attachment publication; equipment is never mirrored into vanilla slots. */
public final class BodyEquipmentTransfer {
    private BodyEquipmentTransfer() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result equip(ServerPlayer actor, String hand, String slot, String expectedToken,
                               long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), hand, slot, expectedToken, expectedRevision,
                true, () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result unequip(ServerPlayer actor, String slot, String hand, String expectedToken,
                                 long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), hand, slot, expectedToken, expectedRevision,
                false, () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    // Trusted fixture seam: tests supply validity for a real spawned body, never a FakePlayer authority.
    static Result transferOwned(LivingEntity body, String hand, String slot, String expectedToken,
                                long revision, boolean equipping, BooleanSupplier authorityValid) {
        if (body == null || hand == null || slot == null || expectedToken == null || authorityValid == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !live(body, level)
                || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision)
            return Result.DENIED;
        // The bound prototype, not a saved layout or client-provided name, declares this slot.
        Optional<LiveHands.EquipmentSnapshot> snapshot = LiveHands.equipmentSnapshot(body);
        if (snapshot.isEmpty()) return Result.DENIED;
        Optional<LiveHands.EquipmentSlot> target = snapshot.orElseThrow().slots().stream()
                .filter(candidate -> candidate.id().equals(slot)).findFirst();
        if (target.isEmpty()) return Result.DENIED;
        ItemStack stack;
        try {
            if (equipping) {
                if (target.orElseThrow().token().isPresent() || target.orElseThrow().stack().isPresent()
                        || !before.token(hand).filter(expectedToken::equals).isPresent()) return Result.DENIED;
                stack = before.stackCopy(hand).orElseThrow();
            } else {
                if (!target.orElseThrow().token().filter(expectedToken::equals).isPresent()) return Result.DENIED;
                stack = target.orElseThrow().stack().orElseThrow();
            }
        } catch (IllegalArgumentException | java.util.NoSuchElementException invalid) {
            return Result.DENIED;
        }
        if (!eligible(slot, stack)) return Result.DENIED;
        Optional<LiveHands> next = equipping
                ? before.equip(revision, body, hand, slot, expectedToken)
                : before.unequip(revision, body, slot, hand, expectedToken);
        if (next.isEmpty() || !authorityValid.getAsBoolean() || !live(body, level)
                || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || !eligible(slot, stack)) return Result.DENIED;
        try {
            body.setData(ModDataAttachments.LIVE_HANDS.get(), next.orElseThrow());
        } catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                && next.orElseThrow().compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    private static boolean eligible(String slot, ItemStack stack) {
        return stack != null && stack.getCount() == 1
                && (slot.equals("belt") && stack.is(ModItems.BELT)
                    || slot.equals("back") && stack.is(ModItems.BAG))
                && PouchContents.read(stack).isPresent();
    }

    private static boolean live(LivingEntity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body;
    }
}
