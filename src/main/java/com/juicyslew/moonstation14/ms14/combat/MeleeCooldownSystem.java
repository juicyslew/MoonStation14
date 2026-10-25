package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Trusted server-thread owner of the melee source cooldown commit. */
public final class MeleeCooldownSystem {
    public record Consumed(MeleeWeaponResolver.Selection selection, long handRevision) { }
    public record Finished(MeleeWeaponResolver.Selection selection, boolean swingLeft) { }
    public static boolean toggleSwingLeft(boolean prior) { return !prior; }
    private record Owner(LiveHands hands, ItemStack stack, MeleeCooldownData data) { }
    private MeleeCooldownSystem() { }

    /** Read-only cooldown query for attack admission; absent means the selection/owner was invalid. */
    public static java.util.OptionalLong nextAttackTick(LivingEntity body, MeleeWeaponResolver.Selection expected) {
        if (!serverThread(body) || !MeleeWeaponResolver.revalidate(body, expected)) return java.util.OptionalLong.empty();
        Owner owner = resolveOwner(body, expected);
        return owner == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(owner.data().nextAttackTick());
    }

    public static boolean ready(LivingEntity body, MeleeWeaponResolver.Selection expected, long nowTick) {
        var deadline = nextAttackTick(body, expected);
        return deadline.isPresent() && nowTick >= deadline.getAsLong();
    }

    /** Commits the source's SS14 post-attempt direction toggle, including canceled post-attempts. */
    public static java.util.Optional<Finished> finishAttempt(LivingEntity body, MeleeWeaponResolver.Selection current) {
        if (!serverThread(body) || current == null || !MeleeWeaponResolver.revalidate(body, current)) return java.util.Optional.empty();
        if (current.kind() == MeleeWeaponResolver.Kind.UNARMED) {
            var attachment = body.getExistingDataOrNull(ModDataAttachments.MELEE_COOLDOWN.get());
            var prior = attachment == null ? new MeleeCooldownData(0) : attachment.data();
            boolean toggled = toggleSwingLeft(prior.swingLeft());
            body.setData(ModDataAttachments.MELEE_COOLDOWN.get(), new MeleeCooldownAttachment(new MeleeCooldownData(prior.nextAttackTick(), toggled)));
            return MeleeWeaponResolver.resolve(body).map(selection -> new Finished(selection, toggled));
        }
        var view = MeleeItemOwner.read(body, current).orElse(null);
        if (view == null) return java.util.Optional.empty();
        var prior = view.stack().get(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get());
        if (prior == null) prior = new MeleeCooldownData(0);
        boolean toggled = toggleSwingLeft(prior.swingLeft());
        var updated = new MeleeCooldownData(prior.nextAttackTick(), toggled);
        var replacement = view.stack();
        replacement.set(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get(), updated);
        var committed = MeleeItemOwner.commitComponents(view, replacement).orElse(null);
        return committed == null ? java.util.Optional.empty() : java.util.Optional.of(new Finished(committed.selection(), toggled));
    }

    public static java.util.Optional<Consumed> consume(LivingEntity body,
            MeleeWeaponResolver.Selection expected, long nowTick) {
        if (!serverThread(body) || !MeleeWeaponResolver.revalidate(body, expected))
            return java.util.Optional.empty();
        var current = MeleeWeaponResolver.resolve(body).orElse(null);
        if (current == null || !current.equals(expected)) return java.util.Optional.empty();
        Owner owner = resolveOwner(body, current);
        if (owner == null) return java.util.Optional.empty();
        LiveHands hands = owner.hands();
        ItemStack stack = owner.stack();
        MeleeCooldownData prior = owner.data();
        if (nowTick < prior.nextAttackTick()) return java.util.Optional.empty();
        var deadline = MeleeCooldownData.deadline(nowTick, current.weapon().attackRate());
        if (deadline.isEmpty()) return java.util.Optional.empty();
        MeleeCooldownData updated = prior.withDeadline(deadline.getAsLong());
        if (current.kind() == MeleeWeaponResolver.Kind.UNARMED) {
            body.setData(ModDataAttachments.MELEE_COOLDOWN.get(), new MeleeCooldownAttachment(updated));
            return java.util.Optional.of(new Consumed(current, hands.revision()));
        }
        var view = MeleeItemOwner.read(body, current).orElse(null);
        if (view == null) return java.util.Optional.empty();
        ItemStack replacement = view.stack();
        replacement.set(ModDataComponents.MELEE_COOLDOWN.get(), updated);
        var committed = MeleeItemOwner.commitComponents(view, replacement).orElse(null);
        if (committed == null) return java.util.Optional.empty();
        var candidateRevision = committed.revision();
        var refreshed = committed.selection();
        if (refreshed == null || refreshed.kind() != current.kind()
                || !refreshed.itemToken().equals(current.itemToken()) || !refreshed.weapon().equals(current.weapon()))
            return java.util.Optional.of(new Consumed(new MeleeWeaponResolver.Selection(current.kind(), current.profile(),
                    current.body(), candidateRevision, current.activeHand(), current.itemToken()), candidateRevision));
        return java.util.Optional.of(new Consumed(refreshed, refreshed.handRevision()));
    }

    private static boolean serverThread(LivingEntity body) {
        return body != null && body.level() instanceof ServerLevel && body.level().getServer() != null
                && body.level().getServer().isSameThread();
    }

    private static Owner resolveOwner(LivingEntity body, MeleeWeaponResolver.Selection source) {
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (source.kind() == MeleeWeaponResolver.Kind.UNARMED) {
            var attachment = body.getExistingDataOrNull(ModDataAttachments.MELEE_COOLDOWN.get());
            return new Owner(hands, null, attachment == null ? new MeleeCooldownData(0) : attachment.data());
        }
        if (hands == null || source.itemToken().isEmpty()) return null;
        ItemStack stack = source.kind() == MeleeWeaponResolver.Kind.HELD
                ? hands.stackCopy(source.activeHand()).orElse(null)
                : hands.equipmentStack(com.juicyslew.moonstation14.ms14.equipment.EquipmentSlots.GLOVES).orElse(null);
        if (stack == null || stack.isEmpty()) return null;
        MeleeCooldownData value = stack.get(ModDataComponents.MELEE_COOLDOWN.get());
        return new Owner(hands, stack, value == null ? new MeleeCooldownData(0) : value);
    }
}
