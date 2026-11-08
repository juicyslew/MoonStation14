package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyMenuReach;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Authenticated timed pipe-layer adjustment using the shared timed-interaction owner. */
public final class AtmosMachineLayerUse {
    private static final String KIND = "atmos-machine-layer";
    private AtmosMachineLayerUse() { }

    public static int durationTicks(AtmosMachineProfile profile) {
        return profile == null ? 0 : profile.pipeLayers().map(AtmosMachineProfile.PipeLayers::changeDurationTicks).orElse(0);
    }

    /** True if an atmos machine was targeted; invalid tool/unsupported layer attempts are consumed. */
    public static boolean start(ServerPlayer actor, BodyBlockUseRequest request,
                                BodyHandRequestService.BlockUseAdmission admission, ItemStack held) {
        if (request == null || admission == null || held == null
                || !(admission.authority().body().level() instanceof ServerLevel level)) return false;
        BlockPos pos = admission.hit().pos().immutable();
        LevelChunk chunk = loadedFullChunk(level, pos);
        if (chunk == null || !(chunk.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)) return false;
        if (!ToolQualities.has(held, ToolProfile.Quality.SCREWING)) return true;
        AtmosMachineProfile profile = machine.resolvedProfile();
        int duration = durationTicks(profile);
        if (duration < 1 || profile.pipeLayers().map(l -> l.locked() || l.count() < 2).orElse(true)) return true;
        PipeTopology.Layer initialLayer = machine.selectedPipeLayer();
        if (!supportsChange(profile, initialLayer)) return true;

        HandActorAuthority.Snapshot authority = admission.authority();
        LiveHands hands = admission.hands();
        String hand = request.handId(), token = request.expectedToken();
        long handRevision = request.expectedRevision();
        UUID identity = machine.placementIdentity();
        String profileId = machine.profileIdentity();
        boolean valid = valid(actor, authority, hands, hand, token, handRevision, held, level, pos,
                machine, identity, profileId, profile, initialLayer);
        if (!valid) return true;
        TimedInteractionSystem.start(authority.body(), actor, request.sequence(), KIND, identity.toString(),
                duration, true, true,
                () -> valid(actor, authority, hands, hand, token, handRevision, held, level, pos,
                        machine, identity, profileId, profile, initialLayer),
                () -> {
                    if (!valid(actor, authority, hands, hand, token, handRevision, held, level, pos,
                            machine, identity, profileId, profile, initialLayer)) return TimedInteractionSystem.Completion.STOP;
                    PipeTopology.Layer next = nextLayer(profile, initialLayer);
                    if (!machine.changePipeLayer(next)) return TimedInteractionSystem.Completion.DENIED;
                    actor.sendSystemMessage(Component.literal("Pipe layer changed to " + next.name().toLowerCase(java.util.Locale.ROOT) + "."));
                    return TimedInteractionSystem.Completion.STOP;
                }, java.util.Optional.of(pos), () -> { });
        return true;
    }

    public static boolean supportsChange(AtmosMachineProfile profile, PipeTopology.Layer layer) {
        var layers = profile == null ? null : profile.pipeLayers().orElse(null);
        return layers != null && !layers.locked() && layers.count() > 1 && layer.ordinal() < layers.count();
    }

    public static PipeTopology.Layer nextLayer(AtmosMachineProfile profile, PipeTopology.Layer current) {
        if (!supportsChange(profile, current)) return null;
        int count = profile.pipeLayers().orElseThrow().count();
        return PipeTopology.Layer.values()[(current.ordinal() + 1) % count];
    }

    private static boolean valid(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands hands,
                                 String hand, String token, long revision, ItemStack held, ServerLevel level,
                                 BlockPos pos, AtmosMachineBlockEntity expected, UUID identity, String profileId,
                                 AtmosMachineProfile profile, PipeTopology.Layer layer) {
        if (!HandActorAuthority.revalidate(actor, authority) || authority.body().level() != level
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(authority.body()) || hands.revision() != revision
                || !hands.token(hand).filter(token::equals).isPresent()
                || !hands.stackCopy(hand).filter(stack -> ItemStack.isSameItemSameComponents(stack, held)).isPresent()
                || !ToolQualities.has(held, ToolProfile.Quality.SCREWING)) return false;
        LevelChunk chunk = loadedFullChunk(level, pos);
        if (chunk == null || chunk.getBlockEntity(pos) != expected || expected.isRemoved()
                || !java.util.Objects.equals(expected.placementIdentity(), identity)
                || !profileId.equals(expected.profileIdentity()) || expected.resolvedProfile() != profile
                || expected.selectedPipeLayer() != layer || !supportsChange(profile, layer)) return false;
        var blockState = chunk.getBlockState(pos);
        var state = blockState.getShape(chunk, pos);
        if (state.isEmpty()) return false;
        var bounds = state.bounds();
        var point = new net.minecraft.world.phys.Vec3(pos.getX() + (bounds.minX + bounds.maxX) / 2,
                pos.getY() + (bounds.minY + bounds.maxY) / 2, pos.getZ() + (bounds.minZ + bounds.maxZ) / 2);
        return BodyMenuReach.check(level, authority.body(), pos, point) == BodyMenuReach.Result.VALID;
    }

    /** Never asks the level to acquire a chunk: only exact already-FULL targets are eligible. */
    private static LevelChunk loadedFullChunk(ServerLevel level, BlockPos pos) {
        if (pos == null || pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(pos)) return null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL) ? chunk : null;
    }
}
