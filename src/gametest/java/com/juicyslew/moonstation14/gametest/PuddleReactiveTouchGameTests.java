package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.slip.ReactiveTouchSystem;
import com.juicyslew.moonstation14.ms14.slip.SlipEvent;
import com.juicyslew.moonstation14.ms14.slip.SlipSystem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PuddleReactiveTouchGameTests {
    private static final ResourceKey<ReagentData> SPACE_LUBE = ModReagents.createKey("spacelube");
    private static final ResourceKey<ReagentData> POLYTRINIC_ACID = ModReagents.createKey("polytrinicacid");

    private PuddleReactiveTouchGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unsupportedTouchPayloadIsRejectedBeforeDoseSplit(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        helper.setBlock(position.below(), Blocks.STONE);
        helper.setBlock(position, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(position));
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(SPACE_LUBE, 2f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);

        EffectData.CleanBloodstream unsupported = new EffectData.CleanBloodstream(
                EffectCommonData.DEFAULT, ModReagents.createKey("water"), 1f);
        require(!ReactiveTouchSystem.supportsTouchPayload(unsupported),
                "unsupported Touch payload must fail preflight");
        require(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).totalUnits() == 200,
                "preflight rejection must leave puddle dose unchanged");
        helper.succeed();
    }

    /** Direct typed-effect control for the same acid reaction used by puddle Touch. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void directPolytrinicAcidHealthChangeFakePlayerInvulnerabilityAndVillagerDamage(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "acid-direct-control"));
        Villager villager = helper.spawn(EntityType.VILLAGER, helper.absolutePos(new BlockPos(4, 1, 2)));
        player.setInvulnerable(false);
        player.getAbilities().invulnerable = false;
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        villager.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        player.setHealth(20f);
        villager.setHealth(20f);
        putAt(player, helper, new BlockPos(2, 1, 2));
        require(level.addFreshEntity(player), "direct-control FakePlayer must join the GameTest level");
        player.invulnerableTime = 0;
        villager.invulnerableTime = 0;

        var reagent = PrototypeRuntime.serverReagents().get(POLYTRINIC_ACID.location());
        require(reagent != null, "server reagent prototype must load polytrinicacid");
        var acidic = reagent.reactiveEffects().get("acidic");
        require(acidic != null && acidic.methods().contains("touch"),
                "polytrinicacid acidic reaction must contain Touch method");
        var effect = acidic.effects().stream().filter(value -> value instanceof com.juicyslew.moonstation14.component.codec.json.EffectData.HealthChange)
                .map(value -> (com.juicyslew.moonstation14.component.codec.json.EffectData.HealthChange) value)
                .findFirst().orElseThrow(() -> new GameTestAssertException(
                        "polytrinicacid acidic Touch must resolve HealthChange"));
        require(effect.damage().types().getOrDefault("caustic", 0f) == .5f && !effect.ignoreResistances(),
                "control must use resolved polytrinicacid Touch HealthChange (0.5 caustic, resistances enabled)");

        EffectSystem effects = EffectSystem.withDefaults();
        EffectResult playerResult = effects.apply(effect, new EffectContext(level, player, .60f,
                RandomSource.create(0xAC1D), ConditionContext.unavailable(), EffectCause.CONTACT));
        EffectResult villagerResult = effects.apply(effect, new EffectContext(level, villager, .60f,
                RandomSource.create(0xAC1D), ConditionContext.unavailable(), EffectCause.CONTACT));
        float playerCaustic = caustic(player);
        float villagerCaustic = caustic(villager);
        require(playerResult == EffectResult.APPLIED && villagerResult == EffectResult.APPLIED,
                "direct resolved acid HealthChange must be admitted for both targets; player=" + playerResult
                        + ", villager=" + villagerResult);
        require(player.isInvulnerableTo(level.damageSources().generic()),
                "NeoForge FakePlayer is inherently invulnerable; it cannot prove the connected-player damage path");
        require(playerCaustic == 0f && player.getHealth() == 20f,
                "FakePlayer's invulnerability must prevent direct typed damage; caustic=" + playerCaustic
                        + ", health=" + player.getHealth());
        require(Math.abs(villagerCaustic - .3f) < .0001f,
                "direct resolved acid HealthChange must record .30 caustic on damageable Villager; got "
                        + villagerCaustic);
        require(Math.abs(villager.getHealth() - 19.94f) < .001f,
                "direct resolved acid HealthChange must project .30 typed damage as .06 health at 5 typed/health; villager="
                        + villager.getHealth());
        helper.succeed();
    }

    private static float caustic(LivingEntity target) {
        DamageData damage = target.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        return damage == null ? 0f : damage.getMap().getOrDefault(DamageKeys.CAUSTIC, 0f);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void realSlipTouchChanceDoseAndDamageForPlayerAndVillager(GameTestHelper helper) {
        var level = helper.getLevel();
        List<Trial> trials = new ArrayList<>();
        List<SlipEvent> observed = new ArrayList<>();
        Consumer<SlipEvent> observer = observed::add;
        require(SlipSystem.addListener(observer), "register focused SlipEvent observer");
        try {
            Trial fakeAccepted = makeTrial(helper, new BlockPos(2, 1, 2), true, true, "touch-player-accepted");
            Trial fakeRejected = makeTrial(helper, new BlockPos(5, 1, 2), true, false, "touch-player-rejected");
            Trial villagerAccepted = makeTrial(helper, new BlockPos(2, 1, 5), false, true, "touch-villager-accepted");
            Trial villagerRejected = makeTrial(helper, new BlockPos(5, 1, 5), false, false, "touch-villager-rejected");
            trials.addAll(List.of(fakeAccepted, fakeRejected, villagerAccepted, villagerRejected));
            for (Trial trial : trials) {
                ReactiveTouchSystem.registerTestRandom((net.minecraft.server.level.ServerLevel) level,
                        helper.absolutePos(trial.puddlePosition), trial.target.getUUID(), randomFor(trial.accepted));
            }
            helper.runAfterDelay(35, () -> cleanup(trials, observer, helper));

            helper.runAfterDelay(2, () -> {
                try {
                    for (Trial trial : trials) {
                        require(CharacterIdentitySystem.resolve(trial.target).isPresent(), "actor must be bound to human");
                        require(CharacterIdentitySystem.resolve(trial.target).orElseThrow().equals(
                                        CharacterIdentitySystem.resolve(fakeAccepted.target).orElseThrow()),
                                "FakePlayer and Villager must share identical human character policy");
                        // Villagers still dispatch from entityInside; FakePlayers wait for accepted movement.
                        trial.target.setDeltaMovement(.12, 0, 0);
                        Vec3 beforeMove = trial.target.position();
                        trial.target.move(MoverType.SELF, new Vec3(1, 0, 0));
                        if (trial.target instanceof FakePlayer player) {
                            long eventsBeforeAcceptance = observed.stream()
                                    .filter(event -> event.target() == player).count();
                            require(eventsBeforeAcceptance == 0
                                            && !player.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                                    trial.label + " physical contact alone must not slip a player before acceptance");
                            assertDose(trial, false);
                            Vec3 acceptedDisplacement = player.position().subtract(beforeMove);
                            SlipSystem.onAcceptedPlayerMovement((ServerPlayer) player, acceptedDisplacement);
                        }
                    }
                    helper.runAfterDelay(1, () -> {
                        try {
                            for (Trial trial : trials) {
                                long events = observed.stream().filter(event -> event.target() == trial.target).count();
                                require(events == 1, "real block Entity.move must emit exactly one SlipEvent for "
                                        + trial.label + ", got " + events);
                                SlipEvent event = observed.stream().filter(value -> value.target() == trial.target)
                                        .findFirst().orElseThrow();
                                require(!event.wasSliding(), "first slip event must be before sliding starts");
                                require(trial.target.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                                        "actual slip status must apply: " + trial.label);
                                assertDose(trial, trial.accepted);

                                // Repeat overlap movement without leaving: neither a second slip nor a second dose.
                                trial.target.setDeltaMovement(.12, 0, 0);
                                Vec3 beforeRepeat = trial.target.position();
                                trial.target.move(MoverType.SELF, new Vec3(.1, 0, 0));
                                if (trial.target instanceof FakePlayer player) {
                                    Vec3 acceptedDisplacement = player.position().subtract(beforeRepeat);
                                    SlipSystem.onAcceptedPlayerMovement((ServerPlayer) player, acceptedDisplacement);
                                }
                                assertDose(trial, trial.accepted);
                                require(observed.stream().filter(value -> value.target() == trial.target).count() == 1,
                                        "repeated movement in the same contact must not duplicate slip/touch");
                            }
                            cleanup(trials, observer, helper);
                            helper.succeed();
                        } catch (RuntimeException | Error exception) {
                            cleanup(trials, observer, helper);
                            throw exception;
                        }
                    });
                } catch (RuntimeException | Error exception) {
                    cleanup(trials, observer, helper);
                    throw exception;
                }
            });
        } catch (RuntimeException | Error exception) {
            cleanup(trials, observer, helper);
            throw exception;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void slidingSuperSlipperySlipDoesNotSpendReactiveTouchDose(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos firstPosition = new BlockPos(2, 1, 2);
        BlockPos secondPosition = new BlockPos(7, 1, 2);
        // Place the actor outside both sources before either puddle is populated.
        Villager villager = helper.spawn(EntityType.VILLAGER, firstPosition.offset(-1, 0, 0));
        villager.setNoAi(true);
        villager.getAttribute(Attributes.ARMOR).setBaseValue(0f);
        putAt(villager, helper, firstPosition.offset(-1, 0, 0));
        require(CharacterIdentitySystem.resolve(villager).isPresent(), "villager must be bound to human");

        Trial first = makePuddleTrial(helper, firstPosition, villager, "sliding-first-source");
        Trial second = makePuddleTrial(helper, secondPosition, villager, "sliding-second-source");
        List<Trial> trials = List.of(first, second);
        List<SlipEvent> observed = new ArrayList<>();
        Consumer<SlipEvent> observer = event -> {
            if (event.level() == level && event.target() == villager
                    && (event.sourcePosition().equals(helper.absolutePos(firstPosition))
                    || event.sourcePosition().equals(helper.absolutePos(secondPosition)))) {
                observed.add(event);
            }
        };

        try {
            require(SlipSystem.addListener(observer), "register scoped SlipEvent observer");
            for (Trial trial : trials) {
                ReactiveTouchSystem.registerTestRandom(level, helper.absolutePos(trial.puddlePosition),
                        villager.getUUID(), randomFor(true));
            }
            // Failsafe cleanup also covers GameTest timeout while the delayed assertions are pending.
            helper.runAfterDelay(55, () -> cleanup(trials, observer, helper));
            helper.runAfterDelay(2, () -> {
                try {
                    villager.setDeltaMovement(.12, 0, 0);
                    villager.move(MoverType.SELF, new Vec3(1, 0, 0));
                    helper.runAfterDelay(1, () -> {
                        try {
                            require(observed.size() == 1, "actual first Entity.move must admit exactly one source event");
                            SlipEvent firstEvent = observed.get(0);
                            require(firstEvent.sourcePosition().equals(helper.absolutePos(firstPosition))
                                            && firstEvent.source() == first.puddle,
                                    "first event must be scoped to the first populated puddle");
                            require(!firstEvent.wasSliding(), "first slip must begin before sliding starts");
                            require(villager.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                                    "first actual slip must apply knockdown status");
                            assertDose(first, true);
                            require(Math.abs(caustic(villager) - .3f) < .0001f,
                                    "first accepted Touch must apply .30 caustic");

                            // Leave the first contact and make a real move into the distinct second source.
                            putAt(villager, helper, secondPosition.offset(-1, 0, 0));
                            villager.setDeltaMovement(.12, 0, 0);
                            villager.move(MoverType.SELF, new Vec3(1, 0, 0));
                            require(observed.size() == 2,
                                    "actual second Entity.move must emit an event while knockdown is active");
                            SlipEvent secondEvent = observed.get(1);
                            require(secondEvent.sourcePosition().equals(helper.absolutePos(secondPosition))
                                            && secondEvent.source() == second.puddle,
                                    "second event must be scoped to the independent second puddle");
                            require(secondEvent.wasSliding(), "second admitted slip must observe already-sliding state");
                            require(MS14Provider.getDetached(second.puddle, MS14Bridges.REAGENT)
                                            .snapshotUnits().equals(second.initial),
                                    "already-sliding slip must not spend the second source's accepted 15% dose");
                            require(Math.abs(caustic(villager) - .3f) < .0001f,
                                    "already-sliding slip must not apply a second Touch or additional caustic");
                            cleanup(trials, observer, helper);
                            helper.succeed();
                        } catch (RuntimeException | Error exception) {
                            cleanup(trials, observer, helper);
                            throw exception;
                        }
                    });
                } catch (RuntimeException | Error exception) {
                    cleanup(trials, observer, helper);
                    throw exception;
                }
            });
        } catch (RuntimeException | Error exception) {
            cleanup(trials, observer, helper);
            throw exception;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void repeatedSuperSlipperyEntriesTouchOnlyWhenNotAlreadySliding(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos puddlePosition = new BlockPos(3, 1, 3);
        BlockPos secondPosition = puddlePosition.offset(1, 0, 0);
        helper.setBlock(puddlePosition.below(), Blocks.STONE);
        helper.setBlock(puddlePosition, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) level.getBlockEntity(helper.absolutePos(puddlePosition));
        helper.setBlock(secondPosition.below(), Blocks.STONE);
        helper.setBlock(secondPosition, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity secondPuddle = (PuddleBlockEntity) level.getBlockEntity(helper.absolutePos(secondPosition));

        Villager villager = helper.spawn(EntityType.VILLAGER, puddlePosition.offset(-1, 0, 0));
        villager.setNoAi(true);
        villager.getAttribute(Attributes.ARMOR).setBaseValue(0f);
        villager.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        villager.setHealth(20f);
        putAt(villager, helper, puddlePosition.offset(-1, 0, 0));
        require(CharacterIdentitySystem.resolve(villager).isPresent(), "villager must be bound to human");

        ReagentAttachment initial = new ReagentAttachment();
        initial.specificAdd(SPACE_LUBE, 25f, puddle.getCapacity());
        initial.specificAdd(POLYTRINIC_ACID, 25f, puddle.getCapacity());
        require(initial.totalUnits() == 5_000, "fresh fixture must contain exactly 50 units");
        MS14Provider.update(puddle, MS14Bridges.REAGENT, initial);
        Map<ResourceKey<ReagentData>, Long> initialUnits = initial.snapshotUnits();
        ReagentAttachment secondInitial = new ReagentAttachment();
        secondInitial.specificAdd(SPACE_LUBE, 25f, secondPuddle.getCapacity());
        secondInitial.specificAdd(POLYTRINIC_ACID, 25f, secondPuddle.getCapacity());
        require(secondInitial.totalUnits() == 5_000, "second fixture must contain exactly 50 units");
        MS14Provider.update(secondPuddle, MS14Bridges.REAGENT, secondInitial);
        Map<ResourceKey<ReagentData>, Long> secondInitialUnits = secondInitial.snapshotUnits();
        List<SlipEvent> observed = new ArrayList<>();
        Consumer<SlipEvent> observer = event -> {
            if (event.level() == level && event.target() == villager
                    && (event.sourcePosition().equals(helper.absolutePos(puddlePosition))
                    || event.sourcePosition().equals(helper.absolutePos(secondPosition)))) observed.add(event);
        };
        require(SlipSystem.addListener(observer), "register scoped SlipEvent observer");
        BlockPos absoluteSource = helper.absolutePos(puddlePosition);
        BlockPos absoluteSecondSource = helper.absolutePos(secondPosition);
        Trial firstTrial = new Trial(puddlePosition, puddle, villager, true, "repeated-entry-first", initialUnits);
        Trial secondTrial = new Trial(secondPosition, secondPuddle, villager, true, "repeated-entry-second",
                secondInitialUnits);
        List<Trial> trials = List.of(firstTrial, secondTrial);
        try {
            ReactiveTouchSystem.registerTestRandom(level, absoluteSource, villager.getUUID(), randomFor(true));
            ReactiveTouchSystem.registerTestRandom(level, absoluteSecondSource, villager.getUUID(), randomFor(true));
            // Failsafe also clears scoped global test hooks if a delayed assertion times out.
            helper.runAfterDelay(50, () -> cleanup(trials, observer, helper));
            helper.runAfterDelay(2, () -> {
                try {
                    var firstDose = ReagentUnits.split(initialUnits,
                            ReactiveTouchSystem.touchRequestCents(ReagentUnits.total(initialUnits.values())));
                    enterByRealMove(villager, helper, puddlePosition);
                    require(observed.size() == 1 && !observed.get(0).wasSliding(),
                            "first admitted event must observe pre-slip standing state");
                    assertRemainder(puddle, initialUnits, firstDose, "first accepted Touch");
                    require(ReagentUnits.total(firstDose.values()) == 750L,
                            "first accepted Touch must remove exactly 7.50 units from the source");
                    require(firstDose.get(POLYTRINIC_ACID) == 375L,
                            "first 15% split from 25/25 must dose exactly 3.75 acid");
                    require(Math.abs(caustic(villager) - 1.875f) < .001f,
                            "first accepted Touch must apply approximately 1.875 typed caustic; got " + caustic(villager));

                    helper.runAfterDelay(1, () -> {
                        try {
                            // Move directly from the first puddle into an adjacent independent source, without
                            // ending either contact or clearing the sliding attachment between entries.
                            villager.setDeltaMovement(.25, 0, 0);
                            villager.move(MoverType.SELF, new Vec3(1, 0, 0));
                            require(observed.size() == 2
                                            && observed.get(1).sourcePosition().equals(absoluteSecondSource)
                                            && observed.get(1).source() == secondPuddle
                                            && observed.get(1).wasSliding(),
                                    "second source event during continuous contact must observe already-sliding state");
                            require(MS14Provider.getDetached(secondPuddle, MS14Bridges.REAGENT)
                                            .snapshotUnits().equals(secondInitialUnits),
                                    "already-sliding entry must leave the second source's contents unchanged");
                            require(Math.abs(caustic(villager) - 1.875f) < .001f,
                                    "already-sliding event must not apply another Touch dose");

                            helper.runAfterDelay(1, () -> {
                                try {
                                    // Exit beyond both adjacent qualifying sources. Contact exit clears Sliding
                                    // independently of knockdown, which is checked before the status is cleared.
                                    putAt(villager, helper, secondPosition.offset(2, 0, 0));
                                    SlipSystem.reconcileTarget(villager);
                                    require(!SlipSystem.isSliding(villager),
                                            "exiting all qualifying sources must clear Sliding while knockdown remains active");
                                    require(villager.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                                            "physical contact exit must not clear the active knockdown status");
                                    com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem.clearAll(
                                            ((com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait) villager)
                                                    .toHandleSelf(), level);
                                    SlipSystem.reconcileTarget(villager);
                                    require(!SlipSystem.isSliding(villager),
                                            "external status clear must reconcile sliding state");
                                    ReactiveTouchSystem.unregisterTestRandom(level, absoluteSource, villager.getUUID());
                                    ReactiveTouchSystem.registerTestRandom(level, absoluteSource,
                                            villager.getUUID(), randomFor(true));
                                    enterByRealMove(villager, helper, puddlePosition);
                                    assertThirdTouch(villager, puddle, initialUnits, firstDose, observed);
                                    cleanup(trials, observer, helper);
                                    helper.succeed();
                                } catch (RuntimeException | Error exception) {
                                    cleanup(trials, observer, helper);
                                    throw exception;
                                }
                            });
                        } catch (RuntimeException | Error exception) {
                            cleanup(trials, observer, helper);
                            throw exception;
                        }
                    });
                } catch (RuntimeException | Error exception) {
                    cleanup(trials, observer, helper);
                    throw exception;
                }
            });
        } catch (RuntimeException | Error exception) {
            cleanup(trials, observer, helper);
            throw exception;
        }
    }

    private static void assertThirdTouch(Villager villager, PuddleBlockEntity puddle,
                                         Map<ResourceKey<ReagentData>, Long> initialUnits,
                                         Map<ResourceKey<ReagentData>, Long> firstDose, List<SlipEvent> observed) {
        require(observed.size() == 3 && !observed.get(2).wasSliding(),
                "third event after status reconciliation must observe standing state");
        var afterFirst = new LinkedHashMap<>(initialUnits);
        firstDose.forEach((key, cents) -> afterFirst.compute(key, (ignored, value) -> value - cents));
        var thirdDose = ReagentUnits.split(afterFirst,
                ReactiveTouchSystem.touchRequestCents(ReagentUnits.total(afterFirst.values())));
        assertRemainder(puddle, afterFirst, thirdDose, "third accepted Touch");
        float expectedCausticIncrease = ReagentUnits.toFloat(thirdDose.get(POLYTRINIC_ACID)) * .5f;
        require(Math.abs(expectedCausticIncrease - 1.59375f) < .01f,
                "shared proportional split should calculate the expected third typed dose");
        require(Math.abs(caustic(villager) - (1.875f + expectedCausticIncrease)) < .01f,
                "third Touch must apply typed caustic inside vanilla hurt cooldown; expected increase "
                        + expectedCausticIncrease + ", actual total " + caustic(villager));
    }

    private static void enterByRealMove(Villager villager, GameTestHelper helper, BlockPos puddlePosition) {
        putAt(villager, helper, puddlePosition.offset(-1, 0, 0));
        // 25/25 weights Space Lube and acid to a 4.5 blocks/s slip threshold.
        villager.setDeltaMovement(.25, 0, 0);
        villager.move(MoverType.SELF, new Vec3(1, 0, 0));
    }

    private static void exitAndReconcile(Villager villager, GameTestHelper helper, BlockPos puddlePosition) {
        putAt(villager, helper, puddlePosition.offset(2, 0, 0));
        SlipSystem.reconcileTarget(villager);
    }

    private static void assertRemainder(PuddleBlockEntity puddle, Map<ResourceKey<ReagentData>, Long> before,
                                        Map<ResourceKey<ReagentData>, Long> removed, String context) {
        ReagentAttachment current = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
        Map<ResourceKey<ReagentData>, Long> expected = new LinkedHashMap<>(before);
        removed.forEach((key, cents) -> expected.compute(key, (ignored, value) -> value - cents));
        require(current.snapshotUnits().equals(expected), context + " must leave exact shared-split source remainder");
    }

    private static Trial makePuddleTrial(GameTestHelper helper, BlockPos puddlePosition,
                                         LivingEntity target, String label) {
        BlockPos floor = puddlePosition.below();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(puddlePosition, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(puddlePosition));
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(SPACE_LUBE, 16f, puddle.getCapacity());
        contents.specificAdd(POLYTRINIC_ACID, 4f, puddle.getCapacity());
        require(contents.totalUnits() == 2_000, label + " fixture must contain exactly 20 units");
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);
        return new Trial(puddlePosition.immutable(), puddle, target, true, label, contents.snapshotUnits());
    }

    private static Trial makeTrial(GameTestHelper helper, BlockPos puddlePosition, boolean fakePlayer,
                                   boolean accepted, String label) {
        BlockPos outside = puddlePosition.offset(-1, 0, 0);
        LivingEntity target;
        if (fakePlayer) {
            FakePlayer player = new FakePlayer((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), label));
            player.setInvulnerable(false);
            player.setHealth(10f);
            putAt(player, helper, outside);
            require(helper.getLevel().addFreshEntity(player), "FakePlayer must join the GameTest server level");
            target = player;
        } else {
            Villager villager = helper.spawn(EntityType.VILLAGER, outside);
            villager.setNoAi(true);
            villager.getAttribute(Attributes.ARMOR).setBaseValue(0f);
            putAt(villager, helper, outside);
            target = villager;
        }

        BlockPos floor = puddlePosition.below();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(puddlePosition, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(puddlePosition));
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(SPACE_LUBE, 16f, puddle.getCapacity());
        contents.specificAdd(POLYTRINIC_ACID, 4f, puddle.getCapacity());
        require(contents.totalUnits() == 2_000, "each independent fixture starts with exactly 20 units");
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);

        require(CharacterIdentitySystem.resolve(target).isPresent(), "real actor must receive the human binding");
        return new Trial(puddlePosition.immutable(), puddle, target, accepted, label,
                contents.snapshotUnits());
    }

    private static void assertDose(Trial trial, boolean accepted) {
        ReagentAttachment current = MS14Provider.getDetached(trial.puddle, MS14Bridges.REAGENT);
        var now = current.snapshotUnits();
        if (accepted) {
            require(current.totalUnits() == 1_700, trial.label + " accepted touch must remove 15% of current 20 units");
            require(trial.initial.get(SPACE_LUBE) - now.getOrDefault(SPACE_LUBE, 0L) == 240,
                    trial.label + " must transfer 2.40 Space Lube by common proportional split");
            require(trial.initial.get(POLYTRINIC_ACID) - now.getOrDefault(POLYTRINIC_ACID, 0L) == 60,
                    trial.label + " must transfer 0.60 polytrinic acid by common proportional split");
            DamageData damage = trial.target.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            float caustic = damage == null ? 0f : damage.getMap().getOrDefault(DamageKeys.CAUSTIC, 0f);
            if (trial.target instanceof FakePlayer) {
                require(caustic == 0f && trial.target.getHealth() == 10f,
                        trial.label + " FakePlayer Touch dose is consumed but FakePlayer is invulnerable; got caustic="
                                + caustic + ", health=" + trial.target.getHealth());
            } else {
                require(Math.abs(caustic - .3f) < .0001f,
                        trial.label + " Touch HealthChange must apply scaled 0.30 caustic; got " + caustic
                                + ", health=" + trial.target.getHealth() + ", hasDamage="
                                + trial.target.hasData(ModDataAttachments.DAMAGE.get()) + ", ledger="
                                + (damage == null ? null : damage.getMap()));
            }
        } else {
            require(now.equals(trial.initial), trial.label + " rejected 50% chance must preserve every source reagent");
            DamageData damage = trial.target.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            require(damage == null || damage.getMap().getOrDefault(DamageKeys.CAUSTIC, 0f) == 0f,
                    trial.label + " rejected chance must not apply caustic damage");
        }
        require(now.values().stream().mapToLong(Long::longValue).sum()
                        + (accepted ? 300 : 0) == 2_000,
                trial.label + " source remainder plus accepted dose must conserve total cent volume");
    }

    private static RandomSource randomFor(boolean accepted) {
        for (long seed = 0; seed < 10_000; seed++) {
            RandomSource candidate = RandomSource.create(seed);
            if ((candidate.nextFloat() < .5f) == accepted) return RandomSource.create(seed);
        }
        throw new IllegalStateException("could not find a deterministic server RNG seed");
    }

    private static void putAt(Entity entity, GameTestHelper helper, BlockPos position) {
        BlockPos absolute = helper.absolutePos(position);
        entity.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
    }

    private static void cleanup(List<Trial> trials, Consumer<SlipEvent> observer, GameTestHelper helper) {
        for (Trial trial : trials) {
            ReactiveTouchSystem.unregisterTestRandom((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                    helper.absolutePos(trial.puddlePosition), trial.target.getUUID());
        }
        SlipSystem.removeListener(observer);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private record Trial(BlockPos puddlePosition, PuddleBlockEntity puddle, LivingEntity target,
                         boolean accepted, String label, java.util.Map<ResourceKey<ReagentData>, Long> initial) { }
}
