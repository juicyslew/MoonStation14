package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SolutionExamineFormatterTest {
    @Test void centsFormatWithoutLossOrInsignificantDecimals() {
        assertEquals("0", com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(0));
        assertEquals("0.01", com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(1));
        assertEquals("0.1", com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(10));
        assertEquals("0.25", com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(25));
        assertEquals("1", com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(100));
        assertEquals("200", com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(20000));
        assertEquals("123.45", com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(12345));
        assertEquals("21474836.47", com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(
                com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.MAX_CENTS));
        assertThrows(IllegalArgumentException.class, () -> com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(-1));
    }
    @Test void emptySolutionFormatsOnlyVolumeWithCallerSuppliedGenericTemplates() {
        var output = SolutionExamineFormatter.format(new ReagentAttachment(), null, 500f, false,
                SolutionExamineFormatter.Templates.GENERIC, ignored -> null);
        assertEquals("examine.solution.volume", ((net.minecraft.network.chat.contents.TranslatableContents)
                output.volume().getContents()).getKey());
        var category = (net.minecraft.network.chat.Component) ((net.minecraft.network.chat.contents.TranslatableContents)
                output.volume().getContents()).getArgs()[0];
        assertEquals("examine.solution.volume.empty", ((net.minecraft.network.chat.contents.TranslatableContents)
                category.getContents()).getKey());
        assertTrue(output.details().isEmpty());
        assertFalse(output.volume().getString().contains("{0}"));
        assertFalse(output.volume().getString().contains("%1$s"));
        assertEquals("empty", SolutionExamineFormatter.volumeCategory(0, 1000f));
        assertEquals("empty", SolutionExamineFormatter.volumeCategory(1, 1000f));
        assertEquals("empty", SolutionExamineFormatter.volumeCategory(999, 1000f));
        assertEquals("mostly-empty", SolutionExamineFormatter.volumeCategory(1000, 1000f));
        assertEquals("mostly-empty", SolutionExamineFormatter.volumeCategory(33000, 1000f));
        assertEquals("half-full", SolutionExamineFormatter.volumeCategory(34000, 1000f));
        assertEquals("half-full", SolutionExamineFormatter.volumeCategory(66000, 1000f));
        assertEquals("mostly-full", SolutionExamineFormatter.volumeCategory(67000, 1000f));
        assertEquals("mostly-full", SolutionExamineFormatter.volumeCategory(99000, 1000f));
        assertEquals("full", SolutionExamineFormatter.volumeCategory(100000, 1000f));
        assertEquals("mostly-full", SolutionExamineFormatter.volumeCategory(101000, 1000f));
    }
}
