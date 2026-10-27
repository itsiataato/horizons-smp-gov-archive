package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.SelfCardKeys;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.JailRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

import net.minecraft.server.level.ServerPlayer;

/**
 * Builds the self card: the one record every player may read, their own.
 *
 * <p>One builder, two renderers. The dashboard tab renders the returned field map in the client
 * theme; {@code /gov me} renders the same map as chat lines. Neither renderer reads the data file
 * and neither guesses: everything here comes from {@link GovSavedData} and from the live player,
 * so a card cannot disagree with the record it describes.
 *
 * <p>The map keys are stable and lowercase, and the values are the record's own vocabulary
 * ({@link DutyState} names, {@code true}/{@code false}, the jail state as {@code held}/{@code none}).
 * A client that does not know a key ignores it; a key that is absent renders as a dash. That is
 * the tolerant-read half of the forward-compatibility rule, applied to a screen.
 */
public final class SelfCardService {

    private SelfCardService() {
    }

    /**
     * Builds the self card for a player.
     *
     * <p>Call on the server thread. The player's file is created on first contact, exactly as the
     * dispatcher does before a capability check, so the card of a brand new player is a real card
     * with empty fields rather than a refusal.
     *
     * @param player the player
     * @param data the government data
     * @param now epoch milliseconds to stamp a first-contact file with
     * @return the field map, in display order
     */
    public static Map<String, String> fields(
            final ServerPlayer player, final GovSavedData data, final long now) {
        final PlayerRecord record = data.playerOrCreate(player.getUUID(), player.getName().getString(), now);
        final JobRecord job = record.primaryJobId().isEmpty() ? null : data.job(record.primaryJobId());
        java.util.Set<GovCapability> capabilities =
                GovCapabilities.effective(record, data::job);
        if (GovCapabilities.isOperator(player)) {
            capabilities = java.util.EnumSet.allOf(GovCapability.class);
        }

        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(SelfCardKeys.NAME, record.lastName().isBlank() ? player.getName().getString() : record.lastName());
        fields.put(SelfCardKeys.JOB, job == null ? "" : job.displayName());
        fields.put(SelfCardKeys.JOB_ID, job == null ? "" : job.id());
        fields.put(SelfCardKeys.RANK, record.rank().isBlank()
                ? (job == null ? "" : job.rankLabel())
                : record.rank());
        fields.put(SelfCardKeys.DUTY, record.duty().name());
        fields.put(SelfCardKeys.SUSPECT, Boolean.toString(record.isSuspect()));

        final JailRecord jail = record.jail();
        fields.put(SelfCardKeys.JAIL, jail.active() ? SelfCardKeys.JAIL_HELD : SelfCardKeys.JAIL_NONE);
        fields.put(SelfCardKeys.JAIL_UNTIL, Long.toString(jail.active() ? jail.until() : 0L));
        fields.put(SelfCardKeys.JAIL_CELL, jail.active() ? jail.cellRegion() : "");
        fields.put(SelfCardKeys.FINES, Integer.toString(outstandingFines(data, player.getUUID())));
        fields.put(SelfCardKeys.CAP_PEOPLE,
                Boolean.toString(capabilities.contains(GovCapability.PLAYER_LOOKUP)));
        fields.put(SelfCardKeys.CAP_NOTES,
                Boolean.toString(capabilities.contains(GovCapability.NOTE_VIEW_ALL)));
        fields.put(SelfCardKeys.CAP_AUDIT,
                Boolean.toString(capabilities.contains(GovCapability.AUDIT_VIEW)));
        fields.put(SelfCardKeys.CAP_JOBS_MANAGE,
                Boolean.toString(capabilities.contains(GovCapability.JOB_MANAGE)
                        || capabilities.contains(GovCapability.JOB_ASSIGN)));
        return fields;
    }

    /** The number of fines still expecting gold from a player. */
    public static int outstandingFines(final GovSavedData data, final UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        int count = 0;
        for (final FineRecord fine : data.fines().values()) {
            if (uuid.equals(fine.targetUuid()) && fine.outstanding()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Renders the same fields as chat lines, for {@code /gov me}.
     *
     * <p>Plain strings, not Components: the caller decides the styling, and a plain line is what
     * the smoke test can grep. A missing key renders as a dash, so the two renderers show the same
     * card even if one of them is older than the field map.
     */
    public static List<String> chatLines(final Map<String, String> fields) {
        final Map<String, String> safe = fields == null ? Map.of() : fields;
        final List<String> lines = new ArrayList<>();
        lines.add("Government record: " + value(safe, SelfCardKeys.NAME));
        lines.add("  Job: " + jobText(safe));
        lines.add("  Rank: " + value(safe, SelfCardKeys.RANK));
        lines.add("  Duty: " + dutyText(value(safe, SelfCardKeys.DUTY)));
        lines.add("  Suspect: " + yesNo("true".equals(value(safe, SelfCardKeys.SUSPECT))));
        lines.add("  Jail: " + jailText(safe));
        lines.add("  Unpaid fines: " + value(safe, SelfCardKeys.FINES));
        return lines;
    }

    private static String value(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.value(fields, key);
    }

    private static String jobText(final Map<String, String> fields) {
        final String job = value(fields, SelfCardKeys.JOB);
        final String id = value(fields, SelfCardKeys.JOB_ID);
        return "-".equals(job) ? "-" : (job + " (" + id + ")");
    }

    private static String dutyText(final String duty) {
        return switch (duty) {
            case "ON_DUTY" -> "on duty";
            case "OFF_DUTY" -> "off duty";
            case "N_A" -> "not applicable";
            default -> duty;
        };
    }

    private static String jailText(final Map<String, String> fields) {
        if (!SelfCardKeys.JAIL_HELD.equals(value(fields, SelfCardKeys.JAIL))) {
            return "not detained";
        }
        final String cell = fields.get(SelfCardKeys.JAIL_CELL);
        final String until = fields.get(SelfCardKeys.JAIL_UNTIL);
        final StringBuilder out = new StringBuilder("detained");
        if (cell != null && !cell.isBlank()) {
            out.append(", cell ").append(cell);
        }
        out.append(", ").append(untilText(until));
        return out.toString();
    }

    /**
     * The remaining detention as words.
     *
     * <p>The wire carries the epoch millisecond end, not a pre-rendered string, because the two
     * renderers tick at different moments; each formats it against its own clock. This one is for
     * a chat line, where "for 9 min more" reads better than a timestamp.
     */
    private static String untilText(final String until) {
        final long end;
        try {
            end = Long.parseLong(until == null ? "" : until.trim());
        } catch (final NumberFormatException e) {
            return "open-ended";
        }
        if (end <= 0L) {
            return "open-ended";
        }
        final long remaining = end - System.currentTimeMillis();
        if (remaining <= 0L) {
            return "release due";
        }
        final long minutes = Math.max(1L, remaining / 60000L);
        if (minutes < 60L) {
            return "for " + minutes + " min more";
        }
        final long hours = minutes / 60L;
        final long rest = minutes % 60L;
        return "for " + hours + " h" + (rest == 0L ? "" : " " + rest + " min") + " more";
    }

    private static String yesNo(final boolean value) {
        return value ? "yes" : "no";
    }
}
