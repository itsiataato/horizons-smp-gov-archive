package com.horizonssmp.gov.command;

import java.util.Map;
import java.util.OptionalLong;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;
import com.horizonssmp.gov.server.economy.AccountService;
import com.horizonssmp.gov.server.economy.BankRegions;
import com.horizonssmp.gov.server.economy.BankService;
import com.horizonssmp.gov.server.economy.LiveVaultGateway;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bank subcommands for the economy surface.
 */
final class EconomyBankCommands {

    private EconomyBankCommands() {
    }

    static int bankBalance(
            final CommandContext<CommandSourceStack> context, final String rawAccount) {
        final String account = EconomyCommandHelpers.accountRef(context.getSource(), rawAccount);
        if (account == null) {
            context.getSource().sendFailure(Component.literal(EconomyCommands.UNKNOWN_ACCOUNT));
            return 0;
        }
        final Map<String, String> fields = EconomyCommandHelpers.read(context, "/gov bank balance",
                com.horizonssmp.gov.common.OpCode.BANK_BALANCE,
                Map.of(com.horizonssmp.gov.common.EconomyKeys.KEY_ACCOUNT, account));
        if (fields == null) {
            return 0;
        }
        EconomyCommandHelpers.send(context, "Account: " + EconomyCommandHelpers.value(fields, com.horizonssmp.gov.common.EconomyKeys.KEY_ACCOUNT));
        EconomyCommandHelpers.send(context, "Balance: " + EconomyCommandHelpers.value(fields, com.horizonssmp.gov.common.EconomyKeys.KEY_BALANCE));
        EconomyCommandHelpers.send(context, "Status: " + EconomyCommandHelpers.value(fields, com.horizonssmp.gov.common.EconomyKeys.KEY_STATUS));
        return 1;
    }

    static int bankHistory(
            final CommandContext<CommandSourceStack> context,
            final String rawAccount,
            final String rawPage) {
        final String account = EconomyCommandHelpers.accountRef(context.getSource(), rawAccount);
        if (account == null) {
            context.getSource().sendFailure(Component.literal(EconomyCommands.UNKNOWN_ACCOUNT));
            return 0;
        }
        final Map<String, String> fields = EconomyCommandHelpers.read(context, "/gov bank history",
                com.horizonssmp.gov.common.OpCode.BANK_HISTORY,
                Map.of(com.horizonssmp.gov.common.EconomyKeys.KEY_ACCOUNT, account,
                        com.horizonssmp.gov.common.EconomyKeys.KEY_PAGE, rawPage == null ? "0" : rawPage));
        if (fields == null) {
            return 0;
        }
        EconomyCommandHelpers.send(context, "Account: " + account);
        EconomyCommandHelpers.send(context, "Page: " + EconomyCommandHelpers.pageText(fields, com.horizonssmp.gov.common.EconomyKeys.KEY_PAGE, "pages")
                + "  Entries: " + EconomyCommandHelpers.value(fields, "total"));
        int index = 0;
        while (fields.containsKey(EconomyCommands.LEDGER_ROW + index + ".seq")) {
            final String row = EconomyCommands.LEDGER_ROW + index + ".";
            EconomyCommandHelpers.send(context, (index + 1) + ". " + EconomyCommandHelpers.when(fields.get(row + "at"))
                    + "  " + EconomyCommandHelpers.value(fields, row + "kind")
                    + "  " + EconomyCommandHelpers.value(fields, row + "from")
                    + " -> " + EconomyCommandHelpers.value(fields, row + "to")
                    + "  gross " + EconomyCommandHelpers.value(fields, row + "gross")
                    + "  tax " + EconomyCommandHelpers.value(fields, row + "tax")
                    + "  " + EconomyCommandHelpers.value(fields, row + "reason"));
            index++;
        }
        if (index == 0) {
            EconomyCommandHelpers.send(context, "No entries on this page.");
        }
        return 1;
    }

    static int bankTransfer(final CommandContext<CommandSourceStack> context) {
        if (EconomyCommandHelpers.requirePlayer(context, "/gov bank transfer") == null) {
            return 0;
        }
        final CommandSourceStack source = context.getSource();
        final String from = EconomyCommandHelpers.accountRef(source, EconomyCommandHelpers.arg(context, "from"));
        final String to = EconomyCommandHelpers.accountRef(source, EconomyCommandHelpers.arg(context, "to"));
        if (from == null || to == null) {
            source.sendFailure(Component.literal(EconomyCommands.UNKNOWN_ACCOUNT));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov bank transfer", com.horizonssmp.gov.common.OpCode.BANK_TRANSFER,
                Map.of(com.horizonssmp.gov.common.EconomyKeys.KEY_FROM, from,
                        com.horizonssmp.gov.common.EconomyKeys.KEY_TO, to,
                        com.horizonssmp.gov.common.EconomyKeys.KEY_AMOUNT, EconomyCommandHelpers.arg(context, "amount"),
                        com.horizonssmp.gov.common.EconomyKeys.KEY_REASON, EconomyCommandHelpers.arg(context, "reason"),
                        com.horizonssmp.gov.common.EconomyKeys.KEY_NONCE, EconomyCommandHelpers.nonce()));
    }

    static int bankWithdraw(final CommandContext<CommandSourceStack> context) {
        if (EconomyCommandHelpers.requirePlayer(context, "/gov bank withdraw") == null) {
            return 0;
        }
        final GovTargets.Resolved target = EconomyCommandHelpers.resolveTarget(context, EconomyCommandHelpers.arg(context, "player"));
        if (target == null) {
            return 0;
        }
        return GovCommand.dispatch(context, "/gov bank withdraw", com.horizonssmp.gov.common.OpCode.BANK_WITHDRAW,
                Map.of(com.horizonssmp.gov.server.GovTargets.KEY_TARGET, target.uuid().toString(),
                        com.horizonssmp.gov.common.EconomyKeys.KEY_AMOUNT, EconomyCommandHelpers.arg(context, "amount"),
                        com.horizonssmp.gov.common.EconomyKeys.KEY_NONCE, EconomyCommandHelpers.nonce()));
    }

    static int bankCredit(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        if (!source.hasPermission(EconomyCommands.CREDIT_PERMISSION)) {
            source.sendFailure(Component.literal(
                    "A console credit requires permission level " + EconomyCommands.CREDIT_PERMISSION + "."));
            return 0;
        }
        final ServerPlayer operator = source.getPlayer();
        if (operator == null) {
            source.sendFailure(Component.literal(
                    "The credit remedy requires an in-game operator; the bank service has no"
                            + " console actor."));
            return 0;
        }
        final GovTargets.Resolved target = EconomyCommandHelpers.resolveTarget(context, EconomyCommandHelpers.arg(context, "player"));
        if (target == null) {
            return 0;
        }
        final OptionalLong amount = EconomyMath.parseAmount(EconomyCommandHelpers.arg(context, "amount"));
        if (amount.isEmpty() || amount.getAsLong() <= 0L) {
            source.sendFailure(Component.literal(EconomyCommands.BAD_AMOUNT));
            return 0;
        }
        final EconomyResult result = bank(source).consoleCredit(
                operator, target.uuid(), amount.getAsLong(), EconomyCommandHelpers.arg(context, "reason"));
        if (result.failed()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        EconomyCommandHelpers.send(context, result.message());
        return 1;
    }

    private static BankService bank(final CommandSourceStack source) {
        final GovSavedData data = GovSavedData.get(source.getServer());
        return new BankService(data, new AccountService(data),
                LiveVaultGateway.live(source.getServer()), BankRegions.current());
    }
}
