package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Captures and publishes observer-only snapshots around authoritative melee resolution. */
public final class MeleeArcObserverPublisher {
    public record Selection(MeleeWeaponData weapon, ItemPresentation presentation, MeleeArcGeometry.Aim aim) { }
    public static net.minecraft.resources.ResourceLocation arcForMode(MeleeWeaponData weapon, MeleeAttackRequest.Mode mode) {
        return mode == MeleeAttackRequest.Mode.WIDE ? weapon.wideArc() : weapon.arc();
    }
    private MeleeArcObserverPublisher() { }

    public static Selection capture(LivingEntity body, MeleeWeaponResolver.Selection selected, java.util.UUID targetUUID) {
        return capture(body, selected, targetUUID, false);
    }
    public static Selection capture(LivingEntity body, MeleeWeaponResolver.Selection selected, java.util.UUID targetUUID, boolean wide) {
        if (body == null || selected == null) return null;
        MeleeWeaponData weapon = selected.weapon();
        ItemPresentation presentation = selected.kind() == MeleeWeaponResolver.Kind.UNARMED ? null
                : MeleeItemOwner.read(body, selected).map(view -> ItemPresentation.fromStack(view.stack())).orElse(null);
        if (selected.kind() != MeleeWeaponResolver.Kind.UNARMED && presentation == null) return null;
        Vec3 targetPosition = null;
        if (targetUUID != null && body.level() instanceof net.minecraft.server.level.ServerLevel level) {
            var target = level.getEntity(targetUUID);
            if (target != null && !target.isRemoved()) targetPosition = target.position();
        }
        var aim = MeleeArcGeometry.capture(body.position(), body.getBbHeight(), body.getLookAngle(), targetPosition, weapon.range());
        weapon = new MeleeWeaponData(weapon.damage(), weapon.attackRate(), weapon.range(), weapon.resistanceBypass(), weapon.autoAttack(), weapon.clickDamageModifier(), weapon.mustBeEquippedToUse(), weapon.audio(), arcForMode(weapon, wide ? MeleeAttackRequest.Mode.WIDE : MeleeAttackRequest.Mode.LIGHT), weapon.animationOffset(), weapon.wideAnimationRotation(), weapon.bluntStaminaDamageFactor(), weapon.resetOnHandSelected(), weapon.angleDegrees(), weapon.wideArc(), weapon.altDisarm());
        return aim == null ? null : new Selection(weapon, presentation, aim);
    }

    public static void publish(ServerPlayer player, LivingEntity body, java.util.UUID targetUUID, Selection frozen, boolean swingLeft) {
        if (frozen == null || player == null || body == null || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var data = frozen.weapon();
        var aim = frozen.aim();
        var payload = new MeleeArcPayload(body.getId(), body.getUUID(), level.dimension().location(), player.getUUID(), data.arc(),
                data.attackRate(), data.angleDegrees(), data.animationOffset(), data.wideAnimationRotation(), aim.bodyPosition(), aim.direction(),
                 aim.attackPoint(), frozen.presentation(), swingLeft);
        PacketDistributor.sendToPlayersTrackingEntity(body, payload);
    }
}
