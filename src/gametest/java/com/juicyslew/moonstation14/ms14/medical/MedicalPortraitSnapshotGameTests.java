package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"health-analyzer"})
@PrefixGameTestTemplate(false)
public final class MedicalPortraitSnapshotGameTests {
    private MedicalPortraitSnapshotGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void everyResolvedProjectCharacterHostOwnsVisualSnapshot(GameTestHelper helper) {
        var catalog = com.juicyslew.moonstation14.ms14.character.ModCharacters.catalog(helper.getLevel());
        var seen = new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
        for (var data : catalog.asMap().values()) for (var hostId : data.hostEntityTypes()) {
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(hostId).orElseThrow(
                    () -> new net.minecraft.gametest.framework.GameTestAssertException("unregistered project character host: " + hostId));
            if (!isMedicalHost(data)) continue;
            // Observer/non-medical and abstract hosts are not portrait candidates. Concrete
            // resolved project medical hosts, however, must never be silently skipped.
            var made = type == EntityType.PLAYER
                    ? com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration
                        .getEntityType().create(helper.getLevel())
                    : type.create(helper.getLevel());
            require(made instanceof net.minecraft.world.entity.LivingEntity,
                    "resolved project medical host factory must create LivingEntity: " + hostId);
            var source = (net.minecraft.world.entity.LivingEntity) made;
            verifyMedicalHostAppearance(hostId, source);
            seen.add(hostId);
            var copy = MedicalPortraitSnapshot.create(source);
            require(copy != null && copy.getType() == source.getType(), "medical host has detached renderer copy: " + hostId);
            require(MedicalPortraitSnapshot.sameAppearance(source, copy), "medical host snapshot compares equal: " + hostId);
            require(helper.getLevel().getEntity(copy.getId()) == null, "medical host copy remains detached: " + hostId);
        }
        require(!seen.isEmpty(), "resolved project character hosts exercised");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void futureVanillaHostWithoutVisualContractFailsValidation(GameTestHelper helper) {
        var futureHostId = ResourceLocation.withDefaultNamespace("fox");
        var fox = EntityType.FOX.create(helper.getLevel());
        require(fox != null, "native fox created for future-host contract negative fixture");
        try {
            verifyMedicalHostAppearance(futureHostId, fox);
            throw new net.minecraft.gametest.framework.GameTestAssertException("missing visual contract was accepted");
        } catch (net.minecraft.gametest.framework.GameTestAssertException expected) {
            require(expected.getMessage().contains(futureHostId.toString()), "failure identifies future project host id");
        }
        helper.succeed();
    }

    private static boolean isMedicalHost(com.juicyslew.moonstation14.component.codec.json.CharacterData data) {
        return data.component(com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.class).isPresent()
                && data.component(com.juicyslew.moonstation14.ms14.character.components.MobThresholdsComponent.class).isPresent();
    }

    private static void verifyMedicalHostAppearance(ResourceLocation hostId, net.minecraft.world.entity.LivingEntity source) {
        require(com.juicyslew.moonstation14.ms14.character.CharacterVisualService.hasExplicitVisualState(source),
                "medical host must preserve complete visual appearance (host " + hostId + ", entity "
                        + source.getType() + ")");
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void unlistedNativeLivingPortraitUsesGenericPath(GameTestHelper helper) {
        var source = EntityType.COW.create(helper.getLevel());
        require(source != null, "native cow created");
        source.setBaby(true);
        var copy = MedicalPortraitSnapshot.create(source);
        require(copy != null && copy.getType() == EntityType.COW && copy.isBaby(),
                "unlisted native living entity uses generic detached snapshot path");
        require(MedicalPortraitSnapshot.sameAppearance(source, copy), "generic native appearance retained");
        source.setBaby(false);
        require(!MedicalPortraitSnapshot.sameAppearance(source, copy), "native mutation invalidates appearance snapshot");
        require(helper.getLevel().getEntity(copy.getId()) == null, "native snapshot remains detached");
        helper.succeed();
    }

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
    public static void sheepPortraitPreservesNativeRendererAppearance(GameTestHelper helper) {
        Sheep source = EntityType.SHEEP.create(helper.getLevel());
        require(source != null, "sheep created");
        source.setColor(net.minecraft.world.item.DyeColor.BLUE);
        source.setSheared(true);
        source.setBaby(true);
        source.setPose(Pose.SLEEPING);
        source.setHealth(source.getMaxHealth() / 2);
        var health = source.getHealth();
        var copy = MedicalPortraitSnapshot.create(source);
        require(copy instanceof Sheep, "sheep snapshot supported");
        Sheep sheep = (Sheep) copy;
        require(sheep.getColor() == net.minecraft.world.item.DyeColor.BLUE && sheep.isSheared() && sheep.isBaby(),
                "sheep color, sheared wool, and age retained");
        require(sheep.isAlive() && sheep.getPose() == Pose.STANDING && sheep.getHealth() == sheep.getMaxHealth(),
                "portrait normalized without changing source");
        require(MedicalPortraitSnapshot.sameAppearance(source, copy), "sheep appearance compares equal");
        source.setColor(net.minecraft.world.item.DyeColor.RED);
        require(!MedicalPortraitSnapshot.sameAppearance(source, copy), "sheep color mutation invalidates appearance snapshot");
        require(source.getColor() == net.minecraft.world.item.DyeColor.RED && source.isSheared() && source.isBaby()
                        && source.getPose() == Pose.SLEEPING && source.getHealth() == health
                        && helper.getLevel().getEntity(copy.getId()) == null,
                "patient unchanged and detached snapshot is not registered");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void nativeMousePortraitUsesDetachedLivingEntityPath(GameTestHelper helper) {
        var source = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(helper.getLevel());
        require(source != null, "mouse created");
        source.setAppearance(com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.Variant.BROWN);
        source.setPose(Pose.SLEEPING);
        source.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, namedGear());
        var equipment = source.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        var copy = MedicalPortraitSnapshot.create(source);
        require(copy != null && copy.getType() == source.getType(), "native mouse renderer source created");
        require(((com.juicyslew.moonstation14.entities.MouseMob) copy).appearance() == source.appearance(), "resolved synced coat retained");
        require(copy.isAlive() && copy.getPose() == Pose.STANDING, "mouse portrait normalized");
        assertGearAndNeutralState(source, copy);
        source.setAppearance(com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.Variant.GRAY);
        require(!MedicalPortraitSnapshot.sameAppearance(source, copy), "mouse coat mutation invalidates cached appearance");
        require(helper.getLevel().getEntity(copy.getId()) == null, "mouse snapshot remains detached");
        require(source.getPose() == Pose.SLEEPING && source.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD) == equipment,
                "mouse patient unchanged");
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
