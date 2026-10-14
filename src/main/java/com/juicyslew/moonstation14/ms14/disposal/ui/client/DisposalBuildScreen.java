package com.juicyslew.moonstation14.ms14.disposal.ui.client;

import com.juicyslew.moonstation14.ms14.disposal.domain.*;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import com.juicyslew.moonstation14.ms14.disposal.ui.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Configuration editor and explicit timed-rotation proposal UI. */
public final class DisposalBuildScreen extends AbstractContainerScreen<DisposalBuildMenu> {
    private long sequence=1;
    private EditBox tagField, filterField;
    private List<DisposalOrientation> candidates=List.of();
    private int candidateIndex;
    private long observedRevision=Long.MIN_VALUE;
    public DisposalBuildScreen(DisposalBuildMenu menu, Inventory inv, Component title) { super(menu,inv,title); imageWidth=220; imageHeight=220; }

    @Override protected void init() {
        super.init();
        if (menu.editing()) {
            if (!menu.tubeAnchored()) {
                addRenderableWidget(Button.builder(Component.translatable("gui.moonstation14.disposal.rotation.previous"), b->step(-1)).bounds(leftPos+8,topPos+46,98,20).build());
                addRenderableWidget(Button.builder(Component.translatable("gui.moonstation14.disposal.rotation.next"), b->step(1)).bounds(leftPos+112,topPos+46,98,20).build());
                addRenderableWidget(Button.builder(Component.translatable("gui.moonstation14.disposal.rotation.confirm"), b->confirmRotation()).bounds(leftPos+8,topPos+72,202,20).build());
            }
            tagField=new EditBox(font,leftPos+8,topPos+104,200,18,Component.literal("Tag"));
            tagField.setMaxLength(64); tagField.setValue(menu.routingTag()); addRenderableWidget(tagField);
            filterField=new EditBox(font,leftPos+8,topPos+132,200,18,Component.literal("Router filter"));
            filterField.setMaxLength(128); filterField.setValue(menu.routingFilter()); addRenderableWidget(filterField);
            addRenderableWidget(Button.builder(Component.literal("Apply routing"),b->sendConfig()).bounds(leftPos+8,topPos+156,200,20).build());
        } else addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(leftPos+8,topPos+32,200,20).build());
        refreshCandidates();
        DisposalConstructionPreview.setConfigurationPlanProvider(this::configurationPlan);
    }

    private DisposalConstructionPreview.ConfigurationPlan configurationPlan(){
        refreshCandidates();if(menu.tubeAnchored()||candidates.isEmpty()||Minecraft.getInstance().level==null)return null;
        var body=GhostControlClient.ownedCharacterForHud();long epoch=GhostControlClient.committedCharacterEpoch();if(body==null||epoch<=0)return null;
        var held=BodyHandClient.activeHeldStackFor(body.getUUID(),epoch).orElse(null);if(held==null||!held.is(ModItems.SCREWDRIVER.get()))return null;
        var record=recordAt(menu.pos());return record==null?null:new DisposalConstructionPreview.ConfigurationPlan(body.getUUID(),epoch,menu.pos(),record.prototypeId(),candidates.get(candidateIndex));
    }

    private void step(int delta) { refreshCandidates(); if(!candidates.isEmpty()) candidateIndex=Math.floorMod(candidateIndex+delta,candidates.size()); }
    private void confirmRotation() { refreshCandidates(); if(!candidates.isEmpty()) sendCommand("@rotate_confirm:"+candidateIndex); }
    private void sendCommand(String command) { PacketDistributor.sendToServer(new DisposalBuildSelection(menu.containerId,menu.session(),sequence++,command)); }
    private void sendConfig() { PacketDistributor.sendToServer(new DisposalTubeConfigureRequest(menu.containerId,menu.session(),sequence++,menu.configuredTubeId(),menu.configuredTubeRevision(),tagField.getValue(),filterField.getValue())); }

    private void refreshCandidates() {
        Minecraft mc=Minecraft.getInstance(); if(!menu.editing()||mc.level==null)return;
        var record=recordAt(menu.pos()); if(record==null||record.revision()!=menu.configuredTubeRevision()||!record.frame().equals(menu.tubeFrame())){candidates=List.of();return;}
        var id=ResourceLocation.tryParse(record.prototypeId());var data=id==null?null:PrototypeRuntime.clientDisposals().get(id);
        if(data==null||data.tube()==null){candidates=List.of();return;}
        var reciprocal=EnumSet.noneOf(Direction.class);
        for(Direction d:Direction.values()){
            BlockPos p=menu.pos().relative(d); if(!mc.level.hasChunkAt(p))continue;
            var n=recordAt(p); if(n==null||!n.anchored())continue;
            var nid=ResourceLocation.tryParse(n.prototypeId());var nd=nid==null?null:PrototypeRuntime.clientDisposals().get(nid);
            if(nd!=null&&nd.tube()!=null&&DisposalTubeRouting.worldConnections(nd.tube(),n.frame()).contains(DisposalDirection.fromMC(d.getOpposite())))reciprocal.add(d);
        }
        candidates=DisposalOrientationCandidates.candidates(data.tube(),menu.tubeFrame(),reciprocal);
        if(observedRevision!=menu.configuredTubeRevision()){
            observedRevision=menu.configuredTubeRevision();candidateIndex=0;
            for(int i=0;i<candidates.size();i++)if(candidates.get(i).equals(menu.tubeFrame())){candidateIndex=i;break;}
        }
        if(!candidates.isEmpty())candidateIndex=Math.floorMod(candidateIndex,candidates.size());
    }
    private com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord recordAt(BlockPos pos) {
        var level=Minecraft.getInstance().level;
        return level==null||!level.hasChunkAt(pos)?null:DisposalConstructionClient.tubeRecord(pos,level.dimension().location()).orElse(null);
    }
    private int lostConnections(DisposalOrientation frame) {
        var record=recordAt(menu.pos()); if(record==null)return 0;
        var id=ResourceLocation.tryParse(record.prototypeId());var data=id==null?null:PrototypeRuntime.clientDisposals().get(id);if(data==null||data.tube()==null)return 0;
        var reciprocal=EnumSet.noneOf(Direction.class);
        for(Direction d:Direction.values()){
            var neighbor=recordAt(menu.pos().relative(d));if(neighbor==null||!neighbor.anchored())continue;
            var nid=ResourceLocation.tryParse(neighbor.prototypeId());var nd=nid==null?null:PrototypeRuntime.clientDisposals().get(nid);
            if(nd!=null&&nd.tube()!=null&&DisposalTubeRouting.worldConnections(nd.tube(),neighbor.frame()).contains(DisposalDirection.fromMC(d.getOpposite())))reciprocal.add(d);
        }
        return lostConnectionCount(DisposalTubeRouting.worldConnections(data.tube(),menu.tubeFrame()),
                DisposalTubeRouting.worldConnections(data.tube(),frame),reciprocal);
    }
    static int lostConnectionCount(Set<DisposalDirection> current,Set<DisposalDirection> proposed,Set<Direction> reciprocalNeighbours){
        int lost=0;for(Direction d:reciprocalNeighbours){var face=DisposalDirection.fromMC(d);if(current.contains(face)&&!proposed.contains(face))lost++;}return lost;
    }

    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) { g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF20242A); }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        if(menu.editing()){
            refreshCandidates();g.drawString(font,"Disposal tube configuration",8,8,0xFFFFFF);
            if(candidates.isEmpty())g.drawString(font,"No loaded prototype/record preview",8,28,0xFFAA55);
            else {
                var frame=candidates.get(candidateIndex);
                g.drawString(font,"Current F "+menu.tubeFrame().forward().getName()+" U "+menu.tubeFrame().up().getName(),8,28,0xDDDDDD);
                g.drawString(font,"Proposed F "+frame.forward().getName()+" U "+frame.up().getName(),8,40,0x55FFFF);
                int lost=lostConnections(frame);if(lost>0)g.drawString(font,"Warning: loses "+lost+" reciprocal connection(s)",8,91,0xFFAA55);
            }
            if(menu.tubeAnchored())g.drawString(font,"Unanchor with a wrench first",8,58,0xFFAA55);
            g.drawString(font,"Tagger tag",8,94,0xDDDDDD);g.drawString(font,"Router filter",8,122,0xDDDDDD);
        } else g.drawString(font,"Use construction controls in-world",8,8,0xFFFFFF);
        g.drawString(font,menu.actionResult(),8,170,0xAAAAAA);
    }
}

