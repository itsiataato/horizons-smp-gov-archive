package com.horizonssmp.gov.config;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Shared builder and read helpers for the Gov config. Split out of {@link GovConfig} so the
 * registration can be distributed across section classes without exceeding the file-size limit.
 */
public final class GovConfigCore {

    /** The single builder used by every section; never pushed or popped. */
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** The built spec, assigned once all sections have registered. */
    public static ModConfigSpec SPEC;

    private GovConfigCore() {
    }

    /** Reads a value, or its default when the config is not loaded. */
    public static <T> T read(final ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** A defensive copy of a string-list config value, with {@code null} elements dropped. */
    public static List<String> strings(final ModConfigSpec.ConfigValue<List<? extends String>> value) {
        final List<String> result = new ArrayList<>();
        for (final String entry : read(value)) {
            if (entry != null) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }
}
