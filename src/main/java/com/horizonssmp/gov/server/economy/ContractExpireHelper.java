package com.horizonssmp.gov.server.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.enums.ContractState;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.ContractRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.Sanitizer;

import net.minecraft.server.level.ServerPlayer;

final class ContractExpireHelper {

    private final GovSavedData data;
    private final AccountService accounts;

    ContractExpireHelper(final GovSavedData data, final AccountService accounts) {
        this.data = data;
        this.accounts = accounts;
    }

    EconomyResult expire(final String id, final long now) {
        if (!ContractSupport.enabled()) {
            return EconomyResult.failure(ErrorCode.ECONOMY_DISABLED);
        }
        if (id == null || id.isBlank()) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.CONTRACT_REQUIRED);
        }
        if (now < 0L) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final ContractRecord contract = data.contract(id.strip());
        if (contract == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET, ContractSupport.CONTRACT_MISSING);
        }
        if (contract.terminal()) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE);
        }
        if (!ContractSupport.isDue(contract, now)) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE);
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
                    ContractSupport.TXN_EXPIRE,
                    com.horizonssmp.gov.common.enums.LedgerKind.CONTRACT,
                    ContractSupport.ESCROW,
                    ContractSupport.GOVERNMENT,
                    contract.total(),
                    0L,
                    null,
                    "contract " + contract.id() + " expired"));
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
                    ContractSupport.TXN_EXPIRE,
                    com.horizonssmp.gov.common.enums.LedgerKind.CONTRACT,
                    ContractSupport.ESCROW,
                    builderAccount.id(),
                    contract.materials(),
                    0L,
                    null,
                    "contract " + contract.id() + " expired"));
            entries.add(new LedgerEntry(
                    ledgerSeq + 1L,
                    now,
                    ContractSupport.TXN_EXPIRE,
                    com.horizonssmp.gov.common.enums.LedgerKind.CONTRACT,
                    ContractSupport.ESCROW,
                    ContractSupport.GOVERNMENT,
                    contract.labor(),
                    0L,
                    null,
                    "contract " + contract.id() + " expired"));
            nextLedgerSeq = ledgerSeq + 2L;
        }

        final EconomyMeta updatedMeta = ContractSupport.withMeta(
                meta,
                meta.contractEscrow() - contract.total(),
                nextLedgerSeq,
                meta.nextContractSeq());
        final ContractRecord expired = ContractSupport.copy(
                contract,
                ContractState.EXPIRED,
                contract.acceptedAt(),
                contract.submittedAt(),
                contract.approvedAt(),
                contract.expiresAt());

        for (final AccountRecord replacement : replacements) {
            data.putAccount(replacement);
        }
        data.setEconomyMeta(updatedMeta);
        data.putContract(expired);
        for (final LedgerEntry entry : entries) {
            data.appendLedger(entry);
        }
        data.markDirty();

        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(EconomyKeys.KEY_CONTRACT, expired.id());
        payload.put(EconomyKeys.KEY_STATE, expired.state().name());
        payload.put("total", Long.toString(expired.total()));
        AuditService.append(
                data,
                null,
                "",
                "",
                ContractSupport.ACTION_EXPIRE,
                contract.builder(),
                ContractSupport.target(data, contract.builder()).name(),
                Sanitizer.payloadSummary(payload),
                "contract " + contract.id() + " expired");

        return EconomyResult.success("Contract expired.")
                .with(EconomyKeys.KEY_CONTRACT, expired.id())
                .with(EconomyKeys.KEY_STATE, expired.state().name())
                .with(EconomyKeys.KEY_AMOUNT, Long.toString(contract.total()));
    }

    EconomyResult expireDue(final long now) {
        if (!ContractSupport.enabled()) {
            return EconomyResult.failure(ErrorCode.ECONOMY_DISABLED);
        }
        if (now < 0L) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        int expiredCount = 0;
        EconomyResult firstFailure = null;
        for (final String contractId : new ArrayList<>(data.openContractIds())) {
            final ContractRecord contract = data.contract(contractId);
            if (contract == null || contract.terminal() || !ContractSupport.isDue(contract, now)) {
                continue;
            }
            final EconomyResult result = expire(contract.id(), now);
            if (result.failed()) {
                if (firstFailure == null) {
                    firstFailure = result;
                }
            } else {
                expiredCount++;
            }
        }
        if (firstFailure != null) {
            return firstFailure;
        }
        return EconomyResult.success()
                .with("contract.expired", Integer.toString(expiredCount));
    }
}