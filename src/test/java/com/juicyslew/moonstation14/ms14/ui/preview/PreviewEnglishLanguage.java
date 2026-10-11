package com.juicyslew.moonstation14.ms14.ui.preview;

import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import java.util.Optional;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;

/** Resolves fixture labels from the same English resource files shipped by Minecraft/the mod. */
final class PreviewEnglishLanguage {
    private final Map<String, String> entries = new LinkedHashMap<>();
    private final Language previous;
    private PreviewEnglishLanguage() { previous=Language.getInstance(); }

    static PreviewEnglishLanguage load(Path project, Path gameResources) throws Exception {
        var result = new PreviewEnglishLanguage();
        try (var zip = new ZipFile(gameResources.toFile())) {
            result.read(zip, "assets/minecraft/lang/en_us.json");
        }
        Path modLanguage = project.resolve("src/main/resources/assets/moonstation14/lang/en_us.json");
        if (java.nio.file.Files.isRegularFile(modLanguage)) try (var input=java.nio.file.Files.newInputStream(modLanguage)) {
            Language.loadFromJson(input,result.entries::put);
        }
        Language.inject(new Language() {
            @Override public String getOrDefault(String key,String fallback){return result.entries.getOrDefault(key,fallback);}
            @Override public boolean has(String key){return result.entries.containsKey(key);}
            @Override public boolean isDefaultRightToLeft(){return false;}
            @Override public FormattedCharSequence getVisualOrder(FormattedText text){return sink->text.visit((style,value)->StringDecomposer.iterateFormatted(value,style,sink)?Optional.empty():FormattedText.STOP_ITERATION,Style.EMPTY).isPresent();}
            @Override public Map<String,String> getLanguageData(){return Map.copyOf(result.entries);}
        });
        return result;
    }

    private void read(ZipFile zip, String path) throws Exception {
        var entry = zip.getEntry(path);
        if (entry == null) throw new IllegalStateException("missing translation resource " + path);
        try (var input=zip.getInputStream(entry)) { Language.loadFromJson(input,entries::put); }
    }
    Component component(String key, String fallback, Object... args) { return Component.translatableWithFallback(key,fallback,args); }
    Component reagent(String key,String pathFallback){Component translated=Component.translatable(key);return translated.getString().equals(key)?Component.literal(pathFallback):translated;}
    Map<String, String> manifestEntries() { return Map.copyOf(entries); }
    void restore(){Language.inject(previous);}
}
