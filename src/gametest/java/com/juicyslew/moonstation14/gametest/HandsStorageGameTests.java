package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import com.juicyslew.moonstation14.ms14.hands.HandAttachment;
import com.juicyslew.moonstation14.ms14.hands.HandComponent;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HandsStorageGameTests {
    private HandsStorageGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void handAttachmentPersistsOnEntitySaveAndLoadWithoutMaterializingOthers(GameTestHelper helper) {
        Zombie body = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie untouched = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 1));
        require(!body.hasData(ModDataAttachments.HANDS.get()), "new entity should not start with hand state");
        require(!untouched.hasData(ModDataAttachments.HANDS.get()), "unbound entity should remain unmaterialized");

        HandState state = HandState.create(java.util.List.of("left", "right"), "right")
                .place("left", new ItemToken("opaque-gametest-token")).state();
        MS14Provider.update(body, MS14Bridges.HANDS, HandComponent.from(state).toAttachment());
        CompoundTag saved = body.saveWithoutId(new CompoundTag());

        Zombie loaded = EntityType.ZOMBIE.create(helper.getLevel());
        require(loaded != null, "test entity type must be constructible");
        loaded.load(saved);
        HandAttachment restored = loaded.getExistingDataOrNull(ModDataAttachments.HANDS.get());
        require(restored != null, "entity load should restore registered hands attachment");
        require(restored.toComponent().equals(HandComponent.from(state)), "loaded hand component must match saved body state");
        require(!untouched.hasData(ModDataAttachments.HANDS.get()), "reading another entity must not create hands data");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void capabilityResolvesBoundBodyPrototypeReadOnly(GameTestHelper helper) {
        Villager humanBody = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Pig pigBody = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        Zombie unboundBody = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        helper.runAfterDelay(1, () -> {
            require(HandCapability.resolve(humanBody).orElseThrow().equals(java.util.List.of("left", "right")),
                    "human body must resolve its prototype-declared ordered hand IDs");
            require(HandCapability.resolve(pigBody).isEmpty(), "prototype without hands must have no capability");
            require(HandCapability.resolve(unboundBody).isEmpty(), "unbound body must have no capability");
            require(!humanBody.hasData(ModDataAttachments.HANDS.get())
                            && !pigBody.hasData(ModDataAttachments.HANDS.get())
                            && !unboundBody.hasData(ModDataAttachments.HANDS.get()),
                    "capability reads must not materialize hands state");

            CharacterIdentityAttachment dangling = new CharacterIdentityAttachment();
            dangling.bind(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "missing-character"));
            MS14Provider.update(unboundBody, MS14Bridges.CHARACTER_IDENTITY, dangling);
            require(HandCapability.resolve(unboundBody).isEmpty(), "dangling body identity must have no capability");
            require(MS14Provider.getDetached(unboundBody, MS14Bridges.CHARACTER_IDENTITY).characterId()
                            .equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "missing-character")),
                    "capability lookup must not rewrite a dangling identity");
            require(!humanBody.hasData(ModDataAttachments.HANDS.get())
                            && !pigBody.hasData(ModDataAttachments.HANDS.get())
                            && !unboundBody.hasData(ModDataAttachments.HANDS.get()),
                    "failed and successful capability reads must remain read-only");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mismatchedBoundHostCannotExposeHandsOrStun(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Pig foreignHost = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        helper.runAfterDelay(1, () -> {
            require(HandCapability.resolve(body).orElseThrow().equals(java.util.List.of("left", "right")),
                    "fixture should initially have a matching character host");
            HandComponent held = HandComponent.from(HandState.create(java.util.List.of("right", "left"))
                    .place("left", new ItemToken("preserved-token")).state());
            MS14Provider.update(body, MS14Bridges.HANDS, held.toAttachment());
            CharacterIdentityAttachment wrong = new CharacterIdentityAttachment();
            wrong.bind(net.minecraft.resources.ResourceLocation.parse("moonstation14:pig"));
            MS14Provider.update(body, MS14Bridges.CHARACTER_IDENTITY, wrong);
            CharacterIdentityAttachment human = new CharacterIdentityAttachment();
            human.bind(net.minecraft.resources.ResourceLocation.parse("moonstation14:human"));
            MS14Provider.update(foreignHost, MS14Bridges.CHARACTER_IDENTITY, human);
            require(HandCapability.resolve(body).isEmpty(), "foreign bound identity must not expose hands");
            require(HandCapability.resolve(foreignHost).isEmpty(),
                    "a foreign host must not acquire the human hand component");
            require(com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy
                    .resolveActor(body).isEmpty(), "foreign identity must not expose stun or slip policy");
            require(com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy
                    .resolveActor(foreignHost).isEmpty(), "foreign host must not expose human stun policy");
            require(held.equals(body.getExistingDataOrNull(ModDataAttachments.HANDS.get()).toComponent()),
                    "inert lookup must preserve stored hand layout and held token");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
