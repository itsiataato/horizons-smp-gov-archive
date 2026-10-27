package com.horizonssmp.gov.client;

import java.util.List;

/**
 * Offline-capable target entry for fine-issue and vehicle-register.
 *
 * <p>The dashboard target steppers only offer online players as a quick-pick, but the
 * server resolves any raw name through the stored player files and the profile cache
 * ({@code GovTargets}), so a typed offline name is routable as-is. These helpers keep
 * that policy in one place: strip the raw text, prefer typed text over the stepper
 * quick-pick, and pick the blank-target status key. There is deliberately no
 * client-side name validation beyond the blank check — an unknown name is a server
 * {@code BAD_TARGET} refusal, which surfaces verbatim through the existing
 * MUTATE-failure status path (bank-credit precedent: never trust client validation).
 *
 * <p>Pure Java, no Minecraft imports, so it stays headless-testable.
 */
final class GovOfflineTarget {

    /** Minecraft player-name ceiling, matching the owner/target box lengths. */
    static final int MAX_NAME = 16;

    private GovOfflineTarget() {
    }

    /** Strips the raw text; null-safe. */
    static String clean(final String raw) {
        return raw == null ? "" : raw.strip();
    }

    /** True when there is nothing to send. */
    static boolean isBlank(final String raw) {
        return clean(raw).isEmpty();
    }

    /**
     * Typed text wins; the stepper quick-pick is the fallback when the box is blank.
     */
    static String orQuickPick(final String typed, final String quickPick) {
        final String cleaned = clean(typed);
        return cleaned.isEmpty() ? clean(quickPick) : cleaned;
    }

    /**
     * Advances the stepper quick-pick one slot, wrapping around the online list.
     * An unknown current value restarts from the head. The caller keeps the
     * previous value to decide whether typed box text survives the step.
     */
    static String stepQuickPick(final List<String> online, final String current,
            final int delta) {
        int index = online.indexOf(current);
        if (index < 0) {
            index = 0;
        }
        return online.get((index + delta + online.size()) % online.size());
    }

    /**
     * Blank-target status key: the no-online key when nobody is online, else the
     * panel's no-target key.
     */
    static String blankKey(final boolean onlineEmpty, final String noOnlineKey,
            final String noTargetKey) {
        return onlineEmpty ? noOnlineKey : noTargetKey;
    }
}
