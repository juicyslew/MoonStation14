package com.juicyslew.moonstation14.ms14.disposal.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ExposureAirRouting;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlock;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.lung.LungSystem;
import com.juicyslew.moonstation14.ms14.lung.LungToxicity;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.organ.ModOrgans;
import com.juicyslew.moonstation14.ms14.organ.OrganCategory;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "atmosphere", "power", "character", "containment", "physiology", "hands"})
@PrefixGameTestTemplate(false)
public final class DisposalFixtureCharacterTourGameTests {
    private static final BlockPos FLOOR_REF = new BlockPos(7, 0, 9);
    private static final BlockPos UNIT_RELATIVE = new BlockPos(3, 2, 8);
    private DisposalFixtureCharacterTourGameTests() { }

    @GameTest(batch = "disposals_camera_tour", template = "disposal_clear", templateNamespace = "moonstation14_camera_tour", timeoutTicks = 1150)
    public static void boundHumanRidesWholePoweredDisposalTourWithFiniteAir(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        AtmosphereService.INSTANCE.configureAtServerStart(Set.of(level.dimension()), AtmosphereService.INSTANCE.spaceDimensionsForTest());
        BlockPos floorRef = helper.absolutePos(FLOOR_REF);
        BlockPos origin = floorRef.offset(-6, 0, -8);
        String result = DisposalFixtureCommands.build(level, floorRef);
        require(result.startsWith("Built from floor reference"), "fixture build: " + result);
        var plan = DisposalFixtureTourPlan.resolve(PrototypeRuntime.serverDisposals());
        require(plan.nodes().size() == 259, "resolved tour must contain all planned nodes");
        BlockPos unitPos = origin.offset(UNIT_RELATIVE);
        awaitReady(helper, level, origin, floorRef, unitPos, plan, 0);
    }

