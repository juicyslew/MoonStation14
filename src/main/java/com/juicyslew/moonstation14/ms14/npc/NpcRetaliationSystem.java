package com.juicyslew.moonstation14.ms14.npc;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.NpcRetaliationComponent;
import com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns only provoked targets. NPC movement and native Mob melee remain their existing owners. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class NpcRetaliationSystem {
    private static final Map<UUID, Memory> MEMORIES = new HashMap<>();
    private static final Map<UUID, Long> LAST_ATTACK = new HashMap<>();
    private NpcRetaliationSystem() { }
    record Memory(UUID attacker, long expiresAt) { }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0) return;
        var character = CharacterIdentitySystem.resolveForActor(mob).orElse(null);
        NpcRetaliationComponent policy = character == null ? null
                : character.component(NpcRetaliationComponent.class).orElse(null);
        if (policy == null || !com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem
                .autonomousNpcEligible(mob, level, character)
                || com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(mob).isPresent()) return;
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        provoke(mob, attacker, level.getGameTime());
    }

    public static boolean provoke(Mob victim, LivingEntity attacker, long now) {
        if (victim == null || !(victim.level() instanceof ServerLevel level)) return false;
        var character = CharacterIdentitySystem.resolveForActor(victim).orElse(null);
        NpcRetaliationComponent policy = character == null ? null
                : character.component(NpcRetaliationComponent.class).orElse(null);
        if (policy == null
                || !com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.autonomousNpcEligible(victim, level, character)
                || com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(victim).isPresent()
                || attacker == null || attacker == victim || !attacker.isAlive() || attacker.isDeadOrDying()
                || attacker.level() != victim.level() || victim.isAlliedTo(attacker)
                || victim.getTeam() != null && victim.getTeam().equals(attacker.getTeam())) return false;
        return remember(MEMORIES, victim.getUUID(), attacker.getUUID(), now,
                policy.attackMemorySeconds(), false);
    }

    static boolean remember(Map<UUID, Memory> memories, UUID victim, UUID attacker, long now,
                            int attackMemorySeconds, boolean friendly) {
        if (victim == null || attacker == null || victim.equals(attacker) || friendly) return false;
        long timeout = attackMemorySeconds == 0 ? Long.MAX_VALUE : now + attackMemorySeconds * 20L;
        memories.put(victim, new Memory(attacker, timeout));
        return true;
    }

    static UUID rememberedTarget(Map<UUID, Memory> memories, UUID victim, long now) {
        Memory memory = memories.get(victim);
        if (memory == null) return null;
        if (now >= memory.expiresAt()) { memories.remove(victim); return null; }
        return memory.attacker();
    }

    static boolean acquireAttackCooldown(Map<UUID, Long> attacks, UUID attacker, long now, long cooldownTicks) {
        Long previous = attacks.get(attacker);
        if (previous != null && now - previous < cooldownTicks) return false;
        attacks.put(attacker, now);
        return true;
    }

    public static LivingEntity target(Mob mob, long now) {
        if (!(mob.level() instanceof ServerLevel level)) return null;
        var character = CharacterIdentitySystem.resolveForActor(mob).orElse(null);
        if (character == null || character.component(NpcRetaliationComponent.class).isEmpty()
                || !com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.autonomousNpcEligible(mob, level, character)
                || com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(mob).isPresent()) {
            MEMORIES.remove(mob.getUUID()); return null;
        }
        UUID remembered = rememberedTarget(MEMORIES, mob.getUUID(), now);
        if (remembered == null) return null;
        if (!(level.getEntity(remembered) instanceof LivingEntity target)
                || !target.isAlive() || target.isDeadOrDying() || mob.isAlliedTo(target)
                || mob.getTeam() != null && mob.getTeam().equals(target.getTeam())) {
            MEMORIES.remove(mob.getUUID()); return null;
        }
        return target;
    }

    /** Applies resolved unarmed melee via the canonical typed damage transaction. */
    public static boolean attack(Mob mob, LivingEntity target, long now) {
        if (mob == null || target == null || !(mob.level() instanceof ServerLevel level)
                || mob.level() != target.level() || !mob.isAlive() || mob.isDeadOrDying()
                || !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(mob)
                || !target.isAlive() || target.isDeadOrDying()
                || mob.isAlliedTo(target) || mob.getTeam() != null && mob.getTeam().equals(target.getTeam())
                || !mob.getSensing().hasLineOfSight(target)) return false;
        var character = CharacterIdentitySystem.resolveForActor(mob).orElse(null);
        if (character == null || character.component(NpcRetaliationComponent.class).isEmpty()
                || !com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.autonomousNpcEligible(mob, level, character)
                || com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(mob).isPresent()
                || target(mob, now) != target) return false;
        MeleeAttackComponent melee = character.component(MeleeAttackComponent.class).orElse(null);
        if (melee == null) return false;
        if (!withinReach(mob, target, melee.range())) return false;
        if (!acquireAttackCooldown(LAST_ATTACK, mob.getUUID(), now, melee.cooldownTicks())) return false;
        return com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(target,
                melee.damage().types(), 1f, false,
                com.juicyslew.moonstation14.ms14.damage.DamageSystem.Cause.COMBAT)
                == com.juicyslew.moonstation14.ms14.damage.DamageSystem.Result.APPLIED;
    }

    /** Range is nearest bounding-box edge separation, matching SS14's melee range contract. */
    public static boolean withinReach(Mob attacker, LivingEntity target, double range) {
        if (attacker == null || target == null || !Double.isFinite(range) || range < 0) return false;
        return edgeDistance(attacker.distanceTo(target), attacker.getBbWidth(), target.getBbWidth()) <= range;
    }

    public static double edgeDistance(double centerDistance, double attackerWidth, double targetWidth) {
        return Math.max(0d, centerDistance - attackerWidth * .5d - targetWidth * .5d);
    }

    public static void forget(Mob mob) {
        if (mob != null) { MEMORIES.remove(mob.getUUID()); LAST_ATTACK.remove(mob.getUUID()); }
    }
}
