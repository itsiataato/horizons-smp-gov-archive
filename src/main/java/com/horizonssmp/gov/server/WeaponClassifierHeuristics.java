package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.horizonssmp.gov.common.enums.WeaponClass;

/**
 * Pure heuristic helpers for {@link WeaponClassifier}. Package-private: not part of the public API.
 */
final class WeaponClassifierHeuristics {

    private WeaponClassifierHeuristics() {
    }

    static String normalizeId(final String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    static int severity(final WeaponClass value) {
        if (value == null) {
            return 0;
        }
        return switch (value) {
            case NONE -> 0;
            case UNCLASSIFIED -> 1;
            case LIGHT, SHOTGUN, RIFLE, PRECISION -> 2;
            case BLASTING -> 3;
            case HEAVY -> 4;
            case EXPLOSIVE -> 5;
            case NUCLEAR -> 6;
        };
    }

    static List<String> tokens(final String text) {
        final List<String> result = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = Character.toLowerCase(text.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                current.append(c);
            } else if (current.length() > 0) {
                result.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    /**
     * True when the id path contains the phrase as a whole token sequence.
     */
    static boolean matchesPhrase(final String itemId, final String phrase) {
        if (itemId == null || phrase == null || phrase.isBlank()) {
            return false;
        }
        final String path = itemId.contains(":")
                ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        final List<String> idTokens = tokens(path);
        final List<String> phraseTokens = tokens(phrase);
        if (phraseTokens.isEmpty() || phraseTokens.size() > idTokens.size()) {
            return false;
        }
        for (int start = 0; start <= idTokens.size() - phraseTokens.size(); start++) {
            boolean match = true;
            for (int i = 0; i < phraseTokens.size(); i++) {
                if (!idTokens.get(start + i).equals(phraseTokens.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesAny(final String itemId, final List<String> tokens) {
        for (final String token : tokens) {
            if (matchesPhrase(itemId, token)) {
                return true;
            }
        }
        return false;
    }

    static boolean isTaczId(final String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        final String namespace = itemId.contains(":")
                ? itemId.substring(0, itemId.indexOf(':')) : "";
        if (namespace.contains("tacz")) {
            return true;
        }
        return itemId.contains("tacz:");
    }

    static boolean isTacz(final String itemId, final List<String> taczNamespaces) {
        if (isTaczId(itemId)) {
            return true;
        }
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        final String namespace = itemId.contains(":")
                ? itemId.substring(0, itemId.indexOf(':')) : "";
        for (final String configured : taczNamespaces) {
            if (configured == null || configured.isBlank()) {
                continue;
            }
            final String needle = configured.trim();
            if (namespace.equals(needle) || namespace.startsWith(needle)
                    || itemId.startsWith(needle)) {
                return true;
            }
        }
        return false;
    }
}