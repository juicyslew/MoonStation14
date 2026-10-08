package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.ExaminerComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Resolves exactly one committed control role; camera/carrier identity is never accepted as actor authority. */
public final class ExamineAuthority {
    public record Actor(Entity entity, UUID uuid, long epoch, ExaminerComponent examiner,
                        ExaminationPolicy.State state, boolean blind, double blurMagnitude,
                        boolean ghost, boolean attemptAllowed) { }
    private ExamineAuthority() { }

    public static Actor resolve(ServerPlayer player, ExamineRequest request) {
        if (player == null || request == null || !(player.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || player.isRemoved()
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return null;
        List<Actor> matches = new ArrayList<>(2);
        var character = LifecycleCharacterSessionControl.committedActorSnapshot(player).orElse(null);
        if (character != null) {
            Actor actor = fromEntity(character.body(), character.epoch(), false);
            if (actor != null) matches.add(actor);
        }
        var lifecycleGhost = LifecycleGhostSessionControl.committedActorSnapshot(player).orElse(null);
        if (lifecycleGhost != null) {
            Actor actor = fromEntity(lifecycleGhost.ghost(), lifecycleGhost.epoch(), true, observerExaminer(level));
            if (actor != null) matches.add(actor);
        }
        var observer = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
        if (observer != null && observer.epoch() == request.epoch()) {
            Entity corpseEntity = level.getEntity(observer.corpseBodyId());
            boolean observerValid = false;
            if (observer.boundAuthority() != null && corpseEntity instanceof LivingEntity corpse) {
                observerValid = observer.boundAuthority().valid(player, corpse, observer);
            } else if (observer.boundAuthority() == null && observer.corpseBodyId().equals(player.getUUID())) {
                corpseEntity = player;
                var status = player.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get());
                observerValid = status != null && status.data().state() == MobStateComponent.State.DEAD;
            }
            Entity observerActor = observer.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.GHOST
                    ? level.getEntity(observer.ghostId()) : corpseEntity;
            if (observerValid && observerActor instanceof LivingEntity living && observerActor.level() == level
                    && !observerActor.isRemoved()) {
                CharacterData corpseProfile = observer.mode()
                        == com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.CORPSE
                        ? CharacterIdentitySystem.resolveForActor(living).orElse(null) : null;
                ExaminerComponent ghostProfile = observer.mode()
                        == com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.GHOST
                        ? observerExaminer(level) : null;
                ObserverRole role = observerRole(observer.mode(), corpseProfile, ghostProfile);
                if (role != null) {
                    Actor actor = fromEntity(observerActor, observer.epoch(),
                            role.ghost(), role.examiner());
                    if (actor != null) matches.add(actor);
                }
            }
        }
        var debug = com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl
                .committedControlSnapshot(player);
        if (debug.committed()) {
            var active = com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl
                    .activeHarness(player).orElse(null);
            if (active == null || active.entity() != debug.owned() || active.epoch() < 0) return null;
            boolean ghost = active.kind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.GHOST;
            Actor actor = fromEntity(active.entity(), active.epoch(), ghost, ghost ? observerExaminer(level) : null);
            if (actor != null) matches.add(actor);
        }
        if (matches.size() != 1) return null;
        Actor selected = matches.getFirst();
        return selected.uuid().equals(request.actorUuid()) && selected.epoch() == request.epoch()
                && selected.entity().level() == level && !selected.entity().isRemoved() ? selected : null;
    }

    private static Actor fromEntity(Entity entity, long epoch, boolean ghost) {
        return fromEntity(entity, epoch, ghost, null);
    }

    private static ExaminerComponent observerExaminer(ServerLevel level) {
        var profile = ModCharacters.catalog(level).get(ResourceLocation.fromNamespaceAndPath("moonstation14", "observer"));
        return profile == null ? null : profile.component(ExaminerComponent.class).orElse(null);
    }

    /** Maps observer session mode to its resolved body/observer examiner profile; used by the live authority path. */
    static ObserverRole observerRole(com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode mode,
                                     CharacterData corpseProfile, ExaminerComponent observerProfile) {
        if (mode == null) return null;
        if (mode == com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.CORPSE) {
            ExaminerComponent bodyExaminer = corpseProfile == null ? null
                    : corpseProfile.component(ExaminerComponent.class).orElse(null);
            return bodyExaminer == null ? null : new ObserverRole(bodyExaminer, false);
        }
        return observerProfile == null ? null : new ObserverRole(observerProfile, true);
    }

    record ObserverRole(ExaminerComponent examiner, boolean ghost) { }

    static Actor fromEntity(Entity entity, long epoch, boolean ghost, ExaminerComponent forcedProfile) {
        if (entity == null || entity.isRemoved() || epoch < 0) return null;
        var data = entity instanceof net.minecraft.world.entity.LivingEntity living
                ? CharacterIdentitySystem.resolveForActor(living).orElse(null) : null;
        ExaminerComponent profile = forcedProfile != null ? forcedProfile
                : data == null ? null : data.component(ExaminerComponent.class).orElse(null);
        if (profile == null) return null;
        MobStateComponent.State state = MobStateComponent.State.ALIVE;
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            var attachment = living.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get());
            state = attachment == null
                    ? com.juicyslew.moonstation14.ms14.mobs.MobStateData.DEFAULT.state()
                    : attachment.data().state();
        }
        ExaminationPolicy.State policyState = switch (state) {
            case ALIVE -> ExaminationPolicy.State.ALIVE;
            case CRITICAL -> ExaminationPolicy.State.CRITICAL;
            case DEAD -> ExaminationPolicy.State.DEAD;
        };
        return new Actor(entity, entity.getUUID(), epoch,
                profile, policyState, false, 0, ghost, true);
    }
}
