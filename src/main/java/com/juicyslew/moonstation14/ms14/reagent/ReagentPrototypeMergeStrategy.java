package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolutionException;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SS14 reagent inheritance for the metabolism map. Stage objects are shallow
 * merged; effects and metabolites remain whole properties rather than being
 * concatenated or recursively merged.
 */
public final class ReagentPrototypeMergeStrategy implements PrototypeMergeStrategy {
    public static final String METABOLISMS = "metabolisms";
    private static final String EFFECTS = "effects";
    private static final String METABOLITES = "metabolites";

    @Override
    public boolean handles(String fieldName) {
        return METABOLISMS.equals(fieldName);
    }

    @Override
    public JsonElement merge(String fieldName, ResourceLocation childId, JsonObject child,
                             List<JsonObject> resolvedParents) {
        if (!handles(fieldName)) {
            return null;
        }

        Map<String, JsonObject> stages = new LinkedHashMap<>();
        // Parents are visited in declaration order, so the first parent owns
        // each shared stage property. The child is applied afterwards.
        for (JsonObject parent : resolvedParents) {
            mergePrototypeStages(childId, "parent", parent, stages, false);
        }
        if (child.has(METABOLISMS)) {
            mergePrototypeStages(childId, "child", child, stages, true);
        }

        if (stages.isEmpty()) {
            // Preserve an explicitly supplied empty map (and an empty map
            // inherited from a parent) rather than turning presence into
            // absence.
            boolean metabolismWasPresent = child.has(METABOLISMS)
                    || resolvedParents.stream().anyMatch(parent -> parent.has(METABOLISMS));
            return metabolismWasPresent ? new JsonObject() : null;
        }
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonObject> stage : stages.entrySet()) {
            result.add(stage.getKey(), stage.getValue().deepCopy());
        }
        return result;
    }

    private void mergePrototypeStages(ResourceLocation childId, String sourceKind, JsonObject source,
                                      Map<String, JsonObject> stages, boolean childSource) {
        if (!source.has(METABOLISMS)) {
            return;
        }
        JsonElement metabolismElement = source.get(METABOLISMS);
        if (metabolismElement == null || !metabolismElement.isJsonObject()) {
            throw invalidShape(childId, sourceKind + " 'metabolisms'", "an object keyed by stage");
        }
        for (Map.Entry<String, JsonElement> stageEntry : metabolismElement.getAsJsonObject().entrySet()) {
            String stageName = stageEntry.getKey();
            JsonElement stageElement = stageEntry.getValue();
            if (!stageElement.isJsonObject()) {
                throw invalidShape(childId, sourceKind + " metabolism stage '" + stageName + "'", "an object");
            }
            JsonObject stage = stageElement.getAsJsonObject();
            validateStageProperties(childId, sourceKind, stageName, stage);

            JsonObject target = stages.computeIfAbsent(stageName, ignored -> new JsonObject());
            for (Map.Entry<String, JsonElement> property : stage.entrySet()) {
                // Earlier parents have already occupied their properties. A
                // child always wins, including with an explicit empty list/map.
                if (childSource || !target.has(property.getKey())) {
                    target.add(property.getKey(), property.getValue().deepCopy());
                }
            }
        }
    }

    private void validateStageProperties(ResourceLocation childId, String sourceKind, String stageName,
                                         JsonObject stage) {
        if (stage.has(EFFECTS) && !stage.get(EFFECTS).isJsonArray()) {
            throw invalidShape(childId, sourceKind + " stage '" + stageName + ".effects'", "an array");
        }
        if (stage.has(METABOLITES) && !stage.get(METABOLITES).isJsonObject()) {
            throw invalidShape(childId, sourceKind + " stage '" + stageName + ".metabolites'", "an object");
        }
    }

    private PrototypeResolutionException invalidShape(ResourceLocation childId, String field, String expected) {
        return new PrototypeResolutionException("Reagent '" + childId + "' has malformed " + field
                + ": expected " + expected);
    }
}
