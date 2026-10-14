package com.juicyslew.moonstation14.ms14.construction.client;

import java.util.*;
import java.util.function.Function;
import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;

/** Pure family-row and viewport model shared by the browser and focused unit tests. */
final class ConstructionBrowserModel {
    static final List<String> CATEGORIES=List.of("All","Favorites","Structures","Items");
    static int nextCategory(int current,int direction){return Math.floorMod(current+direction,CATEGORIES.size());}
    record Row<F,R>(String label, F family, R recipe) { boolean isFamily(){return recipe==null;} }
    static <R> List<FilteredListModel.Entry> catalogEntries(Collection<R> recipes,
            Function<R,String> id,Function<R,String> family,Function<R,String> label,
            String selectedFamily,boolean inFamily,boolean searching,Function<String,String> familyLabel) {
        LinkedHashMap<String,List<R>> groups=new LinkedHashMap<>();
        for(R recipe:recipes)groups.computeIfAbsent(family.apply(recipe),key->new ArrayList<>()).add(recipe);
        List<FilteredListModel.Entry> result=new ArrayList<>();
        if(inFamily&&selectedFamily!=null)result.add(new FilteredListModel.Entry("@back","‹ Back"));
        for(var group:groups.entrySet()) {
            if(inFamily&&!searching&&!Objects.equals(selectedFamily,group.getKey()))continue;
            if(!inFamily&&!searching&&group.getValue().size()>1) {
                result.add(new FilteredListModel.Entry("@family:"+group.getKey(),familyLabel.apply(group.getKey())));
                continue;
            }
            for(R recipe:group.getValue())result.add(new FilteredListModel.Entry(id.apply(recipe),label.apply(recipe)));
        }
        return List.copyOf(result);
    }
    static <F,R> List<Row<F,R>> rows(Collection<R> recipes, Function<R,F> family, Function<R,String> label, boolean flatten) {
        LinkedHashMap<F,List<R>> groups=new LinkedHashMap<>();
        for(R r:recipes)groups.computeIfAbsent(family.apply(r),k->new ArrayList<>()).add(r);
        List<Row<F,R>> result=new ArrayList<>();
        for(var e:groups.entrySet())if(!flatten&&e.getValue().size()>1)result.add(new Row<>(String.valueOf(e.getKey()),e.getKey(),null));else for(R r:e.getValue())result.add(new Row<>(label.apply(r),e.getKey(),r));
        return List.copyOf(result);
    }
    static int visibleIndex(double y,int top,int bottom,int rowHeight,int scroll,int rowCount){
        if(y<top||y>=bottom||rowHeight<=0)return -1;int i=scroll+(int)((y-top)/rowHeight);return i>=0&&i<rowCount?i:-1;
    }
    static int boundedScroll(int scroll,int rowCount,int visibleRows){return Math.max(0,Math.min(scroll,Math.max(0,rowCount-visibleRows)));}
}

