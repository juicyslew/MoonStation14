package com.juicyslew.moonstation14.ms14.construction.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineSearchList;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionBrowserModelTest {
    private record Recipe(String id,String family,String label){}
    private static final List<Recipe> RECIPES=List.of(new Recipe("pipe","pipes","Pipe"),new Recipe("bend","pipes","Bend"),new Recipe("wall","walls","Wall"),new Recipe("rod","items","Metal Rod"));
    @Test void groupsMetadataFamiliesAndFlattensSearchResults(){
        var rows=ConstructionBrowserModel.rows(RECIPES,Recipe::family,Recipe::label,false);
        assertEquals(3,rows.size());assertTrue(rows.get(0).isFamily());assertEquals("pipes",rows.get(0).label());assertEquals("wall",rows.get(1).recipe().id());assertEquals("rod",rows.get(2).recipe().id());
        var search=ConstructionBrowserModel.rows(RECIPES.stream().filter(r->r.label().contains("Bend")).toList(),Recipe::family,Recipe::label,true);
        assertEquals("Bend",search.getFirst().label());assertFalse(search.getFirst().isFamily());
    }
    @Test void viewportBoundsScrollAndRejectsClicksOutsideClippedRows(){
        assertEquals(3,ConstructionBrowserModel.boundedScroll(30,10,7));
        assertEquals(-1,ConstructionBrowserModel.visibleIndex(90,100,144,22,0,5));
        assertEquals(2,ConstructionBrowserModel.visibleIndex(143,100,166,22,1,5));
        assertEquals(-1,ConstructionBrowserModel.visibleIndex(166,100,166,22,1,5));
    }
    @Test void categoryCycleLabelTracksAllFourFilters(){
        int category=0;assertEquals("All",ConstructionBrowserModel.CATEGORIES.get(category));
        for(String expected:List.of("Favorites","Structures","Items","All")){category=ConstructionBrowserModel.nextCategory(category,1);assertEquals(expected,ConstructionBrowserModel.CATEGORIES.get(category));}
    }
    @Test void liveSearchResponderRefreshesFlattenedRootAndKeepsQueryAcrossStableAndLateSnapshots(){
        var catalog=new ArrayList<>(RECIPES);catalog.add(new Recipe("plastic-wall","structures","Plastic Wall"));
        var model=new FilteredListModel(32,2);long[] revision={0};String[] query={""};String[] family={null};boolean[] inFamily={false};
        var lastEntries=new ArrayList<List<FilteredListModel.Entry>>();
        Runnable refresh=()->{
            var entries=ConstructionBrowserModel.catalogEntries(catalog,Recipe::id,Recipe::family,Recipe::label,
                    family[0],inFamily[0],!query[0].isBlank(),String::toUpperCase);
            if(lastEntries.isEmpty()||!entries.equals(lastEntries.get(0))){if(lastEntries.isEmpty())lastEntries.add(entries);else lastEntries.set(0,entries);model.snapshot(++revision[0],entries);}
        };
        refresh.run();
        assertTrue(model.visibleAt(0).id().startsWith("@family:"));
        MachineSearchList.applyQuery(model,"p",value->{query[0]=value;refresh.run();});
        MachineSearchList.applyQuery(model,"pl",value->{query[0]=value;refresh.run();});
        MachineSearchList.applyQuery(model,"plastic",value->{query[0]=value;refresh.run();});
        assertEquals("Plastic Wall",model.visibleAt(0).label());
        long stableRevision=model.revision();String stableSelection=model.selectedId();refresh.run();
        assertEquals(stableRevision,model.revision());assertEquals("plastic",query[0]);assertEquals(stableSelection,model.selectedId());

        catalog.add(new Recipe("plastic-floor","structures","Plastic Floor"));
        refresh.run();
        assertEquals(2,model.matchCount());assertEquals("plastic",query[0]);
        MachineSearchList.applyQuery(model,"",value->{query[0]=value;refresh.run();});
        assertTrue(model.visibleAt(0).id().startsWith("@family:"));
        family[0]="pipes";inFamily[0]=true;
        MachineSearchList.applyQuery(model,"metal",value->{query[0]=value;refresh.run();});
        // Search is global even when a family remains selected; the category is
        // remembered for the empty-query browse state, not a search constraint.
        assertEquals("Metal Rod",model.visibleAt(0).label());
        MachineSearchList.applyQuery(model,"pipe",value->{query[0]=value;refresh.run();});
        assertEquals("Pipe",model.visibleAt(0).label());
    }
}
