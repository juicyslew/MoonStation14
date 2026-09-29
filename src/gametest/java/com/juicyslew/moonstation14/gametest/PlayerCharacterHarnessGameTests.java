package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterAppearance;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerCharacterHarnessGameTests {
    private PlayerCharacterHarnessGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void characterHarnessIsDistinctPersistentMobWithoutAutomaticBinding(GameTestHelper helper) {
        PlayerCharacterHarnessEntity body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(1, 1, 1));
        Entity entity = body;
        require(entity.getType() == PlayerCharacterHarnessRegistration.getEntityType(), "uses registered type");
        require(PlayerCharacterHarnessRegistration.ID.toString().equals("moonstation14:player_character_harness"),
                "has dedicated player-character type id");
        require(body instanceof Mob && body.playerCharacterBinding() == null,
                "registration/spawn does not automatically bind an account, profile, or Mind");
        require(!body.isNoGravity() && !body.noPhysics,
                "uses ordinary gravity and collision rather than ghost movement policy");
        require(PlayerCharacterHarnessRegistration.getEntityType().canSerialize(), "entity type is saveable");
        if (MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()) {
            helper.succeed();
            return;
        }
        boolean boundWhileOff = PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(),
                "main", UUID.randomUUID(), ModCharacters.HUMAN_ID);
        require(!boundWhileOff && body.playerCharacterBinding() == null,
                "default-off startup gates prevent instance identity mutation");
        require(ModCharacters.characterForHost(helper.getLevel(), PlayerCharacterHarnessRegistration.ID).isEmpty(),
                "custom character path is explicitly bound, not mapped through host_entity_types");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void characterHarnessIsNoAiBeforeSpawnAndAfterAnySavedOwnerLoad(GameTestHelper helper) {
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "registered character type creates a body");
        require(body.isNoAi(), "new character body has no voluntary AI before world insertion");
        require(!body.isNoGravity() && !body.noPhysics,
                "no-AI policy retains ordinary gravity and collision before insertion");
        body.setPos(1.5, 1, 1.5);
        require(helper.getLevel().addFreshEntity(body), "new character body joins the GameTest level");
        require(body.isNoAi(), "character body remains no-AI after insertion");

        CompoundTag unbound = new CompoundTag();
        unbound.putBoolean("NoAI", false);
        assertSavedLoadReassertsNoAi(helper, unbound, 2, "unbound");

        CompoundTag bound = new CompoundTag();
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID());
        binding.putString("Profile", "main");
        binding.putUUID("Mind", UUID.randomUUID());
        bound.put("Moonstation14PlayerCharacterBinding", binding);
        bound.putBoolean("NoAI", false);
        assertSavedLoadReassertsNoAi(helper, bound, 3, "bound");

        CompoundTag invalidOwner = new CompoundTag();
        invalidOwner.putString("Moonstation14PlayerCharacterBinding", "invalid");
        invalidOwner.putBoolean("NoAI", false);
        assertSavedLoadReassertsNoAi(helper, invalidOwner, 4, "invalid-owner");
        helper.succeed();
    }

    private static void assertSavedLoadReassertsNoAi(GameTestHelper helper, CompoundTag saved, int x,
                                                       String ownerState) {
        PlayerCharacterHarnessEntity loaded = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(loaded != null, ownerState + " character body creates before NBT load");
        loaded.readAdditionalSaveData(saved);
        require(loaded.isNoAi(), ownerState + " saved NoAI=false is overridden after load");
        require(!loaded.isNoGravity() && !loaded.noPhysics,
                ownerState + " no-AI load preserves ordinary gravity and collision");
        if (ownerState.equals("invalid-owner")) {
            require(loaded.hasInvalidSavedBinding(), "invalid saved owner remains invalid-unbindable");
        } else if (ownerState.equals("bound")) {
            require(loaded.playerCharacterBinding() != null, "valid saved owner remains explicitly bound");
        } else {
            require(loaded.playerCharacterBinding() == null, "absent owner remains unbound");
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void malformedSavedBindingIsPermanentlyUnbindableAndPreserved(GameTestHelper helper) {
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("Moonstation14PlayerCharacterBinding", "raw-malformed-binding");
        assertMalformedSavedBindingRoundTrip(helper, wrongType, 1,
                "wrong-type malformed binding");

        CompoundTag partialBinding = new CompoundTag();
        CompoundTag partial = new CompoundTag();
        partial.putUUID("Account", UUID.randomUUID());
        partial.putString("Profile", "main");
        partialBinding.put("Moonstation14PlayerCharacterBinding", partial);
        assertMalformedSavedBindingRoundTrip(helper, partialBinding, 3,
                "partial-compound malformed binding");
        helper.succeed();
    }

    private static void assertMalformedSavedBindingRoundTrip(GameTestHelper helper, CompoundTag injected,
                                                               int x, String description) {
        PlayerCharacterHarnessEntity original = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(x, 1, 1));
        original.readAdditionalSaveData(injected);
        require(original.hasInvalidSavedBinding(), description + " is intrinsically invalid, regardless of gate");
        require(original.playerCharacterBinding() == null, description + " is not accepted as identity");
        CompoundTag saved = new CompoundTag();
        original.addAdditionalSaveData(saved);
        require(saved.get("Moonstation14PlayerCharacterBinding").equals(
                injected.get("Moonstation14PlayerCharacterBinding")), description + " survives source save");

        PlayerCharacterHarnessEntity restored = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(x + 1, 1, 1));
        restored.readAdditionalSaveData(saved);
        require(restored.hasInvalidSavedBinding(), description + " remains invalid after fresh-entity load");
        require(restored.playerCharacterBinding() == null, description + " remains unbound after fresh-entity load");
        CompoundTag resaved = new CompoundTag();
        restored.addAdditionalSaveData(resaved);
        require(resaved.get("Moonstation14PlayerCharacterBinding").equals(
                injected.get("Moonstation14PlayerCharacterBinding")), description + " is preserved exactly on resave");
        require(!PlayerCharacterHarnessBinder.bind(restored, UUID.randomUUID(), "main", UUID.randomUUID(),
                ModCharacters.HUMAN_ID), description + " is rejected by binder");
        if (MindGhostStartupGate.enabledForServer() && !MovementStartupGate.enabledForServer()) {
            require(!PlayerCharacterHarnessBinder.bind(original, UUID.randomUUID(), "main", UUID.randomUUID(),
                    ModCharacters.HUMAN_ID), description + " is rejected by binder with gates enabled");
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void validBindingRoundTripsThroughFreshEntity(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        UUID mind = UUID.randomUUID();
        CompoundTag savedBinding = new CompoundTag();
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", account);
        binding.putString("Profile", "main");
        binding.putUUID("Mind", mind);
        savedBinding.put("Moonstation14PlayerCharacterBinding", binding);

        PlayerCharacterHarnessEntity original = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(1, 1, 1));
        original.readAdditionalSaveData(savedBinding);
        require(!original.hasInvalidSavedBinding(), "valid saved binding is not marked invalid");
        require(account.equals(original.playerCharacterBinding().accountId())
                        && "main".equals(original.playerCharacterBinding().profileKey())
                        && mind.equals(original.playerCharacterBinding().mindId()),
                "valid owner/profile/Mind values load exactly");

        CompoundTag entitySave = new CompoundTag();
        original.addAdditionalSaveData(entitySave);
        PlayerCharacterHarnessEntity restored = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(3, 1, 1));
        restored.readAdditionalSaveData(entitySave);
        require(!restored.hasInvalidSavedBinding(), "valid binding remains valid after fresh-entity load");
        require(original.playerCharacterBinding().equals(restored.playerCharacterBinding()),
                "valid owner/profile/Mind binding is exact after entity save and fresh-entity load");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void explicitlyBoundMarkedCustomCharacterCanUseWorldStepWithoutHostMapping(GameTestHelper helper) {
        for (int x = 1; x <= 8; x++) {
            for (int z = 1; z <= 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        PlayerCharacterHarnessEntity body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(3, 1, 3));
        CompoundTag bindingTag = new CompoundTag();
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID());
        binding.putString("Profile", "main");
        binding.putUUID("Mind", UUID.randomUUID());
        bindingTag.put("Moonstation14PlayerCharacterBinding", binding);
        body.readAdditionalSaveData(bindingTag);
        require(CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID),
                "test attaches the authoritative resolved HUMAN prototype identity");
        require(com.juicyslew.moonstation14.ms14.interaction.ComplexInteractionSystem.enabled(body),
                "registered harness can receive human capability through explicit identity enrollment");
        require(ModCharacters.characterForHost(helper.getLevel(), PlayerCharacterHarnessRegistration.ID).isEmpty(),
                "custom body remains absent from host_entity_types");
        BlockPos absolute = helper.absolutePos(new BlockPos(3, 1, 3));
        body.setPos(absolute.getX() + .5d, absolute.getY(), absolute.getZ() + .5d);
        body.setDeltaMovement(Vec3.ZERO);
        body.move(MoverType.SELF, new Vec3(0, -.05d, 0));
        require(body.onGround(), "custom body is grounded before world step");
        MindControlledMob owner = (MindControlledMob) (Mob) body;
        owner.moonstation14$setMovementOwned(true);
        GroundedHarnessWorldStep step = new GroundedHarnessWorldStep();
        double startZ = body.getZ();
        require(step.step(body, 0, 1000, false, false, 0f).isPresent(),
                "explicit bound and marked custom character passes the alternate eligibility branch");
        require(body.getZ() > startZ, "accepted custom-character intent moves the body authoritatively");

        owner.moonstation14$setMovementOwned(false);
        require(step.step(body, 0, 1000, false, false, 0f).isEmpty(),
                "custom body without movement-owned marker is rejected");
        owner.moonstation14$setMovementOwned(true);
        PlayerCharacterHarnessEntity wrongIdentity = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(6, 1, 6));
        wrongIdentity.readAdditionalSaveData(bindingTag);
        CharacterIdentityAttachment wrong = new CharacterIdentityAttachment();
        wrong.bind(ResourceLocation.fromNamespaceAndPath("test", "not_human"));
        MS14Provider.update(wrongIdentity, MS14Bridges.CHARACTER_IDENTITY, wrong);
        ((MindControlledMob) (Mob) wrongIdentity).moonstation14$setMovementOwned(true);
        require(step.step(wrongIdentity, 0, 1000, false, false, 0f).isEmpty(),
                "custom body bound to a non-HUMAN identity is rejected");
        body.remove(Entity.RemovalReason.DISCARDED);
        require(step.step(body, 0, 1000, false, false, 0f).isEmpty(),
                "stale or removed custom body is rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void appearanceRoundTripsSeparatelyFromBindingAndMalformedAppearanceIsSafe(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        UUID mind = UUID.randomUUID();
        CompoundTag saved = new CompoundTag();
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", account);
        binding.putString("Profile", "main");
        binding.putUUID("Mind", mind);
        saved.put("Moonstation14PlayerCharacterBinding", binding);
        saved.putInt("Moonstation14PlayerCharacterAppearance", PlayerCharacterAppearance.ALEX.index());
        saved.putInt("Moonstation14PlayerCharacterBodyShape", PlayerCharacterBodyShape.SLIM.index());

        PlayerCharacterHarnessEntity original = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(1, 1, 1));
        original.readAdditionalSaveData(saved);
        require(original.appearance() == PlayerCharacterAppearance.ALEX, "supported appearance is loaded");
        require(original.bodyShape() == PlayerCharacterBodyShape.SLIM, "independent slim body shape is loaded");
        CompoundTag roundTrip = new CompoundTag();
        original.addAdditionalSaveData(roundTrip);
        PlayerCharacterHarnessEntity restored = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(3, 1, 1));
        restored.readAdditionalSaveData(roundTrip);
        require(restored.appearance() == PlayerCharacterAppearance.ALEX, "appearance persists across entity NBT");
        require(restored.bodyShape() == PlayerCharacterBodyShape.SLIM,
                "body shape persists independently across entity NBT");
        require(restored.playerCharacterBinding().accountId().equals(account)
                        && restored.playerCharacterBinding().mindId().equals(mind),
                "appearance persistence remains separate from owner/Mind binding");

        CompoundTag malformed = new CompoundTag();
        malformed.putInt("Moonstation14PlayerCharacterAppearance", 99);
        malformed.putString("Moonstation14PlayerCharacterBodyShape", "invalid-shape");
        malformed.put("Moonstation14PlayerCharacterBinding", binding.copy());
        restored.readAdditionalSaveData(malformed);
        require(restored.appearance() == PlayerCharacterAppearance.DEFAULT, "unsupported appearance falls back safely");
        require(restored.bodyShape() == PlayerCharacterBodyShape.WIDE, "malformed shape falls back safely");
        require(restored.playerCharacterBinding() != null
                        && restored.playerCharacterBinding().accountId().equals(account),
                "malformed appearance does not clear or make the bound body rebindable");
        CompoundTag preserved = new CompoundTag();
        restored.addAdditionalSaveData(preserved);
        require(preserved.getInt("Moonstation14PlayerCharacterAppearance") == 99,
                "malformed appearance evidence is retained on save");
        require(preserved.get("Moonstation14PlayerCharacterBodyShape").equals(
                        malformed.get("Moonstation14PlayerCharacterBodyShape")),
                "malformed shape evidence is retained on save");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void offlineBadgeTimestampPersistsAndFailsClosedOnMalformedData(GameTestHelper helper) {
        long offlineSince = 1_000_000L;
        require(!PlayerCharacterHarnessEntity.offlineBadgeDue(offlineSince, offlineSince + 59_999L),
                "offline badge is absent before the sixty-second boundary");
        require(PlayerCharacterHarnessEntity.offlineBadgeDue(offlineSince, offlineSince + 60_000L),
                "offline badge appears at the sixty-second boundary");
        PlayerCharacterHarnessEntity original = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(1, 1, 1));
        original.setOfflineSinceMillis(offlineSince);
        CompoundTag saved = new CompoundTag();
        original.addAdditionalSaveData(saved);
        PlayerCharacterHarnessEntity restored = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(3, 1, 1));
        restored.readAdditionalSaveData(saved);
        require(restored.offlineSinceMillis() == offlineSince,
                "offline timestamp survives fresh-entity NBT reload");
        restored.tick();
        require(!restored.hasOfflineBadge(), "persisted offline timestamp alone cannot label an unbound body");
        restored.clearOfflineSinceMillis();
        require(restored.offlineSinceMillis() == null && !restored.hasOfflineBadge(),
                "successful reconnect marker clear removes timestamp and synchronized badge");

        CompoundTag invalid = new CompoundTag();
        invalid.putString("Moonstation14PlayerCharacterOfflineSinceMillis", "not-a-timestamp");
        PlayerCharacterHarnessEntity malformed = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(5, 1, 1));
        malformed.readAdditionalSaveData(invalid);
        CompoundTag preservedInvalid = new CompoundTag();
        malformed.addAdditionalSaveData(preservedInvalid);
        require(malformed.offlineSinceMillis() == null
                        && preservedInvalid.getString("Moonstation14PlayerCharacterOfflineSinceMillis").equals("invalid"),
                "malformed timestamp fails closed and remains explicit in entity data");
        malformed.tick();
        require(!malformed.hasOfflineBadge(), "malformed timestamp cannot enable synchronized badge");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void offlineBadgeRequiresExactAccountDisconnectAndObservedGrace(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        long now = 1_000_000L;
        long parkedAt = now - 120_000L;
        long leftAt = now - 59_999L;
        require(!PlayerCharacterHarnessEntity.offlineBadgeVisible(owner, parkedAt, null, false, now),
                "an aged persisted timestamp alone is not proof of disconnection");
        require(!PlayerCharacterHarnessEntity.offlineBadgeVisible(owner, parkedAt, leftAt, true, now),
                "active character connected as owner cannot show Offline");
        require(!PlayerCharacterHarnessEntity.offlineBadgeVisible(owner, parkedAt, leftAt, true, now),
                "parked Creative operator connected as owner cannot show Offline");
        require(!PlayerCharacterHarnessEntity.offlineBadgeVisible(owner, parkedAt, leftAt - 60_000L, true, now),
                "connected ghost after corpse death cannot show Offline even after a long park");
        require(!PlayerCharacterHarnessEntity.offlineBadgeVisible(owner, parkedAt, leftAt, false, now),
                "real logout still has sixty seconds of display grace from observed departure");
        require(PlayerCharacterHarnessEntity.offlineBadgeVisible(owner, parkedAt, now - 60_000L, false, now),
                "real logout shows Offline once observed disconnect grace expires");
        require(!PlayerCharacterHarnessEntity.offlineBadgeVisible(owner, parkedAt, now - 60_000L, true, now),
                "reconnecting the same owner clears badge regardless of the persisted timestamp");
        require(!PlayerCharacterHarnessEntity.offlineBadgeVisible(null, parkedAt, now - 60_000L, false, now),
                "absent or malformed binding fails closed");
        require(!PlayerCharacterHarnessEntity.offlineBadgeVisible(owner, null, now - 60_000L, false, now),
                "no lifecycle offline timestamp fails closed");

        CompoundTag saved = new CompoundTag();
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", owner);
        binding.putString("Profile", "main");
        binding.putUUID("Mind", UUID.randomUUID());
        saved.put("Moonstation14PlayerCharacterBinding", binding);
        saved.putLong("Moonstation14PlayerCharacterOfflineSinceMillis", parkedAt);
        PlayerCharacterHarnessEntity body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(1, 1, 1));
        body.readAdditionalSaveData(saved);
        body.tick();
        require(!body.hasOfflineBadge(), "freshly observed disconnected bound body syncs false despite old NBT");
        CompoundTag malformed = saved.copy();
        malformed.putString("Moonstation14PlayerCharacterBinding", "bad-binding");
        body.readAdditionalSaveData(malformed);
        body.tick();
        require(!body.hasOfflineBadge(), "malformed binding syncs false even with old offline timestamp");
        CompoundTag preserved = new CompoundTag();
        body.addAdditionalSaveData(preserved);
        require(preserved.get("Moonstation14PlayerCharacterBinding").equals(
                malformed.get("Moonstation14PlayerCharacterBinding")),
                "malformed binding evidence is preserved after badge refresh");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void deadCharacterBodyRemainsInWorldWithoutBeingResurrected(GameTestHelper helper) {
        PlayerCharacterHarnessEntity body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(1, 1, 1));
        UUID bodyId = body.getUUID();
        body.setHealth(0.0F);
        body.die(helper.getLevel().damageSources().generic());
        require(body.hasConfirmedDeath(), "first accepted server death is exposed to a future adapter");
        require(!body.isAlive() && body.getHealth() <= 0.0F, "death does not restore health or living state");

        body.die(helper.getLevel().damageSources().generic());
        require(body.hasConfirmedDeath(), "repeated death leaves the one-shot confirmation set");
        helper.runAtTickTime(30, () -> {
            require(!body.isRemoved() && helper.getLevel().getEntity(bodyId) == body,
                    "custom character corpse remains loaded in the same world after vanilla's removal window");
            require(body.getUUID().equals(bodyId), "corpse identity does not change");
            require(!body.isAlive() && body.getHealth() <= 0.0F,
                    "corpse remains dead and is not resurrected after more than twenty ticks");
            require(body.hasConfirmedDeath(), "corpse retains its accepted-death signal");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
