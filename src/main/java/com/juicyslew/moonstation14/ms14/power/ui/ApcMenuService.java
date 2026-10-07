package com.juicyslew.moonstation14.ms14.power.ui;

import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.util.UUID;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.server.level.FullChunkStatus;

/** Server-side menu open and request validation. */
public final class ApcMenuService {
    private ApcMenuService() { }

    public static boolean open(ServerPlayer player, BlockPos pos, PowerDeviceBlockEntity device) {
        if (!validLoadedApc(player, pos, device) || !deviceAccess(player, pos)) return false;
        UUID session = UUID.randomUUID();
        MenuProvider provider = new SimpleMenuProvider((id, inventory, owner) ->
                new ApcMenu(id, inventory, pos, session, player.serverLevel(), device),
                Component.translatable("container.moonstation14.apc"));
        player.openMenu(provider, buf -> {
            buf.writeBlockPos(pos);
            buf.writeUUID(session);
        });
        return player.containerMenu instanceof ApcMenu menu && menu.session().equals(session)
                && menu.boundDevice() == device;
    }

    /** Body-hand bridge. Admission is already authenticated/replay-consumed by the caller. */
    public static boolean openForBody(ServerPlayer actor, BodyBlockUseRequest request,
                                      BodyHandRequestService.BlockUseAdmission admission) {
        if (actor == null || request == null || admission == null
                || request.intent() != BodyBlockUseRequest.Intent.ACTIVATE_WORLD) return false;
        var authority = admission.authority();
        if (authority == null || HandActorAuthority.resolve(actor).filter(authority::equals).isEmpty()
                || !HandActorAuthority.revalidate(actor, authority)
                || !(authority.body().level() instanceof net.minecraft.server.level.ServerLevel level)
                || admission.hands() == null
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != admission.hands()
                || !admission.hands().compatible(authority.body())
                || admission.hands().revision() != request.expectedRevision()
                || request.expectedToken() != null || admission.hands().token(request.handId()).isPresent()
                || admission.hands().stackCopy(request.handId()).isPresent()) return false;
        var target = BodyBlockUseTargetProbe.probe(level, authority.body(), request.target(), request.face());
        if (target == null || !target.equals(admission.hit())) return false;
        var pos = target.pos();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                || !(chunk.getBlockEntity(pos) instanceof PowerDeviceBlockEntity device)
                || !(chunk.getBlockState(pos).getBlock() instanceof com.juicyslew.moonstation14.block.custom.PowerDeviceBlock block)
                || block.kind() != com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind.APC
                || !HandActorAuthority.revalidate(actor, authority)) return false;
        UUID session = UUID.randomUUID();
        actor.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new ApcMenu(id, inventory, pos,
                session, level, device, authority, request), Component.translatable("container.moonstation14.apc")),
                buf -> { buf.writeBlockPos(pos); buf.writeUUID(session); });
        return actor.containerMenu instanceof ApcMenu menu && menu.session().equals(session)
                && menu.boundDevice() == device && menu.bodyAuthority() == authority;
    }

    static boolean validBodyContext(ServerPlayer actor, ApcMenu menu) {
        if (actor == null || menu == null || actor.containerMenu != menu
                || !HandActorAuthority.revalidateInMenu(actor, menu.bodyAuthority(), menu)
                || !com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(menu.bodyAuthority().body())
                || com.juicyslew.moonstation14.ms14.containment.DisposalContainedActionPolicy.deniesExternalMutation(actor)
                || !(menu.bodyAuthority().body().level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        var pos = menu.devicePos();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                || chunk.getBlockEntity(pos) != menu.boundDevice()
                || !(chunk.getBlockState(pos).getBlock() instanceof com.juicyslew.moonstation14.block.custom.PowerDeviceBlock block)
                || block.kind() != com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind.APC) return false;
        var request = menu.bodyRequest();
        var fresh = BodyBlockUseTargetProbe.probe(level, menu.bodyAuthority().body(), request.target(), request.face());
        return fresh != null && fresh.pos().equals(pos)
                && HandActorAuthority.revalidateInMenu(actor, menu.bodyAuthority(), menu);
    }

    public static ApcToggleResponse apply(ServerPlayer player, ApcToggleRequest request) {
        if (player.containerMenu.containerId != request.containerId()
                || !(player.containerMenu instanceof ApcMenu menu)
                || !menu.session().equals(request.session()))
            return result(request, false, false, false, 0, 0, false);
        if (!menu.validContext(player)) return result(request, false, false, false, 0, 0, false);
        PowerDeviceBlockEntity device = menu.boundDevice();
        // Body menus are revalidated above, including a fresh target probe against the
        // controlled body's current position. Use that same authenticated body as the
        // reach actor; ordinary vanilla menus retain player/carrier reach semantics.
        var body = menu.bodyAuthority() == null ? null : menu.bodyAuthority().body();
        double carrierDistance = player.distanceToSqr(menu.devicePos().getX() + .5,
                menu.devicePos().getY() + .5, menu.devicePos().getZ() + .5);
        double bodyDistance = body == null ? Double.NaN : body.distanceToSqr(menu.devicePos().getX() + .5,
                menu.devicePos().getY() + .5, menu.devicePos().getZ() + .5);
        InteractionActorContext actorContext = selectInteractionActor(menu.bodyAuthority() != null,
                menu.bodyAuthority() == null || validBodyContext(player, menu),
                body == null || com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body),
                body != null && com.juicyslew.moonstation14.ms14.containment.DisposalContainedActionPolicy
                        .deniesExternalMutation(player), bodyDistance, player.mayBuild(), carrierDistance);
        if (!actorContext.allowed()) return result(request, false, false, false, 0, 0, false);
        double distance = actorContext.distanceSquared();
        ApcBreakerIntentPolicy.Decision decision = ApcBreakerIntentPolicy.decide(
                request.expectedRevision(), menu.currentRevision(), device.breakerClosed(), request.desiredClosed());
        boolean accepted = decision == ApcBreakerIntentPolicy.Decision.NO_CHANGE
                || decision == ApcBreakerIntentPolicy.Decision.TOGGLE && (menu.bodyAuthority() == null
                ? device.toggleBreaker(player, distance) : device.toggleBreakerAuthorized(true, distance));
        return result(request, accepted, true, device.breakerClosed(), menu.currentRevision(),
                ApcMenu.batteryPermille(device.energyJoules()), device.tripLatched());
    }

    /** Selects the authorization/reach facts appropriate to a freshly validated menu actor. */
    static InteractionActorContext selectInteractionActor(boolean bodyMenu, boolean bodyContextValid,
                                                           boolean bodyCanAct, boolean contained,
                                                           double bodyDistanceSquared, boolean normalMayBuild,
                                                           double carrierDistanceSquared) {
        if (bodyMenu) return new InteractionActorContext(bodyDistanceSquared,
                bodyContextValid && bodyCanAct && !contained);
        return new InteractionActorContext(carrierDistanceSquared, normalMayBuild);
    }

    record InteractionActorContext(double distanceSquared, boolean allowed) { }

    private static ApcToggleResponse result(ApcToggleRequest request, boolean accepted, boolean snapshot,
                                            boolean breakerClosed, long revision, int batteryPermille,
                                            boolean tripLatched) {
        return new ApcToggleResponse(request.containerId(), request.session(), request.requestId(), accepted,
                snapshot, breakerClosed, revision, batteryPermille, tripLatched);
    }

    private static boolean validLoadedApc(ServerPlayer player, BlockPos pos, PowerDeviceBlockEntity device) {
        if (!player.serverLevel().hasChunkAt(pos) || player.serverLevel().getBlockEntity(pos) != device) return false;
        return player.serverLevel().getBlockState(pos).getBlock() instanceof
                com.juicyslew.moonstation14.block.custom.PowerDeviceBlock block
                && block.kind() == com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind.APC;
    }

    private static boolean deviceAccess(ServerPlayer player, BlockPos pos) {
        double distance = player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        return com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules.canToggle(
                true, player.mayBuild(), distance);
    }
}
