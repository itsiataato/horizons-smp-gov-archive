package com.horizonssmp.gov.command;

import java.util.Map;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.OpCode;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

final class EconomyPayrollCommands {

    private EconomyPayrollCommands() {
    }

    static int payrollRun(
            final CommandContext<CommandSourceStack> context, final String reason) {
        if (EconomyCommandHelpers.requirePlayer(context, "/gov payroll run") == null) {
            return 0;
        }
        return GovCommand.dispatch(context, "/gov payroll run", OpCode.PAYROLL_RUN,
                Map.of(EconomyKeys.KEY_SCOPE, EconomyCommandHelpers.arg(context, "scope"),
                        EconomyKeys.KEY_REASON, reason == null ? "" : reason,
                        EconomyKeys.KEY_NONCE, EconomyCommandHelpers.nonce()));
    }
}
