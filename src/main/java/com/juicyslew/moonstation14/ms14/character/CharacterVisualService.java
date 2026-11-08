package com.juicyslew.moonstation14.ms14.character;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared detached-entity visual capture/application and comparison. */
public final class CharacterVisualService {
    private CharacterVisualService() { }

    public static LivingEntity createDetached(LivingEntity source) {
        var made = source.getType().create(source.level());
        if (!(made instanceof LivingEntity copy)) return null;
        capture(source).applyTo(copy);
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values())
            copy.setItemSlot(slot, source.getItemBySlot(slot).copy());
        return copy;
    }

    public static CharacterVisualState.VisualSnapshot capture(LivingEntity entity) {
        if (entity instanceof CharacterVisualState owner) return owner.captureVisualState();
        if (entity instanceof Pig pig) return new NativePigState(pig.isBaby(), pig.isSaddled());
        if (entity instanceof Sheep sheep) return new NativeSheepState(sheep.getColor(), sheep.isSheared(), sheep.isBaby());
        if (entity instanceof Villager villager) return new NativeVillagerState(villager.getVillagerData(), villager.isBaby());
        return new NativeLivingState(entity.isBaby());
    }

    /** Whether the shared capture path preserves appearance beyond generic living age state. */
    public static boolean hasExplicitVisualState(LivingEntity entity) {
        return !(capture(entity) instanceof NativeLivingState);
    }

    public static boolean same(LivingEntity left, LivingEntity right) {
        if (left.getClass() != right.getClass() || !capture(left).equals(capture(right))) return false;
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values())
            if (!ItemStack.isSameItemSameComponents(left.getItemBySlot(slot), right.getItemBySlot(slot))) return false;
        return true;
    }

    private record NativeLivingState(boolean baby) implements CharacterVisualState.VisualSnapshot {
        public void applyTo(LivingEntity e) { if (baby && e instanceof net.minecraft.world.entity.Mob mob) mob.setBaby(true); }
    }
    private record NativePigState(boolean baby, boolean saddled) implements CharacterVisualState.VisualSnapshot {
        public void applyTo(LivingEntity e) { var p=(Pig)e; if (baby) p.setBaby(true); if (saddled) p.equipSaddle(new ItemStack(Items.SADDLE), SoundSource.NEUTRAL); }
    }
    private record NativeSheepState(net.minecraft.world.item.DyeColor color, boolean sheared, boolean baby)
            implements CharacterVisualState.VisualSnapshot {
        public void applyTo(LivingEntity e) {
            var sheep = (Sheep) e;
            sheep.setColor(color);
            sheep.setSheared(sheared);
            sheep.setBaby(baby);
        }
    }
    private record NativeVillagerState(net.minecraft.world.entity.npc.VillagerData data, boolean baby) implements CharacterVisualState.VisualSnapshot {
        public void applyTo(LivingEntity e) { var v=(Villager)e; v.setVillagerData(data); if (baby) v.setBaby(true); }
    }
}
