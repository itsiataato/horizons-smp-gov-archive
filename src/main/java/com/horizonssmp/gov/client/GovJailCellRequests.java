package com.horizonssmp.gov.client;

import java.util.LinkedHashMap;
import java.util.Map;

import com.horizonssmp.gov.common.JailKeys;

/**
 * The dashboard wire payloads for the runtime jail-cell mutations.
 *
 * <p>The maps built here are the client half of the contract whose server half lives in
 * {@code GovJailCellHandlers}: {@code JAIL_CELL_CREATE} takes a non-blank cell name, parseable
 * integer block coordinates and a non-blank dimension, and {@code JAIL_CELL_DELETE} takes only
 * the name. The client resolves {@code here} into explicit coordinates before sending, exactly
 * like {@code /gov jail new <name> here} does, so the server never sees the word {@code here}.
 * A blank dimension on create means {@code minecraft:overworld}, mirroring the command path for
 * explicit {@code x,y,z}. Both operations return {@code null} when the inputs cannot form a
 * valid payload, so the panel can fail with its own message before anything is sent.
 */
final class GovJailCellRequests {

    /** The dimension a manual coordinate entry belongs to, per the command path. */
    static final String DEFAULT_DIMENSION = "minecraft:overworld";

    private GovJailCellRequests() {
    }

    static Integer parseIntOrNull(final String raw) {
        final String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    static Map<String, String> createPayload(final String name, final String x,
            final String y, final String z, final String dimension) {
        final String cleanName = name == null ? "" : name.strip();
        final Integer parsedX = parseIntOrNull(x);
        final Integer parsedY = parseIntOrNull(y);
        final Integer parsedZ = parseIntOrNull(z);
        if (cleanName.isEmpty() || parsedX == null || parsedY == null || parsedZ == null) {
            return null;
        }
        final String cleanDim = dimension == null || dimension.isBlank()
                ? DEFAULT_DIMENSION : dimension.strip();
        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(JailKeys.KEY_CELL_NAME, cleanName);
        payload.put(JailKeys.KEY_CELL_X, Integer.toString(parsedX));
        payload.put(JailKeys.KEY_CELL_Y, Integer.toString(parsedY));
        payload.put(JailKeys.KEY_CELL_Z, Integer.toString(parsedZ));
        payload.put(JailKeys.KEY_CELL_DIMENSION, cleanDim);
        return payload;
    }

    static Map<String, String> deletePayload(final String name) {
        final String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) {
            return null;
        }
        return Map.of(JailKeys.KEY_CELL_NAME, cleanName);
    }
}
