package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.live.AtmosLayerHandsFixture;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Registered-player horizontal NORTH-face body block-use regression for visible-lane cable cutting. */
@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "network", "cable-cut-visible"})
public final class BodyCableCutAuthenticatedGameTests {
    private BodyCableCutAuthenticatedGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void registeredBodyCutsOnlyVisibleLaneAndPropagatesAdmittedHit(GameTestHelper h) {
        var fixture = RegisteredServerPlayerFixture.register(h.getLevel(), "body-cable-cut-visible-v1");
        ServerLevel level = h.getLevel();
        BlockPos host = h.absolutePos(new BlockPos(8, 2, 8));
        var ownedDrops = new ArrayList<ItemEntity>();
        var preexistingDrops = new java.util.HashSet<java.util.UUID>();
        level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(host).inflate(2), entity -> true)
                .forEach(entity -> preexistingDrops.add(entity.getUUID()));
        AtomicBoolean closed = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (!closed.compareAndSet(false, true)) return;
            level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(host).inflate(2), entity -> true)
                    .stream().filter(entity -> !preexistingDrops.contains(entity.getUUID())
                            && isCableSpool(entity.getItem())).forEach(ItemEntity::discard);
            ownedDrops.forEach(ItemEntity::discard);
            for (CableTier tier : CableTier.values()) CableStorage.remove(level, host, Direction.NORTH, tier);
            level.setBlock(host, Blocks.AIR.defaultBlockState(), 3);
            fixture.close();
        };
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a, net.minecraft.gametest.framework.GameTestInfo b, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
        });
        // Match the canonical registered-player fixtures: allow GameTestServer's lifecycle
        // startup owner to initialize before the PlayerList login emits CHARACTER Begin.
        h.runAtTickTime(2, () -> {
        try {
            var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
            for (CableTier tier : CableTier.values())
                require(CableStorage.place(level, host, Direction.NORTH, tier), "seed shared-face cable tier " + tier);
            var body = owner.body();
            var handSnapshot = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
            String hand = handSnapshot.activeHand();
            ItemStack cutter = new ItemStack(ModItems.WIRECUTTER.get());
            require(AtmosLayerHandsFixture.set(body, cutter), "actual live-hand owner accepts cutters");
            var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 2));
            var held = query.hands().stream().filter(x -> x.id().equals(hand)).findFirst().orElseThrow();
            Vec3 intended = new Vec3(host.getX() + .34, host.getY() + .34, host.getZ());
            var hit = aimExact(body, level, intended, host);
            require(hit.getDirection() == Direction.NORTH && hit.getLocation().distanceToSqr(intended) < 1e-5,
                    "server body ray hits the known non-center HV lane, not block center: " + hit);

            var stale = new BodyBlockUseRequest(owner.epoch(), 1, query.revision() + 1, hand,
                    held.token(), host, Direction.NORTH, false, BodyBlockUseRequest.Intent.USE);
            require(AuthenticatedBlockUseDriver.use(fixture.player(), stale) == BodyBlockUseResult.Outcome.DENIED,
                    "stale hand revision denied through authenticated request route");
            require(allTiers(level, host), "stale request retains all three tiers");
            require(noSpools(level, host), "stale request creates no spool/refund");

            var validQuery = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 3));
            var validHand = validQuery.hands().stream().filter(x -> x.id().equals(hand)).findFirst().orElseThrow();
            require(fixture.player().isSpectator(), "registered CHARACTER carrier remains vanilla spectator");
            require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body),
                    "actual controlled character body, not spectator carrier, is action eligible");
            require(fixture.player().distanceToSqr(host.getX() + .5, host.getY() + .5, host.getZ() + .5) > 64,
                    "registered carrier is outside the removed actor-centered eight-block check");
            hit = aimExact(body, level, intended, host);
            require(hit.getDirection() == Direction.NORTH && hit.getLocation().distanceToSqr(intended) < 1e-5,
                    "fresh request body ray still hits exact non-center lane: " + hit);
            var valid = new BodyBlockUseRequest(owner.epoch(), 2, validQuery.revision(), hand,
                    validHand.token(), host, Direction.NORTH, false, BodyBlockUseRequest.Intent.USE);
            BodyBlockUseResult.Outcome outcome = AuthenticatedBlockUseDriver.use(fixture.player(), valid);
            // Capture any output immediately so a later assertion failure still cleans up only
            // fixture-created cable-spool entities, never preexisting world items.
            level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(host).inflate(2), entity ->
                    entity.isAlive() && isCableSpool(entity.getItem()) && !preexistingDrops.contains(entity.getUUID()))
                    .forEach(ownedDrops::add);
            require(outcome == BodyBlockUseResult.Outcome.PLACED,
                    "registered authenticated body block-use cuts the visible lane; outcome=" + outcome);
            require(!CableStorage.has(level, host, Direction.NORTH, CableTier.HV)
                            && CableStorage.has(level, host, Direction.NORTH, CableTier.MV)
                            && CableStorage.has(level, host, Direction.NORTH, CableTier.APC),
                    "known NORTH-face HV lane alone removed; MV/APC records preserved");
            ItemStack heldAfter = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body)
                    .orElseThrow().stackCopy(hand).orElseThrow();
            require(heldAfter.getItem() == ModItems.WIRECUTTER.get() && heldAfter.getCount() == 1,
                    "cut does not consume the real held cutter");
            List<ItemEntity> drops = matchingDrops(level, host, ModItems.HV_CABLE_SPOOL.get());
            ownedDrops.addAll(drops);
            require(drops.size() == 1, "exactly one fresh matching HV spool spawned: " + drops.size());
            ItemEntity drop = drops.getFirst();
            require(drop.getItem().getCount() == 1 && drop.getZ() < host.getZ(),
                    "fresh spool is a single entity outside the clicked NORTH face");
            require(matchingDrops(level, host, ModItems.MV_CABLE_SPOOL.get()).isEmpty()
                            && matchingDrops(level, host, ModItems.APC_CABLE_SPOOL.get()).isEmpty(),
                    "no unrelated tier refunded");
            h.succeed();
        } catch (Throwable failure) {
            cleanup.run();
            h.fail("Authenticated visible-lane cable cut: " + failure);
        }
        });
    }

    private static net.minecraft.world.phys.BlockHitResult aimExact(net.minecraft.world.entity.LivingEntity body,
            ServerLevel level, Vec3 point, BlockPos host) {
        body.setPos(point.x, point.y - body.getEyeHeight(), point.z - 2);
        Vec3 delta = point.subtract(body.getEyePosition());
        require(body.getEyePosition().distanceTo(point) <= BodyBlockUseTargetProbe.MAX_REACH,
                "target lane is inside actual body request reach");
        Vec3 direction = delta.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.asin(direction.y));
        body.setYRot(yaw);
        body.setYBodyRot(yaw);
        body.setYHeadRot(yaw);
        body.setXRot(pitch);
        Vec3 eye = body.getEyePosition(), look = body.getLookAngle();
        require(Double.isFinite(eye.x) && Double.isFinite(eye.y) && Double.isFinite(eye.z)
                        && eye.distanceTo(point) <= BodyBlockUseTargetProbe.MAX_REACH
                        && look.z > .999 && Math.abs(look.x) < .001 && Math.abs(look.y) < .001,
                "configured controlled-body eye/look aims horizontally toward NORTH lane: eye=" + eye + " look=" + look);
        var hit = level.clip(new net.minecraft.world.level.ClipContext(body.getEyePosition(),
                body.getEyePosition().add(body.getLookAngle().scale(5)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, body));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(host) || hit.getDirection() != Direction.NORTH)
            throw new GameTestAssertException("exact ray missed NORTH host face: type="
                + hit.getType() + " pos=" + hit.getBlockPos() + " face=" + hit.getDirection() + " location="
                + hit.getLocation() + " body=" + body.position() + " eye=" + body.getEyePosition() + " look="
                + body.getLookAngle() + " intended=" + point + " yaw=" + body.getYRot() + " headYaw="
                + body.getYHeadRot() + " pitch=" + body.getXRot());
        return hit;
    }

    private static boolean allTiers(ServerLevel level, BlockPos host) {
        for (CableTier tier : CableTier.values()) if (!CableStorage.has(level, host, Direction.NORTH, tier)) return false;
        return true;
    }

    private static boolean noSpools(ServerLevel level, BlockPos host) {
        return matchingDrops(level, host, ModItems.HV_CABLE_SPOOL.get()).isEmpty()
                && matchingDrops(level, host, ModItems.MV_CABLE_SPOOL.get()).isEmpty()
                && matchingDrops(level, host, ModItems.APC_CABLE_SPOOL.get()).isEmpty();
    }

    private static List<ItemEntity> matchingDrops(ServerLevel level, BlockPos host, net.minecraft.world.item.Item item) {
        return level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(host).inflate(2), entity -> entity.isAlive() && entity.getItem().is(item));
    }

    private static boolean isCableSpool(ItemStack stack) {
        return stack.is(ModItems.HV_CABLE_SPOOL.get()) || stack.is(ModItems.MV_CABLE_SPOOL.get())
                || stack.is(ModItems.APC_CABLE_SPOOL.get());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
