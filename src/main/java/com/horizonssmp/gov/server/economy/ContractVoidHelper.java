package com.horizonssmp.gov.server.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.enums.ContractState;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.ContractRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;
import com.horizonssmp.gov.server.Sanitizer;

import net.minecraft.server.level.ServerPlayer;

final class ContractVoidHelper {

    private final GovSavedData data;
    private final AccountService accounts;

    ContractVoidHelper(final GovSavedData data, final AccountService accounts) {
        this.data = data;
        this.accounts = accounts;
    }

    EconomyResult voidContract(
            final ServerPlayer actor, final String id, final String reason, final long now) {
        if (!ContractSupport.enabled()) {
            return EconomyResult.failure(ErrorCode.ECONOMY_DISABLED);
        }
        if (actor == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.ACTOR_REQUIRED);
        }
        if (id == null || id.isBlank()) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.CONTRACT_REQUIRED);
        }
        if (now < 0L) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (!ContractSupport.holdsManage(data, actor)) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }
        final ContractRecord contract = data.contract(id.strip());
        if (contract == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET, ContractSupport.CONTRACT_MISSING);
        }
        if (contract.terminal()) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE);
        }
        if (contract.poster().equals(actor.getUUID())) {
            return EconomyResult.failure(
                    ErrorCode.FORBIDDEN, "You cannot void your own contract.");
        }
        if (contract.builder().equals(actor.getUUID())) {
            return EconomyResult.failure(
                    ErrorCode.FORBIDDEN,
                    "The builder cannot void their own contract.");
        }
        GovTargets.Resolved reviewer = null;
        if (!GovCapabilities.holds(
                data.player(actor.getUUID()), GovCapabilities.GOVEXECUTIVE_JOB_ID)) {
            reviewer = ContractSupport.independentReviewer(actor, data);
            if (reviewer == null) {
                return EconomyResult.failure(
                        ErrorCode.NO_LEGAL_BASIS, ContractSupport.SECOND_REVIEWER_REQUIRED);
            }
        }
        final String cleanReason = Sanitizer.singleLine(reason, Sanitizer.MAX_TEXT);
        if (cleanReason.length() < ContractSupport.MIN_TEXT
                || cleanReason.length() > ContractSupport.MAX_REASON) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.REASON_INVALID);
        }

        final EconomyMeta meta = data.economyMeta();
        if (meta.contractEscrow() < contract.total()) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE, ContractSupport.ESCROW_INCONSISTENT);
        }

        final long ledgerSeq = accounts.nextSeq();
        final List<AccountRecord> replacements = new ArrayList<>(2);
        final List<LedgerEntry> entries = new ArrayList<>(2);
        final long nextLedgerSeq;

        if (contract.state() == ContractState.POSTED) {
            final AccountRecord government = accounts.government();
            if (!government.acceptsCredit()) {
                return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
            }
            final long newGovernmentBalance;
            try {
                newGovernmentBalance =
                        Math.addExact(government.balance(), contract.total());
            } catch (final ArithmeticException overflow) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            if (newGovernmentBalance > AccountRecord.MAX_BALANCE) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            replacements.add(ContractSupport.withBalance(government, newGovernmentBalance, ledgerSeq));
            entries.add(new LedgerEntry(
                    ledgerSeq,
                    now,
                    ContractSupport.TXN_VOID,
                    com.horizonssmp.gov.common.enums.LedgerKind.CONTRACT,
                    ContractSupport.ESCROW,
                    ContractSupport.GOVERNMENT,
                    contract.total(),
                    0L,
                    actor.getUUID(),
                    cleanReason));
            nextLedgerSeq = ledgerSeq + 1L;
        } else {
            final AccountRecord builderAccount = accounts.player(contract.builder());
            if (!builderAccount.acceptsCredit()) {
                return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
            }
            final long newBuilderBalance;
            try {
                newBuilderBalance =
                        Math.addExact(builderAccount.balance(), contract.materials());
            } catch (final ArithmeticException overflow) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            if (newBuilderBalance > AccountRecord.MAX_BALANCE) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            final AccountRecord government = accounts.government();
            if (!government.acceptsCredit()) {
                return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
            }
            final long newGovernmentBalance;
            try {
                newGovernmentBalance =
                        Math.addExact(government.balance(), contract.labor());
            } catch (final ArithmeticException overflow) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            if (newGovernmentBalance > AccountRecord.MAX_BALANCE) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            replacements.add(ContractSupport.withBalance(builderAccount, newBuilderBalance, ledgerSeq));
            replacements.add(ContractSupport.withBalance(government, newGovernmentBalance, ledgerSeq + 1L));
            entries.add(new LedgerEntry(
                    ledgerSeq,
                    now,
                    ContractSupport.TXN_VOID,
                    com.horizonssmp.gov.common.enums.LedgerKind.CONTRACT,
                    ContractSupport.ESCROW,
                    builderAccount.id(),
                    contract.materials(),
                    0L,
                    actor.getUUID(),
                    cleanReason));
            entries.add(new LedgerEntry(
                    ledgerSeq + 1L,
                    now,
                    ContractSupport.TXN_VOID,
                    com.horizonssmp.gov.common.enums.LedgerKind.CONTRACT,
                    ContractSupport.ESCROW,
                    ContractSupport.GOVERNMENT,
                    contract.labor(),
                    0L,
                    actor.getUUID(),
                    cleanReason));
            nextLedgerSeq = ledgerSeq + 2L;
        }

        final EconomyMeta updatedMeta = ContractSupport.withMeta(
                meta,
                meta.contractEscrow() - contract.total(),
                nextLedgerSeq,
                meta.nextContractSeq());
        final ContractRecord voided = ContractSupport.copy(
                contract,
                ContractState.VOIDED,
                contract.acceptedAt(),
                contract.submittedAt(),
                contract.approvedAt(),
                contract.expiresAt());

        for (final AccountRecord replacement : replacements) {
            data.putAccount(replacement);
        }
        data.setEconomyMeta(updatedMeta);
        data.putContract(voided);
        for (final LedgerEntry entry : entries) {
            data.appendLedger(entry);
        }
        data.markDirty();

        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(EconomyKeys.KEY_CONTRACT, voided.id());
        payload.put(EconomyKeys.KEY_STATE, voided.state().name());
        payload.put("total", Long.toString(voided.total()));
        if (reviewer != null) {
            payload.put("reviewer", reviewer.name());
            payload.put("reviewerUuid", reviewer.uuid().toString());
        }
        AuditService.append(
                data,
                actor,
                OpCode.CONTRACT_VOID.name(),
                ContractSupport.target(data, contract.builder()),
                payload,
                cleanReason);

        return EconomyResult.success("Contract voided.")
                .with(EconomyKeys.KEY_CONTRACT, voided.id())
                .with(EconomyKeys.KEY_STATE, voided.state().name())
                .with(EconomyKeys.KEY_AMOUNT, Long.toString(contract.total()));
    }
}