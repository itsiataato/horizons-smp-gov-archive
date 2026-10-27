package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.Sanitizer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

final class BankServiceConsole {

    static EconomyResult consoleCredit(
            final BankService service,
            final ServerPlayer source,
            final UUID target,
            final long amount,
            final String reason) {
        if (source == null) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }
        final CommandSourceStack commandSource = source.createCommandSourceStack();
        if (commandSource == null || !commandSource.hasPermission(4)) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }
        if (target == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        if (amount <= 0L || amount > EconomyMath.MAX_BALANCE) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final String cleanReason =
                Sanitizer.singleLine(reason, AccountService.MAX_REASON_LENGTH);
        if (cleanReason.length() < AccountService.MIN_REASON_LENGTH) {
            return EconomyResult.failure(
                    ErrorCode.BAD_PAYLOAD, BankService.CONSOLE_REASON_REQUIRED);
        }
        return service.accounts.withActorLock(target,
                () -> consoleCreditLocked(service, source, target, amount, cleanReason));
    }

    private static EconomyResult consoleCreditLocked(
            final BankService service,
            final ServerPlayer source,
            final UUID target,
            final long amount,
            final String cleanReason) {
        final String accountId = AccountService.playerAccountId(target);
        service.accounts.player(target);
        final EconomyResult credited = service.accounts.credit(accountId, amount, cleanReason);
        if (credited.failed()) {
            return credited;
        }

        final long balance = BankServiceHelpers.balanceOf(service.accounts, accountId);
        final PlayerRecord record = service.data.player(target);
        final Map<String, String> payload =
                new LinkedHashMap<>(BankServiceHelpers.auditPayload(accountId, amount, 0L));
        payload.put(BankService.KEY_ACTOR, source.getUUID().toString());
        payload.put(BankService.KEY_ACTOR_NAME, source.getName().getString());
        payload.put(BankService.KEY_ORIGIN, BankService.ORIGIN_SYSTEM);
        AuditService.append(
                service.data,
                source.getUUID(),
                source.getName().getString(),
                "",
                OpCode.CONTRACT_CREDIT.name(),
                target,
                record == null ? "" : record.lastName(),
                Sanitizer.payloadSummary(payload),
                cleanReason);
        return BankServiceHelpers.movementSuccess("", accountId, balance,
                TxnDraft.PHYSICAL, accountId, amount, 0L, "");
    }
}