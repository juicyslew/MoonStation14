package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.effect.PopupMessageEffect;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
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
import net.minecraft.world.item.ItemStack;

/** Authoritative lifecycle and solution holder; persisted values are never reinitialized on catalog reload. */
public final class HydroponicsTrayBlockEntity extends BlockEntity {
    private static final String PLANT = "PlantState", SOIL = "SoilSolution", INIT = "ProfileInitialized", PLACEMENT = "PlacementIdentity";
    private static final String APPEARANCE_STATUS = "PlantAppearanceStatus", APPEARANCE_STAGE = "PlantAppearanceStage", APPEARANCE_KEY = "PlantAppearanceKey";
    private UUID placementIdentity = UUID.randomUUID();
    private PlantInstance plant;
    private boolean seedRestoredThisPoll;
    private ReagentAttachment soil = new ReagentAttachment();
    private boolean initialized;
    private long lastPublish = Long.MIN_VALUE;
    private List<PlantReagentMetabolism.UnsupportedEffect> unsupportedMetabolism = List.of();
    private PlantMutationSystem.Result mutationResult = new PlantMutationSystem.Result(0, List.of());
    private PlantMetabolismEvaluator.GasExchangeReport gasExchangeReport = PlantMetabolismEvaluator.GasExchangeReport.none();
    public enum AutoHarvestOutcome { NONE, SUCCESS, FAILED }
    private AutoHarvestOutcome autoHarvestOutcome = AutoHarvestOutcome.NONE;
    private PlantInstance.Status appearanceStatus = PlantInstance.Status.EMPTY;
    private int appearanceStage;
    private String appearanceKey = "";
    private int warningBits;
    private static final String APPEARANCE_WARNINGS = "PlantAppearanceWarnings";
    private Optional<PlantMetabolismEvaluator.Environment> appearanceEnvironment = Optional.empty();
    // Narrow, transient fixture seam: only the ordinary scheduled ticker consults it.
    private transient RandomGenerator fixtureCycleRandom;
    private transient boolean fixturePaused;
    private transient long fixturePauseStarted;

