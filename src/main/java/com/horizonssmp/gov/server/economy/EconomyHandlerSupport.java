package com.horizonssmp.gov.server.economy;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.server.GovActionDispatcher;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared state and helpers for the economy handlers.
 *
 * <p>Keeps the cached {@link AccountService}/{@link StoreCatalog} and the parsing/resolution
 * helpers that every economy handler uses, so the per-domain handler classes stay small.
 */
public final class EconomyHandlerSupport {

    /** The refusal for a malformed amount, shared by every amount-bearing action. */
    public static final String BAD_AMOUNT = "Enter a whole amount in gold ingots.";

    /** The refusal for a malformed store stack count. */
    public static final String BAD_STACKS = "Enter a whole number of stacks.";

    /** The contract post payload key for the materials budget. */
    public static final String KEY_MATERIALS = "materials";

    /** The contract post payload key for the labour fee. */
    public static final String KEY_LABOR = "labor";

    /** The contract post payload key for the written order. */
    public static final String KEY_DESCRIPTION = "description";

    /** The permission level a console credit requires. */
    public static final int CONSOLE_PERMISSION = 4;

    private static volatile StoreCatalog cachedCatalog;
    private static volatile MinecraftServer cachedCatalogServer;
    private static AccountService cachedAccounts;
    private static GovSavedData cachedAccountsData;

    private EconomyHandlerSupport() {
    }

    public static BankService bank(final GovActionDispatcher.ActionContext context) {
        final GovSavedData data = context.data();
        return new BankService(
                data,
                accounts(data),
                LiveVaultGateway.live(context.actor().getServer()),
                BankRegions.current());
    }

    public static StoreService store(final GovActionDispatcher.ActionContext context) {
        final GovSavedData data = context.data();
        return new StoreService(
                data,
                accounts(data),
                catalog(context.actor().getServer()),
                new LiveItemDelivery());
    }

    public static ContractService contracts(final GovActionDispatcher.ActionContext context) {
        final GovSavedData data = context.data();
        return new ContractService(data, accounts(data));
    }

    public static PayrollService payroll(final GovActionDispatcher.ActionContext context) {
        final GovSavedData data = context.data();
        return new PayrollService(data, accounts(data));
    }

    /**
     * The shared {@link AccountService} for one saved data set.
     */
    public static synchronized AccountService accounts(final GovSavedData data) {
        if (cachedAccounts == null || cachedAccountsData != data) {
            cachedAccounts = new AccountService(data);
            cachedAccountsData = data;
        }
        return cachedAccounts;
    }

    /** The store catalogue for this server, loaded once because loading scans every recipe. */
    public static StoreCatalog catalog(final MinecraftServer server) {
        StoreCatalog loaded = cachedCatalog;
        if (loaded != null && cachedCatalogServer == server) {
            return loaded;
        }
        synchronized (EconomyHandlerSupport.class) {
            loaded = cachedCatalog;
            if (loaded == null || cachedCatalogServer != server) {
                loaded = StoreCatalog.load(server);
                cachedCatalog = loaded;
                cachedCatalogServer = server;
            }
            return loaded;
        }
    }

    /**
     * Resolves an account reference from one payload key, defaulting to {@code me}.
     */
    public static String accountRef(
            final GovActionDispatcher.ActionContext context, final String key) {
        final String raw = context.payload(key).strip();
        return resolveAccountRef(context, raw.isEmpty() ? "me" : raw);
    }

    private static String resolveAccountRef(
            final GovActionDispatcher.ActionContext context, final String raw) {
        final String value = raw.strip();
        final String lower = value.toLowerCase(Locale.ROOT);
        if ("me".equals(lower)) {
            return AccountService.PLAYER_PREFIX + context.actor().getUUID();
        }
        if ("gov".equals(lower) || "government".equals(lower)) {
            return GovSavedData.GOVERNMENT_ACCOUNT_ID;
        }
        if ("police".equals(lower)) {
            return GovSavedData.POLICE_ACCOUNT_ID;
        }
        if (lower.startsWith(AccountService.BUSINESS_PREFIX)) {
            final String id =
                    value.substring(AccountService.BUSINESS_PREFIX.length()).strip();
            return id.isEmpty() ? null : AccountService.BUSINESS_PREFIX + id;
        }
        final String name = lower.startsWith(AccountService.PLAYER_PREFIX)
                ? value.substring(AccountService.PLAYER_PREFIX.length()).strip()
                : value;
        if (name.isEmpty()) {
            return null;
        }
        final GovTargets.Resolved target = GovTargets.resolve(
                context.actor().getServer(), context.data(), name);
        return target == null ? null : AccountService.PLAYER_PREFIX + target.uuid();
    }

    public static OptionalLong amount(
            final GovActionDispatcher.ActionContext context, final String key) {
        return EconomyMath.parseAmount(context.payload(key).strip());
    }

    public static String nonce(final GovActionDispatcher.ActionContext context) {
        return context.payload(EconomyKeys.KEY_NONCE);
    }

    /**
     * Consumes the request nonce before a mutating handler does anything else.
     */
    public static ActionResult consumeNonce(
            final GovActionDispatcher.ActionContext context) {
        final EconomyResult result =
                accounts(context.data()).acceptNonce(context.actor().getUUID(), nonce(context));
        return result.failed() ? write(context, result) : null;
    }

    /** A requested page clamped at zero; the service clamps it to its own last page. */
    public static int page(final GovActionDispatcher.ActionContext context) {
        final String raw = context.payload(EconomyKeys.KEY_PAGE).strip();
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (final NumberFormatException invalid) {
            return 0;
        }
    }

    public static OptionalInt whole(final String raw) {
        final String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (final NumberFormatException invalid) {
            return OptionalInt.empty();
        }
    }

    public static String firstNonBlank(final String... values) {
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    public static boolean hasConsolePermission(final ServerPlayer actor) {
        final CommandSourceStack source = actor.createCommandSourceStack();
        return source != null && source.hasPermission(CONSOLE_PERMISSION);
    }

    public static ActionResult read(
            final GovActionDispatcher.ActionContext context, final EconomyResult result) {
        if (result.failed()) {
            return ActionResult.failure(
                    context.request().reqId(), result.error(), result.message());
        }
        return ActionResult.success(
                context.request().reqId(), GovFields.encode(result.fields()));
    }

    public static ActionResult write(
            final GovActionDispatcher.ActionContext context, final EconomyResult result) {
        if (result.failed()) {
            return ActionResult.failure(
                    context.request().reqId(), result.error(), result.message());
        }
        return ActionResult.success(context.request().reqId(), result.message());
    }

    public static ActionResult badTarget(final GovActionDispatcher.ActionContext context) {
        return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
    }

    public static ActionResult badAmount(final GovActionDispatcher.ActionContext context) {
        return ActionResult.failure(
                context.request().reqId(), ErrorCode.BAD_PAYLOAD, BAD_AMOUNT);
    }
}