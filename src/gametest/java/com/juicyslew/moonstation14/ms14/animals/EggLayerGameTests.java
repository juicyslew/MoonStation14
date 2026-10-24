package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.components.EggLayerComponent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"animals", "character"})
@net.neoforged.fml.common.EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class EggLayerGameTests {
    private static volatile boolean rejectEggPublication;
    private EggLayerGameTests() { }

    @net.neoforged.bus.api.SubscribeEvent
    public static void rejectEggJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (rejectEggPublication && event.getEntity() instanceof LooseItemEntity item
                && item.getItem().is(net.minecraft.world.item.Items.EGG)) event.setCanceled(true);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"animals", "character"})
    public static void successfulOutputIsPublishedAsLooseItem(GameTestHelper helper) {
        var level = helper.getLevel();
        var chickenType = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().values().stream()
                .map(net.neoforged.neoforge.registries.DeferredHolder::get)
                .filter(type -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type)
                        .equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "chicken")))
                .findFirst().orElseThrow(() -> new AssertionError("MoonStation14 chicken host unavailable"));
        var chicken = chickenType.create(level);
        if (chicken == null) throw new AssertionError("chicken factory unavailable");
        chicken.moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)).getCenter());
        level.addFreshEntity(chicken);
        // Egg production consumes the resolved policy's nutrition cost. Avoid making
        // this production fixture depend on randomized join-time hunger.
        chicken.setData(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get(),
                new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(
                        new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(60)));
        var policy = new EggLayerComponent("minecraft:egg", 60, 120, 60);
        if (!EggLayerSystem.tryLayEgg(chicken, policy)) throw new AssertionError("eligible chicken failed egg lay");
        boolean found = !level.getEntitiesOfClass(LooseItemEntity.class, chicken.getBoundingBox().inflate(2),
                item -> item.getItem().is(net.minecraft.world.item.Items.EGG)).isEmpty();
        if (!found) throw new AssertionError("egg output was not published through LooseItemEntity");
        chicken.discard();
        level.getEntitiesOfClass(LooseItemEntity.class, chicken.getBoundingBox().inflate(2),
                item -> item.getItem().is(net.minecraft.world.item.Items.EGG)).forEach(LooseItemEntity::discard);
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"animals", "character"})
    public static void canceledPublicationRollsBackProduction(GameTestHelper helper) {
        var level = helper.getLevel();
        var chicken = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().values().stream()
                .map(net.neoforged.neoforge.registries.DeferredHolder::get)
                .filter(type -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type)
                        .equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "chicken")))
                .findFirst().orElseThrow(() -> new AssertionError("MoonStation14 chicken host unavailable")).create(level);
        if (chicken == null) throw new AssertionError("chicken factory unavailable");
        chicken.moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)).getCenter());
        level.addFreshEntity(chicken);
        chicken.setData(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get(),
                new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(
                        new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(100)));
        rejectEggPublication = true;
        boolean produced;
        try { produced = EggLayerSystem.tryLayEgg(chicken, new EggLayerComponent("minecraft:egg", 60, 120, 60)); }
        finally { rejectEggPublication = false; }
        require(!produced, "canceled staged world publication reports failure");
        require(chicken.getData(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get()).hunger() == 100,
                "failed publication leaves hunger unchanged");
        require(level.getEntitiesOfClass(LooseItemEntity.class, chicken.getBoundingBox().inflate(2),
                item -> item.getItem().is(net.minecraft.world.item.Items.EGG)).isEmpty(), "failed egg is not left in world");
        chicken.discard();
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
