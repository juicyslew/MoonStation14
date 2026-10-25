package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.ScopedPrototypeCatalogFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.botany.processing.GrinderProgram;
import com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestTags({"botany", "botany-grinder-juicing"})
@PrefixGameTestTemplate(false)
public final class GrinderJuicingGameTests {
    private static final ResourceLocation TOMATO = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
    private static final ResourceLocation GLASS_PANE = ResourceLocation.fromNamespaceAndPath("minecraft", "glass_pane");

    private GrinderJuicingGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "catalog-grinder-juice-output")
    public static void juiceUsesResolvedOutputAndConsumesHarvestAtNormalDeadline(GameTestHelper helper) {
        var setup = setup(helper);
        var tomato = ProduceFactory.createHarvest(TOMATO, 10);
        // A real harvest starts with edible chemistry; JUICE must replace it with the resolved output.
        require(!tomato.get(ModDataComponents.REAGENT.get()).centContents().isEmpty(), "harvest has source food chemistry");
        var genetics = tomato.get(ModDataComponents.GENETIC_VALUE.get());
        var tokens = setup.insert(tomato, "tomato");
        var machine = setup.machine;
        require(machine.canStart(GrinderProgram.Operation.JUICE), "resolved tomato is JUICE-eligible");
        require(machine.startTrusted(machine.startProof(), setup.level.getGameTime(), true, GrinderProgram.Operation.JUICE),
                "trusted domain entry starts selected JUICE operation");
        require(machine.program().operation() == GrinderProgram.Operation.JUICE, "program retains selected JUICE operation");
        require(machine.resolvedProfile().juiceSound() != null
                && machine.resolvedProfile().juiceSound().sound().equals(ResourceLocation.fromNamespaceAndPath("moonstation14", "botany/grinder/juicer")),
                "resolved juice profile selects the registered juicer cue");
        helper.runAfterDelay(70, () -> {
            try {
                var output = setup.beakerSlot.snapshot().stack().get(ModDataComponents.REAGENT.get());
                var juice = ResourceLocation.fromNamespaceAndPath("moonstation14", "juicetomato");
                require(output.centContents().size() == 1 && output.centContents().getOrDefault(ModReagents.createKey("juicetomato"), 0L) == 1000,
                        "completion replaces food chemistry with exactly 1000 cents of tomato juice");
                require(setup.chamberSlot.snapshot().stack().isEmpty(), "completed JUICE consumes tomato harvest");
                require(setup.ledger.snapshot().locationOf(tokens.source).isEmpty(), "consumed harvest token leaves ledger");
                require(genetics != null, "harvest carries registered genetics");
                require(output.centContents().keySet().stream().allMatch(key -> key.location().equals(juice)), "no food reagents remain in beaker");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("actual grinder JUICE completion: " + failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 30, batch = "catalog-grinder-juice-mixed-reject")
    public static void juiceRejectsMixedInputsBeforeStartingButGrindRemainsAvailable(GameTestHelper helper) {
        var setup = setup(helper);
        setup.insert(ProduceFactory.createHarvest(TOMATO, 10), "tomato");
        setup.insert(ProduceFactory.createHarvest(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), 5), "wheat");
        var before = setup.beakerSlot.snapshot();
        require(!setup.machine.canStart(GrinderProgram.Operation.JUICE), "mixed eligible/ineligible chamber denies JUICE");
        require(!setup.machine.startTrusted(setup.machine.startProof(), setup.level.getGameTime(), true, GrinderProgram.Operation.JUICE),
                "mixed input rejection occurs before program start");
        require(!setup.machine.program().active(), "rejected JUICE leaves machine inactive");
        require(setup.chamberSlot.snapshot().stack().getItem() == ModItems.TOMATO.get(), "rejected JUICE consumes no tomato");
        require(setup.machine.chamberSlots().get(1).snapshot().stack().getItem() == ModItems.WHEAT_BUSHEL.get(), "rejected JUICE consumes no wheat");
        require(setup.beakerSlot.snapshot().revision() == before.revision()
                && ItemStack.matches(setup.beakerSlot.snapshot().stack(), before.stack()), "rejected JUICE leaves beaker unchanged");
        require(setup.machine.canStart(GrinderProgram.Operation.GRIND), "same populated chamber remains eligible for GRIND");
        require(setup.machine.startTrusted(setup.machine.startProof(), setup.level.getGameTime(), true, GrinderProgram.Operation.GRIND),
                "same mixed inputs actually start selected GRIND mode");
        require(setup.machine.program().active() && setup.machine.program().operation() == GrinderProgram.Operation.GRIND,
                "successful mixed-input operation is active GRIND, not JUICE");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "catalog-grinder-juice-nonfit")
    public static void juiceOutputThatDoesNotFitRetainsWholeInputAndBeaker(GameTestHelper helper) {
        var beaker = new ItemStack(ModItems.BEAKER.get());
        long capacity = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(
                ((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) beaker.getItem()).getCapacity(beaker, helper.getLevel()));
        var initial = ReagentComponent.fromCents(Map.of(ModReagents.createKey("water"), capacity - 900));
        var setup = setup(helper, initial);
        var tomato = ProduceFactory.createHarvest(TOMATO, 10);
        var tokens = setup.insert(tomato, "nonfit-tomato");
        var beakerBefore = setup.beakerSlot.snapshot();
        var tomatoBefore = setup.chamberSlot.snapshot();
        require(setup.machine.canStart(GrinderProgram.Operation.JUICE), "source eligibility does not depend on free beaker capacity");
        require(setup.machine.startTrusted(setup.machine.startProof(), setup.level.getGameTime(), true, GrinderProgram.Operation.JUICE),
                "trusted domain start accepts juice even though full output cannot fit");
        helper.runAfterDelay(70, () -> {
            try {
                var beakerAfter = setup.beakerSlot.snapshot();
                var tomatoAfter = setup.chamberSlot.snapshot();
                require(ItemStack.matches(beakerBefore.stack(), beakerAfter.stack()), "non-fitting juice leaves beaker contents unchanged");
                require(beakerBefore.revision() == beakerAfter.revision(), "non-fitting juice does not commit a partial beaker update");
                require(ItemStack.matches(tomatoBefore.stack(), tomatoAfter.stack())
                        && tomatoBefore.token().equals(tomatoAfter.token()), "non-fitting juice retains entire tomato stack and ownership token");
                require(setup.ledger.snapshot().locationOf(tokens.source).orElseThrow()
                        .equals(new ItemLocation.MachineSlot(setup.chamberSlot.ownerId(), setup.chamberSlot.slotId())),
                        "unconsumed harvest remains in its actual owned machine slot");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("non-fitting grinder JUICE completion: " + failure); }
        });
    }

    @GameTestTags({"botany", "botany-grinder-glass-grind"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "grinder-glass-grind")
    public static void grindInitializesGlassPaneFromResolvedFoodSolution(GameTestHelper helper) {
        var foods = ScopedPrototypeCatalogFixture.replace(helper,
                com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE, map -> {
                    map.put(GLASS_PANE, new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(
                            500, Map.of(ResourceLocation.fromNamespaceAndPath("moonstation14", "silicon"), 500L), true, "shardglass"));
                    return map;
                });
        ScopedPrototypeCatalogFixture.Handle extracts = null;
        try {
            extracts = ScopedPrototypeCatalogFixture.replace(helper,
                    com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE, map -> {
                        map.put(GLASS_PANE, new com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile("shardglass", null));
                        return map;
                    });
            var setup = setup(helper);
            var tokens = setup.insert(new ItemStack(net.minecraft.world.item.Items.GLASS_PANE), "glass-pane");
            require(setup.machine.canStart(GrinderProgram.Operation.GRIND), "resolved glass pane is GRIND-eligible");
            require(setup.machine.startTrusted(setup.machine.startProof(), setup.level.getGameTime(), true), "trusted start begins GRIND");
            helper.runAfterDelay(70, () -> {
                try {
                    var output = setup.beakerSlot.snapshot().stack().get(ModDataComponents.REAGENT.get());
                    require(output.centContents().size() == 1
                            && output.centContents().getOrDefault(ModReagents.createKey("silicon"), 0L) == 500,
                            "lazy source initializer supplies resolved silicon solution");
                    require(setup.chamberSlot.snapshot().stack().isEmpty(), "GRIND consumes glass pane");
                    require(setup.ledger.snapshot().locationOf(tokens.source).isEmpty(), "consumed source token is retired");
                    require(!setup.machine.program().active(), "completed GRIND is inactive");
                    helper.succeed();
                } catch (Throwable failure) { helper.fail("glass pane GRIND completion: " + failure); }
            });
        } catch (Throwable failure) {
            if (extracts != null) extracts.restore();
            foods.restore();
            throw failure;
        }
    }

    @GameTestTags({"botany", "botany-grinder-glass-grind-mismatch"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "grinder-glass-grind-mismatch")
    public static void grindRejectsAndRevalidatesChangedExtractableName(GameTestHelper helper) {
        var foods = ScopedPrototypeCatalogFixture.replace(helper,
                com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE, map -> {
                    map.put(GLASS_PANE, new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(
                            500, Map.of(ResourceLocation.fromNamespaceAndPath("moonstation14", "silicon"), 500L), true, "shardglass"));
                    return map;
                });
        var extractType = com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE;
        var extracts = ScopedPrototypeCatalogFixture.replace(helper, extractType, map -> {
            map.put(GLASS_PANE, new com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile("food", null));
            return map;
        });
        try {
            var setup = setup(helper);
            var tokens = setup.insert(new ItemStack(net.minecraft.world.item.Items.GLASS_PANE), "mismatch-glass-pane");
            var sourceBefore = setup.chamberSlot.snapshot();
            var beakerBefore = setup.beakerSlot.snapshot();
            var locationBefore = setup.ledger.snapshot().locationOf(tokens.source);
            require(!sourceBefore.stack().has(ModDataComponents.REAGENT.get()), "fresh glass pane starts without seeded source chemistry");
            require(!setup.machine.canStart(GrinderProgram.Operation.GRIND), "configured name mismatch denies GRIND eligibility");
            require(!setup.machine.startTrusted(setup.machine.startProof(), setup.level.getGameTime(), true), "trusted start rejects mismatched source name");
            requireSlotUnchanged(sourceBefore, setup.chamberSlot.snapshot(), "rejected start preserves source slot");
            requireSlotUnchanged(beakerBefore, setup.beakerSlot.snapshot(), "rejected start preserves beaker");
            require(setup.ledger.snapshot().locationOf(tokens.source).equals(locationBefore), "rejected start preserves source ownership");
            var configured = new java.util.HashMap<>(com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager().snapshot(extractType).asMap());
            configured.put(GLASS_PANE, new com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile("shardglass", null));
            com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager().publishDecoded(extractType, configured);
            require(setup.machine.canStart(GrinderProgram.Operation.GRIND), "matching name enables GRIND");
            require(setup.machine.startTrusted(setup.machine.startProof(), setup.level.getGameTime(), true), "actual trusted start succeeds with matching name");
            configured.put(GLASS_PANE, new com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile("food", null));
            com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager().publishDecoded(extractType, configured);
            require(setup.machine.program().active(), "program remains active after catalog mismatch is introduced");
            helper.runAfterDelay(70, () -> {
                try {
                    requireSlotUnchanged(sourceBefore, setup.chamberSlot.snapshot(), "completion revalidation preserves source");
                    requireSlotUnchanged(beakerBefore, setup.beakerSlot.snapshot(), "completion revalidation preserves beaker");
                    require(setup.ledger.snapshot().locationOf(tokens.source).equals(locationBefore), "completion revalidation preserves ownership");
                    require(setup.beakerSlot.snapshot().stack().get(ModDataComponents.REAGENT.get()).centContents().isEmpty(), "mismatch produces no silicon output");
                    require(!setup.machine.program().active(), "mismatched completion retires program");
                    helper.succeed();
                } catch (Throwable failure) { helper.fail("GRIND catalog mismatch revalidation: " + failure); }
            });
        } catch (Throwable failure) {
            extracts.restore();
            foods.restore();
            throw failure;
        }
    }

    private static void requireSlotUnchanged(com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot.Snapshot before,
                                             com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot.Snapshot after, String message) {
        require(before.revision() == after.revision() && java.util.Objects.equals(before.token(), after.token())
                && ItemStack.matches(before.stack(), after.stack()), message);
    }

    @GameTestTags({"botany", "botany-grinder-power", "botany-grinder-juicing"})
    @GameTest(template = "power_receiver_tall_clear", templateNamespace = "moonstation14", timeoutTicks = 220)
    public static void juiceContinuesThroughRealApcPowerLoss(GameTestHelper helper) {
        var power = AirAlarmPowerFixture.place(helper);
        ServerLevel level = power.level();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 12, 8));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.REAGENT_GRINDER.get().defaultBlockState(), 3);
        helper.runAfterDelay(2, () -> {
            try {
                var setup = setupAt(level, pos, new ReagentComponent(Map.of()));
                var tokens = setup.insert(ProduceFactory.createHarvest(TOMATO, 10), "powered-tomato");
                PowerFixtureReadiness.await(helper, level, 55,
                        () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level,
                                new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(power.cable(), net.minecraft.core.Direction.WEST,
                                        com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC)),
                        () -> "APC topology ready before juicing", () -> {
                            require(setup.machine.canStart(GrinderProgram.Operation.JUICE), "powered tomato is juice eligible");
                            require(setup.machine.startTrusted(setup.machine.startProof(), level.getGameTime(), true, GrinderProgram.Operation.JUICE),
                                    "trusted domain entry starts JUICE on powered machine");
                            long started = level.getGameTime();
                            require(setup.machine.program().operation() == GrinderProgram.Operation.JUICE, "active operation is JUICE before outage");
                            PowerFixtureReadiness.await(helper, level, 35,
                                    () -> com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, setup.machine).known()
                                            && com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, setup.machine).deliveredWatts() == 750,
                                    () -> "await actual 750 W allocation", () -> {
                                        var apc = (com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity) level.getBlockEntity(power.apc());
                                        require(apc != null && apc.toggleBreakerAuthorized(true, 0), "open actual APC breaker through power API");
                                        require(!setup.machine.isPowered(), "actual breaker loss makes powered grinder unavailable");
                                        PowerFixtureReadiness.await(helper, level, 35, () -> !setup.machine.isPowered(),
                                                () -> "wait for real power projection to go offline", () -> {
                                                    require(setup.machine.program().active()
                                                            && setup.machine.program().operation() == GrinderProgram.Operation.JUICE,
                                                            "power loss preserves active selected JUICE operation");
                                                    require(level.getBlockState(pos).getValue(ReagentGrinderBlock.WORKING),
                                                            "working projection remains on while selected program continues through outage");
                                                    require(level.getGameTime() < started + setup.machine.resolvedProfile().startDelayTicks(),
                                                            "breaker outage is observed before normal program deadline");
                                                    long remaining = Math.max(1, started + 70 - level.getGameTime());
                                                    helper.runAfterDelay((int) remaining, () -> {
                                                        try {
                                                            var output = setup.beakerSlot.snapshot().stack().get(ModDataComponents.REAGENT.get());
                                                            require(output.centContents().size() == 1
                                                                    && output.centContents().getOrDefault(ModReagents.createKey("juicetomato"), 0L) == 1000,
                                                                    "normal deadline completes JUICE output after outage");
                                                            require(setup.chamberSlot.snapshot().stack().isEmpty()
                                                                    && setup.ledger.snapshot().locationOf(tokens.source).isEmpty(),
                                                                    "power loss does not prevent consuming juice-eligible tomato");
                                                            require(!setup.machine.program().active(), "completion clears active program");
                                                            require(!level.getBlockState(pos).getValue(ReagentGrinderBlock.WORKING),
                                                                    "normal completion clears working projection after outage");
                                                            helper.succeed();
                                                        } catch (Throwable failure) { helper.fail("JUICE after real APC power loss: " + failure); }
                                                    });
                                                });
                                    });
                        });
            } catch (Throwable failure) { helper.fail("powered JUICE fixture: " + failure); }
        });
    }

    private static Setup setup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.REAGENT_GRINDER.get().defaultBlockState(), 3);
        return setupAt(level, pos, new ReagentComponent(Map.of()));
    }

    private static Setup setup(GameTestHelper helper, ReagentComponent beakerContents) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.REAGENT_GRINDER.get().defaultBlockState(), 3);
        return setupAt(level, pos, beakerContents);
    }

    private static Setup setupAt(ServerLevel level, BlockPos pos, ReagentComponent beakerContents) {
        var machine = (ReagentGrinderBlockEntity) level.getBlockEntity(pos);
        require(machine != null && machine.isPowered(), "loaded grinder is available");
        var ledger = LevelItemLocationLedgers.forLevel(level);
        var beaker = new ItemStack(ModItems.BEAKER.get());
        beaker.set(ModDataComponents.REAGENT.get(), beakerContents);
        var beakerToken = new ItemToken("grinder-juice-beaker-" + UUID.randomUUID());
        var beakerOrigin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        require(ledger.register(beakerToken, beakerOrigin).accepted(), "register beaker owner");
        var beakerSlot = machine.beakerSlot();
        require(beakerSlot.insert(beakerSlot.snapshot().revision(), ledger.snapshot().revision(), beakerToken, beakerOrigin, beaker),
                "insert beaker through machine-owned slot");
        var first = machine.chamberSlots().get(0);
        return new Setup(level, machine, ledger, beakerSlot, first);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class Setup {
        final ServerLevel level;
        final ReagentGrinderBlockEntity machine;
        final com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger ledger;
        final com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot beakerSlot, chamberSlot;

        Setup(ServerLevel level, ReagentGrinderBlockEntity machine, com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger ledger,
              com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot beakerSlot,
              com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot chamberSlot) {
            this.level = level; this.machine = machine; this.ledger = ledger; this.beakerSlot = beakerSlot;
            this.chamberSlot = chamberSlot;
        }

        Tokens insert(ItemStack stack, String label) {
            var token = new ItemToken("grinder-juice-" + label + "-" + UUID.randomUUID());
            var origin = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
            require(ledger.register(token, origin).accepted(), "register " + label + " owner");
            var slot = machine.chamberSlots().stream().filter(value -> value.snapshot().stack().isEmpty()).findFirst().orElseThrow();
            require(slot.insert(slot.snapshot().revision(), ledger.snapshot().revision(), token, origin, stack), "insert " + label + " through owned chamber slot");
            return new Tokens(token);
        }
    }
    private record Tokens(ItemToken source) { }
}
