package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlantSpeciesMutationTest {
    private static ResourceLocation id(String s) { return ResourceLocation.fromNamespaceAndPath("test", s); }
    private static SeedProfile profile(String name, int stages, Map<ResourceLocation, SeedProfile.Chemical> chemicals, List<ResourceLocation> targets) {
        return new SeedProfile(id(name+"packet"), id(name+"product"), name,
                new SeedProfile.Biology(11,2,3,4,5,6,7,8,9,10,12),
                new SeedProfile.Lifecycle(stages,20,21,22,23,24,SeedProfile.Harvest.SELF_HARVEST,false,true),
                 chemicals, Map.of(GasType.OXYGEN, .2f), Map.of(GasType.AMMONIA,.3f), targets, SeedProfile.Scream.DISABLED, "test-"+name+"-name");
    }
    @Test void speciesChangeTakesSpeciesGenesAndReconcilesChemistryWithoutMutatingSnapshots() {
        var oldInherent=id("old_inherent"); var existing=id("existing_inherent"); var acquired=id("acquired"); var targetNew=id("target_new");
        var originalChem=new LinkedHashMap<ResourceLocation,SeedProfile.Chemical>();
        originalChem.put(oldInherent,new SeedProfile.Chemical(1,2,3,true));
        originalChem.put(existing,new SeedProfile.Chemical(4,8,2,false));
        originalChem.put(acquired,new SeedProfile.Chemical(5,9,2,false));
        var targetChem=new LinkedHashMap<ResourceLocation,SeedProfile.Chemical>();
        targetChem.put(existing,new SeedProfile.Chemical(10,20,2,true)); targetChem.put(targetNew,new SeedProfile.Chemical(2,6,3,true));
        var old=profile("old",3,originalChem,List.of(id("oldtarget"),id("oldtarget")));
        var target=profile("target",7,targetChem,List.of(id("next"),id("next"),id("last")));
        var oldSpecies=id("old_species"); var targetSpecies=id("target_species"); var value=new GeneticValue(oldSpecies,old);
        var result=value.transformSpecies(targetSpecies,target); var changed=result.profile();
        assertEquals(targetSpecies,result.species()); assertEquals(target.packet(),changed.packet()); assertEquals(target.product(),changed.product());
         assertEquals(target.visualKey(),changed.visualKey()); assertEquals(target.mutationTargets(),changed.mutationTargets());
         assertEquals(target.sourceName(),changed.sourceName());
        assertEquals(old.biology(),changed.biology()); assertSame(old.biology(),changed.biology());
        assertEquals(new SeedProfile.Lifecycle(7,20,21,22,23,24,SeedProfile.Harvest.SELF_HARVEST,false,true),changed.lifecycle());
        assertEquals(old.consumeGasses(),changed.consumeGasses()); assertEquals(old.exudeGasses(),changed.exudeGasses());
        assertFalse(changed.chemicals().containsKey(oldInherent)); assertEquals(originalChem.get(existing),changed.chemicals().get(existing));
        assertEquals(originalChem.get(acquired),changed.chemicals().get(acquired)); assertEquals(targetChem.get(targetNew),changed.chemicals().get(targetNew));
        assertFalse(changed.chemicals().get(existing).inherent());
        assertEquals(originalChem,old.chemicals()); assertEquals(List.of(id("oldtarget"),id("oldtarget")),old.mutationTargets());
        assertEquals(targetChem,target.chemicals()); assertEquals(oldSpecies,value.species());
        assertThrows(UnsupportedOperationException.class,()->changed.chemicals().clear());
        assertThrows(UnsupportedOperationException.class,()->changed.mutationTargets().clear());
    }
}
