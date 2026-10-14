package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.medical_observer.BoundObserverAuthority;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.TargetEligibility;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** Binds medical observer requests to a durable lifecycle DEAD_CLAIM. */
final class LifecycleMedicalObserverBridge implements BoundObserverAuthority {
    private final MinecraftServer server;
    private final LifecycleServerContext context;
    private final UUID account;
    private final String profile;
    private final UUID mind;
    private final UUID corpse;
    private final long generation;

    private LifecycleMedicalObserverBridge(MinecraftServer server, LifecycleServerContext context,
            SavedLifecycleProfile saved) {
        this.server = server; this.context = context; this.account = saved.accountId();
        this.profile = saved.profileKey(); this.mind = saved.mindId(); this.corpse = saved.bodyId();
        this.generation = saved.connectionGeneration();
    }

    static boolean beginConnectedCorpse(MinecraftServer server, ServerPlayer owner,
            net.minecraft.world.entity.Mob body, long deathEpoch, UUID account, String profile, com.juicyslew.moonstation14.ms14.player_body_control.MindId mind,
            MobHarnessId bodyId) {
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null || owner == null || body == null) {
            MoonStation14.LOGGER.info("[lifecycle observer] bound CORPSE bridge denied owner={} body={} epoch={} state={} confirmedDeath={} reason={}",
                    owner == null ? null : owner.getUUID(), body == null ? bodyId : body.getUUID(),
                    deathEpoch, medicalState(body),
                    deathConfirmed(body), context == null ? "lifecycle-context-missing"
                            : owner == null ? "owner-missing" : "body-missing");
            return false;
        }
        try {
            var state = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
            boolean confirmedDeath = deathConfirmed(body);
            if (!confirmedDeath || state == null
                    || state.data().state() != MobStateComponent.State.DEAD) {
                MoonStation14.LOGGER.info("[lifecycle observer] bound CORPSE bridge denied owner={} body={} epoch={} authorityGeneration={} state={} nativeHealth={}/{} confirmedDeath={} reason={}",
                        owner.getUUID(), body.getUUID(), deathEpoch, deathEpoch, state == null ? null : state.data().state(),
                        body.getHealth(), body.getMaxHealth(), deathConfirmed(body),
                        !deathConfirmed(body) ? "native-death-unconfirmed"
                                : state == null ? "medical-state-missing" : "medical-state-not-dead");
                return false;
            }
            SavedLifecycleProfile saved = context.currentDeadClaim(account).orElse(null);
            if (saved == null || !saved.profileKey().equals(profile) || !saved.mindId().equals(mind.value())
                    || !saved.bodyId().equals(bodyId.value())) {
                MoonStation14.LOGGER.info("[lifecycle observer] bound CORPSE bridge denied owner={} body={} epoch={} authorityGeneration={} state={} confirmedDeath={} reason=dead-claim-mismatch",
                        owner.getUUID(), body.getUUID(), deathEpoch, deathEpoch,
                        state.data().state(), deathConfirmed(body));
                return false;
            }
            var bridge = new LifecycleMedicalObserverBridge(server, context, saved);
            boolean admitted = ObserverSystem.beginBoundCorpse(owner, body, saved.connectionGeneration(), bridge);
            MoonStation14.LOGGER.info("[lifecycle observer] bound CORPSE bridge result owner={} body={} epoch={} authorityGeneration={} state={} confirmedDeath={} admitted={}",
                    owner.getUUID(), body.getUUID(), deathEpoch, saved.connectionGeneration(),
                    state.data().state(), deathConfirmed(body), admitted);
            return admitted;
        } catch (Exception failure) {
            MoonStation14.LOGGER.warn("[lifecycle observer] bound CORPSE bridge failed owner={} body={} authorityGeneration={} state={} confirmedDeath={} cause={}",
                    owner.getUUID(), body.getUUID(), deathEpoch,
                    medicalState(body), deathConfirmed(body), failure.toString());
            return false;
        }
    }

    private static MobStateComponent.State medicalState(net.minecraft.world.entity.Mob body) {
        var state = body == null ? null : body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        return state == null ? null : state.data().state();
    }

    private static boolean deathConfirmed(net.minecraft.world.entity.Mob body) {
        if (body instanceof PlayerCharacterHarnessEntity human) return human.hasConfirmedDeath();
        return medicalState(body) == MobStateComponent.State.DEAD && (body.isDeadOrDying() || body.getHealth() <= 0);
    }

    @Override public boolean valid(ServerPlayer owner, LivingEntity body, ObserverSession session) {
        if (owner == null || body == null || session == null || !server.isSameThread()
                || !account.equals(owner.getUUID())
                || !corpse.equals(body.getUUID()) || !session.ownerId().equals(account)
                || !session.corpseBodyId().equals(corpse) || session.epoch() != generation
                || !(body instanceof net.minecraft.world.entity.Mob character) || body.isRemoved()
                || !(body.level() instanceof ServerLevel level) || level.getServer() != server
                || level.getEntity(corpse) != body) return false;
        var binding = PlayerCharacterHarnessBinder.binding(character);
        if (binding == null || !binding.accountId().equals(account) || !binding.profileKey().equals(profile)
                || !binding.mindId().equals(mind)) return false;
        var memory = context.lifecycle().profile(account).orElse(null);
        var corpseHarness = context.ownership().registeredHarness(new MobHarnessId(corpse)).orElse(null);
        if (corpseHarness == null || corpseHarness.kind() != MobHarnessKind.CHARACTER) return false;
        if (session.mode() == ObserverSession.Mode.CORPSE) {
            if (session.epoch() != generation || memory == null || memory.active()
                    || memory.state() != PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM
                    || memory.connectionGeneration() != generation || !memory.profileId().equals(profile)
                     || !memory.mindId().value().equals(mind) || !memory.bodyId().value().equals(corpse)) return false;
            try {
                SavedLifecycleProfile current = context.currentDeadClaim(account).orElse(null);
                return current != null && current.state() == SavedLifecycleProfile.State.DEAD_CLAIM
                        && current.connectionGeneration() == generation && current.profileKey().equals(profile)
                        && current.mindId().equals(mind) && current.bodyId().equals(corpse);
            } catch (Exception failure) { return false; }
        }
        if (session.mode() != ObserverSession.Mode.GHOST || session.epoch() != generation
                || session.authorityGeneration() <= generation || session.ghostId() == null || memory == null
                || !memory.active() || memory.state() != PlayerLifecycleRegistry.LifecycleState.GHOST
                || memory.connectionGeneration() != session.authorityGeneration() || !memory.profileId().equals(profile)
                || !memory.mindId().value().equals(mind)
                || context.ownership().registeredHarness(new MobHarnessId(session.ghostId()))
                    .filter(h -> h.kind() == MobHarnessKind.GHOST).isEmpty()
                || !(body.level() instanceof ServerLevel ghostLevel)
                || ghostLevel.getEntity(session.ghostId()) == null
                || !ghostLevel.getEntity(session.ghostId()).getUUID().equals(session.ghostId())) return false;
        if (!activeGhostBodyMatches(memory.bodyId().value(), session.ghostId())) {
            MoonStation14.LOGGER.warn("[lifecycle observer] GHOST authority rejected owner={} corpse={} ghost={} sessionEpoch={} authorityGeneration={} lifecycleState={} activeBody={} expectedGhost={} durableDeadClaimBody={}",
                    account, corpse, session.ghostId(), session.epoch(), session.authorityGeneration(),
                    memory.state(), memory.bodyId().value(), session.ghostId(), corpse);
            return false;
        }
        try {
            SavedLifecycleProfile current = context.currentAccountProfile(account).orElse(null);
            return current != null && current.state() == SavedLifecycleProfile.State.DEAD_CLAIM
                    && current.connectionGeneration() == session.authorityGeneration() && current.profileKey().equals(profile)
                    && current.mindId().equals(mind) && current.bodyId().equals(corpse);
        } catch (Exception failure) { return false; }
    }

    static boolean activeGhostBodyMatches(UUID activeBodyId, UUID sessionGhostId) {
        return activeBodyId != null && sessionGhostId != null && activeBodyId.equals(sessionGhostId);
    }

    @Override public BoundObserverAuthority.GhostStartResult startGhost(ServerPlayer owner, LivingEntity body, long epoch,
            com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent movement) {
        if (epoch != generation || !valid(owner, body, new ObserverSession(account, corpse, null,
                epoch, ObserverSession.Mode.CORPSE, false, this, 0))) return null;
        if (movement == null) {
            MoonStation14.LOGGER.warn("[lifecycle observer] GHOST start denied owner={} corpse={} generation={} reason=observer-movement-unresolved",
                    account, corpse, generation);
            return null;
        }
        LifecycleDeadClaimGhostStager.Result result = LifecycleDeadClaimGhostStager.stageConnectedDeath(owner, server,
                ghost -> ObserverSystem.beforeBoundGhostAdmission(owner, ghost));
        if (result.outcome() != LifecycleDeadClaimGhostStager.Outcome.PREPARED) {
            MoonStation14.LOGGER.warn("[lifecycle observer] GHOST start denied owner={} corpse={} generation={} reason=staging-failed detail={}",
                    account, corpse, generation, result.diagnostic());
            return null;
        }
        LifecycleDeadClaimGhostStager.PreparedGhost prepared = result.prepared();
        if (!corpse.equals(prepared.corpseUUID()) || !mind.equals(prepared.mindUUID())
                || prepared.generation() <= generation
                || !prepared.ghost().isAlive() || prepared.ghost().isRemoved()
                || !(prepared.ghost().level() instanceof ServerLevel level)
                || level.getServer() != server || level.getEntity(prepared.ghost().getUUID()) != prepared.ghost()
                || context.ownership().registeredHarness(new MobHarnessId(prepared.ghost().getUUID()))
                    .filter(h -> h.kind() == MobHarnessKind.GHOST).isEmpty()) {
            LifecycleDeadClaimGhostStager.returnPreparedToDeadClaim(context, prepared);
            return null;
        }
        var started = LifecycleGhostSessionControl.beginPrepared(owner, prepared.ghost(), context.lifecycle(),
                prepared.generation(), new MobHarnessId(corpse), movement);
        if (started != LifecycleGhostSessionControl.StartResult.PREPARED) {
            MoonStation14.LOGGER.warn("[lifecycle observer] GHOST start rolled back owner={} corpse={} ghost={} priorGeneration={} stagedGeneration={} reason=session-start-{}",
                    account, corpse, prepared.ghost().getUUID(), generation, prepared.generation(), started);
            LifecycleDeadClaimGhostStager.returnPreparedToDeadClaim(context, prepared);
            return null;
        }
        return new BoundObserverAuthority.GhostStartResult(prepared.ghost(), prepared.generation());
    }

    @Override public boolean returnToBody(ServerPlayer owner, LivingEntity body, ObserverSession session) {
        if (owner == null || body == null || session == null || !server.isSameThread()
                || !account.equals(owner.getUUID()) || !corpse.equals(body.getUUID())
                || (session.mode() != ObserverSession.Mode.GHOST && session.mode() != ObserverSession.Mode.CORPSE)
                || (session.mode() == ObserverSession.Mode.GHOST && !session.returnAvailable())
                || session.authorityGeneration() <= 0
                || !valid(owner, body, session)
                || !(body instanceof PlayerCharacterHarnessEntity character)
                || body.isRemoved() || !body.isAlive() || !character.isNoAi() || character.hasConfirmedDeath()
                || !(body.level() instanceof ServerLevel level) || level.getServer() != server
                || level.getEntity(corpse) != body
                || LifecycleCharacterSessionControl.hasSession(owner)
                || com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl.ownsDebugSession(owner))
            return false;
        var state = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        if (state == null || (state.data().state() != MobStateComponent.State.ALIVE
                && state.data().state() != MobStateComponent.State.CRITICAL)) return false;
        var binding = character.playerCharacterBinding();
        if (binding == null || !binding.accountId().equals(account) || !binding.profileKey().equals(profile)
                || !binding.mindId().equals(mind)) return false;
        try {
            SavedLifecycleProfile current = context.currentAccountProfile(account).orElse(null);
            if (current == null || !(current.state() == SavedLifecycleProfile.State.DEAD_CLAIM
                    || current.state() == SavedLifecycleProfile.State.GHOST)
                    || !current.profileKey().equals(profile) || !current.mindId().equals(mind)
                    || current.connectionGeneration() != session.authorityGeneration()) return false;
            MobHarnessId bodyId = new MobHarnessId(corpse);
            MobHarness exact = context.ownership().registeredHarness(bodyId).orElse(null);
            TargetEligibility eligible = target -> target.equals(exact) && target.id().equals(bodyId)
                    && target.kind() == MobHarnessKind.CHARACTER && body.isAlive() && !body.isRemoved()
                    && body.level() == level && level.getEntity(corpse) == body && character.isNoAi()
                    && !character.hasConfirmedDeath()
                    && (state.data().state() == MobStateComponent.State.ALIVE
                        || state.data().state() == MobStateComponent.State.CRITICAL);
            if (exact == null || !eligible.isEligible(exact)) return false;
            var active = context.returnRevivedCorpse(current, session.authorityGeneration(), eligible).orElse(null);
            if (active == null || active.connectionGeneration() <= session.authorityGeneration()) return false;
            if (session.mode() == ObserverSession.Mode.GHOST
                    && !LifecycleGhostSessionControl.teardownAfterRevive(owner,
                            new MobHarnessId(session.ghostId()), session.authorityGeneration())) return false;
            var started = LifecycleCharacterSessionControl.beginPrepared(owner, character,
                    context.lifecycle(), active.connectionGeneration());
            if (started == LifecycleCharacterSessionControl.StartResult.PREPARED) return true;
            context.lifecycle().suspendActiveSessionForRecovery(account,
                    new com.juicyslew.moonstation14.ms14.player_body_control.MindId(mind), bodyId,
                    active.connectionGeneration());
            return false;
        } catch (Exception failure) {
            return false;
        }
    }
}

