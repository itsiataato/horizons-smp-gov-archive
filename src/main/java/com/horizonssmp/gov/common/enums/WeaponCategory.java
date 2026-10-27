package com.horizonssmp.gov.common.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * The weapon-registry class stored on a {@code WeaponRecord}.
 *
 * <p>These are the firearm classes of Schedule 5.A only. Class D is a dealer permit and Class G is
 * a duty permit, so neither is a property of a weapon; explosives and nuclear material are
 * controlled but are not registered firearms (they have no serial in this build), so they do not
 * belong here either. {@link #UNCLASSIFIED} is the tolerant fallback for a damaged or pre-M6 tag.
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. Do not rename a constant without a schema
 * migration. A tag written by the pre-M6 scaffold (A/B/C/D) therefore reads as
 * {@link #UNCLASSIFIED} rather than silently becoming a real firearm class.
 */
public enum WeaponCategory {
    L,
    S,
    R,
    P,
    H,
    UNCLASSIFIED;

    /** The permit class a holder needs to carry this category, or {@code null}. */
    public PermitClass permitClass() {
        return switch (this) {
            case L -> PermitClass.L;
            case S -> PermitClass.S;
            case R -> PermitClass.R;
            case P -> PermitClass.P;
            case H -> PermitClass.H;
            case UNCLASSIFIED -> null;
        };
    }

    /** True for the Class H military-grade list. */
    public boolean militaryGrade() {
        return this == H;
    }

    /** Parses a stored category, defaulting to {@link #UNCLASSIFIED} when unknown. */
    public static WeaponCategory parse(final String name) {
        if (name == null || name.isBlank()) {
            return UNCLASSIFIED;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return UNCLASSIFIED;
        }
    }

    /** Strict parse for a command or payload: empty for anything this build does not know. */
    public static Optional<WeaponCategory> byId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The categories a weapon may actually be registered under. */
    public static String ids() {
        return "l, s, r, p, h";
    }
}
