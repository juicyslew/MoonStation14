package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.eventhooks.ModEventHooks;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CharacterIdentityGameTests {
    private CharacterIdentityGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void villagerJoinBindsHumanPolicyAndDanglingKeyRemainsInert(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Pig unadaptedPig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "identity-test"));
        player.setPos(3.5, 1, 1.5);
        require(helper.getLevel().addFreshEntity(player), "fake player must join the server level");

        helper.runAfterDelay(1, () -> {
            require(villager.hasData(ModDataAttachments.CHARACTER_IDENTITY.get()),
                    "real villager join must enroll a character key");
            require(player.hasData(ModDataAttachments.CHARACTER_IDENTITY.get()),
                    "fake player join must enroll a character key");
            var villagerIdentity = MS14Provider.getDetached(villager, MS14Bridges.CHARACTER_IDENTITY);
            var playerIdentity = MS14Provider.getDetached(player, MS14Bridges.CHARACTER_IDENTITY);
            ResourceLocation villagerKey = villagerIdentity.characterId();
            ResourceLocation playerKey = playerIdentity.characterId();
            require(villagerKey.equals(ModCharacters.HUMAN_ID) && playerKey.equals(ModCharacters.HUMAN_ID),
                    "villager and player keys must both be human");
            require(villagerKey.equals(playerKey), "villager and player must bind the same key");
            require(villager.level() instanceof ServerLevel && player.level() instanceof ServerLevel
                            && !villager.level().isClientSide && !player.level().isClientSide,
                    "both identity fixtures must be server-side actors");
            var villagerPolicy = CharacterIdentitySystem.resolve(villager).orElseThrow();
            var playerPolicy = CharacterIdentitySystem.resolve(player).orElseThrow();
            require(villagerPolicy.equals(playerPolicy),
                    "villager and player must resolve identical character data");
            require(!villagerPolicy.slipData().reactiveGroups().isEmpty()
                            && !villagerPolicy.slipData().reactiveMethods().isEmpty(),
                    "human character data must contain populated slip_data");
            require(villagerPolicy.equals(ModCharacters.require(helper.getLevel(), ModCharacters.HUMAN_ID)),
                    "villager must resolve the same populated human policy used by player enrollment");
            require(MS14Provider.getDetached(villager, MS14Bridges.CHARACTER_IDENTITY).characterId()
                            .equals(villagerKey)
                            && MS14Provider.getDetached(player, MS14Bridges.CHARACTER_IDENTITY).characterId()
                            .equals(playerKey),
                    "identity reads must not change either actor's key");
            require(!unadaptedPig.hasData(ModDataAttachments.CHARACTER_IDENTITY.get())
                            && CharacterIdentitySystem.resolve(unadaptedPig).isEmpty(),
                    "unadapted actors remain inert and identity reads must not materialize state");
            require(!unadaptedPig.hasData(ModDataAttachments.CHARACTER_IDENTITY.get()),
                    "failed identity lookup must leave absence unmaterialized");

            // A dangling existing key remains intact and resolves inertly; enrollment must not replace it.
            var invalid = new CharacterIdentityAttachment();
            var invalidId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "dangling");
            invalid.bind(invalidId);
            MS14Provider.update(villager, MS14Bridges.CHARACTER_IDENTITY, invalid);
            require(!CharacterIdentitySystem.enroll(villager, helper.getLevel(), ModCharacters.HUMAN_ID),
                    "dangling key must not be overwritten");
            require(MS14Provider.getDetached(villager, MS14Bridges.CHARACTER_IDENTITY).characterId()
                            .equals(invalidId), "invalid key must remain diagnostic and unchanged");
            require(CharacterIdentitySystem.resolve(villager).isEmpty(), "dangling identity must fail closed");

            FakePlayer danglingPlayer = new FakePlayer(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "dangling-rejoin-test"));
            MS14Provider.update(danglingPlayer, MS14Bridges.CHARACTER_IDENTITY, invalid);
            danglingPlayer.setPos(4.5, 1, 1.5);
            require(helper.getLevel().addFreshEntity(danglingPlayer), "dangling player must rejoin the level");
            helper.runAfterDelay(1, () -> {
                require(MS14Provider.getDetached(danglingPlayer, MS14Bridges.CHARACTER_IDENTITY)
                                .characterId().equals(invalidId),
                        "join enrollment must not overwrite a dangling persisted key");
                require(CharacterIdentitySystem.resolve(danglingPlayer).isEmpty(),
                        "rejoined player with a dangling key must remain inert");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void playerClonePreservesIdentityAfterDeath(GameTestHelper helper) {
        verifyCloneIdentity(helper, true);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void playerClonePreservesIdentityWithoutDeath(GameTestHelper helper) {
        verifyCloneIdentity(helper, false);
    }

    private static void verifyCloneIdentity(GameTestHelper helper, boolean wasDeath) {
        FakePlayer original = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "clone-original"));
        original.setPos(3.5, 1, 1.5);
        require(helper.getLevel().addFreshEntity(original), "original player must join the level");
        helper.runAfterDelay(1, () -> {
            var originalKey = MS14Provider.getDetached(original, MS14Bridges.CHARACTER_IDENTITY).characterId();
            require(originalKey.equals(ModCharacters.HUMAN_ID), "joined original must have its human key");

            FakePlayer clone = new FakePlayer(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "clone-target"));
            PlayerEvent.Clone event = new PlayerEvent.Clone(clone, original, wasDeath);
            ModEventHooks.applyPlayerCloneIdentityPolicy(event);
            require(clone.hasData(ModDataAttachments.CHARACTER_IDENTITY.get()),
                    "clone event must copy identity before clone joins");
            require(MS14Provider.getDetached(clone, MS14Bridges.CHARACTER_IDENTITY).characterId()
                            .equals(originalKey),
                    "clone must preserve the exact original identity key");

            clone.setPos(4.5, 1, 1.5);
            require(helper.getLevel().addFreshEntity(clone), "identity-bearing clone must rejoin the level");
            helper.runAfterDelay(1, () -> {
                require(MS14Provider.getDetached(clone, MS14Bridges.CHARACTER_IDENTITY).characterId()
                                .equals(originalKey),
                        "join enrollment must leave the cloned key unchanged");
                require(CharacterIdentitySystem.resolve(clone).isPresent(),
                        "rejoined clone must resolve its preserved identity");
                helper.succeed();
            });
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
