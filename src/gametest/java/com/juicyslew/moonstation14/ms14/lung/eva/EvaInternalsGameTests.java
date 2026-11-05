package com.juicyslew.moonstation14.ms14.lung.eva;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.actions.BodyActionRequest;
import com.juicyslew.moonstation14.ms14.actions.BodyActionService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Authenticated native coverage for carried EVA internals equipment. */
@PrefixGameTestTemplate(false)
@GameTestTags("eva_internals")
public final class EvaInternalsGameTests {
    private EvaInternalsGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 400)
    public static void equippedInternalsActionDrivesScheduledFiniteTankDraw(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "evainternals");
        cleanupOnTerminal(helper, fixture);
        helper.runAtTickTime(2, () -> setupAndToggle(helper, fixture, level));
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 800)
    @GameTestTags("eva_internals")
    public static void emptyTankStaysSelectedUntilMaskToolIsLowered(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "evadeplete");
        cleanupOnTerminal(helper, fixture);
        helper.runAtTickTime(2, () -> setupDepletion(helper, fixture, level));
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 400)
    @GameTestTags("eva_protection")
    public static void evaPressureProtectionRequiresHelmetAndDoesNotReplaceAmbientAir(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "evapressure");
        cleanupOnTerminal(helper, fixture);
        helper.runAtTickTime(2, () -> setupPressureProtection(helper, fixture, level));
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 400)
    @GameTestTags("eva_protection")
    public static void wornEvaGearSlowsNormalHotAtmosphereBodyHeating(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture protectedFixture = RegisteredServerPlayerFixture.register(level, "evahotgear");
        RegisteredServerPlayerFixture unprotectedFixture = RegisteredServerPlayerFixture.register(level, "evahotbare");
        cleanupOnTerminal(helper, protectedFixture);
        cleanupOnTerminal(helper, unprotectedFixture);
        helper.runAtTickTime(2, () -> setupHotThermalComparison(helper, level, protectedFixture, unprotectedFixture));
    }

    private static void setupHotThermalComparison(GameTestHelper helper,
            net.minecraft.server.level.ServerLevel level, RegisteredServerPlayerFixture protectedFixture,
            RegisteredServerPlayerFixture unprotectedFixture) {
        try {
            var protectedActive = RegisteredPlayerReadyDriver.readyAndQuery(protectedFixture);
            var unprotectedActive = RegisteredPlayerReadyDriver.readyAndQuery(unprotectedFixture);
            var protectedBody = protectedActive.body();
            var unprotectedBody = unprotectedActive.body();
            var authority = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                    .resolve(protectedFixture.player()).orElseThrow();
            equipRegisteredItem(protectedBody, protectedFixture.player(), authority, level, "eva_helmet");
            equipRegisteredItem(protectedBody, protectedFixture.player(), authority, level, "eva_suit");

            var protectedCell = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
            var unprotectedCell = helper.absolutePos(new net.minecraft.core.BlockPos(7, 1, 2));
            com.juicyslew.moonstation14.gametest.AtmosphereRoomGameTests.buildSealedHotspotCell(helper, 2, 2);
            com.juicyslew.moonstation14.gametest.AtmosphereRoomGameTests.buildSealedHotspotCell(helper, 7, 2);
            relocateControlledBody(protectedFixture, protectedBody, protectedCell);
            relocateControlledBody(unprotectedFixture, unprotectedBody, unprotectedCell);
            var hotAir = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(
                    java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, 14.8,
                            com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN, 55.2), 600.0);
            var atmosphere = com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
            require(atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level,
                    java.util.List.of(protectedCell, protectedCell.above()), hotAir),
                    "protected hot room is trusted finite gas");
            require(atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level,
                    java.util.List.of(unprotectedCell, unprotectedCell.above()), hotAir),
                    "control hot room is independently trusted finite gas");
            var protectedBreathCell = net.minecraft.core.BlockPos.containing(protectedBody.getEyePosition());
            var controlBreathCell = net.minecraft.core.BlockPos.containing(unprotectedBody.getEyePosition());
            require(atmosphere.sample(level, protectedBreathCell).orElseThrow().temperatureKelvin() == 600.0
                            && atmosphere.sample(level, controlBreathCell).orElseThrow().temperatureKelvin() == 600.0,
                    "matched finite chambers expose both bodies to the same known hot atmosphere");
            require(com.juicyslew.moonstation14.ms14.equipment.EquipmentProtectionProjection
                            .temperatureCoefficients(com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment
                                    .entries(protectedBody)).heating() < 1.0
                            && com.juicyslew.moonstation14.ms14.equipment.EquipmentProtectionProjection
                                    .temperatureCoefficients(com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment
                                            .entries(unprotectedBody)).heating() == 1.0,
                    "only the protected body has resolved worn thermal coefficients");
            pollForNormalTemperatureInitialization(helper, protectedFixture, unprotectedFixture,
                    protectedBody, unprotectedBody, atmosphere, protectedBreathCell, controlBreathCell, 0);
        } catch (RuntimeException | Error failure) {
            protectedFixture.close();
            unprotectedFixture.close();
            throw failure;
        }
    }

    private static void pollForNormalTemperatureInitialization(GameTestHelper helper,
            RegisteredServerPlayerFixture protectedFixture, RegisteredServerPlayerFixture unprotectedFixture,
            net.minecraft.world.entity.LivingEntity protectedBody, net.minecraft.world.entity.LivingEntity unprotectedBody,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService atmosphere,
            net.minecraft.core.BlockPos protectedCell, net.minecraft.core.BlockPos controlCell, int waited) {
        var protectedTemperature = protectedBody.getExistingDataOrNull(
                com.juicyslew.moonstation14.component.ModDataAttachments.BODY_TEMPERATURE.get());
        var controlTemperature = unprotectedBody.getExistingDataOrNull(
                com.juicyslew.moonstation14.component.ModDataAttachments.BODY_TEMPERATURE.get());
        if (protectedTemperature == null || controlTemperature == null) {
            if (waited >= 40) {
                protectedFixture.close();
                unprotectedFixture.close();
                throw new GameTestAssertException("normal tick hooks did not initialize body temperatures; protected="
                        + protectedTemperature + " control=" + controlTemperature);
            }
            BoundedGameTestWait.safeDelay(helper, 1, () -> pollForNormalTemperatureInitialization(helper,
                    protectedFixture, unprotectedFixture, protectedBody, unprotectedBody, atmosphere,
                    protectedCell, controlCell, waited + 1));
            return;
        }
        double protectedStart = protectedTemperature.kelvin();
        double controlStart = controlTemperature.kelvin();
        BoundedGameTestWait.safeDelay(helper, 220, () -> verifyHotTemperatureComparison(helper,
                protectedFixture, unprotectedFixture, protectedBody, unprotectedBody, atmosphere,
                protectedCell, controlCell, protectedStart, controlStart));
    }

    private static void verifyHotTemperatureComparison(GameTestHelper helper,
            RegisteredServerPlayerFixture protectedFixture, RegisteredServerPlayerFixture unprotectedFixture,
            net.minecraft.world.entity.LivingEntity protectedBody, net.minecraft.world.entity.LivingEntity unprotectedBody,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService atmosphere,
            net.minecraft.core.BlockPos protectedCell, net.minecraft.core.BlockPos controlCell,
            double protectedStart, double controlStart) {
        try {
            var protectedAir = atmosphere.sample(helper.getLevel(), protectedCell).orElseThrow();
            var controlAir = atmosphere.sample(helper.getLevel(), controlCell).orElseThrow();
            require(protectedAir.totalMoles() > 0 && controlAir.totalMoles() > 0
                            && protectedAir.temperatureKelvin() > 400 && controlAir.temperatureKelvin() > 400,
                    "hot finite-room gas remains a strict physical source after normal energy exchange");
            var protectedFinal = protectedBody.getExistingDataOrNull(
                    com.juicyslew.moonstation14.component.ModDataAttachments.BODY_TEMPERATURE.get());
            var controlFinal = unprotectedBody.getExistingDataOrNull(
                    com.juicyslew.moonstation14.component.ModDataAttachments.BODY_TEMPERATURE.get());
            require(protectedFinal != null && controlFinal != null, "both tick-owned temperature states persist");
            double protectedGain = protectedFinal.kelvin() - protectedStart;
            double controlGain = controlFinal.kelvin() - controlStart;
            require(controlGain > 0.05 && controlGain > protectedGain + 0.05,
                    "normal hot-atmosphere exposure heats unprotected body more; protected=" + protectedGain
                            + "K naked=" + controlGain + "K final=" + protectedFinal.kelvin() + "/"
                            + controlFinal.kelvin());
            helper.succeed();
            protectedFixture.close();
            unprotectedFixture.close();
        } catch (RuntimeException | Error failure) {
            protectedFixture.close();
            unprotectedFixture.close();
            throw failure;
        }
    }

    private static void setupPressureProtection(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                                net.minecraft.server.level.ServerLevel level) {
        try {
            var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            var body = active.body();
            var authority = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                    .resolve(fixture.player()).orElseThrow();
            equipRegisteredItem(body, fixture.player(), authority, level, "eva_helmet");
            equipRegisteredItem(body, fixture.player(), authority, level, "eva_suit");
            var maskItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "breathing_mask"));
            require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body,
                    new net.minecraft.world.item.ItemStack(maskItem),
                    () -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                            .revalidate(fixture.player(), authority)), "trusted mask fixture give");
            var hands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
            String maskHand = findHand(hands, stack -> stack.is(maskItem));
            var maskToken = hands.token(maskHand).orElseThrow();
            require(com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.quickEquip(
                    fixture.player(), maskHand, maskToken, hands.revision())
                    == com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.Result.SUCCESS,
                    "breathing mask equipped through live inventory owner");
            var tank = com.juicyslew.moonstation14.item.custom.GasTankItem.createStack(level,
                    com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks.OXYGEN_FILLED_ID);
            require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body, tank,
                    () -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                            .revalidate(fixture.player(), authority)), "trusted filled oxygen tank give");
            hands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
            String tankHand = findHand(hands, stack -> stack.getItem() instanceof com.juicyslew.moonstation14.item.custom.GasTankItem);
            String tankToken = hands.token(tankHand).orElseThrow();

            var origin = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
            com.juicyslew.moonstation14.gametest.AtmosphereRoomGameTests.buildSealedHotspotCell(helper, 2, 2);
            relocateControlledBody(fixture, body, origin);
            var atmosphere = com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
            var vacuum = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(java.util.Map.of(), 293.15);
            require(atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level,
                    java.util.List.of(origin, origin.above()), vacuum), "isolated finite vacuum chamber initialized");
            var breathingCell = net.minecraft.core.BlockPos.containing(body.getEyePosition());
            require(atmosphere.sample(level, breathingCell).orElseThrow().pressureKpa(1.0) == 0,
                    "physical chamber is known zero-pressure vacuum before first exposure");

            var before = BodyActionService.query(fixture.player()).orElseThrow();
            var actionId = ResourceLocation.fromNamespaceAndPath("moonstation14", "toggle_internals");
            var action = before.actions().stream().filter(e -> e.actionId().equals(actionId)).findFirst().orElseThrow();
            require(action.providerId().contains(tankToken), "carried full tank provides internals action");
            require(BodyActionService.activate(fixture.player(), new BodyActionRequest(before.bodyId(), before.bodyEpoch(),
                    before.revision(), actionId, action.providerId(), 1)).status()
                    == com.juicyslew.moonstation14.ms14.actions.BodyActionResult.Status.ACCEPTED,
                    "internals connected with full oxygen tank");
            var character = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(body)
                    .orElseThrow();
            var policy = character.barotrauma().orElseThrow();
            var baseline = com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(body);
            var entries = com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.entries(body);
            require(com.juicyslew.moonstation14.ms14.equipment.EquipmentProtectionProjection.pressure(entries, policy).isPresent(),
                    "resolved worn helmet and suit satisfy all pressure protection slots");
            BoundedGameTestWait.safeDelay(helper, 50, () -> awaitProtectedExposure(helper, fixture, body, tankToken,
                    policy, baseline, atmosphere, breathingCell, 0));
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void awaitProtectedExposure(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity body, String tankToken,
            com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent policy,
            java.util.Map<String, Float> baseline,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService atmosphere,
            net.minecraft.core.BlockPos breathingCell, int waited) {
        try {
            require(atmosphere.sample(helper.getLevel(), breathingCell).orElseThrow().pressureKpa(1.0)
                            <= com.juicyslew.moonstation14.ms14.atmos.exposure.BarotraumaAtmospherePolicy.LOW_HAZARD_KPA,
                    "physical ambient remains known low pressure despite accumulated exhaled gas");
            var source = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElseThrow();
            require(source.token().equals(tankToken) && source.contents().mixture().totalMoles() > 0,
                    "tank remains selected and working; ambient pressure must not be substituted");
            var damage = com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(body);
            require(policy.damage().types().keySet().stream().allMatch(key ->
                            damage.getOrDefault(key, 0f) <= baseline.getOrDefault(key, 0f)),
                    "full EVA protection prevents pressure-type damage over normal exposure ticks");
            var respiratoryPolicy = com.juicyslew.moonstation14.ms14.lung.LungSystem.resolvePolicy(body).orElseThrow();
            var respiration = com.juicyslew.moonstation14.ms14.organ.BodySystem.current(body).orElseThrow().respiration();
            int cadence = com.juicyslew.moonstation14.ms14.lung.LungSystem.intervalTicks(
                    respiratoryPolicy.breathIntervalSeconds());
            int inhaleWithin = -1;
            for (int delta = 1; delta <= cadence / 2; delta++) {
                if (com.juicyslew.moonstation14.ms14.lung.LungSystem.isDue(
                        helper.getLevel().getGameTime() + delta, body.getId(), cadence)) {
                    inhaleWithin = delta;
                    break;
                }
            }
            if (waited < 120 && (respiration.phase() != com.juicyslew.moonstation14.ms14.lung.LungComponent.Phase.INHALING
                    || inhaleWithin < 1)) {
                BoundedGameTestWait.safeDelay(helper, 1, () -> awaitProtectedExposure(helper, fixture, body,
                        tankToken, policy, baseline, atmosphere, breathingCell, waited + 1));
                return;
            }
            require(respiration.phase() == com.juicyslew.moonstation14.ms14.lung.LungComponent.Phase.INHALING
                            && inhaleWithin >= 1 && inhaleWithin <= cadence / 2,
                    "overlap setup waits for actual inhaling phase and due cadence in first half of 40-tick helmet delay; "
                            + "phase=" + respiration.phase() + " cadence=" + cadence + " nextDue=" + inhaleWithin);
            var equipment = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.equipmentSnapshot(body).orElseThrow();
            var helmetSlot = equipment.slots().stream().filter(slot -> slot.stack().isPresent()
                    && slot.stack().orElseThrow().is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "eva_helmet")))).findFirst().orElseThrow();
            var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            String emptyHand = hands.handIds().stream().filter(h -> hands.stackCopy(h).isEmpty()).findFirst().orElseThrow();
            var currentAuthority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player())
                    .orElseThrow();
            var fullQuery = fixture.queryHands(currentAuthority.epoch());
            var currentHelmet = fullQuery.equipment().stream().filter(slot -> slot.id().equals(helmetSlot.id())
                    && helmetSlot.token().orElseThrow().equals(slot.token())).findFirst().orElseThrow();
            var request = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                    com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.UNEQUIP_SLOT,
                    emptyHand, currentAuthority.epoch(), 1, fullQuery.revision(), null, currentHelmet.token(),
                    null, -1, -1, -1, -1, -1, -1, null, currentHelmet.id(), null, null);
            var response = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(
                    fixture.player(), request);
            require(response.accepted(), "authenticated generic UNEQUIP_SLOT request accepted: " + response.reason());
            var initialSource = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElseThrow();
            require(initialSource.token().equals(tankToken), "timed removal begins with exact oxygen source connected");
            var capturedHands = body.getExistingDataOrNull(
                    com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            require(com.juicyslew.moonstation14.ms14.hands.live.LiveHands.equipmentSnapshot(body).orElseThrow()
                            .slots().stream().anyMatch(slot -> slot.token().filter(currentHelmet.token()::equals).isPresent()),
                    "40-tick timed unequip starts with helmet still worn");
            awaitTimedHelmetRemoval(helper, fixture, body, tankToken, policy, baseline, atmosphere,
                    breathingCell, currentHelmet.token(), initialSource.contents(), capturedHands,
                    helper.getLevel().getGameTime(), 0, false);
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void awaitTimedHelmetRemoval(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity body, String tankToken,
            com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent policy,
            java.util.Map<String, Float> baseline,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService atmosphere,
            net.minecraft.core.BlockPos breathingCell, String helmetToken,
            com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents initialTank,
            com.juicyslew.moonstation14.ms14.hands.live.LiveHands capturedHands,
            long startedAt, int waited, boolean sawDraw) {
        try {
            var liveEquipment = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.equipmentSnapshot(body).orElseThrow();
            boolean helmetPresent = liveEquipment.slots().stream().anyMatch(slot -> slot.id().equals("head")
                    && slot.token().filter(helmetToken::equals).isPresent());
            var source = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElseThrow();
            require(source.token().equals(tankToken), "exact tank remains selected throughout timed helmet removal");
            double initialOxygen = initialTank.mixture().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN);
            double currentOxygen = source.contents().mixture().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN);
            boolean drew = sawDraw || currentOxygen < initialOxygen;
            var currentHands = body.getExistingDataOrNull(
                    com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if (helmetPresent) {
                long elapsed = helper.getLevel().getGameTime() - startedAt;
                require(elapsed <= 60,
                        "timed equipment operation remains bounded after its authored 40-tick duration");
                if (drew) {
                    require(currentOxygen < initialOxygen
                                    && currentHands.continuesAcrossPassiveTankDebit(body, capturedHands)
                                    && currentHands.revision() == capturedHands.revision(),
                            "normal inhalation is a proven passive tank-only snapshot change during timed operation");
                }
                var current = com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(body);
                require(policy.damage().types().keySet().stream().allMatch(key ->
                                current.getOrDefault(key, 0f) <= baseline.getOrDefault(key, 0f)),
                        "pressure damage remains absent while timed normal request still wears helmet");
                if (waited >= 60) throw new GameTestAssertException("authorized timed helmet removal did not complete; "
                        + "wornSlots=" + com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.entries(body)
                                .stream().map(com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.Entry::slotId).toList());
                BoundedGameTestWait.safeDelay(helper, 1, () -> awaitTimedHelmetRemoval(helper, fixture, body, tankToken,
                        policy, baseline, atmosphere, breathingCell, helmetToken, initialTank, capturedHands,
                        startedAt, waited + 1, drew));
                return;
            }
            require(helper.getLevel().getGameTime() - startedAt >= 40,
                    "helmet stays worn until authored unequip timing completes");
            require(drew && currentOxygen < initialOxygen,
                    "normal scheduled inhale debits the exact tank before timed removal completes");
            require(currentHands.revision() > capturedHands.revision(),
                    "equipment completion commits after the passive debit without replacing tank identity");
            require(com.juicyslew.moonstation14.ms14.lung.InternalsSystem.isBreathToolFunctional(body),
                    "worn breathing mask continues to provide a functional breath tool after helmet removal");
            var worn = com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.entries(body);
            require(com.juicyslew.moonstation14.ms14.equipment.EquipmentProtectionProjection.pressure(worn, policy).isEmpty(),
                    "normal helmet removal leaves incomplete pressure coverage despite suit and mask");
            pollUnprotectedPressure(helper, fixture, body, tankToken, policy, baseline, atmosphere, breathingCell, 0);
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void pollUnprotectedPressure(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity body, String tankToken,
            com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent policy,
            java.util.Map<String, Float> baseline,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService atmosphere,
            net.minecraft.core.BlockPos breathingCell, int waited) {
        try {
            require(atmosphere.sample(helper.getLevel(), breathingCell).orElseThrow().pressureKpa(1.0)
                            <= com.juicyslew.moonstation14.ms14.atmos.exposure.BarotraumaAtmospherePolicy.LOW_HAZARD_KPA,
                    "physical ambient remains in low-pressure hazard after helmet removal");
            var source = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElseThrow();
            require(source.token().equals(tankToken), "oxygen source remains connected through pressure damage");
            var damage = com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(body);
            boolean pressureDamage = policy.damage().types().keySet().stream().anyMatch(key ->
                    damage.getOrDefault(key, 0f) > baseline.getOrDefault(key, 0f));
            if (pressureDamage) {
                helper.succeed();
                fixture.close();
                return;
            }
            if (waited >= 120) throw new GameTestAssertException("unprotected vacuum produced no policy-typed pressure damage; "
                    + "types=" + policy.damage().types().keySet() + " actual=" + damage + " tankPressure="
                    + source.contents().mixture().pressureKpa(source.profile().volumeCubicMeters()));
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
        BoundedGameTestWait.safeDelay(helper, 1, () -> pollUnprotectedPressure(helper, fixture, body,
                tankToken, policy, baseline, atmosphere, breathingCell, waited + 1));
    }

    private static void setupDepletion(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                       net.minecraft.server.level.ServerLevel level) {
        try {
            var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            var body = active.body();
            var authority = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                    .resolve(fixture.player()).orElseThrow();
            var maskActionId = ResourceLocation.fromNamespaceAndPath("moonstation14", "toggle_mask");
            var internalsActionId = ResourceLocation.fromNamespaceAndPath("moonstation14", "toggle_internals");
            var maskItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "breathing_mask"));
            require(maskItem != net.minecraft.world.item.Items.AIR, "registered breathing mask exists");

            var tank = com.juicyslew.moonstation14.item.custom.GasTankItem.createStack(level,
                    com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks.OXYGEN_FILLED_ID);
            var originalContents = com.juicyslew.moonstation14.item.custom.GasTankItem.contents(tank, level).orElseThrow();
            var profile = com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks.catalog(level)
                    .get(originalContents.prototypeId());
            double oneBreath = com.juicyslew.moonstation14.ms14.atmos.core.GasMixture.molesForPressureDelta(
                    profile.outletPressureKpa(), .0005, 293.15);
            var finiteOxygen = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(
                    java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, oneBreath * 2.2),
                    293.15, originalContents.mixture().volumeCubicMeters());
            tank = com.juicyslew.moonstation14.item.custom.GasTankItem.withMixture(tank, finiteOxygen);
            require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body, tank,
                    () -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                            .revalidate(fixture.player(), authority)), "trusted body give accepts finite low-charge oxygen tank");
            var hands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
            String tankHand = findHand(hands, s -> s.getItem() instanceof com.juicyslew.moonstation14.item.custom.GasTankItem);
            String tankToken = hands.token(tankHand).orElseThrow();
            equipRegisteredItem(body, fixture.player(), authority, level, "eva_helmet");
            equipRegisteredItem(body, fixture.player(), authority, level, "eva_suit");
            require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body,
                    new net.minecraft.world.item.ItemStack(maskItem),
                    () -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                            .revalidate(fixture.player(), authority)), "trusted body give accepts breathing mask");
            hands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
            String maskHand = findHand(hands, s -> s.is(maskItem));
            String maskToken = hands.token(maskHand).orElseThrow();
            require(com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.quickEquip(
                    fixture.player(), maskHand, maskToken, hands.revision())
                    == com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.Result.SUCCESS,
                    "mask equipped through live inventory owner");

            var internalsId = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
            com.juicyslew.moonstation14.gametest.AtmosphereRoomGameTests.buildSealedHotspotCell(helper, 2, 2);
            relocateControlledBody(fixture, body, internalsId);
            var atmosphere = com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
            var vacuum = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(java.util.Map.of(), 293.15);
            require(atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level,
                    java.util.List.of(internalsId, internalsId.above()), vacuum), "sealed known-vacuum chamber initialized");
            var breathingCell = net.minecraft.core.BlockPos.containing(body.getEyePosition());
            require(atmosphere.sample(level, breathingCell).orElseThrow().pressureKpa(1.0) == 0,
                    "breathing exposure is known zero-pressure finite vacuum before first scheduled breath");

            var beforeToggle = BodyActionService.query(fixture.player()).orElseThrow();
            var toggle = beforeToggle.actions().stream().filter(e -> e.actionId().equals(internalsActionId)).findFirst().orElseThrow();
            require(toggle.providerId().contains(tankToken), "internals capability comes from exact carried tank");
            require(BodyActionService.activate(fixture.player(), new BodyActionRequest(beforeToggle.bodyId(),
                    beforeToggle.bodyEpoch(), beforeToggle.revision(), internalsActionId, toggle.providerId(), 1)).status()
                    == com.juicyslew.moonstation14.ms14.actions.BodyActionResult.Status.ACCEPTED,
                    "authenticated internals toggle connects small real oxygen charge");
            require(com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElseThrow()
                    .token().equals(tankToken), "exact charged tank selected");
            var initialRespiration = com.juicyslew.moonstation14.ms14.organ.BodySystem.current(body).orElseThrow().respiration();
            double initialSaturation = initialRespiration.saturation();
            var initialPhase = initialRespiration.phase();
            var initialTankContents = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body)
                    .orElseThrow().contents();
            boolean[] phaseAdvanced = {false};
            final int[] waited = {0};
            pollUntilTankEmpty(helper, fixture, body, tankToken, maskToken, maskActionId,
                    atmosphere, breathingCell, initialTankContents, initialSaturation, initialPhase, phaseAdvanced, waited);
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static String findHand(com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands,
                                   java.util.function.Predicate<net.minecraft.world.item.ItemStack> match) {
        return hands.handIds().stream().filter(h -> hands.stackCopy(h).filter(match).isPresent())
                .findFirst().orElseThrow();
    }

    /** Mirrors the validated controlled-body move in EvaFixtureCommands.enter. */
    private static void relocateControlledBody(RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity expectedBody, net.minecraft.core.BlockPos target) {
        var authority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player())
                .orElseThrow(() -> new GameTestAssertException("no authenticated body relocation authority"));
        require(authority.body() == expectedBody, "relocation target is the lifecycle-owned controlled body");
        var level = fixture.player().serverLevel();
        var box = expectedBody.getBoundingBox().move(target.getX() + .5 - expectedBody.getX(),
                target.getY() - expectedBody.getY(), target.getZ() + .5 - expectedBody.getZ());
        require(expectedBody.level() == level && !expectedBody.isRemoved() && expectedBody.isAlive()
                        && level.hasChunkAt(target) && level.noCollision(expectedBody, box)
                        && com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(fixture.player(), authority),
                "authorized body relocation target is loaded, collision-free, and current");
        expectedBody.teleportTo(target.getX() + .5, target.getY(), target.getZ() + .5);
        expectedBody.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        require(com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(fixture.player(), authority),
                "controlled body/camera authority remains valid after canonical fixture relocation");
    }

    private static void cleanupOnTerminal(GameTestHelper helper, RegisteredServerPlayerFixture fixture) {
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup() { fixture.close(); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored,
                    net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored,
                    net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                    net.minecraft.gametest.framework.GameTestInfo newTest,
                    net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
        });
    }

    private static void equipRegisteredItem(net.minecraft.world.entity.LivingEntity body,
            net.minecraft.server.level.ServerPlayer player,
            com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.Snapshot authority,
            net.minecraft.server.level.ServerLevel level, String itemPath) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("moonstation14", itemPath));
        require(item != net.minecraft.world.item.Items.AIR, "registered equipment exists: " + itemPath);
        require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body,
                new net.minecraft.world.item.ItemStack(item),
                () -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                        .revalidate(player, authority)), "trusted body give accepts " + itemPath);
        var hands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
        String hand = findHand(hands, stack -> stack.is(item));
        String token = hands.token(hand).orElseThrow();
        require(com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.quickEquip(
                player, hand, token, hands.revision())
                == com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.Result.SUCCESS,
                itemPath + " equipped through live inventory owner");
    }

    private static void pollUntilTankEmpty(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity body, String tankToken, String maskToken,
            ResourceLocation maskActionId,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService atmosphere,
            net.minecraft.core.BlockPos breathingCell,
            com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents initialTankContents,
            double initialSaturation,
            com.juicyslew.moonstation14.ms14.lung.LungComponent.Phase initialPhase,
            boolean[] phaseAdvanced, int[] waited) {
        if (waited[0]++ >= 300) {
            fixture.close();
            var respiration = com.juicyslew.moonstation14.ms14.organ.BodySystem.current(body)
                    .map(com.juicyslew.moonstation14.ms14.organ.BodyState::respiration).orElse(null);
            var source = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElse(null);
            var room = atmosphere.sample(helper.getLevel(), breathingCell).orElse(null);
            var lung = com.juicyslew.moonstation14.ms14.organ.BodySystem.current(body).flatMap(state ->
                    state.organs().stream().filter(organ -> organ.category()
                            == com.juicyslew.moonstation14.ms14.organ.OrganCategory.LUNGS).findFirst())
                    .map(com.juicyslew.moonstation14.ms14.organ.OrganInstance::lung).orElse(null);
            throw new GameTestAssertException("small finite oxygen charge did not deplete in 300 polling ticks; gameTime="
                    + helper.getLevel().getGameTime() + " entityId=" + body.getId() + " saturation="
                    + (respiration == null ? null : respiration.saturation()) + " phase="
                    + (respiration == null ? null : respiration.phase()) + " lungMoles="
                    + (lung == null ? null : lung.mixture().gasMoles()) + " selected=" + tankDiagnostic(body, tankToken)
                    + " initialTank=" + initialTankContents.mixture().gasMoles() + " ambient="
                    + (room == null ? "UNKNOWN" : "pressure=" + room.pressureKpa(1) + ",moles=" + room.gasMoles())
                    + " knownCell=" + atmosphere.diagnosticCell(helper.getLevel(), breathingCell).gas());
        }
        try {
            var source = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElseThrow();
            require(source.token().equals(tankToken), "tank remains selected while draining");
            if (com.juicyslew.moonstation14.ms14.organ.BodySystem.current(body).orElseThrow()
                    .respiration().phase() != initialPhase) phaseAdvanced[0] = true;
            double oxygen = source.contents().mixture().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN);
            if (oxygen <= 1e-7) {
                require(atmosphere.sample(helper.getLevel(), breathingCell).orElseThrow().pressureKpa(1.0)
                                <= com.juicyslew.moonstation14.ms14.atmos.exposure.BarotraumaAtmospherePolicy.LOW_HAZARD_KPA,
                        "empty source remains in known low-pressure chamber with accumulated exhalation");
                var state = com.juicyslew.moonstation14.ms14.organ.BodySystem.current(body).orElseThrow().respiration();
                require(state.saturation() < initialSaturation && phaseAdvanced[0],
                        "normal scheduled physiology advances and depletes saturation in vacuum");
                var emptyCharge = source.contents();
                var snapshot = BodyActionService.query(fixture.player()).orElseThrow();
                var mask = snapshot.actions().stream().filter(e -> e.actionId().equals(maskActionId)).findFirst().orElseThrow();
                require(mask.providerId().endsWith(maskToken), "mask action provider is exact worn mask");
                var lowered = BodyActionService.activate(fixture.player(), new BodyActionRequest(snapshot.bodyId(),
                        snapshot.bodyEpoch(), snapshot.revision(), maskActionId, mask.providerId(), 2));
                require(lowered.status() == com.juicyslew.moonstation14.ms14.actions.BodyActionResult.Status.ACCEPTED,
                        "mask lowering uses authenticated toggle action");
                require(com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElseThrow()
                                .token().equals(tankToken)
                                && com.juicyslew.moonstation14.ms14.lung.InternalsSystem.isConnected(body),
                        "lowering the mask does not disconnect internals while the EVA helmet remains a functional breath tool");
                var authority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player())
                        .orElseThrow();
                var fullQuery = fixture.queryHands(authority.epoch());
                var helmet = fullQuery.equipment().stream().filter(slot -> slot.token() != null
                        && "moonstation14:eva_helmet".equals(slot.itemId()))
                        .findFirst().orElseThrow();
                var hands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
                String emptyHand = hands.handIds().stream().filter(hand -> hands.stackCopy(hand).isEmpty())
                        .findFirst().orElseThrow();
                var unequip = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                        com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.UNEQUIP_SLOT,
                        emptyHand, authority.epoch(), 1, fullQuery.revision(), null, helmet.token(),
                        null, -1, -1, -1, -1, -1, -1, null, helmet.id(), null, null);
                var removal = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(
                        fixture.player(), unequip);
                require(removal.accepted(), "normal authenticated helmet unequip starts while mask is lowered: "
                        + removal.reason());
                awaitLastBreathToolRemoval(helper, fixture, body, tankToken, maskToken, maskActionId,
                        helmet.token(), emptyCharge, helper.getLevel().getGameTime(), 0);
                return;
            }
            require(atmosphere.sample(helper.getLevel(), breathingCell).orElseThrow().pressureKpa(1.0)
                            <= com.juicyslew.moonstation14.ms14.atmos.exposure.BarotraumaAtmospherePolicy.LOW_HAZARD_KPA,
                    "known finite low-pressure atmosphere remains respiratory exposure during tank draw");
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
        BoundedGameTestWait.safeDelay(helper, 1, () -> pollUntilTankEmpty(helper, fixture, body, tankToken,
                maskToken, maskActionId, atmosphere, breathingCell, initialTankContents, initialSaturation,
                initialPhase, phaseAdvanced, waited));
    }

    private static void awaitLastBreathToolRemoval(GameTestHelper helper,
            RegisteredServerPlayerFixture fixture, net.minecraft.world.entity.LivingEntity body,
            String tankToken, String maskToken, ResourceLocation maskActionId, String helmetToken,
            com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents emptyCharge,
            long startedAt, int waited) {
        try {
            var equipment = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.equipmentSnapshot(body).orElseThrow();
            boolean helmetStillWorn = equipment.slots().stream().anyMatch(slot ->
                    slot.token().filter(helmetToken::equals).isPresent());
            if (helmetStillWorn) {
                require(com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElseThrow()
                                .token().equals(tankToken),
                        "lowered mask remains covered by the still-worn helmet until timed removal completes");
                if (waited >= 60) throw new GameTestAssertException("normal timed helmet removal exceeded its bound");
                BoundedGameTestWait.safeDelay(helper, 1, () -> awaitLastBreathToolRemoval(helper, fixture, body,
                        tankToken, maskToken, maskActionId, helmetToken, emptyCharge, startedAt, waited + 1));
                return;
            }
            require(helper.getLevel().getGameTime() - startedAt >= 40,
                    "helmet remains worn until authored 40-tick unequip delay completes");
            require(com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).isEmpty()
                            && !com.juicyslew.moonstation14.ms14.lung.InternalsSystem.isConnected(body),
                    "removing the helmet after mask lowering removes the last tool and disconnects internals");
            awaitMaskRaiseCooldown(helper, fixture, body, tankToken, maskToken, maskActionId, emptyCharge, 0);
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void awaitMaskRaiseCooldown(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity body, String tankToken, String maskToken,
            ResourceLocation maskActionId, com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents emptyCharge,
            int waited) {
        try {
            var snapshot = BodyActionService.query(fixture.player()).orElseThrow();
            var mask = snapshot.actions().stream().filter(entry -> entry.actionId().equals(maskActionId)
                    && entry.providerId().endsWith(maskToken)).findFirst().orElseThrow();
            if (!mask.enabled()) {
                if (waited >= 80) throw new GameTestAssertException("mask toggle action cooldown did not expire; "+mask.disabledReason());
                BoundedGameTestWait.safeDelay(helper, 1, () -> awaitMaskRaiseCooldown(helper, fixture, body,
                        tankToken, maskToken, maskActionId, emptyCharge, waited + 1));
                return;
            }
            var raised = BodyActionService.activate(fixture.player(), new BodyActionRequest(snapshot.bodyId(),
                    snapshot.bodyEpoch(), snapshot.revision(), maskActionId, mask.providerId(), 3));
            require(raised.status() == com.juicyslew.moonstation14.ms14.actions.BodyActionResult.Status.ACCEPTED,
                    "mask raise follows its live action cooldown through authenticated action owner");
            require(com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).isEmpty()
                            && !com.juicyslew.moonstation14.ms14.lung.InternalsSystem.isConnected(body),
                    "raising the mask after last-tool loss does not silently reconnect or refill the tank");
            var hands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
            var retained = hands.stackCopy(findHand(hands,
                    stack -> stack.getItem() instanceof com.juicyslew.moonstation14.item.custom.GasTankItem))
                    .flatMap(stack -> com.juicyslew.moonstation14.item.custom.GasTankItem.contents(stack)).orElseThrow();
            require(retained.mixture().gasMoles().equals(emptyCharge.mixture().gasMoles())
                            && retained.prototypeId().equals(emptyCharge.prototypeId()),
                    "empty tank contents and prototype remain unchanged after disconnect and mask re-raise; token="
                            + tankToken);
            helper.succeed();
            fixture.close();
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void setupAndToggle(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                       net.minecraft.server.level.ServerLevel level) {
        try {
            var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            var body = active.body();
            var carrier = fixture.player();
            require(com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy
                    .isCarrier(carrier), "registered ready player is lifecycle carrier distinct from controlled body");
            require(com.juicyslew.moonstation14.ms14.lung.LungSystem.resolvePolicy(carrier).isEmpty(),
                    "carrier has no character respiratory policy");
            var carrierRespirationBefore = com.juicyslew.moonstation14.ms14.organ.BodySystem.current(carrier)
                    .map(b -> b.respiration()).orElse(null);
            var carrierGaspAttachment = carrier.getExistingDataOrNull(
                    com.juicyslew.moonstation14.component.ModDataAttachments.GASP_COOLDOWN.get());
            var carrierGaspBefore = carrierGaspAttachment == null ? java.util.OptionalLong.empty()
                    : carrierGaspAttachment.lastAttemptTick();
            require(com.juicyslew.moonstation14.ms14.lung.LungSystem.resolvePolicy(body).isPresent(),
                    "controlled body retains its respiratory policy");
            var authority = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                    .resolve(fixture.player()).orElseThrow();
            var internals = ResourceLocation.fromNamespaceAndPath("moonstation14", "toggle_internals");
            var maskAction = ResourceLocation.fromNamespaceAndPath("moonstation14", "toggle_mask");
            var bare = BodyActionService.query(fixture.player()).orElseThrow();
            require(bare.actions().stream().noneMatch(e -> e.actionId().equals(internals)
                    || e.actionId().equals(maskAction)), "bare human has no item-provided actions");

            var tankStack = com.juicyslew.moonstation14.item.custom.GasTankItem.createStack(level,
                    com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks.OXYGEN_FILLED_ID);
            require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body, tankStack,
                    () -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                            .revalidate(fixture.player(), authority)), "trusted body give accepts filled oxygen tank");
            var initialHands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
            String tankHand = initialHands.handIds().stream().filter(h -> initialHands.stackCopy(h)
                    .filter(s -> s.getItem() instanceof com.juicyslew.moonstation14.item.custom.GasTankItem)
                    .isPresent()).findFirst().orElseThrow();
            String tankToken = initialHands.token(tankHand).orElseThrow();

            var maskItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "breathing_mask"));
            require(maskItem != net.minecraft.world.item.Items.AIR, "registered breathing mask exists");
            require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body,
                    new net.minecraft.world.item.ItemStack(maskItem),
                    () -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                            .revalidate(fixture.player(), authority)), "trusted body give accepts breathing mask");
            var equippedHands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
            String maskHand = equippedHands.handIds().stream().filter(h -> equippedHands.stackCopy(h).filter(s -> s.is(maskItem))
                    .isPresent()).findFirst().orElseThrow();
            String maskToken = equippedHands.token(maskHand).orElseThrow();
            require(com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.quickEquip(
                    fixture.player(), maskHand, maskToken, equippedHands.revision())
                    == com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.Result.SUCCESS,
                    "mask equipped through live equipment owner");

            var snapshot = BodyActionService.query(fixture.player()).orElseThrow();
            var internalEntry = snapshot.actions().stream().filter(e -> e.actionId().equals(internals)).findFirst().orElseThrow();
            var maskEntry = snapshot.actions().stream().filter(e -> e.actionId().equals(maskAction)).findFirst().orElseThrow();
            require(internalEntry.providerId().contains(tankToken) && maskEntry.providerId().contains(maskToken),
                    "item action providers identify exact source and worn mask tokens");

            var cell = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
            com.juicyslew.moonstation14.gametest.AtmosphereRoomGameTests.buildSealedHotspotCell(helper, 2, 2);
            relocateControlledBody(fixture, body, cell);
            var atmosphere = com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
            var roomAir = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(
                    java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, 8d,
                            com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN, 30d), 293.15);
            require(atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level,
                    java.util.List.of(cell, cell.above()), roomAir),
                    "sealed finite chamber initialized through trusted atmosphere setup");
            var breathingCell = net.minecraft.core.BlockPos.containing(body.getEyePosition());
            require(atmosphere.sample(level, breathingCell).isPresent(), "breathing headspace starts as known finite atmosphere");

            snapshot = BodyActionService.query(fixture.player()).orElseThrow();
            internalEntry = snapshot.actions().stream().filter(e -> e.actionId().equals(internals)).findFirst().orElseThrow();
            var result = BodyActionService.activate(fixture.player(), new BodyActionRequest(snapshot.bodyId(),
                    snapshot.bodyEpoch(), snapshot.revision(), internals, internalEntry.providerId(), 1));
            require(result.status() == com.juicyslew.moonstation14.ms14.actions.BodyActionResult.Status.ACCEPTED,
                    "authenticated internals action accepted, result=" + result.status() + "/" + result.reason());
            var selected = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElseThrow();
            require(selected.token().equals(tankToken), "connected source is exact carried tank");
            var initialContents = selected.contents();
            long liveRevision = body.getExistingDataOrNull(
                    com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).revision();
            double initialCo2 = atmosphere.sample(level, breathingCell).orElseThrow().moles(
                    com.juicyslew.moonstation14.ms14.atmos.core.GasType.CARBON_DIOXIDE);
            pollScheduledDraw(helper, fixture, body, tankToken, liveRevision, initialContents,
                    atmosphere, breathingCell, initialCo2, carrier, carrierRespirationBefore, carrierGaspBefore, 0);
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void pollScheduledDraw(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity body, String token, long liveRevision,
            com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents initial,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService atmosphere,
            net.minecraft.core.BlockPos cell, double initialCo2, net.minecraft.server.level.ServerPlayer carrier,
            com.juicyslew.moonstation14.ms14.organ.RespirationState carrierRespirationBefore,
            java.util.OptionalLong carrierGaspBefore, int waited) {
        if (waited >= 160) {
            var diagnostic = atmosphere.diagnosticCell(helper.getLevel(), cell);
            fixture.close();
            throw new GameTestAssertException("scheduled inhale timeout; waited=" + waited + " phase="
                    + com.juicyslew.moonstation14.ms14.organ.BodySystem.current(body)
                    .map(b -> b.respiration().phase()).orElse(null) + " tank=" + tankDiagnostic(body, token)
                    + " finiteGas=" + diagnostic.gas());
        }
        var source = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElse(null);
        if (source == null) {
            fixture.close();
            throw new GameTestAssertException("internals disconnected before scheduled inhale; token=" + token);
        }
        double before = initial.mixture().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN);
        double after = source.contents().mixture().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN);
        if (after < before) {
          try {
            require(source.token().equals(token), "scheduled inhale retains physical tank identity");
            require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                    .revision() == liveRevision, "canonical hand ownership revision remains stable");
            var tankProfile = source.profile();
            var expected = com.juicyslew.moonstation14.ms14.atmos.tank.GasTankDrawHelper.drawOutput(initial,
                    tankProfile, com.juicyslew.moonstation14.ms14.lung.LungSystem.resolvePolicy(body)
                            .orElseThrow().breathVolumeLiters()).inhaled();
            double debited = before - after;
            require(debited > 0 && Math.abs(debited - expected.moles(
                            com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN)) < Math.max(0.02, expected.totalMoles() * .08),
                     "tank debit matches regulated outlet draw; expected=" + expected.gasMoles() + " actual=" + debited);
            require(java.util.Objects.equals(carrierRespirationBefore,
                            com.juicyslew.moonstation14.ms14.organ.BodySystem.current(carrier)
                                    .map(b -> b.respiration()).orElse(null))
                            && java.util.Objects.equals(carrierGaspBefore, gaspSnapshot(carrier)),
                    "normal native tick hooks leave carrier respiration and gasp cooldown untouched");
            var refreshed = BodyActionService.query(fixture.player()).orElseThrow();
            require(refreshed.actions().stream().anyMatch(e -> e.actionId().getPath().equals("toggle_internals")
                    && e.providerId().contains(token)), "action snapshot retains provider after contents replication");
            var postInhale = source.contents();
            pollExhale(helper, fixture, body, token, liveRevision, postInhale, atmosphere, cell, initialCo2,
                    carrier, carrierRespirationBefore, carrierGaspBefore, 0);
            return;
          } catch (RuntimeException | Error failure) {
              fixture.close();
              throw failure;
          }
        }
        BoundedGameTestWait.safeDelay(helper, 1, () -> pollScheduledDraw(helper, fixture, body, token,
                liveRevision, initial, atmosphere, cell, initialCo2, fixture.player(), carrierRespirationBefore,
                carrierGaspBefore, waited + 1));
    }

    private static void pollExhale(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity body, String token, long liveRevision,
            com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents afterInhale,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService atmosphere,
            net.minecraft.core.BlockPos cell, double initialCo2, net.minecraft.server.level.ServerPlayer carrier,
            com.juicyslew.moonstation14.ms14.organ.RespirationState carrierRespirationBefore,
            java.util.OptionalLong carrierGaspBefore, int waited) {
        if (waited >= 160) {
            fixture.close();
            throw new GameTestAssertException("scheduled exhale timeout; waited=" + waited + " tank="
                    + tankDiagnostic(body, token) + " finiteGas=" + atmosphere.diagnosticCell(helper.getLevel(), cell).gas());
        }
        try {
            var current = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElseThrow();
            require(current.token().equals(token), "selected tank retained through exhale");
            require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                    .revision() == liveRevision, "ownership remains unchanged through exhale");
            var room = atmosphere.sample(helper.getLevel(), cell).orElseThrow();
            require(current.contents().mixture().gasMoles().equals(afterInhale.mixture().gasMoles()),
                    "exhalation never credits tank contents");
            if (room.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.CARBON_DIOXIDE) > initialCo2) {
                require(java.util.Objects.equals(carrierRespirationBefore,
                                com.juicyslew.moonstation14.ms14.organ.BodySystem.current(carrier)
                                        .map(b -> b.respiration()).orElse(null))
                                && java.util.Objects.equals(carrierGaspBefore, gaspSnapshot(carrier)),
                        "normal inhale/exhale ticks leave native carrier physiology and cooldown attachment untouched");
                require(com.juicyslew.moonstation14.ms14.organ.BodySystem.current(body).orElseThrow()
                                .respiration().saturation() > 0,
                        "controlled body remains healthy through actual inhale/exhale ticks");
                System.out.println("MS14_RESPIRATION_CARRIER_PASS EvaInternalsGameTests.equippedInternalsActionDrivesScheduledFiniteTankDraw");
                helper.succeed();
                fixture.close();
                return;
            }
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
        BoundedGameTestWait.safeDelay(helper, 1, () -> pollExhale(helper, fixture, body, token,
                liveRevision, afterInhale, atmosphere, cell, initialCo2, carrier, carrierRespirationBefore,
                carrierGaspBefore, waited + 1));
    }

    private static String tankDiagnostic(net.minecraft.world.entity.LivingEntity body, String token) {
        var source = com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElse(null);
        return source == null ? "disconnected,expected=" + token : "token=" + source.token() + ",pressure="
                + source.contents().mixture().pressureKpa(source.profile().volumeCubicMeters());
    }

    private static java.util.OptionalLong gaspSnapshot(net.minecraft.world.entity.LivingEntity entity) {
        var attachment = entity.getExistingDataOrNull(
                com.juicyslew.moonstation14.component.ModDataAttachments.GASP_COOLDOWN.get());
        return attachment == null ? java.util.OptionalLong.empty() : attachment.lastAttemptTick();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
