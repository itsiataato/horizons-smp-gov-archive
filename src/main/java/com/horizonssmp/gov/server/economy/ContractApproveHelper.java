package com.horizonssmp.gov.server.economy;

import java.util.LinkedHashMap;
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
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;

import net.minecraft.server.level.ServerPlayer;

final class ContractApproveHelper {

    private final GovSavedData data;
    private final AccountService accounts;

    ContractApproveHelper(final GovSavedData data, final AccountService accounts) {
        this.data = data;
        this.accounts = accounts;
    }

    EconomyResult approve(final ServerPlayer actor, final String id, final long now) {
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
        if (contract.state() != ContractState.SUBMITTED) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE);
        }
        if (!ContractSupport.builderEligible(data, contract.builder())) {
            return EconomyResult.failure(ErrorCode.CONTRACT_LIMIT, ContractSupport.BUILDER_REQUIRED);
        }
        if (ContractSupport.isDue(contract, now)) {
            return EconomyResult.failure(
                    ErrorCode.INVALID_STATE, "That contract has expired.");
        }
        if (contract.poster().equals(actor.getUUID())) {
            return EconomyResult.failure(
                    ErrorCode.FORBIDDEN, "You cannot approve your own contract.");
        }
        if (contract.builder().equals(actor.getUUID())) {
            return EconomyResult.failure(
                    ErrorCode.FORBIDDEN, "The builder cannot approve their own contract.");
        }

        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(EconomyKeys.KEY_CONTRACT, contract.id());
        payload.put(EconomyKeys.KEY_STATE, contract.state().name());
        payload.put("total", Long.toString(contract.total()));

        if (!GovCapabilities.holds(
                data.player(actor.getUUID()), GovCapabilities.GOVEXECUTIVE_JOB_ID)) {
            final GovTargets.Resolved reviewer = ContractSupport.independentReviewer(actor, data);
            if (reviewer == null) {
                return EconomyResult.failure(
                        ErrorCode.NO_LEGAL_BASIS, ContractSupport.SECOND_REVIEWER_REQUIRED);
            }
            payload.put("reviewer", reviewer.name());
            payload.put("reviewerUuid", reviewer.uuid().toString());
        }

        final AccountRecord builderAccount = accounts.player(contract.builder());
        if (!builderAccount.acceptsCredit()) {
            return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
        }
        final long newBalance;
        try {
            newBalance = Math.addExact(builderAccount.balance(), contract.total());
        } catch (final ArithmeticException overflow) {
            return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
        }
        if (newBalance > AccountRecord.MAX_BALANCE) {
            return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
        }

        final EconomyMeta meta = data.economyMeta();
        if (meta.contractEscrow() < contract.total()) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE, ContractSupport.ESCROW_INCONSISTENT);
        }
        final long ledgerSeq = accounts.nextSeq();
        final AccountRecord updatedBuilder =
                ContractSupport.withBalance(builderAccount, newBalance, ledgerSeq);
        final EconomyMeta updatedMeta = ContractSupport.withMeta(
                meta,
                meta.contractEscrow() - contract.total(),
                ledgerSeq + 1L,
                meta.nextContractSeq());
        final ContractRecord approved = ContractSupport.copy(
                contract,
                ContractState.APPROVED,
                contract.acceptedAt(),
                contract.submittedAt(),
                now,
                contract.expiresAt());
        final LedgerEntry entry = new LedgerEntry(
                ledgerSeq,
                now,
                ContractSupport.TXN_APPROVE,
                com.horizonssmp.gov.common.enums.LedgerKind.CONTRACT,
                ContractSupport.ESCROW,
                builderAccount.id(),
                contract.total(),
                0L,
                actor.getUUID(),
                "contract " + contract.id() + " approved");

        data.putAccount(updatedBuilder);
        data.setEconomyMeta(updatedMeta);
        data.putContract(approved);
        data.appendLedger(entry);
        data.markDirty();

        AuditService.append(
                data,
                actor,
                OpCode.CONTRACT_APPROVE.name(),
                ContractSupport.target(data, contract.builder()),
                payload,
                "");

        return EconomyResult.success("Contract approved.")
                .with(EconomyKeys.KEY_CONTRACT, approved.id())
                .with(EconomyKeys.KEY_STATE, approved.state().name())
                .with(EconomyKeys.KEY_AMOUNT, Long.toString(contract.total()))
                .with(EconomyKeys.KEY_ACCOUNT, builderAccount.id());
    }
}