package com.horizonssmp.gov.common.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * The permit classes of Schedule 5.A, Article 8.12 and Article 8.09.
 *
 * <p>L, S, R and P are civilian carry permits. H is a heavy-weapon permit that is military-only:
 * issuing one requires the holder to already hold a valid Class G duty permit. D is the dealer
 * permit. G is the government/military duty permit; issuing one requires the
 * {@code military.authority} capability, which is a capability and never a job name. BLASTING is
 * the Class B TNT permit of Article 5.07. MINING is the Article 8.09 mining permit, stored in the
 * same registry because Article 8.12 says the registrar keeps both.
 *
 * <p>The string ids are persisted in {@code gov.dat} as part of the permit record key. Do not
 * rename one without a migration.
 */
public enum PermitClass {

    L("l", "L", "Class L Light Firearm"),
    S("s", "S", "Class S Shotgun"),
    R("r", "R", "Class R Rifle"),
    P("p", "P", "Class P Precision Rifle"),
    H("h", "H", "Class H Heavy Weapon"),
    D("d", "D", "Class D Dealer"),
    G("g", "G", "Class G Government Duty"),
    BLASTING("blasting", "B", "Class B Blasting (TNT, at most 16)"),
    MINING("mining", "M", "Mining Permit");

    private final String id;
    private final String code;
    private final String label;

    PermitClass(final String id, final String code, final String label) {
        this.id = id;
        this.code = code;
        this.label = label;
    }

    /** The stable lowercase id, used in the record key and on the wire. */
    public String id() {
        return id;
    }

    /** The short code printed in the law. */
    public String code() {
        return code;
    }

    /** The human-readable class name. */
    public String label() {
        return label;
    }

    /** True for a permit that authorizes carrying a firearm class. */
    public boolean isWeaponPermit() {
        return this == L || this == S || this == R || this == P || this == H;
    }

    /** True for a permit that can only be issued inside the military authority gate. */
    public boolean militaryOnly() {
        return this == H || this == G;
    }

    /** The registry category this permit authorizes, or {@code null} for non-weapon permits. */
    public WeaponCategory weaponCategory() {
        return switch (this) {
            case L -> WeaponCategory.L;
            case S -> WeaponCategory.S;
            case R -> WeaponCategory.R;
            case P -> WeaponCategory.P;
            case H -> WeaponCategory.H;
            default -> null;
        };
    }

    /**
     * Parses a class from its id, code, enum name or a common synonym, case-insensitively.
     * Returns empty for anything unknown, never a guessed civilian class.
     */
    public static Optional<PermitClass> byId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (final PermitClass value : values()) {
            if (value.id.equals(needle) || value.code.equalsIgnoreCase(needle)
                    || value.name().equalsIgnoreCase(needle)) {
                return Optional.of(value);
            }
        }
        return switch (needle) {
            case "blasting_permit", "tnt", "class_b" -> Optional.of(BLASTING);
            case "mining_permit", "miningpermit" -> Optional.of(MINING);
            case "government", "government_duty", "military" -> Optional.of(G);
            case "dealer" -> Optional.of(D);
            default -> Optional.empty();
        };
    }

    /** Parses a class, returning {@code null} for anything unknown. */
    public static PermitClass parse(final String raw) {
        return byId(raw).orElse(null);
    }

    /** The weapon-registry classes that may be issued through the permit command. */
    public static String ids() {
        final StringBuilder out = new StringBuilder();
        for (final PermitClass value : values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value.id);
        }
        return out.toString();
    }
}
