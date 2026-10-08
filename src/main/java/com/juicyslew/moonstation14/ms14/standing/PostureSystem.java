package com.juicyslew.moonstation14.ms14.standing;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.components.CrawlerComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.StandingStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.NativeHeldItemTransfer;
import com.juicyslew.moonstation14.ms14.mobs.MobStateChangedEvent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import com.juicyslew.moonstation14.ms14.damage.AppliedDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server authority for prone posture; prototype policy is always read from the resolved body. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class PostureSystem {
    private static final String GET_UP = "standing";
    private static final Map<UUID, LivingEntity> DOWN_BODIES = new HashMap<>();
    private static final Map<UUID, Long> OWN_CRAWL_EXPIRY = new HashMap<>();
    private PostureSystem() { }

    private record Policy(boolean eligible, CrawlerComponent crawler) { }
    private static Policy policy(LivingEntity body) {
        var character = ActiveCharacterPolicy.resolveActor(body).orElse(null);
        if (character == null) return new Policy(false, null);
        var standing = character.component(StandingStateComponent.class).orElse(null);
        return new Policy(standing != null,
                character.component(CrawlerComponent.class).orElse(null));
    }
    private static PostureData posture(LivingEntity body) {
        PostureAttachment value = body.getExistingDataOrNull(ModDataAttachments.POSTURE.get());
        return value == null ? PostureData.STANDING : value.data();
    }
    private record HandAvailability(int total, int free, boolean hasHands) { }
    private static HandAvailability handAvailability(LivingEntity body) {
        var character = ActiveCharacterPolicy.resolveActor(body).orElse(null);
        boolean hasHands = character != null && character.component(HandsPrototypeComponent.class).isPresent();
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (!(body.level() instanceof ServerLevel)) {
            PostureData view = posture(body);
            return new HandAvailability(view.totalHands(), view.freeHands(), hasHands);
        }
        if (hands == null && hasHands) hands = LiveHands.initialize(body).orElse(null);
        if (hands == null || !hands.compatible(body)) return new HandAvailability(0, 0, hasHands);
        LiveHands resolved = hands;
        int typedTotal = resolved.handIds().size();
        if (body instanceof net.minecraft.world.entity.player.Player player) {
            boolean mappingSupported = resolved.handIds().contains("left") && resolved.handIds().contains("right");
            if (!mappingSupported && NativeHeldItemTransfer.occupiedHands(body) > 0)
                return new HandAvailability(typedTotal, 0, hasHands);
            int free = (int) resolved.handIds().stream().filter(id -> resolved.stackCopy(id).isEmpty()
                    && resolved.reservation(id).isEmpty()
                    && !NativeHeldItemTransfer.nativeOccupiedHand(player, id)).count();
            return new HandAvailability(typedTotal, free, hasHands);
        }
        int free = (int) resolved.handIds().stream()
                .filter(id -> resolved.stackCopy(id).isEmpty() && resolved.reservation(id).isEmpty()).count();
        return new HandAvailability(typedTotal, free, hasHands);
    }
    private static void save(LivingEntity body, PostureData data) {
        if (!data.ownCrawling()) OWN_CRAWL_EXPIRY.remove(body.getUUID());
        body.setData(ModDataAttachments.POSTURE.get(), new PostureAttachment(data));
        PhysicalPosture.apply(body);
        if (data.down()) DOWN_BODIES.put(body.getUUID(), body);
        else DOWN_BODIES.remove(body.getUUID(), body);
        // refreshDimensions updates the server AABB; the attachment sync triggers the client bridge.
        body.refreshDimensions();
    }
    public static boolean isDownPosture(LivingEntity body) { return isDown(body); }
    private static long now(ServerLevel level) { return level.getServer().overworld().getGameTime(); }
    private static boolean conscious(LivingEntity body) {
        return body.isAlive() && !body.isDeadOrDying()
                && com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(body);
    }
    public static boolean isDown(LivingEntity body) { return body != null && posture(body).down(); }
    public static double crawlMultiplier(LivingEntity body) {
        if (body == null || !posture(body).down()) return 1;
        CrawlerComponent crawler = policy(body).crawler();
        if (crawler == null) return 0;
        HandAvailability hands = handAvailability(body);
        if (!hands.hasHands()) return crawler.speedModifier();
        return PostureData.crawlSpeed(true, crawler.speedModifier(), hands.free(), hands.total());
    }
    /** Publish only coarse hand availability; private hand contents never leave the server. */
    public static void refreshMovementAvailability(LivingEntity body) {
        refreshMovementAvailability(body, body != null && (body.level().isClientSide
                ? posture(body).pulling()
                : com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService.isPulling(body)));
    }
    public static void refreshMovementAvailability(LivingEntity body, boolean pulling) {
        if (body == null || !(body.level() instanceof ServerLevel)) return;
        HandAvailability hands = handAvailability(body);
        int total = hands.total(), free = hands.free();
        PostureData current = posture(body);
        PostureData next = current.movement(total, free, pulling);
        if (!next.equals(current)) save(body, next);
    }
    /** SS14 StandingState friction default, modified by the resolved Crawler profile when present. */
    public static double downFriction(LivingEntity body) {
        if (body == null || !posture(body).down() || !policy(body).eligible()) return 1d;
        CrawlerComponent crawler = policy(body).crawler();
        return .4d * (crawler == null ? 1d : crawler.frictionModifier());
    }
    public static double standTime(LivingEntity body) {
        CrawlerComponent crawler = policy(body).crawler();
        double base = crawler == null ? 0 : crawler.standTimeSeconds();
        HandAvailability hands = handAvailability(body);
        return hands.hasHands() ? PostureData.standSeconds(base, hands.free(), hands.total()) : base;
    }
    public static boolean voluntaryToggle(LivingEntity body) {
        var character = body == null ? null : ActiveCharacterPolicy.resolveActor(body).orElse(null);
        var standing = character == null ? null : character.component(StandingStateComponent.class).orElse(null);
        if (body == null || !(body.level() instanceof ServerLevel level) || standing == null || !standing.standingEligible()
                || policy(body).crawler() == null || !body.isAlive() || !conscious(body)) return false;
        PostureData before = posture(body);
        if (before.down()) return requestStand(body, body instanceof net.minecraft.server.level.ServerPlayer player ? player : null);
        long grace = (long) Math.ceil(policy(body).crawler().defaultKnockedDurationSeconds() * 20);
        PostureData next = before.voluntaryToggle(now(level), Math.max(0, grace));
        HandAvailability hands = handAvailability(body);
        save(body, next.movement(hands.total(), hands.free(), next.pulling()).facing(body.getYRot()));
        return true;
    }
    public static boolean requestStand(LivingEntity body, net.minecraft.server.level.ServerPlayer owner) {
        if (body == null || !(body.level() instanceof ServerLevel level) || !posture(body).down()
                || !policy(body).eligible()) return false;
        if (!canGetUp(body) || !standingFits(body)) return false;
        UUID id = body.getUUID();
        int ticks = Math.max(1, (int)Math.ceil(standTime(body) * 20));
        var result = TimedInteractionSystem.start(body, owner, 0, GET_UP, id.toString(), ticks,
                true, false, 5,
                () -> body.isAlive() && body.level() == level && canGetUp(body),
                () -> {
                    if (!canGetUp(body)) return TimedInteractionSystem.Completion.STOP;
                    if (!standingFits(body)) return TimedInteractionSystem.Completion.STOP;
                    save(body, posture(body).completeGetUp(true));
                    return TimedInteractionSystem.Completion.STOP;
                });
        if (result == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED) {
            var state = body.getData(ModDataAttachments.TIMED_OPERATIONS.get());
            state.operations().stream().filter(op -> op.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE && op.kind().equals(GET_UP) && op.sourceKey().equals(id.toString())).findFirst().ifPresent(op -> save(body, posture(body).beginGetUp(op.id())));
        }
        return result == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED;
    }
    private static boolean canGetUp(LivingEntity body) {
        if (body == null || !(body.level() instanceof ServerLevel level) || !policy(body).eligible()) return false;
        PostureData data = posture(body);
        return data.mayStand(level.getServer().overworld().getGameTime(),
                !com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isStunned(body), conscious(body));
    }
    private static boolean standingFits(LivingEntity body) {
        // Uses the entity's standing pose dimensions, independent of its current prone dimensions.
        var dimensions = body.getDimensions(net.minecraft.world.entity.Pose.STANDING);
        var box = dimensions.makeBoundingBox(body.position());
        return body.level().noCollision(body, box);
    }
    public static void forceDown(LivingEntity body, long until) {
        forceDown(body, until, PostureData.DownTransition.KNOCKDOWN);
    }
    private static void forceDown(LivingEntity body, long until, PostureData.DownTransition transition) {
        if (!(body.level() instanceof ServerLevel level) || !policy(body).eligible()) return;
        PostureData current = posture(body);
        PostureData next = current.forceDown(Math.max(now(level), until), transition);
        OWN_CRAWL_EXPIRY.remove(body.getUUID());
        HandAvailability hands = handAvailability(body);
        if (!current.down()) next = next.facing(body.getYRot());
        save(body, next.movement(hands.total(), hands.free(), next.pulling()));
        TimedInteractionSystem.cancelDuplicate(body, GET_UP, body.getUUID().toString());
    }
    /** Conscious self-directed roll; never used by an external puller. */
    public static boolean voluntaryRoll(LivingEntity body) {
        if (body == null || !(body.level() instanceof ServerLevel) || !posture(body).down()
                || !conscious(body) || policy(body).crawler() == null) return false;
        save(body, posture(body).voluntaryRoll());
        return true;
    }
    /** Turn a distinct downed body using an actor with an available hand. */
    public static boolean turnOtherBody(LivingEntity actor, LivingEntity target) {
        if (actor == null || target == null || actor == target || actor.level() != target.level()
                || !conscious(actor) || !policy(actor).eligible() || policy(actor).crawler() == null
                || !posture(target).down() || target.isRemoved()) return false;
        HandAvailability hands = handAvailability(actor);
        if (!hands.hasHands() || hands.free() < 1) return false;
        var targetCharacter = ActiveCharacterPolicy.resolveActor(target).orElse(null);
        if (targetCharacter == null || targetCharacter.component(StandingStateComponent.class).isEmpty()) return false;
        save(target, posture(target).voluntaryRoll());
        return true;
    }
    /** Voluntary crawling input changes presentation without changing posture policy. */
    public static void onOwnCrawlInput(LivingEntity body, boolean hasInput) {
        if (body == null || !(body.level() instanceof ServerLevel level)) return;
        PostureData current = posture(body);
        boolean eligible = hasInput && current.down() && conscious(body)
                && policy(body).crawler() != null && crawlMultiplier(body) > 0;
        if (eligible) OWN_CRAWL_EXPIRY.put(body.getUUID(), now(level) + 12);
        else OWN_CRAWL_EXPIRY.remove(body.getUUID());
        PostureData next = current.crawling(eligible);
        if (eligible && next.faceUp()) next = new PostureData(next.down(), next.voluntaryAutoStand(),
                next.knockdownUntil(), next.getUpKey(), next.totalHands(), next.freeHands(), next.pulling(), false, true,
                next.movementFacingYaw());
        if (!next.equals(current)) save(body, next);
    }
    /** Updates prone presentation only from accepted, owner-authored horizontal world displacement. */
    public static void onOwnMovement(LivingEntity body, net.minecraft.world.phys.Vec3 acceptedDisplacement) {
        if (body == null || acceptedDisplacement == null || !(body.level() instanceof ServerLevel)
                || !posture(body).down() || !conscious(body) || policy(body).crawler() == null
                || crawlMultiplier(body) <= 0 || !Double.isFinite(acceptedDisplacement.x)
                || !Double.isFinite(acceptedDisplacement.z)
                || acceptedDisplacement.horizontalDistanceSqr() < 1.0e-8) return;
        float yaw = PostureData.yawForWorldMovement(acceptedDisplacement.x, acceptedDisplacement.z);
        PostureData current = posture(body);
        if (!current.ownCrawling()) return;
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - current.movementFacingYaw())) > .01f)
            save(body, current.facing(yaw));
    }
    /** SS14 Crawler damage refresh policy: positive applied typed damage uses the resolved threshold. */
    @SubscribeEvent public static void onAppliedDamage(AppliedDamageEvent event) {
        LivingEntity body = event.entity();
        if (!(body.level() instanceof ServerLevel level) || event.typedDamage() <= 0
                || !event.feedback().interruptActions() || !posture(body).down()) return;
        CrawlerComponent crawler = policy(body).crawler();
        if (crawler == null || event.typedDamage() < crawler.damageThreshold()) return;
        long grace = Math.max(0, (long) Math.ceil(crawler.defaultKnockedDurationSeconds() * 20));
        PostureData current = posture(body);
        PostureData next = current.refreshKnockdown(Math.addExact(now(level), grace));
        if (!next.equals(current)) save(body, next);
    }
    @SubscribeEvent public static void onMobStateChanged(MobStateChangedEvent event) {
        if (event.current() == MobStateComponent.State.CRITICAL || event.current() == MobStateComponent.State.DEAD) {
            CrawlerComponent crawler = policy(event.body()).crawler();
            long ticks = crawler == null ? 0 : Math.max(0, (long)Math.ceil(crawler.defaultKnockedDurationSeconds() * 20));
            if (event.body().level() instanceof ServerLevel level) forceDown(event.body(), now(level) + ticks,
                    PostureData.DownTransition.MEDICAL);
        }
    }
    @SubscribeEvent public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof LivingEntity body)
                || !posture(body).down() || !policy(body).eligible()) return;
        DOWN_BODIES.put(body.getUUID(), body);
    }
    @SubscribeEvent public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity body) {
            DOWN_BODIES.remove(body.getUUID(), body);
            OWN_CRAWL_EXPIRY.remove(body.getUUID());
        }
    }
     @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) { DOWN_BODIES.clear(); OWN_CRAWL_EXPIRY.clear(); }
    @SubscribeEvent public static void onPostServerTick(ServerTickEvent.Post event) {
        for (LivingEntity body : List.copyOf(DOWN_BODIES.values())) {
            if (body.isRemoved()) { DOWN_BODIES.remove(body.getUUID(), body); continue; }
            if (!(body.level() instanceof ServerLevel level) || level.getServer() != event.getServer()) continue;
            if (!posture(body).down()) { DOWN_BODIES.remove(body.getUUID(), body); continue; }
            refreshMovementAvailability(body);
            PostureData data = posture(body);
            Long crawlExpiry = OWN_CRAWL_EXPIRY.get(body.getUUID());
            if (data.ownCrawling() && (crawlExpiry == null || now(level) > crawlExpiry || !conscious(body))) {
                OWN_CRAWL_EXPIRY.remove(body.getUUID());
                save(body, data.crawling(false));
                data = posture(body);
            }
            if (!data.voluntaryAutoStand() || !canGetUp(body)) continue;
            UUID id = body.getUUID();
            var timed = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
            if (timed != null && timed.operations().stream().anyMatch(op -> op.phase()
                    == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE
                    && op.kind().equals(GET_UP) && op.sourceKey().equals(id.toString()))) continue;
            if (policy(body).crawler() == null) {
                if (standingFits(body)) save(body, data.completeGetUp(true));
            } else requestStand(body, body instanceof net.minecraft.server.level.ServerPlayer player ? player : null);
        }
    }
}
