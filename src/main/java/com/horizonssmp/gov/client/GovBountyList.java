package com.horizonssmp.gov.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.BountyKeys;

/**
 * The bounty subtab rows: parsing, selection helpers and the count line.
 *
 * <p>The {@code BOUNTY_LIST} answer carries the true total in {@code bounties} plus up to
 * {@link BountyKeys#MAX_LISTED_BOUNTIES} dense rows, newest first, together with the place-form
 * bounds and the online picker names. This helper feeds the panel through the responses hook;
 * geometry and buttons live in {@code GovBountyGeometry} and the treasury view/button files.
 * The count line is built in code ("1 bounty" / "N bounties"); the empty state reuses the
 * bounty-empty text.
 */
final class GovBountyList {

    private GovBountyList() {
    }

    /** One bounty row: the stable id, target and poster names, amount, expiry and ownership. */
    record BountyRow(String id, String target, String poster, long amount, long expiresAt,
            boolean mine) {
    }

    /** One clipped bounty view: pre-clipped during layout, drawn verbatim by render. */
    record BountyView(String id, String target, String poster, String amount) {
    }

    /** Reads the total, bounds, picker names and dense rows; skips rows without an id. */
    static void parse(final GovTreasuryPanel p, final Map<String, String> fields) {
        final List<BountyRow> rows = new ArrayList<>();
        for (int index = 0; index < BountyKeys.MAX_LISTED_BOUNTIES; index++) {
            final String prefix = BountyKeys.ROW_PREFIX + index + ".";
            final String id = fields.get(prefix + BountyKeys.ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            rows.add(new BountyRow(id,
                    fields.getOrDefault(prefix + BountyKeys.ROW_TARGET, ""),
                    fields.getOrDefault(prefix + BountyKeys.ROW_POSTER, ""),
                    GovTreasurySupport.longField(fields, prefix + BountyKeys.ROW_AMOUNT),
                    GovTreasurySupport.longField(fields, prefix + BountyKeys.ROW_EXPIRES),
                    Boolean.parseBoolean(
                            fields.getOrDefault(prefix + BountyKeys.ROW_MINE, "false"))));
        }
        p.bountyRows.clear();
        p.bountyRows.addAll(rows);
        p.bountyTotal = Math.max(0, GovTreasurySupport.intField(fields, BountyKeys.TOTAL));
        p.bountyMin = Math.max(0L, GovTreasurySupport.longField(fields, BountyKeys.MIN_AMOUNT));
        p.bountyMax = Math.max(0L, GovTreasurySupport.longField(fields, BountyKeys.MAX_AMOUNT));
        p.bountyOnline.clear();
        // Write-only by design: the free-text form needs no picker, but the names are
        // still parsed so the client accepts the server's wire shape unchanged.
        final int online = Math.min(BountyKeys.MAX_LISTED_ONLINE,
                Math.max(0, GovTreasurySupport.intField(fields, BountyKeys.ONLINE_COUNT)));
        for (int index = 0; index < online; index++) {
            final String name = fields.get(BountyKeys.ONLINE_PREFIX + index);
            if (name != null && !name.isBlank()) {
                p.bountyOnline.add(name);
            }
        }
        if (p.bountyMin > 0L && p.bountyMax >= p.bountyMin) {
            GovBountyForm.seedAmount(p);
        }
        if (!p.bountySelectedId.isEmpty() && indexOf(p.bountyRows, p.bountySelectedId) < 0) {
            p.bountySelectedId = "";
        }
        if (p.bountyCancelArmed && !containsId(p.bountyRows, p.bountyCancelArmedId)) {
            p.bountyCancelArmed = false;
            p.bountyCancelArmedId = "";
            p.bountyCancelArmedAt = 0L;
            p.status = "";
            p.statusIsError = false;
        }
        clampScroll(p);
        if (!p.bountySelectedId.isEmpty()) {
            reveal(p);
        }
    }

    /** Forgets every bounty value received; the next visit re-asks for its own page. */
    static void resetBounty(final GovTreasuryPanel p) {
        p.bountyState = GovTreasuryTypes.LoadState.NONE;
        p.bountyError = "";
        p.latestBountyReqId = "";
        p.bountyRows.clear();
        p.bountyViews.clear();
        p.bountyHits.clear();
        p.bountyCancelHits.clear();
        p.bountyOnline.clear();
        p.bountyMin = 0L;
        p.bountyMax = 0L;
        p.bountyTotal = 0;
        p.bountySelectedId = "";
        p.bountyScroll = 0;
        p.bountyScrollMax = 0;
        p.bountyCancelArmed = false;
        p.bountyCancelArmedId = "";
        p.bountyCancelArmedAt = 0L;
        p.bountyCountView = "";
    }

    /** Row index of the stable bounty id, or {@code -1} when blank or absent. */
    static int indexOf(final List<BountyRow> rows, final String id) {
        if (id != null && !id.isBlank()) {
            for (int index = 0; index < rows.size(); index++) {
                if (id.equals(rows.get(index).id())) {
                    return index;
                }
            }
        }
        return -1;
    }

    static boolean containsId(final List<BountyRow> rows, final String id) {
        return indexOf(rows, id) >= 0;
    }

    static void clampScroll(final GovTreasuryPanel p) {
        p.bountyScrollMax = Math.max(0,
                p.bountyRows.size() - Math.max(0, p.bountyVisibleRows));
        p.bountyScroll = GovMetrics.clamp(p.bountyScroll, 0, p.bountyScrollMax);
    }

    /** Keeps the keyboard cursor inside the visible window after it moves. */
    static void reveal(final GovTreasuryPanel p) {
        final int selected = indexOf(p.bountyRows, p.bountySelectedId);
        if (selected < 0 || p.bountyVisibleRows <= 0) {
            return;
        }
        if (selected < p.bountyScroll) {
            p.bountyScroll = selected;
        } else if (selected >= p.bountyScroll + p.bountyVisibleRows) {
            p.bountyScroll = selected - p.bountyVisibleRows + 1;
        }
        clampScroll(p);
    }

    static String countLine(final GovTreasuryPanel p) {
        final int total = Math.max(0, p.bountyTotal);
        final String head = total == 1 ? "1 bounty" : total + " bounties";
        if (total > p.bountyRows.size() && !p.bountyRows.isEmpty()) {
            return head + " (" + p.bountyRows.size() + " shown)";
        }
        return head;
    }
}
