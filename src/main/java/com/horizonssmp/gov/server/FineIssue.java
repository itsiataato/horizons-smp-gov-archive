package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.horizonssmp.gov.LawData;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.FineBand;
import com.horizonssmp.gov.common.enums.FineStatus;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.GoldAmount;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.law.LawIndex;

public final class FineIssue {

    private FineIssue() {
    }

    public static FineService.Outcome issue(
            final GovSavedData data, final FineService.Actor actor, final UUID targetUuid, final String targetName,
            final String bandText, final String articleText, final String restitutionText,
            final String victimText, final String exileText, final String rawReason, final long now) {
        if (data == null || actor == null || targetUuid == null) {
            return FineService.Outcome.refused(ErrorCode.INVALID_STATE, "No person to fine.");
        }
        if (!actor.capabilities().contains(com.horizonssmp.gov.common.GovCapability.FINE_ISSUE)) {
            return FineService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + com.horizonssmp.gov.common.GovCapability.FINE_ISSUE.id() + " for that action.");
        }

        final FineIssueParse.ParseResult parsed = FineIssueParse.parseIssue(
                bandText, articleText, restitutionText, victimText, exileText, rawReason);
        if (parsed.spec() == null) {
            return FineService.Outcome.refused(parsed.code(), parsed.message());
        }
        final FineIssueParse.IssueSpec spec = parsed.spec();

        final PlayerRecord record = data.playerOrCreate(targetUuid, targetName, now);
        final String name = record.lastName().isBlank()
                ? Sanitizer.name(targetName) : record.lastName();
        final String id = FineIds.newFineId(data);
        final FineRecord fine = new FineRecord(
                id,
                targetUuid,
                actor.uuid(),
                spec.articleId(),
                spec.band(),
                GoldAmount.of(FineBands.bandGold(spec.band())),
                GoldAmount.of(spec.restitution()),
                spec.reason(),
                now,
                0L,
                FineStatus.UNPAID,
                0L,
                "",
                null,
                "",
                spec.exileDays(),
                spec.victim(),
                "",
                false);
        data.putFine(fine);
        HistoryService.append(data, targetUuid, HistoryKind.FINE_ISSUED,
                "Fine " + id + " (" + spec.band().name() + ", " + FineBands.bandGold(spec.band())
                        + " gold ingots) was issued: " + spec.reason(),
                actor.uuid(), actor.name(), now, id);

        final StringBuilder message = new StringBuilder("issued ")
                .append(spec.band().name())
                .append(" fine ").append(id)
                .append(" on ").append(name)
                .append(": ").append(FineBands.bandGold(spec.band())).append(" gold ingots");
        if (spec.restitution() > 0L) {
            message.append(" plus ").append(spec.restitution())
                    .append(" restitution to ").append(spec.victim());
        }
        message.append(" (article ").append(spec.articleId()).append(')');
        if (spec.exileDays() > 0) {
            message.append(", exile order ").append(spec.exileDays()).append(" in-game days");
        }
        message.append('.');
        return FineService.Outcome.ok(message.toString(), id);
    }

    public static FineService.Outcome issuePrecheck(
            final String bandText, final String articleText, final String restitutionText,
            final String victimText, final String exileText, final String rawReason) {
        final FineIssueParse.ParseResult parsed = FineIssueParse.parseIssue(
                bandText, articleText, restitutionText, victimText, exileText, rawReason);
        return parsed.spec() == null
                ? FineService.Outcome.refused(parsed.code(), parsed.message())
                : null;
    }

    public static FineService.Outcome issueComplaintFine(
            final GovSavedData data,
            final UUID targetUuid,
            final String targetDisplayName,
            final UUID issuerUuid,
            final String issuerDisplayName,
            final BusinessRecord business,
            final EmploymentContractRecord contract,
            final String rawReason,
            final long now) {
        if (data == null || targetUuid == null || issuerUuid == null
                || business == null || contract == null) {
            return FineService.Outcome.refused(ErrorCode.INVALID_STATE, "No complaint fine to issue.");
        }
        if (targetUuid.equals(issuerUuid)) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "A complaint fine cannot name the same person twice.");
        }
        if (!issuerUuid.equals(business.ownerUuid())) {
            return FineService.Outcome.refused(
                    ErrorCode.FORBIDDEN, "Only the business owner can be the restitution victim.");
        }
        if (!targetUuid.equals(contract.employeeUuid())
                || contract.status() != com.horizonssmp.gov.common.enums.EmploymentStatus.ACTIVE) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The complaint fine must name an active contract of the accused employee.");
        }
        if (LawIndex.get().article(FineConstants.DEFAULT_ARTICLE).isEmpty()) {
            return FineService.Outcome.refused(
                    ErrorCode.BAD_ARTICLE,
                    "Article " + FineConstants.DEFAULT_ARTICLE + " is missing from the bundled law.");
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return FineService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "A complaint fine needs a reason.");
        }
        final long restitution = Math.min(
                FineConstants.MAX_COMPLAINT_RESTITUTION_GOLD, Math.max(0L, contract.paidTotal()));
        final String id = FineIds.newFineId(data);
        final String victim = Sanitizer.name(
                issuerDisplayName == null || issuerDisplayName.isBlank()
                        ? FineLookups.targetName(data, issuerUuid) : issuerDisplayName);
        final String employee = Sanitizer.name(
                targetDisplayName == null || targetDisplayName.isBlank()
                        ? FineLookups.targetName(data, targetUuid) : targetDisplayName);
        data.playerOrCreate(targetUuid, employee, now);
        final FineRecord fine = new FineRecord(
                id,
                targetUuid,
                issuerUuid,
                FineConstants.DEFAULT_ARTICLE,
                FineBand.AGGRAVATED,
                GoldAmount.of(FineConstants.COMPLAINT_FINE_GOLD),
                GoldAmount.of(restitution),
                "Three upheld complaints under contract " + contract.id() + " at '"
                        + business.name() + "': " + reason,
                now,
                0L,
                FineStatus.UNPAID,
                0L,
                "",
                null,
                "",
                0,
                victim,
                "",
                false,
                issuerUuid,
                0L);
        data.putFine(fine);
        HistoryService.appendAll(data,
                List.of(targetUuid, issuerUuid),
                HistoryKind.FINE_ISSUED,
                "Complaint fine " + id + " (" + FineConstants.COMPLAINT_FINE_GOLD + " gold ingots, restitution "
                        + restitution + " to " + victim + ") was issued.",
                issuerUuid, victim, now, id);
        return FineService.Outcome.ok(
                "issued complaint fine " + id + " on " + employee + " for "
                        + FineConstants.COMPLAINT_FINE_GOLD + " gold ingots plus " + restitution
                        + " restitution to " + victim + ".",
                id);
    }

}

