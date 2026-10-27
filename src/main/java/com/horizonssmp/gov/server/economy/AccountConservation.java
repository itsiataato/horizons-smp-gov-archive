package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;

final class AccountConservation {

    private AccountConservation() {
    }

    static long sumBalances(final AccountService service) {
        long total = 0L;
        for (final AccountRecord account : service.data().accounts().values()) {
            total = EconomyMath.addExact(total, account.balance());
        }
        return total;
    }

    static boolean conservationHolds(final AccountService service) {
        final EconomyMeta meta = service.data().economyMeta();
        if (meta.totalWithdrawn() > meta.totalDeposited()) {
            return false;
        }
        try {
            return EconomyMath.conservation(
                    sumBalances(service),
                    meta.contractEscrow(),
                    meta.totalDeposited(),
                    meta.totalWithdrawn());
        } catch (final ArithmeticException overflow) {
            return false;
        }
    }

    static void assertConservation(final AccountService service) {
        final EconomyMeta meta = service.data().economyMeta();
        final long sum;
        try {
            sum = sumBalances(service);
        } catch (final ArithmeticException overflow) {
            throw new IllegalStateException(
                    "economy balance sum overflowed; the file has to be repaired", overflow);
        }
        if (meta.totalWithdrawn() > meta.totalDeposited()) {
            throw new IllegalStateException(
                    "economy withdrew " + meta.totalWithdrawn() + " but only deposited "
                            + meta.totalDeposited());
        }
        if (!EconomyMath.conservation(
                sum, meta.contractEscrow(), meta.totalDeposited(), meta.totalWithdrawn())) {
            throw new IllegalStateException(
                    "economy conservation violated: balances=" + sum
                            + " escrow=" + meta.contractEscrow()
                            + " deposited=" + meta.totalDeposited()
                            + " withdrawn=" + meta.totalWithdrawn());
        }
    }
}