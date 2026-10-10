package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.ms14.devicenet.*;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** Server-side link editor operations. UI/input adapters must first authenticate body-hand proof. */
public final class NetworkConfiguratorServer {
    private record Session(ServerPlayer actor, HandActorAuthority.Snapshot authority, UUID tool,
                           String hand, String token, long revision, UUID id,
                           DeviceIdentity source, DeviceIdentity sink, long request) { }
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private NetworkConfiguratorServer() { }
    public static void clear(ServerPlayer actor) { if(actor!=null) SESSIONS.remove(actor); }
    public static void clearAll() { SESSIONS.clear(); }

    /** Authenticated body-eye selection. Each call admits only the endpoint currently under the body eye. */
    public static boolean selectTarget(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                       String hand, String token, long revision, net.minecraft.core.BlockPos hit) {
        if (!intent(actor, authority, hand, token, revision)) return false;
        LiveHands hands=authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack stack=hands.stackCopy(hand).orElse(ItemStack.EMPTY);
        NetworkConfiguratorStackData data=stack.get(ModDataComponents.NETWORK_CONFIGURATOR.get());
        if (stack.isEmpty() || !com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.isConfigurator(stack) || data==null
                || !BodyBlockUseTargetProbe.withinReach((ServerLevel) authority.body().level(), authority.body(), hit)) return false;
        var endpoints=endpointsAt((ServerLevel)authority.body().level(),hit);
        if(endpoints.size()!=1) return false;
        DeviceIdentity selected=endpoints.getFirst();
        DeviceEndpoint endpoint=DeviceNetworkRuntime.forLevel((ServerLevel)authority.body().level()).transport().lookup(selected);
        if(endpoint==null) return false;
        return admitDeviceIdentity(actor, authority, hand, token, revision, data.identity(), selected, endpoint);
    }

    /** Selects an already body-eye-admitted portable endpoint without requiring a block hit. */
    public static boolean selectAdmittedDevice(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                                String hand, String token, long revision,
                                                DeviceIdentity admittedDeviceIdentity) {
        if (!intent(actor, authority, hand, token, revision) || admittedDeviceIdentity == null) return false;
        LiveHands hands=authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack stack=hands.stackCopy(hand).orElse(ItemStack.EMPTY);
        NetworkConfiguratorStackData data=stack.get(ModDataComponents.NETWORK_CONFIGURATOR.get());
        if (stack.isEmpty() || !com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.isConfigurator(stack) || data==null) return false;
        DeviceEndpoint endpoint=DeviceNetworkRuntime.forLevel((ServerLevel)authority.body().level()).transport().lookup(admittedDeviceIdentity);
        if(endpoint==null)return false;
        return admitDeviceIdentity(actor,authority,hand,token,revision,data.identity(),admittedDeviceIdentity,endpoint);
    }

    private static boolean admitDeviceIdentity(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                                String hand, String token, long revision, DeviceIdentity tool,
                                                DeviceIdentity selected, DeviceEndpoint endpoint) {
        Session old=SESSIONS.get(actor);
        boolean first=old==null || !valid(actor,old);
        boolean canSource=!endpoint.composition().sources().isEmpty();
        boolean canSink=!endpoint.composition().sinks().isEmpty();
        if(!canSource&&!canSink)return false;
        DeviceIdentity source=first?null:old.source(), sink=first?null:old.sink();
        if(source==null && (canSource || !canSink)) source=selected;
        else if(sink==null && (canSink || !canSource)) sink=selected;
        else { source=canSource?selected:null; sink=canSource?null:selected; }
        if(source!=null && sink!=null) {
            DeviceEndpoint s=DeviceNetworkRuntime.forLevel((ServerLevel)authority.body().level()).transport().lookup(source);
            DeviceEndpoint t=DeviceNetworkRuntime.forLevel((ServerLevel)authority.body().level()).transport().lookup(sink);
            // Opening the editor is endpoint selection, not linking. Range is enforced by link operations.
            if(s==null||t==null||s.composition().sources().isEmpty()||t.composition().sinks().isEmpty()) return false;
        }
        UUID id=old==null||first?UUID.randomUUID():old.id();
        // request is the session-wide operation high-water mark, including target selections.
        SESSIONS.put(actor,new Session(actor,authority, tool.value(),hand,token,revision,id,source,sink,
                ConfiguratorOperationSequence.nextPublication(old==null||first?-1:old.request())));
        return true;
    }

