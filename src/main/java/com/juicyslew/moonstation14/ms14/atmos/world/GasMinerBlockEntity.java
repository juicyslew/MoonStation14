package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/** Runtime shell; all source parameters come from the resolved profile, never saved/invented state. */
public final class GasMinerBlockEntity extends BlockEntity {
    private long lastTick = Long.MIN_VALUE;
    private transient double producedMoles;
    private transient double producedThermalJoules;
    private UUID placementIdentity;
    public GasMinerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.GAS_MINER.get(), pos, state); }
    public static void tick(ServerLevel level, GasMinerBlockEntity miner) {
        if (miner.isRemoved() || miner.level != level || !level.getServer().isSameThread()) return;
        miner.ensurePlacementIdentity(level);
        long now = level.getGameTime();
        if (now == miner.lastTick) return;
        miner.lastTick = now;
        if (!level.hasChunkAt(miner.worldPosition)) return;
        GasMinerProfile profile = miner.resolvedProfile();
        if (profile == null) return;
        var fixtureData=com.juicyslew.moonstation14.ms14.atmos.machine.fixture.AtmosMachineryFixtureSavedData.existing(level).orElse(null);
        var identity=miner.placementIdentity();
        var fixtureOwner=fixtureData==null?null:fixtureData.entries().stream()
                .filter(entry->entry.ownsMiner(miner.worldPosition,identity)).findFirst().orElse(null);
        if(fixtureOwner!=null&&!fixtureOwner.laboratoryStarted())return;
        double requested = profile.molesForElapsedSeconds(1.0 / 20.0);
        var receipt = AtmosphereService.INSTANCE.generateFiniteGas(level, miner.worldPosition, profile.gas(), requested,
                profile.spawnTemperatureKelvin(), profile.maxExternalPressureKpa(), profile.maxTotalMoles());
        receipt.ifPresent(actual -> {
            miner.producedMoles = com.juicyslew.moonstation14.ms14.atmos.core.FiniteObservationTotal.add(miner.producedMoles, actual.packet().totalMoles());
            miner.producedThermalJoules = com.juicyslew.moonstation14.ms14.atmos.core.FiniteObservationTotal.add(miner.producedThermalJoules, actual.thermalEnergyJoules());
        });
    }
    public GasMinerProfile resolvedProfile() {
        return level == null || !(getBlockState().getBlock() instanceof GasMinerBlock block) ? null
                : ModGasMiners.catalog(level).get(ModGasMiners.id(block.profileIdentity()));
    }
    public double producedMoles() { return producedMoles; }
    public double producedThermalJoules() { return producedThermalJoules; }
    public UUID placementIdentity() {
        if (level instanceof ServerLevel server && server.getServer().isSameThread() && isExactLoadedHost(server) && placementIdentity == null) {
            placementIdentity = UUID.randomUUID();
            setChanged();
        }
        return isLivePlacementIdentity() ? placementIdentity : null;
    }
    public boolean hasLivePlacementIdentity() { return isLivePlacementIdentity() && placementIdentity != null; }
    private boolean isLivePlacementIdentity() { return level instanceof ServerLevel server && isExactLoadedHost(server); }
    private void ensurePlacementIdentity(ServerLevel server) {
        if (placementIdentity == null && isExactLoadedHost(server)) { placementIdentity = UUID.randomUUID(); setChanged(); }
    }
    private boolean isExactLoadedHost(ServerLevel server) {
        if (server == null || !server.getServer().isSameThread() || level != server || isRemoved()) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                && chunk.getBlockEntities().get(worldPosition) == this
                && chunk.getBlockState(worldPosition).getBlock() instanceof GasMinerBlock block
                && block.profileIdentity().equals(((GasMinerBlock) getBlockState().getBlock()).profileIdentity());
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (placementIdentity != null) tag.putUUID("PlacementIdentity", placementIdentity);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        placementIdentity = tag.hasUUID("PlacementIdentity") ? tag.getUUID("PlacementIdentity") : null;
    }
}
