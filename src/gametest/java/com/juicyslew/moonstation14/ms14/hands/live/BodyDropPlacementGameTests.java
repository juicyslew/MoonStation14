package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

public final class BodyDropPlacementGameTests {
    private BodyDropPlacementGameTests() { }

    public static void bodyDropSpawnsForwardWithNaturalGravity(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("forward-drop"), new ItemStack(Items.STICK));
        require(stored.succeeded(), "fixture item stored");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        var expected = body.position().add(0, 1.125, 0).add(body.getLookAngle().scale(1.25));
        require(BodyHandItemTransfer.dropOwned(body, "left", "forward-drop", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "drop succeeds");
        var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(3),
                item -> item.getItem().is(Items.STICK));
        require(items.size() == 1, "single world item owns stack");
        ItemEntity item = items.getFirst();
        require(item.position().distanceToSqr(expected) < .07, "item starts at short hand-height aim projection");
        require(item.getDeltaMovement().dot(body.getLookAngle()) > .1,
                "air drop has deterministic forward motion and retains natural gravity");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("left").isEmpty(),
                "body hand no longer owns dropped stack");
        helper.succeed();
    }

    @GameTestTags("hands-release-kinematics")
    public static void handDropInheritsBodyWorldVelocity(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        body.setDeltaMovement(.08, .03, -.04);
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("release-motion"), new ItemStack(Items.STICK));
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        var placement = BodyItemTargetProbe.resolveDrop((ServerLevel) helper.getLevel(), body);
        require(placement != null && !placement.surfacePlacement(), "AIR release resolves a nonsurface placement");
        Vec3 bodyOwnAtRelease = body.getDeltaMovement();
        Vec3 expectedMotion = bodyOwnAtRelease.add(placement.velocity());
        require(BodyHandItemTransfer.dropOwned(body, "left", "release-motion", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "release commits");
        var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(3),
                item -> item.getItem().is(Items.STICK));
        require(items.size() == 1 && items.getFirst().getDeltaMovement().distanceTo(expectedMotion) < 1e-8,
                "AIR drop preserves release-time body velocity plus plan intent exactly once: expected=" + expectedMotion
                        + " actual=" + (items.isEmpty() ? null : items.getFirst().getDeltaMovement()));
        helper.succeed();
    }

    @GameTestTags({"medical-corrections-regression"})
    public static void containedRefusalDropsAtOwnerPosition(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack held = new ItemStack(Items.DIAMOND, 3);
        LiveHands stored = initial.putWhole(initial.revision(), "left", new ItemToken("contained-refusal"), held).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored);
        UUID ownerId = UUID.randomUUID();
        Vec3 ownerPosition = body.position().add(0, 0, 2);
        ContainedBodyOwner refusingOwner = new ContainedBodyOwner() {
            @Override public UUID identity() { return ownerId; }
            @Override public ServerLevel level() { return helper.getLevel(); }
            @Override public Vec3 position() { return ownerPosition; }
            @Override public Stage stage() { return Stage.UNIT; }
            @Override public boolean isValid() { return true; }
            @Override public boolean tryEscape(net.minecraft.world.entity.LivingEntity ignored) { return false; }
        };
        require(com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                .forLevel(helper.getLevel()).bind(body, refusingOwner), "fixture owner binds");
        var level = helper.getLevel();
        var query = body.getBoundingBox().inflate(5);
        var before = level.getEntitiesOfClass(ItemEntity.class, query, item -> item.getItem().is(Items.DIAMOND))
                .stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        var result = BodyHandItemTransfer.dropOwned(body, "left", "contained-refusal", stored.revision(), () -> true);
        var created = level.getEntitiesOfClass(ItemEntity.class, query, item -> item.getItem().is(Items.DIAMOND)
                && !before.contains(item.getUUID()));
        require(result == BodyHandItemTransfer.Result.SUCCESS,
                "semantic refusal uses contained-owner fallback: result=" + result + ", actor="
                        + body.position() + ", owner=" + ownerPosition + ", hand="
                        + body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).stackCopy("left"));
        require(created.size() == 1 && created.getFirst().getItem().getCount() == 3,
                "operation creates exactly one whole-stack world owner: created=" + created.size()
                        + ", entities=" + created.stream().map(item -> item.getUUID() + ":"
                        + item.getItem().getCount() + "@" + item.position()).toList());
        require(created.getFirst().position().distanceToSqr(ownerPosition) < .001,
                "new fallback entity uses owner transform, not camera/aim ray: actor=" + body.position()
                        + ", owner=" + ownerPosition + ", created=" + created.getFirst().position());
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("left").isEmpty(),
                "body hand is cleared after successful fallback: hand="
                        + body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("left")
                        + " originalCount=" + held.getCount() + " owner=" + ownerId);
        helper.succeed();
    }

    public static void containedHandDropMovesWholeStackIntoTransitHolder(GameTestHelper helper) {
        var level = (ServerLevel) helper.getLevel();
        BlockPos trunk = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(trunk, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
        require(com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData.forMutation(level).add(level,
                new com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord(UUID.randomUUID(), trunk,
                        "moonstation14:disposal_trunk", net.minecraft.core.Direction.NORTH, true, "", "", 0),
                pos -> level.getBlockState(pos).is(com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_TUBE.get())),
                "live trunk record installed");
        var holder = com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder.spawnFlushed(level,
                trunk, new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(java.util.Map.of(), 293.15)).orElseThrow();
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 3));
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack held = new ItemStack(Items.DIAMOND, 4);
        LiveHands.WholeResult stored = initial.putWhole(initial.revision(), "left", new ItemToken("transit-drop"), held);
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        UUID sourceId = UUID.randomUUID();
        ContainedBodyOwner source = new ContainedBodyOwner() {
            @Override public UUID identity() { return sourceId; }
            @Override public ServerLevel level() { return level; }
            @Override public Vec3 position() { return body.position(); }
            @Override public Stage stage() { return Stage.UNIT; }
            @Override public boolean isValid() { return true; }
            @Override public boolean tryEscape(net.minecraft.world.entity.LivingEntity ignored) { return false; }
        };
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        require(containment.bind(body, source) && holder.addBody(level, body, source), "body transfers into live holder");
        int beforeItems = level.getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.DIAMOND)).size();
        require(BodyHandItemTransfer.dropOwned(body, "left", "transit-drop", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "real body transfer kernel commits drop");
        require(holder.contents().snapshot().size() == 1
                && holder.contents().snapshot().values().iterator().next().stack().getCount() == 4,
                "transit holder owns exactly the complete stack");
        require(containment.ownerOf(body).orElse(null) == holder.containmentOwner()
                && level.getEntity(body.getUUID()) == body, "same live body remains contained by holder");
        var resolvedHolder = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals()
                .get(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.HOLDER);
        var holderView = holder.containmentOwner().view();
        require(resolvedHolder != null && holderView.ownerIdentity().equals(holder.containmentOwner().identity())
                        && holderView.worldTarget().isEmpty()
                        && holderView.cameraCollision() == resolvedHolder.containedView().cameraCollision()
                        && !holderView.cameraCollision(),
                "live holder view publishes its identity, no target, and resolved collision override");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("left").isEmpty(),
                "source hand cleared");
        require(level.getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.DIAMOND)).size() == beforeItems, "no world item was spawned");
        helper.succeed();
    }

    public static void upwardSurfaceDropHasNoIntentionalVelocity(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        body.setYRot(-90);
        body.setXRot(65);
        helper.getLevel().setBlock(body.blockPosition().offset(1, -1, 0), Blocks.STONE.defaultBlockState(), 3);
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("ground-drop"), new ItemStack(Items.STICK));
        require(stored.succeeded(), "fixture item stored");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        require(BodyHandItemTransfer.dropOwned(body, "left", "ground-drop", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "ground drop succeeds");
        var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.STICK));
        require(items.size() == 1 && Math.abs(items.getFirst().getY() - (body.getY() + .001)) < .01,
                "item rests directly above hit ground surface: " + items.getFirst().position()
                        + " eye=" + body.getEyePosition() + " look=" + body.getLookAngle());
        require(items.getFirst().getDeltaMovement().lengthSqr() == 0, "surface placement has no intentional velocity");
        helper.succeed();
    }

    public static void dropFitsUnderTwoBlockRoof(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        body.setYRot(-90);
        body.setXRot(65);
        // Two blocks of actual body-relative headroom; the ceiling must intersect eye height,
        // while the conservative launch point remains beneath it.
        BlockPos feet = body.blockPosition();
        BlockPos roof = feet.above(2);
        BlockPos shelf = feet.offset(1, -1, 0);
        helper.getLevel().setBlock(roof, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(shelf, Blocks.STONE.defaultBlockState(), 3);
        require(!helper.getLevel().getBlockState(roof).getCollisionShape(helper.getLevel(), roof).isEmpty(),
                "body-relative roof has collision");
        require(roof.getY() > body.getEyeY() && roof.getY() > body.getY() + 1.125,
                "actual two-block room roof is above eye and hand-height launch");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("roof-drop"), new ItemStack(Items.STICK));
        require(stored.succeeded(), "fixture item stored");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        require(BodyHandItemTransfer.dropOwned(body, "left", "roof-drop", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "drop succeeds under roof");
        var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.STICK));
        require(items.size() == 1, "one dropped item remains in world");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("left").isEmpty(),
                "hand relinquishes only after placement succeeds");
        helper.succeed();
    }

    public static void groundAimIsClampedWithoutLosingDrop(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        body.setYRot(-90);
        body.setXRot(16);
        BlockPos feet = body.blockPosition();
        helper.getLevel().setBlock(feet.offset(4, -1, 0), Blocks.STONE.defaultBlockState(), 3);
        var placement = BodyItemTargetProbe.resolveDrop(helper.getLevel(), body);
        require(placement != null, "out-of-aim ground cannot deny conservation placement");
        Vec3 launch = new Vec3(body.getX(), body.getY() + 1.125, body.getZ());
        require(launch.distanceTo(placement.position()) <= 3.5,
                "ground beyond the 3.5-block aim ray is not selected");
        helper.succeed();
    }

    /** Carrier exclusion is exact: no implicit mob exemption and no unrelated-occupant exemption. */
    public static void crowdingHonorsExactCarrierExclusion(GameTestHelper helper) {
        var level = helper.getLevel();
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setYRot(-90);
        require(BodyItemTargetProbe.resolveDrop(level, body) == null,
                "overlapping carrier blocks placement when not explicitly excluded");
        require(BodyItemTargetProbe.resolveDrop(level, body, carrier) != null,
                "explicitly excluded carrier permits a clear candidate");
        Villager unrelated = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(BodyItemTargetProbe.resolveDrop(level, body, carrier) == null,
                "unrelated overlapping entity still blocks placement");
        unrelated.discard();
        require(BodyItemTargetProbe.resolveDrop(level, body, carrier) != null,
                "placement becomes clear after only unrelated blocker is removed");
        carrier.discard();
        body.discard();
        helper.succeed();
    }

    /** Trusted fixture seam: blocked ownership remains in hand, then transfers once the unrelated blocker clears. */
    public static void crowdedDropRetainsHandUntilUnrelatedBlockerClears(GameTestHelper helper) {
        var level = helper.getLevel();
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        Villager unrelated = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setYRot(-90);
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack fixtureStack = new ItemStack(Items.STICK, 4);
        fixtureStack.set(DataComponents.CUSTOM_NAME, Component.literal("named carrier drop"));
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("carrier-drop"), fixtureStack);
        require(stored.succeeded(), "fixture item stored");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        var query = body.getBoundingBox().inflate(5);
        var before = level.getEntitiesOfClass(ItemEntity.class, query).stream()
                .map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        long revision = stored.state().revision();
        require(BodyHandItemTransfer.dropOwned(body, carrier, "left", "carrier-drop", revision, () -> true)
                        == BodyHandItemTransfer.Result.DENIED,
                "explicit carrier exclusion does not bypass unrelated blocker");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after == stored.state(), "denied attempt retains the exact pre-attempt hand-state object");
        require(retainsDropOwner(after, revision, "carrier-drop", stored.state().stackCopy("left").orElseThrow()),
                "denied attempt preserves hand owner, token, revision and whole stack");
        require(level.getEntitiesOfClass(ItemEntity.class, query).stream().map(ItemEntity::getUUID)
                        .noneMatch(id -> !before.contains(id)),
                "denied attempt publishes no item entity");
        unrelated.discard();
        var placement = BodyItemTargetProbe.resolveDrop(level, body, carrier);
        require(placement != null, "candidate clears after unrelated blocker alone is removed");
        require(BodyHandItemTransfer.dropOwned(body, carrier, "left", "carrier-drop", revision, () -> true)
                        == BodyHandItemTransfer.Result.SUCCESS,
                "same revision and token succeeds with only designated carrier excluded");
        after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after.token("left").isEmpty() && after.revision() == revision + 1,
                "success clears hand and advances revision exactly once");
        var created = level.getEntitiesOfClass(ItemEntity.class, query, item -> !before.contains(item.getUUID()));
        require(created.size() == 1 && created.getFirst().getItem().getCount() == fixtureStack.getCount()
                        && ItemStack.isSameItemSameComponents(created.getFirst().getItem(), fixtureStack),
                "exactly one newly published whole-stack owner");
        require(created.getFirst().position().distanceToSqr(placement.position()) < .001,
                "created owner uses the resolved clear placement candidate");
        carrier.discard();
        body.discard();
        helper.succeed();
    }

    static boolean retainsDropOwner(LiveHands state, long revision, String token, ItemStack expectedStack) {
        var actualStack = state.stackCopy("left");
        return state.revision() == revision && state.token("left").filter(token::equals).isPresent()
                && actualStack.isPresent() && actualStack.orElseThrow().getCount() == expectedStack.getCount()
                && ItemStack.isSameItemSameComponents(actualStack.orElseThrow(), expectedStack);
    }

    public static void enclosedDropUsesLoadedBodyFallback(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setYRot(-90);
        BlockPos feet = body.blockPosition();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            helper.getLevel().setBlock(feet.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int y = 0; y <= 1; y++) {
            helper.getLevel().setBlock(feet.offset(-1, y, 0), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet.offset(1, y, 0), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet.offset(0, y, -1), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet.offset(0, y, 1), Blocks.STONE.defaultBlockState(), 3);
        }
        for (BlockPos wall : new BlockPos[]{feet.offset(-1, 0, 0), feet.offset(1, 0, 0),
                feet.offset(0, 0, -1), feet.offset(0, 0, 1)})
            require(!helper.getLevel().getBlockState(wall).getCollisionShape(helper.getLevel(), wall).isEmpty(),
                    "body-relative enclosing wall has collision: " + wall);
        BlockPos roof = feet.above(2);
        require(!helper.getLevel().getBlockState(roof).getCollisionShape(helper.getLevel(), roof).isEmpty(),
                "body-relative enclosure roof has collision");
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("enclosed-drop"), new ItemStack(Items.STICK));
        require(stored.succeeded(), "fixture item stored");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        require(BodyHandItemTransfer.dropOwned(body, "left", "enclosed-drop", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "enclosure cannot deny drop");
        require(helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(3),
                item -> item.getItem().is(Items.STICK)).size() == 1, "one item spawned in loaded enclosure");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
