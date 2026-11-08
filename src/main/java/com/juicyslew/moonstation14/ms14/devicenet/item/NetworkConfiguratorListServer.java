package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.devicenet.*;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Owner-authoritative list editor for the configurator's LIST mode. */
public final class NetworkConfiguratorListServer {
    private record Session(HandActorAuthority.Snapshot authority, UUID tool, String hand, String token,
                           long revision, UUID id, DeviceIdentity target, long request, boolean show) { }
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final DeviceListService LISTS = new DeviceListService();
    private NetworkConfiguratorListServer() { }
    public static void clear(ServerPlayer actor) { if(actor!=null) SESSIONS.remove(actor); }
    public static void clearAll() { SESSIONS.clear(); }

    /** Activate the list UI only for a currently held configurator explicitly in LIST mode. */
    public static UUID openList(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                String hand, String token, long revision) {
        if (!intent(actor, authority, hand, token, revision)) return null;
        ItemStack stack = held(authority, hand);
        NetworkConfiguratorStackData data = state(stack);
        if (data == null || data.mode() != NetworkConfiguratorStackData.Mode.LIST) return null;
        UUID id = UUID.randomUUID();
        SESSIONS.put(actor, new Session(authority, data.identity().value(), hand, token, revision, id, null, -1, false));
        return id;
    }

    /** Stage the unique endpoint under the body eye. Reject ambiguous, non-savable and duplicate identities. */
    public static boolean selectOrStageEndpoint(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                                String hand, String token, long revision,
                                                net.minecraft.core.BlockPos hit) {
        if (!intent(actor, authority, hand, token, revision) || !BodyBlockUseTargetProbe.withinReach((ServerLevel) authority.body().level(), authority.body(), hit)) return false;
        ItemStack before = held(authority, hand);
        NetworkConfiguratorStackData data = state(before);
        if (data == null || data.mode() != NetworkConfiguratorStackData.Mode.LIST) return false;
        ServerLevel level = (ServerLevel) authority.body().level();
        List<DeviceEndpoint> found = DeviceNetworkRuntime.forLevel(level).transport().lookupAt(hit);
        if (found.size() != 1) return false;
        return stageEndpoint(actor, authority, hand, token, revision, before, data, found.getFirst());
    }

    /** Stage a portable endpoint already admitted by the body-eye loose-item probe. */
    public static boolean selectOrStageAdmittedDevice(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                                       String hand, String token, long revision,
                                                       DeviceIdentity identity) {
        if (!intent(actor, authority, hand, token, revision) || identity == null) return false;
        ItemStack before = held(authority, hand);
        NetworkConfiguratorStackData data = state(before);
        if (data == null || data.mode() != NetworkConfiguratorStackData.Mode.LIST) return false;
        DeviceEndpoint endpoint = DeviceNetworkRuntime.forLevel((ServerLevel) authority.body().level()).transport().lookup(identity);
        return endpoint != null && stageEndpoint(actor, authority, hand, token, revision, before, data, endpoint);
    }

    private static boolean stageEndpoint(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                         String hand, String token, long revision, ItemStack before,
                                         NetworkConfiguratorStackData data, DeviceEndpoint endpoint) {
        var network = endpoint.composition().network().orElse(null);
        if (!endpoint.available() || network == null || !network.savableAddress() || network.address().isBlank()
                || data.staged().stream().anyMatch(v -> v.identity().equals(endpoint.identity().value()))) return false;
        var staged = new ArrayList<>(data.staged());
        staged.add(new NetworkConfiguratorStackData.StagedDevice(endpoint.identity().value(), network.address(), endpoint.displayName()));
        NetworkConfiguratorStackData afterData = new NetworkConfiguratorStackData(data.identity(), data.address(), data.mode(), staged,
                data.selectedSource(), data.selectedSourcePort());
        ItemStack after = before.copy(); after.set(ModDataComponents.NETWORK_CONFIGURATOR.get(), afterData);
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (BodyHeldItemMutation.commit(actor, authority, hands, hand, token, revision, before, after)
                != BodyHeldItemMutation.Result.SUCCESS) return false;
        LiveHands published = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        Session session = SESSIONS.get(actor);
        if (published != null && session != null && session.authority().epoch() == authority.epoch()
                && session.hand().equals(hand) && session.token().equals(token) && session.revision() == revision) {
            SESSIONS.put(actor, new Session(session.authority(), session.tool(), session.hand(), session.token(),
                    published.revision(), session.id(), session.target(), ConfiguratorOperationSequence.nextPublication(session.request()), session.show()));
        }
        return true;
    }

