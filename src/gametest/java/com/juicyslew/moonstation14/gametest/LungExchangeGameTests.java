package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.BreathExchange;
import com.juicyslew.moonstation14.ms14.organ.*;
import com.juicyslew.moonstation14.ms14.lung.LungSystem;
import com.juicyslew.moonstation14.ms14.lung.LungComponent;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LungExchangeGameTests {
    private static final double TEMPERATURE = 293.15;

    private LungExchangeGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "lung_breath_exchange", timeoutTicks = 80)
    public static void lateBodyReconcilesWithoutUnknownBreath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fixture = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(((fixture.getX() >> 4) + 80) * 16,
                fixture.getY(), ((fixture.getZ() >> 4) + 80) * 16);
        level.getChunk(origin);
        buildRoom(level, origin);
        BlockPos cell = origin.offset(2, 1, 2);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        Pig pig = EntityType.PIG.create(level);
        require(pig != null, "pig fixture required");
        pig.setNoAi(true);
        pig.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        level.addFreshEntity(pig);
        // Simulate a join before the prototype was published: the eligible actor has no body.
        pig.removeData(ModDataAttachments.BODY.get());
        int interval = LungSystem.intervalTicks(LungSystem.resolvePolicy(pig).orElseThrow().breathIntervalSeconds());
        long due = 0;
        while (!LungSystem.isDue(due, pig.getId(), interval)) due++;
        require(service.sample(level, BlockPos.containing(pig.getEyePosition())).isEmpty(),
                "room must still be unclassified");
        require(!LungSystem.tickIfDue(pig, due, service), "unknown strict sample cannot breathe");
        LungComponent attached = snapshot(pig);
        require(pig.hasData(ModDataAttachments.BODY.get()) && attached.initialized()
                        && attached.gasMoles().isEmpty() && attached.saturation() == 5
                        && attached.phase() == LungComponent.Phase.INHALING,
                "late policy must attach join-equivalent empty state without advancing it");
        require(noOverride(level, cell), "unknown sample cannot create room gas");
        AtmosphereService disabled = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        disabled.configureAtServerStart(false, Set.of(level.dimension()));
        require(!LungSystem.tickIfDue(pig, due, disabled)
                        && snapshot(pig).equals(attached),
                "disabled atmosphere cannot advance attached lung");
        var retained = LungComponent.from(new GasMixture(Map.of(GasType.NITROGEN, 0.2), TEMPERATURE),
                98, true, LungComponent.Phase.EXHALING);
        BodyState before = BodySystem.current(pig).orElseThrow();
        OrganInstance lung = before.find(OrganCategory.LUNGS).orElseThrow();
        BodyState saved = before.updateLung(lung.id(), LungOrganState.from(retained), ModOrgans.catalog(level))
                .updateRespiration(RespirationState.from(retained));
        pig.setData(ModDataAttachments.BODY.get(), new BodyAttachment(saved));
        require(!LungSystem.tickIfDue(pig, due, service), "unknown sample cannot exhale stored gas");
        LungComponent reconciled = snapshot(pig);
        require(reconciled.saturation() == 5 && reconciled.phase() == retained.phase()
                        && reconciled.gasMoles().equals(retained.gasMoles())
                        && reconciled.temperatureKelvin() == retained.temperatureKelvin()
                        && BodySystem.current(pig).orElseThrow().find(OrganCategory.LUNGS).orElseThrow().id().equals(lung.id()),
                "reconciliation must clamp saved BODY saturation without replacing organ identity, gas or phase");
        require(noOverride(level, cell), "unknown sample cannot exhale stored gas");
        classifyUntilSample(service, level, cell, 12);
        require(LungSystem.tickIfDue(pig, due, service), "strict sample permits the next due exhale");
        require(snapshot(pig).phase()
                        == LungComponent.Phase.INHALING, "valid exhale advances phase");
        OrganInstance detached = BodySystem.current(pig).orElseThrow().find(OrganCategory.LUNGS).orElseThrow();
        require(BodySystem.detach(pig, detached.id()).isPresent(), "detach lung fixture");
        BodyState withoutLung = BodySystem.current(pig).orElseThrow();
        require(LungSystem.tickIfDue(pig, due + interval, service), "known sample depletes mob without lungs");
        require(BodySystem.current(pig).orElseThrow().organs().isEmpty()
                        && BodySystem.current(pig).orElseThrow().respiration().saturation() == 1
                        && BodySystem.current(pig).orElseThrow().respiration().phase() == withoutLung.respiration().phase(),
                "missing lungs cannot inhale or advance phase but still deplete saturation");
        pig.removeData(ModDataAttachments.BODY.get());
        pig.setHealth(0);
        require(!LungSystem.tickIfDue(pig, due + interval, service)
                        && !pig.hasData(ModDataAttachments.BODY.get()),
                "dead actor must never receive a new lung");
        pig.discard();
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "lung_breath_exchange", timeoutTicks = 80)
    public static void actualInhaleAloneAppliesConfiguredTypedToxins(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fixture = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(((fixture.getX() >> 4) + 64) * 16,
                fixture.getY(), ((fixture.getZ() >> 4) + 64) * 16);
        level.getChunk(origin);
        buildRoom(level, origin);
        BlockPos cell = origin.offset(2, 1, 2);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        Villager actor = EntityType.VILLAGER.create(level);
        require(actor != null, "villager fixture required");
        actor.setNoAi(true);
        actor.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        level.addFreshEntity(actor);
        BlockPos breathCell = BlockPos.containing(actor.getEyePosition());
        require(LungSystem.reconcile(actor), "human lung must be enrolled");
        require(com.juicyslew.moonstation14.ms14.organ.ModOrgans.catalog(level)
                .get(net.minecraft.resources.ResourceLocation.parse("moonstation14:organ_lungs_human"))
                .lung().orElseThrow().toxicGasDamagePerMole().containsKey("plasma"),
                "human policy must include plasma");
        int interval = LungSystem.intervalTicks(LungSystem.resolvePolicy(actor).orElseThrow().breathIntervalSeconds());
        long due = 0;
        while (!LungSystem.isDue(due, actor.getId(), interval)) due++;
        require(!LungSystem.tickIfDue(actor, due, service), "unclassified sample refuses inhale");
        require(typed(actor, "poison") == 0 && typed(actor, "radiation") == 0,
                "unknown sample cannot cause toxins");
        classifyUntilSample(service, level, breathCell, 12);
        require(LungSystem.tickIfDue(actor, due, service), "proven vacuum inhale succeeds");
        require(typed(actor, "poison") == 0 && typed(actor, "radiation") == 0,
                "vacuum cannot cause toxins");
        require(LungSystem.tickIfDue(actor, due + interval, service), "vacuum exhale succeeds");
        require(service.addGas(level, breathCell, GasType.PLASMA, 1, TEMPERATURE), "add plasma");
        require(service.addGas(level, breathCell, GasType.TRITIUM, 1, TEMPERATURE), "add tritium");
        require(service.addGas(level, breathCell, GasType.NITROGEN, 2, TEMPERATURE), "add inert nitrogen");
        AtmosphereService disabled = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        disabled.configureAtServerStart(false, Set.of(level.dimension()));
        require(!LungSystem.tickIfDue(actor, due + 2L * interval, disabled), "disabled atmosphere refuses inhale");
        require(typed(actor, "poison") == 0 && typed(actor, "radiation") == 0,
                "disabled atmosphere cannot cause toxins");
        GasMixture source = service.sample(level, breathCell).orElseThrow();
        double breathMoles = source.totalMoles() * 0.0005;
        double poisonExpected = source.moles(GasType.PLASMA) * breathMoles / source.totalMoles();
        double radiationExpected = source.moles(GasType.TRITIUM) * breathMoles / source.totalMoles();
        double poisonBefore = typed(actor, "poison");
        double radiationBefore = typed(actor, "radiation");
        require(LungSystem.tickIfDue(actor, due + 2L * interval, service), "mixed inhale succeeds");
        double plasmaStored = snapshot(actor).mixture().moles(GasType.PLASMA);
        require(close(typed(actor, "poison") - poisonBefore, poisonExpected),
                "plasma poison uses inhaled moles: expected " + poisonExpected + " actual " + (typed(actor, "poison") - poisonBefore)
                        + " stored plasma " + plasmaStored);
        require(close(typed(actor, "radiation") - radiationBefore, radiationExpected),
                "tritium radiation uses inhaled moles: expected " + radiationExpected + " actual " + (typed(actor, "radiation") - radiationBefore));
        double poisonAfter = typed(actor, "poison");
        double radiationAfter = typed(actor, "radiation");
        require(LungSystem.tickIfDue(actor, due + 3L * interval, service), "exhale succeeds");
        require(close(typed(actor, "poison"), poisonAfter) && close(typed(actor, "radiation"), radiationAfter),
                "exhale and retained room gas do not reapply toxins");
        actor.discard();
        helper.succeed();
    }

    private static LungComponent snapshot(net.minecraft.world.entity.LivingEntity entity) {
        BodyState body = BodySystem.current(entity).orElseThrow();
        LungOrganState lung = body.find(OrganCategory.LUNGS).orElseThrow().lung();
        RespirationState respiration = body.respiration();
        return new LungComponent(lung.gasMoles(), lung.temperatureKelvin(), respiration.saturation(),
                respiration.initialized(), respiration.phase());
    }

    private static float typed(Villager actor, String key) {
        DamageData stored = actor.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        return stored == null ? 0f : stored.getMap().getOrDefault(key, 0f);
    }

    @GameTest(template = "atmos_large_empty", batch = "lung_breath_exchange", timeoutTicks = 80)
    public static void zeroKelvinStrictInhaleLeavesRoomAndBodyUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fixture = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(((fixture.getX() >> 4) + 96) * 16,
                fixture.getY(), ((fixture.getZ() >> 4) + 96) * 16);
        level.getChunk(origin);
        buildRoom(level, origin);
        BlockPos cell = origin.offset(2, 1, 2);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        classifyUntilSample(service, level, cell, 12);
        require(service.addGas(level, cell, GasType.OXYGEN, 1, 0), "fixture needs nonzero gas at zero kelvin");
        Pig pig = EntityType.PIG.create(level);
        require(pig != null, "pig fixture required");
        pig.setNoAi(true);
        pig.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        level.addFreshEntity(pig);
        require(LungSystem.reconcile(pig), "pig must have initialized BODY");
        BlockPos eye = BlockPos.containing(pig.getEyePosition());
        GasMixture beforeRoom = service.sample(level, eye).orElseThrow();
        require(beforeRoom.totalMoles() > 0 && beforeRoom.temperatureKelvin() == 0,
                "strict sample must be nonzero gas at zero kelvin");
        BodyState beforeBody = BodySystem.current(pig).orElseThrow();
        int interval = LungSystem.intervalTicks(LungSystem.resolvePolicy(pig).orElseThrow().breathIntervalSeconds());
        long due = 0;
        while (!LungSystem.isDue(due, pig.getId(), interval)) due++;
        require(!LungSystem.tickIfDue(pig, due, service), "invalid strict inhale must fail without throwing");
        require(beforeBody.equals(BodySystem.current(pig).orElseThrow()), "invalid inhale must preserve BODY and phase");
        require(sameMixture(beforeRoom, service.sample(level, eye).orElseThrow()),
                "invalid inhale must preserve room gas");
        pig.removeData(ModDataAttachments.BODY.get());
        require(!LungSystem.tickIfDue(pig, due, service) && !pig.hasData(ModDataAttachments.BODY.get()),
                "invalid sample must not initialize a missing BODY either");
        require(sameMixture(beforeRoom, service.sample(level, eye).orElseThrow()),
                "invalid sample must not change room during late reconciliation");
        pig.discard();
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "lung_breath_exchange", timeoutTicks = 80)
    public static void isolatedWorldTickAlternatesAndReturnsOnlyStoredGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fixture = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(((fixture.getX() >> 4) + 48) * 16,
                fixture.getY(), ((fixture.getZ() >> 4) + 48) * 16);
        level.getChunk(origin);
        buildRoom(level, origin);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos cell = origin.offset(2, 1, 2);
        classifyUntilSample(service, level, cell, 12);
        Pig pig = EntityType.PIG.create(level);
        require(pig != null, "pig fixture required");
        pig.setNoAi(true);
        pig.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        level.addFreshEntity(pig);
        require(LungSystem.reconcile(pig), "prototype lung must be enrolled");
        int interval = LungSystem.intervalTicks(LungSystem.resolvePolicy(pig).orElseThrow().breathIntervalSeconds());
        long due = 0;
        while (!LungSystem.isDue(due, pig.getId(), interval)) due++;
        require(LungSystem.tickIfDue(pig, due, service), "proven vacuum is a valid inhale");
        LungComponent inhaledVacuum = snapshot(pig);
        require(inhaledVacuum.phase() == LungComponent.Phase.EXHALING && inhaledVacuum.gasMoles().isEmpty(),
                "vacuum inhale advances only phase");
        require(inhaledVacuum.saturation() == 3, "vacuum must deplete saturation by two");
        require(service.addBreathableAir(level, cell, 5, TEMPERATURE), "finite room must receive gas");
        GasMixture beforeExhale = service.sample(level, cell).orElseThrow();
        require(LungSystem.tickIfDue(pig, due + interval, service), "empty exhale succeeds");
        require(sameMixture(beforeExhale, service.sample(level, cell).orElseThrow()),
                "empty exhale does not inhale or change room");
        require(LungSystem.tickIfDue(pig, due + 2L * interval, service), "next phase inhales real room gas");
        LungComponent full = snapshot(pig);
        require(full.phase() == LungComponent.Phase.EXHALING && !full.gasMoles().isEmpty(),
                "inhale fills lung without exhaling");
        require(close(full.mixture().totalMoles(), beforeExhale.totalMoles() * 0.0005),
                "half-liter inhale requests only the strict cell's volume-equivalent moles");
        GasMixture beforeReturn = service.sample(level, cell).orElseThrow();
        require(LungSystem.tickIfDue(pig, due + 3L * interval, service), "stored gas is exhaled");
        GasMixture afterReturn = service.sample(level, cell).orElseThrow();
        for (GasType type : GasType.values())
            require(close(afterReturn.moles(type), beforeReturn.moles(type) + full.mixture().moles(type)),
                    "exhale returns exactly stored " + type);
        require(snapshot(pig).gasMoles().isEmpty(),
                "successful exhale clears lung inventory");
        pig.discard();
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "lung_breath_exchange", timeoutTicks = 80)
    public static void serviceExchangeIsAtomicAndConservesFiniteCellGasAndEnergy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fixtureAnchor = helper.absolutePos(BlockPos.ZERO);
        BlockPos roomOrigin = new BlockPos(
                ((fixtureAnchor.getX() >> 4) + 32) * 16,
                fixtureAnchor.getY(),
                ((fixtureAnchor.getZ() >> 4) + 32) * 16);
        level.getChunk(roomOrigin);
        buildRoom(level, roomOrigin);
        // This is a private enabled policy. It neither enables nor reconfigures INSTANCE.
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos cell = roomOrigin.offset(2, 1, 2);

        require(service.isEnabled(), "the isolated service must be enabled");
        require(service.exchangeBreath(level, cell, 2.0, GasMixture.vacuum()).isEmpty(),
                "unknown ownership must reject an exchange before changing the cell");
        require(noOverride(level, cell), "unknown exchange must not materialize a gas override");

        GasMixture initial = classifyUntilSample(service, level, cell, 12);
        require(initial.totalMoles() == 0.0, "sealed finite room must classify initially as vacuum");
        require(noOverride(level, cell), "classified vacuum has no stored override");
        GasMixture rejectedExhale = new GasMixture(Map.of(GasType.OXYGEN, 1d), TEMPERATURE);
        require(service.exchangeBreath(level, cell, 0, rejectedExhale, inhaled -> false).isEmpty(),
                "invalid candidate must reject exhale before write");
        require(noOverride(level, cell), "rejected exhale cannot create a room override");
        require(service.addBreathableAir(level, cell, 12.0, TEMPERATURE),
                "finite source gas must be installed in the claimed cell");
        GasMixture before = service.sample(level, cell).orElseThrow();
        GasMixture exhaled = new GasMixture(Map.of(
                GasType.OXYGEN, 0.35,
                GasType.CARBON_DIOXIDE, 0.55,
                GasType.WATER_VAPOR, 0.10), 305.0);

        require(service.exchangeBreath(level, cell, 3.0, exhaled, inhaled -> false).isEmpty(),
                "invalid candidate must reject the entire exchange");
        require(sameMixture(before, service.sample(level, cell).orElseThrow()),
                "rejected candidate must leave finite source unchanged");

        int[] validations = {0};
        BreathExchange exchange = service.exchangeBreath(level, cell, 3.0, exhaled, inhaled -> {
            validations[0]++;
            return inhaled.totalMoles() <= 3.0;
        }).orElseThrow(
                () -> new GameTestAssertException("finite claimed source cell must accept breath exchange"));
        require(validations[0] == 1, "successful candidate must be validated exactly once");
        GasMixture after = service.sample(level, cell).orElseThrow();
        require(sameMixture(exchange.roomAfter(), after),
                "the finite cell must contain exactly the exchange result after its atomic write");
        require(Math.abs(exchange.inhaled().totalMoles() - 3.0) < 1.0e-9,
                "inhaled gas must be capped at and match the requested sample amount");
        for (GasType type : GasType.values()) {
            require(close(exchange.inhaled().moles(type),
                            before.moles(type) * 3.0 / before.totalMoles()),
                    "inhaled " + type + " must match the proportional source sample");
            require(close(before.moles(type) + exhaled.moles(type),
                            exchange.inhaled().moles(type) + after.moles(type)),
                    "finite room plus inhaled portion must conserve " + type);
        }
        require(close(before.thermalEnergy() + exhaled.thermalEnergy(),
                        exchange.inhaled().thermalEnergy() + after.thermalEnergy()),
                "finite room plus inhaled portion must conserve thermal energy");

        // A rejected request exercises the public API's no-partial-write failure path.
        GasMixture beforeRejected = service.sample(level, cell).orElseThrow();
        require(service.exchangeBreath(level, cell, Double.NaN, exhaled).isEmpty(),
                "non-finite inhale request must fail");
        require(sameMixture(beforeRejected, service.sample(level, cell).orElseThrow()),
                "failed candidate must leave the finite source exactly unchanged");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "lung_breath_exchange", timeoutTicks = 40)
    public static void exteriorAndInvalidCellsDoNotCreateFiniteOverrides(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos skyCell = helper.absolutePos(new BlockPos(2, 100, 2));
        int firstFreeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, skyCell.getX(), skyCell.getZ());
        if (firstFreeY > skyCell.getY()) skyCell = new BlockPos(skyCell.getX(), firstFreeY + 1, skyCell.getZ());
        require(skyCell.getY() < level.getMaxBuildHeight(), "fixture must provide an in-bounds sky cell");
        LevelChunk skyChunk = (LevelChunk) level.getChunk(skyCell);
        require(skyChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get()) == null,
                "sky fixture must begin without atmosphere chunk data");
        GasMixture skyAmbient = service.sample(level, skyCell).orElseThrow(
                () -> new GameTestAssertException("sky cell should be proven exterior"));
        var exteriorExchange = service.exchangeBreath(level, skyCell, 2.0, GasMixture.vacuum()).orElseThrow(
                () -> new GameTestAssertException("exterior should supply an ambient breath"));
        double expectedInhaled = skyAmbient.totalMoles() == 0.0 ? 0.0 : 2.0;
        require(close(exteriorExchange.inhaled().totalMoles(), expectedInhaled),
                "exterior exchange should return ambient composition; expected moles=" + expectedInhaled
                        + ", actual=" + exteriorExchange.inhaled().totalMoles());
        require(sameMixture(skyAmbient, service.sample(level, skyCell).orElseThrow()),
                "exterior exchange must not change ambient composition or energy");
        require(skyChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get()) == null,
                "exterior exchange must not materialize a finite chunk override");
        require(service.exchangeBreath(level, skyCell, 2.0, GasMixture.vacuum(), inhaled -> false).isEmpty(),
                "exterior candidate rejection must fail without writing");
        require(skyChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get()) == null,
                "rejected exterior exchange cannot materialize finite data");

        BlockPos solid = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        require(service.exchangeBreath(level, solid, 2.0, GasMixture.vacuum()).isEmpty(),
                "solid cells must reject exchange");
        require(noOverride(level, solid), "solid-cell failure must not create an override");

        BlockPos unloaded = new BlockPos((skyChunk.getPos().x + 32) * 16, 100,
                (skyChunk.getPos().z + 32) * 16);
        ChunkAccess notLoaded = level.getChunkSource().getChunk(unloaded.getX() >> 4,
                unloaded.getZ() >> 4, ChunkStatus.FULL, false);
        require(notLoaded == null, "unloaded-cell fixture must remain unloaded");
        require(service.exchangeBreath(level, unloaded, 2.0, GasMixture.vacuum()).isEmpty(),
                "unloaded cells must reject exchange without loading the chunk");
        helper.succeed();
    }

    private static void buildRoom(ServerLevel level, BlockPos origin) {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                level.setBlock(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(origin.offset(x, 3, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int i = 1; i <= 5; i++) {
                level.setBlock(origin.offset(1, y, i), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(origin.offset(5, y, i), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(origin.offset(i, y, 1), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(origin.offset(i, y, 5), Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    private static GasMixture classifyUntilSample(AtmosphereService service, ServerLevel level,
                                                   BlockPos pos, int maxPasses) {
        for (int pass = 1; pass <= maxPasses; pass++) {
            service.tick(level, (long) pass * AtmosphereService.TICK_CADENCE);
            var sample = service.sample(level, pos);
            if (sample.isPresent()) return sample.get();
        }
        throw new GameTestAssertException("finite cell ownership did not classify within " + maxPasses + " passes");
    }

    private static boolean noOverride(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = (LevelChunk) level.getChunk(pos);
        var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        return data == null || data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15) == null;
    }

    private static boolean sameMixture(GasMixture left, GasMixture right) {
        if (left.temperatureKelvin() != right.temperatureKelvin()) return false;
        for (GasType type : GasType.values())
            if (left.moles(type) != right.moles(type)) return false;
        return true;
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) <= 1.0e-9 * Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
