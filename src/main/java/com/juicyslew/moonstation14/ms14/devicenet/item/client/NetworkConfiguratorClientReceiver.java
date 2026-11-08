package com.juicyslew.moonstation14.ms14.devicenet.item.client;

import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorSnapshot;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorPayloads;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListSnapshot;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListRequest;
import com.juicyslew.moonstation14.ms14.devicenet.item.ConfiguratorOperationSequence;
import java.util.concurrent.atomic.AtomicLong;

/** Client-thread receiver for server-authoritative configurator state. */
public final class NetworkConfiguratorClientReceiver {
    private static java.util.UUID retainedSession;
    private static final AtomicLong REQUEST_SEQUENCE=new AtomicLong();
    private static java.util.UUID retainedListSession;
    private static final AtomicLong LIST_REQUEST_SEQUENCE=new AtomicLong();
    private NetworkConfiguratorClientReceiver() { }
    public static void clear() {
        retainedSession=null; retainedListSession=null;
        REQUEST_SEQUENCE.set(0); LIST_REQUEST_SEQUENCE.set(0);
    }
    public static void receive(NetworkConfiguratorSnapshot snapshot) {
        Minecraft mc=Minecraft.getInstance();
        var handProof=proof(snapshot.epoch());
        if(handProof==null)return;
        if(snapshot.open() && mc.screen!=null && !(mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen))return;
        NetworkConfiguratorLinkWindowState.INSTANCE.accept(snapshot,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),command->{
            if(!snapshot.session().equals(retainedSession)){retainedSession=snapshot.session();REQUEST_SEQUENCE.set(0);}
            long id=REQUEST_SEQUENCE.updateAndGet(current->ConfiguratorOperationSequence.nextCommand(current,snapshot.requestId()));
            var proof=proof(snapshot.epoch());if(proof==null)return;
            var request=new NetworkConfiguratorRequest(snapshot.session(),id,snapshot.epoch(),proof.revision(),proof.handId(),proof.token(),command.operation(),null,command.sourcePort(),command.sinkPort());
            PacketDistributor.sendToServer(new NetworkConfiguratorPayloads.Request(request));
        });
        if(snapshot.open() && mc.screen==null) BodyHandClient.medicalPointerMode();
    }
    public static void receive(NetworkConfiguratorListSnapshot snapshot) {
        Minecraft mc=Minecraft.getInstance();
        var handProof=proof(snapshot.epoch());
        if(handProof==null || handProof.revision()!=snapshot.handRevision())return;
        if(snapshot.open() && mc.screen!=null && !(mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen))return;
        NetworkConfiguratorListWindowState.INSTANCE.accept(snapshot,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),command->{
            var proof=proof(snapshot.epoch());if(proof==null||proof.revision()!=snapshot.handRevision())return;
            if(!snapshot.session().equals(retainedListSession)){retainedListSession=snapshot.session();LIST_REQUEST_SEQUENCE.set(0);}
            long id=LIST_REQUEST_SEQUENCE.updateAndGet(current->ConfiguratorOperationSequence.nextCommand(current,snapshot.requestId()));
            var request=new NetworkConfiguratorListRequest(snapshot.session(),id,snapshot.epoch(),proof.revision(),proof.handId(),proof.token(),command.operation(),command.members(),command.member());
            PacketDistributor.sendToServer(new NetworkConfiguratorPayloads.ListRequest(request));
        });
        if(snapshot.open() && mc.screen==null) BodyHandClient.medicalPointerMode();
    }
    private record HandProof(long revision,String handId,String token){}
    private static HandProof proof(long epoch){
        var proof=BodyHandClient.networkConfiguratorProof();
        if(proof==null||proof.reason()!=BodyHandStateSnapshot.Reason.OK||proof.epoch()!=epoch)return null;
        var hand=proof.hands().stream().filter(h->h.id().equals(proof.activeHand())).findFirst().orElse(null);
        if(hand==null||hand.token()==null)return null;
        var itemId=net.minecraft.resources.ResourceLocation.tryParse(hand.itemId());
        if(itemId==null)return null;
        var stack=new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId));
        if(com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.resolve(stack,true)
                .flatMap(com.juicyslew.moonstation14.ms14.devicenet.NetworkDeviceItemData::configurator).isEmpty())return null;
        return new HandProof(proof.revision(),hand.id(),hand.token());
    }
}

