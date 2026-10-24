package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/** Resolved extractor configuration and persistent placement identity; processing is integrated separately. */
public final class SeedExtractorBlockEntity extends BlockEntity {
    private static final String PLACEMENT = "PlacementIdentity";
    private UUID placementIdentity = UUID.randomUUID();
    private boolean initialized;

    public SeedExtractorBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.SEED_EXTRACTOR.get(), pos, state); }
    public UUID placementIdentity() { return placementIdentity; }
    public boolean isInitialized() { return initialized; }
    public boolean isLoadedValid() { return level != null && level.hasChunkAt(worldPosition)
            && level.getBlockEntity(worldPosition) == this && !isRemoved(); }
    public MachineProfile resolvedProfile() {
        var catalog = ModBotanyMachineProfiles.serverCatalog();
        return catalog == null ? null : catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "seed_extractor"));
    }
    /** SS14 zero-load receiver semantics: enabled zero-load machines satisfy received power == load. */
    public boolean isPowered() {
        if (level == null || !(level instanceof ServerLevel) || !isLoadedValid() || !initialized) return false;
        MachineProfile profile = resolvedProfile();
        if (profile == null || !profile.extractor()) return false;
        // No PowerReceiver component means SS14 IsPowered defaults true. A receiver with
        // zero configured load is likewise available; positive loads fail closed until
        // the shared meter integration can supply received watts.
        if (!profile.powered()) return true;
        return com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter((ServerLevel) level, this).known();
    }
    /** Projects the existing extraction-admission power predicate into the client block appearance. */
    public void projectPowerVisual() {
        if (!(level instanceof ServerLevel) || !isLoadedValid() || getBlockState().getBlock() != com.juicyslew.moonstation14.block.ModBlocks.SEED_EXTRACTOR.get()) return;
        BlockState current = getBlockState();
        boolean powered = isPowered();
        if (current.hasProperty(SeedExtractorBlock.POWERED) && current.getValue(SeedExtractorBlock.POWERED) != powered)
            level.setBlock(worldPosition, current.setValue(SeedExtractorBlock.POWERED, powered), 3);
    }
    public static void tick(Level level, BlockPos pos, SeedExtractorBlockEntity entity) {
        if (!(level instanceof ServerLevel) || !entity.isLoadedValid() || entity.level != level || entity.initialized) return;
        MachineProfile profile = entity.resolvedProfile();
        if (profile == null || !profile.extractor()) return;
        entity.initialized = true;
        entity.setChanged();
        com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineChanged((ServerLevel) level, pos);
    }
    @Override public void onLoad() { super.onLoad(); if (level instanceof ServerLevel server && initialized) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineChanged(server, worldPosition); }
    @Override public void setRemoved() { if (level instanceof ServerLevel server) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineRemoved(server, worldPosition); super.setRemoved(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putUUID(PLACEMENT, placementIdentity); tag.putBoolean("ProfileInitialized", initialized);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(PLACEMENT)) placementIdentity = tag.getUUID(PLACEMENT);
        initialized = tag.getBoolean("ProfileInitialized");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
