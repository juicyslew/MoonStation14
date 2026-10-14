package com.juicyslew.moonstation14.ms14.medical;

import net.minecraft.world.entity.LivingEntity;

/** Builds an unregistered, detached visual copy for medical UI observation. */
public final class MedicalPortraitSnapshot {
    private MedicalPortraitSnapshot() { }

    public static LivingEntity create(LivingEntity source) {
        try {
            var made = source.getType().create(source.level());
            if (!(made instanceof LivingEntity copy)) return null;
            if (source instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity original
                    && copy instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity target) {
                var appearance = new net.minecraft.nbt.CompoundTag();
                appearance.putInt("Moonstation14PlayerCharacterAppearance", original.appearance().index());
                appearance.putInt("Moonstation14PlayerCharacterBodyShape", original.bodyShape().index());
                target.readAdditionalSaveData(appearance);
            } else if (source instanceof net.minecraft.world.entity.animal.Pig original
                    && copy instanceof net.minecraft.world.entity.animal.Pig target) {
                if (original.isBaby()) target.setBaby(true);
                if (original.isSaddled()) target.equipSaddle(new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.SADDLE), net.minecraft.sounds.SoundSource.NEUTRAL);
            } else if (source instanceof net.minecraft.world.entity.npc.Villager original
                    && copy instanceof net.minecraft.world.entity.npc.Villager target) {
                target.setVillagerData(original.getVillagerData());
                if (original.isBaby()) target.setBaby(true);
            } else return null;
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values())
                copy.setItemSlot(slot, source.getItemBySlot(slot).copy());
            copy.setHealth(copy.getMaxHealth());
            copy.deathTime = 0;
            copy.hurtTime = 0;
            copy.setPose(net.minecraft.world.entity.Pose.STANDING);
            copy.setDeltaMovement(0, 0, 0);
            copy.setOnGround(true);
            copy.setXRot(0);
            copy.setYRot(180);
            copy.setYBodyRot(180);
            copy.setYHeadRot(180);
            copy.xRotO = 0;
            copy.yRotO = copy.yBodyRotO = copy.yHeadRotO = 180;
            copy.tickCount = 0;
            return copy;
        } catch (RuntimeException unsupported) {
            return null;
        }
    }

    /** Exact visual-input comparison for the client cache; item identity/count are not appearance. */
    public static boolean sameAppearance(LivingEntity a, LivingEntity b) {
        if (a.getClass() != b.getClass()) return false;
        if (a.isBaby() != b.isBaby()) return false;
        if (a instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity x
                && b instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity y
                && (x.appearance() != y.appearance() || x.bodyShape() != y.bodyShape())) return false;
        if (a instanceof net.minecraft.world.entity.npc.Villager x && b instanceof net.minecraft.world.entity.npc.Villager y
                && !x.getVillagerData().equals(y.getVillagerData())) return false;
        if (a instanceof net.minecraft.world.entity.animal.Pig x && b instanceof net.minecraft.world.entity.animal.Pig y
                && x.isSaddled() != y.isSaddled()) return false;
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values())
            if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(a.getItemBySlot(slot), b.getItemBySlot(slot))) return false;
        return true;
    }
}

