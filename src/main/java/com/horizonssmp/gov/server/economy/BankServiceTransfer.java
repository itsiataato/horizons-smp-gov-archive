package com.horizonssmp.gov.server.economy;import java.util.Locale;
import java.util.UUID;import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.AccountType;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.Sanitizer;import net.minecraft.server.level.ServerPlayer;

final class BankServiceTransfer {

    private record AccountRef(AccountType type, String id) {
    }

    static EconomyResult transfer(
            final BankService service,
            final ServerPlayer actor,
            final String fromId,
            final String toId,
            final long gross,
            final String reason,
            final String nonce) {
        final EconomyResult enabled = service.requireEnabled();
        if (enabled.failed()) {
            return enabled;
        }
        if (actor == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, BankService.ACTOR_REQUIRED);
        }
        if (!service.checkManage(actor)) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }
        if (!service.regions.isBank(actor)) {
            return EconomyResult.failure(ErrorCode.REGION_REQUIRED);
        }
        final EconomyResult shape = BankServiceHelpers.checkMovementAmount(gross, BankService.MIN_TRANSFER, BankService.MAX_TRANSFER);
        if (shape.failed()) {
            return shape;
        }

        final UUID actorId = actor.getUUID();
        final AccountRef from = parseAccountRef(service, fromId, actorId);
        if (from == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final AccountRef to = parseAccountRef(service, toId, actorId);
        if (to == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (from.id().equals(to.id())) {
            return EconomyResult.failure(ErrorCode.SELF_TRANSFER);
        }
        if (!pairAllowed(from.type(), to.type())) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }

        final String cleanReason =
                Sanitizer.singleLine(reason, AccountService.MAX_REASON_LENGTH);
        if (singletonType(from.type())
                && cleanReason.length() < AccountService.MIN_REASON_LENGTH) {
            return EconomyResult.failure(
                    ErrorCode.BAD_PAYLOAD, BankService.SINGLETON_REASON_REQUIRED);
        }
        if (!EconomyMath.validNonce(nonce)) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, BankService.NONCE_INVALID);
        }
        if (service.data.economyMeta().hasNonce(actorId, nonce)) {
            return EconomyResult.failure(ErrorCode.DUPLICATE_REQUEST);
        }

        final EconomyResult token = service.accounts.takeEconomyToken(actorId, OpCode.BANK_TRANSFER);
        if (token.failed()) {
            return token;
        }
        return service.accounts.withLocks(actorId, businessScope(from, to),
                () -> transferLocked(service, actor, from, to, gross, cleanReason, nonce));
    }

    private static EconomyResult transferLocked(
            final BankService service,
            final ServerPlayer actor,
            final AccountRef from,
            final AccountRef to,
            final long gross,
            final String reason,
            final String nonce) {
        final AccountRecord source = resolveAccountRef(service, from);
        if (source == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        if (!mayDebitSource(service, source, actor.getUUID())) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }
        final AccountRecord destination = resolveAccountRef(service, to);
        if (destination == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        if (source.id().equals(destination.id())) {
            return EconomyResult.failure(ErrorCode.SELF_TRANSFER);
        }
        if (!pairAllowed(source.type(), destination.type())) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }

        final UUID actorId = actor.getUUID();
        final boolean ownPlayerAccount = destination.type() == AccountType.PLAYER
                && destination.id().equals(AccountService.playerAccountId(actorId));
        if (singletonType(from.type())
                && (ownPlayerAccount || isOwnedBusiness(service, to, actorId))) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }

        final boolean singletonSource = source.singleton();
        if (singletonSource && reason.length() < AccountService.MIN_REASON_LENGTH) {
            return EconomyResult.failure(
                    ErrorCode.BAD_PAYLOAD, BankService.SINGLETON_REASON_REQUIRED);
        }
        final long tax = singletonSource ? 0L : service.tax(gross);
        if (gross - tax < 1L) {
            return EconomyResult.failure(ErrorCode.AMOUNT_TOO_SMALL);
        }
        final EconomyResult preconditions =
                BankServiceHelpers.checkMovementAccounts(source, destination, gross, tax);
        if (preconditions.failed()) {
            return preconditions;
        }

        final EconomyResult accepted = service.accounts.acceptNonce(actorId, nonce);
        if (accepted.failed()) {
            return accepted;
        }

        final EconomyResult moved = service.accounts.transfer(
                source.id(), destination.id(), gross, tax, BankService.TXN_TRANSFER,
                com.horizonssmp.gov.common.enums.LedgerKind.TRANSFER, actorId, reason);
        if (moved.failed()) {
            return moved;
        }

        final long balance = BankServiceHelpers.balanceOf(service.accounts, source.id());
        BankServiceHelpers.audit(service, actor, OpCode.BANK_TRANSFER, null,
                BankServiceHelpers.movementPayload(source.id(), destination.id(), gross, tax), reason);
        return BankServiceHelpers.movementSuccess("", source.id(), balance,
                source.id(), destination.id(), gross, tax, reason);
    }

    private static AccountRef parseAccountRef(final BankService service, final String ref, final UUID actor) {
        if (ref == null) {
            return null;
        }
        final String raw = ref.strip();
        if (raw.isEmpty()) {
            return null;
        }
        final String lower = raw.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "me":
                return actor == null
                        ? null
                        : new AccountRef(
                                AccountType.PLAYER, AccountService.playerAccountId(actor));
            case "gov":
            case "government":
                return new AccountRef(
                        AccountType.GOVERNMENT, GovSavedData.GOVERNMENT_ACCOUNT_ID);
            case "police":
                return new AccountRef(AccountType.POLICE, GovSavedData.POLICE_ACCOUNT_ID);
            default:
                break;
        }
        if (lower.startsWith(AccountService.PLAYER_PREFIX)) {
            final UUID owner =
                    parseUuid(raw.substring(AccountService.PLAYER_PREFIX.length()).strip());
            return owner == null
                    ? null
                    : new AccountRef(AccountType.PLAYER, AccountService.playerAccountId(owner));
        }
        if (lower.startsWith(AccountService.BUSINESS_PREFIX)) {
            final String businessId =
                    raw.substring(AccountService.BUSINESS_PREFIX.length()).strip();
            if (businessId.isEmpty() || service.data.business(businessId) == null) {
                return null;
            }
            return new AccountRef(
                    AccountType.BUSINESS, AccountService.businessAccountId(businessId));
        }
        return null;
    }

    private static UUID parseUuid(final String raw) {
        if (raw.length() != 36) {
            return null;
        }
        try {
            final UUID parsed = UUID.fromString(raw);
            return parsed.toString().equalsIgnoreCase(raw) ? parsed : null;
        } catch (final IllegalArgumentException invalid) {
            return null;
        }
    }

    private static AccountRecord resolveAccountRef(final BankService service, final AccountRef ref) {
        if (ref == null) {
            return null;
        }
        return service.accounts.lookup(ref.id());
    }

    private static boolean pairAllowed(final AccountType from, final AccountType to) {
        if (from == null || to == null) {
            return false;
        }
        switch (from) {
            case PLAYER:
                return to == AccountType.PLAYER;
            case BUSINESS:
                return to == AccountType.PLAYER
                        || to == AccountType.BUSINESS
                        || to == AccountType.GOVERNMENT
                        || to == AccountType.POLICE;
            case GOVERNMENT:
                return to == AccountType.POLICE
                        || to == AccountType.BUSINESS
                        || to == AccountType.PLAYER;
            case POLICE:
                return to == AccountType.PLAYER;
            default:
                return false;
        }
    }

    private static boolean mayDebitSource(final BankService service, final AccountRecord source, final UUID actorId) {
        if (source == null || actorId == null) {
            return false;
        }
        switch (source.type()) {
            case PLAYER:
                return source.id().equals(AccountService.playerAccountId(actorId));
            case BUSINESS:
                return mayDebitBusiness(service, source.id(), actorId);
            case GOVERNMENT:
            case POLICE:
                final PlayerRecord actorRecord = service.data.player(actorId);
                return GovCapabilities.holds(
                        actorRecord, GovCapabilities.GOVEXECUTIVE_JOB_ID);
            default:
                return false;
        }
    }

    private static boolean mayDebitBusiness(final BankService service, final String accountId, final UUID actorId) {
        if (!accountId.startsWith(AccountService.BUSINESS_PREFIX)) {
            return false;
        }
        final String businessId =
                accountId.substring(AccountService.BUSINESS_PREFIX.length());
        final BusinessRecord business = service.data.business(businessId);
        return business != null && actorId.equals(business.ownerUuid());
    }

    private static boolean isOwnedBusiness(final BankService service, final AccountRef ref, final UUID actorId) {
        if (ref == null || ref.type() != AccountType.BUSINESS || actorId == null) {
            return false;
        }
        final String businessId = businessIdOf(ref);
        if (businessId == null) {
            return false;
        }
        final BusinessRecord business = service.data.business(businessId);
        return business != null && actorId.equals(business.ownerUuid());
    }

    private static boolean singletonType(final AccountType type) {
        return type == AccountType.GOVERNMENT || type == AccountType.POLICE;
    }

    private static String businessScope(final AccountRef from, final AccountRef to) {
        final String source = businessIdOf(from);
        return source != null ? source : businessIdOf(to);
    }

    private static String businessIdOf(final AccountRef ref) {
        if (ref == null || ref.type() != AccountType.BUSINESS) {
            return null;
        }
        final String id = ref.id();
        if (!id.startsWith(AccountService.BUSINESS_PREFIX)) {
            return null;
        }
        final String businessId = id.substring(AccountService.BUSINESS_PREFIX.length());
        return businessId.isEmpty() ? null : businessId;
    }
}
