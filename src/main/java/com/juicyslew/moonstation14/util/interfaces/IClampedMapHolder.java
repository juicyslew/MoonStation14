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
        // MODIFIES PROVIDED DICTIONARY, THEN RETURNS THE DIFFERENCE.
        if (!Float.isFinite(amount) || amount < 0f) {
            throw new IllegalArgumentException("amount must be finite and nonnegative");
        }

        Map<T, Float> data = this.getMap();
        // Reactions can only happen when something's added to a container, NOT when removed is added to a container.
        double vol = 0d;
        for (Map.Entry<T, Float> e : data.entrySet()) {
            Float value = e.getValue();
            if (value == null || !Float.isFinite(value) || value < 0f) {
                throw new IllegalArgumentException("stored amount must be finite and nonnegative");
            }
            vol += value;
        }

        Map<T, Float> difference = new HashMap<>();

        // There is no proportional transfer to perform. Build the cleaned map
        // first so zero entries are removed without ever dividing by zero.
        if (vol == 0d || amount == 0f) {
            Map<T, Float> cleaned = new HashMap<>();
            for (Map.Entry<T, Float> e : data.entrySet()) {
                if (e.getValue() > 0f) cleaned.put(e.getKey(), e.getValue());
            }
            data.clear();
            data.putAll(cleaned);
            return difference;
        }

        // A full transfer returns the original positive quantities exactly.
        if ((double) amount >= vol) {
            for (Map.Entry<T, Float> e : data.entrySet()) {
                if (e.getValue() > 0f) difference.put(e.getKey(), e.getValue());
            }
            data.clear();
            return difference;
        }

        // Stage all writes. Besides keeping zero entries out of the result,
        // this ensures every source validation above completes before mutation.
        Map<T, Float> remaining = new HashMap<>();
        double leftToRemove = amount;
        for (Map.Entry<T, Float> e : data.entrySet()) {
            float sourceAmount = e.getValue();
            if (sourceAmount == 0f) continue;

            double proportional = (double) sourceAmount * amount / vol;
            float proposedRemoved = (float) Math.min(proportional, leftToRemove);
            float sourceRemaining = sourceAmount - proposedRemoved;
            float removed = sourceAmount - sourceRemaining;

            // The source subtraction is the actual transfer. If its rounding
            // exceeds the request, choose the representable remaining source
            // at or above (source - request) instead of repeatedly shrinking
            // the returned removal.
            if ((double) removed > leftToRemove) {
                sourceRemaining = (float) ((double) sourceAmount - leftToRemove);
                if ((double) sourceAmount - sourceRemaining > leftToRemove) {
                    sourceRemaining = Math.nextUp(sourceRemaining);
                }
                removed = sourceAmount - sourceRemaining;
            }

            if (removed > 0f && (double) removed <= leftToRemove) {
                difference.put(e.getKey(), removed);
                leftToRemove -= removed;
            } else {
                // No positive representable delta fits in the request.
                sourceRemaining = sourceAmount;
            }

            if (sourceRemaining > 0f) remaining.put(e.getKey(), sourceRemaining);
        }

        data.clear();
        data.putAll(remaining);
        return difference;
    }

    default void trimZeroes(){
        Map<T, Float> data = this.getMap();
        data.entrySet().removeIf(entry -> entry.getValue() == 0);
    }
}
