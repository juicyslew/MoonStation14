package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Wall-only, server-authoritative installation from the controlled character's active body hand. */
public final class FunctionalAirAlarmPlacement {
    private FunctionalAirAlarmPlacement() { }

    public enum Result { INSTALLED, DENIED, RECOVERY_REQUIRED }

    /** Geometry-only wall admission; the caller must first verify both chunks are FULL-loaded. */
    public static BlockState mountedState(LevelReader level, BlockPos clicked, Direction face) {
        if (face == null) return null;
        BlockState host = level.getBlockState(clicked);
        BlockPos destination = clicked.relative(face);
        BlockState placement = ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(AirAlarmBlock.FACING, face);
        return host.isFaceSturdy(level, clicked, face) && placement.canSurvive(level, destination) ? placement : null;
    }

    /** Claim/protection hook; cancel to deny installation (independent of mayInteract). */
    public static final class PermissionEvent extends Event implements ICancellableEvent {
        private final ServerPlayer controller;
        private final LivingEntity body;
        private final ItemStack heldItem;
        private final BlockPos clicked;
        private final Direction face;
        private final BlockPos destination;

        public PermissionEvent(ServerPlayer controller, LivingEntity body, ItemStack heldItem,
                               BlockPos clicked, Direction face, BlockPos destination) {
            this.controller = controller;
            this.body = body;
            this.heldItem = heldItem.copy();
            this.clicked = clicked.immutable();
            this.face = face;
            this.destination = destination.immutable();
        }
        public ServerPlayer controller() { return controller; }
        public LivingEntity body() { return body; }
        public ItemStack heldItem() { return heldItem.copy(); }
        public BlockPos clicked() { return clicked; }
        public Direction face() { return face; }
        public BlockPos destination() { return destination; }
    }

    public static Result place(ServerPlayer actor, BodyBlockUseRequest request,
                               BodyHandRequestService.BlockUseAdmission admission) {
        var authority = admission.authority();
        var hands = admission.hands();
        var hit = admission.hit();
        var body = authority.body();
        if (!(body.level() instanceof ServerLevel level) || !HandActorAuthority.revalidate(actor, authority)
                ) return Result.DENIED;
        ItemStack held = hands.stackCopy(request.handId()).orElse(null);
        if (!alarm(held) || !request.handId().equals(hands.activeHand())) return Result.DENIED;
        BlockPos clicked = hit.pos();
        long x = (long) clicked.getX() + hit.face().getStepX();
        long y = (long) clicked.getY() + hit.face().getStepY();
        long z = (long) clicked.getZ() + hit.face().getStepZ();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE)
            return Result.DENIED;
        BlockPos destination = new BlockPos((int) x, (int)y, (int) z);
        if (!positionsAllowed(level, actor, clicked, destination)) return Result.DENIED;
        BlockState host = level.getBlockState(clicked), original = level.getBlockState(destination);
        BlockEntity hostEntity = level.getBlockEntity(clicked), originalEntity = level.getBlockEntity(destination);
        BlockState placement = mountedState(level, clicked, hit.face());
        var alarmData = PrototypeRuntime.serverAirAlarms().get(ModAirAlarms.AIR_ALARM);
        if (!original.isAir() || !level.getFluidState(destination).isEmpty() || originalEntity != null
                || placement == null
                || alarmData == null || (hit.face().getAxis().isVertical() && !alarmData.wallMount().allowVerticalMounts())
                || !prototypeReady() || !placement.getCollisionShape(level, destination, CollisionContext.of(body)).isEmpty()
                || !handsValid(actor, authority, hands, request, held)) return Result.DENIED;