    /** Bind a configure session only to a currently loaded list owner within body reach. */
    public static boolean openConfigure(ServerPlayer actor, UUID session, DeviceIdentity target) {
        Session old = actor == null ? null : SESSIONS.get(actor);
        if (old == null || !old.id().equals(session) || !valid(actor, old) || target == null) return false;
        ServerLevel level = (ServerLevel) old.authority().body().level();
        DeviceEndpoint endpoint = DeviceNetworkRuntime.forLevel(level).transport().lookup(target);
        if (endpoint == null || endpoint.deviceList() == null || !BodyBlockUseTargetProbe.lineOfSight((ServerLevel) old.authority().body().level(), old.authority().body(), endpoint.position())) return false;
        long request=old.target()!=null&&old.target().equals(target)?old.request():ConfiguratorOperationSequence.nextPublication(old.request());
        SESSIONS.put(actor, new Session(old.authority(), old.tool(), old.hand(), old.token(), old.revision(), old.id(), target, request, old.show()));
        return true;
    }

    /** Current admitted list-editor state for initial UI publication. */
    public static NetworkConfiguratorListSnapshot currentSnapshot(ServerPlayer actor) {
        Session s=actor==null?null:SESSIONS.get(actor);
        if(s==null||!valid(actor,s)) return null;
        return snapshot((ServerLevel)s.authority().body().level(),s,s.request());
    }

