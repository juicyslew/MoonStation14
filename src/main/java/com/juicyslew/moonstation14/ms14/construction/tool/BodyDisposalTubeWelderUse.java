package com.juicyslew.moonstation14.ms14.construction.tool;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/** Timed, admitted body-hand welder action for dismantling an exposed disposal segment. */
public final class BodyDisposalTubeWelderUse {
    private static final Map<MinecraftServer, Map<UUID, Pending>> PENDING = new WeakHashMap<>();
    private BodyDisposalTubeWelderUse() { }
    public static void register(IEventBus bus) {
        bus.addListener(BodyDisposalTubeWelderUse::tick);
        bus.addListener(BodyDisposalTubeWelderUse::damage);
        bus.addListener(BodyDisposalTubeWelderUse::stop);
    }

    /** Called only by the already admitted primary-use body-hit route. */
    public static boolean begin(ServerPlayer actor, BodyBlockUseRequest request,
                                BodyHandRequestService.BlockUseAdmission admission) {
        if (actor == null || request == null || admission == null || request.intent() != BodyBlockUseRequest.Intent.USE) return false;
        var authority = admission.authority(); LiveHands hands = admission.hands(); LivingEntity body = authority.body();
        if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || request.expectedRevision() != hands.revision() || !request.handId().equals(hands.activeHand())
                || !authority.handIds().contains(request.handId()) || !request.target().equals(admission.hit().pos())
                || request.face() != admission.hit().face()) return false;
        ItemStack stack = hands.stackCopy(request.handId()).orElse(null);
        if (stack == null || !stack.is(ModItems.WELDER) || !hands.token(request.handId()).filter(request.expectedToken()::equals).isPresent()
                || !HandActorAuthority.revalidate(actor, authority)
                || body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != hands
                || !WelderFuelService.canWeld(stack) || !exposed(level, request.target())
                || BodyBlockUseTargetProbe.probe(level, body, request.target(), request.face()) == null) return false;
        var manager = PrototypeRuntime.serverManager();
        if (!manager.registeredTypes().contains(ModTools.TOOL_TYPE)) return false;
        var catalog = manager.snapshot(ModTools.TOOL_TYPE);
        if (!ModTools.hasQuality(catalog, ModTools.WELDER, ToolPrototypeData.Quality.Welding)) return false;
        var speed = ModTools.speedModifier(catalog, ModTools.WELDER, ToolPrototypeData.Quality.Welding);
        if (speed.isEmpty()) return false;
        DisposalTubeWorldData world = DisposalTubeWorldData.existing(level).orElse(null);
        if (world == null) return false;
        var lookup = world.lookup(level, request.target());
        if (lookup.kind() != DisposalTubeWorldData.LookupKind.PRESENT || lookup.record() == null) return false;
        long deadline;
        try { deadline = Math.addExact(level.getGameTime(), delayTicks(speed.orElseThrow())); }
        catch (ArithmeticException e) { return false; }
        PENDING.computeIfAbsent(level.getServer(), ignored -> new HashMap<>()).put(body.getUUID(), new Pending(actor, authority,
                level, world, lookup.record(), request.face(), request.handId(), request.expectedToken(), hands.revision(), stack,
                body.position(), speed.orElseThrow(), deadline));
        return true;
    }
    static long delayTicks(double speed) {
        if (!Double.isFinite(speed) || speed <= 0) throw new IllegalArgumentException("invalid tool speed");
        return Math.max(1, (long)Math.ceil(20 / speed));
    }
    private static void tick(ServerTickEvent.Post event) {
        var server = event.getServer(); var actions = PENDING.get(server); if (actions == null) return;
        for (Pending a : new ArrayList<>(actions.values())) {
            UUID id = a.authority.body().getUUID(); if (actions.get(id) != a) continue;
            if (!valid(a)) { actions.remove(id, a); continue; }
            if (a.level.getGameTime() < a.deadline) continue;
            // Re-evaluate fuel at the completion instant; ordinary passive burn does not alter the hand token/revision.
            LiveHands hands = a.authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            ItemStack current = hands.stackCopy(a.hand).orElse(ItemStack.EMPTY);
            if (!WelderFuelService.canWeld(current)) { actions.remove(id, a); continue; }
            ItemStack steel = new ItemStack(ModItems.STEEL.get(), 2);
            ItemEntity staged;
            BlockPos target = a.record.pos();
            try { staged = new ItemEntity(a.level, target.getX()+.5, target.getY()+.25, target.getZ()+.5, steel); staged.setUnlimitedLifetime(); }
            catch (RuntimeException ex) { actions.remove(id, a); continue; }
            boolean added;
            try { added = a.level.addFreshEntity(staged); } catch (RuntimeException ex) { added = false; }
            if (!added || a.level.getEntity(staged.getUUID()) != staged || !staged.isAddedToLevel()
                    || staged.isRemoved() || !staged.getItem().equals(steel)) { actions.remove(id, a); continue; }
            var latest = a.world.lookup(a.level, a.record.pos());
            boolean removed = latest.kind() == DisposalTubeWorldData.LookupKind.PRESENT
                    && latest.record().equals(a.record) && exposed(a.level, a.record.pos())
                    && a.world.remove(a.level, a.record, pos -> exposed(a.level, pos));
            if (!removed && a.level.getEntity(staged.getUUID()) == staged) staged.discard();
            actions.remove(id, a);
        }
        if (actions.isEmpty()) PENDING.remove(server);
    }
    private static boolean valid(Pending a) {
        LivingEntity body = a.authority.body(); ServerPlayer actor = a.actor;
        if (a.level.isClientSide || a.level.getServer() == null || a.level.getEntity(body.getId()) != body || body.isRemoved()
                || !body.isAlive() || !body.isAddedToLevel() || !actor.isAlive() || actor.connection == null
                || actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor
                || !HandActorAuthority.revalidate(actor, a.authority) || !body.position().equals(a.origin)) return false;
        LiveHands hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body) || hands.revision() != a.revision || !a.hand.equals(hands.activeHand())
                || !hands.token(a.hand).filter(a.token::equals).isPresent()) return false;
        ItemStack stack = hands.stackCopy(a.hand).orElse(null);
        if (stack == null || !stack.is(ModItems.WELDER) || !ItemStack.isSameItemSameComponents(stack,a.tool)
                || stack.getCount() != a.tool.getCount() || !WelderFuelService.canWeld(stack)) return false;
        var manager = PrototypeRuntime.serverManager();
        if (!manager.registeredTypes().contains(ModTools.TOOL_TYPE)) return false;
        var catalog = manager.snapshot(ModTools.TOOL_TYPE);
        return ModTools.speedModifier(catalog, ModTools.WELDER, ToolPrototypeData.Quality.Welding)
                .filter(s -> Double.compare(s,a.speed)==0).isPresent()
                && exposed(a.level,a.record.pos()) && BodyBlockUseTargetProbe.probe(a.level,body,a.record.pos(),a.face)!=null
                && a.world.lookup(a.level,a.record.pos()).record()!=null
                && a.record.equals(a.world.lookup(a.level,a.record.pos()).record());
    }
    private static void damage(LivingDamageEvent.Post e) { if (e.getNewDamage()>0 && e.getEntity().level() instanceof ServerLevel l) { var m=PENDING.get(l.getServer()); if(m!=null)m.remove(e.getEntity().getUUID()); } }
    private static void stop(ServerStoppedEvent e) { PENDING.remove(e.getServer()); }
    private static boolean exposed(ServerLevel l, BlockPos p) { LevelChunk c=l.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4); return c!=null&&c.getFullStatus().isOrAfter(FullChunkStatus.FULL)&&DisposalTubeWorldData.existing(l).map(data->data.lookup(l,p).kind()==DisposalTubeWorldData.LookupKind.PRESENT).orElse(false); }
    private record Pending(ServerPlayer actor, HandActorAuthority.Snapshot authority, ServerLevel level, DisposalTubeWorldData world,
                           DisposalTubeRecord record, net.minecraft.core.Direction face, String hand, String token, long revision,
                           ItemStack tool, Vec3 origin, double speed, long deadline) {
        private Pending { tool=tool.copy(); }
    }
}
