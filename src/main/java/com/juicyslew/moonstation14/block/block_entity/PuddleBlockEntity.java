package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PuddleBlock;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.ms14.puddle.PuddleData;
import com.juicyslew.moonstation14.ms14.puddle.ModPuddles;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.getBlendedColor;
import static com.juicyslew.moonstation14.util.Helpers.findFirstSurfaceBelow;
import static com.juicyslew.moonstation14.util.MapOperations.getTotal;
import static com.juicyslew.moonstation14.util.NetworkingUtils.*;

public class PuddleBlockEntity extends BlockEntity implements IReagentTrait {
    private static final Logger LOGGER = LoggerFactory.getLogger(PuddleBlockEntity.class);
    private long lastCommittedUnits = -1L;
    private boolean removingEmptyBlock;
    private int deferredEmptyCleanup;
    private boolean deferredFailureBlocksCleanup;
    /** Optional instance-bound resolved profile; absent in normal placed puddles. */
    private PuddleData boundPuddleData;
    // Gonna probably want a generic ContainerBlockEntity.
    static Map<Integer, Direction> puddleOffsets = Map.of(
            0, Direction.NORTH,//List.of(0,1),
            1, Direction.EAST,
            2, Direction.SOUTH,
            3, Direction.WEST
    );

    static final float overflowThreshold = 50f;
    static final float MAX_CAPACITY = PuddleData.DEFAULT_CAPACITY;