    public static NetworkConfiguratorListSnapshot apply(ServerPlayer actor, NetworkConfiguratorListRequest request) {
        Session s = actor == null ? null : SESSIONS.get(actor);
        if (request == null || s == null || !valid(actor, s) || !s.id().equals(request.session())
                || request.epoch() != s.authority().epoch() || request.revision() != s.revision()
                || !request.handId().equals(s.hand()) || !request.handToken().equals(s.token())
                || request.requestId() <= s.request()) { if (actor != null && s != null && (request == null || s.id().equals(request.session()))) SESSIONS.remove(actor, s); return null; }
        if (request.operation() == NetworkConfiguratorListRequest.Operation.DONE) { SESSIONS.remove(actor); return null; }
        boolean show = request.operation() == NetworkConfiguratorListRequest.Operation.SHOW ? !s.show() : s.show();
        Session next = new Session(s.authority(), s.tool(), s.hand(), s.token(), s.revision(), s.id(), s.target(), request.requestId(), show);
        if (s.target() == null && (request.operation() == NetworkConfiguratorListRequest.Operation.REMOVE
                || request.operation() == NetworkConfiguratorListRequest.Operation.CLEAR)) {
            if (!mutateStaging(actor, s, request, List.of())) return null;
            LiveHands live = s.authority().body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            next = refreshed(actor, s, live, request.requestId(), show);
        }
        if (s.target() != null && request.operation() != NetworkConfiguratorListRequest.Operation.QUERY
                && request.operation() != NetworkConfiguratorListRequest.Operation.SHOW) {
            ServerLevel level = (ServerLevel) s.authority().body().level();
            DeviceEndpoint target = DeviceNetworkRuntime.forLevel(level).transport().lookup(s.target());
            if (target == null || target.deviceList() == null || !BodyBlockUseTargetProbe.lineOfSight(level, s.authority().body(), target.position())) {
                SESSIONS.remove(actor);
                return new NetworkConfiguratorListSnapshot(s.id(), request.requestId(), s.revision(), s.authority().epoch(), false, false, "", List.of(), List.of(), List.of(), List.of());
            }
            List<String> members = request.members();
            if (request.operation() == NetworkConfiguratorListRequest.Operation.SET || request.operation() == NetworkConfiguratorListRequest.Operation.ADD)
                members = stagedIdentities(held(s.authority(), s.hand()));
            if (request.operation() == NetworkConfiguratorListRequest.Operation.COPY) {
                ItemStack stack = held(s.authority(), s.hand());
                NetworkConfiguratorStackData data = state(stack);
                if (data == null) { SESSIONS.remove(actor); return null; }
                var stagedMembers = new ArrayList<NetworkConfiguratorStackData.StagedDevice>();
                var transport = DeviceNetworkRuntime.forLevel(level).transport();
                for (String member : target.deviceList().addresses()) {
                    UUID uuid;
                    try { uuid = UUID.fromString(member); } catch (IllegalArgumentException ex) { continue; }
                    DeviceEndpoint endpoint = transport.lookup(new DeviceIdentity(uuid));
                    var known = data.staged().stream().filter(v -> v.identity().equals(uuid)).findFirst().orElse(null);
                    stagedMembers.add(endpoint != null
                            ? new NetworkConfiguratorStackData.StagedDevice(uuid, endpoint.composition().network().map(DeviceComposition.NetworkInterface::address).orElse(""), endpoint.displayName())
                            : known != null ? known : new NetworkConfiguratorStackData.StagedDevice(uuid, "", ""));
                }
                NetworkConfiguratorStackData updated = new NetworkConfiguratorStackData(data.identity(), data.address(), data.mode(), stagedMembers, data.selectedSource(), data.selectedSourcePort());
                ItemStack after = stack.copy(); after.set(ModDataComponents.NETWORK_CONFIGURATOR.get(), updated);
                LiveHands live = s.authority().body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                if (BodyHeldItemMutation.commit(actor, s.authority(), live, s.hand(), s.token(), s.revision(), stack, after) != BodyHeldItemMutation.Result.SUCCESS) { SESSIONS.remove(actor); return null; }
                LiveHands confirmed = s.authority().body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                next = new Session(s.authority(), s.tool(), s.hand(), s.token(), confirmed.revision(), s.id(), s.target(), request.requestId(), show);
            }
            DeviceListService.Mutation mutation = switch (request.operation()) {
                case SET -> DeviceListService.Mutation.set(members);
                case ADD -> DeviceListService.Mutation.add(members);
                case CLEAR -> DeviceListService.Mutation.clear();
                case COPY -> null;
                case REMOVE -> DeviceListService.Mutation.remove(request.member());
                default -> null;
            };
            if (mutation != null && request.operation() != NetworkConfiguratorListRequest.Operation.COPY) LISTS.apply(target, mutation);
        }
        SESSIONS.put(actor, next);
        return snapshot((ServerLevel) s.authority().body().level(), next, request.requestId());
    }

    private static List<String> stagedIdentities(ItemStack stack) {
        NetworkConfiguratorStackData data = state(stack);
        return data == null ? List.of() : data.staged().stream().map(v -> v.identity().toString()).toList();
    }

    private static boolean mutateStaging(ServerPlayer actor, Session s, NetworkConfiguratorListRequest request, List<String> ignored) {
        ItemStack before = held(s.authority(), s.hand());
        NetworkConfiguratorStackData data = state(before);
        if (data == null) return false;
        var staged = new ArrayList<>(data.staged());
        if (request.operation() == NetworkConfiguratorListRequest.Operation.CLEAR) staged.clear();
        else {
            UUID identity;
            try { identity = UUID.fromString(request.member()); } catch (RuntimeException ex) { return false; }
            staged.removeIf(v -> v.identity().equals(identity));
        }
        ItemStack after = before.copy();
        after.set(ModDataComponents.NETWORK_CONFIGURATOR.get(), new NetworkConfiguratorStackData(data.identity(), data.address(), data.mode(), staged, data.selectedSource(), data.selectedSourcePort()));
        LiveHands hands = s.authority().body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return BodyHeldItemMutation.commit(actor, s.authority(), hands, s.hand(), s.token(), s.revision(), before, after) == BodyHeldItemMutation.Result.SUCCESS;
    }

