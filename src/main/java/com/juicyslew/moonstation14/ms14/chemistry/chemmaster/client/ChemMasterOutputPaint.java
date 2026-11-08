package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Ordered output controls and authoritative package contents; no inventory is predicted here. */
public final class ChemMasterOutputPaint {
    private ChemMasterOutputPaint() { }
    public static void paint(OrderedPaint p, ChemMasterWindowState state, ChemMasterOutputForm.State formState,
                      ChemMasterOutputLayout.Geometry g, int mx, int my, boolean pending,
                      int selectedPillType, ChemMasterControlRequest.DrawSource drawSource,java.util.List<ChemMasterPaint.DisplayRow> displayRows) {
        var source=drawSource==ChemMasterControlRequest.DrawSource.BUFFER?state.bufferReagents():state.inputReagents();
        long sourceCents=source.stream().mapToLong(ChemMasterWindowState.ReagentRow::amountCents).sum();
        p.text(Component.literal("Draw from: "+(drawSource==ChemMasterControlRequest.DrawSource.BUFFER?"Buffer":"Input beaker")+
                "  "+ReagentUnits.formatCents(sourceCents)+"u"),g.sourceCaption().x(),g.sourceCaption().y()+1,MachineWindowRenderer.TEXT,true);
        var half=g.sourceSelection().width()/2;
        button(p,"Buffer",new ChemMasterOutputLayout.Rect(g.sourceSelection().x(),g.sourceSelection().y(),half,g.sourceSelection().height()),mx,my,
                drawSource==ChemMasterControlRequest.DrawSource.BUFFER,!pending);
        button(p,"Input",new ChemMasterOutputLayout.Rect(g.sourceSelection().x()+half,g.sourceSelection().y(),g.sourceSelection().width()-half,g.sourceSelection().height()),mx,my,
                drawSource==ChemMasterControlRequest.DrawSource.INPUT,!pending);
        var slot=state.outputSlot();
        String fallback=switch(slot.kind()){case EMPTY->"No output container";case BOTTLE->"Bottle";case PILL_CANISTER->"Pill canister";};
        String output=slot.itemName().map(Component::getString).orElse(fallback);
        if(slot.kind()==ChemMasterWindowState.OutputKind.BOTTLE)
            output+="  "+ReagentUnits.formatCents(slot.volumeCents())+" / "+ReagentUnits.formatCents(slot.capacityCents())+"u";
        else if(slot.kind()==ChemMasterWindowState.OutputKind.PILL_CANISTER)
            output+="  "+slot.pills().size()+" pills  "+slot.freeCells()+" free";
        p.text(Component.literal(ChemMasterPaint.fitText(p,"Output: "+output,g.outputCaption().width())),g.outputCaption().x(),g.outputCaption().y()+1,MachineWindowRenderer.MUTED_TEXT,true);
        button(p,"Eject",g.eject(),mx,my,false,slot.kind()!=ChemMasterWindowState.OutputKind.EMPTY&&!pending);
        int sourceUnits=(int)Math.min(Integer.MAX_VALUE,sourceCents/100);
        int bottleFree=(int)Math.min(Integer.MAX_VALUE,Math.max(0,slot.capacityCents()-slot.volumeCents())/100);
        boolean canAdjustPills=!pending&&slot.kind()==ChemMasterWindowState.OutputKind.PILL_CANISTER&&state.pillDosageLimit()>0&&sourceUnits>0&&slot.freeCells()>0;
        boolean canAdjustDose=!pending&&Math.min(sourceUnits,state.pillDosageLimit())>0;
        boolean canAdjustBottle=!pending&&slot.kind()==ChemMasterWindowState.OutputKind.BOTTLE&&sourceUnits>0&&bottleFree>0;

        for(int i=0;i<g.pills().size();i++){
            var cell=g.pills().get(i);int x=cell.x(),y=cell.y(),right=x+cell.width(),bottom=y+cell.height();
            if(cell.width()<1||cell.height()<1)continue;
            boolean paletteEnabled=slot.kind()==ChemMasterWindowState.OutputKind.PILL_CANISTER&&!pending;
            if(i==selectedPillType||(paletteEnabled&&cell.contains(mx,my))){int color=i==selectedPillType?0xffe5c45c:0xff92b4bc;
                p.fill(x,y,right,y+1,color);p.fill(x,bottom-1,right,bottom,color);p.fill(x,y,x+1,bottom,color);p.fill(right-1,y,right,bottom,color);}
            p.fill(x+1,y+1,right-1,bottom-1,paletteEnabled?0xff26363b:0xff202b2f);
            ChemMasterPillArt.paintUniform(p,i,cell.x()+2,cell.y()+2,Math.max(0,cell.width()-4),Math.max(0,cell.height()-4),paletteEnabled?0xffffffff:0xff7b898d);
        }
        button(p,"−",g.pillCountMinus(),mx,my,false,canAdjustPills);
        button(p,"+",g.pillCountPlus(),mx,my,false,canAdjustPills);
        p.text(Component.literal("Count"),g.pillCountLabel().x(),g.pillCountLabel().y()+1,MachineWindowRenderer.MUTED_TEXT,true);
        button(p,"−",g.pillDoseMinus(),mx,my,false,canAdjustDose);
        button(p,"+",g.pillDosePlus(),mx,my,false,canAdjustDose);
        p.text(Component.literal("Dose"),g.pillDoseLabel().x(),g.pillDoseLabel().y()+1,MachineWindowRenderer.MUTED_TEXT,true);
        boolean canPills=slot.kind()==ChemMasterWindowState.OutputKind.PILL_CANISTER&&formState.canCreatePills()&&!pending;
        button(p,"Create",g.createPills(),mx,my,false,canPills);
        button(p,"−",g.bottleDoseMinus(),mx,my,false,canAdjustBottle);
        button(p,"+",g.bottleDosePlus(),mx,my,false,canAdjustBottle);
        p.text(Component.literal("Bottle"),g.bottleDoseLabel().x(),g.bottleDoseLabel().y()+1,MachineWindowRenderer.MUTED_TEXT,true);
        button(p,"Fill",g.fillBottle(),mx,my,false,!pending&&slot.kind()==ChemMasterWindowState.OutputKind.BOTTLE&&formState.canFillBottle());
        paintContents(p,slot,g.outputInfo(),displayRows);
        state.rejectionReason().ifPresent(reason->{var area=g.outputInfo();if(area.height()>p.lineHeight())p.text(Component.literal(ChemMasterPaint.fitText(p,reason,area.width()-4)),area.x()+2,area.y()+area.height()-p.lineHeight()-1,0xffff7777,true);});
    }
    private static void paintContents(OrderedPaint p,ChemMasterWindowState.OutputSlot slot,ChemMasterOutputLayout.Rect area,java.util.List<ChemMasterPaint.DisplayRow> displayRows){
        if(area.width()<2||area.height()<2)return;
        p.fill(area.x(),area.y(),area.x()+area.width(),area.y()+area.height(),0x6611191d);
        p.push();try{
            int line=p.lineHeight()+1,y=area.y()+2;
            if(slot.kind()==ChemMasterWindowState.OutputKind.PILL_CANISTER){
                int cols=Math.max(1,slot.width()),rows=Math.max(1,slot.height());int cw=Math.max(1,area.width()/cols),ch=Math.max(1,Math.min(line+2,area.height()/rows));
                for(int cy=0;cy<rows;cy++)for(int cx=0;cx<cols;cx++){
                    int x=area.x()+cx*cw,yy=area.y()+cy*ch;p.fill(x,yy,x+cw-1,yy+ch-1,0xff26363b);
                    final int fx=cx,fy=cy;var pill=slot.pills().stream().filter(d->d.x()==fx&&d.y()==fy).findFirst().orElse(null);
                    if(pill!=null&&ch>2){
                        int iconBoxWidth=Math.max(0,Math.min(cw/2,cw-4));
                        if(iconBoxWidth>0)ChemMasterPillArt.paintUniform(p,pill.type(),x+1,yy+1,iconBoxWidth,Math.max(0,ch-2),0xffffffff);
                        if(cw>iconBoxWidth+3){
                            String label=pill.label().orElse("Pill "+(pill.type()+1));
                            int labelX=x+iconBoxWidth+2;
                            p.text(Component.literal(ChemMasterPaint.fitText(p,label,Math.max(0,cw-(labelX-x)-1))),labelX,yy+1,MachineWindowRenderer.TEXT,false);
                        }
                    }
                }
            }else if(slot.kind()==ChemMasterWindowState.OutputKind.BOTTLE){
                for(var row:slot.reagents()){
                    var display=displayRows.stream().filter(d->d.row().reagent().location().equals(row.reagent().location())).findFirst().orElse(null);
                    int color=display==null?0xff82959b:display.color();
                    p.fill(area.x()+2,y+2,area.x()+6,y+6,color);
                    String name=display==null?ChemMasterWorkspace.localizedName(row.reagent().location()):display.name().getString();
                    String s=name+"  "+ReagentUnits.formatCents(row.amountCents())+"u";
                    p.text(Component.literal(ChemMasterPaint.fitText(p,s,area.width()-12)),area.x()+9,y,MachineWindowRenderer.TEXT,false);y+=line;if(y>=area.y()+area.height())break;
                }
            }
        }finally{p.pop();}
    }
    private static void button(OrderedPaint p,String label,ChemMasterOutputLayout.Rect r,int mx,int my,boolean selected,boolean enabled){
        if(r.width()>2&&r.height()>2)MachineActionButtonRenderer.render(p,label,r.x(),r.y(),r.width(),r.height(),enabled&&r.contains(mx,my),enabled,selected,.65f);
    }
}
