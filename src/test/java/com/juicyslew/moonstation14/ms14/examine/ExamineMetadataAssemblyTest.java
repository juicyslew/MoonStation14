package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamineMetadataAssemblyTest {
    @Test
    void metadataUsesNativeFallbackAndDoesNotOverrideCustomIdentity() {
        var collector = new ExamineCollector(false, Component.literal("Minecraft name"));
        collector.metadata(new ExamineMetadata("source name", "source description"));
        assertEquals("source name", collector.name().getString());
        assertEquals("source description", collector.description().getString());
        collector.preserveName();
        collector.metadata(new ExamineMetadata("generic character", "updated description"));
        assertEquals("source name", collector.name().getString());
        assertEquals("updated description", collector.description().getString());

        var nativeFallback = new ExamineCollector(true, Component.literal("Native"));
        nativeFallback.metadata(ExamineMetadata.EMPTY);
        assertEquals("Native", nativeFallback.name().getString());
        assertTrue(nativeFallback.description().getString().isEmpty());
    }

    @Test
    void detailRowsAreFilteredBeforeSortedOutput() {
        var collector = new ExamineCollector(false, Component.empty());
        collector.detailEntry(Component.literal("sensitive"), "battery", 50);
        collector.publicEntry(Component.literal("public"), "power", 20);
        assertEquals(1, collector.sortedEntries().size());
        assertEquals("public", collector.sortedEntries().getFirst().text().getString());
    }
}
