package com.horizonssmp.gov.server;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.horizonssmp.gov.common.enums.AccountStatus;
import com.horizonssmp.gov.common.enums.AccountType;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.LedgerEntry;

final class GovStartupCheckEconomyPart1 {

    private GovStartupCheckEconomyPart1() {
    }

    static int run(final GovSavedData data, final List<String> failures) {
        int checks = 0;

        checks++;
        if (GovSavedData.SCHEMA_VERSION != 2 || data.schemaVersion() != 2) {
            failures.add("economy: schema version is " + data.schemaVersion()
                    + " (constant " + GovSavedData.SCHEMA_VERSION + "), expected 2");
        }

        checks += expectEconomySingleton(data, GovSavedData.GOVERNMENT_ACCOUNT_ID,
                AccountType.GOVERNMENT, failures);
        checks += expectEconomySingleton(data, GovSavedData.POLICE_ACCOUNT_ID,
                AccountType.POLICE, failures);

        long balanceSum = 0L;
        String blankId = null;
        String nullShape = null;
        String outOfRange = null;
        String keyMismatch = null;
        for (final Map.Entry<String, AccountRecord> entry : data.accounts().entrySet()) {
            final String key = entry.getKey();
            final AccountRecord account = entry.getValue();
            if (account == null) {
                nullShape = nullShape == null ? String.valueOf(key) : nullShape;
                continue;
            }
            if (blankId == null && (account.id() == null || account.id().isBlank())) {
                blankId = String.valueOf(key);
            }
            if (nullShape == null && (account.type() == null || account.status() == null)) {
                nullShape = String.valueOf(key);
            }
            if (outOfRange == null
                    && (account.balance() < 0L || account.balance() > AccountRecord.MAX_BALANCE)) {
                outOfRange = key + " balance " + account.balance();
            }
            if (keyMismatch == null && (key == null || !key.equals(account.id()))) {
                keyMismatch = key + " -> " + account.id();
            }
            balanceSum += account.balance();
        }

        checks++;
        if (blankId != null) {
            failures.add("economy: account " + blankId + " has a blank id");
        }
        checks++;
        if (nullShape != null) {
            failures.add("economy: account " + nullShape + " has a null record, type or status");
        }
        checks++;
        if (outOfRange != null) {
            failures.add("economy: account " + outOfRange + " is outside 0.."
                    + AccountRecord.MAX_BALANCE);
        }
        checks++;
        if (keyMismatch != null) {
            failures.add("economy: account map key " + keyMismatch
                    + " does not match the record id");
        }

        long previousSeq = 0L;
        final Set<Long> sequences = new HashSet<>();
        String nonPositiveSeq = null;
        String outOfOrderSeq = null;
        String duplicateSeq = null;
        String nonPositiveGross = null;
        String negativeTax = null;
        String badNet = null;
        String negativeNet = null;
        String nullKind = null;
        String blankRef = null;
        for (final LedgerEntry entry : data.ledger()) {
            if (entry == null) {
                continue;
            }
            final long seq = entry.seq();
            if (nonPositiveSeq == null && seq <= 0L) {
                nonPositiveSeq = Long.toString(seq);
            }
            if (outOfOrderSeq == null && seq <= previousSeq) {
                outOfOrderSeq = seq + " after " + previousSeq;
            }
            previousSeq = seq;
            if (duplicateSeq == null && !sequences.add(seq)) {
                duplicateSeq = Long.toString(seq);
            }
            if (nonPositiveGross == null && entry.gross() <= 0L) {
                nonPositiveGross = "seq " + seq + " gross " + entry.gross();
            }
            if (negativeTax == null && entry.tax() < 0L) {
                negativeTax = "seq " + seq + " tax " + entry.tax();
            }
            if (badNet == null && entry.net() != entry.gross() - entry.tax()) {
                badNet = "seq " + seq + " net " + entry.net() + " gross " + entry.gross()
                        + " tax " + entry.tax();
            }
            if (negativeNet == null && entry.net() < 0L) {
                negativeNet = "seq " + seq + " net " + entry.net();
            }
            if (nullKind == null && entry.kind() == null) {
                nullKind = "seq " + seq;
            }
            if (blankRef == null
                    && (entry.from() == null || entry.from().isBlank()
                            || entry.to() == null || entry.to().isBlank())) {
                blankRef = "seq " + seq + " from '" + entry.from() + "' to '" + entry.to() + "'";
            }
        }

        checks++;
        if (nonPositiveSeq != null) {
            failures.add("economy: ledger has a non-positive sequence " + nonPositiveSeq);
        }
        checks++;
        if (outOfOrderSeq != null) {
            failures.add("economy: ledger sequence is not strictly increasing at " + outOfOrderSeq);
        }
        checks++;
        if (duplicateSeq != null) {
            failures.add("economy: ledger sequence repeats " + duplicateSeq);
        }
        checks++;
        if (nonPositiveGross != null) {
            failures.add("economy: ledger entry has non-positive gross: " + nonPositiveGross);
        }
        checks++;
        if (negativeTax != null) {
            failures.add("economy: ledger entry has negative tax: " + negativeTax);
        }
        checks++;
        if (badNet != null) {
            failures.add("economy: ledger entry breaks net = gross - tax: " + badNet);
        }
        checks++;
        if (negativeNet != null) {
            failures.add("economy: ledger entry has negative net: " + negativeNet);
        }
        checks++;
        if (nullKind != null) {
            failures.add("economy: ledger entry has no kind: " + nullKind);
        }
        checks++;
        if (blankRef != null) {
            failures.add("economy: ledger entry has a blank from/to reference: " + blankRef);
        }

        final EconomyMeta meta = data.economyMeta();
        checks++;
        final long conserved = balanceSum + meta.contractEscrow();
        final long physical = meta.totalDeposited() - meta.totalWithdrawn();
        if (conserved != physical) {
            failures.add("economy: conservation invariant failed: balances + escrow = "
                    + conserved + ", deposited - withdrawn = " + physical);
        }


        return checks;
    }

    private static int expectEconomySingleton(
            final GovSavedData data,
            final String id,
            final AccountType type,
            final List<String> failures) {
        final AccountRecord account = data.account(id);
        int checks = 0;

        checks++;
        if (account == null) {
            failures.add("economy: " + id + " account is missing");
        }

        checks++;
        if (account != null && account.type() != type) {
            failures.add("economy: " + id + " account type is " + account.type()
                    + ", expected " + type);
        }

        checks++;
        if (account != null && account.status() != AccountStatus.OPEN) {
            failures.add("economy: " + id + " account status is " + account.status()
                    + ", expected OPEN");
        }

        checks++;
        if (account != null && account.balance() < 0L) {
            failures.add("economy: " + id + " account balance is negative: " + account.balance());
        }
        return checks;
    }

    /** The live seeded jobs must be the matrix. */

}
