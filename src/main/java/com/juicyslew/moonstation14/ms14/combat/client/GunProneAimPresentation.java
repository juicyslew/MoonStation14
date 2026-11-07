package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.combat.CombatModeClient;
import com.juicyslew.moonstation14.ms14.combat.ModGunProfiles;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking;
import com.juicyslew.moonstation14.ms14.standing.GunProneAimPolicy;
import com.juicyslew.moonstation14.ms14.standing.PostureData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Read-only client projection of the narrowly admitted prone gun-aim posture. */
public final class GunProneAimPresentation {
    private GunProneAimPresentation() { }

    public static PostureData effectivePosture(LivingEntity entity) {
        if (entity == null) return null;
        var attachment = entity.getExistingDataOrNull(ModDataAttachments.POSTURE.get());
        if (attachment == null) return null;
        PostureData posture = attachment.data();
        var snapshot = BodyHandNetworking.clientCache().get(entity.level().dimension().location(),
                entity.getUUID(), entity.getId()).orElse(null);
        var context = snapshot == null ? null : snapshot.poseContext();
        ItemStack stack = ItemStack.EMPTY;
        if (snapshot != null && context != null) {
            var hand = snapshot.hands().stream().filter(h -> h.id().equals(context.activeHand())).findFirst().orElse(null);
            if (hand != null && hand.item() != null) stack = hand.item().stack();
        }
        var id = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean gun = id != null && ModGunProfiles.clientCatalog().get(id) != null;
        boolean combat = CombatModeClient.modeForPresentation(entity);
        boolean conscious = com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(entity)
                && !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(entity);
        return GunProneAimPolicy.admittedPresentation(gun, combat, context, posture, conscious)
                ? GunProneAimPolicy.normalize(posture, entity.getYRot()) : posture;
    }
}