    /** Handles commands only against the exact live body/tool session; mutations never trust request identities. */
    public static NetworkConfiguratorSnapshot handle(ServerPlayer actor, NetworkConfiguratorRequest request) {
        Session s=actor==null?null:SESSIONS.get(actor);
        if(request==null||s==null||!valid(actor,s)||!s.id().equals(request.session())||request.epoch()!=s.authority().epoch()
                ||request.revision()!=s.revision()||!request.handId().equals(s.hand())||!request.handToken().equals(s.token())
                ||request.requestId()<=s.request()) { if(actor!=null && s!=null && (request==null || s.id().equals(request.session()))) SESSIONS.remove(actor,s); return null; }
        if(request.operation()==NetworkConfiguratorRequest.Operation.DONE){SESSIONS.remove(actor);return null;}
        DeviceIdentity source=s.source(),sink=s.sink();
        if(source==null||sink==null)return snapshotCurrent(actor,request);
        ServerLevel level=(ServerLevel)s.authority().body().level();
        switch(request.operation()) {
            case TOGGLE_PAIR -> togglePair(level,source,request.sourcePort(),sink,request.sinkPort());
            case CLEAR -> clear(level,source,sink);
            case LINK_DEFAULTS -> linkDefaults(level,source,sink);
            default -> { }
        }
        Session next=new Session(s.actor(),s.authority(),s.tool(),s.hand(),s.token(),s.revision(),s.id(),source,sink,request.requestId());
        SESSIONS.put(actor,next);
        return snapshot(level,s.id(),request.requestId(),s.authority().epoch(),source,sink);
    }

    public static NetworkConfiguratorSnapshot snapshotCurrent(ServerPlayer actor, NetworkConfiguratorRequest request) {
        Session s=actor==null?null:SESSIONS.get(actor);
        if(s==null||request==null||!valid(actor,s)||!s.id().equals(request.session())||request.epoch()!=s.authority().epoch()) return null;
        return snapshot((ServerLevel)s.authority().body().level(),s.id(),request.requestId(),s.authority().epoch(),s.source(),s.sink());
    }

    /** Current admitted link-editor state for initial UI publication. */
    public static NetworkConfiguratorSnapshot currentSnapshot(ServerPlayer actor) {
        Session s=actor==null?null:SESSIONS.get(actor);
        if(s==null||!valid(actor,s)) return null;
        return snapshot((ServerLevel)s.authority().body().level(),s.id(),s.request(),s.authority().epoch(),s.source(),s.sink());
    }

    /** Selection feedback derived only from the currently validated session and its admitted endpoint roles. */
    public static String selectionFeedback(ServerPlayer actor) {
        NetworkConfiguratorSnapshot current=currentSnapshot(actor);
        if(current==null)return "Network Configurator: selection rejected; check reach and active hand";
        return selectionFeedback(current);
    }
    public static String selectionFeedback(NetworkConfiguratorSnapshot current) {
        if(current==null)return "Network Configurator: selection rejected; check reach and active hand";
        if(current.source()!=null&&current.sink()!=null)
            return "Network Configurator: " + current.sourceAddress() + " → " + current.sinkAddress() + " selected; opening link editor";
        if(current.source()!=null)return "Network Configurator: " + current.sourceAddress() + " selected as source; select a receiver";
        if(current.sink()!=null)return "Network Configurator: " + current.sinkAddress() + " selected as receiver; select a source";
        return "Network Configurator: select a source and receiver";
    }

    private static boolean valid(ServerPlayer actor,Session s) {
        if(!HandActorAuthority.revalidate(actor,s.authority())||!intent(actor,s.authority(),s.hand(),s.token(),s.revision()))return false;
        LiveHands h=s.authority().body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack stack=h.stackCopy(s.hand()).orElse(ItemStack.EMPTY);
        var d=stack.get(ModDataComponents.NETWORK_CONFIGURATOR.get());
        return com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.isConfigurator(stack)&&d!=null&&d.identity().value().equals(s.tool());
    }
    private static boolean intent(ServerPlayer actor,HandActorAuthority.Snapshot a,String hand,String token,long revision) {
        if(actor==null||a==null||hand==null||token==null||!CharacterControlSystem.canAct(a.body())
                ||!HandActorAuthority.revalidate(actor,a)||!(a.body().level() instanceof ServerLevel level)||!level.getServer().isSameThread())return false;
        LiveHands h=a.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return h!=null&&h.compatible(a.body())&&h.revision()==revision&&hand.equals(h.activeHand())&&h.token(hand).filter(token::equals).isPresent();
    }
    /** Loaded endpoint choices at a block hit; the caller performs body-eye hit/reach admission. */
    public static List<DeviceIdentity> endpointsAt(ServerLevel level, net.minecraft.core.BlockPos hit) {
        return DeviceNetworkRuntime.forLevel(level).transport().lookupAt(hit).stream().map(DeviceEndpoint::identity).toList();
    }

