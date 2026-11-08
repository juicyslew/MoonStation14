package com.juicyslew.moonstation14.ms14.chat.server;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.concurrent.ThreadLocalRandom;

/** Bounded speech accents ported from pinned SS14 animal accent consumers. */
public final class SpeechAccentPolicy {
    private static final List<String> MOUSE = List.of("Squeak!", "Piep!", "Chuu!", "Eeee!", "Pip!", "Fwiep!", "Heep!");
    private static final List<String> CHICKEN = List.of("Cluck!", "Cluck.", "Cluck?", "Cluck cluck!");
    private static final List<String> DUCK = List.of("Quack!", "Quack.", "Quack?", "Quack quack!");
    private static final List<String> PIG = List.of("Oink.", "Oink?", "Oink!", "Oink oink!");
    private static final List<String> KANGAROO = List.of("Grr!", "Hisss!", "Shreak!", "Chuu!");
    private static final List<String> PARROT = List.of("SQUAWK!", "SQUAAAWK!", "AWWK!", "AAWK!", "RAWWK!", "RAAAWK!", "BRAAWK!", "BRAWWK!");
    private static final List<String> KOBOLD = List.of("Yip!", "Grrar.", "Yap!", "Bip.", "Screet?", "Gronk!", "Hiss!", "Eeee!", "Yip.");
    private static final List<String> CRAB = List.of("Click.", "Click-clack!", "Clack?", "Tipi-tap!", "Clik-tap.", "Cliliick.");
    private static final List<String> XENO = List.of("Hiss.", "Hisssss!", "Hisssuuu...", "Hiss...!");
    private static final List<String> MOTHROACH = List.of("Chirp!");
    private static final List<String> SCURRET = List.of("Wa!", "Wa?", "Wa.", "Wa...", "Wawa!", "Wawa?", "Wawa.", "Wawa...",
            "Wa wawa!", "Wa wawa?", "Wa wawa.", "Wa wawa...", "Wawa wa!", "Wawa wa?", "Wawa wa.", "Wawa wa...",
            "Waaaaaa.", "Waaaaaa!", "Waaaaaa?", "Waaaaaa...");
    private SpeechAccentPolicy() { }

    /** Whether this runtime has an explicitly implemented upstream speech accent. */
    public static boolean supports(String accent) {
        return "mouse".equals(accent) || "chicken".equals(accent) || "duck".equals(accent)
                || "monkey".equals(accent) || "parrot".equals(accent) || "lizard".equals(accent)
                || "kobold".equals(accent) || "crab".equals(accent) || "xeno".equals(accent)
                || "mothroach".equals(accent) || "scurret".equals(accent)
                || "kangaroo".equals(accent) || "pig".equals(accent);
    }

    public static String replace(String accent, String text) {
        if ("monkey".equals(accent)) return monkey(text, ThreadLocalRandom.current());
        if ("parrot".equals(accent)) return parrot(text, ThreadLocalRandom.current());
        if ("lizard".equals(accent)) return lizard(text);
        List<String> replacements = replacements(accent);
        if (replacements == null || text == null || text.isBlank()) return text;
        return replacements.get(ThreadLocalRandom.current().nextInt(replacements.size()));
    }

    /** Space Station 14 ParrotAccentSystem defaults: .5 prefix and .5 longest-word repeat chances. */
    private static String parrot(String text, RandomGenerator random) {
        if (text == null || text.isBlank()) return text;
        if (random.nextDouble() < 0.5) {
            String cleaned = text.replaceAll("[^A-Za-z0-9 -]", "");
            String[] words = cleaned.split("\\s+");
            String longest = null;
            for (int i = words.length - 1; i >= 0; i--)
                if (longest == null || words[i].length() > longest.length()) longest = words[i];
            if (longest != null && longest.length() >= 5) {
                String punctuated = ensurePunctuation(text);
                String capitalized = Character.toUpperCase(longest.charAt(0)) + longest.substring(1);
                return punctuated + " " + squawk(random) + " " + capitalized + "!";
            }
        }
        if (random.nextDouble() < 0.5) return squawk(random) + " " + text;
        return ensurePunctuation(text) + " " + squawk(random);
    }

    private static String ensurePunctuation(String text) {
        return text.endsWith("!") || text.endsWith("?") || text.endsWith(".") ? text : text + "!";
    }

    private static String squawk(RandomGenerator random) {
        return PARROT.get(random.nextInt(PARROT.size()));
    }

    /** Port of SS14 LizardAccentSystem's ordered regex replacements. */
    private static String lizard(String text) {
        if (text == null || text.isBlank()) return text;
        return text.replaceAll("s+", "sss").replaceAll("S+", "SSS")
                .replaceAll("(\\w)x", "$1kss").replaceAll("\\bx([\\-|r|R]|\\b)", "ecks$1")
                .replaceAll("\\bX([\\-|r|R]|\\b)", "ECKS$1");
    }

    /** Space Station 14 MonkeyAccentSystem.Accentuate, preserving its per-word/per-character odds. */
    private static String monkey(String text, RandomGenerator random) {
        if (text == null || text.isBlank()) return text;
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder(text.length() + 2);
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (random.nextDouble() < 0.5) {
                if (word.length() > 1) {
                    result.append("O".repeat(word.length()));
                    if (random.nextDouble() < 0.7) result.append('K');
                } else result.append('O');
            } else {
                for (int j = 0; j < word.length(); j++) result.append(random.nextDouble() < 0.2 ? 'H' : 'A');
            }
            if (i < words.length - 1) result.append(' ');
        }
        return result.append('!').toString();
    }

    public static String replace(String accent, String text, int selection) {
        List<String> replacements = replacements(accent);
        if (replacements == null || text == null || text.isBlank()) return text;
        return replacements.get(Math.floorMod(selection, replacements.size()));
    }

    /** Injectable selection makes the exact replacement mapping straightforward to test. */
    public static String replacement(String accent, int selection) {
        List<String> replacements = replacements(accent);
        return replacements != null && selection >= 0 && selection < replacements.size() ? replacements.get(selection) : null;
    }

    private static List<String> replacements(String accent) {
        return switch (accent) {
            case "mouse" -> MOUSE;
            case "chicken" -> CHICKEN;
            case "duck" -> DUCK;
            case "pig" -> PIG;
            case "kangaroo" -> KANGAROO;
            case "kobold" -> KOBOLD;
            case "crab" -> CRAB;
            case "xeno" -> XENO;
            case "mothroach" -> MOTHROACH;
            case "scurret" -> SCURRET;
            default -> null;
        };
    }
}
