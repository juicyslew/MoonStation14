package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerStackData;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Server-authoritative delayed health analyzer operations and per-actor window subscriptions. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class HealthAnalyzerServer {
    private static final String KIND = "health-analyzer";
    private static final double TARGET_REACH = 1.5;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Set<Subscriber>> SUBSCRIBERS = new HashMap<>();
    private static final Map<UUID, Long> EPOCHS = new HashMap<>();
    private static final Map<UUID, Long> FRAMES = new HashMap<>();
    private static final Map<UUID, Set<ServerPlayer>> ROOT_WATCHERS = new HashMap<>();
    /** Physical scanner root is deliberately independent of the active scan session. */
    private static final Map<UUID, ScannerRoot> ROOTS = new HashMap<>();
    private record ScannerRoot(ServerLevel level, UUID entity, boolean worldItem) { }
    private static final Map<Subscriber, HealthAnalyzerSnapshot> LAST_READINGS = new HashMap<>();

    private record Session(ServerPlayer actor, HandActorAuthority.Snapshot authority, UUID scanner,
                           LivingEntity target, MedicalItemData.HealthAnalyzerPolicy policy, long sequence) { }
    private record Subscriber(ServerPlayer actor, long epoch, UUID actorId) { }

    private HealthAnalyzerServer() { }

    /** USE_IN_HAND opens the analyzer UI; resolved ItemToggle policy may additionally toggle scanning. */
    public static BodyHandActionResult.Reason activate(ServerPlayer actor, String handId, String token,
                                                       long revision, long sequence) {
        HandActorAuthority.Snapshot authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null) return BodyHandActionResult.Reason.NO_AUTHORITY;
        LivingEntity body = authority.body();
        if (!intent(body, actor, handId, token, revision, authority)) return BodyHandActionResult.Reason.DENIED;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack before = hands.stackCopy(handId).orElse(ItemStack.EMPTY);
        MedicalItemData data = analyzer(body.level(), before);
        if (data == null || before.getCount() != 1) return BodyHandActionResult.Reason.DENIED;
        HealthAnalyzerStackData old = before.getOrDefault(ModDataComponents.HEALTH_ANALYZER.get(), HealthAnalyzerStackData.create());
        Session linked=SESSIONS.get(old.scannerId());
        if(linked==null || linked.target().isRemoved() || !linked.target().isAlive()) return BodyHandActionResult.Reason.DENIED;
        Set<Subscriber> opened=SUBSCRIBERS.get(old.scannerId());
        Subscriber existing=opened==null?null:opened.stream().filter(s->s.actor()==actor&&s.actorId().equals(body.getUUID())).findFirst().orElse(null);
        if(existing!=null) { closeUI(actor,old.scannerId(),existing.epoch(),existing.actorId()); return BodyHandActionResult.Reason.OK; }
        var decision = HealthAnalyzerActivationPolicy.decide(data.toggle().map(MedicalItemData.TogglePolicy::onUse).orElse(false), old.active());
        if (old.scannerId() == null) return BodyHandActionResult.Reason.DENIED;
        if (before.get(ModDataComponents.HEALTH_ANALYZER.get()) == null || decision != HealthAnalyzerActivationPolicy.Decision.OPEN_ONLY) {
            ItemStack after = before.copy();
            after.set(ModDataComponents.HEALTH_ANALYZER.get(), new HealthAnalyzerStackData(old.scannerId(),
                    decision == HealthAnalyzerActivationPolicy.Decision.TOGGLE_ON_OPEN));
            if (BodyHeldItemMutation.commit(actor, authority, hands, handId, token, revision, before, after)
                    != BodyHeldItemMutation.Result.SUCCESS) return BodyHandActionResult.Reason.DENIED;
        }
        if (decision == HealthAnalyzerActivationPolicy.Decision.TOGGLE_OFF) {
            SESSIONS.remove(old.scannerId());
            sendInactive(old.scannerId(), actor, authority.body().getUUID());
        }
        openUI(old.scannerId(), actor, authority.body().getUUID());
        return BodyHandActionResult.Reason.OK;
    }

    /** Starts a scan against the exact target UUID after validating the current active-hand proof. */
    public static BodyHandActionResult.Reason scan(ServerPlayer actor, String handId, String token,
                                                   long revision, long sequence, UUID targetId) {
        HandActorAuthority.Snapshot authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null) return BodyHandActionResult.Reason.NO_AUTHORITY;
        LivingEntity body = authority.body();
        if (!intent(body, actor, handId, token, revision, authority)) return BodyHandActionResult.Reason.DENIED;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack stack = hands.stackCopy(handId).orElse(ItemStack.EMPTY);
        MedicalItemData data = analyzer(body.level(), stack);
        if (data == null || stack.getCount() != 1) return BodyHandActionResult.Reason.DENIED;
        LivingEntity target = targetId == null ? null : resolveTarget(body, targetId);
        if (target == null || !reachable(body, target, data.healthAnalyzer().orElseThrow().scanRange()))
            return BodyHandActionResult.Reason.DENIED;
        HealthAnalyzerStackData stackData = stack.get(ModDataComponents.HEALTH_ANALYZER.get());
        if (stackData == null) {
            stackData = HealthAnalyzerStackData.create();
            ItemStack initialized = stack.copy();
            initialized.set(ModDataComponents.HEALTH_ANALYZER.get(), stackData);
            if (BodyHeldItemMutation.commit(actor, authority, hands, handId, token, revision, stack, initialized)
                    != BodyHeldItemMutation.Result.SUCCESS) return BodyHandActionResult.Reason.DENIED;
            hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            stack = hands.stackCopy(handId).orElse(ItemStack.EMPTY);
            revision = hands.revision();
        }
        final String proofToken = token;
        String key = "scanner:" + stackData.scannerId() + ":target:" + target.getUUID();
        Session prior=SESSIONS.get(stackData.scannerId());
        Set<Subscriber> subscribers=SUBSCRIBERS.get(stackData.scannerId());
        Subscriber open=subscribers==null?null:subscribers.stream()
                .filter(s->s.actor()==actor&&s.actorId().equals(body.getUUID())).findFirst().orElse(null);
        if(open!=null && prior!=null && prior.target().getUUID().equals(target.getUUID())) {
            closeUI(actor,stackData.scannerId(),open.epoch(),open.actorId());
            // A stray pending duplicate can coexist with a retained live session; cancel it too,
            // but never restart scanning merely because the window was dismissed.
            TimedInteractionSystem.cancelDuplicate(body,KIND,key);
            return BodyHandActionResult.Reason.OK;
        }
        if (TimedInteractionSystem.cancelDuplicate(body, KIND, key)) return BodyHandActionResult.Reason.OK;
        var policy = data.healthAnalyzer().orElseThrow();
        int ticks = Math.max(1, (int) Math.ceil(policy.scanDelay() * 20));
        AtomicLong expectedRevision = new AtomicLong(revision);
        BooleanSupplier valid = () -> intent(body, actor, handId, proofToken, expectedRevision.get(), authority)
                && targetValid(body, target, policy.scanRange());
        var result = TimedInteractionSystem.start(body, actor, sequence, KIND, key, ticks, false, true,
                TARGET_REACH, valid, () -> {
                    if (!valid.getAsBoolean()) return TimedInteractionSystem.Completion.STOP;
                    LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    ItemStack before = current == null ? ItemStack.EMPTY : current.stackCopy(handId).orElse(ItemStack.EMPTY);
                    MedicalItemData currentData = analyzer(body.level(), before);
                    if (currentData == null || before.getCount() != 1) return TimedInteractionSystem.Completion.STOP;
                    HealthAnalyzerStackData old = before.getOrDefault(ModDataComponents.HEALTH_ANALYZER.get(), HealthAnalyzerStackData.create());
                    ItemStack after = before.copy();
                    after.set(ModDataComponents.HEALTH_ANALYZER.get(), new HealthAnalyzerStackData(old.scannerId(), true));
                    var mutation = BodyHeldItemMutation.commit(actor, authority, current, handId, proofToken,
                            current.revision(), before, after);
                    if (mutation != BodyHeldItemMutation.Result.SUCCESS) return mutation == BodyHeldItemMutation.Result.RECOVERY_REQUIRED
                            ? TimedInteractionSystem.Completion.RECOVERY_REQUIRED : TimedInteractionSystem.Completion.STOP;
                    LiveHands committed = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (committed == null) return TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
                    expectedRevision.set(committed.revision());
                    SESSIONS.put(old.scannerId(), new Session(actor, authority, old.scannerId(), target,
                            currentData.healthAnalyzer().orElseThrow(), sequence));
                    openUI(old.scannerId(), actor, authority.body().getUUID());
                    send(old.scannerId(), actor, authority, target, true, true);
                    currentData.healthAnalyzer().orElseThrow().scanSound().flatMap(BuiltInRegistries.SOUND_EVENT::getOptional)
                            .ifPresent(sound -> ((ServerLevel) body.level()).playSound(null, body.getX(), body.getY(), body.getZ(), sound, SoundSource.PLAYERS, 1f, 1f));
                    return TimedInteractionSystem.Completion.STOP;
                });
        return result == TimedInteractionEngine.StartResult.STARTED || result == TimedInteractionEngine.StartResult.DUPLICATE_CANCELLED
                ? BodyHandActionResult.Reason.OK : BodyHandActionResult.Reason.DENIED;
    }

    /** Explicit close unsubscribes the actor only; it does not stop the linked scanner. */
    public static void closeUI(ServerPlayer actor, UUID scannerId, long epoch, UUID actorId) {
        if (actor == null || scannerId == null || actorId == null || !(actor.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return;
        HandActorAuthority.Snapshot authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null || !authority.body().getUUID().equals(actorId)
                || !HandActorAuthority.revalidate(actor, authority)) return;
        Set<Subscriber> users = SUBSCRIBERS.get(scannerId);
        if (users != null && users.removeIf(s -> s.actor() == actor && s.epoch() == epoch && s.actorId().equals(actorId)))
            sendClosed(scannerId, actor, epoch, actorId);
        if (users != null && users.isEmpty()) SUBSCRIBERS.remove(scannerId);
    }

    private static void openUI(UUID scanner, ServerPlayer actor, UUID actorId) {
        HandActorAuthority.resolve(actor).filter(a -> a.body().getUUID().equals(actorId))
                .ifPresent(a -> ROOTS.putIfAbsent(scanner, new ScannerRoot((ServerLevel)a.body().level(), actorId, false)));
        ROOT_WATCHERS.computeIfAbsent(scanner, ignored -> new HashSet<>()).add(actor);
        Set<Subscriber> users = SUBSCRIBERS.computeIfAbsent(scanner, ignored -> new HashSet<>());
        Subscriber subscriber = users.stream().filter(s -> s.actor() == actor && s.actorId().equals(actorId)).findFirst().orElse(null);
        long epoch = subscriber == null ? EPOCHS.merge(scanner, 1L, Long::sum) : subscriber.epoch();
        if (subscriber == null) users.add(new Subscriber(actor, epoch, actorId));
        long frame = FRAMES.merge(scanner, 1L, Long::sum);
        Session linked = SESSIONS.get(scanner);
        Optional<HealthAnalyzerSnapshot> current = linked == null ? Optional.empty()
                : Optional.of(HealthAnalyzerReadings.read(scanner, epoch, actorId, linked.target(), frame,
                    true, withinRange(linked.authority().body(), linked.target(), linked.policy().scanRange())));
        PacketDistributor.sendToPlayer(actor, new HealthAnalyzerWindowUpdate(new HealthAnalyzerWindowState(
                scanner, epoch, actorId, frame, true, current)));
    }

    /** Called after an authoritative body ownership transfer; a world UUID identifies an exact drop. */
    public static void onWorldTransfer(ServerPlayer actor, UUID scannerId, UUID worldEntityId) {
        if (actor == null || scannerId == null || !(actor.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return;
        boolean drop = worldEntityId != null;
        boolean validWorldItem = false;
        if (drop && level.getEntity(worldEntityId) instanceof net.minecraft.world.entity.item.ItemEntity item) {
            ItemStack stack = item.getItem();
            HealthAnalyzerStackData data = stack.get(ModDataComponents.HEALTH_ANALYZER.get());
            if (data != null && data.scannerId().equals(scannerId)) {
                ItemStack inactive = stack.copy(); inactive.set(ModDataComponents.HEALTH_ANALYZER.get(), new HealthAnalyzerStackData(scannerId, false));
                item.setItem(inactive);
                validWorldItem = true;
            }
        }
        SESSIONS.remove(scannerId);
        deactivateBody(actor, scannerId);
        UUID bodyId = HandActorAuthority.resolve(actor).map(a -> a.body().getUUID()).orElse(null);
        var selectedRoot = HealthAnalyzerLifecycle.transferRoot(bodyId, worldEntityId, drop, validWorldItem);
        if (selectedRoot == null) ROOTS.remove(scannerId);
        else ROOTS.put(scannerId, new ScannerRoot(level, selectedRoot.entity(), selectedRoot.worldItem()));
    }

    private static void deactivateBody(ServerPlayer actor, UUID scannerId) {
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null) return;
        LivingEntity body = authority.body();
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (current == null || !current.compatible(body)) return;
        current.deactivateHealthAnalyzer(current.revision(), scannerId).ifPresent(updated -> {
            LiveHandsCommitGateway.commitOwned(body, updated);
            com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.requireFreshQuery(actor);
        });
    }

    private static void closeWatcher(UUID scannerId, ServerPlayer actor) {
        Set<Subscriber> users = SUBSCRIBERS.get(scannerId);
        if (users == null) return;
        for (Subscriber user : List.copyOf(users)) if (user.actor() == actor) {
            users.remove(user); sendClosed(scannerId, actor, user.epoch(), user.actorId());
        }
        Set<ServerPlayer> watchers = ROOT_WATCHERS.get(scannerId);
        if (watchers != null) watchers.remove(actor);
    }

    @SubscribeEvent
    public static void onPostServerTick(ServerTickEvent.Post event) {
        // Enforce UI range every tick, independent from scan sampling cadence.
        for (var entry : List.copyOf(SUBSCRIBERS.entrySet())) for (Subscriber sub : List.copyOf(entry.getValue())) {
            ScannerRoot root = ROOTS.get(entry.getKey());
            var authority = HandActorAuthority.resolve(sub.actor()).orElse(null);
            if (root == null || root.level().getServer() != event.getServer() || authority == null
                    || !authority.body().getUUID().equals(sub.actorId())) { closeWatcher(entry.getKey(), sub.actor()); continue; }
            net.minecraft.world.entity.Entity physical = HealthAnalyzerLifecycle.loadedRoot(root.level(), root.entity());
            if (physical == null) { closeWatcher(entry.getKey(), sub.actor()); continue; }
            ItemStack scannerStack;
            if (root.worldItem()) {
                if (!(physical instanceof net.minecraft.world.entity.item.ItemEntity item)) { closeWatcher(entry.getKey(), sub.actor()); continue; }
                scannerStack = item.getItem();
            } else {
                var source = HealthAnalyzerSources.findInBody(authority.body(), entry.getKey());
                if (source.isEmpty()) { closeWatcher(entry.getKey(), sub.actor()); continue; }
                scannerStack = source.get().stack();
            }
            MedicalItemData policy = analyzer(root.level(), scannerStack);
            double range = policy == null ? 0 : policy.ui().orElseThrow().range();
            if (authority.body().level() != root.level() || !HealthAnalyzerLifecycle.inUiRange(authority.body().position(), physical.position(), range))
                closeWatcher(entry.getKey(), sub.actor());
        }
        for (var entry : List.copyOf(SESSIONS.entrySet())) {
            Session session = entry.getValue();
            if (!(session.actor().level() instanceof ServerLevel level) || level.getServer() != event.getServer()) continue;
            var source = HealthAnalyzerSources.findInBody(session.authority().body(), session.scanner());
            if (source.isEmpty() || source.orElseThrow().kind() == HealthAnalyzerSources.Kind.STORED) {
                SESSIONS.remove(entry.getKey());
                deactivateBody(session.actor(), session.scanner());
                sendInactive(entry.getKey(), session.actor(), session.authority().body().getUUID());
                continue;
            }
            if (!HandActorAuthority.revalidate(session.actor(), session.authority()) || !loadedTarget(session.authority().body(), session.target())) {
                SESSIONS.remove(entry.getKey());
                sendInactive(entry.getKey(), session.actor(), session.authority().body().getUUID());
                continue;
            }
            long now = level.getServer().overworld().getGameTime();
            if (now % Math.max(1, (long) Math.ceil(session.policy().updateInterval() * 20)) == 0)
                send(entry.getKey(), session.actor(), session.authority(), session.target(),
                        withinRange(session.authority().body(), session.target(), session.policy().scanRange()),
                        withinRange(session.authority().body(), session.target(), session.policy().scanRange()));
        }
    }

    private static void send(UUID scanner, ServerPlayer actor, HandActorAuthority.Snapshot authority,
                             LivingEntity target, boolean active, boolean inRange) {
        UUID actorId = authority.body().getUUID();
        Set<Subscriber> users = SUBSCRIBERS.get(scanner);
        if (users == null) return;
        Subscriber subscriber = users.stream().filter(s -> s.actor() == actor && s.actorId().equals(actorId)).findFirst().orElse(null);
        if (subscriber == null) return;
        long epoch = subscriber.epoch();
        long frame = FRAMES.merge(scanner, 1L, Long::sum);
        var snapshot = HealthAnalyzerReadings.read(scanner, epoch, actorId, target, frame, active, inRange);
        if (!inRange) {
            var previous = LAST_READINGS.get(subscriber);
            if (previous != null) snapshot = HealthAnalyzerLifecycle.paused(previous, frame, active, false);
        } else LAST_READINGS.put(subscriber, snapshot);
        PacketDistributor.sendToPlayer(actor, new HealthAnalyzerWindowUpdate(new HealthAnalyzerWindowState(scanner, epoch, actorId, frame, true, Optional.of(snapshot))));
    }

    private static void sendClosed(UUID scanner, ServerPlayer actor, long epoch, UUID actorId) {
        PacketDistributor.sendToPlayer(actor, new HealthAnalyzerWindowUpdate(new HealthAnalyzerWindowState(scanner, epoch, actorId,
                FRAMES.merge(scanner, 1L, Long::sum), false, Optional.empty())));
    }

    private static void sendInactive(UUID scanner, ServerPlayer actor, UUID actorId) {
        Set<Subscriber> users = SUBSCRIBERS.get(scanner);
        if (users == null) return;
        for (Subscriber subscriber : List.copyOf(users)) {
            if (subscriber.actor().isRemoved()) { users.remove(subscriber); continue; }
            long frame = FRAMES.merge(scanner, 1L, Long::sum);
            PacketDistributor.sendToPlayer(subscriber.actor(), new HealthAnalyzerWindowUpdate(
                    new HealthAnalyzerWindowState(scanner, subscriber.epoch(), subscriber.actorId(), frame, true, Optional.empty())));
        }
    }

    private static boolean intent(LivingEntity body, ServerPlayer actor, String hand, String token, long revision,
                                  HandActorAuthority.Snapshot authority) {
        if (body == null || actor == null || hand == null || token == null || revision < 0
                || !(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(body)
                || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body) return false;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return hands != null && hands.compatible(body) && hands.revision() == revision && hands.activeHand().equals(hand)
                && hands.token(hand).filter(token::equals).isPresent();
    }

    private static MedicalItemData analyzer(net.minecraft.world.level.Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            MedicalItemData data = ModMedicalItems.require(ModMedicalItems.catalog(level), id);
            return data.healthAnalyzer().isPresent() ? data : null;
        } catch (RuntimeException missing) { return null; }
    }

    private static LivingEntity resolveTarget(LivingEntity body, UUID id) {
        if (!(body.level() instanceof ServerLevel level)) return null;
        var entity = level.getEntity(id);
        return entity instanceof LivingEntity living && !living.isRemoved() ? living : null;
    }
    private static boolean loadedTarget(LivingEntity body, LivingEntity target) {
        return target != null && !target.isRemoved() && target.isAddedToLevel() && target.level() == body.level()
                && body.level() instanceof ServerLevel level && level.getEntity(target.getUUID()) == target
                && com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.policy(target) != null;
    }
    private static boolean withinRange(LivingEntity body, LivingEntity target, double range) {
        return loadedTarget(body, target) && HealthAnalyzerLifecycle.inResolvedRange(body.position(), target.position(), range);
    }
    private static boolean targetValid(LivingEntity body, LivingEntity target, double range) {
        return loadedTarget(body, target)
                && reachable(body, target, range);
    }
    private static boolean reachable(LivingEntity body, LivingEntity target, double range) {
        if (body.level() != target.level() || !HealthAnalyzerLifecycle.inResolvedRange(body.position(), target.position(), range)
                || !(body.level() instanceof ServerLevel level)) return false;
        var from = body.getEyePosition(); var to = target.getEyePosition();
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 4));
        if (steps > 32) return false;
        for (int i = 0; i <= steps; i++) if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(from.lerp(to, (double) i / steps)))) return false;
        return level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, body)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
}

