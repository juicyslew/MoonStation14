package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerCharacterHarnessBindingTest {
    @Test
    void bindingIsImmutableAndNbtRoundTripsOwnerMindAndProfile() {
        PlayerCharacterBinding binding = new PlayerCharacterBinding(UUID.randomUUID(),
                "main", UUID.randomUUID());
        CompoundTag saved = binding.save();
        assertEquals(binding, PlayerCharacterBinding.load(saved));
        assertThrows(IllegalArgumentException.class, () -> PlayerCharacterBinding.load(new CompoundTag()));
        assertEquals("main", saved.getString("Profile"));
        CompoundTag missingMind = binding.save();
        missingMind.remove("Mind");
        assertThrows(IllegalArgumentException.class, () -> PlayerCharacterBinding.load(missingMind));
        CompoundTag invalidProfile = binding.save();
        invalidProfile.putString("Profile", "minecraft:main");
        assertThrows(IllegalArgumentException.class, () -> PlayerCharacterBinding.load(invalidProfile));
        assertThrows(IllegalArgumentException.class, () -> new PlayerCharacterBinding(binding.accountId(),
                "bad/profile", binding.mindId()));
    }

    @Test
    void malformedBindingDataIsRejectedForWrongFieldTypeAndPartialCompound() {
        CompoundTag partial = new CompoundTag();
        partial.putUUID("Account", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> PlayerCharacterBinding.load(partial));
        CompoundTag wrongFieldType = new CompoundTag();
        wrongFieldType.putString("Account", "not-a-uuid");
        wrongFieldType.putString("Profile", "main");
        wrongFieldType.putUUID("Mind", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> PlayerCharacterBinding.load(wrongFieldType));
    }

    @Test
    void mismatchingIdentityIsNotAnUpdate() {
        PlayerCharacterBinding first = new PlayerCharacterBinding(UUID.randomUUID(),
                "main", UUID.randomUUID());
        PlayerCharacterBinding differentOwner = new PlayerCharacterBinding(UUID.randomUUID(),
                first.profileKey(), first.mindId());
        assertNotEquals(first, differentOwner);
        assertTrue(PlayerCharacterHarnessBinder.canReuseBinding(first, first));
        assertFalse(PlayerCharacterHarnessBinder.canReuseBinding(first, differentOwner));
        assertTrue(PlayerCharacterHarnessBinder.canReuseBinding(null, first));
    }

    @Test
    void bindingKeyValidationAllowsResolvedOrdinaryPrototypesButNotNull() {
        assertTrue(PlayerCharacterHarnessBinder.isSupportedPrototypeKey(ModCharacters.HUMAN_ID));
        assertTrue(PlayerCharacterHarnessBinder.isSupportedPrototypeKey(
                ResourceLocation.fromNamespaceAndPath("minecraft", "pig")));
        assertFalse(PlayerCharacterHarnessBinder.isSupportedPrototypeKey(null));
    }

    @Test
    void ordinaryMobLifecycleBindingCodecRoundTripsAndRejectsMalformedIdentity() {
        var binding = new PlayerCharacterBinding(UUID.randomUUID(), "role_1", UUID.randomUUID());
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        var encoded = PlayerCharacterBinding.CODEC.encodeStart(ops, binding).getOrThrow();
        assertEquals(binding, PlayerCharacterBinding.CODEC.parse(ops, encoded).getOrThrow());
        var malformed = new com.google.gson.JsonObject();
        malformed.addProperty("account", "not-a-uuid");
        malformed.addProperty("profile", "role_1");
        malformed.addProperty("mind", UUID.randomUUID().toString());
        assertTrue(PlayerCharacterBinding.CODEC.parse(ops, malformed).error().isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerCharacterBinding(UUID.randomUUID(), "bad/profile", UUID.randomUUID()));
    }

    @Test
    void appearanceIndicesAreBoundedAndOwnerEditPolicyRequiresActiveExactLifecycleBinding() {
        assertEquals(PlayerCharacterAppearance.DEFAULT, PlayerCharacterAppearance.fromIndex(0));
        assertEquals(PlayerCharacterAppearance.ALEX, PlayerCharacterAppearance.fromIndex(1));
        assertNull(PlayerCharacterAppearance.fromIndex(-1));
        assertNull(PlayerCharacterAppearance.fromIndex(2));
        assertEquals(PlayerCharacterBodyShape.WIDE, PlayerCharacterBodyShape.fromIndex(0));
        assertEquals(PlayerCharacterBodyShape.SLIM, PlayerCharacterBodyShape.fromIndex(1));
        assertNull(PlayerCharacterBodyShape.fromIndex(-1));
        assertNull(PlayerCharacterBodyShape.fromIndex(2));
        assertEquals(4, PlayerCharacterAppearance.values().length * PlayerCharacterBodyShape.values().length,
                "skin selection and geometry form four independent combinations");

        UUID owner = UUID.randomUUID();
        UUID mind = UUID.randomUUID();
        MobHarnessId body = new MobHarnessId(UUID.randomUUID());
        long generation = 12;
        PlayerCharacterBinding binding = new PlayerCharacterBinding(owner, "main", mind);
        PlayerLifecycleRegistry.Snapshot active = lifecycle(owner, "main", mind, body, generation,
                PlayerLifecycleRegistry.LifecycleState.ACTIVE, true);
        assertTrue(canEdit(binding, owner, active, body, generation));
        assertFalse(PlayerCharacterAppearanceService.canEdit(false, binding, owner,
                active, body, generation),
                "off-thread requests cannot change authoritative appearance");
        assertTrue(PlayerCharacterAppearanceService.canEdit(true, binding, owner,
                active, body, generation),
                "the active exact lifecycle binding permits owner edits on the server thread");
        assertFalse(canEdit(binding, UUID.randomUUID(), active, body, generation),
                "a different account cannot change the bound character's appearance");
        assertFalse(canEdit(null, owner, active, body, generation),
                "an unbound body has no owner authorized to change appearance");
        assertFalse(canEdit(binding, owner, lifecycle(owner, "main", UUID.randomUUID(), body, generation,
                PlayerLifecycleRegistry.LifecycleState.ACTIVE, true), body, generation),
                "a different lifecycle Mind cannot authorize the entity binding");
        assertFalse(canEdit(binding, owner, lifecycle(owner, "alternate", mind, body, generation,
                PlayerLifecycleRegistry.LifecycleState.ACTIVE, true), body, generation),
                "a different lifecycle profile cannot authorize the entity binding");
        assertFalse(canEdit(binding, owner, active, new MobHarnessId(UUID.randomUUID()), generation),
                "a different lifecycle body cannot authorize this entity");
        assertFalse(canEdit(binding, owner, active, body, generation - 1),
                "a stale server-known generation cannot authorize an edit");
        assertFalse(canEdit(binding, owner, lifecycle(owner, "main", mind, body, generation,
                PlayerLifecycleRegistry.LifecycleState.OFFLINE, false), body, generation),
                "an inactive lifecycle snapshot cannot authorize an edit");
    }

    private static boolean canEdit(PlayerCharacterBinding binding, UUID accountId,
                                   PlayerLifecycleRegistry.Snapshot profile, MobHarnessId bodyId,
                                   long generation) {
        return PlayerCharacterAppearanceService.canEdit(true, binding, accountId,
                profile, bodyId, generation);
    }

    private static PlayerLifecycleRegistry.Snapshot lifecycle(UUID accountId, String profileId, UUID mindId,
            MobHarnessId bodyId, long generation, PlayerLifecycleRegistry.LifecycleState state, boolean active) {
        return new PlayerLifecycleRegistry.Snapshot(PlayerLifecycleRegistry.SCHEMA_VERSION, accountId,
                profileId, new MindId(mindId), bodyId, state, generation, active, false);
    }

    @Test
    void appearanceAuthenticationRequiresTheExactLiveConnectedPlayerInTheBodyWorld() {
        assertTrue(PlayerCharacterAppearanceService.isAuthenticatedPlayerPolicy(
                true, true, true, true, true, true, true));
        assertFalse(PlayerCharacterAppearanceService.isAuthenticatedPlayerPolicy(
                false, true, true, true, true, true, true), "fake players are not authenticated owners");
        assertFalse(PlayerCharacterAppearanceService.isAuthenticatedPlayerPolicy(
                true, false, true, true, true, true, true), "stale handles are rejected");
        assertFalse(PlayerCharacterAppearanceService.isAuthenticatedPlayerPolicy(
                true, true, true, true, false, true, true), "the owner and body must share a world");
        assertFalse(PlayerCharacterAppearanceService.isAuthenticatedPlayerPolicy(
                true, true, true, true, true, false, true), "disconnected players are rejected");
        assertFalse(PlayerCharacterAppearanceService.isAuthenticatedPlayerPolicy(
                true, true, true, true, true, true, false),
                "the player-list registration must be the exact ServerPlayer instance");
    }
}
