package com.horizonssmp.gov.command;

import java.util.Map;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.server.GovTargets;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class EconomyContractCommands {

    private EconomyContractCommands() {
    }

    static int contractPost(final CommandContext<CommandSourceStack> context) {
        if (EconomyCommandHelpers.requirePlayer(context, "/gov contract post") == null) {
            return 0;
        }
        final GovTargets.Resolved builder = EconomyCommandHelpers.resolveTarget(context, EconomyCommandHelpers.arg(context, "builder"));
        if (builder == null) {
            return 0;
        }
        return GovCommand.dispatch(context, "/gov contract post", OpCode.CONTRACT_POST,
                Map.of(GovTargets.KEY_TARGET, builder.uuid().toString(),
                        EconomyCommands.KEY_MATERIALS, EconomyCommandHelpers.arg(context, "materials"),
                        EconomyCommands.KEY_LABOR, EconomyCommandHelpers.arg(context, "labor"),
                        EconomyCommands.KEY_DESCRIPTION, EconomyCommandHelpers.arg(context, "description"),
                        EconomyKeys.KEY_NONCE, EconomyCommandHelpers.nonce()));
    }

    static int contractList(
            final CommandContext<CommandSourceStack> context, final String rawPage) {
        final Map<String, String> fields = EconomyCommandHelpers.read(context, "/gov contract list",
                OpCode.CONTRACT_LIST,
                Map.of(EconomyKeys.KEY_PAGE, rawPage == null ? "0" : rawPage));
        if (fields == null) {
            return 0;
        }
        EconomyCommandHelpers.send(context, "Contracts: " + EconomyCommandHelpers.value(fields, "contract.total")
                + "  Page: " + EconomyCommandHelpers.pageText(fields, "contract.page", "contract.pages"));
        int index = 0;
        while (fields.containsKey(EconomyCommands.CONTRACT_ROW + index + ".id")) {
            final String row = EconomyCommands.CONTRACT_ROW + index + ".";
            EconomyCommandHelpers.send(context, EconomyCommandHelpers.value(fields, row + "id")
                    + "  " + EconomyCommandHelpers.value(fields, row + "state")
                    + "  poster " + EconomyCommandHelpers.shortId(fields.get(row + "poster"))
                    + "  builder " + EconomyCommandHelpers.shortId(fields.get(row + "builder"))
                    + "  materials " + EconomyCommandHelpers.value(fields, row + "materials")
                    + "  labor " + EconomyCommandHelpers.value(fields, row + "labor")
                    + "  total " + EconomyCommandHelpers.value(fields, row + "total")
                    + "  " + EconomyCommandHelpers.value(fields, row + "description"));
            index++;
        }
        if (index == 0) {
            EconomyCommandHelpers.send(context, "No contracts on this page.");
        }
        return 1;
    }

    static int contractMove(
            final CommandContext<CommandSourceStack> context,
            final String verb,
            final OpCode op) {
        return GovCommand.dispatch(context, "/gov contract " + verb, op,
                Map.of(EconomyKeys.KEY_CONTRACT, EconomyCommandHelpers.arg(context, "id"),
                        EconomyKeys.KEY_NONCE, EconomyCommandHelpers.nonce()));
    }

    static int contractVoid(final CommandContext<CommandSourceStack> context) {
        return GovCommand.dispatch(context, "/gov contract void", OpCode.CONTRACT_VOID,
                Map.of(EconomyKeys.KEY_CONTRACT, EconomyCommandHelpers.arg(context, "id"),
                        EconomyKeys.KEY_REASON, EconomyCommandHelpers.arg(context, "reason"),
                        EconomyKeys.KEY_NONCE, EconomyCommandHelpers.nonce()));
    }
}
