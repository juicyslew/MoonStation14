package com.juicyslew.moonstation14.util.interfaces;

import com.juicyslew.moonstation14.util.MapOperations;

import java.util.HashMap;
import java.util.Map;

public interface IClampedMapHolder<T> {
    Map<T, Float> getMap();
    // float getMaxCapacity();

    default boolean isEmpty(){
        return getMap().isEmpty();
    }
    default void scale(float scale){
        // TODO: Make this return the excess so we can spill the extra in cases where that makes sense.
        Map<T, Float> data = this.getMap();
        for (T key : data.keySet()){
            data.put(key, data.getOrDefault(key, 0f) * scale);
        }
    }
    // Just does the math, doesn't touch the Entity or Networking
    default void specificAdd(T key, float amount, float capacity) {
        if (amount < 0){
            // TODO: Make this logic more generic.
            specificRemove(key, -amount);
            return;
        }
        Map<T, Float> data = this.getMap();
        float totalVolume = MapOperations.getTotal(data);
        float actual_add = Float.min(totalVolume + amount, capacity) - totalVolume;
        data.put(key, data.getOrDefault(key, 0f) + actual_add);
    }

    default void mergeAdd(Map<T, Float> to_add, float capacity){
        // TODO: Make this return the excess so we can spill the extra in cases where that makes sense.
        float to_add_volume = MapOperations.getTotal(to_add);
        if (to_add_volume == 0) return;
        Map<T, Float> data = this.getMap();
        float totalVolume = MapOperations.getTotal(data);
        float actual_add_volume = Float.min(totalVolume + to_add_volume, capacity) - totalVolume;
        float to_mult = actual_add_volume / to_add_volume;
        for (T key : to_add.keySet()){
            data.put(key, Math.max(0f, data.getOrDefault(key, 0f) + to_add.get(key) * to_mult)); // Enforce a minimum (for damage at least)
        }
    }

    default void specificRemove(T key, float amount) {
        Map<T, Float> data = this.getMap();
        // Reactions can only happen when something's added to a container, NOT when removed is added to a container.
        float reagent_present = data.getOrDefault(key, 0f);
        float new_amount = Float.max(reagent_present - amount, 0f);
        if (new_amount == 0){
            data.remove(key);
        }else{
            data.put(key, new_amount); // Add your clamping logic here
        }
        // Handled manually ^
        // trimZeroes();
    }

    default Map<T, Float> naiveRemove(float amount) {
        // MODIFIES PROVIDED DICTIONARY, THEN RETURNS THE DIFFERENCE.=\
        Map<T, Float> data = this.getMap();
        // Reactions can only happen when something's added to a container, NOT when removed is added to a container.
        float vol = MapOperations.getTotal(data);
        float new_amount = Float.min(vol, amount);
        Map<T, Float> difference = new HashMap<>();
        for (Map.Entry<T, Float> e : data.entrySet()) {
            float diff = e.getValue() * new_amount / vol;
            difference.put(e.getKey(), diff);
            data.put(e.getKey(), e.getValue() - diff);
        }
        data.entrySet().removeIf(entry -> entry.getValue() == 0f);
        trimZeroes();
        return difference;
    }

    default void trimZeroes(){
        Map<T, Float> data = this.getMap();
        data.entrySet().removeIf(entry -> entry.getValue() == 0);
    }
}
