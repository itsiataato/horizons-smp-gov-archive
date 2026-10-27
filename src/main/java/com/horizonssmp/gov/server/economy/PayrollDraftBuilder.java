package com.horizonssmp.gov.server.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.records.PlayerRecord;

/**
 * Builds payroll rosters (drafts) for Government, Police and business scopes.
 * Behavior is identical to the previous in-class implementation.
 */
final class PayrollDraftBuilder {

    private static final long DAY_MILLIS = 86_400_000L;

    private PayrollDraftBuilder() {
    }

    static PayrollDraft buildDepartmentDraft(
            final GovSavedData data, final String scope, final UUID actorId, final long now) {
        final List<PlayerRecord> players = new ArrayList<>(data.players().values());
        players.sort(Comparator.comparing(player -> player.uuid().toString()));

        final List<PayrollPayment> payments = new ArrayList<>();
        boolean skippedSelf = false;
        for (final PlayerRecord player : players) {
            if (!scope.equals(departmentOf(player.primaryJobId()))) {
                continue;
            }
            final long wage = Math.min(wageForJob(player.primaryJobId()), GovConfig.payrollPerPayout());
            if (wage <= 0L) {
                continue;
            }
            if (player.uuid().equals(actorId)) {
                skippedSelf = true;
                continue;
            }
            payments.add(new PayrollPayment(
                    AccountService.playerAccountId(player.uuid()),
                    wage,
                    0L,
                    null));
        }
        return new PayrollDraft(payments, skippedSelf ? 1 : 0, 0);
    }

    static PayrollDraft buildBusinessDraft(
            final BusinessRecord business, final UUID actorId, final long now) {
        final List<EmploymentContractRecord> contracts =
                new ArrayList<>(business.contracts());
        contracts.sort(Comparator.comparing(EmploymentContractRecord::id));

        final List<PayrollPayment> payments = new ArrayList<>();
        boolean skippedSelf = false;
        int skippedTax = 0;
        for (final EmploymentContractRecord contract : contracts) {
            if (!contract.active() || contract.wagePerDay() <= 0L) {
                continue;
            }
            if (contract.employeeUuid().equals(actorId)) {
                skippedSelf = true;
                continue;
            }
            final long base = contract.lastPaidAt() > 0L
                    ? contract.lastPaidAt() : contract.startedAt();
            if (base <= 0L || now - base < DAY_MILLIS) {
                continue;
            }
            final long elapsedDays =
                    Math.max(0L, (now - contract.startedAt()) / DAY_MILLIS);
            final long owed = saturatingMultiply(contract.wagePerDay(), elapsedDays);
            final long arrears = owed > contract.paidTotal()
                    ? owed - contract.paidTotal() : 0L;
            final long payable = Math.min(arrears, GovConfig.payrollArrearsCap());
            final long gross = Math.min(GovConfig.payrollPerPayout(), payable);
            if (gross <= 0L) {
                continue;
            }
            final long tax = EconomyMath.tax(gross);
            final long net = gross - tax;
            if (net < 1L) {
                skippedTax++;
                continue;
            }
            final EmploymentContractRecord stamped =
                    contract.withPayroll(base + DAY_MILLIS, gross);
            payments.add(new PayrollPayment(
                    AccountService.playerAccountId(contract.employeeUuid()),
                    gross,
                    tax,
                    stamped));
        }
        return new PayrollDraft(payments, skippedSelf ? 1 : 0, skippedTax);
    }

    private static String departmentOf(final String jobId) {
        if (GovCapabilities.GOVWORKER_JOB_ID.equals(jobId)
                || GovCapabilities.GOVEXECUTIVE_JOB_ID.equals(jobId)) {
            return PayrollService.SCOPE_GOV;
        }
        if (GovCapabilities.POLICE_JOB_ID.equals(jobId)) {
            return PayrollService.SCOPE_POLICE;
        }
        return "";
    }

    private static long wageForJob(final String jobId) {
        if (GovCapabilities.GOVWORKER_JOB_ID.equals(jobId)
                || GovCapabilities.GOVEXECUTIVE_JOB_ID.equals(jobId)) {
            return GovConfig.payrollGovWage();
        }
        if (GovCapabilities.POLICE_JOB_ID.equals(jobId)) {
            return GovConfig.payrollPoliceWage();
        }
        return GovConfig.payrollDefaultJobWage();
    }

    private static long saturatingMultiply(final long wage, final long days) {
        if (wage <= 0L || days <= 0L) {
            return 0L;
        }
        if (wage > Long.MAX_VALUE / days) {
            return Long.MAX_VALUE;
        }
        return wage * days;
    }
}