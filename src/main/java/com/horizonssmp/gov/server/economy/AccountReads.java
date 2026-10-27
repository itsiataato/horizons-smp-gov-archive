package com.horizonssmp.gov.server.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.common.enums.AccountStatus;
import com.horizonssmp.gov.common.enums.AccountType;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.server.GovSavedData;

final class AccountReads {

    private AccountReads() {
    }

    static AccountRecord lookup(final AccountService service, final String accountId) {
        final String id = AccountUtil.canonical(accountId);
        return id == null ? null : service.data().account(id);
    }

    static AccountRecord constant(final AccountService service, final String accountId) {
        final String id = AccountUtil.canonical(accountId);
        if (id == null) {
            return null;
        }
        if (GovSavedData.GOVERNMENT_ACCOUNT_ID.equals(id)) {
            return service.government();
        }
        if (GovSavedData.POLICE_ACCOUNT_ID.equals(id)) {
            return service.police();
        }
        return service.data().account(id);
    }

    static AccountRecord player(final AccountService service, final UUID owner) {
        if (owner == null) {
            throw new IllegalArgumentException("player requires a UUID");
        }
        final String id = AccountService.playerAccountId(owner);
        final AccountRecord existing = service.data().account(id);
        if (existing != null) {
            return existing;
        }
        final AccountRecord created = new AccountRecord(
                id, AccountType.PLAYER, owner, null, 0L, AccountStatus.OPEN, service.now(), 0L);
        service.data().putAccount(created);
        return created;
    }

    static AccountRecord business(final AccountService service, final String businessId) {
        if (businessId == null || businessId.isBlank()) {
            return null;
        }
        return service.data().account(AccountService.businessAccountId(businessId));
    }

    static AccountRecord openBusiness(final AccountService service, final String businessId) {
        final String id = AccountService.businessAccountId(businessId);
        final AccountRecord existing = service.data().account(id);
        if (existing != null) {
            return existing;
        }
        final AccountRecord created = new AccountRecord(
                id, AccountType.BUSINESS, null, businessId.strip(), 0L, AccountStatus.OPEN,
                service.now(), 0L);
        service.data().putAccount(created);
        return created;
    }

    static AccountRecord government(final AccountService service) {
        return singleton(service, GovSavedData.GOVERNMENT_ACCOUNT_ID, AccountType.GOVERNMENT);
    }

    static AccountRecord police(final AccountService service) {
        return singleton(service, GovSavedData.POLICE_ACCOUNT_ID, AccountType.POLICE);
    }

    static AccountService.Balance balance(final AccountService service, final String accountId) {
        final AccountRecord account = lookup(service, accountId);
        return account == null
                ? null
                : new AccountService.Balance(account.id(), account.balance(), account.status());
    }

    static AccountService.HistoryPage history(final AccountService service, final String accountId, final int requestedPage) {
        final String id = AccountUtil.canonical(accountId);
        if (id == null || TxnDraft.PHYSICAL.equals(id)) {
            return new AccountService.HistoryPage(List.of(), 0, 0, 1);
        }
        final boolean government = GovSavedData.GOVERNMENT_ACCOUNT_ID.equals(id);
        final List<LedgerEntry> ledger = service.data().ledger();
        final List<LedgerEntry> matching = new ArrayList<>();
        for (int i = ledger.size() - 1; i >= 0; i--) {
            final LedgerEntry entry = ledger.get(i);
            if (id.equals(entry.from()) || id.equals(entry.to()) || (government && entry.tax() > 0L)) {
                matching.add(entry);
            }
        }
        final int total = matching.size();
        final int pages = Math.max(1, (total + AccountService.HISTORY_PAGE_SIZE - 1) / AccountService.HISTORY_PAGE_SIZE);
        final int page = Math.max(0, Math.min(requestedPage, pages - 1));
        final int start = page * AccountService.HISTORY_PAGE_SIZE;
        final List<LedgerEntry> rows = start >= total
                ? List.of()
                : List.copyOf(matching.subList(start, Math.min(total, start + AccountService.HISTORY_PAGE_SIZE)));
        return new AccountService.HistoryPage(rows, total, page, pages);
    }

    static AccountRecord resolve(final AccountService service, final String accountId) {
        if (GovSavedData.GOVERNMENT_ACCOUNT_ID.equals(accountId)) {
            return service.government();
        }
        if (GovSavedData.POLICE_ACCOUNT_ID.equals(accountId)) {
            return service.police();
        }
        return service.data().account(accountId);
    }

    static AccountRecord singleton(final AccountService service, final String id, final AccountType type) {
        final AccountRecord existing = service.data().account(id);
        if (existing != null) {
            return existing;
        }
        final AccountRecord created = new AccountRecord(
                id, type, null, null, 0L, AccountStatus.OPEN, service.now(), 0L);
        service.data().putAccount(created);
        return created;
    }

    static long balanceOf(final AccountService service, final String accountId) {
        final AccountRecord account = service.data().account(accountId);
        return account == null ? 0L : account.balance();
    }
}