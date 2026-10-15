package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTest;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight;

import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "portable-throw"})
public final class BodyLooseThrowGameTests {
    private BodyLooseThrowGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void throwsWholeStackAndDeniesStaleRequests(GameTestHelper helper) {
        Villager body = body(helper);
        Set<UUID> looseBefore = looseIds(helper, body);
        ItemStack original = new ItemStack(net.minecraft.world.item.Items.DIAMOND, 7);
        original.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("whole throw"));
        LiveHands stocked = stock(body, original, "throw-token");
        require(BodyHandItemTransferGameTestAccess.throwOwned(body, "left", "throw-token", stocked.revision() - 1)
                == BodyLooseThrow.Result.DENIED, "stale throw denied");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == stocked
                && looseIds(helper, body).equals(looseBefore),
                "stale denial leaves owner and world unchanged");
        require(BodyHandItemTransferGameTestAccess.throwOwned(body, "left", "throw-token", stocked.revision())
                == BodyLooseThrow.Result.SUCCESS, "fresh whole-stack throw succeeds");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var loose = newLooseAfter(helper, body, looseBefore);
        require(after.revision() == stocked.revision() + 1 && after.stackCopy("left").isEmpty()
                && loose.size() == 1 && loose.getFirst().getItem().getCount() == 7
                && loose.getFirst().getItem().getHoverName().getString().equals("whole throw"),
                "entire component-bearing stack transferred once");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void armedGrenadeComponentSurvivesLooseThrow(GameTestHelper helper) {
        Villager body = body(helper);
        Set<UUID> looseBefore = looseIds(helper, body);
        ItemStack grenade = new ItemStack(ModItems.GRENADE.get());
        GrenadeFuseData fuse = new GrenadeFuseData(GrenadeFuse.Phase.ARMED, helper.getLevel().getGameTime() + 200, true, false);
        grenade.set(ModDataComponents.GRENADE_FUSE.get(), fuse);
        LiveHands stocked = stock(body, grenade, "armed-grenade");
        require(BodyHandItemTransferGameTestAccess.throwOwned(body, "left", "armed-grenade", stocked.revision())
                == BodyLooseThrow.Result.SUCCESS, "armed grenade throw succeeds");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var loose = newLooseAfter(helper, body, looseBefore);
        require(after.revision() == stocked.revision() + 1 && after.stackCopy("left").isEmpty(),
                "successful throw clears hand and advances revision once");
        require(loose.size() == 1 && ItemStack.isSameItemSameComponents(loose.getFirst().getItem(), grenade)
                        && loose.getFirst().getItem().getCount() == grenade.getCount()
                        && fuse.equals(loose.getFirst().getItem().get(ModDataComponents.GRENADE_FUSE.get())),
                "whole grenade stack, armed phase, and deadline preserved on loose grenade");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void jugThrowFitsUnderTwoBlockCeiling(GameTestHelper helper) {
        Villager body = body(helper);
        Set<UUID> looseBefore = looseIds(helper, body);
        body.setYRot(-90);
        body.setXRot(0);
        BlockPos feet = body.blockPosition();
        int ceilingY = feet.getY() + 2;
        // Cover the initial launch point and its forward path, not just the body's head.
        for (int x = feet.getX() - 1; x <= feet.getX() + 4; x++) {
            for (int z = feet.getZ() - 1; z <= feet.getZ() + 1; z++) {
                helper.getLevel().setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        ItemStack original = new ItemStack(ModItems.JUG.get());
        original.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("whole jug"));
        LiveHands stocked = stock(body, original, "ceiling-jug");
        require(BodyHandItemTransferGameTestAccess.throwOwned(body, "left", "ceiling-jug", stocked.revision())
                == BodyLooseThrow.Result.SUCCESS, "jug throw succeeds below ceiling");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var loose = newLooseAfter(helper, body, looseBefore);
        require(after.revision() == stocked.revision() + 1 && after.stackCopy("left").isEmpty(),
                "successful throw clears hand and advances revision once");
        require(loose.size() == 1 && ItemStack.isSameItemSameComponents(loose.getFirst().getItem(), original)
                        && loose.getFirst().getItem().getCount() == original.getCount(),
                "exactly one unchanged whole jug is in the world");
        require(loose.getFirst().getBoundingBox().maxY <= ceilingY,
                "entire jug collision box remains below ceiling: " + loose.getFirst().getBoundingBox());
        require(loose.getFirst().getDeltaMovement().dot(body.getLookAngle()) > 0,
                "jug has nonzero forward launch velocity");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 160)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "npc-flight", "portable-throw"})
    public static void portableKeyThrowReleasesAndFliesOriginalBody(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        Villager carrier = body(helper);
        // The throw test owns a compact, explicit 6x6 floor and a full cross-lane wall.
        // Keep all authored collision geometry inside its 16x16 empty-template footprint.
        for (int x = 0; x <= 5; x++) for (int z = 0; z <= 5; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        int wallZ = 4;
        for (int x = 0; x <= 5; x++) for (int y = 1; y <= 5; y++)
            level.setBlock(helper.absolutePos(new BlockPos(x, y, wallZ)), Blocks.STONE.defaultBlockState(), 3);
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "resolved mouse prototype creates original body");
        mouse.setNoAi(true);
        mouse.setPos(carrier.getX(), carrier.getY(), carrier.getZ() + .5);
        require(level.addFreshEntity(mouse), "original mouse added");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse, level);
        mouse.setData(ModDataAttachments.STOMACH.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment());
        require(ExternalBodyFlight.canLaunch(mouse), "ordinary prototype mouse is a valid flight recipient");
        var stomach = mouse.getExistingDataOrNull(ModDataAttachments.STOMACH.get()).toComponent();
        Set<UUID> looseBefore = looseIds(helper, carrier);
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        LiveHands before = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stocked = before.putWhole(before.revision(), "left", new ItemToken("portable-throw"), key);
        require(stocked.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, before, stocked.state()),
                "exact portable key committed to carrier hand");
        Vec3 destination = carrier.position().add(0, 0, 5);
        Vec3 delta = destination.subtract(carrier.getEyePosition());
        carrier.setYRot(0);
        carrier.setXRot((float) Math.toDegrees(Math.atan2(-delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        require(com.juicyslew.moonstation14.ms14.hands.HandCapability.resolveCharacter((net.minecraft.world.entity.Mob) mouse)
                        .flatMap(character -> character.component(com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent.class))
                        .isPresent(), "original mouse retains resolved portable capability");
        require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(carrier),
                "fixture carrier has ordinary action authority");
        require(BodyItemTargetProbe.loadedAimPoint(level, carrier, 8.0) != null,
                "loaded block-collider ray yields portable throw aim");
        var portableProfile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolve(
                com.juicyslew.moonstation14.ms14.hands.HandCapability.resolveCharacter((net.minecraft.world.entity.Mob) mouse)
                        .flatMap(character -> character.component(com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent.class))
                        .orElseThrow().profileId());
        Vec3 portableAim = BodyItemTargetProbe.loadedAimPoint(level, carrier, Math.min(8.0, portableProfile.throwRange()));
        double portableHandY = carrier.getY() + net.minecraft.util.Mth.clamp(1.125, 0.0,
                Math.max(0.0, carrier.getBbHeight() - portableProfile.physicalHeight() - .075));
        Vec3 portableOrigin = new Vec3(carrier.getX(), portableHandY, carrier.getZ());
        Vec3 portableLaunch = portableOrigin.add(portableAim.subtract(portableOrigin).normalize().scale(.1));
        require(BodyItemTargetProbe.loadedItemBoxClear(level, portableLaunch,
                        portableProfile.physicalWidth(), portableProfile.physicalHeight()),
                "resolved body-sized throw launch box is clear: " + portableLaunch + ", aim=" + portableAim);
        require(PortableBodyReleaseTransaction.capture(mouse) != null,
                "exact original body has capturable prepared portable custody");
        UUID id = mouse.getUUID();
        var actual = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyHandItemTransferGameTestAccess.throwOwned(carrier, "left", "portable-throw", actual.revision() - 1)
                        == BodyLooseThrow.Result.DENIED,
                "stale key throw denied");
        require(carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == actual
                        && level.getEntity(id) == mouse && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                        .forLevel(level).ownerOf(mouse).isPresent(), "stale denial conserves key and original owner");
        Vec3 start = mouse.position();
        var portableThrow = BodyHandItemTransferGameTestAccess.throwOwned(carrier, "left", "portable-throw", actual.revision());
        require(portableThrow == BodyLooseThrow.Result.SUCCESS, "portable key launches same body: " + portableThrow
                + ", release=" + PortableBodyReleaseTransaction.lastDiagnostic() + ", owner="
                + com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(mouse)
                + ", flags=" + mouse.isInvisible() + "/" + mouse.noPhysics + "/" + mouse.isNoGravity());
        require(level.getEntity(id) == mouse && mouse.getExistingDataOrNull(ModDataAttachments.STOMACH.get())
                        .toComponent().equals(stomach) && !com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                        .forLevel(level).ownerOf(mouse).isPresent() && ExternalBodyFlight.tracked((net.minecraft.world.entity.Mob) mouse)
                        && looseIds(helper, carrier).equals(looseBefore),
                "original UUID, physiology, and flight survive release without a loose key proxy");
        helper.runAfterDelay(2, () -> {
            require(mouse.position().distanceToSqr(start) > .01, "original mouse advances under external flight physics");
            pollPortableLanding(helper, mouse, id, start, helper.absolutePos(new BlockPos(0, 1, wallZ)), 0);
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"living-carry", "portable-throw"})
    public static void portableKeyThrowCooldownPreservesKeyAndBody(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        Villager carrier = body(helper);
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "resolved mouse prototype creates cooldown target");
        mouse.setNoAi(true);
        mouse.setPos(carrier.getX(), carrier.getY(), carrier.getZ() + .5);
        require(level.addFreshEntity(mouse), "cooldown target added");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse, level);
        ItemStack key = PortableBodyCarrying.createKey(carrier, mouse, UUID.randomUUID());
        LiveHands initial = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var keyed = initial.putWhole(initial.revision(), "left", new ItemToken("cooldown-portable-key"), key);
        require(keyed.succeeded(), "portable key staged");
        var ordinary = keyed.state().putWhole(keyed.state().revision(), "right", new ItemToken("cooldown-first-throw"),
                new ItemStack(net.minecraft.world.item.Items.DIAMOND));
        require(ordinary.succeeded() && LiveHandsCommitGateway.commitTrusted(carrier, initial, ordinary.state()),
                "key and ordinary item committed together");
        var ready = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyHandItemTransferGameTestAccess.throwOwned(carrier, "right", "cooldown-first-throw", ready.revision())
                == BodyLooseThrow.Result.SUCCESS, "ordinary throw starts existing carrier cooldown");
        var cooling = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        UUID mouseId = mouse.getUUID();
        require(BodyHandItemTransferGameTestAccess.throwOwned(carrier, "left", "cooldown-portable-key", cooling.revision())
                        == BodyLooseThrow.Result.DENIED,
                "portable throw observes same generic holder cooldown");
        require(cooling == carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())
                        && cooling.stackCopy("left").filter(stack -> PortableBodyKeys.isKey(stack)
                        && PortableBodyKeys.reference(stack).body().equals(mouseId)).isPresent()
                        && level.getEntity(mouseId) == mouse && com.juicyslew.moonstation14.ms14.containment
                        .BodyContainmentService.forLevel(level).ownerOf(mouse).isPresent(),
                "cooldown denial preserves exact key, original body, and custody");
        helper.succeed();
    }

    private static void pollPortableLanding(GameTestHelper helper, net.minecraft.world.entity.LivingEntity mouse,
                                             UUID id, Vec3 start, BlockPos wall, int elapsed) {
        if (mouse.onGround()) {
            require(mouse.getUUID().equals(id) && helper.getLevel().getEntity(id) == mouse,
                    "original UUID remains after wall collision and landing");
            require(mouse.getZ() < wall.getZ() && mouse.position().distanceToSqr(start) > .1,
                    "wall stops original body before plane after physical flight: body=" + mouse.position()
                            + ", start=" + start + ", velocity=" + mouse.getDeltaMovement()
                            + ", box=" + mouse.getBoundingBox() + ", wall=" + wall
                            + ", flightTracked=" + ExternalBodyFlight.tracked((net.minecraft.world.entity.Mob) mouse)
                            + ", flightTicks=" + ExternalBodyFlight.remainingTicks((net.minecraft.world.entity.Mob) mouse)
                            + ", bodyBlock=" + mouse.blockPosition() + ", wallState="
                            + helper.getLevel().getBlockState(wall));
            helper.succeed();
            return;
        }
        if (elapsed >= 35) throw new AssertionError("original body did not land after flight expiry");
        helper.runAfterDelay(1, () -> pollPortableLanding(helper, mouse, id, start, wall, elapsed + 1));
    }

    private static Villager body(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(LiveHands.initialize(body).isPresent(), "eligible body initialized");
        return body;
    }

    private static LiveHands stock(Villager body, ItemStack stack, String token) {
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands next = before.putWhole(before.revision(), "left", new ItemToken(token), stack).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), next);
        return next;
    }

    private static Set<UUID> looseIds(GameTestHelper helper, Villager body) {
        return helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, body.getBoundingBox().inflate(8))
                .stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
    }

    private static java.util.List<LooseItemEntity> newLooseAfter(GameTestHelper helper, Villager body, Set<UUID> before) {
        return helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, body.getBoundingBox().inflate(8))
                .stream().filter(entity -> !before.contains(entity.getUUID())).toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
