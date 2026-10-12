package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.juicyslew.moonstation14.ms14.mobs.MobStateChangedEvent;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
/** Durable corpse-death evidence and connected corpse-to-ghost handoff. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class LifecycleCharacterDeathHandler {
    private LifecycleCharacterDeathHandler() { }

    @SubscribeEvent
    public static void roleMobDied(MobStateChangedEvent event) {
        if (event.current() == MobStateComponent.State.DEAD && event.body() instanceof Mob mob
                && !(mob instanceof PlayerCharacterHarnessEntity)) onConfirmedDeath(mob);
    }

    /** Called solely by PlayerCharacterHarnessEntity after vanilla accepts its first server-side death. */
    public static void onConfirmedDeath(Mob body) {
        if (body == null || !(body.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        boolean dead = body instanceof PlayerCharacterHarnessEntity human ? human.hasConfirmedDeath()
                : body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()) != null
                && body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()).data().state() == MobStateComponent.State.DEAD;
        if (server == null || !callbackEligible(dead, server.isSameThread(),
                !level.isClientSide && !body.isRemoved() && body.isAddedToLevel()
                        && level.getEntity(body.getUUID()) == body,
                body.isDeadOrDying() || body.getHealth() <= 0)
                || body instanceof PlayerCharacterHarnessEntity human && human.hasInvalidSavedBinding()) return;
        var binding = PlayerCharacterHarnessBinder.binding(body);
        var identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (binding == null || identity == null || !identity.isBound()
                || !roleMobDeathDecision(dead, binding != null,
                CharacterIdentitySystem.resolveHost(body, level, identity.characterId()).isPresent(),
                level.getEntity(body.getUUID()) == body)) return;

        var snapshot = LifecycleStartupRuntime.snapshot(server).orElse(null);
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (snapshot == null || context == null) return;
        if (LifecycleCharacterSessionControl.hasExactDeathSession(body)) {
            LifecycleCharacterSessionControl.claimConfirmedDeath(body, context);
            return;
        }
        if (!offlineDeathStateEligible(snapshot.state(), context, binding.accountId())) return;
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
            if (saved == null || !saved.dimension().equals(level.dimension().location().toString())
                    || !(body instanceof PlayerCharacterHarnessEntity)
                    && !identity.characterId().toString().equals(saved.appearance().get("prototype"))) return;
            boolean claimed = context.claimOfflineDeath(saved, corpse, candidate -> candidate.equals(corpse)
                    && dead && body.getHealth() <= 0 && !body.isRemoved()
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
    static boolean callbackEligible(boolean confirmed, boolean serverThread,
                                    boolean exactWorldBody, boolean dead) {
        return confirmed && serverThread && exactWorldBody && dead;
    }

    static boolean roleMobDeathDecision(boolean deadState, boolean exactBinding,
                                        boolean resolvedPrototype, boolean exactWorldBody) {
        return deadState && exactBinding && resolvedPrototype && exactWorldBody;
    }

    /** Startup is immutable: fresh enrollment can leave it EMPTY/UNINITIALIZED after an OFFLINE row is persisted. */
    static boolean offlineDeathStateEligible(LifecycleStartupRuntime.State state, LifecycleServerContext context,
                                             java.util.UUID accountId) {
        if (state == null || context == null || !context.hasReservedClaim(accountId)) return false;
        return state == LifecycleStartupRuntime.State.DEFERRED
                || (state == LifecycleStartupRuntime.State.UNINITIALIZED || state == LifecycleStartupRuntime.State.EMPTY)
                && context.wasCreatedOnThisServer(accountId);
    }
}
