package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay;
import com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators;
import com.juicyslew.moonstation14.ms14.singularity.SingularityGeneratorProfile;
import com.juicyslew.moonstation14.ms14.singularity.SingularityGeneratorStartup;
import com.juicyslew.moonstation14.ms14.singularity.containment.ClosedFieldVolume;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrengthProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Startup-only authoritative generator. It is intentionally not a field generator or containment node. */
public final class SingularityGeneratorBlockEntity extends BlockEntity {
    private static final String PROFILE="StartupProfile", STATE_PRESENT="StartupStatePresent", CHARGE="StartupCharge",
            INERT="StartupInert", FAILSAFE_TICK="FailsafeTick", IDENTITY="PlacementIdentity";
    private ResourceLocation profileId=ModSingularityGenerators.DEFAULT_ID;
    private UUID placementIdentity=UUID.randomUUID();
    private UUID spawnedSingularityIdentity;
    private SingularityGeneratorStartup startup;
    private double savedCharge;
    private boolean savedInert;
    private long savedFailsafeTick;
    private boolean savedStatePresent;

    public SingularityGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SINGULARITY_GENERATOR.get(),pos,state);
    }

    /** PA collision callback. The caller owns particle consumption regardless of this return value. */
    public boolean receiveParticle(ParticleAcceleratorStrengthProfile strength) {
        if (!(level instanceof ServerLevel server) || strength==null || strength.generatorCharge()<=0) return false;
        SingularityGeneratorProfile profile=ModSingularityGenerators.catalog(server).get(profileId);
        if (profile==null || ModSingularityGameplay.catalog(server).get(profile.spawnProfile())==null) return false;
        ensureStartup(profile);
        FieldContainmentRuntime.refresh(server);
        boolean contained=!profile.failsafeEnabled() || FieldContainmentRuntime.closedVolumes(server).stream().anyMatch(bounds ->
                ClosedFieldVolume.containsWithinFaceDistance(bounds,Vec3.atCenterOf(worldPosition),profile.failsafeDistance()));
        boolean spawn=startup.accept(strength.generatorCharge(),contained,server.getGameTime(),profile);
        persistSnapshot();
        if(spawn) spawnSingularity(server,profile);
        return spawn;
    }

    private void ensureStartup(SingularityGeneratorProfile profile) {
        if(startup==null) startup=savedStatePresent
                ? new SingularityGeneratorStartup(profile,savedCharge,savedInert,savedFailsafeTick)
                : new SingularityGeneratorStartup(profile);
    }
    private void persistSnapshot() {
        var snapshot=startup.snapshot(); savedCharge=snapshot.charge(); savedInert=snapshot.inert();
        savedFailsafeTick=snapshot.nextFailsafeTick(); savedStatePresent=true; setChanged();
    }
    private void spawnSingularity(ServerLevel server,SingularityGeneratorProfile profile) {
        SingularityEntity singularity=ModEntities.SINGULARITY.get().create(server);
        if(singularity==null) return;
        singularity.moveTo(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,0,0);
        singularity.initializeFromGenerator(profile.spawnProfile());
        if(server.addFreshEntity(singularity)) {
            spawnedSingularityIdentity=singularity.getUUID();
            setChanged();
        }
    }

    public UUID placementIdentity() { return placementIdentity; }
    public double startupCharge() { return startup==null?savedCharge:startup.charge(); }
    public boolean startupInert() { return startup==null?savedInert:startup.inert(); }
    public long startupNextFailsafeTick() { return startup==null?savedFailsafeTick:startup.nextFailsafeTick(); }
    public ResourceLocation profileId() { return profileId; }
    /** Exact entity accepted by the level from this generator's one-shot spawn. */
    public UUID spawnedSingularityIdentity() { return spawnedSingularityIdentity; }

    @Override protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        if(startup!=null) { var snapshot=startup.snapshot(); savedCharge=snapshot.charge(); savedInert=snapshot.inert(); savedFailsafeTick=snapshot.nextFailsafeTick(); savedStatePresent=true; }
        tag.putString(PROFILE,profileId.toString()); tag.putUUID(IDENTITY,placementIdentity);
        if(spawnedSingularityIdentity!=null)tag.putUUID("SpawnedSingularityIdentity",spawnedSingularityIdentity);
        tag.putBoolean(STATE_PRESENT,savedStatePresent); tag.putDouble(CHARGE,savedCharge);
        tag.putBoolean(INERT,savedInert); tag.putLong(FAILSAFE_TICK,savedFailsafeTick);
    }
    @Override protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        if(tag.contains(PROFILE)) { ResourceLocation parsed=ResourceLocation.tryParse(tag.getString(PROFILE)); if(parsed!=null) profileId=parsed; }
        if(tag.hasUUID(IDENTITY)) placementIdentity=tag.getUUID(IDENTITY);
        spawnedSingularityIdentity=tag.hasUUID("SpawnedSingularityIdentity")?tag.getUUID("SpawnedSingularityIdentity"):null;
        savedStatePresent=tag.getBoolean(STATE_PRESENT); savedCharge=Math.max(0,tag.getDouble(CHARGE));
        savedInert=tag.getBoolean(INERT); savedFailsafeTick=Math.max(0,tag.getLong(FAILSAFE_TICK)); startup=null;
    }
}
