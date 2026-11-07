package com.juicyslew.moonstation14.ms14.examine;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Numeric presentation boundary for examine components; source values remain authoritative and unchanged. */
public final class ExamineNumbers {
    private ExamineNumbers() { }

    public static String format(double value) {
        if (!Double.isFinite(value)) return "\u2014";
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.signum() == 0 ? "0" : rounded.toPlainString();
    }

    public static Component normalize(Component component) {
        if (component == null) return null;
        MutableComponent copy;
        if (component.getContents() instanceof TranslatableContents translated) {
            Object[] args = translated.getArgs().clone();
            for (int i = 0; i < args.length; i++) args[i] = normalizeArg(args[i]);
            copy = Component.translatableWithFallback(translated.getKey(), translated.getFallback(), args)
                    .setStyle(component.getStyle());
        } else {
            copy = MutableComponent.create(component.getContents()).setStyle(component.getStyle());
        }
        for (Component sibling : component.getSiblings()) copy.append(normalize(sibling));
        return copy;
    }

    static Object normalizeArg(Object arg) {
        if (arg instanceof Component component) return normalize(component);
        if (arg instanceof BigDecimal number) return decimal(number);
        if (arg instanceof BigInteger || arg instanceof Byte || arg instanceof Short
                || arg instanceof Integer || arg instanceof Long) return arg;
        if (arg instanceof Double number) return finite(number);
        if (arg instanceof Float number) return Float.isFinite(number)
                ? decimal(new BigDecimal(Float.toString(number))) : "—";
        if (arg instanceof Number number) {
            try { return decimal(new BigDecimal(number.toString())); }
            catch (NumberFormatException ignored) { return "\u2014"; }
        }
        return arg;
    }

    private static String finite(double value) { return Double.isFinite(value) ? format(value) : "—"; }
    private static String decimal(BigDecimal number) {
        BigDecimal rounded = number.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.signum() == 0 ? "0" : rounded.toPlainString();
    }
}
