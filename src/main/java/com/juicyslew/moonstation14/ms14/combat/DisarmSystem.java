package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent;
import com.juicyslew.moonstation14.ms14.character.components.DisarmProneComponent;
import com.juicyslew.moonstation14.ms14.character.components.StatusEffectsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.StaminaPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem;
import com.juicyslew.moonstation14.ms14.combat.MeleeAttackEvents.DisarmAttemptEvent;
import com.juicyslew.moonstation14.ms14.combat.MeleeAttackEvents.DisarmSource;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;
import java.util.UUID;

/** Server-authoritative resolution for the post-cooldown disarm action. */
public final class DisarmSystem {
    private DisarmSystem() { }
    public enum Result { REJECTED, ROLLED_FAILURE, HANDLED, RECOVERY_REQUIRED }

    public static Result resolve(ServerPlayer owner, LivingEntity user, MeleeWeaponResolver.Selection weapon, UUID targetId) {
        if (owner == null || user == null || weapon == null || targetId == null
                || !(user.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || user.level() != owner.level()
                || ActiveCharacterPolicy.isCarrier(user) || user.isRemoved() || !user.isAlive()) return Result.REJECTED;
        Entity found = level.getEntity(targetId);
        if (!(found instanceof LivingEntity target) || target == user || target.isRemoved()
                || !target.isAlive() || target.level() != level || MobStateSystem.isIncapacitated(target)) return Result.REJECTED;
        var actor = ActiveCharacterPolicy.resolveActor(user).orElse(null);
        CombatModeComponent combat = actor == null ? null : actor.component(CombatModeComponent.class).orElse(null);
        if (combat == null || !combat.canDisarm().orElse(false)) return Result.REJECTED;
        LiveHands hands = target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        boolean liveHands = hands != null && hands.compatible(target);
        var targetCharacter = ActiveCharacterPolicy.resolveActor(target).orElse(null);
        var statusAdmission = targetCharacter == null ? null : targetCharacter.component(StatusEffectsPrototypeComponent.class).orElse(null);
        boolean allowsKnockdown = statusAdmission != null && statusAdmission.allowed().contains(StatusEffectsPrototypeComponent.KNOCKDOWN);
        if (!DisarmPolicy.canTarget(liveHands, allowsKnockdown)) return Result.REJECTED;
        var probe = CombatMeleeTargetProbe.probe(level, user, targetId, weapon.weapon().range());
        if (probe.status() != CombatMeleeTargetProbe.Status.HIT || !CombatMeleeTargetProbe.revalidate(level, user, probe)) return Result.REJECTED;

        Optional<Entity> held = Optional.empty();
        ItemSnapshot heldSnapshot = activeHeld(hands);
        float malus = heldSnapshot.stack().map(stack -> LooseObjectProfile.resolveForStack(stack, false).disarmMalus()).orElse(0f);
        float push = Math.clamp(1f - DisarmPolicy.failureChance(combat.baseDisarmFailChance(), malus,
                disarmProne(user), disarmProne(target)), 0f, 1f);
        float damage = StaminaSystem.state(target).damage();
        var stamina = targetCharacter == null ? null : targetCharacter.component(StaminaPrototypeComponent.class).orElse(null);
        float threshold = stamina == null ? 100f : stamina.baseCritThreshold();
        var descriptor = heldSnapshot.descriptor();
        var attempted = new DisarmAttemptEvent(user, target, held, DisarmSource.HELD_ITEM, push * threshold, descriptor);
        if (heldSnapshot.stack().isPresent()) {
            NeoForge.EVENT_BUS.post(attempted);
            if (attempted.isCanceled()) return Result.REJECTED;
        }
        attempted = new DisarmAttemptEvent(user, target, held, DisarmSource.TARGET, push * threshold, descriptor);
        NeoForge.EVENT_BUS.post(attempted);
        if (attempted.isCanceled()) return Result.REJECTED;
        var swing = weapon.weapon().audio().swing();
        if (swing != null) PositionalSoundRouter.play(level, swing.event(), SoundSource.PLAYERS,
                user.getX(), user.getY(), user.getZ(), swing.volume(),
                MeleeSoundPolicy.pitch(1f, level.getRandom().nextFloat(), swing.pitchVariation()), owner.getUUID());
        float failure = DisarmPolicy.failureChance(combat.baseDisarmFailChance(), malus, disarmProne(user), disarmProne(target));
        if (level.getRandom().nextFloat() < failure) {
            owner.sendSystemMessage(Component.translatable("moonstation14.disarm.failure", target.getDisplayName()));
            return Result.ROLLED_FAILURE;
        }
        var disarmed = new MeleeAttackEvents.DisarmedEvent(target, push);
        NeoForge.EVENT_BUS.post(disarmed);
        if (disarmed.terminalAbort()) return Result.RECOVERY_REQUIRED;
        if (!disarmed.handled()) return Result.REJECTED;
        combat.disarmSuccessSound().ifPresent(sound -> {
            ResourceLocation id = ResourceLocation.tryParse(sound);
            if (id != null) PositionalSoundRouter.play(level, id, SoundSource.PLAYERS, target.getX(), target.getY(), target.getZ(),
                    (float) Math.pow(10d, 5d / 20d), 1f + (level.getRandom().nextFloat() * 2f - 1f) * .025f);
        });
        owner.sendSystemMessage(Component.translatable("moonstation14.disarm.success", target.getDisplayName()));
        return Result.HANDLED;
    }

    private record ItemSnapshot(Optional<net.minecraft.world.item.ItemStack> stack, Optional<MeleeAttackEvents.OwnedItem> descriptor) { }
    private static ItemSnapshot activeHeld(LiveHands hands) {
        if (hands == null) return new ItemSnapshot(Optional.empty(), Optional.empty());
        var stack = hands.stackCopy(hands.activeHand());
        var token = hands.token(hands.activeHand());
        Optional<MeleeAttackEvents.OwnedItem> descriptor = stack.isPresent() && token.isPresent()
                ? Optional.of(new MeleeAttackEvents.OwnedItem(hands.activeHand(), token.orElseThrow(), hands.revision(), stack.orElseThrow()))
                : Optional.empty();
        return new ItemSnapshot(stack, descriptor);
    }
    private static boolean disarmProne(LivingEntity actor) {
        return ActiveCharacterPolicy.resolveActor(actor).flatMap(data -> data.component(DisarmProneComponent.class)).isPresent();
    }

    @EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
    public static final class Consumers {
        private Consumers() { }
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void hands(MeleeAttackEvents.DisarmedEvent event) {
            if (event.handled() || !(event.target() instanceof LivingEntity target)) return;
            LiveHands hands = target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (hands == null || !hands.compatible(target)) return;
            com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService.stopForIncapacitation(target);
            if (hands.stackCopy(hands.activeHand()).isEmpty()) return;
            Vec3 direction = new Vec3(target.getRandom().nextDouble() * 2 - 1, 0, target.getRandom().nextDouble() * 2 - 1);
            if (direction.lengthSqr() < 1.0e-8) direction = new Vec3(1, 0, 0);
            direction = direction.normalize().scale(1 + target.getRandom().nextDouble() * .5);
            var result = BodyLooseThrow.throwForDisarm(target, direction);
            if (result == BodyLooseThrow.Result.SUCCESS) {
                event.setHandled(true); event.setPopupPrefix("disarm-action-");
            } else if (DisarmPolicy.shouldTerminate(result)) { event.setHandled(true); event.setTerminalAbort(true); }
        }
        @SubscribeEvent(priority = EventPriority.NORMAL)
        public static void stamina(MeleeAttackEvents.DisarmedEvent event) {
            if (event.handled() || !(event.target() instanceof LivingEntity target) || DisarmPolicy.skipStaminaWhenCritical(StaminaSystem.state(target).critical())) return;
            var policy = ActiveCharacterPolicy.resolveActor(target).flatMap(data -> data.component(StaminaPrototypeComponent.class)).orElse(null);
            if (policy == null) return;
            StaminaSystem.takeStaminaDamage(target, event.pushProbability() * policy.baseCritThreshold());
            event.setStunned(StaminaSystem.state(target).critical());
            event.setPopupPrefix("disarm-action-shove-"); event.setHandled(true);
        }
    }
}
