package com.horizonssmp.gov.server;

import java.util.Locale;
import java.util.Optional;

import com.horizonssmp.gov.LawData;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.FineBand;
import com.horizonssmp.gov.common.enums.FineStatus;
import com.horizonssmp.gov.law.LawIndex;

public final class FineIssueParse {

    private FineIssueParse() {
    }

    public static ParseResult parseIssue(
            final String bandText, final String articleText, final String restitutionText,
            final String victimText, final String exileText, final String rawReason) {
        final FineBand band = FineBands.parseBand(bandText);
        if (band == null) {
            return ParseResult.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown fine band '" + Sanitizer.singleLine(bandText, Sanitizer.MAX_ID)
                            + "'. Use PETTY, MISDEMEANOR, FELONY or AGGRAVATED.");
        }

        final String rawArticle = articleText == null || articleText.isBlank()
                ? FineConstants.DEFAULT_ARTICLE : articleText.strip();
        final Optional<LawData.Article> article = LawIndex.get().article(rawArticle);
        if (article.isEmpty()) {
            return ParseResult.refused(
                    ErrorCode.BAD_ARTICLE,
                    "There is no law article '" + Sanitizer.singleLine(rawArticle, 32)
                            + "' in the bundled law.");
        }

        final Long restitution = parseWhole(restitutionText, "Restitution");
        if (restitution == null) {
            return ParseResult.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Restitution must be a whole number of gold ingots, zero or more.");
        }
        if (restitution > FineConstants.MAX_RESTITUTION_GOLD) {
            return ParseResult.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Restitution is capped at " + FineConstants.MAX_RESTITUTION_GOLD
                            + " gold ingots per fine; " + restitution + " is too high.");
        }
        final String victim = Sanitizer.name(victimText);
        if (restitution > 0L && victim.isBlank()) {
            return ParseResult.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Name the victim the restitution is owed to.");
        }
        if (restitution > 0L && band != FineBand.FELONY && band != FineBand.AGGRAVATED) {
            return ParseResult.refused(
                    ErrorCode.INVALID_STATE,
                    "Only a FELONY or AGGRAVATED fine may carry restitution.");
        }
        if (restitution > 0L && !com.horizonssmp.gov.config.GovConfig.allowRestitution()) {
            return ParseResult.refused(
                    ErrorCode.INVALID_STATE,
                    "Restitution is disabled on this server.");
        }

        final Long requestedExile = parseWhole(exileText, "Exile days");
        if (requestedExile == null) {
            return ParseResult.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Exile days must be a whole number, 0 for none.");
        }
        final int exileDays;
        if (band == FineBand.AGGRAVATED) {
            exileDays = requestedExile == 0L ? FineConstants.MIN_EXILE_DAYS : (int) Math.min(Integer.MAX_VALUE, requestedExile);
            if (exileDays < FineConstants.MIN_EXILE_DAYS || exileDays > FineConstants.MAX_EXILE_DAYS) {
                return ParseResult.refused(
                        ErrorCode.BAD_PAYLOAD,
                        "An AGGRAVATED fine carries an exile order of " + FineConstants.MIN_EXILE_DAYS
                                + " to " + FineConstants.MAX_EXILE_DAYS + " in-game days; "
                                + exileDays + " is outside that range.");
            }
        } else if (requestedExile > 0L) {
            return ParseResult.refused(
                    ErrorCode.INVALID_STATE,
                    "Only an AGGRAVATED fine carries an exile order.");
        } else {
            exileDays = 0;
        }

        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return ParseResult.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A fine needs a reason of at most " + Sanitizer.MAX_TEXT + " characters.");
        }

        return ParseResult.ok(new IssueSpec(band, article.get().id(), restitution, victim, exileDays, reason));
    }

    private static Long parseWhole(final String raw, final String field) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        final String text = raw.trim();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return null;
            }
        }
        try {
            return Long.parseLong(text);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    public record IssueSpec(
            FineBand band,
            String articleId,
            long restitution,
            String victim,
            int exileDays,
            String reason) {
    }

    public record ParseResult(IssueSpec spec, ErrorCode code, String message) {

        static ParseResult ok(final IssueSpec spec) {
            return new ParseResult(spec, null, "");
        }

        static ParseResult refused(final ErrorCode code, final String message) {
            return new ParseResult(null, code, message);
        }
    }
}
