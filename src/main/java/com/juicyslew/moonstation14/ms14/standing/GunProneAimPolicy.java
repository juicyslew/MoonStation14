package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.util.Mth;

/** Shared narrow stance rule for an actively wielded gun in combat while conscious and down. */
public final class GunProneAimPolicy {
    private GunProneAimPolicy() { }

    public static boolean admitted(boolean resolvedGun, boolean exactWieldLease, boolean combat,
                                   PostureData posture, boolean conscious) {
        return resolvedGun && exactWieldLease && combat && posture != null && posture.down() && conscious;
    }

    /** Server admission resolves the active item and validates the private lease at its owner. */
    public static boolean admittedServer(net.minecraft.world.entity.LivingEntity body, PostureData posture, boolean conscious) {
        if (body == null || posture == null || !posture.down() || !conscious
                || !com.juicyslew.moonstation14.ms14.combat.CombatModeSystem.isInCombatMode(body)) return false;
        var selection = com.juicyslew.moonstation14.ms14.combat.GunWeaponResolver.resolve(body).orElse(null);
        var wield = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.WIELD_STATE.get());
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        boolean lease = selection != null && wield != null && selection.activeHand().equals(wield.sourceHand())
                && selection.itemToken().equals(wield.sourceToken())
                && com.juicyslew.moonstation14.ms14.hands.live.WieldHandPolicy.validates(body, hands, wield);
        return admitted(selection != null, lease, true, posture, conscious);
    }

    /** Public pose hints contain no wield credentials; caller supplies already-resolved client gun/combat state. */
    public static boolean admittedPresentation(boolean resolvedGun, boolean combat,
            com.juicyslew.moonstation14.ms14.hands.network.BodyAppearanceSnapshot.PoseContext pose,
            PostureData posture, boolean conscious) {
        boolean validPose = pose != null && pose.hasValidatedWield()
                && pose.activeHand().equals(pose.wieldSourceHand());
        return admitted(resolvedGun, validPose, combat, posture, conscious);
    }

    /** Aim yaw owns the prone facing only while admitted; retain all other posture fields. */
    public static PostureData normalize(PostureData posture, float yaw) {
        if (posture == null || !Float.isFinite(yaw)) throw new IllegalArgumentException("invalid aim posture");
        return new PostureData(posture.down(), posture.voluntaryAutoStand(), posture.knockdownUntil(), posture.getUpKey(),
                posture.totalHands(), posture.freeHands(), posture.pulling(), false, posture.ownCrawling(), Mth.wrapDegrees(yaw), true);
    }
}
