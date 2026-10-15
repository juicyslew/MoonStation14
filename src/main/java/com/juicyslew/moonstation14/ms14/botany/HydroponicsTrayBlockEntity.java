package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.List;

/** Authoritative lifecycle and solution holder; persisted values are never reinitialized on catalog reload. */
public final class HydroponicsTrayBlockEntity extends BlockEntity {
    private static final String PLANT = "PlantState", SOIL = "SoilSolution", INIT = "ProfileInitialized", PLACEMENT = "PlacementIdentity";
    private static final String APPEARANCE_STATUS = "PlantAppearanceStatus", APPEARANCE_STAGE = "PlantAppearanceStage", APPEARANCE_KEY = "PlantAppearanceKey";
    private UUID placementIdentity = UUID.randomUUID();
    private PlantInstance plant;
    private ReagentAttachment soil = new ReagentAttachment();
    private boolean initialized;
    private long lastPublish = Long.MIN_VALUE;
    private List<PlantReagentMetabolism.UnsupportedEffect> unsupportedMetabolism = List.of();
    private PlantInstance.Status appearanceStatus = PlantInstance.Status.EMPTY;
    private int appearanceStage;
    private String appearanceKey = "";
    private int warningBits;
    private static final String APPEARANCE_WARNINGS = "PlantAppearanceWarnings";
    private Optional<PlantMetabolismEvaluator.Environment> appearanceEnvironment = Optional.empty();

