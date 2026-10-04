package com.juicyslew.moonstation14.ms14.construction.tool;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.ui.DisposalBuildMenu;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/** Server-authoritative, timed rotation of an existing disposal tube. */
public final class BodyDisposalTubeRotationUse {
    private static final Map<MinecraftServer, Map<UUID, Pending>> PENDING = new WeakHashMap<>();
    private BodyDisposalTubeRotationUse() { }

    public static void register(IEventBus bus) {
        bus.addListener(BodyDisposalTubeRotationUse::tick);
        bus.addListener(BodyDisposalTubeRotationUse::damage);
        bus.addListener(BodyDisposalTubeRotationUse::stopped);
    }

    public static boolean beginConfirmedRotation(ServerPlayer actor, DisposalBuildMenu menu,
            DisposalTubeRecord exact, DisposalOrientation targetFrame) {
        if (actor == null || menu == null || exact == null || targetFrame == null
                || actor.containerMenu != menu || !menu.editing() || !menu.stillValid(actor)
                || menu.tubeAnchored() || !exact.id().equals(menu.configuredTubeId())
                || exact.revision() != menu.configuredTubeRevision() || exact.anchored()
                || !exact.pos().equals(menu.pos()) || !(actor.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return false;
        var authority = menu.authority();
        if (authority == null || !HandActorAuthority.revalidateInMenu(actor, authority, menu)) return false;
        LivingEntity body = authority.body();
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body)) return false;
        String hand = hands.activeHand();
        String token = hand == null ? null : hands.token(hand).orElse(null);
        ItemStack tool = hand == null ? null : hands.stackCopy(hand).orElse(null);
        if (tool == null || token == null || !tool.is(com.juicyslew.moonstation14.item.ModItems.SCREWDRIVER.get())) return false;
        var manager = PrototypeRuntime.serverManager();
        if (!manager.registeredTypes().contains(ModTools.TOOL_TYPE)) return false;
        var catalog = manager.snapshot(ModTools.TOOL_TYPE);
        if (!ModTools.hasQuality(catalog, ModTools.SCREWDRIVER, ToolPrototypeData.Quality.Screwing)) return false;
        double speed = ModTools.speedModifier(catalog, ModTools.SCREWDRIVER, ToolPrototypeData.Quality.Screwing).orElse(0.0);
        if (!Double.isFinite(speed) || speed <= 0) return false;
        long deadline;
        try { deadline = Math.addExact(level.getGameTime(), requiredTicks(speed)); }
        catch (ArithmeticException overflow) { return false; }
        var current = DisposalTubeWorldData.existing(level).map(d -> d.lookup(level, exact.pos()).record()).orElse(null);
        if (!same(current, exact) || exact.anchored()) return false;
        Pending action = new Pending(actor, authority, menu, level, exact, targetFrame, hands, hand, token,
                hands.revision(), tool, body.position(), speed, deadline);
        PENDING.computeIfAbsent(level.getServer(), ignored -> new HashMap<>()).put(body.getUUID(), action);
        menu.result("Rotation started");
        return true;
    }

    static boolean same(DisposalTubeRecord a, DisposalTubeRecord b) {
        return a != null && b != null && a.id().equals(b.id()) && a.revision() == b.revision()
                && a.prototypeId().equals(b.prototypeId()) && a.frame().equals(b.frame())
                && a.pos().equals(b.pos()) && a.hostKind() == b.hostKind()
                && Objects.equals(a.hostGeneration(), b.hostGeneration()) && !a.anchored();
    }

    static long requiredTicks(double speed) {
        if (!Double.isFinite(speed) || speed <= 0) throw new IllegalArgumentException("invalid tool speed");
        return Math.max(1L, (long)Math.ceil(20.0 / speed));
    }

    private static void tick(ServerTickEvent.Post event) {
        var actions = PENDING.get(event.getServer()); if (actions == null) return;
        for (Pending p : new ArrayList<>(actions.values())) {
            UUID id = p.authority.body().getUUID(); if (actions.get(id) != p) continue;
            if (!valid(p)) { p.menu.result("Rotation cancelled"); cancel(actions, id, p); continue; }
            if (p.level.getGameTime() < p.deadline) continue;
            var data = DisposalTubeWorldData.existing(p.level).orElse(null);
            var now = data == null ? null : data.lookup(p.level, p.exact.pos()).record();
            if (data == null || !same(now, p.exact) || !geometryClear(p)
                    || !data.rotate(p.level, now, p.frame, pos -> DisposalTubeWorldData.existing(p.level)
                    .map(d -> d.lookup(p.level, pos).kind() == DisposalTubeWorldData.LookupKind.PRESENT).orElse(false))) {
                p.menu.result("Target changed"); cancel(actions, id, p); continue;
            }
            var after = data.lookup(p.level, p.exact.pos()).record();
            if (after == null || !after.id().equals(p.exact.id()) || !after.prototypeId().equals(p.exact.prototypeId())
                    || after.revision() != p.exact.revision() + 1 || !after.frame().equals(p.frame) || after.anchored()) {
                p.menu.result("Target changed"); cancel(actions, id, p); continue;
            }
            p.menu.rotated(after.id(), after.revision(), after.frame()); p.menu.result("Rotation updated");
            actions.remove(id, p);
        }
        if (actions.isEmpty()) PENDING.remove(event.getServer());
    }

