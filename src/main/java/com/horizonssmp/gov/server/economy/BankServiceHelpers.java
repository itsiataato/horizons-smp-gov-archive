package com.horizonssmp.gov.server.economy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.AccountType;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.Sanitizer;

import net.minecraft.server.level.ServerPlayer;

final class BankServiceHelpers {

    static EconomyResult withBothLocks(
            final AccountService accounts,
            final UUID first,
            final UUID second,
            final Supplier<EconomyResult> work) {
        if (first == null) {
            return accounts.withActorLock(second, work);
        }
        if (second == null || first.equals(second)) {
            return accounts.withActorLock(first, work);
        }
        if (first.compareTo(second) <= 0) {
            return accounts.withActorLock(first, () -> accounts.withActorLock(second, work));
        }
        return accounts.withActorLock(second, () -> accounts.withActorLock(first, work));
    }

    static boolean overCap(final long balance, final long credit) {
        return balance > EconomyMath.MAX_BALANCE - credit;
    }

    static long balanceOf(final AccountService accounts, final String accountId) {
        final AccountService.Balance balance = accounts.balance(accountId);
        return balance == null ? 0L : balance.balance();
    }

    static Map<String, String> auditPayload(
            final String accountId, final long gross, final long tax) {
        return Map.of(
                EconomyKeys.KEY_ACCOUNT, accountId,
                EconomyKeys.KEY_AMOUNT, Long.toString(gross),
                EconomyKeys.KEY_TAX, Long.toString(tax),
                EconomyKeys.KEY_NET, Long.toString(gross - tax));
    }

    static void audit(
            final BankService service,
            final ServerPlayer actor,
            final OpCode op,
            final ServerPlayer target,
            final Map<String, String> payload,
            final String reason) {
        if (actor == null) {
            return;
        }
        final UUID actorId = actor.getUUID();
        final PlayerRecord record = service.data.player(actorId);
        AuditService.append(
                service.data,
                actorId,
                actor.getName().getString(),
                record == null ? "" : record.primaryJobId(),
                op.name(),
                target == null ? null : target.getUUID(),
                target == null ? "" : target.getName().getString(),
                Sanitizer.payloadSummary(payload),
                reason);
    }

    static EconomyResult success(
            final String message,
            final String accountId,
            final long balance,
            final long gross,
            final long tax,
            final boolean deposit) {
        final String from = deposit ? TxnDraft.PHYSICAL : accountId;
        final String to = deposit ? accountId : TxnDraft.PHYSICAL;
        return EconomyResult.success(message)
                .with(EconomyKeys.KEY_ACCOUNT, accountId)
                .with(EconomyKeys.KEY_BALANCE, Long.toString(balance))
                .with(EconomyKeys.KEY_AMOUNT, Long.toString(gross))
                .with(EconomyKeys.KEY_TAX, Long.toString(tax))
                .with(EconomyKeys.KEY_NET, Long.toString(gross - tax))
                .with(EconomyKeys.KEY_FROM, from)
                .with(EconomyKeys.KEY_TO, to);
    }

    static EconomyResult checkMovementAmount(
            final long gross, final long min, final long max) {
        if (gross <= 0L || gross < min) {
            return EconomyResult.failure(ErrorCode.AMOUNT_TOO_SMALL);
        }
        if (gross > max) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        return EconomyResult.success();
    }

    static EconomyResult checkMovementAccounts(
            final AccountRecord source,
            final AccountRecord destination,
            final long gross,
            final long tax) {
        if (source == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        if (!source.acceptsDebit()) {
            return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
        }
        if (destination == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        if (!destination.acceptsCredit()) {
            return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
        }
        if (source.balance() < gross) {
            return EconomyResult.failure(ErrorCode.INSUFFICIENT_FUNDS);
        }
        if (overCap(destination.balance(), gross - tax)) {
            return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
        }
        return EconomyResult.success();
    }

    static Map<String, String> movementPayload(
            final String fromId, final String toId, final long gross, final long tax) {
        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(EconomyKeys.KEY_ACCOUNT, fromId);
        payload.put(EconomyKeys.KEY_FROM, fromId);
        payload.put(EconomyKeys.KEY_TO, toId);
        payload.put(EconomyKeys.KEY_AMOUNT, Long.toString(gross));
        payload.put(EconomyKeys.KEY_TAX, Long.toString(tax));
        payload.put(EconomyKeys.KEY_NET, Long.toString(gross - tax));
        return payload;
    }

    static EconomyResult movementSuccess(
            final String message,
            final String accountId,
            final long balance,
            final String from,
            final String to,
            final long gross,
            final long tax,
            final String reason) {
        EconomyResult result = EconomyResult.success(message)
                .with(EconomyKeys.KEY_ACCOUNT, accountId)
                .with(EconomyKeys.KEY_BALANCE, Long.toString(balance))
                .with(EconomyKeys.KEY_FROM, from)
                .with(EconomyKeys.KEY_TO, to)
                .with(EconomyKeys.KEY_AMOUNT, Long.toString(gross))
                .with(EconomyKeys.KEY_TAX, Long.toString(tax))
                .with(EconomyKeys.KEY_NET, Long.toString(gross - tax));
        final String cleanReason = reason == null ? "" : reason.strip();
        return cleanReason.isEmpty()
                ? result
                : result.with(EconomyKeys.KEY_REASON, cleanReason);
    }
}