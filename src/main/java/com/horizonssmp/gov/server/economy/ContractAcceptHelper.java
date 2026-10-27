package com.horizonssmp.gov.server.economy;

import java.util.LinkedHashMap;
import java.util.Map;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.enums.ContractState;
import com.horizonssmp.gov.common.records.ContractRecord;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;

import net.minecraft.server.level.ServerPlayer;

final class ContractAcceptHelper {

    private final GovSavedData data;
    private final AccountService accounts;

    ContractAcceptHelper(final GovSavedData data, final AccountService accounts) {
        this.data = data;
        this.accounts = accounts;
    }

    EconomyResult accept(final ServerPlayer builder, final String id, final long now) {
        if (!ContractSupport.enabled()) {
            return EconomyResult.failure(ErrorCode.ECONOMY_DISABLED);
        }
        if (builder == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.ACTOR_REQUIRED);
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
        if (!contract.builder().equals(builder.getUUID())) {
            return EconomyResult.failure(
                    ErrorCode.FORBIDDEN, "Only the named builder can accept that contract.");
        }
        if (!ContractSupport.builderEligible(data, contract.builder())) {
            return EconomyResult.failure(ErrorCode.CONTRACT_LIMIT, ContractSupport.BUILDER_REQUIRED);
        }
        if (contract.state() != ContractState.POSTED) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE);
        }
        if (ContractSupport.isDue(contract, now)) {
            return EconomyResult.failure(
                    ErrorCode.INVALID_STATE, "That contract has expired.");
        }
        final long expiresAt;
        try {
            expiresAt = ContractSupport.deadline(now);
        } catch (final ArithmeticException overflow) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }

        final ContractRecord accepted =
                ContractSupport.copy(contract, ContractState.ACCEPTED, now, contract.submittedAt(),
                        contract.approvedAt(), expiresAt);
        data.putContract(accepted);
        data.markDirty();

        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(EconomyKeys.KEY_CONTRACT, accepted.id());
        payload.put(EconomyKeys.KEY_STATE, accepted.state().name());
        AuditService.append(
                data,
                builder,
                OpCode.CONTRACT_ACCEPT.name(),
                ContractSupport.target(data, contract.builder()),
                payload,
                "");

        return EconomyResult.success("Contract accepted.")
                .with(EconomyKeys.KEY_CONTRACT, accepted.id())
                .with(EconomyKeys.KEY_STATE, accepted.state().name());
    }

    EconomyResult submit(final ServerPlayer builder, final String id, final long now) {
        if (!ContractSupport.enabled()) {
            return EconomyResult.failure(ErrorCode.ECONOMY_DISABLED);
        }
        if (builder == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.ACTOR_REQUIRED);
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
        if (!contract.builder().equals(builder.getUUID())) {
            return EconomyResult.failure(
                    ErrorCode.FORBIDDEN, "Only the named builder can submit that contract.");
        }
        if (!ContractSupport.builderEligible(data, contract.builder())) {
            return EconomyResult.failure(ErrorCode.CONTRACT_LIMIT, ContractSupport.BUILDER_REQUIRED);
        }
        if (contract.state() != ContractState.ACCEPTED) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE);
        }
        if (ContractSupport.isDue(contract, now)) {
            return EconomyResult.failure(
                    ErrorCode.INVALID_STATE, "That contract has expired.");
        }

        final ContractRecord submitted =
                ContractSupport.copy(contract, ContractState.SUBMITTED, contract.acceptedAt(), now,
                        contract.approvedAt(), contract.expiresAt());
        data.putContract(submitted);
        data.markDirty();

        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(EconomyKeys.KEY_CONTRACT, submitted.id());
        payload.put(EconomyKeys.KEY_STATE, submitted.state().name());
        AuditService.append(
                data,
                builder,
                OpCode.CONTRACT_SUBMIT.name(),
                ContractSupport.target(data, contract.builder()),
                payload,
                "");

        return EconomyResult.success("Contract submitted.")
                .with(EconomyKeys.KEY_CONTRACT, submitted.id())
                .with(EconomyKeys.KEY_STATE, submitted.state().name());
    }
}