    public HydroponicsTrayBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.HYDROPONICS_TRAY.get(), pos, state); }
    public PlantInstance plantState() { return plant; }
    public ReagentAttachment soilSolution() { return soil; }
    public UUID placementIdentity() { return placementIdentity; }
    public PlantInstance.Status appearanceStatus() { return appearanceStatus; }
    public int appearanceStage() { return appearanceStage; }
    public String appearanceKey() { return appearanceKey; }
    public int warningBits() { return warningBits; }
    public List<PlantReagentMetabolism.UnsupportedEffect> unsupportedMetabolism() { return unsupportedMetabolism; }
    public boolean isLoadedValid() { return level != null && level.getBlockEntity(worldPosition) == this && !isRemoved(); }
    public TrayProfile resolvedTrayProfile() {
        return getBlockState().getBlock() instanceof HydroponicsTrayBlock block
                ? ModTrayProfiles.serverLookup(ModTrayProfiles.trayId(block.profile())) : null;
    }

    /** Atomic server-thread soil replacement guarded by the admitted tray/plant and exact solution snapshot. */
    public boolean tryReplaceSoil(UUID expectedPlacement, PlantInstance expectedPlant, long expectedPlantRevision,
                                  ReagentAttachment expectedSoil, ReagentAttachment replacement) {
        if (expectedPlacement == null || expectedSoil == null || replacement == null
                || !(level instanceof ServerLevel server) || server.getServer() == null
                || !server.getServer().isSameThread() || !isLoadedValid()
                || server.getBlockEntity(worldPosition) != this || !placementIdentity.equals(expectedPlacement)
                || plant != expectedPlant || (plant == null ? expectedPlantRevision != -1
                    : plant.revision() != expectedPlantRevision) || !soil.equals(expectedSoil)) return false;
        TrayProfile profile = resolvedTrayProfile();
        if (profile == null || profile.soilCapacityCents() < 0
                || replacement.totalUnits() > profile.soilCapacityCents()
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(replacement.getMap(), server, "hydroponics soil")) return false;
        soil = new ReagentAttachment(replacement.toComponent());
        commitMutation();
        return true;
    }

    /** Trusted caller-only lifecycle transition; callers must already have authenticated and validated the action. */
    void commitPlant(ResourceLocation seedId, SeedProfile seed, long gameTime) {
        if (plant == null || seedId == null || seed == null) throw new IllegalStateException("tray is not initialized");
        plant.plant(seedId, seed, gameTime); publishMutation();
    }
    /** Trusted caller-only clear transition. */
    void commitClear() { if (plant != null) { PlantMetabolismEvaluator.clear(plant); publishMutation(); } }
    /** Trusted after-output source commit; the caller captured the exact plant and revision. */
    boolean commitHarvest(PlantInstance expected, long revision, SeedProfile seed) {
        if (plant != expected || expected == null || expected.revision() != revision
                || !expected.ready() || expected.dead() || seed == null) return false;
        PlantMetabolismEvaluator.harvest(expected, seed, 1.0f);
        // Repeat harvest changes readiness without advancing the plant revision in the evaluator.
        // Ensure publish() does not mistake it for an already-published lifecycle snapshot.
        expected.touch();
        publishMutation();
        return true;
    }
    /** Publish any trusted mutation performed by an authorized same-package domain handler. */
    void commitMutation() {
        if (plant == null) return;
        plant.touch();
        publishMutation();
    }
    private void publishMutation() {
        if (level instanceof ServerLevel server && isLoadedValid()) { plant.clampSoil(); setChanged(); publish(server); }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HydroponicsTrayBlockEntity tray) {
        if (!(level instanceof ServerLevel server) || !tray.isLoadedValid() || tray.level != server) return;
        HydroponicsTrayBlock block = (HydroponicsTrayBlock) state.getBlock();
        ResourceLocation profileId = ModTrayProfiles.trayId(block.profile());
        TrayProfile profile = ModTrayProfiles.serverLookup(profileId);
        if (profile == null) return;
        long now = server.getGameTime();
        if (!tray.initialized) {
            tray.plant = new PlantInstance(profile.initialWater(), profile.initialNutrition(), now);
            tray.initialized = true;
            tray.setChanged();
        }
        if (tray.plant == null) return;
        PrototypeCatalog<ReagentData> reagents = PrototypeRuntime.serverReagents();
        if (reagents == null || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                tray.soil.getMap(), reagents, "hydroponics tray " + pos)) return;
        SeedProfile seed = ModSeedProfiles.serverLookup(tray.plant.seed());
        var atmosphere = Optional.<PlantMetabolismEvaluator.Environment>empty();
        BlockPos exposure = pos.above();
        if (!server.getBlockState(exposure).isSolid()) {
            var gas = AtmosphereService.INSTANCE.sample(server, exposure);
            if (gas.isPresent()) atmosphere = Optional.of(new PlantMetabolismEvaluator.Environment(
                    (float) gas.get().pressureKpa(1.0), (float) gas.get().temperatureKelvin()));
        }
        tray.appearanceEnvironment = atmosphere;
        var result = PlantMetabolismEvaluator.poll(tray.plant, profile, seed, now, instance -> {
                    SoilTarget target = new SoilTarget(instance);
                    tray.unsupportedMetabolism = PlantReagentMetabolism.poll(tray.soil,
                            key -> reagents.get(key.location()), target).unsupportedEffects();
                    target.finish();
                }, new RandomGenerator() {
                            @Override public long nextLong() { return server.random.nextLong(); }
                            @Override public int nextInt(int bound) { return server.random.nextInt(bound); }
                            @Override public float nextFloat() { return server.random.nextFloat(); }
                        }, atmosphere);
        if (result.isPresent()) { tray.plant.clampSoil(); tray.setChanged(); tray.publish(server); }
    }
    private static final class SoilTarget implements PlantReagentMetabolism.Target {
        private final PlantInstance plant;
        private float water, nutrition, toxins;
        private SoilTarget(PlantInstance plant) { this.plant=plant; water=plant.water(); nutrition=plant.nutrition(); toxins=plant.soilToxins(); }
        @Override public void adjustWater(float amount) { water += amount; if (amount > 0) toxins -= 4 * amount; }
        @Override public void adjustNutrition(float amount) { nutrition += amount; }
        private void finish() { plant.soil(PlantInstance.clamp(water,0,100), PlantInstance.clamp(nutrition,0,100), plant.weeds(), PlantInstance.clamp(toxins,0,100)); }
    }
    private void publish(ServerLevel server) {
        projectAppearance();
        if (lastPublish == plant.revision() && publishedWarningBits == warningBits) return;
        lastPublish = plant.revision(); server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    private int publishedWarningBits;
    private void projectAppearance() {
        SeedProfile seed = plant == null || plant.seed() == null ? null : ModSeedProfiles.serverLookup(plant.seed());
        var appearance = plant == null
                ? new PlantMetabolismEvaluator.Appearance(PlantInstance.Status.EMPTY, 0)
                : PlantMetabolismEvaluator.appearance(plant, seed);
        appearanceStatus = appearance.status(); appearanceStage = appearance.stage();
        appearanceKey = seed == null ? "" : seed.visualKey();
        TrayProfile profile = resolvedTrayProfile();
        warningBits = PlantMetabolismEvaluator.warningBits(plant, seed, profile != null && profile.drawWarnings(), appearanceEnvironment);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(INIT, initialized); tag.putUUID(PLACEMENT, placementIdentity);
        tag.putString(APPEARANCE_STATUS, appearanceStatus.name()); tag.putInt(APPEARANCE_STAGE, appearanceStage); tag.putString(APPEARANCE_KEY, appearanceKey);
        tag.putInt(APPEARANCE_WARNINGS, warningBits);
        if (plant != null) PlantInstance.CODEC.encodeStart(NbtOps.INSTANCE, plant).result().ifPresent(nbt -> tag.put(PLANT, (net.minecraft.nbt.Tag) nbt));
        tag.put(SOIL, (net.minecraft.nbt.Tag) ReagentAttachment.CODEC.encodeStart(NbtOps.INSTANCE, soil).getOrThrow());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); initialized = tag.getBoolean(INIT);
        try { appearanceStatus = PlantInstance.Status.valueOf(tag.getString(APPEARANCE_STATUS)); } catch (IllegalArgumentException ignored) { appearanceStatus = PlantInstance.Status.EMPTY; }
        appearanceStage = Math.max(0, tag.getInt(APPEARANCE_STAGE)); appearanceKey = tag.getString(APPEARANCE_KEY);
        warningBits = tag.getInt(APPEARANCE_WARNINGS);
        if (tag.hasUUID(PLACEMENT)) placementIdentity = tag.getUUID(PLACEMENT);
        if (tag.contains(PLANT)) plant = PlantInstance.CODEC.parse(NbtOps.INSTANCE, tag.get(PLANT)).result().orElse(null);
        if (tag.contains(SOIL)) soil = ReagentAttachment.CODEC.parse(NbtOps.INSTANCE, tag.get(SOIL)).result().orElseGet(ReagentAttachment::new);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onLoad() {
        super.onLoad(); lastPublish = Long.MIN_VALUE;
        if (initialized && plant != null && level instanceof ServerLevel && ModSeedProfiles.serverReady()) projectAppearance();
    }
    @Override public void setRemoved() { super.setRemoved(); }
}
