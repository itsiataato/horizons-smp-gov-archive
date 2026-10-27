package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.enums.FineBand;
import com.horizonssmp.gov.common.enums.FineStatus;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.config.GovConfig;

final class GovStartupCheckFinesPart1 {

    private GovStartupCheckFinesPart1() {
    }

    static void run(final GovStartupCheckFinesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
                final GovSavedData scratch = ctx.scratch;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final FineService.Actor officer = ctx.officer;
        final FineService.Actor citizen = ctx.citizen;


            // The four band amounts and their source: Article 9.11, verbatim.
            ctx.checks++;
            if (FineService.PETTY_GOLD != 8L || FineService.MISDEMEANOR_GOLD != 16L
                    || FineService.FELONY_GOLD != 32L || FineService.AGGRAVATED_GOLD != 64L) {
                failures.add("the fine band amounts are not the Article 9.11 values 8/16/32/64");
            }
            ctx.checks++;
            if (FineService.bandGold(FineBand.PETTY) != 8L
                    || FineService.bandGold(FineBand.MISDEMEANOR) != 16L
                    || FineService.bandGold(FineBand.FELONY) != 32L
                    || FineService.bandGold(FineBand.AGGRAVATED) != 64L) {
                failures.add("a band does not map to its statutory gold amount");
            }
            ctx.checks++;
            if (FineService.parseBand("petty") != FineBand.PETTY
                    || FineService.parseBand("MISDEMEANOR") != FineBand.MISDEMEANOR
                    || FineService.parseBand("GRAVITY") != null
                    || FineService.parseBand("") != null) {
                failures.add("the band parser is not strict and case tolerant at the same time");
            }

            // Reception proximity: inside, boundary, outside, wrong dimension.
            final FineService.ReceptionPoint receptionPoint =
                    new FineService.ReceptionPoint("minecraft:overworld", 100.0d, 64.0d, 100.0d, 5.0d);
            ctx.checks++;
            if (!receptionPoint.contains("minecraft:overworld", 100.0d, 64.0d, 100.0d)
                    || !receptionPoint.contains("minecraft:overworld", 105.0d, 64.0d, 100.0d)) {
                failures.add("the reception check refused a point inside its radius");
            }
            ctx.checks++;
            if (receptionPoint.contains("minecraft:overworld", 105.01d, 64.0d, 100.0d)
                    || receptionPoint.contains("minecraft:the_nether", 100.0d, 64.0d, 100.0d)) {
                failures.add("the reception check accepted a point outside its radius or dimension");
            }
            ctx.checks++;
            if (FineService.configuredReception().isPresent() != GovConfig.receptionConfigured()) {
                failures.add("the configured reception does not follow gov.reception.configured");
            }


            // Issue round-trip: PETTY carries the statutory amount and no restitution.
            final FineService.Outcome petty = FineService.issue(
                    scratch, officer, alice, "StartupCheck Fine Alice",
                    "PETTY", "9.11", "0", "", "", "StartupCheck littering", now);
            ctx.checks++;
            if (!petty.ok()) {
                failures.add("could not issue a PETTY fine: " + petty.message());
            }
            final FineRecord pettyFine = scratch.fine(petty.fineId());
            ctx.checks++;
            if (pettyFine == null
                    || !pettyFine.id().startsWith("f-")
                    || !alice.equals(pettyFine.targetUuid())
                    || !officer.uuid().equals(pettyFine.issuerUuid())
                    || pettyFine.band() != FineBand.PETTY
                    || pettyFine.amount().count() != 8L
                    || pettyFine.restitution().count() != 0L
                    || !"9.11".equals(pettyFine.lawArticle())
                    || !"StartupCheck littering".equals(pettyFine.reason())
                    || pettyFine.createdAt() != now
                    || pettyFine.status() != FineStatus.UNPAID
                    || pettyFine.exileDays() != 0
                    || !pettyFine.victim().isEmpty()
                    || !pettyFine.paidAtReceptionRef().isEmpty()) {
                failures.add("the issued PETTY fine does not carry its band, amount, article and stamps");
            }

            // Article validation against the bundled law, never a hardcoded list.
            final int finesBefore = scratch.fines().size();
            ctx.checks++;
            if (FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                    "PETTY", "999.999", "0", "", "", "StartupCheck bad article", now)
                    .code() != ErrorCode.BAD_ARTICLE
                    || scratch.fines().size() != finesBefore) {
                failures.add("an unknown law article was accepted or wrote a fine");
            }
            ctx.checks++;
            if (FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                    "GRAVITY", "9.11", "0", "", "", "StartupCheck bad band", now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("an unknown fine band was accepted");
            }
            ctx.checks++;
            if (FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                    "PETTY", "9.11", "0", "", "", "   ", now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("a fine with no reason was accepted");
            }
            ctx.checks++;
            if (FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                    "PETTY", "9.11", "8", "StartupCheck Victim", "", "StartupCheck restitution", now)
                    .code() != ErrorCode.INVALID_STATE) {
                failures.add("a PETTY fine with restitution was accepted");
            }
            ctx.checks++;
            if (FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                    "FELONY", "9.11", Long.toString(FineService.MAX_RESTITUTION_GOLD + 1L),
                    "StartupCheck Victim", "", "StartupCheck over cap", now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("restitution above the hard cap was accepted");
            }
            ctx.checks++;
            if (FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                    "FELONY", "9.11", "10", "", "", "StartupCheck no victim", now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("restitution without a named victim was accepted");
            }

            // Felony restitution, when the server allows restitution at all.
            if (GovConfig.allowRestitution()) {
                final FineService.Outcome felony = FineService.issue(
                        scratch, officer, alice, "StartupCheck Fine Alice",
                        "FELONY", "9.11", "10", "StartupCheck Victim", "",
                        "StartupCheck felony with restitution", now);
                ctx.checks++;
                if (!felony.ok()) {
                    failures.add("could not issue a FELONY fine with restitution: " + felony.message());
                }
                final FineRecord felonyFine = scratch.fine(felony.fineId());
                ctx.checks++;
                if (felonyFine == null
                        || felonyFine.amount().count() != 32L
                        || felonyFine.restitution().count() != 10L
                        || felonyFine.totalDue().count() != 42L
                        || !"StartupCheck Victim".equals(felonyFine.victim())
                        || felonyFine.exileDays() != 0) {
                    failures.add("a FELONY fine did not carry its restitution, victim and total due");
                }
                ctx.checks++;
                if (!FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                        "FELONY", "9.11", Long.toString(FineService.MAX_RESTITUTION_GOLD),
                        "StartupCheck Victim", "", "StartupCheck restitution at the cap", now)
                        .ok()) {
                    failures.add("restitution at exactly the hard cap was refused");
                }
            } else {
                ctx.checks++;
                if (FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                        "FELONY", "9.11", "10", "StartupCheck Victim", "",
                        "StartupCheck restitution disabled", now)
                        .code() != ErrorCode.INVALID_STATE) {
                    failures.add("restitution was accepted while gov.fine.allowRestitution is false");
                }
            }

            // AGGRAVATED: default 7 days, explicit 30 accepted, 6 and 31 refused, exile only here.
            final FineService.Outcome aggravated = FineService.issue(
                    scratch, officer, alice, "StartupCheck Fine Alice",
                    "AGGRAVATED", "9.11", "0", "", "",
                    "StartupCheck aggravated", now);
            ctx.checks++;
            if (!aggravated.ok()) {
                failures.add("could not issue an AGGRAVATED fine: " + aggravated.message());
            }
            final FineRecord aggravatedFine = scratch.fine(aggravated.fineId());
            ctx.checks++;
            if (aggravatedFine == null
                    || aggravatedFine.exileDays() != FineService.MIN_EXILE_DAYS
                    || aggravatedFine.totalDue().count() != 64L) {
                failures.add("an AGGRAVATED fine did not default to a 7-day exile order");
            }
            ctx.checks++;
            if (!FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                    "AGGRAVATED", "9.11", "0", "", "30", "StartupCheck max exile", now).ok()) {
                failures.add("an AGGRAVATED fine with a 30-day exile order was refused");
            }
            ctx.checks++;
            if (FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                    "AGGRAVATED", "9.11", "0", "", "6", "StartupCheck short exile", now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("an AGGRAVATED exile below 7 days was accepted");
            }
            ctx.checks++;
            if (FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                    "AGGRAVATED", "9.11", "0", "", "31", "StartupCheck long exile", now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("an AGGRAVATED exile above 30 days was accepted");
            }
            ctx.checks++;
            if (FineService.issue(scratch, officer, alice, "StartupCheck Fine Alice",
                    "FELONY", "9.11", "0", "", "7", "StartupCheck exile on felony", now)
                    .code() != ErrorCode.INVALID_STATE) {
                failures.add("a non-AGGRAVATED fine carried an exile order");
            }

            // Empty article means the sentencing article, 9.11, still validated by data.
            final FineService.Outcome defaultArticle = FineService.issue(
                    scratch, officer, alice, "StartupCheck Fine Alice",
                    "MISDEMEANOR", "", "0", "", "", "StartupCheck default article", now);
            ctx.checks++;
            if (!defaultArticle.ok()
                    || scratch.fine(defaultArticle.fineId()) == null
                    || !FineService.DEFAULT_ARTICLE.equals(
                            scratch.fine(defaultArticle.fineId()).lawArticle())) {
                failures.add("an omitted article did not default to Article 9.11");
            }
            ctx.checks++;
            final FineService.Outcome sanitized = FineService.issue(
                    scratch, officer, alice, "StartupCheck Fine Alice",
                    "PETTY", "9.11", "0", "", "",
                    "\u00a7cStartupCheck\u00a7r sanitized reason", now);
            if (!sanitized.ok()
                    || scratch.fine(sanitized.fineId()) == null
                    || !"StartupCheck sanitized reason".equals(
                            scratch.fine(sanitized.fineId()).reason())) {
                failures.add("a fine reason did not have its formatting codes stripped");
            }
            ctx.checks++;
            if (FineService.issue(scratch, citizen, alice, "StartupCheck Fine Alice",
                    "PETTY", "9.11", "0", "", "", "StartupCheck no authority", now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a fine issue without fine.issue was accepted");
            }

            // ---------------------------------------------------------- payments

        ctx.aggravatedFine = aggravatedFine;
    }
}
