package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.explosion.grenade.server.GrenadeDetonationService;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Ticks fuse state only while its persisted owner body is connected and authoritative. */
public final class BodyGrenadeHeldFuseService {
    private static final int PLAYERS_PER_TICK = 64;
    private static final Map<net.minecraft.server.MinecraftServer, Integer> CURSORS = new WeakHashMap<>();

    private BodyGrenadeHeldFuseService() { }

    public static void register(IEventBus bus) { bus.addListener(BodyGrenadeHeldFuseService::onPostServerTick); }

    public static void onPostServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        int start = Math.floorMod(CURSORS.getOrDefault(server, 0), players.size());
        int count = Math.min(PLAYERS_PER_TICK, players.size());
        CURSORS.put(server, (start + count) % players.size());
        for (int n = 0; n < count; n++) {
            ServerPlayer actor = players.get((start + n) % players.size());
            var authority = HandActorAuthority.resolveForOwnedItemTick(actor);
            if (authority.isEmpty()) continue;
            var snapshot = authority.orElseThrow();
            var expectedMenu = actor.containerMenu;
            LivingEntity body = authority.orElseThrow().body();
            if (!(actor.level() instanceof ServerLevel level) || body.level() != level
                    || level.getEntity(body.getUUID()) != body || body.isRemoved() || !body.isAlive()
                    || !body.isAddedToLevel() || !level.hasChunkAt(body.blockPosition())) continue;
            LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (hands == null || !hands.compatible(body)) continue;
            for (String hand : hands.handIds()) {
                // Earlier hand ticks can publish a new revision; always operate on the current snapshot.
                hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                if (hands == null || !hands.compatible(body)) break;
                tickHand(body, level, hands, hand,
                        () -> HandActorAuthority.revalidateForOwnedItemTick(actor, snapshot, expectedMenu));
            }
        }
    }

    static void tickHand(LivingEntity body, ServerLevel level, LiveHands before, String hand,
                         java.util.function.BooleanSupplier authorityValid) {
        ItemStack source = before.stackCopy(hand).orElse(null);
        if (source == null || !source.has(ModDataComponents.GRENADE_FUSE.get()) || source.getCount() != 1) return;
        GrenadeFuseData data = source.get(ModDataComponents.GRENADE_FUSE.get());
        if (data == null || data.phase() != GrenadeFuse.Phase.ARMED) return;
        GrenadeFuse.Evaluation evaluation;
        try { evaluation = data.toFuse().evaluate(level.getGameTime()); }
        catch (IllegalArgumentException | IllegalStateException invalid) { return; }
        if (GrenadeFuseData.fromFuse(evaluation.state()).equals(data)) return;
        String tokenValue = before.token(hand).orElse(null);
        if (tokenValue == null) return;
        ItemToken token;
        try { token = new ItemToken(tokenValue); }
        catch (IllegalArgumentException invalid) { return; }

        if (evaluation.expired()) {
            net.minecraft.core.BlockPos blastOrigin = body.blockPosition();
            LiveHands.WholeResult removal = before.takeWhole(before.revision(), hand, token);
            if (!removal.succeeded() || !authorityValid.getAsBoolean()
                    || !publish(body, before, removal.state(), hand, null)) return;
            expireAtOrigin(blastOrigin, () -> preserveContainedPayloads(body, level),
                    origin -> GrenadeDetonationService.detonate(level, origin));
            return;
        }

        ItemStack replacement = source.copy();
        replacement.set(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.fromFuse(evaluation.state()));
        var next = evaluation.beepDue() && evaluation.state().beepedAtForty()
                ? before.advanceOwnedGrenadeBeep(before.revision(), hand, token, replacement)
                : before.replaceOwnedStack(before.revision(), hand, token, replacement);
        if (next.isEmpty() || !authorityValid.getAsBoolean()
                || !(evaluation.beepDue() && evaluation.state().beepedAtForty()
                ? publishBeep(body, before, next.orElseThrow(), hand, replacement)
                : publish(body, before, next.orElseThrow(), hand, replacement))) return;
        if (evaluation.beepDue()) level.playSound(null, body.blockPosition(), ModSounds.GRENADE_BEEP.get(),
                SoundSource.PLAYERS, 0.35F, 1.0F);
    }

    static void preserveContainedPayloads(LivingEntity body, ServerLevel level) {
        ContainedBodyOwner owner = BodyContainmentService.forLevel(level).ownerOf(body).orElse(null);
        if (owner != null) owner.preserveContentsBeforeExplosion();
    }

    static void expireAtOrigin(net.minecraft.core.BlockPos origin, Runnable preserve,
                               java.util.function.Consumer<net.minecraft.core.BlockPos> detonate) {
        if (origin == null || preserve == null || detonate == null) return;
        preserve.run();
        detonate.accept(origin);
    }

    private static boolean publishBeep(LivingEntity body, LiveHands before, LiveHands next, String hand,
                                       ItemStack expected) {
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body))
            return false;
        body.setData(ModDataAttachments.LIVE_HANDS.get(), next);
        LiveHands readback = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return readback == next && readback.compatible(body) && readback.revision() == before.revision()
                && readback.token(hand).equals(before.token(hand)) && readback.stackCopy(hand)
                .filter(stack -> stack.getCount() == 1 && ItemStack.isSameItemSameComponents(stack, expected)).isPresent();
    }

    private static boolean publish(LivingEntity body, LiveHands before, LiveHands next, String hand, ItemStack expected) {
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body)
                || before.revision() == Long.MAX_VALUE) return false;
        body.setData(ModDataAttachments.LIVE_HANDS.get(), next);
        LiveHands readback = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (readback != next || !readback.compatible(body) || readback.revision() != before.revision() + 1) return false;
        if (expected == null) return readback.token(hand).isEmpty() && readback.stackCopy(hand).isEmpty();
        return readback.token(hand).equals(before.token(hand)) && readback.stackCopy(hand)
                .filter(stack -> stack.getCount() == 1 && ItemStack.isSameItemSameComponents(stack, expected)).isPresent();
    }
}
