package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.juicyslew.moonstation14.entities.ItemAttachment;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.character.components.EnsnareableComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;

/** Server-owned, transient index of bola attachments. Attachments remain owned by their item entity. */
@EventBusSubscriber
public final class EnsnaringSystem {
    private static final Map<net.minecraft.server.level.ServerLevel, Map<UUID, Set<UUID>>> INDEX = new WeakHashMap<>();
    private EnsnaringSystem() {}

    @SubscribeEvent public static void onThrownItemHit(ThrownItemHitEvent event) {
        var data = event.profile().ensnaring().orElse(null);
        var item = event.item(); var target = event.target();
        if (data == null || !data.canThrowTrigger() || data.maxEnsnares() < 1 || !item.activeThrownCombat()
                || !(target.level() instanceof net.minecraft.server.level.ServerLevel level)
                || item.level() != target.level() || item.isRemoved() || target.isRemoved()
                || level.getEntity(item.getUUID()) != item || level.getEntity(target.getUUID()) != target
                || ActiveCharacterPolicy.resolveActor(target).flatMap(c -> c.component(EnsnareableComponent.class)).isEmpty()
                || ensnares(target).size() >= data.maxEnsnares()) return;
        if (item.attachTo(target, event.contactPoint(), event.incomingVelocity(), ItemAttachment.AttachmentKind.ENSNARED)) {
            index(level, target.getUUID(), item.getUUID());
            data.ensnareSound().ifPresent(sound -> com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(
                    level, sound, event.contactPoint().x, event.contactPoint().y, event.contactPoint().z, 1f, 1f, net.minecraft.sounds.SoundSource.NEUTRAL));
        }
    }

    public static List<LooseItemEntity> ensnares(LivingEntity body) {
        if (!(body.level() instanceof net.minecraft.server.level.ServerLevel level)) return List.of();
        var ids = INDEX.computeIfAbsent(level, ignored -> new HashMap<>()).get(body.getUUID());
        if (ids == null) return List.of();
        var result = new ArrayList<LooseItemEntity>();
        for (var it = ids.iterator(); it.hasNext();) {
            var item = level.getEntity(it.next());
            var a = item instanceof LooseItemEntity loose ? loose.attachment().orElse(null) : null;
            var data = item instanceof LooseItemEntity loose ? ThrownItemProfile.resolveForStack(loose.getItem(), false)
                    .flatMap(ThrownItemProfile::ensnaring).orElse(null) : null;
            if (!(item instanceof LooseItemEntity loose) || loose.isRemoved() || a == null
                    || a.kind() != ItemAttachment.AttachmentKind.ENSNARED || !body.getUUID().equals(a.targetUUID()) || data == null) it.remove();
            else result.add(loose);
        }
        if (ids.isEmpty()) INDEX.get(level).remove(body.getUUID());
        return List.copyOf(result);
    }
    public static boolean isEnsnared(LivingEntity body) { return !ensnares(body).isEmpty(); }
    public static double movementMultiplier(LivingEntity body, boolean sprint) {
        double factor = 1d;
        for (var item : ensnares(body)) {
            var data = ThrownItemProfile.resolveForStack(item.getItem(), false).flatMap(ThrownItemProfile::ensnaring).orElse(null);
            if (data != null) factor *= sprint ? data.sprintMultiplier() : data.walkMultiplier();
        }
        return factor;
    }
    private static void index(net.minecraft.server.level.ServerLevel level, UUID target, UUID item) {
        INDEX.computeIfAbsent(level, ignored -> new HashMap<>()).computeIfAbsent(target, ignored -> new HashSet<>()).add(item);
    }

    @SubscribeEvent public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !(event.getEntity() instanceof LooseItemEntity item) || item.isRemoved()) return;
        var a = item.attachment().orElse(null);
        if (a != null && a.kind() == ItemAttachment.AttachmentKind.ENSNARED) index(level, a.targetUUID(), item.getUUID());
    }
    @SubscribeEvent public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var byBody = INDEX.get(level);
        if (byBody == null) return;
        UUID leaving = event.getEntity().getUUID();
        byBody.remove(leaving);
        for (var it = byBody.values().iterator(); it.hasNext();) {
            Set<UUID> ids = it.next(); ids.remove(leaving); if (ids.isEmpty()) it.remove();
        }
        if (byBody.isEmpty()) INDEX.remove(level);
    }
    @SubscribeEvent public static void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { INDEX.clear(); }
}
