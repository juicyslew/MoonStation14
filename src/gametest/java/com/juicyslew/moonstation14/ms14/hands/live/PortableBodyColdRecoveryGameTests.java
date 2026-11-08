package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

/** Cold entity-NBT recovery fixtures; entity-join reconciliation must establish custody from the saved root ledger. */
@PrefixGameTestTemplate(false)
public final class PortableBodyColdRecoveryGameTests {
    private PortableBodyColdRecoveryGameTests() { }

    private static final ResourceKey<ReagentData> WATER = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "persistence", "portable-escape", "portable-cold-recovery"})
    public static void worldBagColdRecoveryUsesBothEntityLoadOrders(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = createBody(level, carrier.position().add(0, 0, .5));
        var hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        var withKey = hands.putWhole(hands.revision(), hand, new ItemToken("cold-world-key"), key);
        require(withKey.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, hands, withKey.state()),
                "world cold fixture starts with canonical carrier key");
        var root = new ItemEntity(level, carrier.getX() + 1.5, carrier.getY(), carrier.getZ(), new ItemStack(ModItems.BAG.get()));
        require(level.addFreshEntity(root), "world bag joins before key storage");
        aimAt(carrier, root.position());
        require(BodyWorldGridTransfer.storeOwned(carrier, root, hand, "cold-world-key", withKey.state().revision(),
                root.getItem(), root.getItem().copy(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                () -> true) == BodyWorldGridTransfer.Result.SUCCESS, "key transferred into normal world bag grid");
        var originalFlags = BodyContainmentService.forLevel(level).export(mouse,
                BodyContainmentService.forLevel(level).ownerOf(mouse).orElseThrow()).orElseThrow();
        var stomach = mouse.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).toComponent();
        var lifecycle = mouse.getExistingDataOrNull(ModDataAttachments.MOB_LIFECYCLE_BINDING.get());
        UUID bodyId = mouse.getUUID(), rootId = root.getUUID();
        CompoundTag bodyNbt = saveEntity(mouse);
        CompoundTag rootNbt = saveEntity(root);
        root.discard(); carrier.discard(); mouse.discard();
        BodyContainmentService.forLevel(level).tick();

        Entity loadedRootEntity = EntityType.loadEntityRecursive(rootNbt.copy(), level, entity -> entity);
        require(loadedRootEntity instanceof ItemEntity, "saved world root reloads as item entity");
        ItemEntity loadedRoot = (ItemEntity) loadedRootEntity;
        require(loadedRoot.getUUID().equals(rootId) && level.addFreshEntity(loadedRoot), "same root UUID loaded first");
        safeAfterDelay(helper, 1, "world root-first body reload", () -> {
            LivingEntity loadedBody = loadBody(level, bodyNbt, bodyId, stomach);
            safeAfterDelay(helper, 1, "deferred world-root join reconciliation", () -> {
                var owner = BodyContainmentService.forLevel(level).registeredOwnerOf(loadedBody).orElse(null);
                require(owner instanceof PortableWorldStorageOwner world && world.rootId().equals(rootId)
                                && world.isValid(),
                        "deferred normal join reconciliation binds exact cold root before metabolism probe");
                var waterPrototype = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverReagents().get(WATER.location());
                var digestion = waterPrototype == null ? null : waterPrototype.metabolisms().get(
                        com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage.DIGESTION);
                require(digestion != null, "resolved water prototype exposes its DIGESTION metabolism stage");
                var stomachBeforeDigestion = loadedBody.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).snapshotUnits();
                long beforeWater = stomachBeforeDigestion.getOrDefault(WATER, 0L);
                long rateCents = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(digestion.rate());
                long expectedWater = Math.max(0L, beforeWater - rateCents);
                require(beforeWater >= rateCents && rateCents > 0,
                        "current restored stomach still has a nonempty water dose for direct consumer proof: "
                                + stomachBeforeDigestion + ", rateCents=" + rateCents);
                com.juicyslew.moonstation14.eventhooks.TickHooks.runDueActivities(loadedBody, level,
                        java.util.Set.of(com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM));
                var unitsAfterDigestion = loadedBody.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).snapshotUnits();
                require(unitsAfterDigestion.getOrDefault(WATER, 0L) == expectedWater,
                        "one production metabolism consumer pass applies resolved DIGESTION rate=" + digestion.rate()
                                + ", savedNBT=" + stomach.centContents() + ", beforeConsumer=" + stomachBeforeDigestion
                                + ", expectedWaterCents=" + expectedWater + ", afterConsumer=" + unitsAfterDigestion);
                safeAfterDelay(helper, 2, "world root-first cold recovery/release", () ->
                        assertRecoveredAndRelease(helper, loadedBody, loadedRoot, originalFlags,
                                stomach, lifecycle, rootId, "world root before body"));
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "persistence", "portable-escape", "portable-cold-recovery"})
    public static void worldBagColdRecoveryBodyLoadsBeforeRoot(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = createBody(level, carrier.position().add(0, 0, .5));
        var hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        var withKey = hands.putWhole(hands.revision(), hand, new ItemToken("cold-world-key-body-first"), key);
        require(withKey.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, hands, withKey.state()),
                "world body-first fixture key committed");
        var root = new ItemEntity(level, carrier.getX() + 1.5, carrier.getY(), carrier.getZ(), new ItemStack(ModItems.BAG.get()));
        require(level.addFreshEntity(root), "world body-first root joined");
        aimAt(carrier, root.position());
        require(BodyWorldGridTransfer.storeOwned(carrier, root, hand, "cold-world-key-body-first", withKey.state().revision(),
                root.getItem(), root.getItem().copy(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                () -> true) == BodyWorldGridTransfer.Result.SUCCESS, "key stored in world grid");
        var containment = BodyContainmentService.forLevel(level);
        var oldOwner = containment.ownerOf(mouse).orElseThrow();
        var flags = containment.export(mouse, oldOwner).orElseThrow();
        var stomach = mouse.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).toComponent();
        var lifecycle = mouse.getExistingDataOrNull(ModDataAttachments.MOB_LIFECYCLE_BINDING.get());
        UUID bodyId = mouse.getUUID(), rootId = root.getUUID();
        CompoundTag bodyNbt = saveEntity(mouse), rootNbt = saveEntity(root);
        root.discard(); carrier.discard(); mouse.discard(); containment.tick();
        safeAfterDelay(helper, 1, "world body-first reload", () -> {
            var loadedBody = loadBody(level, bodyNbt, bodyId, stomach);
            require(loadedBody.isInvisible() && loadedBody.noPhysics && loadedBody.isNoGravity()
                            && containment.registeredOwnerOf(loadedBody).isEmpty(),
                    "body-first load is suppressed but has no authority until exact root returns");
            safeAfterDelay(helper, 1, "world body-first root insertion", () -> {
                Entity entity = EntityType.loadEntityRecursive(rootNbt.copy(), level, value -> value);
                require(entity instanceof ItemEntity && entity.getUUID().equals(rootId) && level.addFreshEntity(entity),
                        "same world root arrives after body");
                safeAfterDelay(helper, 2, "world body-first recovery/release", () -> assertRecoveredAndRelease(helper, loadedBody, entity, flags,
                        stomach, lifecycle, rootId, "body before world root"));
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "persistence", "portable-cold-recovery"})
    public static void worldRootRebindsSameUuidReloadWhileStaleMembershipExists(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var originalBody = createBody(level, carrier.position().add(0, 0, .5));
        var hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        var key = PortableBodyCarrying.createKey(carrier, originalBody, UUID.randomUUID());
        var keyedHands = hands.putWhole(hands.revision(), hand, new ItemToken("same-uuid-world-key"), key);
        require(keyedHands.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, hands, keyedHands.state()),
                "same-UUID recovery key commits through live hands authority");
        var root = new ItemEntity(level, carrier.getX() + 1.5, carrier.getY(), carrier.getZ(), new ItemStack(ModItems.BAG.get()));
        require(level.addFreshEntity(root), "same-UUID world root enters level");
        aimAt(carrier, root.position());
        require(BodyWorldGridTransfer.storeOwned(carrier, root, hand, "same-uuid-world-key", keyedHands.state().revision(),
                root.getItem(), root.getItem().copy(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                () -> true) == BodyWorldGridTransfer.Result.SUCCESS, "exact key commits into world root ledger");

        var containment = BodyContainmentService.forLevel(level);
        var staleOwner = containment.ownerOf(originalBody).orElseThrow();
        var originalFlags = containment.export(originalBody, staleOwner).orElseThrow();
        var stomach = originalBody.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).toComponent();
        var lifecycle = originalBody.getExistingDataOrNull(ModDataAttachments.MOB_LIFECYCLE_BINDING.get());
        UUID bodyId = originalBody.getUUID(), rootId = root.getUUID();
        CompoundTag bodyNbt = saveEntity(originalBody), rootNbt = saveEntity(root);

        // Preserve the old transient row so a different Java body with the same UUID must replace it.
        root.discard(); carrier.discard(); originalBody.discard();
        Entity loadedBodyEntity = EntityType.loadEntityRecursive(bodyNbt.copy(), level, entity -> entity);
        require(loadedBodyEntity instanceof LivingEntity && loadedBodyEntity != originalBody
                        && loadedBodyEntity.getUUID().equals(bodyId) && level.addFreshEntity(loadedBodyEntity),
                "new body object loads with the exact original UUID while stale membership remains");
        var loadedBody = (LivingEntity) loadedBodyEntity;
        var loadedStomach = loadedBody.getExistingDataOrNull(ModDataAttachments.STOMACH.get());
        require(loadedStomach != null && loadedStomach.toComponent().equals(stomach),
                "same-UUID saved-NBT physiology is exact before root-join reconciliation: saved="
                        + stomach.centContents() + ", loaded=" + (loadedStomach == null ? null : loadedStomach.snapshotUnits()));
        require(containment.registeredOwnerOf(loadedBody).orElse(null) == staleOwner
                        && loadedBody.isInvisible() && loadedBody.noPhysics && loadedBody.isNoGravity(),
                "same-UUID body remains fail-closed under stale row until its exact root joins");

        // Body join reconciliation is queued first; root insertion follows it before the level tick.
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount(), () -> {
            try {
                Entity loadedRootEntity = EntityType.loadEntityRecursive(rootNbt.copy(), level, entity -> entity);
                require(loadedRootEntity instanceof ItemEntity && loadedRootEntity.getUUID().equals(rootId)
                                && level.addFreshEntity(loadedRootEntity),
                        "exact saved world root joins after the new body join task");
                safeAfterDelay(helper, 2, "same-UUID world-root binding and release", () -> {
                    try {
                        var recovered = containment.registeredOwnerOf(loadedBody).orElse(null);
                        require(recovered instanceof PortableWorldStorageOwner world && world.rootId().equals(rootId)
                                        && world.isValid() && recovered != staleOwner,
                                "production root-join consumer replaces stale owner with exact loaded root owner");
                        require(level.getEntity(bodyId) == loadedBody,
                                "recovery remains on the new Java body with original UUID");
                        assertRecoveredAndRelease(helper, loadedBody, loadedRootEntity, originalFlags,
                                stomach, lifecycle, rootId, "same-UUID body before world root with stale membership");
                    } catch (RuntimeException | Error failure) {
                        helper.fail("same-UUID world-root recovery assertion failed: " + failure);
                    }
                });
            } catch (RuntimeException | Error failure) {
                helper.fail("same-UUID world-root insertion failed: " + failure);
            }
        }));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "persistence", "portable-escape", "portable-cold-recovery"})
    public static void heldGridColdRecoveryUsesBothEntityLoadOrders(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = createBody(level, carrier.position().add(0, 0, .5));
        var hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        ItemStack bag = GridContents.applyCopyWithGrid(new ItemStack(ModItems.BAG.get()), GridContents.of(java.util.List.of(
                new GridContents.Entry("cold-held-child", key, 0, 0, GridGeometry.Rotation.DEG_0)))).orElseThrow();
        var withBag = hands.putWhole(hands.revision(), hand, new ItemToken("cold-held-bag"), bag);
        require(withBag.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, hands, withBag.state()),
                "held grid key committed through existing hands gateway");
        var containment = BodyContainmentService.forLevel(level);
        var owner = containment.ownerOf(mouse).orElseThrow();
        require(owner instanceof PortableBodyCarrierOwner, "held grid is canonical carrier custody");
        var originalFlags = containment.export(mouse, owner).orElseThrow();
        var stomach = mouse.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).toComponent();
        var lifecycle = mouse.getExistingDataOrNull(ModDataAttachments.MOB_LIFECYCLE_BINDING.get());
        UUID bodyId = mouse.getUUID(), rootId = carrier.getUUID();
        CompoundTag bodyNbt = saveEntity(mouse);
        CompoundTag rootNbt = saveEntity(carrier);
        carrier.discard(); mouse.discard(); containment.tick();

        safeAfterDelay(helper, 1, "held-grid body-first reload", () -> {
            LivingEntity loadedBody = loadBody(level, bodyNbt, bodyId, stomach);
            require(loadedBody.isInvisible() && loadedBody.noPhysics && loadedBody.isNoGravity(),
                    "unresolved persisted relation remains inert while carrier root is absent");
            var loadedCarrierEntity = EntityType.loadEntityRecursive(rootNbt.copy(), level, entity -> entity);
            require(loadedCarrierEntity instanceof Villager, "saved carrier reloads as carrier root");
            Villager loadedCarrier = (Villager) loadedCarrierEntity;
            require(loadedCarrier.getUUID().equals(rootId) && level.addFreshEntity(loadedCarrier), "same carrier UUID loads after body");
            safeAfterDelay(helper, 2, "held-grid body-first recovery/release", () ->
                    assertRecoveredAndRelease(helper, loadedBody, loadedCarrier, originalFlags,
                            stomach, lifecycle, rootId, "body before held carrier"));
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "persistence", "portable-escape", "portable-cold-recovery", "portable-carrier-first-cold"})
    public static void heldGridColdRecoveryCarrierLoadsBeforeBody(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = createBody(level, carrier.position().add(0, 0, .5));
        var hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        ItemStack bag = GridContents.applyCopyWithGrid(new ItemStack(ModItems.BAG.get()), GridContents.of(java.util.List.of(
                new GridContents.Entry("cold-held-child", key, 0, 0, GridGeometry.Rotation.DEG_0)))).orElseThrow();
        var withBag = hands.putWhole(hands.revision(), hand, new ItemToken("cold-held-bag"), bag);
        require(withBag.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, hands, withBag.state()),
                "held grid key committed through existing gateway");
        var containment = BodyContainmentService.forLevel(level);
        var owner = containment.ownerOf(mouse).orElseThrow();
        var flags = containment.export(mouse, owner).orElseThrow();
        var stomach = mouse.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).toComponent();
        var lifecycle = mouse.getExistingDataOrNull(ModDataAttachments.MOB_LIFECYCLE_BINDING.get());
        UUID bodyId = mouse.getUUID(), rootId = carrier.getUUID();
        CompoundTag bodyNbt = saveEntity(mouse), rootNbt = saveEntity(carrier);
        carrier.discard(); mouse.discard(); containment.tick();
        safeAfterDelay(helper, 1, "held-grid carrier-first root load", () -> {
            var rootEntity = EntityType.loadEntityRecursive(rootNbt.copy(), level, value -> value);
            require(rootEntity instanceof Villager && rootEntity.getUUID().equals(rootId) && level.addFreshEntity(rootEntity),
                    "same carrier root arrives before body");
            safeAfterDelay(helper, 1, "held-grid carrier-first body load", () -> {
                var loadedBody = loadBody(level, bodyNbt, bodyId, stomach);
                safeAfterDelay(helper, 1, "held-grid carrier-first recovery/release", () ->
                        assertRecoveredAndRelease(helper, loadedBody, rootEntity, flags,
                                stomach, lifecycle, rootId, "carrier before body"));
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "persistence", "portable-cold-recovery"})
    public static void wornGridColdRecoveryCarrierLoadsBeforeBody(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = createBody(level, carrier.position().add(0, 0, .5));
        var hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        ItemStack belt = GridContents.applyCopyWithGrid(new ItemStack(ModItems.BELT.get()), GridContents.of(java.util.List.of(
                new GridContents.Entry("cold-worn-child", key, 0, 0, GridGeometry.Rotation.DEG_0)))).orElseThrow();
        var withBelt = hands.putWhole(hands.revision(), hand, new ItemToken("cold-worn-belt"), belt);
        require(withBelt.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, hands, withBelt.state()),
                "worn-grid fixture belt committed to hand");
        require(BodyEquipmentTransfer.transferOwned(carrier, hand, "belt", "cold-worn-belt",
                withBelt.state().revision(), true, () -> true) == BodyEquipmentTransfer.Result.SUCCESS,
                "key-containing belt moves into canonical worn slot");
        var containment = BodyContainmentService.forLevel(level);
        var owner = containment.ownerOf(mouse).orElseThrow();
        var flags = containment.export(mouse, owner).orElseThrow();
        var stomach = mouse.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).toComponent();
        var lifecycle = mouse.getExistingDataOrNull(ModDataAttachments.MOB_LIFECYCLE_BINDING.get());
        UUID bodyId = mouse.getUUID(), rootId = carrier.getUUID();
        CompoundTag bodyNbt = new CompoundTag();
        CompoundTag rootNbt = new CompoundTag();
        require(mouse.save(bodyNbt), "contained body serializes for recovery");
        require(carrier.save(rootNbt), "carrier root serializes for recovery");
        carrier.discard(); mouse.discard(); containment.tick();
        safeAfterDelay(helper, 1, "worn-grid carrier-first root load", () -> {
            Entity root = EntityType.loadEntityRecursive(rootNbt.copy(), level, entity -> entity);
            require(root instanceof Villager && root.getUUID().equals(rootId) && level.addFreshEntity(root),
                    "worn carrier root loads first");
            safeAfterDelay(helper, 1, "worn-grid carrier-first body load", () -> {
                var loadedBody = loadBody(level, bodyNbt, bodyId, stomach);
                safeAfterDelay(helper, 1, "worn-grid recovery/release", () ->
                        assertRecoveredAndRelease(helper, loadedBody, root, flags,
                                stomach, lifecycle, rootId, "worn belt carrier before body"));
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 45)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "persistence", "portable-cold-recovery"})
    public static void missingColdRootKeyKeepsOriginalBodyFailClosed(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = createBody(level, carrier.position().add(0, 0, .5));
        var hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        var key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        var withKey = hands.putWhole(hands.revision(), hand, new ItemToken("missing-cold-key"), key);
        require(withKey.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, hands, withKey.state()),
                "missing-root fixture carrier key committed");
        var root = new ItemEntity(level, carrier.getX() + 1.5, carrier.getY(), carrier.getZ(), new ItemStack(ModItems.BAG.get()));
        require(level.addFreshEntity(root), "missing-root fixture root added");
        aimAt(carrier, root.position());
        require(BodyWorldGridTransfer.storeOwned(carrier, root, hand, "missing-cold-key", withKey.state().revision(),
                root.getItem(), root.getItem().copy(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                () -> true) == BodyWorldGridTransfer.Result.SUCCESS, "body relation persisted through world key custody");
        CompoundTag bodyNbt = saveEntity(mouse);
        var expectedStomach = mouse.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).toComponent();
        ItemStack keylessRoot = GridContents.applyCopyWithGrid(root.getItem(), GridContents.empty()).orElseThrow();
        root.setItem(keylessRoot);
        CompoundTag keylessRootNbt = saveEntity(root);
        UUID bodyId = mouse.getUUID(), rootId = root.getUUID();
        root.discard(); carrier.discard(); mouse.discard(); BodyContainmentService.forLevel(level).tick();
        var loadedBody = loadBody(level, bodyNbt, bodyId, expectedStomach);
        require(loadedBody.isInvisible() && loadedBody.noPhysics && loadedBody.isNoGravity(),
                "relation remains inert before keyless root reconciliation");
        Entity loaded = EntityType.loadEntityRecursive(keylessRootNbt.copy(), level, entity -> entity);
        require(loaded instanceof ItemEntity && loaded.getUUID().equals(rootId) && level.addFreshEntity(loaded),
                "same keyless root loads");
        safeAfterDelay(helper, 2, "missing cold root remains fail-closed", () -> {
            var containment = BodyContainmentService.forLevel(level);
            require(level.getEntity(bodyId) == loadedBody && containment.registeredOwnerOf(loadedBody).isEmpty()
                            && containment.ownerOf(loadedBody).isEmpty()
                            && GridContents.read(((ItemEntity) loaded).getItem()).orElseThrow().entries().isEmpty()
                            && !loadedBody.isRemoved(),
                    "missing exact key rejects custody/actions without deleting or cloning the body");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 55)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "persistence", "portable-cold-recovery"})
    public static void duplicateColdReferenceCannotBindWrongRoot(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = createBody(level, carrier.position().add(0, 0, .5));
        var hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        var key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        var withKey = hands.putWhole(hands.revision(), hand, new ItemToken("duplicate-cold-key"), key);
        require(withKey.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, hands, withKey.state()),
                "duplicate fixture starts with one owner key");
        var ownerRoot = new ItemEntity(level, carrier.getX() + 1.5, carrier.getY(), carrier.getZ(), new ItemStack(ModItems.BAG.get()));
        require(level.addFreshEntity(ownerRoot), "authoritative world root added");
        aimAt(carrier, ownerRoot.position());
        require(BodyWorldGridTransfer.storeOwned(carrier, ownerRoot, hand, "duplicate-cold-key", withKey.state().revision(),
                ownerRoot.getItem(), ownerRoot.getItem().copy(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                () -> true) == BodyWorldGridTransfer.Result.SUCCESS, "canonical key placed in owner root");
        ItemStack duplicatedContents = GridContents.applyCopyWithGrid(new ItemStack(ModItems.BAG.get()),
                GridContents.read(ownerRoot.getItem()).orElseThrow()).orElseThrow();
        var duplicateRoot = new ItemEntity(level, carrier.getX() + 2.5, carrier.getY(), carrier.getZ(), duplicatedContents);
        require(level.addFreshEntity(duplicateRoot), "stale duplicate ledger root added");
        UUID bodyId = mouse.getUUID(), authorityId = ownerRoot.getUUID(), duplicateId = duplicateRoot.getUUID();
        CompoundTag bodyNbt = saveEntity(mouse), ownerNbt = saveEntity(ownerRoot);
        var expectedStomach = mouse.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).toComponent();
        CompoundTag duplicateNbt = saveEntity(duplicateRoot);
        ownerRoot.discard(); duplicateRoot.discard(); carrier.discard(); mouse.discard();
        BodyContainmentService.forLevel(level).tick();
        var loadedBody = loadBody(level, bodyNbt, bodyId, expectedStomach);
        Entity duplicate = EntityType.loadEntityRecursive(duplicateNbt.copy(), level, entity -> entity);
        require(duplicate instanceof ItemEntity && duplicate.getUUID().equals(duplicateId) && level.addFreshEntity(duplicate),
                "duplicate root loads first");
        safeAfterDelay(helper, 1, "duplicate cold reference root insertion", () -> {
            var containment = BodyContainmentService.forLevel(level);
            require(containment.registeredOwnerOf(loadedBody).isEmpty(),
                    "same reference on wrong root UUID cannot claim the persisted relation");
            Entity authoritative = EntityType.loadEntityRecursive(ownerNbt.copy(), level, entity -> entity);
            require(authoritative instanceof ItemEntity && authoritative.getUUID().equals(authorityId)
                            && level.addFreshEntity(authoritative), "captured authoritative root loads second");
            safeAfterDelay(helper, 2, "duplicate cold reference authority recovery", () -> {
                var recovered = containment.registeredOwnerOf(loadedBody).orElse(null);
                require(recovered instanceof PortableWorldStorageOwner world && world.rootId().equals(authorityId)
                                && !world.rootId().equals(duplicateId) && world.isValid()
                                && PortableBodyCarrying.worldRootReferencesOwned(level, duplicateId,
                                ((ItemEntity) duplicate).getItem()) == false,
                        "one exact loaded ledger binds one canonical owner; duplicate root remains unauthorized");
                helper.succeed();
            });
        });
    }

    private static LivingEntity createBody(net.minecraft.server.level.ServerLevel level, Vec3 position) {
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "mouse prototype creates body");
        mouse.setNoAi(true);
        mouse.setInvisible(false);
        mouse.noPhysics = false;
        mouse.setNoGravity(true); // deliberately differs from the flags written by containment
        mouse.setPos(position);
        require(level.addFreshEntity(mouse), "original mouse joins level");
        CharacterIdentitySystem.enrollSupportedActor(mouse, level);
        mouse.setData(ModDataAttachments.STOMACH.get(), new ReagentAttachment(Map.of(WATER, 1f)));
        return mouse;
    }

    private static LivingEntity loadBody(net.minecraft.server.level.ServerLevel level, CompoundTag bodyNbt, UUID expectedId,
            com.juicyslew.moonstation14.ms14.reagent.ReagentComponent expectedStomach) {
        Entity loaded = EntityType.loadEntityRecursive(bodyNbt.copy(), level, entity -> entity);
        require(loaded instanceof LivingEntity && loaded.getUUID().equals(expectedId), "entity NBT reload preserves original body UUID");
        if (loaded instanceof LivingEntity living) {
            var attachment = living.getExistingDataOrNull(ModDataAttachments.STOMACH.get());
            var actual = attachment == null ? null : attachment.snapshotUnits();
            require(attachment != null && attachment.toComponent().equals(expectedStomach),
                    "immediate saved-NBT stomach round trip before join/metabolism: tick=" + level.getGameTime()
                            + ", savedUnits=" + expectedStomach.centContents() + ", loadedUnits=" + actual);
        }
        require(level.addFreshEntity(loaded), "original saved body joins through normal entity-join events");
        return (LivingEntity) loaded;
    }

    private static CompoundTag saveEntity(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.save(tag); // production entity serialization includes UUID and attachments
        return tag;
    }

    private static void aimAt(LivingEntity actor, Vec3 point) {
        Vec3 delta = point.subtract(actor.getEyePosition());
        actor.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        actor.setXRot((float) Math.toDegrees(Math.atan2(-delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
    }

    private static void assertRecoveredAndRelease(GameTestHelper helper, LivingEntity body, Entity root,
            BodyContainmentService.ContainedBodyFlags expectedFlags, com.juicyslew.moonstation14.ms14.reagent.ReagentComponent stomach,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding lifecycle,
            UUID rootId, String order) {
        var level = (net.minecraft.server.level.ServerLevel) body.level();
        var containment = BodyContainmentService.forLevel(level);
        var owner = containment.ownerOf(body).orElse(null);
        if (level.getEntity(body.getUUID()) != body || owner == null || !owner.isValid())
            throw new AssertionError(order + ": production join hook did not bind exact body/root; "
                    + recoveryDiagnostic(level, body, root, rootId, containment));
        require((owner instanceof PortableWorldStorageOwner world && world.rootId().equals(rootId))
                        || (owner instanceof PortableBodyCarrierOwner carried && carried.carrier().getUUID().equals(rootId)),
                order + ": canonical owner is derived from the exact loaded root ledger");
        require(containment.export(body, owner).filter(expectedFlags::equals).isPresent(),
                order + ": first pre-containment flags are restored into runtime custody");
        require(body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()) != null,
                order + ": restored body retains stomach attachment, tick=" + level.getGameTime()
                        + ", initialUnits=" + stomach.centContents() + ", currentUnits="
                        + body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).snapshotUnits());
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[portable cold recovery] order={} tick={} savedStomachCents={} recoveredStomachCents={} deltaCents={}",
                order, level.getGameTime(), stomach.centContents(),
                body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).snapshotUnits(),
                reagentDelta(stomach.centContents(), body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).snapshotUnits()));
        require(java.util.Objects.equals(body.getExistingDataOrNull(ModDataAttachments.MOB_LIFECYCLE_BINDING.get()), lifecycle),
                order + ": preexisting lifecycle identity survives serialization unchanged; expected=" + lifecycle
                        + ", actual=" + body.getExistingDataOrNull(ModDataAttachments.MOB_LIFECYCLE_BINDING.get()));
        var stomachAtRelease = body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).snapshotUnits();
        var snapshot = PortableBodyReleaseTransaction.capture(body);
        require(snapshot != null, order + ": restored relation supports ordinary exact-body release");
        Vec3 destination = root.position().add(2, 0, 0);
        var result = PortableBodyReleaseTransaction.release(snapshot,
                new PortableBodyReleaseTransaction.Placement(destination, Vec3.ZERO, () -> true));
        require(result == PortableBodyReleaseTransaction.Result.SUCCESS, order + ": release succeeds from canonical recovered owner");
        require(level.getEntity(body.getUUID()) == body && body.isInvisible() == expectedFlags.invisible()
                        && body.noPhysics == expectedFlags.noPhysics() && body.isNoGravity() == expectedFlags.noGravity()
                        && containment.ownerOf(body).isEmpty(),
                order + ": same original mouse is released with original flags");
        require(body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).snapshotUnits().equals(stomachAtRelease),
                order + ": release preserves current metabolism result, tick=" + level.getGameTime()
                        + ", before=" + stomachAtRelease + ", after="
                        + body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).snapshotUnits());
        helper.succeed();
    }

    private static String recoveryDiagnostic(net.minecraft.server.level.ServerLevel level, LivingEntity body,
            Entity expectedRoot, UUID expectedRootId, BodyContainmentService containment) {
        var relation = body.getExistingDataOrNull(ModDataAttachments.PORTABLE_BODY_CONTAINMENT.get());
        var loadedRoot = level.getEntity(expectedRootId);
        var owner = containment.registeredOwnerOf(body).orElse(null);
        String ledger = "unavailable", located = "unavailable";
        String candidateState = "unavailable";
        if (loadedRoot instanceof LivingEntity carrier && relation != null) {
            located = String.valueOf(PortableBodyCarrying.locateReference(carrier, relation.reference()));
            var candidate = PortableBodyCarrying.locateReference(carrier, relation.reference());
            if (candidate != null) {
                var candidateOwner = new PortableBodyCarrierOwner(carrier, relation.reference(), candidate.childToken());
                candidateState = "valid=" + candidateOwner.isValid() + ",bodyAlive=" + body.isAlive()
                        + ",bodyAdded=" + body.isAddedToLevel() + ",bodyRemoved=" + body.isRemoved()
                        + ",carrierAlive=" + carrier.isAlive() + ",carrierAdded=" + carrier.isAddedToLevel()
                        + ",carrierRemoved=" + carrier.isRemoved() + ",dimMatch=" + relation.reference().dimension().equals(level.dimension().location());
            }
        }
        if (loadedRoot instanceof ItemEntity item) ledger = PortableBodyCarrying.directGridKeys(item.getItem()).toString();
        return "body=" + body.getUUID() + ",bodyLoaded=" + (level.getEntity(body.getUUID()) == body)
                + ",bodyFlags=" + body.isInvisible() + "/" + body.noPhysics + "/" + body.isNoGravity()
                + ",relation=" + relation + ",expectedRoot=" + expectedRootId + ",loadedRoot=" + loadedRoot
                + ",sameRoot=" + (loadedRoot == expectedRoot) + ",rootUUID=" + (loadedRoot == null ? null : loadedRoot.getUUID())
                + ",owner=" + owner + ",ownerType=" + (owner == null ? null : owner.getClass().getName())
                + ",rootLedger=" + ledger + ",carrierLocator=" + located + ",candidate=" + candidateState;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    /** Route delayed fixture assertions through GameTest failure reporting instead of aborting the server tick. */
    private static void safeAfterDelay(GameTestHelper helper, int ticks, String label, Runnable callback) {
        helper.runAfterDelay(ticks, () -> {
            try {
                callback.run();
            } catch (RuntimeException | AssertionError failure) {
                helper.fail(label + ": " + failure);
            }
        });
    }

    private static java.util.Map<net.minecraft.resources.ResourceKey<ReagentData>, Long> reagentDelta(
            java.util.Map<net.minecraft.resources.ResourceKey<ReagentData>, Long> before,
            java.util.Map<net.minecraft.resources.ResourceKey<ReagentData>, Long> after) {
        var keys = new java.util.HashSet<>(before.keySet());
        keys.addAll(after.keySet());
        var delta = new java.util.LinkedHashMap<net.minecraft.resources.ResourceKey<ReagentData>, Long>();
        for (var key : keys) {
            long difference = after.getOrDefault(key, 0L) - before.getOrDefault(key, 0L);
            if (difference != 0) delta.put(key, difference);
        }
        return delta;
    }

}
