package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"health-analyzer"})
@PrefixGameTestTemplate(false)
public final class MedicalPortraitSnapshotGameTests {
    private MedicalPortraitSnapshotGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void pigPortraitIsDetachedAndPreservesAppearance(GameTestHelper helper) {
        Pig source = EntityType.PIG.create(helper.getLevel());
        require(source != null, "pig created");
        source.setBaby(true);
        source.equipSaddle(new ItemStack(Items.SADDLE), net.minecraft.sounds.SoundSource.NEUTRAL);
        ItemStack gear = namedGear(); source.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, gear);
        source.setPose(Pose.SLEEPING); source.setXRot(31); source.setYRot(47); source.setHealth(0);
        var sourceHealth = source.getHealth(); var sourceEquipment = source.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        var copy = MedicalPortraitSnapshot.create(source);
        require(copy instanceof Pig, "pig snapshot supported");
        Pig pig = (Pig) copy;
        require(pig.isBaby() && pig.isSaddled(), "pig age and saddle preserved");
        require(pig.isAlive() && pig.getPose() == Pose.STANDING && pig.getXRot() == 0, "snapshot normalized");
        assertGearAndNeutralState(source, copy);
        require(helper.getLevel().getEntity(copy.getId()) == null, "snapshot not added to level");
        require(source.isBaby() && source.isSaddled() && source.getPose() == Pose.SLEEPING
                && source.getXRot() == 31 && source.getYRot() == 47 && source.getHealth() == sourceHealth
                && source.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD) == sourceEquipment, "source unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void villagerPortraitPreservesProfessionAndAge(GameTestHelper helper) {
        Villager source = EntityType.VILLAGER.create(helper.getLevel());
        require(source != null, "villager created");
        source.setVillagerData(source.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
        source.setBaby(true); source.setPose(Pose.SLEEPING); source.setXRot(22); source.setYRot(39);
        source.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, namedGear());
        var sourceEquipment = source.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        var copy = MedicalPortraitSnapshot.create(source);
        require(copy instanceof Villager, "villager snapshot supported");
        Villager villager = (Villager) copy;
        require(villager.getVillagerData().equals(source.getVillagerData()) && villager.isBaby(), "villager data preserved");
        require(villager.isAlive() && villager.getPose() == Pose.STANDING && villager.getXRot() == 0, "snapshot normalized");
        assertGearAndNeutralState(source, copy);
        require(helper.getLevel().getEntity(copy.getId()) == null, "snapshot not added to level");
        require(source.isBaby() && source.getPose() == Pose.SLEEPING && source.getXRot() == 22
                && source.getYRot() == 39 && source.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD) == sourceEquipment,
                "source unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void harnessPortraitPreservesTypedAppearance(GameTestHelper helper) {
        var source = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration
                .getEntityType().create(helper.getLevel());
        require(source != null, "harness created");
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("Moonstation14PlayerCharacterAppearance", 1);
        tag.putInt("Moonstation14PlayerCharacterBodyShape", 0);
        source.readAdditionalSaveData(tag);
        source.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, namedGear());
        source.setPose(Pose.SLEEPING); source.setXRot(17); source.setYRot(63); source.setHealth(0);
        source.hurtTime = 7; source.deathTime = 9;
        var beforeHealth = source.getHealth(); var beforePose = source.getPose();
        var beforeX = source.getXRot(); var beforeY = source.getYRot();
        var beforeHurt = source.hurtTime; var beforeDeath = source.deathTime;
        var beforeEquipment = source.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        var copy = MedicalPortraitSnapshot.create(source);
        require(copy instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity,
                "harness snapshot supported");
        var harness = (com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity) copy;
        require(harness.appearance() == source.appearance() && harness.bodyShape() == source.bodyShape(), "appearance and shape preserved");
        require(harness.isAlive() && harness.getHealth() == harness.getMaxHealth() && harness.getPose() == Pose.STANDING
                && harness.hurtTime == 0 && harness.deathTime == 0 && harness.getXRot() == 0
                && harness.getYRot() == 180 && harness.yRotO == 180 && harness.xRotO == 0, "snapshot normalized");
        assertGearAndNeutralState(source, copy);
        require(source.getHealth() == beforeHealth && source.getPose() == beforePose && source.getXRot() == beforeX
                && source.getYRot() == beforeY && source.hurtTime == beforeHurt && source.deathTime == beforeDeath
                && source.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD) == beforeEquipment,
                "harness source remains unchanged");
        require(helper.getLevel().getEntity(copy.getId()) == null, "snapshot not added to level");
        helper.succeed();
    }

    private static ItemStack namedGear() {
        ItemStack stack = new ItemStack(Items.LEATHER_HELMET);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Portrait test"));
        stack.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x123456, true));
        return stack;
    }

    private static void assertGearAndNeutralState(net.minecraft.world.entity.LivingEntity source,
                                                   net.minecraft.world.entity.LivingEntity copy) {
        var sourceStack = source.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        var copiedStack = copy.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        require(sourceStack != copiedStack && ItemStack.isSameItemSameComponents(sourceStack, copiedStack),
                "equipment components copied into distinct stack");
        require(copy.getHealth() == copy.getMaxHealth() && copy.hurtTime == 0 && copy.deathTime == 0
                && copy.getXRot() == 0 && copy.getYRot() == 180 && copy.yRotO == 180 && copy.xRotO == 0,
                "copy is alive and neutrally oriented");
        require(MedicalPortraitSnapshot.sameAppearance(source, copy), "portrait appearance comparison matches snapshot");
        copiedStack.set(DataComponents.CUSTOM_NAME, Component.literal("Changed"));
        require(!MedicalPortraitSnapshot.sameAppearance(source, copy), "component changes invalidate appearance comparison");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
