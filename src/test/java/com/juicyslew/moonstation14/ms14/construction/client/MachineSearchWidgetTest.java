package com.juicyslew.moonstation14.ms14.construction.client;

import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineSearchList;
import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineSearchWidgetTest {
    record Recipe(String id,String family,String name) { }
    static class DeterministicFont extends Font {
        DeterministicFont(){super((ResourceLocation id)->(FontSet)null,false);}
        @Override public int width(String text){return text.length()*6;}
        @Override public String plainSubstrByWidth(String text,int width){return text.substring(0,Math.min(text.length(),Math.max(0,width/6)));}
    }
    private static final List<Recipe> CATALOG;
    static {
        List<Recipe> recipes=new ArrayList<>();
        recipes.add(new Recipe("wall_a","Walls","Wall A"));recipes.add(new Recipe("wall_b","Walls","Wall B"));
        recipes.add(new Recipe("pipe_a","Pipes","Pipe A"));recipes.add(new Recipe("pipe_b","Pipes","Pipe B"));
        recipes.add(new Recipe("plastic","Disposal","Plastic disposal"));
        IntStream.range(0,12).forEach(i->recipes.add(new Recipe("extra_"+i,"Extras","Extra recipe "+i)));
        CATALOG=List.copyOf(recipes);
    }

    private static List<FilteredListModel.Entry> entries(String family,boolean inFamily,String query){
        return ConstructionBrowserModel.catalogEntries(CATALOG,Recipe::id,Recipe::family,Recipe::name,
                family,inFamily,!query.isBlank(),value->value);
    }

    @Test void realEditBoxResponderSearchesGloballyFromRememberedFamiliesAndClearsBack() {
        AtomicReference<String> query=new AtomicReference<>("");
        FilteredListModel model=new FilteredListModel(64,4);
        final String[] family={"Walls"};final boolean[] inFamily={true};final long[] revision={0};
        MachineSearchList[] holder=new MachineSearchList[1];
        holder[0]=new MachineSearchList(0,0,140,4,Component.literal("recipes"),model,id->{},value->{
            query.set(value);holder[0].snapshot(++revision[0],entries(family[0],inFamily[0],query.get()));
        },new DeterministicFont());
        holder[0].snapshot(++revision[0],entries(family[0],true,""));
        var box=holder[0].searchBox();box.setFocused(true);
        for(char c:"pipe".toCharArray())assertTrue(box.charTyped(c,0));
        assertTrue(model.matchCount()>0);assertTrue(model.visibleAt(0).id().startsWith("pipe_"));
        assertEquals("pipe",query.get());
        // An unchanged Browser-style refresh does not publish a new snapshot or replace the responder-owned query.
        long before=revision[0];holder[0].snapshot(before,entries(family[0],inFamily[0],query.get()));assertEquals("pipe",query.get());assertEquals(before,model.revision());
        // Actual EditBox keyboard deletion consults Screen.hasControlDown(), which
        // requires a live Minecraft window. Exercise the production responder
        // path without claiming that headless unit tests cover keyboard dispatch.
        box.setValue("pl");box.setValue("");
        assertTrue(query.get().isEmpty());assertTrue(IntStream.range(0,model.visibleCount()).mapToObj(i->model.visibleAt(i).id()).anyMatch("wall_a"::equals));

        family[0]="Disposal";holder[0].snapshot(++revision[0],entries(family[0],true,""));
        for(char c:"plastic".toCharArray())assertTrue(box.charTyped(c,0));
        assertEquals("plastic",model.visibleAt(0).id());
        box.setValue("plasti");
        assertTrue(query.get().equals("plasti"));
    }

    @Test void viewportResizeAndActualMouseScrollReachAndSelectLastEntry() {
        FilteredListModel model=new FilteredListModel(64,4);
        MachineSearchList list=new MachineSearchList(0,0,120,4,Component.literal("recipes"),model,id->{},q->{},new DeterministicFont());
        List<FilteredListModel.Entry> rows=IntStream.range(0,15).mapToObj(i->new FilteredListModel.Entry("r"+i,"Recipe "+i)).toList();
        list.snapshot(1,rows);model.filter("Recipe");
        for(int count:new int[]{1,2,5}){
            list.resizeViewport(120,count*MachineSearchList.ROW_HEIGHT);
            assertEquals(count,model.pageSize());
            while(model.first()<model.matchCount()-count)assertTrue(list.mouseScrolled(5,5,0,-1));
            assertEquals("r14",model.visibleAt(count-1).id());
         // AbstractWidget.mouseClicked accesses the client sound manager after
         // selection. Keep this headless test focused on actual scroll state and
         // the same stable-ID selection model without a fake Minecraft singleton.
         assertTrue(model.select("r14"));
            assertEquals("r14",model.selectedId());
            list.resizeViewport(120,MachineSearchList.ROW_HEIGHT);
            assertEquals("r14",model.visibleAt(0).id());
        }
    }

    @Test void compactBrowserSearchBoundsAreNotConsumedByTitleDrag() {
        var l=ConstructionBrowserLayout.calculate(20,30,240,140,false,false,false,false);
        var geometry=new WindowGeometryController(400,300,l.x(),l.y(),l.width(),l.height(),180,112,false);
        assertFalse(geometry.beginPointer(l.search().x()+2,l.search().y()+2,0,13,18));
        assertTrue(geometry.beginPointer(l.x()+20,l.y()+5,0,13,18));
    }

    @Test void browserPointerBridgeFocusesTheRealEditBoxAndTypingFilters() {
        AtomicReference<String> query=new AtomicReference<>("");
        var model=new FilteredListModel(16,2);
        var list=new MachineSearchList(0,0,120,2,Component.literal("recipes"),model,id->{},query::set,new DeterministicFont());
        list.snapshot(1,List.of(new FilteredListModel.Entry("pipe","Pipe"),new FilteredListModel.Entry("wall","Wall")));
        var box=list.searchBox();
        assertFalse(box.isFocused());
        list.focusSearch();
        assertTrue(box.isFocused());
        assertTrue(box.charTyped('p',0));
        assertEquals("p",query.get());
        assertEquals("pipe",model.visibleAt(0).id());
    }

    @Test void rowIconPresentationIsOptionalAndKeepsStableIds() {
        var model=new FilteredListModel(8,2);
        var list=new MachineSearchList(0,0,120,2,Component.literal("recipes"),model,id->{},q->{},new DeterministicFont());
        var rows=List.of(new FilteredListModel.Entry("moonstation14:girder","Girder"));
        list.snapshot(1,rows);
        assertNull(list.rowIconFor("moonstation14:girder"));
        var icon=ResourceLocation.fromNamespaceAndPath("moonstation14","steel_wall_girder_block");
        list.snapshot(2,rows,id->id.equals("moonstation14:girder")?icon:null,16);
        assertEquals(icon,list.rowIconFor("moonstation14:girder"));
        assertEquals(16,list.contentIndent());
    }

    @Test void typedIconDescriptorFlowsThroughTheActualRowRenderCallback() {
        var model=new FilteredListModel(8,2);
        var list=new MachineSearchList(0,0,120,2,Component.literal("recipes"),model,id->{},q->{},new DeterministicFont());
        var descriptor=new Object();var rendered=new AtomicReference<Object>();
        var rows=List.of(new FilteredListModel.Entry("shape","Disposal Bend"));
        list.snapshotWithIcons(1,rows,id->descriptor,(graphics,icon,x,y)->rendered.set(icon),16);
        list.renderIconForRow(null,"shape",4,3);
        assertSame(descriptor,rendered.get());
    }
}
