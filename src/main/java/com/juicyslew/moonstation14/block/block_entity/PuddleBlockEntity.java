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

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.getBlendedColor;
import static com.juicyslew.moonstation14.util.Helpers.findFirstSurfaceBelow;
import static com.juicyslew.moonstation14.util.MapOperations.getTotal;
import static com.juicyslew.moonstation14.util.NetworkingUtils.*;

public class PuddleBlockEntity extends BlockEntity implements IReagentTrait {
    private long lastCommittedUnits = -1L;
    private boolean removingEmptyBlock;
    // Gonna probably want a generic ContainerBlockEntity.
    static Map<Integer, Direction> puddleOffsets = Map.of(
            0, Direction.NORTH,//List.of(0,1),
            1, Direction.EAST,
            2, Direction.SOUTH,
            3, Direction.WEST
    );

    static float overflowThreshold = 20f;

    public PuddleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUDDLE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide)
            lastCommittedUnits = getReagentContainer().totalUnits();
    }

    @Override public float getCapacity() { return 200000f; }

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
            if (drained) removeEmptyBlock();
            else if (committedUnits > 0L) updateFillLevel();
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PuddleBlockEntity be) {
        // Only run logic on the server
        if (level.isClientSide) return;

        // Empty /setblock puddles are allowed to survive placement so callers can
        // fill them in the same server operation, but never persist past a tick.
        ReagentAttachment contents = be.getReagentContainer();
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(contents.getMap(), level,
                "puddle tick " + pos)) return;
        if (contents.totalUnits() == 0L) {
            be.removeEmptyBlock();
            return;
        }

        // The 20-tick heartbeat (using the position offset to prevent lag spikes)
        float ticksPerCalc = 10f;
        if ((level.getGameTime() + pos.hashCode()) % ticksPerCalc == 0) {
            be.slowTick(level, pos, state, be);
        }
    }

    private void slowTick(Level level, BlockPos pos, BlockState state, PuddleBlockEntity be) {
        ReagentAttachment srcCont = getReagentContainer();
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(srcCont.getMap(), level,
                "puddle flow source " + pos)) return;
        var srcBefore = MS14Provider.snapshot(srcCont);
        float volume = getTotal(srcCont.getMap());

        if (volume <= overflowThreshold) return;

        // Might want a cap on flow, unclear.
        float flowCapacity = volume - overflowThreshold;

        // 1. Identify valid neighbors and their "pressure"
        record Neighbor(
                PuddleBlockEntity puddle,
                float pressure,
                BlockPos pos
        ){}
        List<Neighbor> neighbors = new ArrayList<>();
        float totalDrop = 0;

        // Validate all existing destinations before creating even an empty neighbor
        // puddle, so a bad destination makes this whole flow tick a no-op.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos targetPos = findFlowTarget(level, pos.relative(dir));
            if (targetPos != null && level.getBlockEntity(targetPos) instanceof PuddleBlockEntity target
                    && !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                    target.getReagentContainer().getMap(), level, "puddle flow destination " + targetPos)) return;
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos targetPos = findFlowTarget(level, pos.relative(dir));
            if (targetPos == null) continue; // Puddles will avoid ledges longer than max-search dist lol.
            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.canBeReplaced()){
                // This works as long as PUDDLES can't be replaced.
                level.setBlock(targetPos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
            }
            // Can we spread here? (Is it already a puddle or replaceable air?)
            if (level.getBlockEntity(targetPos) instanceof PuddleBlockEntity targetPuddle) {
                // If source is above target, pressure is based on the empty air.
                float neighborEffectiveVol = pos.getY() > targetPos.getY() ? 0 : getTotal(targetPuddle.getReagentContainer().getMap());
                if (volume >= neighborEffectiveVol) {
                    float drop = volume - neighborEffectiveVol;
                    neighbors.add(new Neighbor(targetPuddle, drop, targetPos));
                    totalDrop += drop;
                }
            }
        }

        if (neighbors.isEmpty()) return;

        // 2. Determine how much total VOLUME can move

        // 3. The "Efficiency" Step: Combined Loop
        for (var neighbor : neighbors) {
            PuddleBlockEntity targetPuddle = neighbor.puddle;
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
            if (targetPos.getY() < pos.getY()){
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
            performCombinedTransfer(srcCont, dstCont, netVolumeToMove, osmosisAmount);
            dstCont.recursiveReaction(level, getCapacity());
            MS14Provider.updateIfChanged(targetPuddle, MS14Bridges.REAGENT, dstBefore, dstCont);
        }
        srcCont.recursiveReaction(level, getCapacity());
        MS14Provider.updateIfChanged(this, MS14Bridges.REAGENT, srcBefore, srcCont);
    }

    private void performCombinedTransfer(ReagentAttachment source, ReagentAttachment target, float netVol, float osmosisVol) {
        transferFlow(source, target, netVol, osmosisVol, getCapacity());
    }

    private void removeEmptyBlock() {
        if (removingEmptyBlock || level == null || level.isClientSide
                || level.getBlockEntity(worldPosition) != this
                || !level.getBlockState(worldPosition).is(ModBlocks.PUDDLE.get())) return;
        removingEmptyBlock = true;
        // No drops: reagent state is already empty and must not be duplicated.
        level.removeBlock(worldPosition, false);
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