    private static void awaitReady(GameTestHelper helper, ServerLevel level, BlockPos origin, BlockPos floorRef,
                                   BlockPos unitPos, DisposalFixtureTourPlan.Plan plan, int waited) {
        var unit = level.getBlockEntity(unitPos) instanceof DisposalUnitBlockEntity found ? found : null;
        if (unit == null || !level.getBlockState(unitPos).getValue(DisposalUnitBlock.POWERED)
                || level.getBlockState(unitPos).getValue(DisposalUnitBlock.VISUAL_STATE)
                != DisposalUnitBlock.VisualState.READY) {
            if (waited >= 480) {
                var meter = unit == null ? "missing-unit" : com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.disposalUnitMeter(level, unit);
                boolean lampLit = level.getBlockState(origin.offset(DisposalFixtureCommands.LAMP)).hasProperty(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT)
                        && level.getBlockState(origin.offset(DisposalFixtureCommands.LAMP)).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT);
                fail("real disposal unit did not become powered READY; meter1000W=" + meter + ", lampLit=" + lampLit + ", unitState=" + level.getBlockState(unitPos)
                        + ", timing=" + (unit == null ? "missing" : unit.timingSnapshot()) + ", solve=" + com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.lastSolveDiagnostic(level));
            }
            helper.runAtTickTime(waited + 1, () -> awaitReady(helper, level, origin, floorRef, unitPos, plan,
                    waited + 1));
            return;
        }
        PlayerCharacterHarnessEntity body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(7, 1, 9));
        CompoundTag saved = new CompoundTag();
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID()); binding.putString("Profile", "main");
        binding.putUUID("Mind", UUID.randomUUID());
        saved.put("Moonstation14PlayerCharacterBinding", binding);
        body.readAdditionalSaveData(saved);
        require(body.playerCharacterBinding() != null
                        && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID)
                        && BodySystem.reconcile(body) && LungSystem.reconcile(body),
                "spawned human harness must be bound, enrolled and reconciled");
        require(BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED,
                "enrolled human harness must initialize hands through the normal body-hand bootstrap");
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(hands != null, "human body must have initialized live hands");
        UUID id = body.getUUID();
        PlayerCharacterBinding originalBinding = body.playerCharacterBinding();
        float health = body.getHealth();
        var initialBody = BodySystem.current(body).orElseThrow();
        var initialDamage = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        var initialDamageMap = initialDamage == null ? java.util.Map.<String, Float>of() : java.util.Map.copyOf(initialDamage.getMap());
        var lungInstance = initialBody.find(OrganCategory.LUNGS).orElseThrow();
        var lungPolicy = ModOrgans.catalog(body.level()).get(lungInstance.prototype()).lung().orElseThrow();
        require(unit.insertBody(body), "actual trusted unit insertion must accept bound human body");
        unit.engage();
        var visited = new HashSet<BlockPos>();
        var types = new HashSet<String>();
        var holderAirSeen = new boolean[1];
        int firstBodyTick = body.tickCount;
        helper.runAfterDelay(1, () -> poll(helper, level, origin, floorRef, unitPos, plan, body, id,
                originalBinding, hands, health, initialDamageMap, lungPolicy,
                visited, types, holderAirSeen, new double[]{Double.NaN, Double.NaN}, firstBodyTick,
                new com.juicyslew.moonstation14.ms14.lung.LungComponent.Phase[1], new boolean[1], new String[1],
                new BreathAccounting(), 1, 0));
    }

    private static void poll(GameTestHelper helper, ServerLevel level, BlockPos origin, BlockPos floorRef,
                             BlockPos unitPos, DisposalFixtureTourPlan.Plan plan, PlayerCharacterHarnessEntity body,
                               UUID id, PlayerCharacterBinding binding, LiveHands hands, float health,
                               java.util.Map<String, Float> initialDamageMap, com.juicyslew.moonstation14.ms14.organ.OrganData.Lung lungPolicy,
                                Set<BlockPos> visited, Set<String> types, boolean[] holderAirSeen, double[] holderOxygen, int firstBodyTick,
                                 com.juicyslew.moonstation14.ms14.lung.LungComponent.Phase[] previousPhase,
                                 boolean[] realInhaleSeen, String[] breathDiagnostic, BreathAccounting accounting, int ticks, int elapsed) {
        var holders = level.getEntitiesOfClass(DisposalTransitHolder.class,
                new AABB(origin.getX()-2, origin.getY()-1, origin.getZ()-2,
                        origin.getX()+34, origin.getY()+8, origin.getZ()+32), h -> h.bodyIds().contains(id));
        for (var holder : holders) {
            BlockPos pos = holder.currentPos().subtract(origin);
            visited.add(pos.immutable());
            plan.nodes().stream().filter(node -> node.position().equals(pos))
                    .findFirst().ifPresent(node -> types.add(node.prototypeId().toString()));
            holderAirSeen[0] |= holder.finiteAir().flatMap(a -> a.sample())
                    .map(a -> a.mixture().totalMoles() > 0 && Double.isFinite(a.mixture().pressureKpa(1))).orElse(false);
            var selectedAir = ExposureAirRouting.select(body);
            require(selectedAir.contained() && selectedAir.source().isPresent(),
                    "bound human selects actual containment-owner exposure source");
             var holderSample = holder.finiteAir().flatMap(a -> a.sample()).orElseThrow();
            var selectedSample = selectedAir.source().flatMap(source -> source.sample()).orElseThrow();
            require(selectedSample.mixture().equals(holderSample.mixture()), "selected exposure sample is actual finite holder air");
            double oxygen = selectedSample.mixture().gasMoles().getOrDefault(GasType.OXYGEN, 0.0);
            var respiration = BodySystem.current(body).orElseThrow().respiration();
            if (accounting.previousGas != null) require(accounting.previousHolder.equals(holder.getUUID()),
                    "paired gas samples belong to the same transit holder");
            if (previousPhase[0] == com.juicyslew.moonstation14.ms14.lung.LungComponent.Phase.INHALING
                    && respiration.phase() == com.juicyslew.moonstation14.ms14.lung.LungComponent.Phase.EXHALING) {
                breathDiagnostic[0] = "previousPhase=" + previousPhase[0] + ", currentPhase=" + respiration.phase()
                        + ", previousO2=" + holderOxygen[1] + " mol, currentO2=" + oxygen + " mol";
                if (Double.isFinite(holderOxygen[1]) && Double.isFinite(oxygen) && oxygen < holderOxygen[1]) {
                    realInhaleSeen[0] = true;
                    var previous = accounting.previousGas;
                    var withdrawn = new java.util.EnumMap<GasType, Double>(GasType.class);
                    for (var gas : GasType.values()) {
                        double amount = Math.max(0d, previous.moles(gas) - selectedSample.mixture().moles(gas));
                        if (amount > 0d) withdrawn.put(gas, amount);
                    }
                    var inhaled = new GasMixture(withdrawn, previous.temperatureKelvin());
                    var toxicDamage = LungToxicity.perInhale(inhaled, lungPolicy.toxicGasDamagePerMole(),
                            lungPolicy.toxicGasDamageCapPerInhale()).orElseThrow();
                    accounting.expectedPoison += toxicDamage.getOrDefault(DamageKeys.POISON, 0f);
                }
            }
            accounting.previousGas = selectedSample.mixture();
            accounting.previousHolder = holder.getUUID();
            holderOxygen[0] = holderOxygen[1]; holderOxygen[1] = oxygen;
            previousPhase[0] = respiration.phase();
            var owner = BodyContainmentService.forLevel(level).ownerOf(body).orElseThrow();
            require(!owner.view().cameraCollision() && owner.view().worldTarget().isEmpty(),
                    "transit owner has noncolliding server view and no world target");
        }
        var currentDamage = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        var currentDamageMap = currentDamage == null ? java.util.Map.<String, Float>of() : java.util.Map.copyOf(currentDamage.getMap());
        var delta = damageDelta(initialDamageMap, currentDamageMap);
        if (delta.entrySet().stream().anyMatch(e -> !e.getKey().equals(DamageKeys.POISON) && e.getValue() != 0f)
                || delta.getOrDefault(DamageKeys.POISON, 0f) < 0f
                || delta.getOrDefault(DamageKeys.POISON, 0f) > accounting.expectedPoison + poisonFloatTolerance(accounting.expectedPoison)) {
            var respiration = BodySystem.current(body).map(b -> b.respiration()).orElse(null);
            var transitPos = holders.stream().findFirst().map(h -> h.currentPos().subtract(origin)).orElse(null);
            fail("body health/damage changed beyond inhaled-lung poison allowance: initialHealth=" + health
                    + ", currentHealth=" + body.getHealth() + ", alive=" + body.isAlive()
                    + ", initialNativeDamage=" + initialDamageMap + ", currentNativeDamage=" + currentDamageMap
                    + ", damageDelta=" + delta + ", expectedPoison=" + accounting.expectedPoison
                    + ", transitPos=" + transitPos + ", bodyTick=" + body.tickCount
                    + ", saturation=" + (respiration == null ? "unavailable" : respiration.saturation())
                    + ", lungPhase=" + (respiration == null ? "unavailable" : respiration.phase())
                    + ", selectedFiniteAir=" + ExposureAirRouting.select(body).source().flatMap(s -> s.sample())
                        .map(s -> "pressure=" + s.pressureKpa() + ",moles=" + s.mixture().totalMoles()
                                + ",O2=" + s.mixture().gasMoles().getOrDefault(GasType.OXYGEN, 0.0))
                        .orElse("unavailable"));
        }
        if (BodyContainmentService.forLevel(level).ownerOf(body).isEmpty()) {
            require(visited.size() == 259, "all 259 planned positions visited; observed " + visited.size());
            require(types.containsAll(plan.coveredTypes().stream().map(Object::toString).toList()),
                    "all resolved tube prototype IDs observed: " + types);
            require(holderAirSeen[0], "transit holder carried actual nonempty finite sampled air");
             require(body.isAlive(), "human body remains alive (finite CO2 rebreathing may cause typed poison damage)");
            var finalDamage = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
             var finalDamageMap = finalDamage == null ? java.util.Map.<String, Float>of() : java.util.Map.copyOf(finalDamage.getMap());
             var finalDelta = damageDelta(initialDamageMap, finalDamageMap);
             require(finalDelta.entrySet().stream().allMatch(e -> e.getKey().equals(DamageKeys.POISON) && e.getValue() >= 0f)
                             && finalDelta.getOrDefault(DamageKeys.POISON, 0f) <= accounting.expectedPoison + poisonFloatTolerance(accounting.expectedPoison),
                     "no non-poison damage; poison is bounded by measured inhalation and resolved lung policy");
            require(realInhaleSeen[0], "observed real INHALING->EXHALING holder O2 withdrawal; last transition: "
                    + breathDiagnostic[0] + ", lastSamples=" + holderOxygen[0] + " -> " + holderOxygen[1]
                    + " mol; first/finalBodyTick=" + firstBodyTick + "/" + body.tickCount
                    + ", sleeping=" + body.isSleeping() + ", phase=" + BodySystem.current(body).orElseThrow().respiration().phase()
                    + ", respInitialized=" + BodySystem.current(body).orElseThrow().respiration().initialized()
                    + ", saturation=" + BodySystem.current(body).orElseThrow().respiration().saturation()
                    + ", policy=" + CharacterIdentitySystem.resolveForActor(body)
                        .flatMap(c -> c.component(RespiratorComponent.class))
                        .map(r -> "present/" + r.policy().breathIntervalSeconds() + "s").orElse("missing")
                    + ", selectedAir=" + ExposureAirRouting.select(body).source().flatMap(s -> s.sample())
                        .map(s -> s.volumeCubicMeters() + "m3/" + s.pressureKpa() + "kPa/" + s.mixture().totalMoles() + "mol")
                        .orElse("missing")
                    + ", lungCount=" + BodySystem.current(body).orElseThrow().organs().stream()
                        .filter(o -> o.category() == com.juicyslew.moonstation14.ms14.organ.OrganCategory.LUNGS).count()
                     + ", lungMoles=" + BodySystem.current(body).orElseThrow().find(com.juicyslew.moonstation14.ms14.organ.OrganCategory.LUNGS)
                         .map(o -> o.lung().gasMoles().values().stream().mapToDouble(Double::doubleValue).sum()).orElse(-1d));
            require(body.getUUID().equals(id) && body.playerCharacterBinding().equals(binding),
                    "same body UUID and binding survive transit");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands,
                    "same hands inventory survives transit");
            var air = AtmosphereService.INSTANCE.readAtmosphere(level, BlockPos.containing(body.getEyePosition()));
            require(air.isPresent() && Double.isFinite(air.orElseThrow().mixture().pressureKpa(1))
                            && air.orElseThrow().mixture().gasMoles().getOrDefault(GasType.OXYGEN, 0.0) > 0,
                    "safe outlet human eye cell reads finite healthy room air");
            BlockPos eyeCell = BlockPos.containing(body.getEyePosition());
            require(eyeCell.getX() >= origin.getX()+2 && eyeCell.getX() <= origin.getX()+7
                            && eyeCell.getY() >= origin.getY()+1 && eyeCell.getY() <= origin.getY()+4
                            && eyeCell.getZ() >= origin.getZ()+7 && eyeCell.getZ() <= origin.getZ()+10,
                    "uncontained human returned inside original room at safe outlet");
            helper.succeed(); return;
        }
        int rideBudgetTicks = (int) Math.ceil(plan.estimatedRideSeconds() * 20.0) + 160;
        if (elapsed >= rideBudgetTicks) {
            fail("human tour exceeded resolved duration budget " + rideBudgetTicks + " ticks; visited=" + visited.size());
        }
        helper.runAfterDelay(1, () -> poll(helper, level, origin, floorRef, unitPos, plan, body, id,
                binding, hands, health, initialDamageMap, lungPolicy, visited, types, holderAirSeen,
                holderOxygen, firstBodyTick, previousPhase, realInhaleSeen, breathDiagnostic, accounting, ticks + 1, elapsed + 1));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
    private static void fail(String message) {
        throw new GameTestAssertException(message);
    }

    private static java.util.Map<String, Float> damageDelta(java.util.Map<String, Float> initial,
                                                             java.util.Map<String, Float> current) {
        var result = new java.util.TreeMap<String, Float>();
        var keys = new java.util.TreeSet<String>(); keys.addAll(initial.keySet()); keys.addAll(current.keySet());
        for (String key : keys) {
            float delta = current.getOrDefault(key, 0f) - initial.getOrDefault(key, 0f);
            if (delta != 0f) result.put(key, delta);
        }
        return result;
    }

    private static double poisonFloatTolerance(double expected) {
        float value = (float) expected;
        return 32d * Math.ulp(value);
    }

    private static final class BreathAccounting {
        private GasMixture previousGas;
        private UUID previousHolder;
        private double expectedPoison;
    }
}
