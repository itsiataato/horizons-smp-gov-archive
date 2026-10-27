package com.horizonssmp.gov.command;

import java.util.Map;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.server.GovTargets;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers the economy chat surface.
 *
 * <p>The two top-level commands are {@code /deposit} and {@code /pay}. Everything else lives under
 * the existing {@code /gov} root, which {@link #attach(LiteralArgumentBuilder)} adds to before
 * {@code GovCommand} registers it: {@code /gov bank}, {@code /gov payroll}, {@code /gov contract}
 * and {@code /gov store}. There is no second {@code /gov} literal and no GUI here; the store
 * command prints the first catalogue page in chat.
 *
 * <p>The command layer only resolves names. A {@code player:<name>} account reference becomes
 * {@code player:<uuid>}, a person target becomes a server-confirmed UUID, and every amount is
 * passed as the raw word the player typed. The service behind the dispatcher parses the amount,
 * applies its own limits and refuses with its own neutral sentence. A fresh nonce is generated for
 * every invocation so a retried command is never mistaken for a replay of the previous one.
 *
 * <p>The one exception is {@code /gov bank credit}: it is the audited crash remedy, not ordinary
 * dispatcher traffic. It checks permission level four and calls {@link BankService#consoleCredit}
 * directly, exactly as the specification requires.
 */
public final class EconomyCommands {

    /** Contract payload key for the materials budget. */
    static final String KEY_MATERIALS = "materials";

    /** Contract payload key for the labour fee. */
    static final String KEY_LABOR = "labor";

    /** Contract payload key for the written order. */
    static final String KEY_DESCRIPTION = "description";

    /** Ledger rows are encoded as {@code row.<n>.<field>}. */
    static final String LEDGER_ROW = "row.";

    /** Contract rows are encoded as {@code contract.<n>.<field>}. */
    static final String CONTRACT_ROW = "contract.";

    /** Store rows are encoded as {@code store.item.<n>.<field>}. */
    static final String STORE_ROW = "store.item.";

    /** The permission level the console credit remedy requires. */
    static final int CREDIT_PERMISSION = 4;

    /** The shared refusal for an amount that is not a whole number. */
    static final String BAD_AMOUNT = "Enter a whole amount in gold ingots.";

    /** The shared refusal for an account reference that names no known player. */
    static final String UNKNOWN_ACCOUNT =
            "That account reference does not name a known player.";

    static final String BANK_USAGE = "Usage: /gov bank balance [account]"
            + " | history [account] [page]"
            + " | transfer <from> <to> <amount> <reason...>"
            + " | withdraw <player> <amount>"
            + " | credit <player> <amount> <reason...>";

    static final String PAYROLL_USAGE = "Usage: /gov payroll run <scope> [reason...]";

    static final String CONTRACT_USAGE = "Usage: /gov contract post <builder> <materials>"
            + " <labor> <description...> | list [page] | accept <id> | submit <id>"
            + " | approve <id> | void <id> <reason...>";

    private EconomyCommands() {
    }

    /**
     * Registers the two top-level economy commands.
     *
     * @param event the command-registration event
     */
    public static void register(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("deposit")
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(EconomyCommands::deposit)));
        event.getDispatcher().register(Commands.literal("pay")
                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(EconomyCommands::pay))));
    }

    /**
     * Registers the whole economy surface for the {@code /gov} tree.
     *
     * <p>This is the single call {@code GovCommand} makes: it registers the two top-level
     * commands and adds the economy nodes to the {@code /gov} builder before that builder is
     * handed to the dispatcher.
     *
     * @param event the command-registration event
     * @param root the {@code /gov} builder owned by {@code GovCommand}
     */
    static void register(
            final RegisterCommandsEvent event,
            final LiteralArgumentBuilder<CommandSourceStack> root) {
        register(event);
        attach(root);
    }

    /**
     * Adds the economy subcommands to the existing {@code /gov} builder.
     *
     * <p>Called before the builder is registered, so the dispatcher builds one tree containing
     * both the government commands and these nodes.
     *
     * @param root the {@code /gov} builder owned by {@code GovCommand}
     */
    static void attach(final LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("bank")
                .executes(context -> EconomyCommandHelpers.usage(context, BANK_USAGE))
                .then(Commands.literal("balance")
                        .executes(context -> bankBalance(context, "me"))
                        .then(Commands.argument("account", StringArgumentType.word())
                                .executes(context -> bankBalance(context,
                                        EconomyCommandHelpers.arg(context, "account")))))
                .then(Commands.literal("history")
                        .executes(context -> bankHistory(context, "me", "0"))
                        .then(Commands.argument("account", StringArgumentType.word())
                                .executes(context -> bankHistory(context,
                                        EconomyCommandHelpers.arg(context, "account"), "0"))
                                .then(Commands.argument("page", StringArgumentType.word())
                                        .executes(context -> bankHistory(context,
                                                EconomyCommandHelpers.arg(context, "account"), EconomyCommandHelpers.arg(context, "page"))))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("from", StringArgumentType.word())
                                .then(Commands.argument("to", StringArgumentType.word())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .then(Commands.argument("reason",
                                                                StringArgumentType.greedyString())
                                                        .executes(EconomyCommands::bankTransfer))))))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(EconomyCommands::bankWithdraw))))
                .then(Commands.literal("credit")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .then(Commands.argument("reason",
                                                        StringArgumentType.greedyString())
                                                .executes(EconomyCommands::bankCredit))))));

        root.then(Commands.literal("payroll")
                .executes(context -> EconomyCommandHelpers.usage(context, PAYROLL_USAGE))
                .then(Commands.literal("run")
                        .then(Commands.argument("scope", StringArgumentType.word()).suggests(GovSuggestions.SCOPES)
                                .executes(context -> payrollRun(context, ""))
                                .then(Commands.argument("reason",
                                                StringArgumentType.greedyString())
                                        .executes(context -> payrollRun(context,
                                                EconomyCommandHelpers.arg(context, "reason")))))));

        root.then(Commands.literal("contract")
                .executes(context -> EconomyCommandHelpers.usage(context, CONTRACT_USAGE))
                .then(Commands.literal("post")
                        .then(Commands.argument("builder", StringArgumentType.word())
                                .then(Commands.argument("materials", StringArgumentType.word())
                                        .then(Commands.argument("labor",
                                                        StringArgumentType.word())
                                                .then(Commands.argument("description",
                                                                StringArgumentType.greedyString())
                                                        .executes(context ->
                                                                contractPost(context)))))))
                .then(Commands.literal("list")
                        .executes(context -> contractList(context, "0"))
                        .then(Commands.argument("page", StringArgumentType.word())
                                .executes(context -> contractList(context,
                                        EconomyCommandHelpers.arg(context, "page")))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> contractMove(context, "accept",
                                        OpCode.CONTRACT_ACCEPT))))
                .then(Commands.literal("submit")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> contractMove(context, "submit",
                                        OpCode.CONTRACT_SUBMIT))))
                .then(Commands.literal("approve")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> contractMove(context, "approve",
                                        OpCode.CONTRACT_APPROVE))))
                .then(Commands.literal("void")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("reason",
                                                StringArgumentType.greedyString())
                                        .executes(EconomyContractCommands::contractVoid)))));

        root.then(Commands.literal("store").executes(EconomyStoreCommands::store));
    }

    // ----------------------------------------------------------- top-level commands

    /** {@code /deposit <amount>}: physical gold from the actor's inventory into their account. */
    private static int deposit(final CommandContext<CommandSourceStack> context) {
        if (EconomyCommandHelpers.requirePlayer(context, "/deposit") == null) {
            return 0;
        }
        return GovCommand.dispatch(context, "/deposit", OpCode.BANK_DEPOSIT,
                Map.of(EconomyKeys.KEY_AMOUNT, EconomyCommandHelpers.arg(context, "amount"),
                        EconomyKeys.KEY_NONCE, EconomyCommandHelpers.nonce()));
    }

    /** {@code /pay <player> <amount>}: the actor pays another online player. */
    private static int pay(final CommandContext<CommandSourceStack> context) {
        if (EconomyCommandHelpers.requirePlayer(context, "/pay") == null) {
            return 0;
        }
        final GovTargets.Resolved target = EconomyCommandHelpers.resolveTarget(context, EconomyCommandHelpers.arg(context, "player"));
        if (target == null) {
            return 0;
        }
        return GovCommand.dispatch(context, "/pay", OpCode.PAY_PLAYER,
                Map.of(GovTargets.KEY_TARGET, target.uuid().toString(),
                        EconomyKeys.KEY_AMOUNT, EconomyCommandHelpers.arg(context, "amount"),
                        EconomyKeys.KEY_NONCE, EconomyCommandHelpers.nonce()));
    }

    // ---------------------------------------------------------------- /gov bank

    /** {@code /gov bank balance [account]}: one account's stored balance. */
    private static int bankBalance(
            final CommandContext<CommandSourceStack> context, final String rawAccount) {
        return EconomyBankCommands.bankBalance(context, rawAccount);
    }

    /** {@code /gov bank history [account] [page]}: one page of one account's ledger. */
    private static int bankHistory(
            final CommandContext<CommandSourceStack> context,
            final String rawAccount,
            final String rawPage) {
        return EconomyBankCommands.bankHistory(context, rawAccount, rawPage);
    }

    /** {@code /gov bank transfer <from> <to> <amount> <reason...>}. */
    private static int bankTransfer(final CommandContext<CommandSourceStack> context) {
        return EconomyBankCommands.bankTransfer(context);
    }

    /** {@code /gov bank withdraw <player> <amount>}: a teller pays an account holder. */
    private static int bankWithdraw(final CommandContext<CommandSourceStack> context) {
        return EconomyBankCommands.bankWithdraw(context);
    }

    /**
     * {@code /gov bank credit <player> <amount> <reason...>}: the audited crash remedy.
     *
     * <p>This path is deliberately not a dispatcher action. It checks permission level four and
     * calls the service that writes the system audit line. The service needs a player command
     * source for that permission check, so a pure server console cannot run it.
     */
    private static int bankCredit(final CommandContext<CommandSourceStack> context) {
        return EconomyBankCommands.bankCredit(context);
    }

    // ------------------------------------------------------------- /gov payroll

    /** {@code /gov payroll run <scope> [reason...]}. */
    private static int payrollRun(
            final CommandContext<CommandSourceStack> context, final String reason) {
        return EconomyPayrollCommands.payrollRun(context, reason);
    }

    // ------------------------------------------------------------ /gov contract

    /** {@code /gov contract post <builder> <materials> <labor> <description...>}. */
    private static int contractPost(final CommandContext<CommandSourceStack> context) {
        return EconomyContractCommands.contractPost(context);
    }

    /** {@code /gov contract list [page]}: one page of the contracts this actor may see. */
    private static int contractList(
            final CommandContext<CommandSourceStack> context, final String rawPage) {
        return EconomyContractCommands.contractList(context, rawPage);
    }

    /** {@code /gov contract accept|submit|approve <id>}. */
    private static int contractMove(
            final CommandContext<CommandSourceStack> context,
            final String verb,
            final OpCode op) {
        return EconomyContractCommands.contractMove(context, verb, op);
    }

    // --------------------------------------------------------------- /gov store

}
