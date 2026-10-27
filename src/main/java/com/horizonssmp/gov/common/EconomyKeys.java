package com.horizonssmp.gov.common;

/**
 * The stable keys of the economy payloads, responses and {@code gov.dat} root sections.
 *
 * <p>These strings are the wire contract: the server writes them into a {@link GovFields} block or
 * a command result and the client reads them. They live in the common package so the client never
 * imports a server class to learn a key, and so a key is renamed in exactly one place.
 *
 * <p>The {@code ROOT_*} constants are the root keys of the economy sections in {@code gov.dat}.
 * They are added to {@code GovSavedData.KNOWN_ROOT_KEYS} by the data layer; this class does not
 * own that set.
 */
public final class EconomyKeys {

    // ------------------------------------------------------- request payloads

    /** Payload/response: a stable account id, never a display name. */
    public static final String KEY_ACCOUNT = "account";
    /** Payload/response: a whole number of gold ingots. */
    public static final String KEY_AMOUNT = "amount";
    /** Payload: the client or command nonce that makes a mutation replay-safe. */
    public static final String KEY_NONCE = "nonce";
    /** Payload/response: a stated reason, already sanitized by the service. */
    public static final String KEY_REASON = "reason";
    /** Payload/response: the zero-based page of a paged read. */
    public static final String KEY_PAGE = "page";
    /** Payload/response: a payroll scope: {@code gov}, {@code police} or {@code business:<id>}. */
    public static final String KEY_SCOPE = "scope";
    /** Payload/response: the source side of a transfer or ledger row. */
    public static final String KEY_FROM = "from";
    /** Payload/response: the destination side of a transfer or ledger row. */
    public static final String KEY_TO = "to";
    /** Payload/response: a contract id. */
    public static final String KEY_CONTRACT = "contract";
    /** Payload/response: a store catalogue item id. */
    public static final String KEY_ITEM = "item";
    /** Payload: the number of whole stacks a store purchase asks for. */
    public static final String KEY_STACKS = "stacks";

    // ------------------------------------------------------ response fields

    /** Response: an account balance in gold ingots. */
    public static final String KEY_BALANCE = "balance";
    /** Response: an account or contract status name. */
    public static final String KEY_STATUS = "status";
    /** Response: the tax share of a gross amount. */
    public static final String KEY_TAX = "tax";
    /** Response: the net amount after tax. */
    public static final String KEY_NET = "net";
    /** Response: a list of item rows. */
    public static final String KEY_ITEMS = "items";
    /** Response: a store row price in gold ingots. */
    public static final String KEY_PRICE = "price";
    /** Response: a store row tier, {@code 1..4}. */
    public static final String KEY_TIER = "tier";
    /** Response: one store row's stack size. */
    public static final String KEY_STACK = "stack";
    /** Response: whether the actor can afford the selected store row. */
    public static final String KEY_CAN_BUY = "canBuy";
    /**
     * Response: whether the actor may use the console credit remedy ({@code BANK_CREDIT}).
     *
     * <p>An affordance hint only, mirroring the Audit tab's {@code canAdmin}: the client gates
     * the credit button on {@code "true".equals(fields.get(KEY_CAN_CREDIT))}. Enforcement stays
     * server-side in the {@code BANK_CREDIT} dispatcher gate plus the handler's live check.
     */
    public static final String KEY_CAN_CREDIT = "canCredit";
    /** Response: a contract state name. */
    public static final String KEY_STATE = "state";
    /** Response: the machine-readable error code. */
    public static final String KEY_ERROR = "error";
    /** Response: the neutral one-line message shown to the player. */
    public static final String KEY_MESSAGE = "message";

    // --------------------------------------------------- gov.dat root keys

    /** Root section: account id to {@code AccountRecord}. */
    public static final String ROOT_ACCOUNTS = "accounts";
    /** Root section: the append-only economy ledger. */
    public static final String ROOT_LEDGER = "ledger";
    /** Root section: physical gold movement rows awaiting or recording delivery. */
    public static final String ROOT_VAULT_TXNS = "vaultTxns";
    /** Root section: contract id to {@code ContractRecord}. */
    public static final String ROOT_CONTRACTS = "contracts";
    /** Root section: the payroll run history. */
    public static final String ROOT_PAYROLL_RUNS = "payrollRuns";
    /** Root section: the singleton {@code EconomyMeta} block. */
    public static final String ROOT_ECONOMY_META = "economyMeta";

    private EconomyKeys() {
    }
}
