package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.EggLayerComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-authoritative EggLayer consumer; upstream behavior per EggLayerSystem.cs (SS14 c9df5ef). */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class EggLayerSystem {
    private EggLayerSystem() { }

    public static java.util.Optional<EggLayerComponent> capability(LivingEntity entity) {
        if (entity == null || entity.isRemoved()) return java.util.Optional.empty();
        return CharacterIdentitySystem.resolveForActor(entity).flatMap(data -> data.component(EggLayerComponent.class));
    }

    @SubscribeEvent public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity living) tick(living);
    }

    public static void tick(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || entity.isRemoved()) return;
        var policy = capability(entity).orElse(null);
        if (policy == null || com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.isCarrier(entity)
                || entity instanceof net.minecraft.server.level.ServerPlayer || isPlayerControlledBody(entity, level)) return;
        long now = level.getGameTime();
        var state = entity.getData(ModDataAttachments.EGG_LAYER.get());
        if (state.nextGrowthTick() == 0) {
            entity.setData(ModDataAttachments.EGG_LAYER.get(), new EggLayerAttachment(now + randomDelay(policy, level.getRandom()), state.nextManualLayTick()));
            return;
        }
        if (now < state.nextGrowthTick()) return;
        // SS14 advances the deadline before death/hunger checks; failed production does not retry each tick.
        entity.setData(ModDataAttachments.EGG_LAYER.get(), new EggLayerAttachment(state.nextGrowthTick() + randomDelay(policy, level.getRandom()), state.nextManualLayTick()));
        if (entity.isAlive()) tryLayEgg(entity, policy);
    }

    private static boolean isPlayerControlledBody(LivingEntity entity, ServerLevel level) {
        if (!(entity instanceof net.minecraft.world.entity.Mob mob) || level.getServer() == null) return false;
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            var active = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server
                    .LifecycleCharacterSessionControl.committedActorSnapshot(player).orElse(null);
            if (active != null && active.body() == mob) return true;
        }
        return false;
    }

    public static long randomDelay(EggLayerComponent policy, net.minecraft.util.RandomSource random) {
        return (policy.cooldownMin() == policy.cooldownMax() ? policy.cooldownMin()
                : random.nextInt(policy.cooldownMin(), policy.cooldownMax())) * 20L;
    }

    public static boolean tryLayEgg(LivingEntity entity, EggLayerComponent policy) {
        if (entity == null || policy == null || !entity.isAlive() || entity.isRemoved()
                || !(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return false;
        var hunger = entity.getExistingDataOrNull(ModDataAttachments.HUNGER.get());
        if (!eligibleForProduction(entity.isAlive(), hunger != null, hunger == null ? 0 : hunger.hunger(), policy.hungerUsage())) return false;
        float hungerBefore = hunger == null ? 0 : hunger.hunger();
        ResourceLocation itemId = ResourceLocation.tryParse(policy.eggItem());
        if (itemId == null) return false;
        var item = BuiltInRegistries.ITEM.get(itemId);
        if (item == net.minecraft.world.item.Items.AIR) return false;
        var staged = LooseItemEntity.create(level, entity.position(), new ItemStack(item));
        boolean committed = false;
        try {
            if (!level.addFreshEntity(staged) || !staged.isAddedToLevel() || level.getEntity(staged.getUUID()) != staged
                    || entity.isRemoved() || !entity.isAlive()) return false;
            if (hunger != null) hunger.setHunger(hunger.hunger() - policy.hungerUsage());
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), net.minecraft.sounds.SoundEvents.CHICKEN_EGG,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
            committed = true;
            return true;
        } catch (RuntimeException failure) {
            return false;
        } finally {
            if (!committed) {
                staged.discard();
                if (hunger != null) hunger.setHunger(hungerBefore);
            }
        }
    }

    public static boolean eligibleForProduction(boolean alive, boolean hasHunger, float currentHunger, float cost) {
        return alive && Float.isFinite(cost) && cost >= 0 && (!hasHunger
                || Float.isFinite(currentHunger) && currentHunger >= cost);
    }

    /** Triggered only for the authenticated controlled body; successful lays impose the upstream 60s action delay. */
    public static boolean manualLay(net.minecraft.server.level.ServerPlayer actor, LivingEntity body) {
        var authority = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.resolve(actor).orElse(null);
        if (authority == null || authority.body() != body
                || !com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.revalidate(actor, authority)) return false;
        var policy = capability(body).orElse(null);
        if (policy == null || !body.isAlive() || !(body.level() instanceof ServerLevel level)) return false;
        var state = body.getData(ModDataAttachments.EGG_LAYER.get());
        if (level.getGameTime() < state.nextManualLayTick() || !tryLayEgg(body, policy)) return false;
        body.setData(ModDataAttachments.EGG_LAYER.get(), new EggLayerAttachment(state.nextGrowthTick(), level.getGameTime() + 1200));
        return true;
    }
}
