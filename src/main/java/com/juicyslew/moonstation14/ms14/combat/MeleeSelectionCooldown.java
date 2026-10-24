package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;

/** Detached stack staging for SS14-style hand-selection cooldowns. */
public final class MeleeSelectionCooldown {
    private MeleeSelectionCooldown() { }

    public static ItemStack stage(ItemStack source, MeleeWeaponProfile profile, long nowTick) {
        if (source == null || source.isEmpty() || profile == null || !profile.weapon().resetOnHandSelected()) return source;
        var deadline = MeleeCooldownData.deadline(nowTick, profile.weapon().attackRate());
        if (deadline.isEmpty()) return source;
        ItemStack staged = source.copy();
        MeleeCooldownData prior = staged.get(ModDataComponents.MELEE_COOLDOWN.get());
        long next = Math.max(prior == null ? 0 : prior.nextAttackTick(), deadline.getAsLong());
        staged.set(ModDataComponents.MELEE_COOLDOWN.get(), new MeleeCooldownData(next, prior != null && prior.swingLeft()));
        return staged;
    }

    /** Caller has established that the selected hand receives a newly inserted item identity. */
    public static ItemStack stageForHand(LivingEntity body, LiveHands before, String destinationHand,
                                         ItemStack detachedStack) {
        if (body == null || before == null || destinationHand == null || detachedStack == null
                || !destinationHand.equals(before.activeHand())) return detachedStack;
        return stageForSelectedInsertion(body, detachedStack);
    }

    /** Stages an insertion whose selected-hand identity change has already been established by its caller. */
    public static ItemStack stageForSelectedInsertion(LivingEntity body, ItemStack detachedStack) {
        if (body == null || detachedStack == null || detachedStack.isEmpty()) return detachedStack;
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(detachedStack.getItem());
        var profile = id == null ? null : ModMeleeWeaponProfiles.serverCatalog().get(id);
        if (profile == null) return detachedStack;
        long tick = body.level().getGameTime();
        ItemStack staged = stage(detachedStack, profile, tick);
        return staged == detachedStack ? detachedStack : staged;
    }

    /** Shared candidate identity decision and staging path for hand and equipment insertions. */
    public static LiveHands stageSelectedInsertion(LivingEntity body, LiveHands before, LiveHands candidate) {
        if (before == null || candidate == null) return candidate;
        String hand = candidate.activeHand();
        ItemStack selected = candidate.stackCopy(hand).orElse(null);
        if (selected == null || selected.isEmpty()) return candidate;
        boolean changed = selectionChanged(before.activeHand(), hand, before.token(hand).orElse(null),
                candidate.token(hand).orElse(null), before.stackCopy(hand).map(old -> old.getItem() == selected.getItem()).orElse(false), true);
        if (!changed) return candidate;
        ItemStack staged = stageForSelectedInsertion(body, selected);
        if (staged == selected) return candidate;
        var token = candidate.token(hand).map(com.juicyslew.moonstation14.ms14.hands.ItemToken::new).orElse(null);
        return token == null ? candidate : candidate.replaceOwnedComponents(candidate.revision(), hand, token, staged)
                .orElse(candidate);
    }

    /** Pure identity predicate; stack component mutations do not count as insertion. */
    public static boolean selectionChanged(String beforeActive, String afterActive, String oldToken,
                                           String newToken, boolean sameItem, boolean occupied) {
        return occupied && (!java.util.Objects.equals(beforeActive, afterActive)
                || !java.util.Objects.equals(oldToken, newToken) || !sameItem);
    }
}
