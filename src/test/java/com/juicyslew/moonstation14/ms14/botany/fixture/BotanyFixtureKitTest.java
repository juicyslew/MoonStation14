package com.juicyslew.moonstation14.ms14.botany.fixture;

import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotanyFixtureKitTest {
    @Test void selectsOnlyCorePacketsFromActivatedCatalogAndNamesMissingCoreSeed() {
        var source=BotanyCatalogCandidateTest.resources();
        var manager=new PrototypeManager(); manager.register(ModSeedProfiles.TYPE);
        manager.reload(ModSeedProfiles.TYPE,BotanyCatalogCandidateTest.mainResources("seed_profile",source.keySet().stream().map(id->id.getPath()).toArray(String[]::new)));
        var all=manager.snapshot(ModSeedProfiles.TYPE);
        assertEquals(71,all.asMap().keySet().stream().filter(id->!id.equals(ModSeedProfiles.seedId("defaults"))).count());
        var packets=BotanyFixtureCommands.coreKitPackets(all);
        assertEquals(5,packets.size());
        assertEquals(java.util.List.of("wheat_seeds","tomato_seeds","potato_seeds","poppy_seeds","aloe_seeds"),packets.stream().map(id->id.getPath()).toList());
        var missing=new java.util.LinkedHashMap<>(all.asMap()); missing.remove(ModSeedProfiles.seedId("aloe"));
        var reduced=new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(missing);
        var failure=assertThrows(IllegalStateException.class,()->BotanyFixtureCommands.coreKitPackets(reduced));
        assertTrue(failure.getMessage().contains("aloe"),failure.getMessage());
    }
}