    /** Read a fresh authoritative view, resolving both identities against loaded endpoints only. */
    public static NetworkConfiguratorSnapshot snapshot(ServerLevel level, UUID session, long requestId, long epoch,
                                                         DeviceIdentity sourceId, DeviceIdentity sinkId) {
        var runtime=DeviceNetworkRuntime.forLevel(level);
        DeviceEndpoint source=sourceId==null?null:runtime.transport().lookup(sourceId);
        DeviceEndpoint sink=sinkId==null?null:runtime.transport().lookup(sinkId);
        var links=source==null||sink==null?List.<NetworkConfiguratorSnapshot.Pair>of():runtime.graph().links().stream()
                .filter(l->l.source().device().equals(sourceId)&&l.sink().device().equals(sinkId))
                .map(l->new NetworkConfiguratorSnapshot.Pair(l.source().port(),l.sink().port())).toList();
        boolean open = source != null && sink != null
                && !source.composition().sources().isEmpty() && !sink.composition().sinks().isEmpty();
        return new NetworkConfiguratorSnapshot(session,requestId,epoch,open,sourceId,sinkId,address(source),address(sink),ports(source,true),ports(sink,false),links);
    }

    /** Toggle a valid source/sink pair immediately, enforcing the source endpoint's link range. */
    public static boolean togglePair(ServerLevel level, DeviceIdentity sourceId, String sourcePort,
                                     DeviceIdentity sinkId, String sinkPort) {
        var runtime=DeviceNetworkRuntime.forLevel(level);
        DeviceEndpoint source=runtime.transport().lookup(sourceId), sink=runtime.transport().lookup(sinkId);
        if(source==null||sink==null||!validPort(source,sourcePort,true)||!validPort(sink,sinkPort,false)) return false;
        var link=new DeviceLinkGraph.Link(new DeviceLinkGraph.PortRef(sourceId,sourcePort),new DeviceLinkGraph.PortRef(sinkId,sinkPort));
        var saved=DeviceLinkSavedData.forMutation(level);
        if(runtime.graph().links().contains(link)) return saved.unlink(link);
        return runtime.links().link(runtime.graph(),source,sourcePort,sink,sinkPort);
    }

    public static int clear(ServerLevel level, DeviceIdentity sourceId, DeviceIdentity sinkId) {
        var graph=DeviceNetworkRuntime.forLevel(level).graph();
        var saved=DeviceLinkSavedData.forMutation(level);
        int removed=0;
        for(var link:graph.links()) if(link.source().device().equals(sourceId)
                && (sinkId==null||link.sink().device().equals(sinkId)) && saved.unlink(link)) removed++;
        return removed;
    }

    /** Link each source port's declared defaults to matching sink IDs on the selected target. */
    public static int linkDefaults(ServerLevel level, DeviceIdentity sourceId, DeviceIdentity sinkId) {
        var runtime=DeviceNetworkRuntime.forLevel(level);
        DeviceEndpoint source=runtime.transport().lookup(sourceId), sink=runtime.transport().lookup(sinkId);
        if(source==null||sink==null) return 0;
        int count=0;
        for(DevicePort port:source.composition().sources()) for(String defaultSink:port.defaultLinks())
            if(validPort(sink,defaultSink,false)&&runtime.links().link(runtime.graph(),source,port.id(),sink,defaultSink)) count++;
        return count;
    }

    private static boolean validPort(DeviceEndpoint endpoint,String id,boolean source) {
        return id!=null&&(source?endpoint.composition().sources():endpoint.composition().sinks()).stream().anyMatch(p->p.id().equals(id));
    }
    private static String address(DeviceEndpoint endpoint) {
        return endpoint==null?"":endpoint.composition().network().map(DeviceComposition.NetworkInterface::address).orElse("");
    }
    private static List<NetworkConfiguratorSnapshot.Port> ports(DeviceEndpoint endpoint,boolean source) {
        if(endpoint==null)return List.of();
        return (source?endpoint.composition().sources():endpoint.composition().sinks()).stream()
                .map(p->new NetworkConfiguratorSnapshot.Port(p.id(),p.name(),p.description())).toList();
    }
}
