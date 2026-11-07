package com.juicyslew.moonstation14.ms14.examine;

import net.minecraft.network.chat.Component;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamineNumbersTest {
    @Test void roundsOnlyTypedNumbersAtCollectorBoundaryAndPreservesTranslationStructure() {
        var collector = new ExamineCollector(true,
                Component.translatableWithFallback("fallback", "name %s", 1.234567));
        collector.description(Component.translatableWithFallback("description", "description %s", 1.234567));
        Component nested = Component.translatableWithFallback("nested", "nested fallback %s", 1.235)
                .append(Component.literal(" nested suffix"));
        Component styled = Component.translatableWithFallback("outer", "outer fallback %s %s %s",
                        1.234567, nested, new BigDecimal("1.2345"))
                .withStyle(style -> style.withBold(true)).append(Component.literal(" v1.2345"))
                .append(Component.translatable("sibling", 2.345));
        collector.publicEntry(styled, "test", 1);
        String rendered = collector.sortedEntries().getFirst().text().getString();
        assertTrue(rendered.contains("1.23"));
        assertTrue(rendered.contains("1.24"));
        assertTrue(rendered.contains("v1.2345"));
        assertTrue(rendered.contains("nested suffix"));
        assertEquals(1, rendered.split("nested suffix", -1).length - 1);
        assertTrue(collector.sortedEntries().getFirst().text().getStyle().isBold());
        assertEquals("name 1.23", collector.name().getString());
        assertEquals("description 1.23", collector.description().getString());
    }

    @Test void decimalFormatterBoundsPrecisionAndPreservesIntegerSemantics() {
        assertEquals("1.23", ExamineNumbers.format(1.234567));
        assertEquals("1.24", ExamineNumbers.format(1.235));
        assertEquals("0", ExamineNumbers.format(-0.0001));
        assertEquals("0", ExamineNumbers.format(0.00001));
        assertEquals("1.24", ExamineNumbers.normalizeArg(1.235f));
        assertEquals("1.23", ExamineNumbers.normalizeArg(new BigDecimal("1.2345")));
        assertEquals(Long.MAX_VALUE, ExamineNumbers.normalizeArg(Long.MAX_VALUE));
        assertEquals(BigInteger.ONE.shiftLeft(100), ExamineNumbers.normalizeArg(BigInteger.ONE.shiftLeft(100)));
        assertEquals("\u2014", ExamineNumbers.normalizeArg(Double.NaN));
        assertEquals("\u2014", ExamineNumbers.normalizeArg(Double.POSITIVE_INFINITY));
        assertEquals("\u2014", ExamineNumbers.format(Double.NaN));
    }
}
