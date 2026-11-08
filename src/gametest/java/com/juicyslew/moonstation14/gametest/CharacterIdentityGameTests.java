package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.eventhooks.ModEventHooks;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestTags({"character", "prototype"})
public final class CharacterIdentityGameTests {
    private CharacterIdentityGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"character", "prototype", "medical-state"})
    public static void mappedMouseEnrollmentProjectsKnownZeroAndPreservesDamage(GameTestHelper helper) {
        Mob mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "Mouse fixture must construct");
        mouse.setPos(1.5, 1, 1.5);
        require(helper.getLevel().addFreshEntity(mouse), "Mouse fixture must enter level");
        var mouseId = ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse");
        require(CharacterIdentitySystem.enroll(mouse, helper.getLevel(), mouseId), "resolved Mouse enrolls");
        var zero = mouse.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(zero != null && zero.getMap().isEmpty(), "enrollment publishes a known-zero medical ledger");
        require(DamageSystem.applyHealthChange(mouse, java.util.Map.of("blunt", 5f), 1f, true)
                        == DamageSystem.Result.APPLIED,
                "canonical damage path updates Mouse medical state");
        require(CharacterIdentitySystem.enroll(mouse, helper.getLevel(), mouseId), "same resolved role can reconcile");
            var damaged = mouse.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            require(damaged != null && damaged.getMap().getOrDefault("blunt", 0f) == 5f,
                    "idempotent enrollment preserves the authoritative damage ledger");
            mouse.setHealth(mouse.getMaxHealth() / 2f);
            require(DamageSystem.publishHealthState(mouse), "server can republish the current health ledger");
            require(mouse.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap()
                            .getOrDefault("blunt", 0f) == 5f,
                    "publishing does not rewrite an existing typed ledger from vanilla health");
            require(DamageSystem.applyHealthChange(mouse, java.util.Map.of("blunt", -5f), 1f, true)
                            == DamageSystem.Result.APPLIED,
                    "canonical healing path returns Mouse to known zero");
            var healed = mouse.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            require(healed != null && healed.isEmpty(),
                    "healing synchronizes a present empty ledger rather than reverting to unknown");
            require(DamageSystem.publishHealthState(mouse), "known-zero state can be republished");
            require(mouse.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap().isEmpty(),
                    "publishing known-zero must not import native vanilla health as blunt damage");
            helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void villagerJoinBindsHumanPolicyAndDanglingKeyRemainsInert(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        var unconfiguredCow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "identity-test"));
        player.setPos(3.5, 1, 1.5);
        require(helper.getLevel().addFreshEntity(player), "fake player must join the server level");

        helper.runAfterDelay(1, () -> {
            require(villager.hasData(ModDataAttachments.CHARACTER_IDENTITY.get()),
                    "real villager join must enroll a character key");
            require(pig.hasData(ModDataAttachments.CHARACTER_IDENTITY.get()),
                    "configured real Pig join must enroll a character key");
            require(player.hasData(ModDataAttachments.CHARACTER_IDENTITY.get()),
                    "fake player join must enroll a character key");
            var villagerIdentity = MS14Provider.getDetached(villager, MS14Bridges.CHARACTER_IDENTITY);
            var playerIdentity = MS14Provider.getDetached(player, MS14Bridges.CHARACTER_IDENTITY);
            ResourceLocation villagerKey = villagerIdentity.characterId();
            ResourceLocation playerKey = playerIdentity.characterId();
            require(villagerKey.equals(ModCharacters.HUMAN_ID) && playerKey.equals(ModCharacters.HUMAN_ID),
                    "villager and player keys must both be human");
            var pigIdentity = MS14Provider.getDetached(pig, MS14Bridges.CHARACTER_IDENTITY);
            var pigData = CharacterIdentitySystem.resolve(pig).orElseThrow();
            require(pigIdentity.characterId().equals(ResourceLocation.fromNamespaceAndPath("moonstation14", "pig")),
                    "configured Pig must resolve its data-driven Pig key");
            var pigMovement = pigData.component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class).orElseThrow();
            require(pigMovement.walkSpeed() == 4d && pigMovement.sprintSpeed() == 4d,
                    "Pig policy must carry typed 4/4 movement values");
            require(pigData.component(com.juicyslew.moonstation14.ms14.character.components.StunnableComponent.class).isEmpty() && pigData.component(com.juicyslew.moonstation14.ms14.character.components.NoSlipComponent.class).isPresent(),
                    "Pig local target components must disable stun and slipping");
            require(villagerKey.equals(playerKey), "villager and player must bind the same key");
            require(villager.level() instanceof ServerLevel && player.level() instanceof ServerLevel
                            && !villager.level().isClientSide && !player.level().isClientSide,
                    "both identity fixtures must be server-side actors");
            var villagerPolicy = CharacterIdentitySystem.resolve(villager).orElseThrow();
            var playerPolicy = CharacterIdentitySystem.resolve(player).orElseThrow();
            require(ActiveCharacterPolicy.resolveActor(player).orElseThrow().equals(playerPolicy),
                    "inactive FakePlayer keeps its legacy character policy");
            require(ActiveCharacterPolicy.resolveActor(pig).orElseThrow().equals(pigData),
                    "configured Mob keeps its own character policy");
            require(villagerPolicy.equals(playerPolicy),
                    "villager and player must resolve identical character data");
            require(villagerPolicy.component(com.juicyslew.moonstation14.ms14.character.components.ReactiveComponent.class).isPresent(),
                    "human character data must contain Reactive component");
            require(villagerPolicy.equals(ModCharacters.require(helper.getLevel(), ModCharacters.HUMAN_ID)),
                    "villager must resolve the same populated human policy used by player enrollment");
            require(MS14Provider.getDetached(villager, MS14Bridges.CHARACTER_IDENTITY).characterId()
                            .equals(villagerKey)
                            && MS14Provider.getDetached(player, MS14Bridges.CHARACTER_IDENTITY).characterId()
                            .equals(playerKey),
                    "identity reads must not change either actor's key");
            require(!unconfiguredCow.hasData(ModDataAttachments.CHARACTER_IDENTITY.get())
                            && CharacterIdentitySystem.resolve(unconfiguredCow).isEmpty(),
                    "unconfigured Mob hosts remain inert and identity reads must not materialize state");
            require(!unconfiguredCow.hasData(ModDataAttachments.CHARACTER_IDENTITY.get()),
                    "failed identity lookup must leave absence unmaterialized");

            // A dangling existing key remains intact and resolves inertly; enrollment must not replace it.
            var invalid = new CharacterIdentityAttachment();
            var invalidId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "dangling");
            invalid.bind(invalidId);
            MS14Provider.update(villager, MS14Bridges.CHARACTER_IDENTITY, invalid);
            MS14Provider.update(pig, MS14Bridges.CHARACTER_IDENTITY, invalid);
            CharacterIdentitySystem.enrollSupportedActor(pig, helper.getLevel());
            require(MS14Provider.getDetached(pig, MS14Bridges.CHARACTER_IDENTITY).characterId()
                            .equals(invalidId), "join enrollment must not overwrite a dangling Pig key");
            require(CharacterIdentitySystem.resolve(pig).isEmpty(), "dangling Pig identity must fail closed");

            FakePlayer danglingPlayer = new FakePlayer(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "dangling-rejoin-test"));
            MS14Provider.update(danglingPlayer, MS14Bridges.CHARACTER_IDENTITY, invalid);
            danglingPlayer.setPos(4.5, 1, 1.5);
            require(helper.getLevel().addFreshEntity(danglingPlayer), "dangling player must rejoin the level");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> {
                require(MS14Provider.getDetached(danglingPlayer, MS14Bridges.CHARACTER_IDENTITY)
                                .characterId().equals(invalidId),
                        "join enrollment must not overwrite a dangling persisted key");
                require(CharacterIdentitySystem.resolve(danglingPlayer).isEmpty(),
                        "rejoined player with a dangling key must remain inert");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void playerClonePreservesIdentityAfterDeath(GameTestHelper helper) {
        verifyCloneIdentity(helper, true);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
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
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> {
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
