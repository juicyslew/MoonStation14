package com.juicyslew.moonstation14.ms14.loose_objects;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Live supported damage/timer slice for untrusted empty-hand pickup contact. */
public final class ItemContactDamageSystem {
    private ItemContactDamageSystem() { }
    public enum Decision { HANDLED, NOT_HANDLED }

    public static Decision onContact(LivingEntity target, LooseItemEntity source, LooseObjectProfile.DamageOnInteractData data) {
        if (target == null || source == null || data == null || target.level().isClientSide()) return Decision.NOT_HANDLED;
        var timer = source.getData(ModDataAttachments.ITEM_CONTACT_TIMER);
        long now = source.level().getGameTime();
        if (now < timer.nextInteractionTick()) return Decision.HANDLED;
        if (!data.damageActive()) return Decision.NOT_HANDLED;
        // Upstream IgnoreResistances skips only DamageOnInteractProtectionComponent lookup;
        // DamageableSystem.ChangeDamage still applies ordinary armor. Until local contact
        // protection exists, keep the DamageSystem resistance bypass disabled.
        var protectedDamage = com.juicyslew.moonstation14.ms14.equipment.ContactDamageProtectionSystem.apply(target, data.damage(), data.ignoreResistances());
        var result = DamageSystem.applyHealthChangeDetailed(target, protectedDamage, 1f, ordinaryArmorBypass(data), DamageSystem.Cause.COMBAT, source);
        boolean positive = hasCommittedPositive(result.committedPositive());
        if (positive) {
            timer.setNextInteractionTick(now + Math.round(data.interactTimerSeconds() * 20f));
            if (source.level() instanceof ServerLevel level) {
                com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter.play(level, data.soundEvent(), SoundSource.BLOCKS,
                        source.getX(), source.getY(), source.getZ(), 1f, 1f);
            }
            if (data.popupText() != null) sendPopupToBodyOwner(target, Component.translatable(data.popupText()));
            if (data.stunChance() > 0 && source.getRandom().nextFloat() < data.stunChance())
                com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.applyStun(target, data.stunSeconds());
        }
        if (shouldThrow(data, true, com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService.isBeingPulled(source))) {
            double angle = source.getRandom().nextDouble() * Math.PI * 2;
            double speed = data.throwSpeed() / 20d;
            source.launch(new Vec3(Math.cos(angle) * speed, 0, Math.sin(angle) * speed), source.profile().throwSpin());
        }
        return positive ? Decision.HANDLED : Decision.NOT_HANDLED;
    }

    static boolean hasCommittedPositive(java.util.Map<String, Float> committedPositive) {
        for (float value : committedPositive.values()) if (value > 0f) return true;
        return false;
    }
    static boolean shouldThrow(LooseObjectProfile.DamageOnInteractData data, boolean activeAttempt, boolean pulled) {
        return activeAttempt && data != null && data.damageActive() && data.throwSource() && !pulled;
    }
    static boolean ordinaryArmorBypass(LooseObjectProfile.DamageOnInteractData data) {
        return false;
    }

    private static void sendPopupToBodyOwner(LivingEntity body, Component message) {
        if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            var authority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(player).orElse(null);
            if (authority != null && authority.body() == body
                    && com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(player, authority)) {
                player.sendSystemMessage(message);
                return;
            }
        }
        if (body instanceof net.minecraft.server.level.ServerPlayer player
                && player.getServer() == level.getServer()
                && com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(player).isEmpty())
            player.sendSystemMessage(message);
    }
}
