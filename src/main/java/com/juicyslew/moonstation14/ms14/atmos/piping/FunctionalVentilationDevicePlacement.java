package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.NeoForge;

/** Server-authoritative placement of a typed ventilation device from an active body hand. */
public final class FunctionalVentilationDevicePlacement {
    private FunctionalVentilationDevicePlacement() { }
    public enum Result { INSTALLED, DENIED, RECOVERY_REQUIRED }

    private record TypedDevice(VentilationDeviceConfig.Kind kind, net.minecraft.world.level.block.Block block) { }

    private static TypedDevice typed(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        if (item.is(ModItems.GAS_VENT.get())) return new TypedDevice(VentilationDeviceConfig.Kind.VENT, ModBlocks.GAS_VENT.get());
        if (item.is(ModItems.GAS_SCRUBBER.get())) return new TypedDevice(VentilationDeviceConfig.Kind.SCRUBBER, ModBlocks.GAS_SCRUBBER.get());
        return null;
    }

    public static Result place(ServerPlayer actor, BodyBlockUseRequest request,
                               BodyHandRequestService.BlockUseAdmission admission) {
        var authority = admission.authority();
        var hands = admission.hands();
        var hit = admission.hit();
        var body = authority.body();
        if (!(body.level() instanceof ServerLevel level) || !HandActorAuthority.revalidate(actor, authority)) return Result.DENIED;
        ItemStack held = hands.stackCopy(request.handId()).orElse(null);
        TypedDevice device = typed(held);
        if (device == null || !request.handId().equals(hands.activeHand())) return Result.DENIED;
        BlockPos clicked = hit.pos();
        long x = (long) clicked.getX() + hit.face().getStepX(), y = (long) clicked.getY() + hit.face().getStepY(), z = (long) clicked.getZ() + hit.face().getStepZ();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return Result.DENIED;
        BlockPos destination = new BlockPos((int)x, (int)y, (int)z);
        if (!positionsAllowed(level, actor, clicked, destination)) return Result.DENIED;
        BlockState clickedBefore = level.getBlockState(clicked), original = level.getBlockState(destination);
        BlockEntity clickedEntityBefore = level.getBlockEntity(clicked), originalEntity = level.getBlockEntity(destination);
        if (!original.isAir() || !level.getFluidState(destination).isEmpty() || originalEntity != null) return Result.DENIED;
        if (!handsValid(actor, authority, hands, request, held)) return Result.DENIED;
        var event = new BodyVentilationDevicePlacementPermissionEvent(actor, body, held, clicked, hit.face(), destination, device.kind());
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled() || !positionsAllowed(level, actor, clicked, destination)
                || !handsValid(actor, authority, hands, request, held)
                || level.getBlockState(clicked) != clickedBefore || level.getBlockEntity(clicked) != clickedEntityBefore
                || level.getBlockState(destination) != original || level.getBlockEntity(destination) != originalEntity
                || !original.isAir() || !level.getFluidState(destination).isEmpty()) return Result.DENIED;
        var id = device.kind() == VentilationDeviceConfig.Kind.VENT ? ModVentilationDevices.VENT : ModVentilationDevices.SCRUBBER;
        var resolved = ModVentilationDevices.catalog(level).get(id);
        if (resolved == null || !allowsMount(resolved.fixtureGeometry(), hit.face())) return Result.DENIED;
        Direction facing = hit.face();
        BlockState placement = ((VentilationDeviceBlock) device.block()).defaultBlockState().setValue(VentilationDeviceBlock.FACING, facing);
        if (!placement.canSurvive(level, destination)) return Result.DENIED;
        if (BodyPlacementCollision.deviceIntersects(level, body, destination, placement)) return Result.DENIED;
        try { if (!level.setBlock(destination, placement, 3)) return Result.DENIED; }
        catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        BlockEntity created = level.getBlockEntity(destination);
        if (!(created instanceof VentilationDeviceBlockEntity entity) || entity.kind() != device.kind()
                || !level.getBlockState(destination).is(device.block())) return Result.RECOVERY_REQUIRED;
        var consumed = BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, authority, request.handId(),
                request.expectedToken(), request.expectedRevision(), held);
        if (consumed == BodyHandItemTransfer.Result.SUCCESS) {
            BodyHandStateService.requireFreshQuery(actor);
            return Result.INSTALLED;
        }
        if (consumed == BodyHandItemTransfer.Result.RECOVERY_REQUIRED) return Result.RECOVERY_REQUIRED;
        if (level.getBlockEntity(destination) != created || !level.getBlockState(destination).is(device.block()))
            return Result.RECOVERY_REQUIRED;
        try {
            if (!level.setBlock(destination, original, 3) || level.getBlockEntity(destination) != originalEntity
                    || level.getBlockState(destination) != original) return Result.RECOVERY_REQUIRED;
        } catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        return Result.DENIED;
    }

    /** Uses the resolved prototype capability for the same axis decision made during live placement. */
    public static boolean allowsMount(com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.FixtureGeometry geometry, Direction face) {
        return geometry != null && face != null && (!face.getAxis().isVertical() || geometry.allowVerticalMounts());
    }

    private static boolean handsValid(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands, BodyBlockUseRequest request, ItemStack expected) {
        if (!HandActorAuthority.revalidate(actor, authority) || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(authority.body()) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand()) || hands.token(request.handId()).filter(request.expectedToken()::equals).isEmpty()) return false;
        ItemStack actual = hands.stackCopy(request.handId()).orElse(null);
        return typed(actual) != null && actual.getCount() == expected.getCount() && ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static boolean positionsAllowed(ServerLevel level, ServerPlayer actor, BlockPos clicked, BlockPos destination) {
        return full(level, clicked) && full(level, destination) && level.getWorldBorder().isWithinBounds(clicked)
                && level.getWorldBorder().isWithinBounds(destination) && level.isInWorldBounds(clicked)
                && level.isInWorldBounds(destination) && !level.isOutsideBuildHeight(clicked)
                && !level.isOutsideBuildHeight(destination) && level.mayInteract(actor, clicked) && level.mayInteract(actor, destination);
    }
    private static boolean full(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk;
    }
}
