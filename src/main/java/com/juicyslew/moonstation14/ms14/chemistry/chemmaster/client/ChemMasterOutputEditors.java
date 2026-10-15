package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.mixin.client.EditBoxDisplayAccessor;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Four vanilla editing controls; focus and rendering are hosted by ChemMasterOverlay. */
final class ChemMasterOutputEditors {
    enum Field { LABEL, COUNT, PILL_DOSE, BOTTLE_DOSE }
    private final EditBox[] fields = new EditBox[Field.values().length];
    private final boolean[] editable = new boolean[Field.values().length];
    private Field focused;
    private boolean syncing;

    ChemMasterOutputEditors(ChemMasterOutputForm form) {
        for (Field field : Field.values()) {
            int max = field == Field.LABEL ? ChemMasterOutputForm.LABEL_LIMIT : 10;
            EditBox edit = new EditBox(Minecraft.getInstance().font, 0, 0, 1, 1, Component.empty());
            edit.setMaxLength(max);
        edit.setValue(value(form, field));
            edit.setBordered(false);
            edit.setResponder(v -> {
                if (syncing) return;
                switch (field) {
                    case LABEL -> form.editLabel(v);
                    case COUNT -> form.editPillCount(v);
                    case PILL_DOSE -> form.editPillDose(v, Integer.MAX_VALUE);
                    case BOTTLE_DOSE -> form.editBottleDose(v, Integer.MAX_VALUE);
                }
            });
            fields[field.ordinal()] = edit;
        }
    }
    EditBox get(Field field) { return fields[field.ordinal()]; }
    Field focused() { return focused; }
    boolean hasFocus() { return focused != null; }
    boolean editable(Field field){return editable[field.ordinal()];}
    static boolean editable(Field field,ChemMasterWindowState.OutputKind kind,int pillDoseLimit){
        return switch(field){case LABEL->true;case COUNT,PILL_DOSE->kind==ChemMasterWindowState.OutputKind.PILL_CANISTER&&pillDoseLimit>0;
            case BOTTLE_DOSE->kind==ChemMasterWindowState.OutputKind.BOTTLE;};
    }
    void focus(Field next) { if (focused != null) get(focused).setFocused(false); focused=next; if(next!=null)get(next).setFocused(true); }
    void clearFocus() { focus(null); }
    void sync(ChemMasterOutputForm form, ChemMasterOutputLayout.Geometry g,
              int maxCount, int maxPillDose, int maxBottleDose,ChemMasterWindowState.OutputKind kind,int lineHeight) {
        ChemMasterOutputLayout.Rect[] rects={g.label(),g.pillCount(),g.pillDose(),g.bottleDose()};
        for(Field field:Field.values()){
            EditBox e=get(field); var r=rects[field.ordinal()];
            var viewport=ChemMasterTextFieldPaint.viewport(r,lineHeight);
            e.setX(viewport.x());e.setY(viewport.y());e.setWidth(Math.max(1,viewport.width()));e.setHeight(Math.max(1,viewport.height()));
            String v=value(form,field);
            if(!e.getValue().equals(v)){syncing=true;try{e.setValue(v);}finally{syncing=false;}}
            editable[field.ordinal()]=editable(field,kind,maxPillDose);
            if(!editable[field.ordinal()]&&focused==field)clearFocus();
            e.setEditable(editable[field.ordinal()]);
            final int limit=switch(field){case COUNT->maxCount;case PILL_DOSE->maxPillDose;case BOTTLE_DOSE->maxBottleDose;case LABEL->Integer.MAX_VALUE;};
            if(field!=Field.LABEL)e.setFilter(candidate->candidate.isEmpty()||candidate.length()<=10&&candidate.chars().allMatch(Character::isDigit)&&within(candidate,Integer.MAX_VALUE));
        }
    }
    ChemMasterTextFieldPaint.Snapshot snapshot(Field field, int tick) {
        EditBox e=get(field); var access=(EditBoxDisplayAccessor)e;
        return new ChemMasterTextFieldPaint.Snapshot(e.getValue(),e.getCursorPosition(),access.ms14$highlightPos(),
                access.ms14$displayPos(),e.isFocused(),editable(field),(tick/6)%2==0,tick);
    }
    private static String value(ChemMasterOutputForm f,Field field){return switch(field){case LABEL->f.label();case COUNT->f.pillCount();case PILL_DOSE->f.pillDose();case BOTTLE_DOSE->f.bottleDose();};}
    private static boolean within(String value,int limit){try{return Integer.parseInt(value)<=Math.max(0,limit);}catch(NumberFormatException e){return false;}}
}
