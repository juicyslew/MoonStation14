package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.HandAttachment;
import com.juicyslew.moonstation14.ms14.hands.HandComponent;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.components.PullableComponent;
import com.juicyslew.moonstation14.ms14.character.components.PullerComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestTags({"pulling", "medical-state", "hands"})
public final class BodyPullGameTests {
    private BodyPullGameTests() { }

    @GameTestTags("full-suite-movement-regression")
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void pullsDeadHumanTargetAndReleasesWhenSourceBecomesCritical(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        var ticket = FixtureChunkTickets.around(helper, origin, origin.offset(10, 0, 0), 1);
        Villager source = human(level, origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        Villager target = human(level, origin.getX() + 2.5, origin.getY(), origin.getZ() + .5);
        require(ticket.entityTicking(source.blockPosition()) && ticket.entityTicking(target.blockPosition()),
                "source, target, and selection-ray chunks are entity-ticking before pull admission");
        LiveHands sourceHands = LiveHands.initialize(source).orElseThrow();
        LiveHands targetHands = LiveHands.initialize(target).orElseThrow();
        require(DamageSystem.applyHealthChange(target, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isDead(target), "target enters native DEAD state");
        aimAt(source, visibleInteractionPoint(source, target));
        var targetId = target.getUUID();
        require(BodyPullService.toggleFixture(source, targetId, "left", sourceHands.revision(), () -> true)
                == BodyPullService.Result.SUCCESS, "dead human target can be pulled; "
                        + pullAdmissionDiagnostic(source, target, () -> true));
        require(BodyPullService.toggleFixture(target, source.getUUID(), "left", targetHands.revision(), () -> true)
                != BodyPullService.Result.SUCCESS, "another body cannot reserve the already-pulled target");
        require(source.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).reservation("left")
                .equals(java.util.Optional.of(targetId)), "pull reserves source hand");
        require(source.getExistingDataOrNull(ModDataAttachments.POSTURE.get()).data().pulling(),
                "successful pull publishes pulling posture flag");

        source.setPos(source.getX() - 1.5, source.getY(), source.getZ());
        for (int i = 0; i < 5; i++) BodyPullService.onPostServerTick(null);
        require(level.getEntity(targetId) == target && !target.isRemoved() && target.getUUID().equals(targetId)
                && BodyPullService.isPulling(source), "dead target remains leased without identity replacement");
        var endpoint = source.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get());
        require(endpoint != null && endpoint.constraints().size() == 1
                        && endpoint.constraints().get(0).other().equals(targetId),
                "pull publishes endpoint state for the native spring consumer");
        require(MobStateSystem.isDead(target), "pull retains DEAD medical state");

        require(DamageSystem.applyHealthChange(source, Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                == DamageSystem.Result.APPLIED, "source receives lethal medical damage");
        require(!BodyPullService.isPulling(source)
                && source.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).reservation("left").isEmpty(),
                "medical incapacitation stops pull and releases its hand");
        require(!source.getExistingDataOrNull(ModDataAttachments.POSTURE.get()).data().pulling(),
                "stopped pull clears pulling posture flag");
        ticket.close();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void tetherRetainsLeaseAtElasticExtensionAndClearsOnReservationLoss(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager source = human(level, origin.getX() + 2.5, origin.getY(), origin.getZ() + .5);
        Villager target = human(level, origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        source.setYRot(90);
        source.setYHeadRot(90);
        LiveHands hands = LiveHands.initialize(source).orElseThrow();
        var targetId = target.getUUID();
        require(BodyPullService.toggleFixture(source, targetId, "left", hands.revision(), () -> true)
                        == BodyPullService.Result.SUCCESS,
                "pull starts with a live target and free hand");
        double targetBeforeFollow = target.getX();
        source.setPos(source.getX() + 1.5, source.getY(), source.getZ());
        BodyPullService.onPostServerTick(null);
        require(BodyPullService.isPulling(source), "finite elastic extension does not detach the lease");
        var endpoint = source.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get());
        require(endpoint != null && endpoint.constraints().get(0).self().position().x() == source.getX()
                        && endpoint.constraints().get(0).otherSnapshot().position().x() == targetBeforeFollow,
                "elastic extension refreshes the synchronized spring endpoint snapshots");

        source.removeData(ModDataAttachments.LIVE_HANDS.get());
        BodyPullService.onPostServerTick(null);
        require(!BodyPullService.isPulling(source), "lost reservation releases the pull lease");
        require(source.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get()) == null,
                "lost reservation removes synchronized rope state");
        require(!source.getExistingDataOrNull(ModDataAttachments.POSTURE.get()).data().pulling(),
                "lost reservation clears the pulling posture projection");
        helper.succeed();
    }