    public PuddleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUDDLE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide)
            lastCommittedUnits = getReagentContainer().totalUnits();
    }

    @Override public float getCapacity() {
        PuddleData data = getResolvedPuddleData();
        return data == null ? MAX_CAPACITY : data.solutionContainer().maxVolume();
    }

    /** Current resolved puddle prototype (including inherited metadata and component defaults). */
    public PuddleData getResolvedPuddleData() {
        if (boundPuddleData != null) return boundPuddleData;
        if (level == null) return null;
        return ModPuddles.catalog(level).get(ModPuddles.PUDDLE);
    }

    /** Bind an already-resolved profile to this puddle instance without changing global catalogs. */
    public void bindResolvedPuddleData(PuddleData data) {
        boundPuddleData = Objects.requireNonNull(data, "resolved puddle profile");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        // REQUIRED: Without this, the server returns null and nothing is sent.
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);

        // Use Codec for NBT-based
        saveToTag(tag, this, ModDataAttachments.REAGENT.get());

        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);

        // Unpack the data
        loadFromTag(tag, this, ModDataAttachments.REAGENT.get(), ReagentAttachment.CODEC);

        // Tell the renderer the data is here and it's time to draw
        if (this.level != null && this.level.isClientSide) {
            this.requestModelDataUpdate();
        }
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        // 1. Get the tag from the packet
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            // 2. Load the data using your existing logic
            loadFromTag(tag, this, ModDataAttachments.REAGENT.get(), ReagentAttachment.CODEC);

            // 3. Trigger the rerender
            if (this.level != null && this.level.isClientSide) {
                // This is essential for ModelData and Tints
                this.requestModelDataUpdate();
                // This forces a chunk rebuild (visual redraw)
                this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private ReagentAttachment getReagentContainer(){
        return MS14Provider.getDetached(this, MS14Bridges.REAGENT);
    }

    public void updateFillLevel() {
        ReagentAttachment reagents = getReagentContainer();
        if (reagents.totalUnits() == 0L) {
            removeEmptyBlock();
            return;
        }
        int newLevel = fillLevelForVolume(getTotal(reagents.getMap()));

        BlockState currentState = getBlockState();
        if (currentState.getValue(PuddleBlock.FILL_LEVEL) != newLevel) {
            level.setBlock(worldPosition, currentState.setValue(PuddleBlock.FILL_LEVEL, newLevel), 3);
        }
    }

    static int fillLevelForVolume(float volume) {
        if (!(volume > 0f) || !Float.isFinite(volume)) return 0;

        float percentage = Math.clamp(volume / overflowThreshold, 0f, 1f);
        int fillLevel = (int) Math.floor((percentage * 3) + .1f);
        // Level zero uses the faint empty-puddle texture; positive contents must use a visible splat.
        return Math.max(1, fillLevel);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        // RUNS WHENEVER DATA IS DIRTIED!
        if (this.level != null && !this.level.isClientSide) {
            long committedUnits = getReagentContainer().totalUnits();
            boolean drained = lastCommittedUnits > 0L && committedUnits == 0L;
            lastCommittedUnits = committedUnits;
            if (drained && deferredEmptyCleanup == 0 && !deferredFailureBlocksCleanup) removeEmptyBlock();
            else if (committedUnits > 0L) { deferredFailureBlocksCleanup = false; updateFillLevel(); }
        }
    }

    /** Temporarily keeps an emptied source block alive during a source-first transaction. */
    public void beginDeferredEmptyCleanup() { deferredEmptyCleanup++; }
    public void endDeferredEmptyCleanup(boolean committed) {
        if (deferredEmptyCleanup <= 0) throw new IllegalStateException("unbalanced puddle cleanup deferral");
        if (--deferredEmptyCleanup == 0 && committed && level != null && !level.isClientSide
                && getReagentContainer().totalUnits() == 0L) removeEmptyBlock();
    }

    public void blockDeferredCleanupAfterFailure() { deferredFailureBlocksCleanup = true; }

    public static void tick(Level level, BlockPos pos, BlockState state, PuddleBlockEntity be) {
        // Only run logic on the server
        if (level.isClientSide) return;

        // Empty /setblock puddles are allowed to survive placement so callers can
        // fill them in the same server operation, but never persist past a tick.
        ReagentAttachment contents = be.getReagentContainer();
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(contents.getMap(), level,
                "puddle tick " + pos)) return;
        if (!be.isWithinCapacity(contents, "puddle tick", pos)) return;
        if (contents.totalUnits() == 0L && !be.deferredFailureBlocksCleanup) {
            be.removeEmptyBlock();
            return;
        }

        // The 20-tick heartbeat (using the position offset to prevent lag spikes)
        if (shouldSlowTick(level.getGameTime(), pos)) {
            be.slowTick(level, pos, state, be);
        }
    }

    static boolean shouldSlowTick(long gameTime, BlockPos pos) {
        return Math.floorMod(gameTime + (long) pos.hashCode(), 10L) == 0L;
    }

    private void slowTick(Level level, BlockPos pos, BlockState state, PuddleBlockEntity be) {
        ReagentAttachment srcCont = getReagentContainer();
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(srcCont.getMap(), level,
                "puddle flow source " + pos)) return;
        if (!isWithinCapacity(srcCont, "puddle flow source", pos)) return;
        var srcBefore = MS14Provider.snapshot(srcCont);
        float volume = getTotal(srcCont.getMap());

        if (volume <= overflowThreshold) return;

        // Might want a cap on flow, unclear.
        float flowCapacity = volume - overflowThreshold;

        // 1. Identify valid neighbors and their "pressure"
        record Neighbor(
                PuddleBlockEntity puddle,
                float pressure,
                BlockPos pos,
                boolean crossGrid,
                boolean downward,
                java.util.UUID frameId,
                java.util.UUID ownerFrameId,
                com.juicyslew.moonstation14.ms14.puddle.PuddleOverflowSurfaceResolver.Surface surface
        ){}
        List<Neighbor> neighbors = new ArrayList<>();
        float totalDrop = 0;
        java.util.Set<BlockPos> seenDestinations = new java.util.HashSet<>();
        boolean crossGridAdmissionRejected = false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos edgePos = pos.relative(dir);
            BlockPos targetPos;
            var sourceEdge = level instanceof net.minecraft.server.level.ServerLevel serverLevel
                    ? com.juicyslew.moonstation14.ms14.puddle.PuddleOverflowSurfaceResolver
                    .sourceEdge(serverLevel, pos, dir) : null;
            boolean resolveWorldEdge = sourceEdge != null && sourceEdge.frameOwned()
                    && !sourceEdge.unknown() && !isInsideSourcePlot((net.minecraft.server.level.ServerLevel) level,
                    sourceEdge.frameId(), edgePos);
            if (sourceEdge != null && sourceEdge.frameOwned()) {
                if (sourceEdge.unknown()) continue;
                if (resolveWorldEdge) {
                    targetPos = null;
                } else {
                    FrameFlowTarget local = findFrameFlowTarget((net.minecraft.server.level.ServerLevel) level,
                            sourceEdge.frameId(), edgePos);
                    if (local.target() != null) targetPos = local.target();
                    else if (local.unknown()) continue;
                    else if (local.openEdge()) {
                        targetPos = null;
                        resolveWorldEdge = true;
                    } else continue;
                }
            } else {
                targetPos = findFlowTarget(level, edgePos);
            }
            PuddleBlockEntity targetPuddle = null;
            boolean crossGrid = false;
            boolean downward = false;
            java.util.UUID destinationFrameId = null;
            com.juicyslew.moonstation14.ms14.puddle.PuddleOverflowSurfaceResolver.Surface surface = null;
            if (targetPos == null) {
                if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) continue;
                if (!resolveWorldEdge) continue;
                surface = com.juicyslew.moonstation14.ms14.puddle.PuddleOverflowSurfaceResolver
                        .resolve(serverLevel, sourceEdge);
                if (surface == null || (surface.frameId() == null
                        ? surface.puddlePos().equals(pos) : surface.frameId().equals(sourceEdge.frameId()))) continue;
                targetPos = surface.puddlePos();
                destinationFrameId = surface.frameId();
                downward = surface.worldPoint().y < sourceEdge.worldEdge().y;
                crossGrid = true;
                BlockState destinationState;
                BlockEntity destinationEntity;
                if (destinationFrameId == null) {
                    if (!serverLevel.hasChunkAt(targetPos)) continue;
                    destinationState = serverLevel.getBlockState(targetPos);
                    destinationEntity = serverLevel.getBlockEntity(targetPos);
                } else {
                    var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(serverLevel);
                    var frame = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(serverLevel)
                            .getSubLevel(destinationFrameId);
                    if (!(frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live)
                            || live.isRemoved() || live.getLevel() != serverLevel
                            || container.getSubLevel(destinationFrameId) != live
                            || !com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.revalidate(
                            serverLevel, new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.Hit(
                                    targetPos.below(), Direction.UP, surface.worldPoint(), destinationFrameId))) continue;
                    destinationState = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .knownServerBlock(serverLevel, live, targetPos);
                    destinationEntity = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .knownBlockEntity(serverLevel, live, targetPos);
                    if (destinationState == null) continue;
                }
                if (destinationState == null || (destinationState.canBeReplaced()
                        && !ModBlocks.PUDDLE.get().defaultBlockState().canSurvive(serverLevel, targetPos))) continue;
                if (destinationEntity instanceof PuddleBlockEntity puddle) targetPuddle = puddle;
            } else {
                BlockEntity targetEntity;
                if (crossGrid && destinationFrameId != null && level instanceof net.minecraft.server.level.ServerLevel server) {
                    var frame = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server)
                            .getSubLevel(destinationFrameId);
                    targetEntity = frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live
                            ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .knownBlockEntity(server, live, targetPos) : null;
                } else if (sourceEdge != null && sourceEdge.frameOwned()
                        && level instanceof net.minecraft.server.level.ServerLevel server) {
                    var frame = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server)
                            .getSubLevel(sourceEdge.frameId());
                    targetEntity = frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live
                            ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .knownBlockEntity(server, live, targetPos) : null;
                } else targetEntity = level.getBlockEntity(targetPos);
                if (targetEntity instanceof PuddleBlockEntity puddle) targetPuddle = puddle;
            }
            if (!seenDestinations.add(targetPos)) continue;
            if (targetPuddle != null && (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                    targetPuddle.getReagentContainer().getMap(), level, "puddle flow destination " + targetPos)
                    || !targetPuddle.isWithinCapacity(targetPuddle.getReagentContainer(),
                    "puddle flow destination", targetPos))) {
                if (crossGrid) crossGridAdmissionRejected = true;
                else return;
                continue;
            }
            if (crossGrid && targetPuddle == null) {
                BlockState targetState;
                if (destinationFrameId == null) targetState = level.getBlockState(targetPos);
                else {
                    var server = (net.minecraft.server.level.ServerLevel) level;
                    var frame = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server)
                            .getSubLevel(destinationFrameId);
                    targetState = frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live
                            ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .knownServerBlock(server, live, targetPos) : null;
                }
                if (targetState == null) continue;
                if (!targetState.canBeReplaced()) continue;
            }
            if (targetPuddle == null && !crossGrid) {
                BlockState stateAtTarget;
                if (sourceEdge != null && sourceEdge.frameOwned()
                        && level instanceof net.minecraft.server.level.ServerLevel server) {
                    var frame = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server)
                            .getSubLevel(sourceEdge.frameId());
                    stateAtTarget = frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live
                            ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .knownServerBlock(server, live, targetPos) : null;
                } else stateAtTarget = level.getBlockState(targetPos);
                if (stateAtTarget == null || !stateAtTarget.canBeReplaced()) continue;
                neighbors.add(new Neighbor(null, volume, targetPos, false, false, null,
                        sourceEdge != null && sourceEdge.frameOwned() ? sourceEdge.frameId() : null, null));
                totalDrop += volume;
                continue;
            }
            if (crossGrid && targetPuddle == null) {
                // Empty destination is admitted only after every existing destination has passed validation.
                neighbors.add(new Neighbor(null, volume, targetPos, true, downward, destinationFrameId,
                        destinationFrameId, surface));
                totalDrop += volume;
                continue;
            }
            float neighborEffectiveVol = !crossGrid && pos.getY() > targetPos.getY()
                    ? 0 : getTotal(targetPuddle.getReagentContainer().getMap());
            if (volume >= neighborEffectiveVol) {
                float drop = volume - neighborEffectiveVol;
                neighbors.add(new Neighbor(targetPuddle, drop, targetPos, crossGrid, downward, destinationFrameId,
                        crossGrid ? destinationFrameId : sourceEdge != null && sourceEdge.frameOwned()
                                ? sourceEdge.frameId() : null, crossGrid ? surface : null));
                totalDrop += drop;
            }
        }
        if (crossGridAdmissionRejected) return;
        // Revalidate every resolved cross-grid surface before making any destination block.
        for (int i = neighbors.size() - 1; i >= 0; i--) {
            Neighbor n = neighbors.get(i);
            if (!n.crossGrid()) continue;
            net.minecraft.server.level.ServerLevel server = (net.minecraft.server.level.ServerLevel) level;
            boolean valid;
            if (n.frameId() == null) {
                valid = server.hasChunkAt(n.pos()) && ModBlocks.PUDDLE.get().defaultBlockState()
                        .canSurvive(server, n.pos());
            } else {
                var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server);
                var frame = container.getSubLevel(n.frameId());
                valid = frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live
                        && !live.isRemoved() && live.getLevel() == server && container.getSubLevel(n.frameId()) == live
                        && com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.revalidate(server,
                        new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.Hit(
                                n.surface().puddlePos().below(), Direction.UP, n.surface().worldPoint(), n.frameId()))
                        && ModBlocks.PUDDLE.get().defaultBlockState().canSurvive(server, n.pos());
            }
            if (!valid) {
                if (n.puddle() != null) return;
                neighbors.remove(i);
                totalDrop -= n.pressure();
            }
        }
        // Admit empty local/cross-grid puddles only after all current destinations validate.
        for (int i = 0; i < neighbors.size(); i++) {
            Neighbor n = neighbors.get(i);
            if (n.puddle() != null) continue;
            net.minecraft.server.level.ServerLevel server = (net.minecraft.server.level.ServerLevel) level;
            if (n.frameId() == null) {
                if (n.ownerFrameId() == null) {
                    if (!server.hasChunkAt(n.pos()) || !ModBlocks.PUDDLE.get().defaultBlockState()
                            .canSurvive(server, n.pos())) continue;
                } else {
                    var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server);
                    var owner = container.getSubLevel(n.ownerFrameId());
                    BlockState known = owner instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live
                            ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .knownServerBlock(server, live, n.pos()) : null;
                    if (known == null || !known.canBeReplaced()
                            || !ModBlocks.PUDDLE.get().defaultBlockState().canSurvive(server, n.pos())) continue;
                }
                if (!server.setBlock(n.pos(), ModBlocks.PUDDLE.get().defaultBlockState(), 3)) continue;
            } else {
                var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server);
                var frame = container.getSubLevel(n.frameId());
                if (!(frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live)
                        || live.isRemoved() || live.getLevel() != server || container.getSubLevel(n.frameId()) != live
                        || !com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.revalidate(server,
                        new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.Hit(n.surface().puddlePos().below(),
                                Direction.UP, n.surface().worldPoint(), n.frameId()))
                        || !ModBlocks.PUDDLE.get().defaultBlockState().canSurvive(server, n.pos())) continue;
                if (!server.setBlock(n.pos(), ModBlocks.PUDDLE.get().defaultBlockState(), 3)) continue;
            }
            BlockEntity createdEntity;
            if (n.ownerFrameId() == null) createdEntity = level.getBlockEntity(n.pos());
            else {
                var owner = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server)
                        .getSubLevel(n.ownerFrameId());
                createdEntity = owner instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live
                        ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                        .knownBlockEntity(server, live, n.pos()) : null;
            }
            if (!(createdEntity instanceof PuddleBlockEntity created)) continue;
            neighbors.set(i, new Neighbor(created, n.pressure(), n.pos(), n.crossGrid(), n.downward(), n.frameId(),
                    n.ownerFrameId(), n.surface()));
        }

        // 2. Determine how much total VOLUME can move

        // 3. The "Efficiency" Step: Combined Loop
        for (var neighbor : neighbors) {
            PuddleBlockEntity targetPuddle = neighbor.puddle;
            if (targetPuddle == null) continue;
            if (neighbor.crossGrid()) {
                if (!(level instanceof net.minecraft.server.level.ServerLevel server)
                        || neighbor.surface() == null) continue;
                var surface = neighbor.surface();
                if (neighbor.frameId() == null) {
                    if (!server.hasChunkAt(targetPuddle.getBlockPos())
                            || server.getBlockEntity(targetPuddle.getBlockPos()) != targetPuddle) continue;
                } else {
                    var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server);
                    var frame = container.getSubLevel(neighbor.frameId());
                    if (!(frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live)
                            || live.isRemoved() || live.getLevel() != server
                            || container.getSubLevel(neighbor.frameId()) != live
                            || !com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.revalidate(server,
                            new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.Hit(
                                    surface.puddlePos().below(), Direction.UP, surface.worldPoint(), neighbor.frameId()))
                            || com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .knownBlockEntity(server, live, targetPuddle.getBlockPos()) != targetPuddle) continue;
                }
            }
            float pressure = neighbor.pressure;
            BlockPos targetPos = neighbor.pos;

            // Weight the flow by the drop (Proportionality)
            float netVolumeToMove;
            if (totalDrop > 0){
                float weight = pressure / totalDrop;
                netVolumeToMove = flowCapacity * weight;
            }else{
                netVolumeToMove = 0;
            }

            // OSMOSIS: We always swap at least 10% of our contents,
            // even if netVolumeToMove is 0.

            // Final math: We "push" more than the net volume, then "pull" back the difference
            ReagentAttachment dstCont = targetPuddle.getReagentContainer();
            var dstBefore = MS14Provider.snapshot(dstCont);
            float osmosisAmount = volume * 0.1f;
            if (neighbor.crossGrid()) {
                osmosisAmount = 0f;
            } else if (targetPos.getY() < pos.getY()){
                osmosisAmount = 0f; // Can't "osmos" up a height differential

                // Fun lil' particle. TODO: Not appearing right now.
                if (level.isClientSide) {
                    int color = getBlendedColor(srcCont.getMap(), level);
                    float r = ((color >> 16) & 0xFF) / 255.0f;
                    float g = ((color >> 8) & 0xFF) / 255.0f;
                    float b = (color & 0xFF) / 255.0f;

                    // DustParticleOptions(color_vector, scale)
                    DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.0f);
                    level.addParticle(dust, targetPos.getX() + 0.5, pos.getY(), targetPos.getZ() + 0.5, 0, -1, 0);
                }

            }
            if (neighbor.crossGrid()) transferFlow(srcCont, dstCont, netVolumeToMove, 0f, targetPuddle.getCapacity());
            else performCombinedTransfer(srcCont, dstCont, netVolumeToMove, osmosisAmount);
            dstCont.recursiveReaction(level, targetPuddle.getCapacity());
            MS14Provider.updateIfChanged(targetPuddle, MS14Bridges.REAGENT, dstBefore, dstCont);
        }
        srcCont.recursiveReaction(level, getCapacity());
        MS14Provider.updateIfChanged(this, MS14Bridges.REAGENT, srcBefore, srcCont);
    }

    private void performCombinedTransfer(ReagentAttachment source, ReagentAttachment target, float netVol, float osmosisVol) {
        transferFlow(source, target, netVol, osmosisVol, getCapacity());
    }

    private static boolean isInsideSourcePlot(net.minecraft.server.level.ServerLevel level, java.util.UUID frameId,
                                             BlockPos pos) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        var frame = container.getSubLevel(frameId);
        return frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live && !live.isRemoved()
                && live.getLevel() == level && container.getSubLevel(frameId) == live && live.getPlot() != null
                && live.getPlot().contains(new net.minecraft.world.level.ChunkPos(pos));
    }

    /** Local searches stay inside the source frame's authored plot and use resident-only block reads. */
    @Nullable
    private record FrameFlowTarget(@Nullable BlockPos target, boolean unknown, boolean openEdge) { }

    private static FrameFlowTarget findFrameFlowTarget(net.minecraft.server.level.ServerLevel level,
                                                       java.util.UUID frameId, BlockPos neighborPos) {
        var frame = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(frameId);
        if (!(frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live)
                || !isInsideSourcePlot(level, frameId, neighborPos)) return new FrameFlowTarget(null, true, false);
        BlockState state = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                .knownServerBlock(level, live, neighborPos);
        if (state == null) return new FrameFlowTarget(null, true, false);
        if (state.is(ModBlocks.PUDDLE.get())) return new FrameFlowTarget(neighborPos.immutable(), false, false);
        if (!state.canBeReplaced()) return new FrameFlowTarget(null, false, false);
        BlockPos.MutableBlockPos cursor = neighborPos.mutable();
        for (int i = 0; i < 30; i++) {
            cursor.move(Direction.DOWN);
            if (level.isOutsideBuildHeight(cursor) || !isInsideSourcePlot(level, frameId, cursor))
                return new FrameFlowTarget(null, false, true);
            BlockState below = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .knownServerBlock(level, live, cursor);
            if (below == null) return new FrameFlowTarget(null, true, false);
            if (below.is(ModBlocks.PUDDLE.get())) return new FrameFlowTarget(cursor.immutable(), false, false);
            if (!below.canBeReplaced()) return new FrameFlowTarget(cursor.above().immutable(), false, false);
        }
        return new FrameFlowTarget(null, false, true);
    }

    private void removeEmptyBlock() {
        if (removingEmptyBlock || level == null || level.isClientSide
                || level.getBlockEntity(worldPosition) != this
                || !level.getBlockState(worldPosition).is(ModBlocks.PUDDLE.get())) return;
        removingEmptyBlock = true;
        // No drops: reagent state is already empty and must not be duplicated.
        level.removeBlock(worldPosition, false);
    }

    private boolean isWithinCapacity(ReagentAttachment contents, String operation, BlockPos pos) {
        long capacity = ReagentUnits.fromFloat(getCapacity());
        long stored = contents.totalUnits();
        if (stored <= capacity) return true;
        LOGGER.error("Skipping {} for legacy over-capacity puddle at {}: stored {} cents exceeds {} cents; " +
                "contents are preserved for repair", operation, pos, stored, capacity);
        return false;
    }

    static void transferFlow(ReagentAttachment source, ReagentAttachment target, float netVol,
                             float osmosisVol, float capacity) {
        // Requested flow remains float-derived, so pressure may approximate at cent
        // boundaries. Each committed direction is an exact paired cent transfer.
        float totalToPush = netVol + osmosisVol;
        ReagentAttachment.transferUnits(source, target, ReagentUnits.fromFloat(Math.max(0f, totalToPush)),
                ReagentUnits.fromFloat(capacity));
        ReagentAttachment.transferUnits(target, source, ReagentUnits.fromFloat(Math.max(0f, osmosisVol)),
                ReagentUnits.fromFloat(capacity));
    }

    private BlockPos findFlowTarget(Level level, BlockPos neighborPos) {
        BlockState state = level.getBlockState(neighborPos);

        if (state.canBeReplaced()) {
            return findFirstSurfaceBelow(level, neighborPos, 30, (s) -> s.is(ModBlocks.PUDDLE.get()), (s) -> !s.canBeReplaced());
        }else if (state.is(ModBlocks.PUDDLE.get())){
            return neighborPos;
        }

        return null; // Wall or non-replaceable block
    }
}

