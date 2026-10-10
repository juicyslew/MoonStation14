package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.devicenet.*;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.entities.LooseItemEntity;

import java.util.*;

/** Server-authoritative one-shot source endpoint for held remote signallers. */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class RemoteSignallerServer {
    private static final Map<DeviceIdentity, Long> NEXT_USE = new HashMap<>();
    private static final Map<DeviceIdentity, ServerLevel> PENDING_ENDPOINTS = new HashMap<>();
    private RemoteSignallerServer() { }

    public static void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        NEXT_USE.clear();
        for (var entry : List.copyOf(PENDING_ENDPOINTS.entrySet()))
            if (entry.getValue().getServer() == event.getServer()) PENDING_ENDPOINTS.remove(entry.getKey());
    }

    /** Ensure a loose signaller's persistent stack identity is published as a loaded endpoint. */
    public static boolean prepareLooseEndpoint(LooseItemEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !looseEntityValid(entity, level)) return false;
        ItemStack original = entity.getItem();
        if (original == null || original.isEmpty() || original.getCount() != 1 || entity.getTarget() != null) return false;
        NetworkDeviceItemData policy = ModNetworkDeviceItems.resolve(original, false).filter(p -> p.signallerPort().isPresent()).orElse(null);
        if (policy == null) return false;
        RemoteSignallerStackData state = original.get(ModDataComponents.REMOTE_SIGNALLER.get());
        try { state = ensureAddress(policy,state,level); }
        catch (RuntimeException invalid) { return false; }
        if (!state.equals(original.get(ModDataComponents.REMOTE_SIGNALLER.get()))) {
            ItemStack updated = original.copy();
            updated.set(ModDataComponents.REMOTE_SIGNALLER.get(), state);
            if (!looseEntityValid(entity, level) || !sameStack(original, entity.getItem())) return false;
            try { entity.setItem(updated); } catch (RuntimeException failure) { return false; }
            if (!sameStack(updated, entity.getItem())) return false;
        }
        final RemoteSignallerStackData published = state;
        if (!looseEntityValid(entity, level) || !matchesStackIdentity(entity.getItem(), published)) return false;
        DeviceComposition composition;
        try { composition = signallerComposition(policy,published); }
        catch (RuntimeException invalid) { return false; }
        DeviceEndpoint base = portableEndpoint(published.identity(), level, entity::blockPosition, composition, original.getHoverName().getString(), () -> looseEntityValid(entity, level) && matchesStackIdentity(entity.getItem(), published));
        DeviceEndpoint guarded = new DeviceEndpoint() {
            @Override public DeviceIdentity identity() { return base.identity(); }
            @Override public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension() { return base.dimension(); }
            @Override public net.minecraft.core.BlockPos position() { return base.position(); }
            @Override public DeviceComposition composition() { return base.composition(); }
            @Override public boolean ownsAddressOutsideWorldSavedData() { return true; }
            @Override public boolean available() { return base.available(); }
            @Override public void receive(DevicePayload payload, String sourceAddress) { if (available()) base.receive(payload, sourceAddress); }
        };
        if (!guarded.available()) return false;
        return DeviceNetworkRuntime.forLevel(level).loaded(guarded);
    }

    private static boolean looseEntityValid(LooseItemEntity entity, ServerLevel level) {
        return entity.level() == level && !entity.isRemoved() && entity.isAlive() && entity.isAddedToLevel()
                && level.getEntity(entity.getUUID()) == entity;
    }
    private static boolean matchesStackIdentity(ItemStack stack, RemoteSignallerStackData expected) {
        return stack != null && !stack.isEmpty() && stack.getCount() == 1
                && expected.equals(stack.get(ModDataComponents.REMOTE_SIGNALLER.get()));
    }
    private static boolean sameStack(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    public static BodyHandActionResult.Reason activate(ServerPlayer actor, String hand, String token, long revision) {
        var authority=HandActorAuthority.resolve(actor).orElse(null);
        if(authority==null)return BodyHandActionResult.Reason.NO_AUTHORITY;
        LivingEntity body=authority.body();
        if(!(body.level() instanceof ServerLevel level)||!level.getServer().isSameThread()
                ||!HandActorAuthority.revalidate(actor,authority))return BodyHandActionResult.Reason.NO_AUTHORITY;
        LiveHands hands=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack before=hands==null?ItemStack.EMPTY:hands.stackCopy(hand).orElse(ItemStack.EMPTY);
        if(hands==null||!hands.compatible(body)||hands.revision()!=revision||!hands.activeHand().equals(hand)
                ||hands.token(hand).filter(token::equals).isEmpty()||before.isEmpty()||before.getCount()!=1)
            return BodyHandActionResult.Reason.DENIED;
        NetworkDeviceItemData policy=ModNetworkDeviceItems.resolve(before,false).filter(p->p.signallerPort().isPresent()).orElse(null);
        if(policy==null)return BodyHandActionResult.Reason.DENIED;
        RemoteSignallerStackData state=before.get(ModDataComponents.REMOTE_SIGNALLER.get());
        try { state=ensureAddress(policy,state,level); }
        catch(RuntimeException invalid){return BodyHandActionResult.Reason.DENIED;}
        if(!state.equals(before.get(ModDataComponents.REMOTE_SIGNALLER.get()))) {
            ItemStack after=before.copy(); after.set(ModDataComponents.REMOTE_SIGNALLER.get(),state);
            if(BodyHeldItemMutation.commit(actor,authority,hands,hand,token,revision,before,after)!=BodyHeldItemMutation.Result.SUCCESS)
                return BodyHandActionResult.Reason.DENIED;
            hands=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); before=hands.stackCopy(hand).orElse(ItemStack.EMPTY); revision=hands.revision();
        }
        DeviceComposition composition;
        try { composition=signallerComposition(policy,state); }
        catch(RuntimeException invalid){return BodyHandActionResult.Reason.DENIED;}
        LiveHands heldHands=hands;
        long heldRevision=revision;
        ItemStack heldStack=before.copy();
        RemoteSignallerStackData heldState=state;
        DeviceEndpoint source=portableEndpoint(state.identity(), level, body::blockPosition, composition, before.getHoverName().getString(), () -> {
            if (!HandActorAuthority.revalidate(actor, authority)) return false;
            LiveHands current=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            return current==heldHands && current!=null && current.compatible(body) && current.revision()==heldRevision
                    && current.activeHand().equals(hand) && current.token(hand).filter(token::equals).isPresent()
                    && current.stackCopy(hand).filter(stack -> sameStack(stack, heldStack)
                    && heldState.equals(stack.get(ModDataComponents.REMOTE_SIGNALLER.get()))).isPresent();
        });
        return activatePrepared(level, policy, state, source);
    }

    /** E-world adaptation: only the authenticated, exactly nominated loose stack is admitted. */
    public static BodyHandActionResult.Reason activateLoose(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands hands, com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest request,
            LooseItemEntity entity) {
        boolean emptyHandIntent = request.action()
                == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.ACTIVATE_TARGET;
        boolean activeHandProof = hands != null && hands.activeHand().equals(request.handId())
                && (emptyHandIntent
                    ? request.expectedToken() == null && hands.token(request.handId()).isEmpty()
                        && hands.stackCopy(request.handId()).filter(stack -> !stack.isEmpty()).isEmpty()
                        && hands.reservation(request.handId()).isEmpty()
                    : request.action() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.USE_TARGET
                        && hands.token(request.handId()).filter(request.expectedToken()::equals).isPresent()
                        && hands.stackCopy(request.handId()).filter(stack -> !stack.isEmpty()).isPresent());
        if (authority == null || !HandActorAuthority.revalidate(actor, authority)
                || !(authority.body().level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || hands == null || !hands.compatible(authority.body())
                || hands.revision() != request.expectedRevision() || !activeHandProof
                || !looseEntityValid(entity, level) || !entity.getUUID().equals(request.pickupTarget())
                || !prepareLooseEndpoint(entity) || !HandActorAuthority.revalidate(actor, authority))
            return BodyHandActionResult.Reason.DENIED;
        ItemStack stack = entity.getItem();
        NetworkDeviceItemData policy = ModNetworkDeviceItems.resolve(stack, false)
                .filter(p -> p.signallerPort().isPresent()).orElse(null);
        RemoteSignallerStackData state = stack.get(ModDataComponents.REMOTE_SIGNALLER.get());
        if (policy == null || state == null) return BodyHandActionResult.Reason.DENIED;
        DeviceComposition composition;
        try { composition = signallerComposition(policy, state); }
        catch (RuntimeException invalid) { return BodyHandActionResult.Reason.DENIED; }
        DeviceEndpoint source = portableEndpoint(state.identity(), level, entity::blockPosition, composition,
                stack.getHoverName().getString(), () -> looseEntityValid(entity, level)
                        && matchesStackIdentity(entity.getItem(), state) && HandActorAuthority.revalidate(actor, authority));
        return activatePrepared(level, policy, state, source);
    }

    /** Single held/loose operation after each owner adapter has established its own live proof. */
    static BodyHandActionResult.Reason activatePrepared(ServerLevel level, NetworkDeviceItemData policy,
            RemoteSignallerStackData state, DeviceEndpoint source) {
        Long next = NEXT_USE.get(state.identity());
        if (SignallerUseDelayPolicy.coolingDown(level.getGameTime(), next)) return BodyHandActionResult.Reason.OK;
        DeviceNetworkRuntime runtime = DeviceNetworkRuntime.forLevel(level);
        if (!source.available() || !runtime.loaded(source)) return BodyHandActionResult.Reason.DENIED;
        boolean invoked = runtime.links().invoke(runtime.graph(), source,
                policy.signallerPort().orElseThrow(), DeviceSignal.MOMENTARY);
        NEXT_USE.put(state.identity(), SignallerUseDelayPolicy.nextUse(level.getGameTime(),
                policy.useDelaySeconds().orElse(1f)));
        if (invoked) PENDING_ENDPOINTS.put(state.identity(), level);
        else runtime.unloaded(state.identity());
        return BodyHandActionResult.Reason.OK;
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void endTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for(var entry:List.copyOf(PENDING_ENDPOINTS.entrySet())) if(entry.getValue().getServer()==event.getServer()) {
            DeviceNetworkRuntime.forLevel(entry.getValue()).unloaded(entry.getKey()); PENDING_ENDPOINTS.remove(entry.getKey());
        }
    }

    /** Shared construction path for portable endpoints owned by a held or loose stack. */
    public static DeviceEndpoint portableEndpoint(DeviceIdentity identity, ServerLevel level,
                                                   net.minecraft.core.BlockPos position, DeviceComposition composition) {
        return portableEndpoint(identity, level, position, composition, net.minecraft.network.chat.Component.translatable("item.moonstation14.remote_signaller").getString());
    }
    public static DeviceEndpoint portableEndpoint(DeviceIdentity identity, ServerLevel level,
                                                   net.minecraft.core.BlockPos position, DeviceComposition composition, String displayName) {
        return new PortableEndpoint(identity, level, () -> position, composition, displayName, () -> level.getServer().isSameThread());
    }

    private static DeviceComposition signallerComposition(NetworkDeviceItemData policy,RemoteSignallerStackData state) {
        DeviceComposition composition=policy.deviceComponents().resolve(PrototypeRuntime.serverManager());
        var network=composition.network().orElseThrow(()->new IllegalArgumentException("signaller has no network component"));
        if(state.address().isBlank()&&network.customAddress())throw new IllegalArgumentException("custom address is not configured");
        String address=state.address();
        if(address.isBlank()) throw new IllegalArgumentException("portable default address is not prepared");
        return composition.withAddress(address);
    }

    private static RemoteSignallerStackData ensureAddress(NetworkDeviceItemData policy,RemoteSignallerStackData state,ServerLevel level) {
        if(state==null) state=RemoteSignallerStackData.create();
        DeviceComposition composition=policy.deviceComponents().resolve(PrototypeRuntime.serverManager());
        var network=composition.network().orElseThrow(()->new IllegalArgumentException("signaller has no network component"));
        if(network.customAddress()) {
            if(state.address().isBlank()) throw new IllegalArgumentException("custom address is not configured");
            return state;
        }
        String prefix=network.addressPrefix();
        String address=DeviceNetworkRuntime.forLevel(level).transport().allocateDefaultAddress(
                state.identity(),network.networkId(),prefix,state.address());
        if(address.equals(state.address()))return state;
        return new RemoteSignallerStackData(state.identity(),address);
    }

    private static DeviceEndpoint portableEndpoint(DeviceIdentity identity, ServerLevel level,
                                                   java.util.function.Supplier<net.minecraft.core.BlockPos> position,
                                                   DeviceComposition composition, String displayName, java.util.function.BooleanSupplier available) {
        return new PortableEndpoint(identity, level, position, composition, displayName, available);
    }
    private record PortableEndpoint(DeviceIdentity identity, ServerLevel level, java.util.function.Supplier<net.minecraft.core.BlockPos> positionSupplier,
                                    DeviceComposition composition, String displayName, java.util.function.BooleanSupplier ownerAvailable) implements DeviceEndpoint {
        @Override public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension(){return level.dimension();}
        @Override public String displayName(){return displayName;}
        @Override public net.minecraft.core.BlockPos position(){return positionSupplier.get().immutable();}
        @Override public boolean ownsAddressOutsideWorldSavedData(){return true;}
        @Override public boolean available(){return level.getServer().isSameThread() && ownerAvailable.getAsBoolean();}
        @Override public void receive(DevicePayload payload,String sourceAddress) { }
    }
}
