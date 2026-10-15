package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyReference;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.List;

@GameTestTags("living-carry")
@PrefixGameTestTemplate(false)
public final class PortableBodyCarrierOwnerGameTests {
    private PortableBodyCarrierOwnerGameTests() { }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"held-camera", "living-carry"})
    public static void liveHeldMouseViewPublishesExactCarrierRenderParent(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed");
        mouse.setNoAi(true);
        mouse.setPos(carrier.position());
        require(helper.getLevel().addFreshEntity(mouse), "original mouse loaded");
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        CharacterIdentitySystem.enrollSupportedActor(mouse, level);
        require(!LiveHands.initialize(carrier).isEmpty(), "native carrier hands initialized");
        var key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        var hands = LiveHands.initialize(carrier).orElseThrow();
        var placed = hands.putWhole(hands.revision(), hands.activeHand(), new ItemToken("held-camera-key"), key);
        require(placed.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, hands, placed.state()),
                "canonical portable key held by carrier");

        var owner = BodyContainmentService.forLevel(level).ownerOf(mouse).orElseThrow();
        require(owner instanceof PortableBodyCarrierOwner, "resolved existing production portable owner");
        var view = owner.viewFor(mouse);
        var parent = view.renderParent().orElseThrow();
        require(parent.entityId() == carrier.getId() && parent.entityUuid().equals(carrier.getUUID()),
                "render parent identifies actual canonical holder");
        require(parent.bodyFeetOffset().equals(owner.positionFor(mouse).subtract(carrier.position())),
                "body-local render offset matches native containment positionFor");
        carrier.discard(); mouse.discard(); helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"living-carry", "hands", "portable-name"})
    public static void portableKeyAndPresentationPreservePublicNameComponents(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed");
        mouse.setNoAi(true);
        mouse.setPos(carrier.getX() + .25, carrier.getY(), carrier.getZ());
        require(helper.getLevel().addFreshEntity(mouse), "mouse enters level");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());

        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        require(!key.isEmpty(), "key created for enrolled mouse");
        var defaultName = key.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        require(defaultName != null && "Mouse".equals(defaultName.getString()),
                "resolved character prototype public name is readable");

        mouse.setCustomName(net.minecraft.network.chat.Component.translatable("entity.moonstation14.mouse"));
        key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        require(key.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents,
                "key creation retains native translatable custom-name component");
        LiveHands hands = LiveHands.initialize(carrier).orElseThrow();
        var placed = hands.putWhole(hands.revision(), hands.activeHand(), new ItemToken("component-name-key"), key);
        require(placed.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, hands, placed.state()),
                "key is registered through normal hand gateway");
        var presentation = com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation.fromStack(
                key, helper.getLevel());
        require(presentation.stack().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents,
                "live owned key's server snapshot refresh retains native translatable custom-name component");
        carrier.discard(); mouse.discard(); helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"living-carry", "portable-escape", "hands"})
    public static void releasePlacementIgnoresOnlyNonphysicalOverlaps(GameTestHelper helper) {
        Villager holder = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        holder.setNoGravity(true);
        BlockPos feet = holder.blockPosition();
        helper.getLevel().setBlock(feet.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "release test mouse constructed");
        mouse.setNoGravity(true);
        mouse.setPos(holder.position());
        require(helper.getLevel().addFreshEntity(mouse), "release test mouse loaded");
        Villager otherBody = helper.spawn(EntityType.VILLAGER, feet);
        otherBody.setNoGravity(true);
        otherBody.setPos(holder.position());
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var destination = holder.position();
        require(!BodyItemTargetProbe.portableBodyReleaseClear(level, mouse, holder, destination,
                        mouse.getBbWidth(), mouse.getBbHeight()),
                "overlapping physical character remains a release blocker");
        otherBody.noPhysics = true;
        require(BodyItemTargetProbe.portableBodyReleaseClear(level, mouse, holder, destination,
                        mouse.getBbWidth(), mouse.getBbHeight()),
                "nonphysical overlap does not block exact-holder-feet release");
        otherBody.noPhysics = false;
        helper.getLevel().setBlock(feet, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        require(!BodyItemTargetProbe.portableBodyReleaseClear(level, mouse, holder, destination,
                        mouse.getBbWidth(), mouse.getBbHeight()),
                "solid world collision remains blocking even for no-physics entities");
        mouse.discard(); otherBody.discard(); holder.discard(); helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"living-carry", "portable-escape", "hands"})
    public static void releasePlacementIgnoresSpectatorShellButBlocksSurvivalPlayer(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var shellFixture = com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture
                .register(level, "release-shell");
        Villager holder = null;
        net.minecraft.world.entity.LivingEntity mouse = null;
        net.neoforged.neoforge.common.util.FakePlayer physicalPlayer = null;
        try {
            holder = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
            holder.setNoGravity(true);
            BlockPos feet = holder.blockPosition();
            level.setBlock(feet.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            mouse = ModEntities.MOUSE.get().create(level);
            require(mouse != null, "release shell mouse constructed");
            mouse.setNoGravity(true); mouse.setPos(holder.position());
            require(level.addFreshEntity(mouse), "release shell mouse loaded");
            var shell = shellFixture.player();
            shell.setPos(holder.position());
            shell.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            shell.noPhysics = false;
            require(shell.isSpectator() && !shell.noPhysics,
                    "actual spectator shell has its expected nonphysical-presence flags");
            require(BodyItemTargetProbe.portableBodyReleaseClear(level, mouse, holder, holder.position(),
                            mouse.getBbWidth(), mouse.getBbHeight()),
                    "vanilla spectator shell does not obstruct exact-holder release");
            physicalPlayer = new net.neoforged.neoforge.common.util.FakePlayer(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "release-physical"));
            physicalPlayer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            physicalPlayer.setPos(holder.position());
            physicalPlayer.noPhysics = false;
            require(level.addFreshEntity(physicalPlayer), "unbound physical player fixture joins level");
            require(physicalPlayer.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SURVIVAL
                            && !physicalPlayer.isSpectator() && !physicalPlayer.noPhysics,
                    "unbound native player fixture has physical Survival collision state");
            require(!BodyItemTargetProbe.portableBodyReleaseClear(level, mouse, holder, holder.position(),
                            mouse.getBbWidth(), mouse.getBbHeight()),
                    "ordinary physical Survival player remains a release blocker");
            helper.succeed();
        } finally {
            shellFixture.close();
            if (mouse != null) mouse.discard();
            if (holder != null) holder.discard();
            if (physicalPlayer != null) physicalPlayer.discard();
        }
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 750)
    @GameTestTags({"living-carry", "portable-escape"})
    public static void timedEscapeReleasesExactMouseFromHandKey(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        helper.getLevel().setBlock(carrier.blockPosition().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed");
        mouse.setNoAi(true);
        mouse.setPos(carrier.getX() + .25, carrier.getY(), carrier.getZ());
        require(helper.getLevel().addFreshEntity(mouse), "same mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
        UUID keyId = UUID.randomUUID();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, keyId);
        String keyHand = empty.activeHand();
        var placed = empty.putWhole(empty.revision(), keyHand, new ItemToken("escape-key"), key);
        require(placed.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, empty, placed.state()), "publish key");
        var containment = BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel());
        var owner = containment.ownerOf(mouse).orElseThrow();
        var beforeBody = mouse.position();
        var beforeRoot = carrier.position();
        var diagnostics = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        AutoCloseable diagnosticSubscription = PortableBodyEscapeService.observeDiagnostics(diagnostic -> {
            if (diagnostic.bodyId().equals(mouse.getUUID()))
                diagnostics.add(diagnostic.stage() + ":" + diagnostic.detail());
        });
        require(owner.tryEscape(mouse), "live canonical owner starts timed escape");
        var expectedOperation = activeEscape(mouse);
        var trace = new java.util.ArrayList<String>();
        traceEscape(helper, mouse, owner, beforeBody, trace, 0);
        awaitEscapeDeadline(helper, mouse, expectedOperation, () -> {
            var terminal = operation(mouse, expectedOperation.id());
            try { diagnosticSubscription.close(); } catch (Exception ignored) { }
            require(terminal != null && terminal.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.COMPLETED,
                    "captured hand-key escape op did not complete: id=" + expectedOperation.id() + ", due="
                            + expectedOperation.dueTick() + ", terminal=" + terminal + ", diagnostics=" + diagnostics
                            + ", beforeBody=" + beforeBody + ", afterBindBody=" + mouse.position() + ", beforeRoot="
                            + beforeRoot + ", rootNow=" + carrier.position() + ", bodyRootDistance="
                            + beforeBody.distanceTo(beforeRoot) + ", hull=" + mouse.getBbWidth() + "x" + mouse.getBbHeight()
                            + ", velocity=" + mouse.getDeltaMovement() + ", flags=" + mouse.isInvisible() + "/"
                            + mouse.noPhysics + "/" + mouse.isNoGravity() + ", trace=" + trace);
            LiveHands after = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(after != null && after.token(keyHand).isEmpty(), "exact key removed after five seconds; token="
                    + (after == null ? "no-state" : after.token(keyHand).orElse("empty")) + ", gameTime="
                    + helper.getLevel().getGameTime() + ", " + escapeDiagnostic(mouse, helper) + ", trace=" + trace);
            require(helper.getLevel().getEntity(mouse.getUUID()) == mouse && !mouse.isInvisible()
                    && !mouse.noPhysics && !mouse.isNoGravity(), "original mouse released with original identity and flags");
            require(containment.ownerOf(mouse).isEmpty(), "successful escape clears containment owner");
            carrier.discard(); mouse.discard(); helper.succeed();
        });
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 50)
    @GameTestTags({"living-carry", "portable-escape"})
    public static void invalidPreparedReleasePlacementPreservesKeyOwnerAndFlags(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed"); mouse.setNoAi(true);
        mouse.setPos(carrier.getX() + .25, carrier.getY(), carrier.getZ());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
        String hand = empty.activeHand(); String token = "release-stale-token";
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        LiveHands candidate = empty.putWhole(empty.revision(), hand, new ItemToken(token), key).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, empty, candidate), "key committed");
        var containment = BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel());
        var owner = containment.ownerOf(mouse).orElseThrow();
        var snapshot = PortableBodyReleaseTransaction.capture(mouse);
        require(snapshot != null, "exact root snapshot captured");
        var denied = PortableBodyReleaseTransaction.release(snapshot,
                new PortableBodyReleaseTransaction.Placement(mouse.position(), net.minecraft.world.phys.Vec3.ZERO, () -> false));
        require(denied == PortableBodyReleaseTransaction.Result.DENIED, "collision invalidation denies before mutation");
        require(carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token(hand).filter(token::equals).isPresent()
                        && containment.registeredOwnerOf(mouse).orElse(null) == owner
                        && containment.export(mouse, owner).isPresent(),
                "failed prepared placement preserves exact key, original owner object, and saved flags");
        carrier.discard(); mouse.discard(); helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"living-carry", "portable-malformed-host"})
    public static void unreadableHostsWithRawPortableKeysAreDeniedWithoutConsumingCustody(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed");
        mouse.setNoAi(true);
        mouse.setPos(carrier.getX() + .25, carrier.getY(), carrier.getZ());
        require(helper.getLevel().addFreshEntity(mouse), "mouse enters level");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands before = LiveHands.initialize(carrier).orElseThrow();
        String hand = before.activeHand();
        UUID keyId = UUID.randomUUID();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, keyId);
        require(!key.isEmpty(), "valid key references exact mouse");
        var held = before.putWhole(before.revision(), hand, new ItemToken("raw-portable-key"), key);
        require(held.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, before, held.state()),
                "valid exact key commits through production gateway");
        LiveHands committed = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String candidateHand = committed.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElseThrow();
        var containment = BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel());
        var owner = containment.registeredOwnerOf(mouse).orElseThrow();
        var flags = containment.export(mouse, owner).orElseThrow();

        ItemStack oversizedRaw = new ItemStack(ModItems.BELT.get());
        oversizedRaw.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                new GridContents.Entry("key", key, 0, 0, GridGeometry.Rotation.DEG_0),
                new GridContents.Entry("outside", new ItemStack(com.juicyslew.moonstation14.item.ModItems.PICKAXE.get()), 7, 0,
                        GridGeometry.Rotation.DEG_0))));
        require(GridContents.read(oversizedRaw).isEmpty(), "oversized host cannot resolve through shared grid parser");
        assertRawKeyHostDenied(helper, carrier, mouse, committed, hand, candidateHand, oversizedRaw, containment, owner, flags,
                "unreadable oversized raw grid");

        ItemStack dualRaw = new ItemStack(ModItems.BELT.get());
        dualRaw.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.empty());
        dualRaw.set(ModDataComponents.POUCH_CONTENTS.get(), PouchContents.of("key", key));
        require(GridContents.read(dualRaw).isEmpty(), "dual grid/pouch host remains unreadable");
        assertRawKeyHostDenied(helper, carrier, mouse, committed, hand, candidateHand, dualRaw, containment, owner, flags,
                "dual-component raw pouch key");

        require(helper.getLevel().getEntity(mouse.getUUID()) == mouse
                        && containment.registeredOwnerOf(mouse).orElse(null) == owner
                        && containment.export(mouse, owner).filter(flags::equals).isPresent(),
                "both malformed representations preserve original entity, owner, and flags");
        carrier.discard(); mouse.discard(); helper.succeed();
    }

    private static void assertRawKeyHostDenied(GameTestHelper helper, Villager carrier, net.minecraft.world.entity.LivingEntity mouse,
            LiveHands committed, String hand, String candidateHand, ItemStack host, BodyContainmentService containment,
            com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner owner,
            BodyContainmentService.ContainedBodyFlags flags, String label) {
        require(PortableBodyCarrying.containsPortableReferences(host), label + " raw key is detected");
        LiveHands current = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(current == committed, label + " test starts from exact committed state");
        var staged = current.putWhole(current.revision(), candidateHand, new ItemToken(label), host);
        require(staged.succeeded(), label + " candidate stages without publishing");
        require(!LiveHandsCommitGateway.commitTrusted(carrier, current, staged.state()),
                label + " production hand gateway rejects the unreadable portable-key host");
        LiveHands after = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after == current && after.stackCopy(hand).orElseThrow().get(ModDataComponents.PORTABLE_BODY_REFERENCE.get()) != null
                        && PortableBodyKeys.reference(after.stackCopy(hand).orElseThrow()).body().equals(mouse.getUUID())
                        && after.stackCopy(candidateHand).isEmpty(),
                label + " rejection preserves exact held key and hand snapshot");
        require(containment.registeredOwnerOf(mouse).orElse(null) == owner
                        && containment.export(mouse, owner).filter(flags::equals).isPresent(),
                label + " rejection preserves same body custody and original flags");
        require(helper.getLevel().getEntity(mouse.getUUID()) == mouse,
                label + " rejection preserves exact loaded mouse identity");
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 50)
    @GameTestTags({"living-carry", "portable-escape"})
    public static void postPreparationCollisionFailureRestoresReferenceAndOriginalFlags(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed"); mouse.setNoAi(true);
        mouse.setPos(carrier.getX() + .25, carrier.getY(), carrier.getZ());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
        String hand = empty.activeHand(); String token = "release-collision-token";
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        LiveHands candidate = empty.putWhole(empty.revision(), hand, new ItemToken(token), key).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, empty, candidate), "key committed");
        var containment = BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel());
        var originalOwner = containment.ownerOf(mouse).orElseThrow();
        var originalFlags = containment.export(mouse, originalOwner).orElseThrow();
        var snapshot = PortableBodyReleaseTransaction.capture(mouse);
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        var denied = PortableBodyReleaseTransaction.release(snapshot,
                new PortableBodyReleaseTransaction.Placement(mouse.position(), net.minecraft.world.phys.Vec3.ZERO,
                        () -> checks.incrementAndGet() == 1));
        require(denied == PortableBodyReleaseTransaction.Result.DENIED && checks.get() >= 2,
                "collision invalidation after staged removal reports ordinary denial");
        LiveHands after = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var restoredOwner = containment.registeredOwnerOf(mouse).orElse(null);
        require(after != null && after.token(hand).filter(token::equals).isPresent()
                        && PortableBodyKeys.reference(after.stackCopy(hand).orElseThrow()).body().equals(mouse.getUUID())
                        && restoredOwner instanceof PortableBodyCarrierOwner restored
                        && restored.carrier() == carrier && restored.reference().equals(snapshot.reference())
                        && containment.export(mouse, restoredOwner).filter(originalFlags::equals).isPresent(),
                "rollback retains same body/key/root and original flags under canonical restored owner");
        carrier.discard(); mouse.discard(); helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 50)
    @GameTestTags({"living-carry", "portable-escape"})
    public static void staleRootSnapshotDeniesWithoutChangingBodyCustody(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed"); mouse.setNoAi(true);
        mouse.setPos(carrier.getX() + .25, carrier.getY(), carrier.getZ());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
        String hand = empty.activeHand();
        String token = "release-stale-root-token";
        LiveHands candidate = empty.putWhole(empty.revision(), hand, new ItemToken(token),
                PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID())).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, empty, candidate), "key committed");
        var containment = BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel());
        var owner = containment.ownerOf(mouse).orElseThrow();
        var flags = containment.export(mouse, owner).orElseThrow();
        var snapshot = PortableBodyReleaseTransaction.capture(mouse);
        LiveHands current = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var taken = current.takeWhole(current.revision(), hand, new ItemToken(token));
        var retokened = taken.state().putWhole(taken.state().revision(), hand,
                new ItemToken("release-stale-root-token-replaced"), taken.takenStack().orElseThrow());
        require(taken.succeeded() && retokened.succeeded()
                && LiveHandsCommitGateway.commitTrusted(carrier, current, retokened.state()),
                "same live key changes root token through a committed transfer");
        var result = PortableBodyReleaseTransaction.release(snapshot,
                new PortableBodyReleaseTransaction.Placement(mouse.position(), net.minecraft.world.phys.Vec3.ZERO, () -> true));
        var currentOwner = containment.registeredOwnerOf(mouse).orElse(null);
        require(result == PortableBodyReleaseTransaction.Result.DENIED
                        && currentOwner instanceof PortableBodyCarrierOwner carried && carried.carrier() == carrier
                        && carried.handToken().equals("release-stale-root-token-replaced")
                        && containment.export(mouse, currentOwner).filter(flags::equals).isPresent()
                        && PortableBodyKeys.reference(carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())
                        .stackCopy(hand).orElseThrow()).body().equals(mouse.getUUID()),
                "stale root snapshot preserves exact key, current canonical owner and saved body flags");
        carrier.discard(); mouse.discard(); helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 750)
    @GameTestTags({"living-carry", "portable-escape"})
    public static void timedEscapeReleasesExactMouseFromHeldBagGrid(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        helper.getLevel().setBlock(carrier.blockPosition().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel()); require(mouse != null, "mouse constructed");
        mouse.setNoAi(true); mouse.setPos(carrier.getX() + .25, carrier.getY(), carrier.getZ());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) carrier.level());
        LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
        String hostHand = empty.activeHand(), keyHand = empty.handIds().stream()
                .filter(id -> !id.equals(hostHand)).findFirst().orElseThrow();
        LiveHands withBag = empty.putWhole(empty.revision(), hostHand, new ItemToken("escape-grid-host"),
                new ItemStack(ModItems.BAG.get())).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, empty, withBag), "bag committed");
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        LiveHands withKey = withBag.putWhole(withBag.revision(), keyHand, new ItemToken("escape-grid-key"), key).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, withBag, withKey), "key committed");
        require(BodyHeldGridTransfer.transferOwned(carrier, hostHand, "escape-grid-host", keyHand,
                "escape-grid-key", withKey.revision(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                null, null, true, () -> true) == BodyHeldGridTransfer.Result.SUCCESS, "key stored through normal held-grid route");
        var containment = BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) carrier.level());
        var owner = containment.ownerOf(mouse).orElseThrow();
        require(owner instanceof PortableBodyCarrierOwner, "held grid derives carrier custody");
        require(owner.tryEscape(mouse), "timed escape starts from held grid");
        var expectedOperation = activeEscape(mouse);
        awaitEscapeDeadline(helper, mouse, expectedOperation, () -> {
            var terminal = operation(mouse, expectedOperation.id());
            require(terminal != null && terminal.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.COMPLETED,
                    "captured held-grid escape op did not complete: id=" + expectedOperation.id() + ", due="
                            + expectedOperation.dueTick() + ", terminal=" + terminal);
            var host = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).stackCopy(hostHand).orElseThrow();
            require(com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(host).orElseThrow().entries().stream()
                    .noneMatch(entry -> PortableBodyKeys.isKeyItem(entry.stack())), "held-grid key removed on release");
            require(helper.getLevel().getEntity(mouse.getUUID()) == mouse && !mouse.isInvisible()
                    && containment.ownerOf(mouse).isEmpty(), "same mouse is released from held grid");
            carrier.discard(); mouse.discard(); helper.succeed();
        });
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 750)
    @GameTestTags({"living-carry", "portable-escape"})
    public static void timedEscapeReleasesExactMouseFromWorldBagGrid(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        helper.getLevel().setBlock(carrier.blockPosition().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel()); require(mouse != null, "mouse constructed");
        mouse.setNoAi(true); mouse.setPos(carrier.getX() + .25, carrier.getY(), carrier.getZ());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) carrier.level());
        var level = (net.minecraft.server.level.ServerLevel) carrier.level();
        LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
        String hand = empty.activeHand();
        LiveHands withKey = empty.putWhole(empty.revision(), hand, new ItemToken("escape-world-key"),
                PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID())).state();
        PortableBodyReference reference = PortableBodyKeys.reference(withKey.stackCopy(hand).orElseThrow());
        require(LiveHandsCommitGateway.commitTrusted(carrier, empty, withKey), "key committed");
        var root = new net.minecraft.world.entity.item.ItemEntity(level, carrier.getX() + .5, carrier.getY(), carrier.getZ(),
                new ItemStack(ModItems.BAG.get()));
        root.setNoGravity(true);
        root.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level.setBlock(root.blockPosition().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        require(level.addFreshEntity(root), "world bag root added");
        aimAt(carrier, root.position().add(0, .15, 0));
        ItemStack originalRoot = root.getItem();
        require(BodyWorldGridTransfer.storeOwned(carrier, root, hand, "escape-world-key", withKey.revision(),
                originalRoot, originalRoot.copy(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, () -> true)
                == BodyWorldGridTransfer.Result.SUCCESS, "key stored through normal world-grid route");
        // The world root owns the mouse now; move the former carrier out of the release corridor
        // and give the mouse a clear, loaded-floor-facing direction instead of relying on defaults.
        carrier.setPos(carrier.getX() + 5, carrier.getY(), carrier.getZ());
        for (int z = 0; z <= 4; z++)
            level.setBlock(root.blockPosition().offset(0, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        mouse.setYRot(0f);
        mouse.setXRot(0f);
        var containment = BodyContainmentService.forLevel(level);
        var owner = containment.ownerOf(mouse).orElseThrow();
        require(owner instanceof PortableWorldStorageOwner, "world root derives custody owner");
        UUID capturedRootId = root.getUUID();
        var releaseDiagnostic = new java.util.concurrent.atomic.AtomicReference<PortableBodyReleaseTransaction.Diagnostic>();
        var escapeDiagnostics = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        AutoCloseable diagnosticSubscription = PortableBodyReleaseTransaction.observeDiagnostics(diagnostic -> {
            if (diagnostic.bodyId().equals(mouse.getUUID()) && diagnostic.rootId().equals(capturedRootId))
                releaseDiagnostic.set(diagnostic);
        });
        AutoCloseable escapeSubscription = PortableBodyEscapeService.observeDiagnostics(diagnostic -> {
            if (diagnostic.bodyId().equals(mouse.getUUID()) && capturedRootId.equals(diagnostic.rootId())
                    && !diagnostic.stage().equals("started")
                    && escapeDiagnostics.size() < 24)
                escapeDiagnostics.add(diagnostic.stage() + ":" + diagnostic.detail());
        });
        var start = mouse.position();
        require(owner.tryEscape(mouse), "timed escape starts from world grid: body=" + mouse.position()
                + ", root=" + owner.position() + ", distance=" + mouse.position().distanceTo(owner.position())
                + ", hull=" + mouse.getBbWidth() + "x" + mouse.getBbHeight() + ", ownerValid=" + owner.isValid()
                + ", registered=" + containment.registeredOwnerOf(mouse).orElse(null) + ", current="
                + PortableBodyReleaseTransaction.capture(mouse));
        var operation = activeEscape(mouse);
        var trace = new java.util.ArrayList<String>();
        traceEscape(helper, mouse, owner, start, trace, 0);
        awaitEscapeDeadline(helper, mouse, operation, () -> {
            var terminal = operation(mouse, operation.id());
            var diagnostic = releaseDiagnostic.get();
            try { diagnosticSubscription.close(); } catch (Exception ignored) { }
            try { escapeSubscription.close(); } catch (Exception ignored) { }
            require(terminal != null && terminal.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.COMPLETED,
                    "exact escape operation did not complete: id=" + operation.id() + ", op=" + terminal);
            require(diagnostic != null && diagnostic.bodyId().equals(mouse.getUUID())
                            && diagnostic.rootId().equals(capturedRootId)
                            && diagnostic.result() == PortableBodyReleaseTransaction.Result.SUCCESS,
                    "no matching body/root release diagnostic: body=" + mouse.getUUID() + ", root=" + capturedRootId
                            + ", diagnostic=" + diagnostic + ", escapeDiagnostics=" + escapeDiagnostics
                            + ", operation=" + terminal + ", owner=" + containment.registeredOwnerOf(mouse).orElse(null));
            require(!PortableBodyCarrying.exactWorldEntry(root.getItem(), "escape-world-key",
                    reference),
                    "world-grid key removed on release; result=" + diagnostic
                            + ", currentEntry=" + PortableBodyCarrying.exactWorldEntry(root.getItem(), "escape-world-key", reference)
                            + ", entries=" + com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(root.getItem())
                            .map(contents -> contents.entries().stream().map(entry -> entry.token() + "/"
                                    + PortableBodyKeys.reference(entry.stack())).toList()).orElse(java.util.List.of())
                            + ", owner=" + (containment.registeredOwnerOf(mouse).orElse(null) instanceof PortableWorldStorageOwner current
                                    ? current.rootId() + "/" + current.childToken() + "/" + current.isValid() : "none")
                            + ", escapeDiagnostics=" + escapeDiagnostics);
            require(helper.getLevel().getEntity(mouse.getUUID()) == mouse && !mouse.isInvisible()
                    && containment.ownerOf(mouse).isEmpty(), "same mouse is released from world bag");
            carrier.discard(); root.discard(); mouse.discard(); helper.succeed();
        });
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 45)
    @GameTestTags({"living-carry", "portable-escape"})
    public static void preparedWorldReleaseDeniesRootMutationAfterPrepare(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mouse = ModEntities.MOUSE.get().create(level);
        require(mouse != null, "mouse constructed");
        mouse.setNoAi(true); mouse.setNoGravity(true); mouse.setPos(carrier.position());
        require(level.addFreshEntity(mouse), "original mouse loaded");
        CharacterIdentitySystem.enrollSupportedActor(mouse, level);
        LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
        String hand = empty.activeHand();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        var withKey = empty.putWhole(empty.revision(), hand, new ItemToken("prepared-root-key"), key);
        require(withKey.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, empty, withKey.state()),
                "publish single body key");
        ItemStack bag = new ItemStack(ModItems.BAG.get());
        var root = new net.minecraft.world.entity.item.ItemEntity(level, carrier.getX() + 1.5, carrier.getY(),
                carrier.getZ(), bag);
        require(level.addFreshEntity(root), "world root loaded");
        require(BodyWorldGridTransfer.storeOwned(carrier, root, hand, "prepared-root-key", withKey.state().revision(),
                root.getItem(), root.getItem().copy(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                () -> true) == BodyWorldGridTransfer.Result.SUCCESS, "move key into world root");
        var containment = BodyContainmentService.forLevel(level);
        var owner = containment.ownerOf(mouse).orElseThrow();
        var flags = containment.export(mouse, owner).orElseThrow();
        var exactEntry = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(root.getItem()).orElseThrow()
                .entries().stream().filter(entry -> entry.token().equals("prepared-root-key")).findFirst().orElseThrow();
        var plan = BodyWorldWorldGridTransfer.preparePortableKeyRelease(root, exactEntry.token(),
                PortableBodyKeys.reference(exactEntry.stack()), exactEntry.stack());
        require(plan != null, "release root plan prepared against current root evidence");
        var changedContents = new java.util.ArrayList<>(com.juicyslew.moonstation14.ms14.storage.grid.GridContents
                .read(root.getItem()).orElseThrow().entries());
        changedContents.add(new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry("unrelated-after-prepare",
                new ItemStack(net.minecraft.world.item.Items.STICK), 1, 0, GridGeometry.Rotation.DEG_0));
        ItemStack changedRoot = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(root.getItem(),
                com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(changedContents)).orElseThrow();
        root.setItem(changedRoot);
        ItemStack mutatedRootReference = root.getItem();
        ItemStack changedEvidence = root.getItem().copy();
        require(!plan.remove(), "post-prepare root change denies publication");
        require(root.getItem() == mutatedRootReference && net.minecraft.world.item.ItemStack.matches(changedEvidence, root.getItem())
                        && PortableBodyCarrying.exactWorldEntry(root.getItem(), "prepared-root-key",
                        PortableBodyKeys.reference(key))
                        && level.getEntity(mouse.getUUID()) == mouse
                        && containment.registeredOwnerOf(mouse).orElse(null) == owner && owner.isValid()
                        && containment.export(mouse, owner).filter(flags::equals).isPresent(),
                "denial preserves mutated root, exact key, original body UUID, owner, and original flags");
        root.discard(); mouse.discard(); carrier.discard(); helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void exactHandKeyBindsOptedInLiveMouseAndTracksCarrier(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse entity constructed");
        mouse.setNoAi(true);
        BlockPos mousePos = helper.absolutePos(new BlockPos(2, 1, 2));
        mouse.setPos(mousePos.getX() + .5, mousePos.getY(), mousePos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        require(com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry.registered("PortableBody"),
                "typed portable-body component registered");
        require(HandCapability.resolveCharacter(mouse).flatMap(c -> c.component(
                com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent.class)).isPresent(),
                "resolved mouse prototype opts in");

        helper.runAfterDelay(1, () -> {
            LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
            UUID tokenId = UUID.randomUUID();
            ItemStack key = new ItemStack(ModItems.PORTABLE_BODY_KEY.get());
            key.set(ModDataComponents.PORTABLE_BODY_REFERENCE.get(), new PortableBodyReference(
                    carrier.level().dimension().location(), mouse.getUUID(), tokenId));
            LiveHands.WholeResult placed = empty.putWhole(empty.revision(), empty.activeHand(),
                    new ItemToken(tokenId.toString()), key);
            require(placed.succeeded(), "one exact key placed in candidate hand");
            LiveHands candidate = placed.state();
            require(PortableBodyCarrying.validateProspective(carrier, candidate), "candidate reference resolves and is unique");
            require(LiveHandsCommitGateway.commitTrusted(carrier, empty, candidate), "normal trusted gateway publishes candidate");
            require(BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) carrier.level())
                    .ownerOf(mouse).isPresent(), "gateway binds containment after committed candidate");
            BodyContainmentService containment = BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) carrier.level());
            var owner = containment.ownerOf(mouse).orElseThrow();
            require(owner instanceof PortableBodyCarrierOwner && owner.isValid(), "exact current hand key authenticates owner");
            var pinnedBefore = owner.positionFor(mouse);
            BlockPos moved = helper.absolutePos(new BlockPos(4, 1, 4));
            carrier.setPos(moved.getX() + .5, moved.getY(), moved.getZ() + .5);
            containment.tick();
            require(!mouse.position().equals(pinnedBefore) && mouse.position().distanceTo(carrier.position()) < 1.0,
                    "containment tick repins original mouse at carrier body");
            require(owner.airSource().isPresent(), "carried mouse samples the carrier's world atmosphere source");
            require(containment.unbind(mouse, owner), "fixture releases tracked mouse ownership");
            mouse.discard();
            carrier.discard();
            helper.succeed();
        });
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 60)
    public static void liveMouseKeyMovesHandBagHandAndBeltHandWithoutReplacingBody(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse created");
        mouse.setNoAi(true);
        mouse.setPos(carrier.getX() + 1, carrier.getY(), carrier.getZ());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());

        LiveHands before = LiveHands.initialize(carrier).orElseThrow();
        String hand = before.activeHand();
        String other = before.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElseThrow();
        LiveHands withBag = before.putWhole(before.revision(), hand, new ItemToken("bag-host"),
                new ItemStack(ModItems.BAG.get())).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, before, withBag), "publish bag to hand ledger");
        UUID stableKeyIdentity = UUID.randomUUID();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, stableKeyIdentity);
        LiveHands withKey = withBag.putWhole(withBag.revision(), other, new ItemToken("fresh-hand-token"), key).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, withBag, withKey), "publish non-owning key reference");
        String sourceToken = withKey.token(other).orElseThrow();
        var containment = BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) carrier.level());
        var initialOwner = containment.ownerOf(mouse).orElseThrow();
        require(initialOwner instanceof PortableBodyCarrierOwner, "mouse initially owned through current hand locator");
        require(BodyHeldGridTransfer.transferOwned(carrier, hand, "bag-host", other, sourceToken, withKey.revision(),
                new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, null, null, true, () -> true)
                == BodyHeldGridTransfer.Result.SUCCESS, "existing held-bag store accepts portable key");
        LiveHands storedHeld = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var heldGrid = storedHeld.stackCopy(hand).orElseThrow();
        var heldEntry = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(heldGrid).orElseThrow()
                .entries().getFirst();
        require(BodyHeldGridTransfer.transferOwned(carrier, hand, "bag-host", other, heldEntry.token(),
                storedHeld.revision() - 1, new GridGeometry.Cell(0, 0), null, null, null, false, () -> true)
                == BodyHeldGridTransfer.Result.DENIED && carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == storedHeld,
                "stale held-grid extraction preserves key and owner");
        require(BodyHeldGridTransfer.transferOwned(carrier, hand, "bag-host", other, heldEntry.token(),
                storedHeld.revision(), new GridGeometry.Cell(0, 0), null, null, null, false, () -> true)
                == BodyHeldGridTransfer.Result.SUCCESS, "existing held-bag extract returns same reference to hand");
        LiveHands backInHand = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(PortableBodyKeys.reference(backInHand.stackCopy(other).orElseThrow()).token().equals(stableKeyIdentity),
                "held grid roundtrip preserves stable reference identity");
        require(BodyEquipmentTransfer.transferOwned(carrier, hand, "back", "bag-host", backInHand.revision(), true, () -> true)
                == BodyEquipmentTransfer.Result.SUCCESS, "equip bag through existing body transfer");
        LiveHands bagWorn = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands withBelt = bagWorn.putWhole(bagWorn.revision(), hand, new ItemToken("belt-host"),
                new ItemStack(ModItems.BELT.get())).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, bagWorn, withBelt), "publish belt to hand ledger");
        require(BodyEquipmentTransfer.transferOwned(carrier, hand, "belt", "belt-host", withBelt.revision(), true, () -> true)
                == BodyEquipmentTransfer.Result.SUCCESS, "equip belt through existing body transfer");
        LiveHands beltWorn = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String wornSourceToken = beltWorn.token(other).orElseThrow();
        LiveHands withFreshKeyToken = beltWorn;
        if (withFreshKeyToken.token(other).isEmpty()) throw new GameTestAssertException("key remains in hand after held-grid extraction");
        require(BodyEquippedStorageTransfer.smartOwned(carrier, "belt", "belt-host", other, wornSourceToken,
                beltWorn.revision() - 1, true, () -> true) == BodyEquippedStorageTransfer.Result.DENIED
                && carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == beltWorn
                && containment.ownerOf(mouse).orElse(null) instanceof PortableBodyCarrierOwner,
                "stale worn-grid store preserves key and owner");
        require(BodyEquippedStorageTransfer.smartOwned(carrier, "belt", "belt-host", other, wornSourceToken,
                beltWorn.revision(), true, () -> true) == BodyEquippedStorageTransfer.Result.SUCCESS,
                "existing worn-belt store moves key into grid");
        LiveHands stored = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var belt = LiveHands.equipmentSnapshot(carrier, stored).orElseThrow().slots().stream()
                .filter(slot -> slot.id().equals("belt")).findFirst().orElseThrow().stack().orElseThrow();
        var entries = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(belt).orElseThrow().entries();
        require(entries.size() == 1 && PortableBodyKeys.isKey(entries.getFirst().stack())
                && PortableBodyKeys.reference(entries.getFirst().stack()).token().equals(stableKeyIdentity),
                "worn belt contains exactly the same stable live-body key identity");
        require(containment.ownerOf(mouse).orElseThrow() instanceof PortableBodyCarrierOwner storedOwner
                && storedOwner.carrier() == carrier, "existing gateway retains same body's owner in worn storage");

        require(BodyEquippedStorageTransfer.smartOwned(carrier, "belt", "belt-host", other,
                entries.getFirst().token(), stored.revision(), false, () -> true)
                == BodyEquippedStorageTransfer.Result.SUCCESS, "existing worn-belt extract returns key to hand");
        LiveHands retrieved = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack returned = retrieved.stackCopy(other).orElseThrow();
        require(PortableBodyKeys.reference(returned).token().equals(stableKeyIdentity)
                && PortableBodyKeys.reference(returned).body().equals(mouse.getUUID())
                && containment.ownerOf(mouse).orElseThrow() instanceof PortableBodyCarrierOwner,
                "retrieval preserves reference and original mouse UUID");
        LiveHands finalHand = retrieved;
        String returnedToken = finalHand.token(other).orElseThrow();
        require(BodyEquippedStorageTransfer.smartOwned(carrier, "back", "bag-host", other, returnedToken,
                finalHand.revision(), true, () -> true) == BodyEquippedStorageTransfer.Result.SUCCESS,
                "store same key in bag before testing world-drop guard");
        LiveHands keyInBag = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyEquipmentTransfer.transferOwned(carrier, hand, "back", "bag-host", keyInBag.revision(), false, () -> true)
                == BodyEquipmentTransfer.Result.SUCCESS, "unequip bag with contained key through body transfer");
        LiveHands bagInHand = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyHandItemTransfer.dropOwned(carrier, hand, "bag-host", bagInHand.revision(), () -> true)
                        == BodyHandItemTransfer.Result.SUCCESS
                        && containment.ownerOf(mouse).orElse(null) instanceof PortableWorldStorageOwner,
                "dropping bag moves live mouse custody to the published world root");
        var droppedBag = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                carrier.getBoundingBox().inflate(4), item -> PortableBodyCarrying.containsPortableReferences(item.getItem()))
                .stream().findFirst().orElseThrow();
        droppedBag.discard();
        mouse.discard();
        carrier.discard();
        helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 60)
    public static void fullHeldGridDeniesPortableKeyStoreUnchanged(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse created");
        mouse.setNoAi(true);
        mouse.setPos(carrier.getX() + 1, carrier.getY(), carrier.getZ());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) carrier.level());
        var fullEntries = new java.util.ArrayList<com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry>();
        for (int y = 0; y < 4; y++) for (int x = 0; x < 7; x++)
            fullEntries.add(new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry(
                    "cell-" + x + "-" + y, new ItemStack(net.minecraft.world.item.Items.STICK), x, y,
                    GridGeometry.Rotation.DEG_0));
        ItemStack fullBag = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(
                new ItemStack(ModItems.BAG.get()), com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(fullEntries))
                .orElseThrow();
        LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
        String hostHand = empty.activeHand();
        String keyHand = empty.handIds().stream().filter(id -> !id.equals(hostHand)).findFirst().orElseThrow();
        LiveHands withBag = empty.putWhole(empty.revision(), hostHand, new ItemToken("full-bag"), fullBag).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, empty, withBag), "publish full bag");
        UUID keyId = UUID.randomUUID();
        LiveHands withKey = withBag.putWhole(withBag.revision(), keyHand, new ItemToken("portable-key-token"),
                PortableBodyCarrying.createKey(carrier, mouse, keyId)).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, withBag, withKey), "publish live key");
        var containment = BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) carrier.level());
        var owner = containment.ownerOf(mouse).orElseThrow();
        require(BodyHeldGridTransfer.transferOwned(carrier, hostHand, "full-bag", keyHand,
                "portable-key-token", withKey.revision(), new GridGeometry.Cell(0, 0),
                GridGeometry.Rotation.DEG_0, null, null, true, () -> true) == BodyHeldGridTransfer.Result.DENIED
                        && carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == withKey
                        && containment.ownerOf(mouse).orElse(null) == owner
                        && PortableBodyKeys.reference(withKey.stackCopy(keyHand).orElseThrow()).body().equals(mouse.getUUID()),
                "full grid conserves key, attachment, exact body and containment owner");
        require(containment.unbind(mouse, owner), "fixture unbinds full-grid mouse");
        mouse.discard();
        carrier.discard();
        helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    public static void worldBagOwnsOriginalMouseAcrossStoragePickupAndDrop(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse created");
        mouse.setNoAi(true);
        mouse.setPos(carrier.getX(), carrier.getY(), carrier.getZ() + 1.2);
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) carrier.level());
        LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
        String hand = empty.activeHand();
        String spare = empty.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElseThrow();
        UUID keyId = UUID.randomUUID();
        LiveHands withKey = empty.putWhole(empty.revision(), hand, new ItemToken("world-key"),
                PortableBodyCarrying.createKey(carrier, mouse, keyId)).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, empty, withKey), "publish portable key");
        ItemStack bag = new ItemStack(ModItems.BAG.get());
        var level = (net.minecraft.server.level.ServerLevel) carrier.level();
        var host = new net.minecraft.world.entity.item.ItemEntity(level, carrier.getX() + 1.5, carrier.getY(), carrier.getZ(), bag);
        require(level.addFreshEntity(host), "world bag host added");
        var firstHost = host;
        aimAt(carrier, host.position().add(0, .15, 0));
        var originalRef = host.getItem();
        var initialOwner = BodyContainmentService.forLevel(level).ownerOf(mouse).orElseThrow();
        require(BodyWorldGridTransfer.storeOwned(carrier, host, hand, "world-key", withKey.revision() - 1,
                originalRef, originalRef.copy(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, () -> true)
                == BodyWorldGridTransfer.Result.DENIED
                        && carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == withKey
                        && host.getItem() == originalRef
                        && BodyContainmentService.forLevel(level).ownerOf(mouse).orElse(null) == initialOwner,
                "stale world storage preserves hand, root, and owner");
        require(BodyWorldGridTransfer.storeOwned(carrier, host, hand, "world-key", withKey.revision(),
                originalRef, originalRef.copy(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, () -> true)
                == BodyWorldGridTransfer.Result.SUCCESS, "body hand key transfers into world bag grid");
        LiveHands stored = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var containment = BodyContainmentService.forLevel(level);
        var worldOwner = containment.ownerOf(mouse).orElseThrow();
        require(worldOwner instanceof PortableWorldStorageOwner owner && owner.rootId().equals(host.getUUID())
                && owner.reference().token().equals(keyId), "world bag UUID and child key derive canonical owner");
        var hostGrid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(host.getItem()).orElseThrow();
        var child = hostGrid.entries().getFirst();
        require(child.token().equals("world-key") && PortableBodyKeys.reference(child.stack()).body().equals(mouse.getUUID()),
                "world grid retains the stable live mouse reference");
        var secondHost = new net.minecraft.world.entity.item.ItemEntity(level, carrier.getX() + 2.5, carrier.getY(), carrier.getZ(),
                new ItemStack(ModItems.BAG.get()));
        require(level.addFreshEntity(secondHost), "second world bag host added");
        require(BodyWorldWorldGridTransfer.transfer(carrier, host, secondHost, child.token(), child.x(), child.y(),
                child.rotation(), 0, 0, GridGeometry.Rotation.DEG_0, () -> true)
                == BodyWorldWorldGridTransfer.Outcome.SUCCESS, "existing world-world grid transfer moves exact key owner");
        require(containment.ownerOf(mouse).orElseThrow() instanceof PortableWorldStorageOwner movedWorldOwner
                && movedWorldOwner.rootId().equals(secondHost.getUUID()), "world-to-world transfer follows current root UUID");
        host = secondHost;
        hostGrid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(host.getItem()).orElseThrow();
        child = hostGrid.entries().getFirst();
        host.setPos(host.getX() + 2, host.getY(), host.getZ());
        containment.tick();
        require(mouse.position().distanceTo(host.position()) < 1, "world root movement repins original mouse");

        var currentRoot = host.getItem();
        require(BodyWorldGridTransfer.takeOwned(carrier, host, spare, child.token(), stored.revision(), currentRoot,
                currentRoot.copy(), new GridGeometry.Cell(child.x(), child.y()), child.rotation(), () -> true)
                == BodyWorldGridTransfer.Result.SUCCESS, "world bag take returns original key to body hand");
        LiveHands retrieved = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(PortableBodyKeys.reference(retrieved.stackCopy(spare).orElseThrow()).token().equals(keyId)
                && containment.ownerOf(mouse).orElseThrow() instanceof PortableBodyCarrierOwner,
                "world-to-body transfer preserves key identity and canonical mouse owner");

        var fullContents = new java.util.ArrayList<com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry>();
        for (int y = 0; y < 4; y++) for (int x = 0; x < 7; x++)
            fullContents.add(new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry(
                    "filled-" + x + "-" + y, new ItemStack(net.minecraft.world.item.Items.STICK), x, y,
                    GridGeometry.Rotation.DEG_0));
        var fullBag = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(
                new ItemStack(ModItems.BAG.get()), com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(fullContents))
                .orElseThrow();
        firstHost.setItem(fullBag);
        var fullRef = firstHost.getItem();
        require(BodyWorldGridTransfer.storeOwned(carrier, firstHost, spare, retrieved.token(spare).orElseThrow(),
                retrieved.revision(), fullRef, fullRef.copy(), new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                () -> true) == BodyWorldGridTransfer.Result.DENIED
                        && carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == retrieved
                        && firstHost.getItem() == fullRef
                        && containment.ownerOf(mouse).orElse(null) instanceof PortableBodyCarrierOwner,
                "full world grid denial conserves body key and current owner");

        var emptyRoot = host.getItem();
        host.setPos(carrier.getX() + 1.5, carrier.getY(), carrier.getZ());
        aimAt(carrier, host.position().add(0, .1, 0));
        var secondStore = BodyWorldGridTransfer.storeOwned(carrier, host, spare, retrieved.token(spare).orElseThrow(),
                retrieved.revision(), emptyRoot, emptyRoot.copy(), new GridGeometry.Cell(0, 0),
                GridGeometry.Rotation.DEG_0, () -> true);
        require(secondStore == BodyWorldGridTransfer.Result.SUCCESS,
                "same original key stores into the world bag again; result=" + secondStore + ", actor=" + carrier.position()
                        + ", host=" + host.position() + ", reachable=" + BodyItemTargetProbe.reachableStorageHost(level,
                        carrier, host, 5) + ", handToken=" + retrieved.token(spare).orElse(null)
                        + ", rootEntries=" + com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(host.getItem())
                        .map(grid -> grid.entries().stream().map(entry -> entry.token()).toList()).orElse(java.util.List.of()));
        LiveHands keyStoredAgain = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        host.setPos(carrier.getX() + 1.2, carrier.getY(), carrier.getZ());
        aimAt(carrier, host.position().add(0, .1, 0));
        require(BodyHandItemTransfer.pickupOwned(carrier, host.getUUID(), hand, keyStoredAgain.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "whole world bag pickup moves root ownership into carrier");
        LiveHands bagHeld = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(level.getEntity(host.getUUID()) == null || host.isRemoved(), "picked-up bag source entity removed");
        require(containment.ownerOf(mouse).orElseThrow() instanceof PortableBodyCarrierOwner heldOwner
                && heldOwner.carrier() == carrier, "whole-bag pickup transfers same mouse into carrier root");
        String bagToken = bagHeld.token(hand).orElseThrow();
        var oldBlocks = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        BlockPos origin = carrier.blockPosition();
        for (int x = origin.getX() - 6; x <= origin.getX() + 6; x++)
            for (int y = origin.getY() - 1; y <= origin.getY() + 8; y++)
                for (int z = origin.getZ() - 6; z <= origin.getZ() + 6; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    oldBlocks.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                }
        var blockedDrop = BodyHandItemTransfer.dropOwned(carrier, hand, bagToken, bagHeld.revision(), () -> true);
        require(blockedDrop == BodyHandItemTransfer.Result.DENIED
                        && carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == bagHeld
                        && containment.registeredOwnerOf(mouse).orElse(null) instanceof PortableBodyCarrierOwner,
                "blocked world-root drop conserves bag/body: result=" + blockedDrop + " stateSame="
                        + (carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == bagHeld)
                        + " registry=" + containment.registeredOwnerOf(mouse).orElse(null));
        oldBlocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
        containment.tick();
        aimAt(carrier, carrier.getEyePosition().add(2, 0, 0));
        var successfulDrop = BodyHandItemTransfer.dropOwned(carrier, hand, bagToken, bagHeld.revision(), () -> true);
        require(successfulDrop == BodyHandItemTransfer.Result.SUCCESS,
                "whole-bag drop publishes root before transferring mouse owner: " + successfulDrop);
        var droppedId = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        carrier.getBoundingBox().inflate(4), item -> com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrying.containsPortableReferences(item.getItem()))
                .stream().findFirst().map(net.minecraft.world.entity.Entity::getUUID).orElse(null);
        require(droppedId != null && containment.ownerOf(mouse).orElseThrow() instanceof PortableWorldStorageOwner droppedOwner
                && droppedOwner.rootId().equals(droppedId), "dropped bag remains sole root owner of same original mouse");
        require(level.getEntity(mouse.getUUID()) == mouse && !mouse.isRemoved(), "world custody never clones or removes physiology entity");
        level.getEntity(droppedId).discard();
        if (!firstHost.isRemoved()) firstHost.discard();
        if (!secondHost.isRemoved()) secondHost.discard();
        level.getEntity(mouse.getUUID()).discard();
        level.getEntity(carrier.getUUID()).discard();
        helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 60)
    public static void worldGridAllowsDistinctMouseDestinationAndPreservesOtherOwner(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var incomingBody = ModEntities.MOUSE.get().create(level);
        var residentBody = ModEntities.MOUSE.get().create(level);
        require(incomingBody != null && residentBody != null, "two mouse originals constructed");
        incomingBody.setNoAi(true); residentBody.setNoAi(true);
        incomingBody.setPos(carrier.getX(), carrier.getY(), carrier.getZ());
        residentBody.setPos(carrier.getX(), carrier.getY(), carrier.getZ());
        require(level.addFreshEntity(incomingBody) && level.addFreshEntity(residentBody), "two originals loaded");
        CharacterIdentitySystem.enrollSupportedActor(incomingBody, level);
        CharacterIdentitySystem.enrollSupportedActor(residentBody, level);
        var incomingKey = PortableBodyCarrying.createKey(carrier, incomingBody, UUID.randomUUID());
        var residentKey = PortableBodyCarrying.createKey(carrier, residentBody, UUID.randomUUID());
        var sourceGrid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(List.of(
                new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry("incoming", incomingKey, 0, 0,
                        GridGeometry.Rotation.DEG_0)));
        var residentGrid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(List.of(
                new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry("resident", residentKey, 0, 0,
                        GridGeometry.Rotation.DEG_0)));
        var sourceItem = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(
                new ItemStack(ModItems.BAG.get()), sourceGrid).orElseThrow();
        var destinationItem = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(
                new ItemStack(ModItems.BAG.get()), residentGrid).orElseThrow();
        var source = new net.minecraft.world.entity.item.ItemEntity(level, carrier.getX() + 1, carrier.getY(), carrier.getZ(), sourceItem);
        var destination = new net.minecraft.world.entity.item.ItemEntity(level, carrier.getX() + 2, carrier.getY(), carrier.getZ(), destinationItem);
        require(level.addFreshEntity(source) && level.addFreshEntity(destination), "source and occupied destination roots added");
        var incomingRef = PortableBodyKeys.reference(incomingKey);
        var residentRef = PortableBodyKeys.reference(residentKey);
        var containment = BodyContainmentService.forLevel(level);
        var incomingOwner = new PortableWorldStorageOwner(level, source.getUUID(), "incoming", incomingRef);
        var residentOwner = new PortableWorldStorageOwner(level, destination.getUUID(), "resident", residentRef);
        require(containment.bind(incomingBody, incomingOwner) && containment.bind(residentBody, residentOwner),
                "both mouse/root associations are registered");

        var sourceEntries = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(source.getItem()).orElseThrow().entries();
        var incoming = sourceEntries.getFirst();
        var originalDestination = destination.getItem();
        require(BodyWorldWorldGridTransfer.transfer(carrier, source, destination, "incoming", incoming.x(), incoming.y(),
                incoming.rotation(), 1, 0, GridGeometry.Rotation.DEG_0, () -> true)
                == BodyWorldWorldGridTransfer.Outcome.SUCCESS, "distinct mouse enters a world bag already holding another mouse");
        var destinationEntries = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(destination.getItem()).orElseThrow().entries();
        require(destinationEntries.size() == 2 && destinationEntries.stream().anyMatch(e -> e.token().equals("incoming"))
                && destinationEntries.stream().anyMatch(e -> e.token().equals("resident")), "both distinct key references remain present");
        require(containment.registeredOwnerOf(incomingBody).orElse(null) instanceof PortableWorldStorageOwner movedOwner
                && movedOwner.rootId().equals(destination.getUUID())
                && containment.registeredOwnerOf(residentBody).orElse(null) == residentOwner && residentOwner.isValid(),
                "only incoming root locator changes; destination body's exact owner is retained");

        LiveHands hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        var liveDestination = destination.getItem();
        require(BodyWorldGridTransfer.takeOwned(carrier, destination, hand, "incoming", hands.revision() - 1,
                liveDestination, liveDestination.copy(), new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0, () -> true)
                == BodyWorldGridTransfer.Result.DENIED
                && destination.getItem() == liveDestination
                && carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                && containment.registeredOwnerOf(residentBody).orElse(null) == residentOwner,
                "stale world-to-carrier request conserves both roots and resident body owner");
        require(BodyWorldGridTransfer.takeOwned(carrier, destination, hand, "incoming", hands.revision(),
                liveDestination, liveDestination.copy(), new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0, () -> true)
                == BodyWorldGridTransfer.Result.SUCCESS, "incoming same-UUID body transitions from world root to carrier");
        require(containment.registeredOwnerOf(incomingBody).orElse(null) instanceof PortableBodyCarrierOwner carrierOwner
                && carrierOwner.carrier() == carrier && containment.registeredOwnerOf(residentBody).orElse(null) == residentOwner,
                "successful cross-root transition leaves resident mouse rooted by destination bag");
        require(originalDestination != destination.getItem() && level.getEntity(incomingBody.getUUID()) == incomingBody
                && level.getEntity(residentBody.getUUID()) == residentBody, "both originals survive grid publications");
        source.discard(); destination.discard(); incomingBody.discard(); residentBody.discard(); carrier.discard();
        helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 60)
    public static void worldGridTransferPreservesDistinctDestinationMouseAndTakesIncomingKey(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var first = ModEntities.MOUSE.get().create(level);
        var second = ModEntities.MOUSE.get().create(level);
        require(first != null && second != null, "two original mice constructed");
        first.setNoAi(true); second.setNoAi(true);
        first.setPos(carrier.getX(), carrier.getY(), carrier.getZ());
        second.setPos(carrier.getX(), carrier.getY(), carrier.getZ());
        require(level.addFreshEntity(first) && level.addFreshEntity(second), "both original mice added");
        CharacterIdentitySystem.enrollSupportedActor(first, level);
        CharacterIdentitySystem.enrollSupportedActor(second, level);
        UUID firstKeyId = UUID.randomUUID(), secondKeyId = UUID.randomUUID();
        ItemStack firstKey = PortableBodyCarrying.createKey(carrier, first, firstKeyId);
        ItemStack secondKey = PortableBodyCarrying.createKey(carrier, second, secondKeyId);
        require(PortableBodyKeys.isKey(firstKey) && PortableBodyKeys.isKey(secondKey), "both authoritative references created");
        var firstContents = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(List.of(
                new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry("first-mouse", firstKey, 0, 0,
                        GridGeometry.Rotation.DEG_0)));
        var secondContents = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(List.of(
                new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry("second-mouse", secondKey, 0, 0,
                        GridGeometry.Rotation.DEG_0)));
        ItemStack sourceStack = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(
                new ItemStack(ModItems.BAG.get()), firstContents).orElseThrow();
        ItemStack destinationStack = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(
                new ItemStack(ModItems.BAG.get()), secondContents).orElseThrow();
        var source = new net.minecraft.world.entity.item.ItemEntity(level, carrier.getX() + 1, carrier.getY(), carrier.getZ(), sourceStack);
        var destination = new net.minecraft.world.entity.item.ItemEntity(level, carrier.getX() + 2, carrier.getY(), carrier.getZ(), destinationStack);
        require(level.addFreshEntity(source) && level.addFreshEntity(destination), "both world storage roots loaded");
        var containment = BodyContainmentService.forLevel(level);
        var firstOwner = new PortableWorldStorageOwner(level, source.getUUID(), "first-mouse",
                PortableBodyKeys.reference(firstKey));
        var secondOwner = new PortableWorldStorageOwner(level, destination.getUUID(), "second-mouse",
                PortableBodyKeys.reference(secondKey));
        require(containment.bind(first, firstOwner) && containment.bind(second, secondOwner), "both source roots register in sole containment ledger");

        var existingDestination = destination.getItem();
        var originalSource = source.getItem();
        var entries = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(originalSource).orElseThrow().entries();
        var incoming = entries.getFirst();
        require(BodyWorldWorldGridTransfer.transfer(carrier, source, destination, incoming.token(), incoming.x(), incoming.y(),
                incoming.rotation(), 1, 0, GridGeometry.Rotation.DEG_0, () -> true)
                == BodyWorldWorldGridTransfer.Outcome.SUCCESS, "different mouse enters already occupied-by-mouse world grid");
        var afterGrid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(destination.getItem()).orElseThrow().entries();
        require(afterGrid.size() == 2 && afterGrid.stream().anyMatch(entry -> entry.token().equals("second-mouse"))
                && afterGrid.stream().anyMatch(entry -> entry.token().equals("first-mouse")),
                "destination retains both distinct references without an arbitrary one-body limit");
        require(containment.registeredOwnerOf(first).orElse(null) instanceof PortableWorldStorageOwner moved
                && moved.rootId().equals(destination.getUUID())
                && containment.registeredOwnerOf(second).orElse(null) == secondOwner
                && secondOwner.isValid(), "incoming owner moves; pre-existing destination owner remains exact");

        LiveHands hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        var beforeRoot = destination.getItem();
        var stale = BodyWorldGridTransfer.takeOwned(carrier, destination, hand, "first-mouse", hands.revision() - 1,
                beforeRoot, beforeRoot.copy(), new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0, () -> true);
        require(stale == BodyWorldGridTransfer.Result.DENIED && destination.getItem() == beforeRoot
                && carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                && containment.registeredOwnerOf(first).orElse(null) instanceof PortableWorldStorageOwner
                && containment.registeredOwnerOf(second).orElse(null) == secondOwner,
                "stale world-to-carrier transfer conserves both exact roots and body owners");
        require(BodyWorldGridTransfer.takeOwned(carrier, destination, hand, "first-mouse", hands.revision(),
                beforeRoot, beforeRoot.copy(), new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0, () -> true)
                == BodyWorldGridTransfer.Result.SUCCESS, "same original incoming body transfers from world root to carrier");
        require(containment.registeredOwnerOf(first).orElse(null) instanceof PortableBodyCarrierOwner movedToCarrier
                && movedToCarrier.carrier() == carrier
                && containment.registeredOwnerOf(second).orElse(null) == secondOwner && secondOwner.isValid(),
                "world-to-carrier transition keeps the other body's destination-root owner intact");
        require(level.getEntity(first.getUUID()) == first && level.getEntity(second.getUUID()) == second,
                "cross-root transfers preserve both original entity UUIDs");
        source.discard(); destination.discard(); first.discard(); second.discard(); carrier.discard();
        helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 60)
    public static void trustedHandPickupAndDropPreserveTheSameLiveMouse(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse created");
        mouse.setNoAi(true);
        mouse.setInvisible(false);
        mouse.noPhysics = false;
        mouse.setNoGravity(false);
        mouse.setPos(carrier.getX(), carrier.getY(), carrier.getZ() + 1.4);
        require(helper.getLevel().addFreshEntity(mouse), "mouse spawned");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands empty = LiveHands.initialize(carrier).orElseThrow();
        String hand = empty.activeHand();
        String other = empty.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElseThrow();
        LiveHands fullHands = empty.putWhole(empty.revision(), hand, new ItemToken("occupied-one"),
                new ItemStack(net.minecraft.world.item.Items.APPLE)).state();
        fullHands = fullHands.putWhole(fullHands.revision(), other, new ItemToken("occupied-two"),
                new ItemStack(net.minecraft.world.item.Items.STICK)).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, empty, fullHands), "fixture commits full hand state through gateway");
        require(BodyHandItemTransfer.pickupOwned(carrier, mouse.getUUID(), hand, fullHands.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "full hands deny living pickup without changing source");
        LiveHands afterFull = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(afterFull == fullHands && helper.getLevel().getEntity(mouse.getUUID()) == mouse,
                "full-hand denial conserves both hands and original mouse");
        LiveHands clearedFull = fullHands.takeWhole(fullHands.revision(), other, new ItemToken("occupied-two")).state();
        clearedFull = clearedFull.takeWhole(clearedFull.revision(), hand, new ItemToken("occupied-one")).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, fullHands, clearedFull), "fixture clears full hands through gateway");
        empty = clearedFull;
        aimAt(carrier, mouse.position().add(0, mouse.getBbHeight() * .5, 0));
        LiveHands[] reentrantState = new LiveHands[1];
        boolean[] changedDuringAdmission = {false};
        require(BodyHandItemTransfer.pickupOwned(carrier, mouse.getUUID(), hand, empty.revision(), () -> {
            if (!changedDuringAdmission[0]) {
                changedDuringAdmission[0] = true;
                LiveHands current = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                reentrantState[0] = current.putWhole(current.revision(), other, new ItemToken("reentrant-item"),
                        new ItemStack(net.minecraft.world.item.Items.APPLE)).state();
                require(LiveHandsCommitGateway.commitTrusted(carrier, current, reentrantState[0]),
                        "reentrant ordinary hand update commits through gateway");
            }
            return true;
        }) == BodyHandItemTransfer.Result.DENIED, "staged pickup loses compare-and-commit after reentrant hand change");
        require(carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == reentrantState[0]
                        && BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) carrier.level())
                        .registeredOwnerOf(mouse).isEmpty(),
                "reentrant update remains intact and no mouse ownership is bound");
        LiveHands clearedRace = reentrantState[0].takeWhole(reentrantState[0].revision(), other,
                new ItemToken("reentrant-item")).state();
        require(LiveHandsCommitGateway.commitTrusted(carrier, reentrantState[0], clearedRace),
                "fixture clears raced stack through gateway");
        empty = clearedRace;
        long revision = empty.revision();

        require(BodyHandItemTransfer.pickupOwned(carrier, mouse.getUUID(), hand, revision - 1, () -> true)
                == BodyHandItemTransfer.Result.DENIED, "stale hand revision preserves live mouse");
        require(!BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) carrier.level())
                .registeredOwnerOf(mouse).isPresent(), "stale request has no containment side effect");
        Vec3Position original = new Vec3Position(mouse.getX(), mouse.getY(), mouse.getZ());
        mouse.setPos(carrier.getX() + 9, carrier.getY(), carrier.getZ());
        require(BodyHandItemTransfer.pickupOwned(carrier, mouse.getUUID(), hand, revision, () -> true)
                == BodyHandItemTransfer.Result.DENIED, "out-of-range mouse remains world-owned");
        require(original.distance(mouse.position()) > 8, "failed reach does not relocate mouse");
        mouse.setPos(carrier.getX(), carrier.getY(), carrier.getZ() + 1.4);
        aimAt(carrier, mouse.position().add(0, mouse.getBbHeight() * .5, 0));

        require(BodyHandItemTransfer.pickupOwned(carrier, mouse.getUUID(), hand, revision, () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "first-hit mouse moves into exact hand-key transaction");
        LiveHands held = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String token = held.token(hand).orElseThrow();
        ItemStack key = held.stackCopy(hand).orElseThrow();
        require(com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.isKey(key)
                && key.getCount() == 1 && key.get(ModDataComponents.PORTABLE_BODY_REFERENCE.get()).body().equals(mouse.getUUID()),
                "single hand key references original mouse UUID");
        var containment = BodyContainmentService.forLevel((net.minecraft.server.level.ServerLevel) carrier.level());
        var owner = containment.ownerOf(mouse).orElseThrow();
        require(owner instanceof PortableBodyCarrierOwner && mouse.noPhysics && mouse.isInvisible(),
                "same mouse is contained by current exact hand key");
        Vec3Position heldPosition = new Vec3Position(mouse.getX(), mouse.getY(), mouse.getZ());
        carrier.setPos(carrier.getX() + 2, carrier.getY(), carrier.getZ());
        containment.tick();
        require(!heldPosition.equals(mouse.position()), "containment tick repins body after carrier moves");
        require(mouse.position().distanceTo(carrier.position()) < 1, "repinned body remains at carrier hand position");
        LiveHands movedCarrier = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var level = helper.getLevel();
        var oldBlocks = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        BlockPos origin = carrier.blockPosition();
        for (int x = origin.getX() - 2; x <= origin.getX() + 2; x++)
            for (int y = origin.getY(); y <= origin.getY() + 2; y++)
                for (int z = origin.getZ() - 2; z <= origin.getZ() + 2; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    oldBlocks.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                }
        require(BodyHandItemTransfer.dropOwned(carrier, hand, token, movedCarrier.revision(), () -> true)
                == BodyHandItemTransfer.Result.DENIED, "fully obstructed drop leaves key and live body contained");
        require(carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == movedCarrier
                        && containment.ownerOf(mouse).orElse(null) == owner,
                "invalid drop placement preserves attachment and sole contained owner");
        oldBlocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
        containment.tick();
        movedCarrier = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyHandItemTransfer.dropOwned(carrier, hand, token, movedCarrier.revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "key drop releases original entity at collision-valid point");
        require(helper.getLevel().getEntity(mouse.getUUID()) == mouse && !mouse.isRemoved(),
                "drop preserves exact live mouse instead of creating/deleting an entity");
        require(containment.registeredOwnerOf(mouse).isEmpty() && !mouse.noPhysics && !mouse.isInvisible()
                && !mouse.isNoGravity(), "drop unbind restores original physical flags");
        LiveHands dropped = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(dropped.stackCopy(hand).isEmpty(), "hand key cleared by committed drop");
        helper.succeed();
    }

    private static void aimAt(net.minecraft.world.entity.LivingEntity body, net.minecraft.world.phys.Vec3 point) {
        var delta = point.subtract(body.getEyePosition());
        body.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        body.setXRot((float) Math.toDegrees(Math.atan2(-delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
    }

    private static String escapeDiagnostic(net.minecraft.world.entity.LivingEntity body, GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) body.level();
        var containment = BodyContainmentService.forLevel(level);
        var owner = containment.registeredOwnerOf(body).orElse(null);
        var snapshot = PortableBodyReleaseTransaction.capture(body);
        net.minecraft.world.entity.Entity root = owner instanceof PortableBodyCarrierOwner carrier ? carrier.carrier()
                : owner instanceof PortableWorldStorageOwner world ? world.root() : null;
        var placementActor = owner instanceof PortableBodyCarrierOwner carrier ? carrier.carrier() : body;
        var placement = BodyItemTargetProbe.resolveDrop(level, placementActor, root, body.getBbWidth(), body.getBbHeight(), body);
        long worldTick = level.getServer().overworld().getGameTime();
        var operations = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        long due = operations == null ? -1 : operations.operations().stream()
                .filter(op -> op.kind().equals(PortableBodyEscapeService.OPERATION_KIND))
                .findFirst().map(op -> op.startTick() + op.durationTicks()).orElse(-1L);
        long revision = root instanceof net.minecraft.world.entity.LivingEntity carrier
                ? carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == null ? -1
                : carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).revision() : -1;
        return "serverTick=" + worldTick + ", body=" + body.position() + ", root=" + root
                + ", ownerValid=" + (owner != null && owner.isValid()) + ", snapshotCurrent="
                + (snapshot != null && PortableBodyReleaseTransaction.isCurrent(snapshot)) + ", rootRevision=" + revision
                + ", placement=" + placement + ", accessible=" + (root != null
                && BodyItemTargetProbe.withinInteractionReach(body, root.getBoundingBox()))
                + ", due=" + due + ", operations=" + operations;
    }

    private static void traceEscape(GameTestHelper helper, net.minecraft.world.entity.LivingEntity body,
                                    com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner owner,
                                    net.minecraft.world.phys.Vec3 start, java.util.List<String> trace, int elapsed) {
        if (elapsed >= 6) return;
        helper.runAfterDelay(1, () -> {
            var operation = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
            trace.add("t=" + helper.getLevel().getServer().overworld().getGameTime() + ",p=" + body.position()
                    + ",d=" + body.position().distanceTo(start) + ",root=" + owner.position()
                    + ",rootDistance=" + body.position().distanceTo(owner.position())
                    + ",ownerValid=" + owner.isValid() + ",registered="
                    + (com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                    .forLevel((net.minecraft.server.level.ServerLevel) body.level()).registeredOwnerOf(body).orElse(null) == owner)
                    + ",bodyAdded=" + body.isAddedToLevel() + ",bodyRemoved=" + body.isRemoved()
                    + ",bodyAlive=" + body.isAlive() + ",flags=" + body.isInvisible() + "/" + body.noPhysics
                    + "/" + body.isNoGravity() + ",velocity=" + body.getDeltaMovement() + ",op=" + operation);
            traceEscape(helper, body, owner, start, trace, elapsed + 1);
        });
    }

    private static void awaitEscapeDeadline(GameTestHelper helper, net.minecraft.world.entity.LivingEntity body, Runnable complete) {
        awaitEscapeDeadline(helper, body, activeEscape(body), complete);
    }

    private static void awaitEscapeDeadline(GameTestHelper helper, net.minecraft.world.entity.LivingEntity body,
            com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation expected, Runnable complete) {
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 700,
                () -> "exact escape operation did not reach terminal phase after due tick="
                        + expected.dueTick() + ", id=" + expected.id(),
                () -> {
                    var current = operation(body, expected.id());
                    return current != null
                            && current.phase() != com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE;
                }, complete);
    }

    private static com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation activeEscape(
            net.minecraft.world.entity.LivingEntity body) {
        return body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                .filter(op -> op.kind().equals(PortableBodyEscapeService.OPERATION_KIND)
                        && op.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE)
                .findFirst().orElseThrow();
    }

    private static com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation operation(
            net.minecraft.world.entity.LivingEntity body, long id) {
        var state = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        return state == null ? null : state.operations().stream().filter(op -> op.id() == id).findFirst().orElse(null);
    }

    private record Vec3Position(double x, double y, double z) {
        boolean equals(net.minecraft.world.phys.Vec3 point) {
            return x == point.x && y == point.y && z == point.z;
        }
        double distance(net.minecraft.world.phys.Vec3 point) {
            return Math.sqrt((x - point.x) * (x - point.x) + (y - point.y) * (y - point.y) + (z - point.z) * (z - point.z));
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