    private static Session refreshed(ServerPlayer actor, Session s, LiveHands live, long request, boolean show) {
        LiveHands published = s.authority().body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (published == null) return s;
        return new Session(s.authority(), s.tool(), s.hand(), s.token(), published.revision(), s.id(), s.target(), request, show);
    }

    private static NetworkConfiguratorListSnapshot snapshot(ServerLevel level, Session s, long request) {
        DeviceEndpoint target = s.target() == null ? null : DeviceNetworkRuntime.forLevel(level).transport().lookup(s.target());
        var stack = held(s.authority(), s.hand());
        var data = state(stack);
        DeviceList list = target == null ? null : target.deviceList();
        var positions = new ArrayList<NetworkConfiguratorListSnapshot.MemberPosition>();
        if (s.show() && target != null && list != null) {
            var transport = DeviceNetworkRuntime.forLevel(level).transport();
            for (String member : list.addresses()) {
                UUID identity;
                try { identity = UUID.fromString(member); } catch (IllegalArgumentException ex) { continue; }
                DeviceEndpoint endpoint = transport.lookup(new DeviceIdentity(identity));
                if (endpoint != null) {
                    var pos = endpoint.position();
                    positions.add(new NetworkConfiguratorListSnapshot.MemberPosition(endpoint.identity().value(), level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ()));
                }
            }
        }
        var displayRows = new ArrayList<NetworkConfiguratorStackData.StagedDevice>();
        if (list != null) for (String member : list.addresses()) {
            UUID uuid; try { uuid = UUID.fromString(member); } catch (IllegalArgumentException ex) { continue; }
            DeviceEndpoint endpoint = DeviceNetworkRuntime.forLevel(level).transport().lookup(new DeviceIdentity(uuid));
            var known = data == null ? null : data.staged().stream().filter(v -> v.identity().equals(uuid)).findFirst().orElse(null);
            displayRows.add(endpoint != null
                    ? new NetworkConfiguratorStackData.StagedDevice(uuid, endpoint.composition().network().map(DeviceComposition.NetworkInterface::address).orElse(""), endpoint.displayName())
                    : known != null ? known : new NetworkConfiguratorStackData.StagedDevice(uuid, "", "offline " + uuid));
        }
        return new NetworkConfiguratorListSnapshot(s.id(), request, s.revision(), s.authority().epoch(), true, s.show(),
                target == null ? "" : target.composition().network().map(DeviceComposition.NetworkInterface::address).orElse(""),
                list == null ? List.of() : list.addresses(), data == null ? List.of() : data.staged(), displayRows, positions);
    }
    private static boolean valid(ServerPlayer actor, Session s) {
        if (!HandActorAuthority.revalidate(actor, s.authority()) || !intent(actor, s.authority(), s.hand(), s.token(), s.revision())) return false;
        var data = state(held(s.authority(), s.hand()));
        return data != null && data.mode() == NetworkConfiguratorStackData.Mode.LIST && data.identity().value().equals(s.tool());
    }
    private static boolean intent(ServerPlayer actor, HandActorAuthority.Snapshot a, String hand, String token, long revision) {
        if (actor == null || a == null || hand == null || token == null || !CharacterControlSystem.canAct(a.body())
                || !HandActorAuthority.revalidate(actor, a) || !(a.body().level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return false;
        LiveHands hands = a.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return hands != null && hands.compatible(a.body()) && hands.revision() == revision && hand.equals(hands.activeHand())
                && hands.token(hand).filter(token::equals).isPresent();
    }
    private static ItemStack held(HandActorAuthority.Snapshot a, String hand) {
        LiveHands hands = a.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return hands == null ? ItemStack.EMPTY : hands.stackCopy(hand).orElse(ItemStack.EMPTY);
    }
    private static NetworkConfiguratorStackData state(ItemStack stack) {
        return stack.isEmpty() || !com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.isConfigurator(stack)
                ? null : stack.get(ModDataComponents.NETWORK_CONFIGURATOR.get());
    }
}

