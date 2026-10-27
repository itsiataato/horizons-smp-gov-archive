package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.horizonssmp.gov.common.enums.WeaponClass;

/**
 * JSON reading helpers for {@link WeaponClassifier}. Package-private.
 */
final class WeaponClassifierJson {

    private WeaponClassifierJson() {
    }

    static Map<String, WeaponClass> readClassMap(final JsonObject root, final String key) {
        final JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("'" + key + "' must be an object");
        }
        final Map<String, WeaponClass> result = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("'" + key + "." + entry.getKey() + "' must be a string");
            }
            final String value = entry.getValue().getAsString();
            final WeaponClass parsed = WeaponClass.parse(value);
            if (parsed == null) {
                throw new IllegalArgumentException(
                        "'" + key + "." + entry.getKey() + "' names unknown class '" + value + "'");
            }
            result.put(WeaponClassifierHeuristics.normalizeId(entry.getKey()), parsed);
        }
        return Collections.unmodifiableMap(result);
    }

    static List<String> readStringList(final JsonObject root, final String key) {
        final JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("'" + key + "' must be an array");
        }
        final List<String> result = new ArrayList<>();
        for (final JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("'" + key + "' must contain only strings");
            }
            final String text = value.getAsString().trim().toLowerCase(Locale.ROOT);
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    static int intValue(final JsonObject root, final String key, final int fallback) {
        final JsonElement element = root.get(key);
        try {
            return element == null || !element.isJsonPrimitive() ? fallback : element.getAsInt();
        } catch (final RuntimeException e) {
            return fallback;
        }
    }
}