    public HydroponicsTrayBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.HYDROPONICS_TRAY.get(), pos, state); }
    public PlantInstance plantState() { return plant; }
    public ReagentAttachment soilSolution() { return soil; }
    public UUID placementIdentity() { return placementIdentity; }
    public PlantInstance.Status appearanceStatus() { return appearanceStatus; }
    public int appearanceStage() { return appearanceStage; }
    public String appearanceKey() { return appearanceKey; }
    public int warningBits() { return warningBits; }
    /** Last sampled exposure; read-only and empty until an ordinary tray poll has sampled it. */
    public Optional<PlantMetabolismEvaluator.Environment> cachedAppearanceEnvironment() { return appearanceEnvironment; }
    public List<PlantReagentMetabolism.UnsupportedEffect> unsupportedMetabolism() { return unsupportedMetabolism; }
    public PlantMutationSystem.Result mutationResult() { return mutationResult; }
    /** Last actual living-cycle named-gas exchange outcome; poll skips/dead holders retain it. */
    public PlantMetabolismEvaluator.GasExchangeReport gasExchangeReport() { return gasExchangeReport; }
    public AutoHarvestOutcome autoHarvestOutcome() { return autoHarvestOutcome; }
    void setFixtureCycleRandom(RandomGenerator random) { fixtureCycleRandom = random; }
    void clearFixtureCycleRandom() { fixtureCycleRandom = null; }
    public boolean isLoadedValid() { return level != null && level.getBlockEntity(worldPosition) == this && !isRemoved(); }
    /** Transient fixture-only hold. Ordinary authenticated care/harvest operations remain available. */
    public boolean fixturePaused() { return fixturePaused; }
    public void setFixturePaused(boolean paused) {
        if (paused == fixturePaused || !(level instanceof ServerLevel server) || !isLoadedValid()) return;
        if (paused) { fixturePaused = true; fixturePauseStarted = server.getGameTime(); }
        else {
            // Preserve the full remaining poll/cycle delay for old plants. A plant planted
            // during the hold instead shifts only from its own planting time to resume.
            if (plant != null) plant.resumeFixtureSchedule(fixturePauseStarted, server.getGameTime());
            fixturePaused = false; fixturePauseStarted = 0;
        }
    }
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
    void commitPlant(GeneticValue genetics, long gameTime) {
        if (plant == null || genetics == null) throw new IllegalStateException("tray is not initialized");
        plant.plant(genetics, gameTime); publishMutation();
    }
    void commitPlant(PlantSeedReference reference, long gameTime) {
        if (plant == null || reference == null) throw new IllegalStateException("tray is not initialized");
        plant.plant(reference, gameTime); publishMutation();
    }
    void commitPlant(PlantSeedReference reference, long gameTime, Float healthOverride) {
        if (plant == null || reference == null) throw new IllegalStateException("tray is not initialized");
        plant.plant(reference, gameTime, healthOverride); publishMutation();
    }
    /** Trusted caller-only clear transition. */
    void commitClear() { if (plant != null) { PlantMetabolismEvaluator.clear(plant); publishMutation(); } }
    /** Trusted after-output source commit; the caller captured the exact plant and revision. */
    boolean commitHarvest(PlantInstance expected, long revision, SeedProfile seed) {
        return commitHarvest(expected, revision, seed, 0);
    }
    boolean commitHarvest(PlantInstance expected, long revision, SeedProfile seed, int actualOutputCount) {
        if (plant != expected || expected == null || expected.revision() != revision
                || !expected.ready() || expected.dead() || seed == null) return false;
        var scream = PlantScreamAudio.prepare(seed);
        PlantMetabolismEvaluator.harvest(expected, seed, 1.0f);
        markAutomaticHarvestSourceShared(expected, actualOutputCount);
        // Repeat harvest changes readiness without advancing the plant revision in the evaluator.
        // Ensure publish() does not mistake it for an already-published lifecycle snapshot.
        expected.touch();
        publishMutation();
        PlantScreamAudio.play(level instanceof ServerLevel server ? server : null, worldPosition, scream);
        return true;
    }
    /** Automatic self-harvest retains the source, so any published product aliases its seed reference. */
    static void markAutomaticHarvestSourceShared(PlantInstance source, int actualOutputCount) {
        if (actualOutputCount > 0) source.markSeedShared();
    }
    /** First sampling commit phase, called only after the staged packet is published. */
    boolean commitSamplingHealth(UUID expectedPlacement, PlantInstance expected, long expectedRevision,
                                 PlantSeedReference reference, float postLossHealth) {
        if (!samplingStateCurrent(expectedPlacement, expected, expectedRevision, reference)) return false;
        expected.samplingHealth(postLossHealth);
        expected.markSeedShared(reference);
        publishMutation();
        return true;
    }
    /** Final sampling lifecycle phase follows pickup attempt and best-effort scream. */
    boolean finishSampling(UUID expectedPlacement, PlantInstance expected, long expectedRevision,
                           PlantSeedReference reference, boolean sampledRoll, float endurance) {
        if (!samplingStateCurrent(expectedPlacement, expected, expectedRevision, reference)) return false;
        if (sampledRoll) expected.sampled(true);
        expected.clampCareHealth(endurance);
        publishMutation();
        return true;
    }
    private boolean samplingStateCurrent(UUID expectedPlacement, PlantInstance expected, long expectedRevision,
                                         PlantSeedReference reference) {
        return isLoadedValid() && placementIdentity.equals(expectedPlacement) && plant == expected
                && expected != null && expected.revision() == expectedRevision && !expected.dead()
                && PlantSamplingPlan.isEligible(expected) && reference != null
                && expected.seedReference() == reference && expected.seed() != null;
    }
    static float screamGain(float volumeDb) { return (float) Math.pow(10.0, volumeDb / 20.0); }
    /** Publish any trusted mutation performed by an authorized same-package domain handler. */
    void commitMutation() {
        if (plant == null) return;
        plant.touch();
        publishMutation();
    }
    /** Same-thread pollen replacement after the caller has prevalidated placement and hand publication. */
    void commitPollen(UUID expectedPlacement, PlantSeedReference prepared) {
        if (prepared == null || plant == null || !placementIdentity.equals(expectedPlacement))
            throw new IllegalStateException("pollen target changed after preflight");
        plant.transitionSpecies(prepared);
        publishMutation();
    }
    private void publishMutation() {
        if (level instanceof ServerLevel server && isLoadedValid()) { plant.clampSoil(); setChanged(); publish(server); }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HydroponicsTrayBlockEntity tray) {
        if (!(level instanceof ServerLevel server)) return;
        RandomGenerator cycleRandom = tray.fixtureCycleRandom != null ? tray.fixtureCycleRandom : new RandomGenerator() {
            @Override public long nextLong() { return server.random.nextLong(); }
            @Override public int nextInt(int bound) { return server.random.nextInt(bound); }
            @Override public float nextFloat() { return server.random.nextFloat(); }
            @Override public double nextDouble() { return server.random.nextDouble(); }
        };
        tickWithRandom(level, pos, state, tray, cycleRandom);
    }

    static void tickWithRandom(Level level, BlockPos pos, BlockState state, HydroponicsTrayBlockEntity tray,
                               RandomGenerator cycleRandom) {
        if (!(level instanceof ServerLevel server) || !tray.isLoadedValid() || tray.level != server) return;
        if (tray.fixturePaused) return;
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
        SeedProfile seed = resolvedPlantProfile(tray.plant);
        var atmosphere = Optional.<PlantMetabolismEvaluator.Environment>empty();
        BlockPos exposure = pos.above();
        if (!server.getBlockState(exposure).isSolid()) {
            var gas = AtmosphereService.INSTANCE.sample(server, exposure);
            if (gas.isPresent()) atmosphere = Optional.of(new PlantMetabolismEvaluator.Environment(
                    (float) gas.get().pressureKpa(1.0), (float) gas.get().temperatureKelvin()));
        }
        tray.appearanceEnvironment = atmosphere;
        PlantMetabolismEvaluator.GasExchange gasExchange = new PlantMetabolismEvaluator.GasExchange() {
            @Override public Optional<PlantMetabolismEvaluator.Environment> environment() {
                if (server.getBlockState(exposure).isSolid()) return Optional.empty();
                return AtmosphereService.INSTANCE.sample(server, exposure).map(mixture ->
                        new PlantMetabolismEvaluator.Environment((float) mixture.pressureKpa(1.0), (float) mixture.temperatureKelvin()));
            }
            @Override public boolean consume(GasType gas, double amount) {
                return AtmosphereService.INSTANCE.tryConsumeGas(server, exposure, gas, amount);
            }
            @Override public boolean exude(GasType gas, double amount, float temperatureK) {
                return AtmosphereService.INSTANCE.addGas(server, exposure, gas, amount, temperatureK);
            }
        };
        var result = PlantMetabolismEvaluator.poll(tray.plant, profile, () -> resolvedPlantProfile(tray.plant), now, instance -> {
                    long sampleSeed = server.getGameTime() ^ pos.asLong() ^ ((long) server.dimension().location().hashCode() << 32);
                    tray.seedRestoredThisPoll = false;
                    SoilTarget target = new SoilTarget(instance, server.random::nextFloat, server.random::nextDouble,
                    () -> server.random.nextInt(7, 10), probabilitySample(sampleSeed), () -> tray.seedRestoredThisPoll = true);
                    tray.unsupportedMetabolism = PlantReagentMetabolism.poll(tray.soil,
                            key -> reagents.get(key.location()), target).unsupportedEffects();
                    target.finish();
                }, cycleRandom, atmosphere, instance -> {
                    var tables = PrototypeRuntime.serverPlantMutationTables();
                    PlantMutationTable mutations = tables == null ? null : tables.get(ModPlantMutations.tableId("random_plant_mutations"));
                    if (mutations == null) return;
                    tray.mutationResult = PlantMutationSystem.cycle(instance, mutations, cycleRandom,
                            server.random::nextFloat, PrototypeRuntime.serverWeightedRandomFillSolutions(), reagents,
                            PrototypeRuntime.serverSeedProfiles());
                 }, gasExchange, (source, sourceSeed, cycleAge, sourceCommit) -> {
                     long revision = source.revision();
                     var genetics = source.genetics();
                     int count = PlantMetabolismEvaluator.automaticHarvestCount(sourceSeed);
                     List<ItemStack> outputs = new java.util.ArrayList<>(count);
                     try {
                          for (int i = 0; i < count; i++) outputs.add(source.seedReference() == null
                                  ? ProduceFactory.createHarvest(genetics == null ? new GeneticValue(source.seed(), sourceSeed) : genetics,
                                      sourceSeed.lifecycle().potency())
                                  : ProduceFactory.createHarvest(source.seedReference(), sourceSeed.lifecycle().potency()));
                      } catch (RuntimeException invalidProduct) {
                          tray.autoHarvestOutcome = AutoHarvestOutcome.FAILED;
                          return false;
                      }
                      var preparedScream = PlantScreamAudio.prepare(sourceSeed);
                      var outcome = FreshWorldOutputBatch.publishSystemHarvest(server, pos, outputs,
                             () -> tray.isLoadedValid() && server.getBlockEntity(pos) == tray
                                     && tray.plant == source && source.revision() == revision
                                     && source.ready() && !source.dead() && source.seed() != null
                                     && source.genetics() == genetics
                                     && resolvedPlantProfile(source) == sourceSeed
                                     && sourceSeed.lifecycle().harvest() == SeedProfile.Harvest.SELF_HARVEST,
                              () -> {
                                  sourceCommit.run();
                                   markAutomaticHarvestSourceShared(source, count);
                                  source.touch();
                                 tray.publishMutation();
                             });
                      tray.autoHarvestOutcome = outcome == BodyHandItemTransfer.Result.SUCCESS
                              ? AutoHarvestOutcome.SUCCESS : AutoHarvestOutcome.FAILED;
                       if (outcome == BodyHandItemTransfer.Result.SUCCESS)
                           PlantScreamAudio.play(server, tray.getBlockPos(), preparedScream);
                      return outcome == BodyHandItemTransfer.Result.SUCCESS;
                 });
        if (result.isPresent()) {
            if (result.get().gasExchange().attempted()) tray.gasExchangeReport = result.get().gasExchange();
            tray.plant.clampSoil(); tray.setChanged(); tray.publish(server);
            if (tray.seedRestoredThisPoll) {
                tray.seedRestoredThisPoll = false;
                PopupMessageEffect.showAt(server, pos.getCenter(), "botany-plant-seedsrestored",
                        EffectData.PopupVisualType.Small);
            }
        }
    }
    static double probabilitySample(long seed) {
        long z = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
    private static final class SoilTarget implements PlantReagentMetabolism.Target {
        private final PlantInstance plant;
        private final java.util.function.Supplier<Float> random;
        private final java.util.function.DoubleSupplier randomDouble;
        private final java.util.function.IntSupplier matureDeviationRoll;
        private final double careProbabilitySample;
        private final Runnable seedRestored;
        private float water, nutrition, toxins;
        private float weeds, pests;
        private SoilTarget(PlantInstance plant, java.util.function.Supplier<Float> random, java.util.function.DoubleSupplier randomDouble, java.util.function.IntSupplier matureDeviationRoll, double careProbabilitySample, Runnable seedRestored) { this.plant=plant; this.random=random; this.randomDouble=randomDouble; this.matureDeviationRoll=matureDeviationRoll; this.careProbabilitySample=careProbabilitySample; this.seedRestored=seedRestored; water=plant.water(); nutrition=plant.nutrition(); toxins=plant.soilToxins(); weeds=plant.weeds(); pests=plant.pests(); }
        @Override public void adjustWater(float amount) { water += amount; if (amount > 0) toxins -= 4 * amount; }
        @Override public void adjustNutrition(float amount) { nutrition += amount; }
        @Override public void adjustToxins(float amount) { toxins += amount; }
        @Override public void adjustWeeds(float amount) { weeds += amount; }
        @Override public void adjustPests(float amount) { pests += amount; }
        @Override public void adjustHealth(float amount) {
            boolean wasAlive = hasLivingPlant();
            plant.adjustCareHealth(amount);
            if (wasAlive && plant.dead()) { weeds += 1; pests = 0; }
        }
        @Override public void adjustPotency(float amount) {
            var genetics = currentGenetics();
            if (genetics != null) plant.genetics(genetics.adjustPotency(amount));
        }
        @Override public void robustHarvest(com.juicyslew.moonstation14.component.codec.json.PlantMetabolismData.RobustHarvest effect) {
            var genetics = currentGenetics();
            if (genetics == null) return;
            float potency = genetics.profile().lifecycle().potency();
            int yield = genetics.profile().lifecycle().yield();
            if (potency < effect.potencyLimit()) plant.genetics(genetics.robustHarvest(
                    effect.potencyLimit(), effect.potencyIncrease(), effect.potencySeedlessThreshold()));
            else if (yield > 1 && nextDouble() < .1d) plant.genetics(genetics.robustHarvest(
                    effect.potencyLimit(), effect.potencyIncrease(), effect.potencySeedlessThreshold()));
        }
        @Override public void cryoxadone() { plant.cryoxadone(resolvedPlantProfile(plant), matureDeviationRoll); }
        @Override public void affectGrowth(int amount) { plant.affectGrowth(amount, resolvedPlantProfile(plant)); }
        @Override public void diethylamine() {
            var genetics = currentGenetics();
            if (genetics != null) {
                boolean lifespan = nextDouble() < .1d;
                boolean endurance = nextDouble() < .1d;
                plant.genetics(genetics.diethylamine(lifespan, endurance));
            }
        }
        @Override public void phalanximine() {
            var genetics = currentGenetics();
            if (genetics != null) plant.genetics(genetics.viable());
        }
        @Override public void restoreSeeds() {
            var genetics = currentGenetics();
            if (hasLivingPlant() && genetics != null && genetics.profile().lifecycle().seedless()) {
                plant.genetics(genetics.restoreSeeds());
                seedRestored.run();
            }
        }
        private GeneticValue currentGenetics() {
            if (plant.seed() == null) return null;
            return plant.genetics() != null ? plant.genetics()
                    : resolvedPlantProfile(plant) == null ? null : new GeneticValue(plant.seed(), resolvedPlantProfile(plant));
        }
        @Override public void adjustMutationLevel(float amount) { plant.adjustMutationLevel(amount * plant.mutationModifier()); }
        @Override public void adjustMutationModifier(float amount) { plant.adjustMutationModifier(amount); }
        @Override public boolean hasLivingPlant() { return plant.seed() != null && !plant.dead(); }
        @Override public boolean admitsMetabolismPoll() { return plant.mutationLevel() < 25f; }
        @Override public void beginNonemptyPoll() { plant.ensureUniqueSeed(); }
        @Override public float nextFloat() { return random.get(); }
        @Override public double nextDouble() { return randomDouble.getAsDouble(); }
        @Override public double probabilitySample() { return careProbabilitySample; }
        @Override public boolean hasChemicalMutationAdapter() { return true; }
        @Override public void prepareChemicalMutation(String tableId) {
            PlantChemicalMutation.prepare(tableId, PrototypeRuntime.serverWeightedRandomFillSolutions(), PrototypeRuntime.serverReagents());
        }
        @Override public boolean mutateChemicals(String tableId) {
            if (plant.seed() == null || plant.dead() || plant.genetics() == null) return true;
            var table = PlantChemicalMutation.prepare(tableId, PrototypeRuntime.serverWeightedRandomFillSolutions(), PrototypeRuntime.serverReagents());
            plant.genetics(PlantChemicalMutation.apply(plant.genetics(), table, random));
            return true;
        }
        private void finish() {
            plant.soil(PlantInstance.clamp(water,0,100), PlantInstance.clamp(nutrition,0,100), PlantInstance.clamp(weeds,0,10), PlantInstance.clamp(toxins,0,100));
            plant.pestLevel(PlantInstance.clamp(pests,0,10));
            SeedProfile profile = resolvedPlantProfile(plant);
            plant.clampCareHealth(profile == null ? 20f : profile.biology().endurance());
        }
    }
    private void publish(ServerLevel server) {
        projectAppearance();
        if (lastPublish == plant.revision() && publishedWarningBits == warningBits) return;
        lastPublish = plant.revision(); server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    private int publishedWarningBits;
    public static SeedProfile resolvedPlantProfile(PlantInstance instance) {
        if (instance == null || instance.seed() == null) return null;
        SeedProfile genetics = instance.effectiveProfile();
        return genetics != null ? genetics : ModSeedProfiles.serverLookup(instance.seed());
    }
    private void projectAppearance() {
        SeedProfile seed = resolvedPlantProfile(plant);
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
