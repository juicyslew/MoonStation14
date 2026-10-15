package com.juicyslew.moonstation14.ms14.construction.tool;

import com.juicyslew.moonstation14.ms14.disposal.ui.DisposalBuildMenuService;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;

/** Opens the disposal editor with a screwdriver; orientation is confirmed in the editor. */
public final class BodyDisposalTubeJunctionUse {
    private BodyDisposalTubeJunctionUse() { }

    /** Retained for the common bootstrap; delayed rotation registration belongs to the rotation service. */
    public static void register(IEventBus bus) {
        BodyDisposalTubeRotationUse.register(bus);
    }

    /** Compatibility entry point for the already admitted screwdriver primary-use route. */
    public static boolean begin(ServerPlayer actor, BodyBlockUseRequest request,
                                BodyHandRequestService.BlockUseAdmission admission) {
        if (actor == null || request == null || admission == null
                || request.intent() != BodyBlockUseRequest.Intent.USE
                || !request.handId().equals(admission.hands().activeHand())
                || request.expectedRevision() != admission.hands().revision()
                || !request.target().equals(admission.hit().pos())
                || request.face() != admission.hit().face()) return false;
        var body = admission.authority().body();
        if (!(body.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !DisposalTubeWorldData.existing(level)
                .map(data -> data.lookup(level, request.target()).kind() == DisposalTubeWorldData.LookupKind.PRESENT)
                .orElse(false)) return false;

        var editRequest = new BodyBlockUseRequest(request.epoch(), request.sequence(), request.expectedRevision(),
                request.handId(), request.expectedToken(), request.target(), request.face(), request.sneaking(),
                BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
        return DisposalBuildMenuService.openEdit(actor, editRequest, admission);
    }
}