        var event = new PermissionEvent(actor, body, held, clicked, hit.face(), destination);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled() || !positionsAllowed(level, actor, clicked, destination)
                || !handsValid(actor, authority, hands, request, held)
                || level.getBlockState(clicked) != host || level.getBlockEntity(clicked) != hostEntity
                || level.getBlockState(destination) != original || level.getBlockEntity(destination) != originalEntity
                || !level.getFluidState(destination).isEmpty() || mountedState(level, clicked, hit.face()) != placement
                || !prototypeReady()
                || !placement.getCollisionShape(level, destination, CollisionContext.of(body)).isEmpty())
            return Result.DENIED;

        try {
            if (!level.setBlock(destination, placement, 3))
                return unchanged(level, destination, original, originalEntity) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        } catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        BlockEntity created = level.getBlockEntity(destination);
        if (!(created instanceof AirAlarmBlockEntity alarmEntity)
                || !level.getBlockState(destination).is(ModBlocks.AIR_ALARM.get())
                || alarmEntity.prototype() == null
                || alarmEntity.prototype() != PrototypeRuntime.serverAirAlarms().get(ModAirAlarms.AIR_ALARM)
                || !positionsAllowed(level, actor, clicked, destination)
                || level.getBlockState(clicked) != host || level.getBlockEntity(clicked) != hostEntity
                || !level.getBlockState(destination).canSurvive(level, destination))
            return rollback(level, destination, original, originalEntity, created);

        var consumed = BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, authority, request.handId(),
                request.expectedToken(), request.expectedRevision(), held);
        if (consumed == BodyHandItemTransfer.Result.SUCCESS) {
            BodyHandStateService.requireFreshQuery(actor);
            return Result.INSTALLED;
        }
        if (consumed == BodyHandItemTransfer.Result.RECOVERY_REQUIRED) return Result.RECOVERY_REQUIRED;
        return rollback(level, destination, original, originalEntity, created);
    }

    private static Result rollback(ServerLevel level, BlockPos destination, BlockState original,
                                   BlockEntity originalEntity, BlockEntity created) {
        if (level.getBlockEntity(destination) != created || !level.getBlockState(destination).is(ModBlocks.AIR_ALARM.get()))
            return Result.RECOVERY_REQUIRED;
        try {
            return level.setBlock(destination, original, 3) && unchanged(level, destination, original, originalEntity)
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        } catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
    }

    private static boolean unchanged(ServerLevel level, BlockPos pos, BlockState state, BlockEntity entity) {
        return level.getBlockState(pos) == state && level.getBlockEntity(pos) == entity;
    }

    private static boolean prototypeReady() {
        var data = PrototypeRuntime.serverAirAlarms().get(ModAirAlarms.AIR_ALARM);
        if (data == null) return false;
        try {
            data.validate(); // Resolved inherited anchored/non-colliding wallmount and machine power defaults.
            return data.wallMount().anchored() && data.wallMount().nonColliding();
        } catch (RuntimeException invalid) { return false; }
    }

    private static boolean alarm(ItemStack item) { return item != null && !item.isEmpty() && item.is(ModItems.AIR_ALARM.get()); }

    private static boolean handsValid(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands hands,
                                      BodyBlockUseRequest request, ItemStack expected) {
        if (!HandActorAuthority.revalidate(actor, authority)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(authority.body()) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand())
                || hands.token(request.handId()).filter(request.expectedToken()::equals).isEmpty()) return false;
        ItemStack actual = hands.stackCopy(request.handId()).orElse(null);
        return alarm(actual) && actual.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static boolean positionsAllowed(ServerLevel level, ServerPlayer actor, BlockPos clicked, BlockPos destination) {
        return level.isInWorldBounds(clicked) && level.isInWorldBounds(destination)
                && !level.isOutsideBuildHeight(clicked) && !level.isOutsideBuildHeight(destination)
                && level.getWorldBorder().isWithinBounds(clicked) && level.getWorldBorder().isWithinBounds(destination)
                && full(level, clicked) && full(level, destination)
                && level.mayInteract(actor, clicked) && level.mayInteract(actor, destination);
    }

    private static boolean full(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk;
    }
}

