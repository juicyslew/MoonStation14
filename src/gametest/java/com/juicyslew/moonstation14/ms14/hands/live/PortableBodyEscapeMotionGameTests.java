package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Regression for parent-relative escape validity and hand-selection retention. */
@GameTestTags("living-carry")
@PrefixGameTestTemplate(false)
public final class PortableBodyEscapeMotionGameTests {
    private PortableBodyEscapeMotionGameTests() { }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 750)
    @GameTestTags({"living-carry", "portable-escape", "portable-escape-motion"})
    public static void movingCarrierAndSelectingHandDoNotRestartEscape(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        helper.getLevel().setBlock(carrier.blockPosition().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed");
        mouse.setNoAi(true);
        mouse.setPos(carrier.position());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        PortableBodyGameTestAccess.holdPortableKey(carrier, hand, PortableBodyKeys.reference(key).token().toString(), key);
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                .forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel());
        var owner = (PortableBodyCarrierOwner) containment.ownerOf(mouse).orElseThrow();
        require(owner.tryEscape(mouse), "escape operation starts through canonical custody owner");
        var started = activeEscape(mouse);
        require(started != null, "tryEscape synchronously installs ACTIVE operation; state=" + operationReport(mouse));
        helper.runAfterDelay(20, () -> {
            // Translate the parent incrementally so the body-pinning owner can update its
            // child before each timed-interaction sample; a teleport creates a transient
            // parent-relative displacement that is correctly interruptible.
            LiveHands current = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            String other = current.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElseThrow();
            LiveHands selected = current.selectActive(current.revision(), other).orElseThrow();
            require(LiveHandsCommitGateway.commitTrusted(carrier, current, selected), "normal active-hand selection commits");
            moveCarrierIncrementally(helper, carrier, 8, () -> helper.runAfterDelay(75, () -> {
                var terminal = findEscape(mouse);
                require(terminal != null && terminal.id() == started.id(),
                        "carrier movement/active-hand selection retain original operation id=" + started.id()
                                + "; state=" + operationReport(mouse));
                require(terminal.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.COMPLETED,
                        "same escape operation completes after carrier movement/hand selection; operation=" + terminal
                                + "; tick=" + helper.getLevel().getGameTime() + "; body=" + mouse.position()
                                + "; root=" + carrier.position() + "; hands=" + handReport(carrier));
                require(helper.getLevel().getEntity(mouse.getUUID()) == mouse && !mouse.isInvisible() && !mouse.noPhysics,
                        "completion releases original controlled body");
                carrier.discard(); mouse.discard(); helper.succeed();
            }));
        });
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"living-carry", "portable-escape", "portable-escape-motion"})
    public static void bodyRelativeDisplacementCancelsEscape(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed");
        mouse.setNoAi(true); mouse.setPos(carrier.position());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands hands = LiveHands.initialize(carrier).orElseThrow();
        PortableBodyGameTestAccess.holdPortableKey(carrier, hands.activeHand(), "escape-relative-key",
                PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID()));
        var owner = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                .forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel()).ownerOf(mouse).orElseThrow();
        require(owner.tryEscape(mouse), "escape operation starts");
        helper.runAfterDelay(2, () -> {
            mouse.setPos(mouse.getX() + .4, mouse.getY(), mouse.getZ());
            helper.runAfterDelay(2, () -> {
                var operation = findEscape(mouse);
                require(operation != null && operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.CANCELLED,
                        "actual body-to-parent displacement over .3 cancels escape");
                carrier.discard(); mouse.discard(); helper.succeed();
            });
        });
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"living-carry", "portable-escape", "portable-escape-motion"})
    public static void physicalKeyDropCancelsEscape(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        helper.getLevel().setBlock(carrier.blockPosition().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed"); mouse.setNoAi(true); mouse.setPos(carrier.position());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        String canonicalToken = PortableBodyKeys.reference(key).token().toString();
        PortableBodyGameTestAccess.holdPortableKey(carrier, hand, canonicalToken, key);
        var owner = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                .forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel()).ownerOf(mouse).orElseThrow();
        require(owner.tryEscape(mouse), "escape operation starts");
        helper.runAfterDelay(2, () -> {
            LiveHands current = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            String token = current.token(hand).orElseThrow();
            require(token.equals(PortableBodyKeys.reference(key).token().toString()),
                    "DROP fixture key token equals canonical portable reference token; handToken=" + token
                            + "; referenceToken=" + PortableBodyKeys.reference(key).token());
            var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
            var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
            var exactOwner = containment.ownerOf(mouse).orElse(null);
            require(carrier.isAddedToLevel() && !carrier.isRemoved() && level.getEntity(carrier.getUUID()) == carrier,
                    "DROP preflight carrier loaded; tick=" + level.getGameTime());
            require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(carrier),
                    "DROP preflight carrier can act; tick=" + level.getGameTime());
            require(current.compatible(carrier) && current.revision() >= 0 && current.token(hand).filter(token::equals).isPresent(),
                    "DROP preflight canonical hand/token/revision; hand=" + hand + "; token=" + token
                            + "; state=" + handReport(carrier));
            require(PortableBodyCarrying.validateProspective(carrier, current),
                    "DROP preflight portable-key prospective ledger valid; " + handReport(carrier));
            require(exactOwner instanceof PortableBodyCarrierOwner portable && portable.carrier() == carrier
                            && portable.isValid() && portable.handToken().equals(token),
                    "DROP preflight exact owner valid; owner=" + exactOwner + "; token=" + token);
            var placement = BodyItemTargetProbe.resolveDrop(level, carrier, carrier, mouse.getBbWidth(), mouse.getBbHeight(), mouse);
            require(placement != null, "DROP preflight loaded placement candidate exists; carrier=" + carrier.position()
                    + "; look=" + carrier.getLookAngle() + "; body=" + mouse.position());
            require(BodyHandItemTransfer.portableBodyDropClear(level, carrier, mouse, placement.position(),
                            mouse.getBbWidth(), mouse.getBbHeight()),
                    "DROP preflight exact production clearance predicate; candidate=" + placement.position()
                            + "; carrier=" + carrier.position() + "; body=" + mouse.position());
            var releaseSnapshot = PortableBodyReleaseTransaction.capture(mouse);
            require(releaseSnapshot != null && releaseSnapshot.rootOwner() == exactOwner
                            && releaseSnapshot.rootRevision() == current.revision(),
                    "DROP preflight release snapshot current; snapshot=" + releaseSnapshot
                            + "; handRevision=" + current.revision());
            BodyHandItemTransfer.Result result = BodyHandItemTransfer.dropOwned(carrier, hand, token, current.revision(), () -> true);
            require(result == BodyHandItemTransfer.Result.SUCCESS,
                    "normal physical drop moves exact key to world custody; result=" + result
                            + "; tick=" + helper.getLevel().getGameTime() + "; hand=" + hand + "; token=" + token
                            + "; revision=" + current.revision() + "; state=" + handReport(carrier)
                            + "; body=" + mouse.getUUID() + "/" + mouse.position() + "/noPhysics=" + mouse.noPhysics
                            + "; carrier=" + carrier.getUUID() + "/" + carrier.position()
                            + "; releaseDiagnosticMayBeStale=" + PortableBodyReleaseTransaction.lastDiagnostic());
            helper.runAfterDelay(2, () -> {
                var operation = findEscape(mouse);
                require(operation != null && operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.CANCELLED,
                        "physical key drop invalidates captured custody and cancels escape");
                carrier.discard(); mouse.discard(); helper.succeed();
            });
        });
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 130)
    @GameTestTags({"living-carry", "portable-escape", "portable-escape-handoff"})
    public static void canonicalHandTransferRetainsEscapeOperation(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        helper.getLevel().setBlock(carrier.blockPosition().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed"); mouse.setNoAi(true); mouse.setPos(carrier.position());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands hands = LiveHands.initialize(carrier).orElseThrow();
        String source = hands.handIds().get(0), destination = hands.handIds().get(1);
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        String token = PortableBodyKeys.reference(key).token().toString();
        PortableBodyGameTestAccess.holdPortableKey(carrier, source, token, key);
        var owner = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                .forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel()).ownerOf(mouse).orElseThrow();
        require(owner.tryEscape(mouse), "canonical owner starts AI escape");
        var started = activeEscape(mouse);
        require(started != null, "ACTIVE escape installed");
        helper.runAfterDelay(2, () -> {
            LiveHands current = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyHandTransfer.transferOwned(carrier, source, destination, token, current.revision() + 1, () -> true)
                    == BodyHandTransfer.Result.DENIED, "stale-revision transfer denied");
            require(PortableBodyReleaseTransaction.isCurrent(PortableBodyReleaseTransaction.capture(mouse)),
                    "denied transfer leaves custody snapshot current");
            require(activeEscape(mouse).id() == started.id() && activeEscape(mouse).startTick() == started.startTick(),
                    "denied transfer retains same ACTIVE escape");
            var sourceOwner = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                    .forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel()).ownerOf(mouse).orElseThrow();
            var sourceCustody = ((PortableBodyCarrierOwner) sourceOwner).activeEscapeCustody();
            var oldSnapshot = PortableBodyReleaseTransaction.capture(mouse);
            require(oldSnapshot != null && oldSnapshot.rootOwner() == sourceOwner
                            && PortableBodyReleaseTransaction.isCurrent(oldSnapshot),
                    "preflight captured old-owner snapshot is current before transfer");
            require(BodyHandTransfer.transferOwned(carrier, source, destination, token, current.revision(), () -> true)
                    == BodyHandTransfer.Result.SUCCESS, "production whole-hand transfer succeeds");
            require(!PortableBodyReleaseTransaction.isCurrent(oldSnapshot),
                    "old-owner snapshot is stale after committed transfer");
            var transferredOwner = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                    .forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel()).ownerOf(mouse).orElseThrow();
            require(transferredOwner instanceof PortableBodyCarrierOwner carried
                            && carried.activeEscapeCustody() == sourceCustody
                            && carried.activeEscapeOperationId() == started.id(),
                    "immediate transfer retains exact custody handle and original operation id");
            var refreshed = PortableBodyReleaseTransaction.capture(mouse);
            require(refreshed != null && refreshed.rootOwner() == com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                    .forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel()).ownerOf(mouse).orElse(null),
                    "fresh strict snapshot belongs to post-transfer owner");
            require(PortableBodyReleaseTransaction.isCurrent(refreshed), "fresh post-transfer snapshot current");
            require(sourceCustody.isCurrent(), "retained custody has fresh current post-transfer snapshot immediately");
            observeOriginalEscapeDeadline(helper, carrier, mouse, started.id(), started.startTick());
        });
    }

    private static void observeOriginalEscapeDeadline(GameTestHelper helper, Villager carrier,
                                                       net.minecraft.world.entity.LivingEntity mouse,
                                                       long operationId, long startTick) {
        var operation = findEscape(mouse);
        long elapsed = helper.getLevel().getGameTime() - startTick;
        require(operation != null && operation.id() == operationId && operation.startTick() == startTick,
                "original escape id/start tick retained at elapsed=" + elapsed + "; operation=" + operation);
        if (elapsed < 101) {
            require(operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE,
                    "original five-second operation remains active before post-deadline observation; elapsed="
                            + elapsed + "; phase=" + operation.phase());
            helper.runAfterDelay(1, () -> observeOriginalEscapeDeadline(helper, carrier, mouse, operationId, startTick));
            return;
        }
        require(operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.COMPLETED,
                "original five-second operation completes by elapsed=101; phase=" + operation.phase()
                        + "; tick=" + helper.getLevel().getGameTime() + "; duration=" + (helper.getLevel().getGameTime() - startTick));
        require(helper.getLevel().getEntity(mouse.getUUID()) == mouse && !mouse.noPhysics,
                "same original UUID body released");
        carrier.discard(); mouse.discard(); helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"living-carry", "portable-escape", "portable-escape-motion"})
    public static void worldDropThenRepickupDoesNotRefreshEscapeCustody(GameTestHelper helper) {
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        carrier.setNoGravity(true);
        helper.getLevel().setBlock(carrier.blockPosition().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed"); mouse.setNoAi(true); mouse.setPos(carrier.position());
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        LiveHands hands = LiveHands.initialize(carrier).orElseThrow();
        String hand = hands.activeHand();
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        String token = PortableBodyKeys.reference(key).token().toString();
        PortableBodyGameTestAccess.holdPortableKey(carrier, hand, token, key);
        var owner = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                .forLevel((net.minecraft.server.level.ServerLevel) helper.getLevel()).ownerOf(mouse).orElseThrow();
        require(owner.tryEscape(mouse), "AI escape starts through canonical custody owner");
        var started = activeEscape(mouse);
        require(started != null, "active escape captured");
        helper.runAfterDelay(2, () -> {
            LiveHands held = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyHandItemTransfer.dropOwned(carrier, hand, token, held.revision(), () -> true)
                    == BodyHandItemTransfer.Result.SUCCESS, "production world drop releases the original living mouse");
            var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
            var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
            require(level.getEntity(mouse.getUUID()) == mouse && containment.ownerOf(mouse).isEmpty(),
                    "world drop releases containment while preserving original loaded mouse UUID");
            var immediatelyAfterDrop = mouse.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()).operations()
                    .stream().filter(op -> op.id() == started.id()).findFirst().orElse(null);
            require(immediatelyAfterDrop != null && immediatelyAfterDrop.phase()
                            == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.CANCELLED,
                    "original escape is cancelled at committed drop before same-tick repickup");
            LiveHands empty = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyHandItemTransfer.pickupSelectedOwned(carrier, mouse.getUUID(), hand,
                    empty.revision(), () -> true) == BodyHandItemTransfer.Result.SUCCESS,
                    "production selected living pickup reclaims the original mouse by UUID");
            require(level.getEntity(mouse.getUUID()) == mouse && containment.ownerOf(mouse).isPresent(),
                    "living pickup reuses original entity and restores canonical custody");
            var operations = mouse.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()).operations();
            var original = operations.stream().filter(op -> op.id() == started.id()).findFirst().orElse(null);
            require(original != null && original.startTick() == started.startTick()
                            && original.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.CANCELLED,
                    "original operation cancels across genuine drop/repickup; original=" + original + "; operations=" + operations);
            carrier.discard(); mouse.discard(); helper.succeed();
        });
    }

    private static com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation activeEscape(net.minecraft.world.entity.LivingEntity body) {
        return body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                .filter(op -> op.kind().equals(PortableBodyEscapeService.OPERATION_KIND)
                        && op.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE)
                .findFirst().orElse(null);
    }
    private static com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation findEscape(net.minecraft.world.entity.LivingEntity body) {
        return body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                .filter(op -> op.kind().equals(PortableBodyEscapeService.OPERATION_KIND)).findFirst().orElse(null);
    }
    private static String operationReport(net.minecraft.world.entity.LivingEntity body) {
        var state = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        return state == null ? "absent" : state.operations().toString();
    }
    private static String handReport(net.minecraft.world.entity.LivingEntity body) {
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return hands == null ? "absent" : "active=" + hands.activeHand() + ",revision=" + hands.revision()
                + ",handIds=" + hands.handIds().stream().map(id -> id + "=" + hands.token(id).orElse("empty")).toList();
    }
    private static void moveCarrierIncrementally(GameTestHelper helper, Villager carrier, int remaining, Runnable complete) {
        if (remaining == 0) { complete.run(); return; }
        helper.runAfterDelay(1, () -> {
            carrier.setPos(carrier.getX() + .1, carrier.getY(), carrier.getZ());
            moveCarrierIncrementally(helper, carrier, remaining - 1, complete);
        });
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
