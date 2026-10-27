package com.horizonssmp.gov.common.records;

import java.util.ArrayList;
import java.util.List;

/**
 * List helpers for {@link BusinessRecord}. Behavior-preserving.
 */
public final class BusinessRecordLists {

    private BusinessRecordLists() {
    }

    public static <T> List<T> bounded(final List<T> values, final int maximum) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        final List<T> copy = new ArrayList<>(Math.min(values.size(), maximum));
        for (final T value : values) {
            if (value != null) {
                copy.add(value);
                if (copy.size() >= maximum) {
                    break;
                }
            }
        }
        return List.copyOf(copy);
    }
}