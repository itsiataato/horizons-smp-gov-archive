package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.server.economy.BankRegions;
import com.horizonssmp.gov.server.economy.StoreCatalog;
import net.minecraft.server.MinecraftServer;

final class GovStartupCheckEconomyPart2 {

    private GovStartupCheckEconomyPart2() {
    }

    static int run(final GovSavedData data, final MinecraftServer server, final List<String> failures) {
        int checks = 0;
        checks++;
        if (OpCode.values().length != 122) {
            failures.add("economy: OpCode count is " + OpCode.values().length
                    + ", expected 122");
        }
        final Set<OpCode> economyOps = EnumSet.of(
                OpCode.BANK_BALANCE,
                OpCode.BANK_HISTORY,
                OpCode.BANK_DEPOSIT,
                OpCode.BANK_WITHDRAW,
                OpCode.BANK_TRANSFER,
                OpCode.PAY_PLAYER,
                OpCode.PAYROLL_RUN,
                OpCode.STORE_LIST,
                OpCode.STORE_BUY,
                OpCode.CONTRACT_LIST,
                OpCode.CONTRACT_POST,
                OpCode.CONTRACT_ACCEPT,
                OpCode.CONTRACT_SUBMIT,
                OpCode.CONTRACT_APPROVE,
                OpCode.CONTRACT_VOID,
                OpCode.CONTRACT_CREDIT,
                OpCode.BANK_CREDIT);
        checks++;
        if (economyOps.size() != 17) {
            failures.add("economy: economy opcode set has " + economyOps.size()
                    + " entries, expected 17");
        }
        checks++;
        final List<OpCode> unregistered = new ArrayList<>();
        for (final OpCode op : economyOps) {
            if (!GovActionDispatcher.isRegistered(op)) {
                unregistered.add(op);
            }
        }
        if (!unregistered.isEmpty()) {
            failures.add("economy: economy opcodes have no handler: " + unregistered);
        }

        checks++;
        final List<String> bankErrors = BankRegions.current().validationErrors();
        if (!bankErrors.isEmpty()) {
            failures.add("economy: bank region configuration is invalid: "
                    + String.join("; ", bankErrors));
        }


        checks++;
        if (GovConfig.bankEnabled() && GovConfig.contractEnabled()
                && GovConfig.contractReviewer().isBlank()) {
            failures.add("economy: contract reviewer is blank while contracts are enabled");
        }

        checks++;
        // WHY: the lifecycle always passes the live server, so the recipe-derived gold scan runs
        // on a real boot; run(data) keeps null for headless tests and skips only that scan. The
        // direct gold rule and every configuration rule still run. Validation is not gated on
        // storeEnabled: StoreCatalog parses a disabled catalogue so a typo is never hidden by the
        // rollout flag.
        final List<String> storeErrors = StoreCatalog.load(server).validationErrors();
        if (!storeErrors.isEmpty()) {
            failures.add("economy: store catalogue is invalid: "
                    + String.join("; ", storeErrors));
        }


        return checks;
    }
}
