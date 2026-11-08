package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.ScopedPrototypeCatalogFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.medical.SyringeStackData;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.SyringeItem;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "chemistry", "physiology", "interaction", "loose-objects"})
public final class BodyLooseJugSipGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> SODIUM =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "sodium"));
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> CHLORINE =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "chlorine"));
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> TABLESALT =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "tablesalt"));

    private BodyLooseJugSipGameTests() { }

    @GameTestTags({"hands-liquid", "beaker"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void resolvedJugAndBeakerPoliciesPourFiveAcrossContainerKinds(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "human body created");
        body.setNoAi(true); body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        body.setYRot(-90); body.setYHeadRot(-90);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level,
                com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID), "human body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "hands initialized");
        for (ItemStack prototype : new ItemStack[]{new ItemStack(ModItems.JUG.get()), new ItemStack(ModItems.BEAKER.get())}) {
            for (ItemStack receiver : new ItemStack[]{new ItemStack(ModItems.JUG.get()), new ItemStack(ModItems.BEAKER.get())}) {
                ItemStack filled = prototype.copy(); filled.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 10f)));
                LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                String hand = empty.activeHand(), token = "pair-" + UUID.randomUUID();
                var inserted = empty.putWhole(empty.revision(), hand, new com.juicyslew.moonstation14.ms14.hands.ItemToken(token), filled);
                require(inserted.succeeded(), "real registered source inserted"); body.setData(ModDataAttachments.LIVE_HANDS.get(), inserted.state());
                LooseItemEntity target = LooseItemEntity.create(level,
                        new Vec3(origin.getX() + 2.5, origin.getY() + 1.2, origin.getZ() + .5), receiver);
                target.setNoPickUpDelay(); require(level.addFreshEntity(target), "real registered recipient spawned");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position());
                LiveHands before = inserted.state();
                require(BodyLiquidInteraction.fillOwned(body, target.getUUID(), hand, token, before.revision(), false, () -> true)
                        == BodyLiquidInteraction.Result.SUCCESS, "resolved source drain/target refill policies permit "
                        + prototype.getItem() + " -> " + receiver.getItem());
                LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(water(after.stackCopy(hand).orElseThrow()) == 5f && water(target.getItem()) == 5f,
                        "five units moved across independently named logical solutions");
                require(waterCents(after.stackCopy(hand).orElseThrow()) + waterCents(target.getItem()) == 1000,
                        "integer-cent conservation");
                target.discard();
                var removed = after.takeWhole(after.revision(), hand, new com.juicyslew.moonstation14.ms14.hands.ItemToken(token));
                require(removed.succeeded(), "fixture releases prior pair source");
                body.setData(ModDataAttachments.LIVE_HANDS.get(), removed.state());
            }
        }
        helper.succeed();
    }

    @GameTestTags({"hands-liquid", "beaker", "chemmaster-reactions"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void ownedJugPourReactsInRecipientBeaker(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "human body created");
        body.setNoAi(true); body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        body.setYRot(-90); body.setYHeadRot(-90);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level,
                com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID), "human body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "hands initialized");
        ItemStack source = new ItemStack(ModItems.JUG.get());
        source.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(CHLORINE, 5f)));
        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String hand = empty.activeHand(), token = "reaction-pour";
        var inserted = empty.putWhole(empty.revision(), hand, new com.juicyslew.moonstation14.ms14.hands.ItemToken(token), source);
        require(inserted.succeeded(), "real registered jug inserted"); body.setData(ModDataAttachments.LIVE_HANDS.get(), inserted.state());
        ItemStack recipient = new ItemStack(ModItems.BEAKER.get());
        recipient.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(SODIUM, 5f)));
        LooseItemEntity target = LooseItemEntity.create(level,
                new Vec3(origin.getX() + 2.5, origin.getY() + 1.2, origin.getZ() + .5), recipient);
        target.setNoPickUpDelay(); require(level.addFreshEntity(target), "real registered recipient beaker spawned");
        body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position());
        require(BodyItemTargetProbe.probeSelected(level, body, target.getUUID(), BodyItemTargetProbe.MAX_REACH) != null,
                "recipient is reachable by the live item target probe");
        require(((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) source.getItem()).drainableSolution(source, level).enabled()
                        && ((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) recipient.getItem()).refillableSolution(recipient, level).enabled(),
                "resolved jug drain and beaker refill policies are enabled");
        LiveHands before = inserted.state();
        var pourResult = BodyLiquidInteraction.fillOwned(body, target.getUUID(), hand, token, before.revision(), false, () -> true);
        require(pourResult == BodyLiquidInteraction.Result.SUCCESS, "owned jug-to-beaker pour committed: " + pourResult);
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var destination = target.getItem().get(ModDataComponents.REAGENT.get());
        var remainingSource = after.stackCopy(hand).orElseThrow().get(ModDataComponents.REAGENT.get());
        require(destination != null && destination.centContents().getOrDefault(TABLESALT, 0L) == 1000L
                        && !destination.centContents().containsKey(SODIUM) && !destination.centContents().containsKey(CHLORINE),
                "actual pour recipient runs the registered sodium/chlorine recipe into ten units of table salt");
        require(remainingSource != null && remainingSource.centContents().getOrDefault(CHLORINE, 0L) == 0L,
                "source jug loses exactly the accepted five-unit chlorine dose");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void emptyHandSipsLooseJugConservingContents(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "human body created");
        body.setNoAi(true);
        body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body), "body spawned");
        require(CharacterIdentitySystem.enroll(body, level, com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID),
                "human identity bound");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body),
                "live hands and body system initialized");
        require(StomachSystem.isEligible(body), "human body has eligible stomach");
        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(empty != null && empty.activeHand().equals("left") && empty.revision() == 0,
                "active hand starts empty at revision zero");

        ItemStack jug = new ItemStack(ModItems.JUG.get());
        jug.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 10f)));
        LooseItemEntity target = LooseItemEntity.create(level,
                new Vec3(origin.getX() + 2.5, origin.getY() + 1.2, origin.getZ() + .5), jug);
        target.setNoPickUpDelay();
        require(level.addFreshEntity(target), "filled loose jug spawned");
        var targetId = target.getUUID();

        require(BodyLiquidInteraction.sipOwned(body, targetId, empty.activeHand(), null, empty.revision(), () -> true)
                == BodyLiquidInteraction.Result.SUCCESS, "trusted body route sips loose jug");
        require(!target.isRemoved() && level.getEntity(targetId) == target
                        && water(target.getItem()) == 5f,
                "same world jug remains with five units");
        require(water(MS14Provider.get(body, MS14Bridges.STOMACH).getMap()) == 5f,
                "stomach receives the exact five-unit dose");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after != null && after.activeHand().equals("left") && after.stackCopy("left").isEmpty(),
                "empty active hand remains empty");
        require(BodyLiquidInteraction.sipOwned(body, targetId, after.activeHand(), null, after.revision(), () -> true)
                        == BodyLiquidInteraction.Result.DENIED,
                "immediate repeat is rejected by sip cooldown");
        require(water(target.getItem()) == 5f && water(MS14Provider.get(body, MS14Bridges.STOMACH).getMap()) == 5f,
                "cooldown denial conserves both source and stomach");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void ownedJugFillTransfersBothDirectionsConservingContents(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "human body created");
        body.setNoAi(true);
        body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body), "body spawned");
        require(CharacterIdentitySystem.enroll(body, level, com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID),
                "human identity bound");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body),
                "live hands and body system initialized");
        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String hand = empty.activeHand();
        LiveHands.WholeResult inserted = empty.putWhole(empty.revision(), hand, new com.juicyslew.moonstation14.ms14.hands.ItemToken("jug"),
                filledJug(10));
        require(inserted.succeeded(), "filled held jug inserted");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), inserted.state());
        LiveHands before = inserted.state();
        String token = before.token(hand).orElseThrow();
        LooseItemEntity target = LooseItemEntity.create(level,
                new Vec3(origin.getX() + 2.5, origin.getY() + 1.2, origin.getZ() + .5), new ItemStack(ModItems.JUG.get()));
        target.setNoPickUpDelay();
        require(level.addFreshEntity(target), "empty loose jug spawned");
        UUID targetId = target.getUUID();

        require(BodyLiquidInteraction.fillOwned(body, targetId, hand, token, before.revision(), false, () -> true)
                == BodyLiquidInteraction.Result.SUCCESS, "held jug fills loose jug");
        LiveHands poured = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(poured.revision() == before.revision() + 1 && poured.token(hand).filter(token::equals).isPresent(),
                "fill preserves ownership and advances revision once");
        require(water(poured.stackCopy(hand).orElseThrow()) == 5f && water(target.getItem()) == 5f,
                "forward transfer leaves five units in each jug");
        require(waterCents(poured.stackCopy(hand).orElseThrow()) + waterCents(target.getItem()) == 1000,
                "forward transfer conserves integer cents");
        require(BodyLiquidInteraction.fillOwned(body, targetId, hand, token, poured.revision(), true, () -> true)
                == BodyLiquidInteraction.Result.SUCCESS, "reverse gesture keeps the qualifying held-source pour first");
        LiveHands returned = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(returned.revision() == poured.revision() + 1 && returned.token(hand).filter(token::equals).isPresent(),
                "forward-priority pour preserves ownership and advances revision once");
        require(water(returned.stackCopy(hand).orElseThrow()) == 0f && water(target.getItem()) == 10f,
                "forward-priority transfer moves only the still-available held source");
        require(waterCents(returned.stackCopy(hand).orElseThrow()) + waterCents(target.getItem()) == 1000,
                "reverse transfer conserves integer cents");

        LiveHands snapshot = returned;
        ItemStack heldSnapshot = snapshot.stackCopy(hand).orElseThrow();
        ItemStack worldSnapshot = target.getItem().copy();
        require(BodyLiquidInteraction.fillOwned(body, targetId, hand, "stale", snapshot.revision(), false, () -> true)
                        == BodyLiquidInteraction.Result.DENIED,
                "stale owner token is rejected");
        requireUnchanged(body, target, snapshot, heldSnapshot, worldSnapshot, hand, "stale token denial");
        target.setItem(filledJug(ModItems.JUG.get().getCapacity()));
        worldSnapshot = target.getItem().copy();
        require(BodyLiquidInteraction.fillOwned(body, targetId, hand, token, snapshot.revision(), false, () -> true)
                        == BodyLiquidInteraction.Result.DENIED,
                "full target is rejected");
        requireUnchanged(body, target, snapshot, heldSnapshot, worldSnapshot, hand, "full target denial");
        helper.succeed();
    }

    @GameTestTags({"hands-liquid", "generated-produce-capacity"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20,
            batch = "food-refill-owned-transaction-tomato")
    public static void ownedJugFillInitializesOnlyFreshTomatoOnStagedCopy(GameTestHelper helper) {
        var type = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE;
        var tomatoId = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
        var profile = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.serverLookup(tomatoId);
        require(profile != null, "resolved tomato food profile exists");
        ScopedPrototypeCatalogFixture.replace(helper, type, candidate -> {
            candidate.put(tomatoId, new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(
                    profile.capacityCents(), profile.contentsCents(), profile.canReact(), profile.solutionName(), true));
            return candidate;
        });
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created"); body.setNoAi(true); body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        body.setYRot(-90); body.setYHeadRot(-90);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level,
                com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID), "body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "hands initialized");
        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String hand = empty.activeHand(), token = "tomato-refill-" + UUID.randomUUID();
        var inserted = empty.putWhole(empty.revision(), hand, new com.juicyslew.moonstation14.ms14.hands.ItemToken(token), filledJug(10));
        require(inserted.succeeded(), "source jug inserted"); body.setData(ModDataAttachments.LIVE_HANDS.get(), inserted.state());
        LooseItemEntity target = LooseItemEntity.create(level, new Vec3(origin.getX() + 2.5, origin.getY() + 1.2, origin.getZ() + .5),
                new ItemStack(ModItems.TOMATO.get()));
        target.setNoPickUpDelay(); require(level.addFreshEntity(target), "fresh tomato spawned");
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack heldBefore = hands.stackCopy(hand).orElseThrow();
        ItemStack tomatoBefore = target.getItem().copy();
        require(BodyLiquidInteraction.fillOwned(body, target.getUUID(), hand, token, hands.revision(), false, () -> true)
                        == BodyLiquidInteraction.Result.DENIED, "fresh full tomato rejects owned fill");
        requireUnchanged(body, target, hands, heldBefore, tomatoBefore, hand, "fresh full tomato rejection");
        require(target.getItem().get(ModDataComponents.REAGENT.get()) == null,
                "rejected fresh target does not publish prototype chemistry");

        var nutriment = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "nutriment"));
        int capacity = profile.capacityCents();
        int existing = capacity / 2;
        target.getItem().set(ModDataComponents.REAGENT.get(), ReagentComponent.fromCents(Map.of(nutriment, (long) existing)));
        ItemStack partialBefore = target.getItem().copy();
        require(BodyLiquidInteraction.fillOwned(body, target.getUUID(), hand, token, hands.revision(), false, () -> true)
                        == BodyLiquidInteraction.Result.SUCCESS, "partially consumed tomato accepts fill through owned route");
        var partialAfter = target.getItem().get(ModDataComponents.REAGENT.get());
        long totalAfter = partialAfter == null ? 0 : partialAfter.centContents().values().stream().mapToLong(Long::longValue).sum();
        require(partialAfter != null && totalAfter <= capacity && totalAfter > existing,
                "partial tomato receives only available capacity");
        require(partialAfter.centContents().getOrDefault(nutriment, 0L) >= existing,
                "partial tomato retains its existing chemistry");

        target.setItem(new ItemStack(ModItems.TOMATO.get()));
        target.getItem().set(ModDataComponents.REAGENT.get(), new ReagentComponent());
        hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyLiquidInteraction.fillOwned(body, target.getUUID(), hand, token, hands.revision(), false, () -> true)
                        == BodyLiquidInteraction.Result.SUCCESS, "explicit-empty tomato accepts owned fill");
        var explicitContents = target.getItem().get(ModDataComponents.REAGENT.get()).centContents();
        require(!explicitContents.containsKey(nutriment)
                        && !explicitContents.containsKey(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                        ResourceLocation.fromNamespaceAndPath("moonstation14", "vitamin"))),
                "explicit-empty tomato receives source chemistry only, never prototype nutriment or vitamin");

        var seedId = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
        var seed = com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.serverCatalog().get(seedId);
        require(seed != null, "resolved tomato seed profile exists");
        // The partial and explicit-empty cases above consume the original 10u jug.
        // Restore a bounded source so this case tests generated-target admission,
        // rather than accidentally asserting that an exhausted source can refill.
        hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack replenishedJug = filledJug(10);
        hands = hands.replaceOwnedComponents(hands.revision(), hand,
                new com.juicyslew.moonstation14.ms14.hands.ItemToken(token), replenishedJug).orElseThrow();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), hands);
        ItemStack generated = com.juicyslew.moonstation14.ms14.botany.ProduceFactory.createHarvest(seedId, seed, 1f);
        generated.remove(ModDataComponents.REAGENT.get());
        target.setItem(generated);
        hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyLiquidInteraction.fillOwned(body, target.getUUID(), hand, token, hands.revision(), false, () -> true)
                        == BodyLiquidInteraction.Result.SUCCESS, "generated tomato without reagent accepts owned fill");
        var generatedContents = target.getItem().get(ModDataComponents.REAGENT.get()).centContents();
        require(!generatedContents.containsKey(nutriment)
                        && !generatedContents.containsKey(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                        ResourceLocation.fromNamespaceAndPath("moonstation14", "vitamin"))),
                "generated tomato receives source chemistry only, never prototype nutriment or vitamin");
        helper.succeed();
    }

    @GameTestTags({"syringe", "medical-treatment", "hands", "chemistry"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void syringeContainerSelectedDosesDrawAndInjectAtTwoBlockReach(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "human body created");
        body.setNoAi(true); body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        body.setYRot(-90); body.setYHeadRot(-90); body.setXRot(0);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level,
                com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID), "human body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "typed hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String hand = initial.activeHand();
        var syringe = new ItemStack(ModItems.SYRINGE.get());
        var initialData = SyringeItem.initialData(level);
        syringe.set(ModDataComponents.SYRINGE.get(), new SyringeStackData(initialData.mode(), 5));
        LiveHands.WholeResult heldResult = initial.putWhole(initial.revision(), hand,
                new com.juicyslew.moonstation14.ms14.hands.ItemToken("syringe"), syringe);
        require(heldResult.succeeded(), "real syringe inserted into hands");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), heldResult.state());
        LiveHands hands = heldResult.state();
        String token = hands.token(hand).orElseThrow();
        LooseItemEntity target = LooseItemEntity.create(level,
                new Vec3(origin.getX() + 2.25, origin.getY() + 1.2, origin.getZ() + .5), filledJug(30));
        target.setNoPickUpDelay(); require(level.addFreshEntity(target), "loose jug spawned");
        var initialBox = target.getBoundingBox();
        var eye = body.getEyePosition();
        double targetX = eye.x + 1.75 + (target.getX() - initialBox.minX);
        double targetY = eye.y + (target.getY() - initialBox.getCenter().y);
        double targetZ = eye.z + (target.getZ() - initialBox.getCenter().z);
        target.setPos(targetX, targetY, targetZ);
        var box = target.getBoundingBox();
        var closest = new net.minecraft.world.phys.Vec3(
                net.minecraft.util.Mth.clamp(eye.x, box.minX, box.maxX),
                net.minecraft.util.Mth.clamp(eye.y, box.minY, box.maxY),
                net.minecraft.util.Mth.clamp(eye.z, box.minZ, box.maxZ));
        require(Math.abs(eye.distanceTo(closest) - 1.75) < 1e-6,
                "non-inflated jug bounding box nearest face is exactly 1.75 from body eye");
        double horizontal = Math.sqrt(Math.pow(closest.x - body.getX(), 2) + Math.pow(closest.z - body.getZ(), 2));
        body.setYRot(-90); body.setYHeadRot(-90);
        body.setXRot((float)-Math.toDegrees(Math.atan2(closest.y - body.getEyeY(), horizontal)));
        require(BodyItemTargetProbe.probeSelected(level, body, target.getUUID(), 2.0) != null,
                "the correctly aimed 2.0-block probe admits the target");
        require(BodyItemTargetProbe.loadedReachableItem(level, body, target.getUUID(), 2.0),
                "the actual completion reach predicate admits the target at 2.0 blocks");
        require(!BodyItemTargetProbe.loadedReachableItem(level, body, target.getUUID(), 1.5),
                "the former non-inflated completion predicate rejects this target at 1.5 blocks");

        var catalog = com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalog(level);
        var policy = com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.require(catalog, SyringeItem.PROTOTYPE_ID)
                .syringePolicy().orElseThrow();
        var modes = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverInjectorModes();
        var drawId = policy.allowedModes().stream().filter(id -> modes.get(id).kind()
                == com.juicyslew.moonstation14.ms14.medical.InjectorModeData.Kind.DRAW).findFirst().orElseThrow();
        var injectId = policy.allowedModes().stream().filter(id -> modes.get(id).kind()
                == com.juicyslew.moonstation14.ms14.medical.InjectorModeData.Kind.INJECT).findFirst().orElseThrow();
        for (int selected : new int[]{5, 10, 15}) {
            ItemStack selectedStack = hands.stackCopy(hand).orElseThrow();
            selectedStack.set(ModDataComponents.SYRINGE.get(), new SyringeStackData(drawId, selected));
            hands = hands.replaceOwnedComponents(hands.revision(), hand,
                    new com.juicyslew.moonstation14.ms14.hands.ItemToken(token), selectedStack).orElseThrow();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), hands);
            long remaining = 15;
            while (remaining > 0) {
                ItemStack currentSyringe = hands.stackCopy(hand).orElseThrow();
                SyringeStackData selectedData = ((SyringeItem) currentSyringe.getItem()).dataOrInitial(currentSyringe, level);
                require(selectedData.transferAmountUnits() == selected, "resolved stack retains selected dose " + selected);
                long requestedCents = ReagentUnits.fromFloat(selectedData.transferAmountUnits());
                require(requestedCents == selected * 100, "fixture selection resolves to expected hundredths");
                require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                                && hands.compatible(body) && hands.activeHand().equals(hand)
                                && hands.token(hand).filter(token::equals).isPresent(),
                        "fixture presents current compatible owned hand proof");
                require(hands.stackCopy(hand).orElseThrow().getItem() instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait
                                && target.getItem().getItem() instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait,
                        "syringe and jug both implement reagent trait");
                require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body),
                        "fixture body is eligible to act");
                require(com.juicyslew.moonstation14.ms14.reagent.ReagentSystem.bridgeForHolder(hands.stackCopy(hand).orElseThrow()) != null,
                        "syringe has an existing reagent bridge");
                require(BodyItemTargetProbe.probeSelected(level, body, target.getUUID(), 2.0) != null,
                        "selected consumer target remains on body-eye ray");
                require(com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.representable(hands, body),
                        "selected syringe hands remain representable at trusted commit");
                var syringeProbe = hands.stackCopy(hand).orElseThrow();
                var sourceProbe = target.getItem().copy();
                var syringeBridge = com.juicyslew.moonstation14.ms14.reagent.ReagentSystem.bridgeForHolder(syringeProbe);
                var sourceBridge = com.juicyslew.moonstation14.ms14.reagent.ReagentSystem.bridgeForHolder(sourceProbe);
                require(syringeBridge != null && sourceBridge != null, "container transfer endpoints have reagent bridges");
                var dosePlan = com.juicyslew.moonstation14.ms14.loose_objects.interaction.LiquidInteractionCapability.plan(
                        ReagentUnits.fromMap(com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(sourceProbe, sourceBridge).getMap()),
                        ReagentUnits.fromMap(com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(syringeProbe, syringeBridge).getMap()),
                        ReagentUnits.fromFloat(((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) syringeProbe.getItem())
                                .getCapacity(syringeProbe, level)), requestedCents);
                long syringeCapacity = ReagentUnits.fromFloat(((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) syringeProbe.getItem())
                        .getCapacity(syringeProbe, level));
                long expectedAdmitted = Math.min(requestedCents, Math.min(waterCents(target.getItem()),
                        syringeCapacity - waterCents(syringeProbe)));
                require(dosePlan.transferredCents() == expectedAdmitted,
                        "selected draw clamps to source and remaining syringe capacity");
                long before = waterCents(target.getItem());
                var transferResult = BodyLiquidInteraction.fillDoseOwned(body, target.getUUID(), hand, token, hands.revision(), true,
                        Math.toIntExact(requestedCents), () -> true);
                require(transferResult == BodyLiquidInteraction.Result.SUCCESS,
                        "selected draw accepted by production owned consumer at 1.75 blocks; result=" + transferResult);
                hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                long moved = before - waterCents(target.getItem());
                require(moved > 0 && moved <= selected * 100, "draw clamps to selected dose/source/free capacity");
                remaining -= moved / 100;
                require(waterCents(hands.stackCopy(hand).orElseThrow()) + waterCents(target.getItem())
                        == 3000, "integer-cent contents conserved on draw");
                if (remaining == 0) {
                    require(hands.stackCopy(hand).orElseThrow().get(ModDataComponents.SYRINGE.get()).mode().equals(injectId),
                            "full syringe auto-switches to inject");
                    break;
                }
            }
            long doseLeft = 15;
            while (doseLeft > 0) {
                ItemStack currentSyringe = hands.stackCopy(hand).orElseThrow();
                SyringeStackData selectedData = ((SyringeItem) currentSyringe.getItem()).dataOrInitial(currentSyringe, level);
                require(selectedData.mode().equals(injectId) && selectedData.transferAmountUnits() == selected,
                        "inject mode and selected dose retained");
                long requestedCents = ReagentUnits.fromFloat(selectedData.transferAmountUnits());
                require(requestedCents == selected * 100, "inject selection resolves to expected hundredths");
                long before = waterCents(target.getItem());
                require(BodyLiquidInteraction.fillDoseOwned(body, target.getUUID(), hand, token, hands.revision(), false,
                        Math.toIntExact(requestedCents), () -> true) == BodyLiquidInteraction.Result.SUCCESS,
                        "selected inject accepted immediately by production owned consumer");
                hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                long moved = waterCents(target.getItem()) - before;
                require(moved > 0 && moved <= selected * 100, "inject clamps to selected dose/source/free capacity");
                doseLeft -= moved / 100;
                require(waterCents(hands.stackCopy(hand).orElseThrow()) + waterCents(target.getItem())
                        == 3000, "integer-cent contents conserved on inject");
                if (doseLeft == 0) {
                    require(hands.stackCopy(hand).orElseThrow().get(ModDataComponents.SYRINGE.get()).mode().equals(drawId),
                            "empty syringe auto-switches to draw");
                    break;
                }
            }
        }
        helper.succeed();
    }

    private static ItemStack filledJug(float units) {
        ItemStack stack = new ItemStack(ModItems.JUG.get());
        if (units > 0) stack.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, units)));
        return stack;
    }

    private static long waterCents(ItemStack stack) {
        ReagentComponent component = stack.get(ModDataComponents.REAGENT.get());
        return component == null ? 0 : ReagentUnits.fromFloat(component.contents().getOrDefault(WATER, 0f));
    }

    private static void requireUnchanged(Villager body, LooseItemEntity target, LiveHands expected,
                                         ItemStack held, ItemStack world, String hand, String reason) {
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(current == expected && current.revision() == expected.revision()
                        && current.stackCopy(hand).filter(s -> ItemStack.isSameItemSameComponents(s, held)).isPresent()
                        && ItemStack.isSameItemSameComponents(target.getItem(), world),
                reason + " preserves both owner components and revision");
    }

    private static float water(ItemStack stack) {
        ReagentComponent reagents = stack.get(ModDataComponents.REAGENT.get());
        return reagents == null ? 0f : reagents.contents().getOrDefault(WATER, 0f);
    }

    private static float water(Map<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Float> map) {
        return map.getOrDefault(WATER, 0f);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