    private static boolean valid(Pending p) {
        if (p.actor.containerMenu != p.menu || !p.menu.stillValid(p.actor)
                || !HandActorAuthority.revalidateInMenu(p.actor, p.authority, p.menu)
                || !p.authority.body().position().equals(p.origin) || p.level.getEntity(p.authority.body().getUUID()) != p.authority.body()) return false;
        LiveHands hands = p.authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || hands.revision() != p.revision || !p.hand.equals(hands.activeHand())
                || hands.token(p.hand).filter(p.token::equals).isEmpty()
                || hands.stackCopy(p.hand).filter(s -> ItemStack.isSameItemSameComponents(s, p.tool)
                && s.getCount() == p.tool.getCount()).isEmpty()) return false;
        var manager = PrototypeRuntime.serverManager();
        if (!manager.registeredTypes().contains(ModTools.TOOL_TYPE)
                || ModTools.speedModifier(manager.snapshot(ModTools.TOOL_TYPE), ModTools.SCREWDRIVER,
                ToolPrototypeData.Quality.Screwing).filter(s -> Double.compare(s, p.speed) == 0).isEmpty()) return false;
        var data = DisposalTubeWorldData.existing(p.level).orElse(null);
        return data != null && same(data.lookup(p.level, p.exact.pos()).record(), p.exact);
    }

    private static boolean geometryClear(Pending p) {
        var resolved = PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(p.exact.prototypeId()));
        if (resolved == null || resolved.tube() == null || resolved.geometry() == null) return false;
        var bodyBox = p.authority.body().getBoundingBox();
        for (var local : resolved.geometry().boxes(resolved.tube(), p.frame)) {
            var box = local.move(p.exact.pos());
            if (box.intersects(bodyBox)) return false;
            for (var entity : p.level.getEntities(p.authority.body(), box.inflate(1.0e-7)))
                if (entity.getBoundingBox().intersects(box)) return false;
            int minX = (int)Math.floor(box.minX), maxX = (int)Math.floor(Math.nextDown(box.maxX));
            int minY = (int)Math.floor(box.minY), maxY = (int)Math.floor(Math.nextDown(box.maxY));
            int minZ = (int)Math.floor(box.minZ), maxZ = (int)Math.floor(Math.nextDown(box.maxZ));
            for (int x=minX;x<=maxX;x++) for (int y=minY;y<=maxY;y++) for (int z=minZ;z<=maxZ;z++) {
                var pos = new net.minecraft.core.BlockPos(x,y,z);
                if (pos.equals(p.exact.pos()) || p.exact.hostKind() == DisposalTubeRecord.HostKind.SEALED_WALL
                        && pos.asLong() == p.exact.pos().asLong()) continue;
                var chunk = p.level.getChunkSource().getChunk(x >> 4, z >> 4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk)) return false;
                for (var shape : p.level.getBlockState(pos).getCollisionShape(p.level, pos,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()).toAabbs())
                    if (shape.move(pos).intersects(box)) return false;
            }
        }
        return true;
    }

    private static void cancel(Map<UUID, Pending> map, UUID id, Pending p) { map.remove(id, p); }
    private static void damage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity().level() instanceof ServerLevel level)) return;
        var map = PENDING.get(level.getServer());
        if (map != null) {
            var pending = map.remove(event.getEntity().getUUID());
            if (pending != null) pending.menu.result("Rotation cancelled");
        }
    }
    private static void stopped(ServerStoppedEvent event) { PENDING.remove(event.getServer()); }

    private record Pending(ServerPlayer actor, HandActorAuthority.Snapshot authority, DisposalBuildMenu menu,
            ServerLevel level, DisposalTubeRecord exact, DisposalOrientation frame, LiveHands hands,
            String hand, String token, long revision, ItemStack tool, net.minecraft.world.phys.Vec3 origin,
            double speed, long deadline) {
        private Pending { tool = tool.copy(); }
    }
}
