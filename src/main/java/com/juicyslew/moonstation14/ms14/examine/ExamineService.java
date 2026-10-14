package com.juicyslew.moonstation14.ms14.examine;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Authoritative, read-only examination query entry point. */
public final class ExamineService {
    public enum Admission { ACCEPTED, NOT_CONNECTED, NO_EXACT_ROLE, PROFILE_MISSING, MOB_STATE_MISSING,
        ACTOR_STAMP_MISMATCH, TARGET_ABSENT, OUT_OF_RANGE, LOS_BLOCKED, ATTEMPT_DENIED, SCENE_INVISIBLE }
    public record QueryResult(ExamineResponse response, Admission admission,
                              ExamineTargetResolver.VisibilityResult visibility,
                              double distance, ExaminationPolicy.State actorState,
                              net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 targetPoint) { }
    public static final Component DENIED_NAME = Component.literal("You can't make out whatever that is.");
    private ExamineService() { }

    public static ExamineResponse query(ServerPlayer player, ExamineRequest request) {
        return queryDiagnostic(player, request).response();
    }

    /** Diagnostic companion to the stable response-only query API. */
    public static QueryResult queryDiagnostic(ServerPlayer player, ExamineRequest request) {
        Component empty = Component.empty();
        if (player == null || request == null || !(player.level() instanceof ServerLevel level))
            return result(denied(request, empty, null), Admission.NOT_CONNECTED);
        ExamineAuthority.Actor authority = ExamineAuthority.resolve(player, request);
        if (authority == null) return result(denied(request, empty, null), Admission.NO_EXACT_ROLE);
        Entity actor = authority.entity();
        if (request.target().kind() == ExamineTarget.Kind.SCENE) {
            if (request.controllerIncarnation() != null)
                return result(denied(request, empty, null), Admission.ACTOR_STAMP_MISMATCH);
            if (!ExamineContributorRegistry.attemptsAllow(actor, request.target(), level))
                return result(denied(request, empty, null), Admission.ATTEMPT_DENIED);
            var profile = com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.catalog(level)
                    .get(request.target().sceneId());
            boolean visible = com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneExamination.visible(
                    level, actor, profile, level.getTimeOfDay(1), authority.state() == ExaminationPolicy.State.CRITICAL);
            if (!visible) return result(denied(request, empty, null), Admission.SCENE_INVISIBLE,
                    new Diagnostic(new ExamineTargetResolver.VisibilityResult(false, null, "scene_invisible"),
                            Double.NaN, authority.state(), actor.getEyePosition(), null));
            ExamineCollector collector = new ExamineCollector(false, Component.empty());
            collector.metadata(profile.metadata());
            var diagnostic = new Diagnostic(new ExamineTargetResolver.VisibilityResult(true, null, "scene_visible"),
                    Double.NaN, authority.state(), actor.getEyePosition(), null);
            return result(acceptedResponse(request, authority, collector, false, null), Admission.ACCEPTED, diagnostic);
        }
        var resolved = ExamineTargetResolver.resolve(level, actor, request.target()).orElse(null);
        if (resolved == null) return result(denied(request, empty, null), Admission.TARGET_ABSENT);
        if (!matchesControllerIncarnation(request.controllerIncarnation(), resolved.controllerIncarnation()))
            return result(denied(request, empty, resolved.controllerIncarnation()), Admission.ACTOR_STAMP_MISMATCH);
        var visibility = ExamineTargetResolver.visibility(level, actor, resolved);
        boolean visible = visibility.visible();
        boolean attemptAllowed = ExamineContributorRegistry.attemptsAllow(actor, request.target(), level);
        var denialData = new Diagnostic(visibility, resolved.distance(), authority.state(), actor.getEyePosition(), resolved.point());
        var policyExaminer = new ExaminationPolicy.Examiner(authority.examiner().skipChecks(),
                authority.examiner().checkInRangeUnOccluded());
        boolean canExamine = ExaminationPolicy.canExamine(policyExaminer, authority.state(), authority.blind(),
                authority.blurMagnitude(), resolved.distance(), true, visible, attemptAllowed);
        if (!canExamine) {
            Admission admission = resolved.distance() > ExaminationPolicy.NORMAL_RANGE
                    ? Admission.OUT_OF_RANGE : !visible ? Admission.LOS_BLOCKED : Admission.ATTEMPT_DENIED;
            return result(denied(request, empty, resolved.controllerIncarnation()), admission, denialData);
        }

        boolean detailsAllowed = ExaminationPolicy.detailsAllowed(policyExaminer, authority.state(),
                authority.ghost(), resolved.distance(), true, visible);
        Component nativeName = resolved.entity() != null ? resolved.entity().getDisplayName()
                : resolved.state().getBlock().getName();
        ExamineCollector collector = new ExamineCollector(detailsAllowed, nativeName);
        ExamineContext context = new ExamineContext(actor, level, request.target(), resolved.entity(),
                resolved.root(), resolved.state(), resolved.blockEntity(), detailsAllowed);
        for (ExamineContributor contributor : ExamineContributorRegistry.contributors()) {
            try { contributor.contribute(context, collector); }
            catch (RuntimeException failure) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.warn("Examine provider failed; skipping its contribution", failure);
            }
        }
        return result(acceptedResponse(request, authority, collector, detailsAllowed,
                resolved.controllerIncarnation()), Admission.ACCEPTED, denialData);
    }

    /** Common accepted-response assembly keeps scene metadata and world contributions on one safe protocol path. */
    private static ExamineResponse acceptedResponse(ExamineRequest request, ExamineAuthority.Actor authority,
                                                    ExamineCollector collector, boolean detailsAllowed,
                                                    UUID controllerIncarnation) {
        List<ExamineEntry> entries = collector.sortedEntries();
        return new ExamineResponse(request.requestId(), request.target(), authority.uuid(), authority.epoch(),
                true, collector.name(), collector.description(), entries, detailsAllowed, controllerIncarnation);
    }

    private static QueryResult result(ExamineResponse response, Admission admission) {
        return new QueryResult(response, admission, null, Double.NaN, null, null, null);
    }

    private record Diagnostic(ExamineTargetResolver.VisibilityResult visibility, double distance,
                              ExaminationPolicy.State state, net.minecraft.world.phys.Vec3 eye,
                              net.minecraft.world.phys.Vec3 targetPoint) { }
    private static QueryResult result(ExamineResponse response, Admission admission, Diagnostic d) {
        return new QueryResult(response, admission, d.visibility(), d.distance(), d.state(), d.eye(), d.targetPoint());
    }

    /** Initial queries may omit a stamp; every stamped refresh must match the currently resolved controller object. */
    static boolean matchesControllerIncarnation(UUID expected, UUID current) {
        return expected == null || Objects.equals(expected, current);
    }

    private static ExamineResponse denied(ExamineRequest request, Component empty, UUID controllerIncarnation) {
        if (request == null) return new ExamineResponse(0, ExamineTarget.block(net.minecraft.core.BlockPos.ZERO),
                new java.util.UUID(0, 0), 0, false, DENIED_NAME, empty, List.of(), false, controllerIncarnation);
        return new ExamineResponse(request.requestId(), request.target(), request.actorUuid(), request.epoch(),
                false, DENIED_NAME, empty, List.of(), false, controllerIncarnation);
    }
}

