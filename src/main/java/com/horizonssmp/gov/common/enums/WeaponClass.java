package com.horizonssmp.gov.common.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * What an item <i>is</i> under Schedule 5.A, produced by the classifier.
 *
 * <p>This is deliberately separate from {@link PermitClass}. A weapon's class is a property of the
 * item (a pistol is Class L); a permit is a privilege a person holds (a person may hold a Class L
 * permit). Keeping them apart is what lets the registry record a state-held Class H weapon with no
 * owner permit reference, and lets a person hold a Class G duty permit without pretending that a
 * Class G permit is a weapon.
 *
 * <p>{@link #UNCLASSIFIED} exists for the tolerant path only: law 5.13 says an unlisted weapon or
 * explosive is Class H until the council classifies it, so the enforcement decision normalizes it
 * to {@link #HEAVY} and the answer names the source as unclassified. No item is ever stored as
 * {@code UNCLASSIFIED} in a weapon record.
 *
 * <p>Ids are stable display/tag values. They are not persisted in {@code gov.dat} (the registry
 * stores {@link WeaponCategory} and the boolean {@code militaryGrade}); the JSON mapping persists
 * them.
 */
public enum WeaponClass {

    LIGHT("L", "Class L Light Firearm", PermitClass.L, false, false, false),
    SHOTGUN("S", "Class S Shotgun", PermitClass.S, false, false, false),
    RIFLE("R", "Class R Rifle", PermitClass.R, false, false, false),
    PRECISION("P", "Class P Precision Rifle", PermitClass.P, false, false, false),
    HEAVY("H", "Class H Heavy Weapon", PermitClass.H, true, false, false),
    BLASTING("BLASTING", "TNT (Class B Blasting)", PermitClass.BLASTING, false, true, false),
    EXPLOSIVE("EXPLOSIVE", "Explosive ordnance", PermitClass.H, true, true, false),
    NUCLEAR("NUCLEAR", "Nuclear material", PermitClass.H, true, false, true),
    UNCLASSIFIED("UNCLASSIFIED", "Unclassified weapon-like item", PermitClass.H, true, false, false),
    NONE("NONE", "Not a regulated weapon", null, false, false, false);

    private final String id;
    private final String label;
    private final PermitClass permitClass;
    private final boolean militaryGrade;
    private final boolean explosive;
    private final boolean nuclear;

    WeaponClass(final String id, final String label, final PermitClass permitClass,
            final boolean militaryGrade, final boolean explosive, final boolean nuclear) {
        this.id = id;
        this.label = label;
        this.permitClass = permitClass;
        this.militaryGrade = militaryGrade;
        this.explosive = explosive;
        this.nuclear = nuclear;
    }

    /** The stable uppercase id used in the JSON mapping and the field block. */
    public String id() {
        return id;
    }

    /** Human-readable class name. */
    public String label() {
        return label;
    }

    /** The permit class that authorizes this item, or {@code null} for {@link #NONE}. */
    public PermitClass permitClass() {
        return permitClass;
    }

    /** True for the Class H list of Article 5.08. */
    public boolean militaryGrade() {
        return militaryGrade;
    }

    /** True for TNT and every explosive, including the Blasting exception. */
    public boolean explosive() {
        return explosive;
    }

    /** True for uranium and any nuclear material. */
    public boolean nuclear() {
        return nuclear;
    }

    /** True when the item is a regulated weapon-like item rather than an ordinary item. */
    public boolean weaponLike() {
        return this != NONE;
    }

    /** True when the item is a firearm class that needs a matching carry permit. */
    public boolean carryPermitClass() {
        return this == LIGHT || this == SHOTGUN || this == RIFLE || this == PRECISION || this == HEAVY;
    }

    /**
     * The registry category for a classified weapon, or {@code null} when the item is not a
     * registrable firearm (TNT, explosives and nuclear material are controlled but not registered
     * as firearms; the registry records firearms and their serials).
     */
    public WeaponCategory registryCategory() {
        return switch (this) {
            case LIGHT -> WeaponCategory.L;
            case SHOTGUN -> WeaponCategory.S;
            case RIFLE -> WeaponCategory.R;
            case PRECISION -> WeaponCategory.P;
            case HEAVY -> WeaponCategory.H;
            default -> null;
        };
    }

    /** Parses a stored or configured class id, tolerantly and case-insensitively. */
    public static Optional<WeaponClass> byId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (final WeaponClass value : values()) {
            if (value.id.equalsIgnoreCase(needle) || value.name().equalsIgnoreCase(needle)) {
                return Optional.of(value);
            }
        }
        return switch (needle) {
            case "pistol", "handgun", "light" -> Optional.of(LIGHT);
            case "shotgun" -> Optional.of(SHOTGUN);
            case "rifle", "smg" -> Optional.of(RIFLE);
            case "sniper", "marksman", "precision" -> Optional.of(PRECISION);
            case "mg", "machinegun", "machine_gun", "rpg", "launcher", "grenade", "heavy" ->
                    Optional.of(HEAVY);
            case "tnt" -> Optional.of(BLASTING);
            case "bomb", "explosives" -> Optional.of(EXPLOSIVE);
            case "uranium", "nuclear" -> Optional.of(NUCLEAR);
            default -> Optional.empty();
        };
    }

    /** Parses a class id, returning {@code null} for anything unknown. */
    public static WeaponClass parse(final String raw) {
        return byId(raw).orElse(null);
    }

    /** Every id, comma separated, for help text. */
    public static String ids() {
        final StringBuilder out = new StringBuilder();
        for (final WeaponClass value : values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value.id);
        }
        return out.toString();
    }
}