    @GameTestTags({"pulling", "removed-puller-cleanup"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void removedPullerDetachesWithoutThrowingOnHandCleanup(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager source = human(level, origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        Villager target = human(level, origin.getX() + 2.5, origin.getY(), origin.getZ() + .5);
        LiveHands hands = LiveHands.initialize(source).orElseThrow();
        require(BodyPullService.toggleFixture(source, target.getUUID(), "left", hands.revision(), () -> true)
                        == BodyPullService.Result.SUCCESS,
                "pull starts before puller removal");

        source.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        BodyPullService.onPostServerTick(null);

        require(!BodyPullService.isPulling(source), "removed puller lease is detached");
        require(target.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get()) == null,
                "removed puller rope endpoint is cleared");
        helper.succeed();
    }

    @GameTestTags({"pulling", "merge-main-regression"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void incompatibleLivePullerClearsOnlyItsExactReservation(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager source = human(level, origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        Villager target = human(level, origin.getX() + 2.5, origin.getY(), origin.getZ() + .5);
        LiveHands initial = LiveHands.initialize(source).orElseThrow();
        var encoded = LiveHands.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, initial)
                .getOrThrow().getAsJsonObject();
        var slots = encoded.getAsJsonArray("hands");
        var right = java.util.stream.StreamSupport.stream(slots.spliterator(), false)
                .map(com.google.gson.JsonElement::getAsJsonObject)
                .filter(slot -> slot.get("id").getAsString().equals("right")).findFirst().orElseThrow();
        var occupant = new com.google.gson.JsonObject();
        occupant.addProperty("token", "preserved-item");
        occupant.add("stack", net.minecraft.world.item.ItemStack.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 3)).getOrThrow());
        right.add("occupant", occupant);
        LiveHands withItem = LiveHands.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
        source.setData(ModDataAttachments.LIVE_HANDS.get(), withItem);
        require(BodyPullService.toggleFixture(source, target.getUUID(), "left", withItem.revision(), () -> true)
                        == BodyPullService.Result.SUCCESS,
                "pull starts with compatible live hand layout");
        LiveHands reserved = source.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var equipmentBefore = LiveHands.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, reserved)
                .getOrThrow().getAsJsonObject().get("equipment").deepCopy();

        var changedMetadata = new HandAttachment(HandComponent.from(HandState.create(List.of("right", "left"))));
        source.setData(ModDataAttachments.HANDS.get(), changedMetadata);
        require(!reserved.compatible(source), "changed declared hand layout makes saved live state incompatible");

        BodyPullService.onPostServerTick(null);

        LiveHands cleaned = source.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(!BodyPullService.isPulling(source), "incompatible live puller lease detaches");
        require(cleaned != null && cleaned.revision() == reserved.revision() + 1
                        && cleaned.reservation("left").isEmpty(),
                "cleanup removes only the exact owned reservation");
        require(cleaned.token("right").equals(java.util.Optional.of("preserved-item"))
                        && cleaned.stackCopy("right").orElseThrow().getCount() == 3,
                "other held item and count are preserved");
        require(LiveHands.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, cleaned)
                        .getOrThrow().getAsJsonObject().get("equipment").equals(equipmentBefore),
                "native equipment state is preserved");
        require(source.getExistingDataOrNull(ModDataAttachments.HANDS.get()) == changedMetadata,
                "cleanup does not rewrite historical hand-layout metadata");
        require(source.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get()) == null
                        && target.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get()) == null,
                "both rope endpoints detach");
        require(!source.getExistingDataOrNull(ModDataAttachments.POSTURE.get()).data().pulling(),
                "pulling posture projection clears");
        helper.succeed();
    }

    private static Villager human(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "human-profile villager created");
        body.setNoAi(true);
        body.setNoGravity(true);
        body.setPos(x, y, z);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID)
                && BodySystem.reconcile(body), "villager enrolled as human body");
        body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        body.setHealth(40f);
        return body;
    }

    static String pullAdmissionDiagnostic(LivingEntity body, net.minecraft.world.entity.Entity target,
                                          java.util.function.BooleanSupplier authorityValid) {
        var actorData = ActiveCharacterPolicy.resolveActor(body);
        var targetData = ActiveCharacterPolicy.resolveActor(target);
        boolean puller = actorData.flatMap(data -> data.component(PullerComponent.class)).isPresent();
        boolean pullable = targetData.flatMap(data -> data.component(PullableComponent.class)).isPresent();
        Vec3 eye = body.getEyePosition();
        Vec3 rayEnd = eye.add(body.getLookAngle().scale(5.0));
        var targetBox = com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of((LivingEntity) target);
        Vec3 selectedPoint = com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay
                .hit(targetBox, eye, rayEnd, 5.0, Double.POSITIVE_INFINITY).orElse(null);
        boolean visible = false;
        Object blockHit = "not-tested";
        boolean selectionChunksLoaded = false;
        if (selectedPoint != null && body.level() instanceof net.minecraft.server.level.ServerLevel level) {
            var endpoint = net.minecraft.core.BlockPos.containing(selectedPoint);
            int minX = Math.min(body.blockPosition().getX(), endpoint.getX()) >> 4;
            int maxX = Math.max(body.blockPosition().getX(), endpoint.getX()) >> 4;
            int minZ = Math.min(body.blockPosition().getZ(), endpoint.getZ()) >> 4;
            int maxZ = Math.max(body.blockPosition().getZ(), endpoint.getZ()) >> 4;
            selectionChunksLoaded = true;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                selectionChunksLoaded &= level.getChunkSource().getChunkNow(x, z) != null;
            var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, selectedPoint,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, body));
            blockHit = hit.getType() + (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                    ? ":" + ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos() : "");
            visible = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
        }
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return "source=" + body.getUUID() + " pos=" + body.position() + " alive=" + body.isAlive()
                + " critical=" + MobStateSystem.isCritical(body) + " dead=" + MobStateSystem.isDead(body)
                + " stunned=" + CharacterControlSystem.isStunned(body) + " canAct=" + CharacterControlSystem.canAct(body)
                + " actorIdentity=" + identityId(body)
                + " actorResolved=" + actorData.isPresent() + " puller=" + puller
                + " hands=" + (hands == null ? null : "compatible=" + hands.compatible(body) + ",revision=" + hands.revision())
                + " target=" + target.getUUID() + " targetPos=" + target.position() + " targetPresent="
                + (body.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && serverLevel.getEntity(target.getUUID()) == target) + " targetRemoved=" + target.isRemoved()
                + " targetIdentity=" + identityId(target)
                + " targetResolved=" + targetData.isPresent() + " pullable=" + pullable
                + " authorityValid=" + authorityValid.getAsBoolean() + " rayPoint=" + selectedPoint
                + " rayDistance=" + (selectedPoint == null ? "none" : eye.distanceTo(selectedPoint))
                + " rayReach=5.0 selectionChunksLoaded=" + selectionChunksLoaded
                + " visible=" + visible + " blockHit=" + blockHit + " targetAlive="
                + (target instanceof LivingEntity living && living.isAlive()) + " targetCritical="
                + (target instanceof LivingEntity living && MobStateSystem.isCritical(living)) + " targetDead="
                + (target instanceof LivingEntity living && MobStateSystem.isDead(living));
    }

    private static Vec3 visibleInteractionPoint(LivingEntity body, LivingEntity target) {
        var box = com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(target);
        Vec3 eye = body.getEyePosition();
        return new Vec3(net.minecraft.util.Mth.clamp(eye.x, box.minX, box.maxX),
                net.minecraft.util.Mth.clamp(eye.y, box.minY, box.maxY),
                net.minecraft.util.Mth.clamp(eye.z, box.minZ, box.maxZ));
    }

    private static void aimAt(LivingEntity body, Vec3 point) {
        Vec3 eye = body.getEyePosition();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        body.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        body.setYHeadRot(body.getYRot());
        body.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static Object identityId(net.minecraft.world.entity.Entity entity) {
        var identity = entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        return identity == null ? null : identity.characterId();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
