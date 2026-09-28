package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
/** Durable corpse-death evidence and connected corpse-to-ghost handoff. */
public final class LifecycleCharacterDeathHandler {
    private LifecycleCharacterDeathHandler() { }

    /** Called solely by PlayerCharacterHarnessEntity after vanilla accepts its first server-side death. */
    public static void onConfirmedDeath(PlayerCharacterHarnessEntity body) {
        if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()) return;
        if (body == null || !(body.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        if (server == null || !callbackEligible(true, false, body.hasConfirmedDeath(), server.isSameThread(),
                !level.isClientSide && !body.isRemoved() && body.isAddedToLevel()
                        && body.getType() == PlayerCharacterHarnessRegistration.getEntityType()
                        && level.getEntity(body.getUUID()) == body,
                body.isDeadOrDying() || body.getHealth() <= 0) || body.hasInvalidSavedBinding()) return;
        var binding = body.playerCharacterBinding();
        var identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (binding == null || identity == null || !identity.isBound()
                || !ModCharacters.HUMAN_ID.equals(identity.characterId())
                || CharacterIdentitySystem.resolve(body).isEmpty()) return;

        var snapshot = LifecycleStartupRuntime.snapshot(server).orElse(null);
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (snapshot == null || context == null) return;
        if (LifecycleCharacterSessionControl.hasExactDeathSession(body)) {
            LifecycleCharacterSessionControl.claimConfirmedDeath(body, context);
            return;
        }
        if (snapshot.state() != LifecycleStartupRuntime.State.DEFERRED) return;
        MobHarness corpse = new MobHarness(new MobHarnessId(body.getUUID()), MobHarnessKind.CHARACTER);
        try {
            SavedLifecycleProfile saved = context.primaryStore().withCurrentPrimary(lease -> {
                var matches = lease.envelope().profiles().stream()
                        .filter(row -> row.accountId().equals(binding.accountId())
                                && row.profileKey().equals(binding.profileKey())
                                && row.mindId().equals(binding.mindId())
                                && row.bodyId().equals(body.getUUID())
                                && row.state() == SavedLifecycleProfile.State.OFFLINE).toList();
                return matches.size() == 1 ? matches.get(0) : null;
            }).orElse(null);
            if (saved == null || !saved.dimension().equals(level.dimension().location().toString())) return;
            boolean claimed = context.claimOfflineDeath(saved, corpse, candidate -> candidate.equals(corpse)
                    && body.hasConfirmedDeath() && body.getHealth() <= 0 && !body.isRemoved()
                    && body.level() == level && level.getEntity(body.getUUID()) == body
                    && binding.accountId().equals(saved.accountId())
                    && binding.profileKey().equals(saved.profileKey())
                    && binding.mindId().equals(saved.mindId()));
            if (!claimed) MoonStation14.LOGGER.warn("[lifecycle death] Offline corpse death was not durably claimed; recovery required account={}", binding.accountId());
        } catch (IOException | RuntimeException failure) {
            MoonStation14.LOGGER.error("[lifecycle death] Offline corpse death persistence failed; no ghost was declared account={}", binding.accountId(), failure);
        }
    }

    /** Pure gate seam shared by focused tests; rejected death evidence never initiates durable work. */
    static boolean callbackEligible(boolean enabled, boolean conflict, boolean confirmed, boolean serverThread,
                                    boolean exactWorldBody, boolean dead) {
        return enabled && !conflict && confirmed && serverThread && exactWorldBody && dead;
    }
}
