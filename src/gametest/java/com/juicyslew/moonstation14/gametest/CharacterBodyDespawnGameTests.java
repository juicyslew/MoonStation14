package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CharacterBodyDespawnGameTests {
    private CharacterBodyDespawnGameTests() { }

    /**
     * Covers vanilla's natural far-player despawn branch with a real Creative server player.
     * This is NOT a chunk-persistence test: GameTest does not provide a safe public API to
     * force its entity chunk to unload and then reload through the server entity inbox.
     * The remote fixture may not be visible to ServerLevel.getEntity(UUID) until its
     * chunk becomes entity-visible; only the originally added instance is checked here.
     */
    @GameTest(template = "empty", batch = "character_body_despawn", timeoutTicks = 100)
    public static void creativeOwnerFarAwayCannotNaturallyDespawnBoundBody(GameTestHelper helper) {
        // Place away from other batches' lingering FakePlayers, as in the existing despawn test.
        PlayerCharacterHarnessEntity body = spawnUnforced(helper, 5);
        UUID id = body.getUUID();
        UUID account = UUID.randomUUID();
        PlayerCharacterBinding expected = new PlayerCharacterBinding(account, "main", UUID.randomUUID());
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", expected.accountId());
        binding.putString("Profile", "main");
        binding.putUUID("Mind", expected.mindId());
        CompoundTag owner = new CompoundTag();
        owner.put("Moonstation14PlayerCharacterBinding", binding);
        body.readAdditionalSaveData(owner);
        require(CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID),
                "body receives HUMAN identity");
        require(BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED,
                "body receives live hands");
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(hands != null && hands.compatible(body), "live hands match HUMAN prototype");

        FakePlayer creative = new FakePlayer(helper.getLevel(), new GameProfile(account, "creative-owner"));
        creative.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        creative.setPos(body.getX() + 200, body.getY(), body.getZ());
        require(helper.getLevel().addFreshEntity(creative), "Creative owner is registered in the server level");
        try {
            int despawn = body.getType().getCategory().getDespawnDistance();
            require(creative.isCreative() && helper.getLevel().getNearestPlayer(body, -1) == creative
                            && creative.distanceToSqr(body) > (double) despawn * despawn,
                    "Creative owner is the actual nearest player beyond instant despawn distance: nearest="
                            + helper.getLevel().getNearestPlayer(body, -1) + ", radius=" + despawn
                            + ", distance=" + creative.distanceTo(body));
            require(!body.isPersistenceRequired() && body.requiresCustomPersistence(),
                    "fixture exercises custom persistence instead of vanilla persistenceRequired");
            body.checkDespawn();
            require(!body.isRemoved() && body.isAddedToLevel() && body.getUUID().equals(id)
                            && expected.equals(body.playerCharacterBinding()),
                    "same added body's UUID and exact owner/Mind binding survive vanilla checkDespawn");
            require(ModCharacters.HUMAN_ID.equals(body.getExistingDataOrNull(
                            ModDataAttachments.CHARACTER_IDENTITY.get()).characterId())
                            && CharacterIdentitySystem.resolveForHost(body).isPresent()
                            && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                            && hands.compatible(body),
                    "same body retains HUMAN prototype and live hands without replacement");
        } finally {
            creative.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "character_body_despawn", timeoutTicks = 100)
    public static void farPlayerCannotDespawnBoundUnboundOrCorpse(GameTestHelper helper) {
        PlayerCharacterHarnessEntity bound = spawnUnforced(helper, 1);
        PlayerCharacterHarnessEntity unbound = spawnUnforced(helper, 2);
        PlayerCharacterHarnessEntity corpse = spawnUnforced(helper, 3);
        // Other GameTest batches leave FakePlayers behind near their structures; these bodies
        // were registered 1000 blocks away so the deliberate player can be nearest.
        UUID boundId = bound.getUUID(), unboundId = unbound.getUUID(), corpseId = corpse.getUUID();
        UUID account = UUID.randomUUID(), mind = UUID.randomUUID();
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", account);
        binding.putString("Profile", "main");
        binding.putUUID("Mind", mind);
        CompoundTag owner = new CompoundTag();
        owner.put("Moonstation14PlayerCharacterBinding", binding);
        bound.readAdditionalSaveData(owner);
        require(CharacterIdentitySystem.enroll(bound, helper.getLevel(), ModCharacters.HUMAN_ID),
                "bound body receives HUMAN identity");
        require(BodyHandBootstrap.ensure(bound) == BodyHandBootstrap.Result.INITIALIZED,
                "bound body receives real live hands");
        bound.setOfflineSinceMillis(12345L);
        corpse.die(helper.getLevel().damageSources().generic());
        require(corpse.hasConfirmedDeath() && !corpse.isRemoved(), "corpse is confirmed and still in world");

        // A registered server-level player, not a fabricated distance passed to a pure policy.
        FakePlayer distant = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "far-body-test"));
        distant.setPos(bound.getX() + 200, bound.getY(), bound.getZ());
        require(helper.getLevel().addFreshEntity(distant), "distant server player joins level");
        for (PlayerCharacterHarnessEntity body : new PlayerCharacterHarnessEntity[]{bound, unbound, corpse}) {
            require(helper.getLevel().getNearestPlayer(body, -1) == distant,
                    "actual nearest server player is the distant fixture: nearest="
                            + helper.getLevel().getNearestPlayer(body, -1));
            int despawn = body.getType().getCategory().getDespawnDistance();
            require(distant.distanceToSqr(body) > (double) despawn * despawn,
                    "player is beyond vanilla category instant-despawn radius");
            require(!body.isPersistenceRequired() && body.requiresCustomPersistence(),
                    "body relies on dedicated custom persistence, not vanilla persistenceRequired");
            // Exercise the real Mob.checkDespawn branch while the structure chunk is loaded.
            body.checkDespawn();
            require(!body.isRemoved(), "far-player checkDespawn cannot discard character body");
        }
        // This checks distance despawn before any chunk transition; it does not observe
        // a server entity-chunk unload or a reload from disk.
        {
            verify(helper, bound, boundId);
            verify(helper, unbound, unboundId);
            verify(helper, corpse, corpseId);
            require(corpse.hasConfirmedDeath(), "corpse remains confirmed after natural ticks");
            require(bound.playerCharacterBinding().equals(new PlayerCharacterBinding(account, "main", mind)),
                    "exact owner/profile/Mind binding survives distance and ticks");
            require(Long.valueOf(12345L).equals(bound.offlineSinceMillis()),
                    "OFFLINE timestamp remains unchanged by despawn protection");

            // Full vanilla entity save/load, including UUID and NeoForge attachments; no replacement is spawned.
            CompoundTag saved = bound.saveWithoutId(new CompoundTag());
            PlayerCharacterHarnessEntity loaded = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
            require(loaded != null, "registered type can reload saved body");
            loaded.load(saved);
            require(loaded.getUUID().equals(boundId) && loaded.playerCharacterBinding().equals(bound.playerCharacterBinding()),
                    "save/load preserves exact UUID and binding");
            require(loaded.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get()) != null
                            && ModCharacters.HUMAN_ID.equals(loaded.getExistingDataOrNull(
                                    ModDataAttachments.CHARACTER_IDENTITY.get()).characterId())
                            && CharacterIdentitySystem.resolveForHost(loaded).isPresent()
                            && loaded.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != null,
                    "save/load preserves HUMAN host and live hands");
            require(Long.valueOf(12345L).equals(loaded.offlineSinceMillis()),
                    "OFFLINE claim timestamp survives save/load");
            CompoundTag corpseSave = corpse.saveWithoutId(new CompoundTag());
            PlayerCharacterHarnessEntity loadedCorpse = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
            require(loadedCorpse != null, "corpse can reload");
            loadedCorpse.load(corpseSave);
            require(loadedCorpse.getUUID().equals(corpseId) && loadedCorpse.hasConfirmedDeath(),
                    "corpse retains exact UUID and confirmed death after save/load");
            distant.discard();
            helper.succeed();
        }
    }

    private static void verify(GameTestHelper helper, PlayerCharacterHarnessEntity body, UUID id) {
        require(!body.isRemoved() && body.isAddedToLevel() && body.getUUID().equals(id),
                "original added character entity must remain intact under its exact UUID");
    }

    private static PlayerCharacterHarnessEntity spawnUnforced(GameTestHelper helper, int x) {
        // GameTestHelper.spawn() calls setPersistenceRequired() on Mobs, masking the bug.
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "registered character body creates");
        BlockPos position = helper.absolutePos(new BlockPos(x + 1000, 1, 1));
        helper.getLevel().getChunkAt(position);
        body.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
        require(helper.getLevel().addFreshEntity(body), "unforced character body joins server level");
        require(!body.isPersistenceRequired(), "fixture must not mask distance despawn with persistenceRequired");
        return body;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
