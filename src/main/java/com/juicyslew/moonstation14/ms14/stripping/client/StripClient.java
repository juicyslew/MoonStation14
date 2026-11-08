package com.juicyslew.moonstation14.ms14.stripping.client;

import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.stripping.*;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Sole bounded strip-view session for the local controlled body. */
public final class StripClient {
    public static final ResourceLocation HIDDEN_ICON=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/equipment/strip_hidden.png");
    private static final StripClient INSTANCE=new StripClient();
    private final StripViewCorrelation correlation=new StripViewCorrelation();
    private final StripWindow window=new StripWindow(this);
    private StripViewSnapshot snapshot;
    private StripTargetBinding targetBinding;
    private long sequence;
    private long targetRevision;
    private long lastQueryTick;
    private String status="";
    private StripClient(){}
    public static StripClient instance(){return INSTANCE;}
    public StripWindow window(){return window;}
    public StripViewSnapshot snapshot(){return snapshot;}
    public String status(){return status;}
    public String targetName(){var e=targetEntity();return e==null?"Strip view":e.getDisplayName().getString();}
    private LivingEntity targetEntity(){var mc=Minecraft.getInstance();if(mc.level==null||targetBinding==null||!targetBinding.dimension().equals(mc.level.dimension()))return null;return mc.level.getEntity(targetBinding.entityId()) instanceof LivingEntity e&&targetBinding.matches(e.getUUID(),e.getId(),e.level().dimension())?e:null;}
    public void bind(){StripNetworking.bindClient(this::accept);PointerOverlayRegistry.register(window);}
    public void open(LivingEntity target) {
        var proof=BodyHandClient.ownerProof(); if(target==null||proof==null||target==proof.body()||Minecraft.getInstance().level==null)return;
        long epoch=proof.epoch();long seq=nextSequence();targetRevision=0;targetBinding=new StripTargetBinding(target.getUUID(),target.getId(),target.level().dimension());correlation.begin(target.getUUID(),epoch,seq);snapshot=null;status="Connecting…";
        PacketDistributor.sendToServer(new StripViewRequest(StripViewRequest.Operation.OPEN,epoch,seq,target.getUUID(),0));
        PointerOverlayRegistry.register(window);window.open();BodyHandClient.openSharedPointerWorkspace();
    }
    public void close(){var proof=BodyHandClient.ownerProof();if(correlation.open()&&proof!=null){long seq=nextSequence();PacketDistributor.sendToServer(new StripViewRequest(StripViewRequest.Operation.CLOSE,proof.epoch(),seq,correlation.target(),targetRevision));}correlation.close();snapshot=null;targetBinding=null;}
    public void contextReset(){correlation.close();snapshot=null;targetBinding=null;targetRevision=0;status="";window.resetOwnedContext();}
    public void tick(long tick){
        if(!window.visible()||!correlation.open())return;
        var proof=BodyHandClient.ownerProof();if(proof==null||proof.epoch()!=correlation.epoch()){window.resetOwnedContext();contextReset();return;}
        var target=targetEntity();
        if(!StripInteractionRange.contains(proof.body(),target)){close();window.remoteClosed();return;}
        if(tick-lastQueryTick>=10&&!correlation.inFlight()){
            long seq=correlation.querySequence(nextSequence());if(seq>0){lastQueryTick=tick;PacketDistributor.sendToServer(new StripViewRequest(StripViewRequest.Operation.QUERY,proof.epoch(),seq,correlation.target(),targetRevision));}
        }
    }
    private long nextSequence(){if(sequence==Long.MAX_VALUE)sequence=0;return ++sequence;}
    private void accept(StripViewSnapshot incoming){
        if(!correlation.accept(incoming))return;
        if(incoming.status()==StripViewSnapshot.Status.CLOSED){snapshot=null;targetBinding=null;status="Closed";window.remoteClosed();return;}
        targetRevision=incoming.targetRevision();
        if(incoming.status()!=StripViewSnapshot.Status.AVAILABLE){snapshot=null;status="Out of range or unavailable";return;}
        if(incoming.templateId()!=null&&template(incoming)==null){snapshot=null;status="Inventory unavailable";return;}
        snapshot=incoming;status=incoming.canAct()?"":"Read only";
    }
    InventoryTemplateData template(StripViewSnapshot view){
        if(view.templateId()==null)return null;
        try{return com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.require(com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.clientCatalog(),ResourceLocation.tryParse(view.templateId()));}
        catch(RuntimeException ignored){return null;}
    }
    List<InventoryTemplateData.Slot> templateSlots(StripViewSnapshot view){
        if(view==null||view.templateId()==null)return List.of();
        var resolved=template(view);return resolved==null?List.of():resolved.slots();
    }
    boolean equipmentSlotEnabled(StripViewSnapshot view,String slotId){
        if(view==null||view.templateId()==null)return false;
        var resolved=template(view);if(resolved==null)return false;
        return StripSlotAvailability.equipmentEnabled(view,resolved,
                com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.clientCatalog(),slotId);
    }
    void action(StripViewSnapshot.Slot slot){
        var proof=BodyHandClient.ownerProof();if(snapshot==null||proof==null||!snapshot.canAct()||proof.epoch()!=snapshot.epoch())return;
        if(slot.kind()==StripSlotRef.Kind.EQUIPMENT&&!equipmentSlotEnabled(snapshot,slot.id()))return;
        long seq=nextSequence();StripSlotRef.Kind kind=slot.kind();
        PacketDistributor.sendToServer(new StripSlotAction(snapshot.epoch(),seq,proof.revision(),snapshot.target(),snapshot.targetRevision(),kind,slot.id()));
        if(!correlation.inFlight()){
            long q=correlation.querySequence(nextSequence());if(q>0)PacketDistributor.sendToServer(new StripViewRequest(StripViewRequest.Operation.QUERY,snapshot.epoch(),q,snapshot.target(),snapshot.targetRevision()));
        }
    }
    /** Client nomination uses normal E's ray ceiling but the server's physical root-reach predicate. */
    public boolean openAimed(){
        var mc=Minecraft.getInstance();var actor=BodyHandClient.currentControlledBody();if(actor==null||mc.level==null)return false;
        Vec3 from=actor.getEyePosition(),to=from.add(actor.getLookAngle().scale(BodyHandClient.NORMAL_WORLD_AIM_REACH));
        var block=mc.level.clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,actor));
        double wallDistance=block.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?from.distanceTo(block.getLocation()):BodyHandClient.NORMAL_WORLD_AIM_REACH;
        var candidates=mc.level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(from,to).inflate(1.0),e->e!=actor&&e!=mc.player&&!e.isRemoved()
                        &&!(e instanceof net.minecraft.world.entity.player.Player)
                        &&com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(e).isPresent());
        var byId=new HashMap<UUID,LivingEntity>();var aimed=new ArrayList<StripTargetSelection.Candidate>();
        for(var entity:candidates){
            var data=com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(entity).orElse(null);
            if(data==null)continue;
            boolean eligible=StripCapability.canTargetBeStripped(data,
                    com.juicyslew.moonstation14.ms14.hands.HandCapability.resolveEquipmentSlots(entity).isPresent());
            var hit=com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay.hit(
                    com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(entity),from,to,
                    BodyHandClient.NORMAL_WORLD_AIM_REACH,wallDistance).orElse(null);
            if(hit==null)continue;
            UUID id=entity.getUUID();byId.put(id,entity);
            aimed.add(new StripTargetSelection.Candidate(id,entity.position(),from.distanceTo(hit),eligible,false));
        }
        UUID selected=StripTargetSelection.select(aimed,actor.position(),StripInteractionRange.BLOCKS,wallDistance);
        LivingEntity best=selected==null?null:byId.get(selected);
        if(best==null)return false;
        open(best);return true;
    }
}

