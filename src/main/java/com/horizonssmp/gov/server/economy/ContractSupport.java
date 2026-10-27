package com.horizonssmp.gov.server.economy;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.ContractState;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.ContractRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;
import com.horizonssmp.gov.server.Sanitizer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/** Shared constants and helpers for the contract lifecycle. Package-private. */
final class ContractSupport {

    static final int MIN_TEXT = 3;
    static final int MAX_DESCRIPTION = 128;
    static final int MAX_REASON = AccountService.MAX_REASON_LENGTH;
    static final long DAY_MILLIS = 86_400_000L;
    static final String ESCROW = "contractEscrow";
    static final String GOVERNMENT = GovSavedData.GOVERNMENT_ACCOUNT_ID;
    static final String TXN_POST = "contract.post";
    static final String TXN_APPROVE = "contract.approve";
    static final String TXN_VOID = "contract.void";
    static final String TXN_EXPIRE = "contract.expire";
    static final String ACTION_EXPIRE = "CONTRACT_EXPIRE";
    static final String SECOND_REVIEWER_REQUIRED =
            "A second government reviewer is required.";
    static final String ACTOR_REQUIRED = "An economy action requires an actor.";
    static final String CONTRACT_REQUIRED = "A contract id is required.";
    static final String CONTRACT_MISSING = "That contract does not exist.";
    static final String BUILDER_OFFLINE = "That builder is not online.";
    static final String DESCRIPTION_INVALID =
            "The description must be 3 to 128 characters.";
    static final String REASON_INVALID = "The reason must be 3 to 128 characters.";
    static final String COMPONENT_INVALID =
            "Materials and labour must each be between 1 and 99 gold ingots.";
    static final String TOTAL_INVALID = "The contract total is too high.";
    static final String BOOK_REQUIRED = "You must hold a book and quill.";
    static final String BUILDER_INVENTORY_FULL =
            "The builder's inventory cannot hold the contract book.";
    static final String BUILDER_REQUIRED =
            "Only holders of the builder job can take contracts.";
    static final String ESCROW_INCONSISTENT = "The contract escrow is inconsistent.";

    private ContractSupport() {
    }

    static boolean enabled() {
        return GovConfig.bankEnabled() && GovConfig.contractEnabled();
    }

    static boolean holdsManage(final GovSavedData data, final ServerPlayer actor) {
        return actor != null && holdsManage(data, data.player(actor.getUUID()));
    }

    static boolean holdsManage(final GovSavedData data, final PlayerRecord record) {
        return GovCapabilities.has(
                GovCapabilities.effective(record, data::job), GovCapability.BANK_MANAGE);
    }

    /**
     * True when the named player may take contracts: they must hold the builder job.
     *
     * <p>TASK-033: this is the single choke point for the builder-only gate. Posting names
     * the builder, accepting and submitting prove the actor is that named builder, and
     * approving re-checks the named builder, so every lifecycle step funnels through here.
     * A government worker, an executive, a citizen, an unemployed player and an unknown
     * UUID are all refused: holding {@code bank.manage} (the poster's authority) never
     * qualifies anyone to build.
     */
    static boolean builderEligible(final GovSavedData data, final java.util.UUID builder) {
        return data != null
                && GovCapabilities.holds(data.player(builder), GovCapabilities.BUILDER_JOB_ID);
    }

    static GovTargets.Resolved independentReviewer(
            final ServerPlayer actor, final GovSavedData data) {
        final String configured = GovConfig.contractReviewer();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        final GovTargets.Resolved reviewer =
                GovTargets.resolve(actor.getServer(), data, configured);
        if (reviewer == null
                || !reviewer.online()
                || !reviewer.uuid().equals(actor.getUUID())) {
            return null;
        }
        return reviewer;
    }

    static int openContractCount(final GovSavedData data) {
        return data.openContractCount();
    }

    static long deadline(final long now) {
        return Math.addExact(
                now, Math.multiplyExact((long) GovConfig.contractAcceptDays(), DAY_MILLIS));
    }

    static boolean isDue(final ContractRecord contract, final long now) {
        long expiresAt = contract.expiresAt();
        if (expiresAt <= 0L) {
            final long base = contract.state() == ContractState.POSTED
                    ? contract.createdAt() : contract.acceptedAt();
            if (base <= 0L) {
                return false;
            }
            try {
                expiresAt = Math.addExact(
                        base,
                        Math.multiplyExact(
                                (long) GovConfig.contractAcceptDays(), DAY_MILLIS));
            } catch (final ArithmeticException overflow) {
                expiresAt = Long.MAX_VALUE;
            }
        }
        return now >= expiresAt;
    }

    static ContractRecord copy(
            final ContractRecord base,
            final ContractState state,
            final long acceptedAt,
            final long submittedAt,
            final long approvedAt,
            final long expiresAt) {
        return new ContractRecord(
                base.id(),
                base.poster(),
                base.builder(),
                base.materials(),
                base.labor(),
                base.total(),
                base.description(),
                state,
                base.createdAt(),
                acceptedAt,
                submittedAt,
                approvedAt,
                expiresAt,
                base.bookTitle());
    }

    static AccountRecord withBalance(
            final AccountRecord base, final long balance, final long lastSeq) {
        return new AccountRecord(
                base.id(),
                base.type(),
                base.owner(),
                base.business(),
                balance,
                base.status(),
                base.createdAt(),
                lastSeq);
    }

    static EconomyMeta withMeta(
            final EconomyMeta base,
            final long contractEscrow,
            final long nextLedgerSeq,
            final long nextContractSeq) {
        return new EconomyMeta(
                base.totalDeposited(),
                base.totalWithdrawn(),
                contractEscrow,
                nextLedgerSeq,
                nextContractSeq,
                base.nonces(),
                base.migrations());
    }

    static GovTargets.Resolved target(final GovSavedData data, final java.util.UUID uuid) {
        final PlayerRecord record = data.player(uuid);
        return new GovTargets.Resolved(
                uuid, record == null ? "" : record.lastName(), null, record);
    }

    static int writableBookSlot(final Inventory inventory) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (isWritableBook(inventory.getItem(slot))) {
                return slot;
            }
        }
        return isWritableBook(inventory.getItem(Inventory.SLOT_OFFHAND))
                ? Inventory.SLOT_OFFHAND : -1;
    }

    static boolean isWritableBook(final ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.WRITABLE_BOOK);
    }

    static boolean hasFreeSlot(final ServerPlayer player) {
        final Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return true;
            }
        }
        return inventory.getItem(Inventory.SLOT_OFFHAND).isEmpty();
    }

    static boolean give(final ServerPlayer player, final ItemStack book) {
        final Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, book);
                inventory.setChanged();
                return true;
            }
        }
        if (inventory.getItem(Inventory.SLOT_OFFHAND).isEmpty()) {
            inventory.setItem(Inventory.SLOT_OFFHAND, book);
            inventory.setChanged();
            return true;
        }
        return false;
    }

    static ItemStack contractBook(
            final String id,
            final String title,
            final String posterName,
            final String builderName,
            final long materials,
            final long labor,
            final long total,
            final String description,
            final long deadline) {
        final List<Filterable<Component>> pages = new ArrayList<>(2);
        pages.add(Filterable.passThrough(Component.literal(
                "BUILDING CONTRACT\n"
                        + "Contract " + id + "\n"
                        + "Posted by " + posterName + "\n"
                        + "Builder " + builderName + "\n"
                        + "Materials " + materials + " gold\n"
                        + "Labour " + labor + " gold\n"
                        + "Total " + total + " gold\n"
                        + "Accept by "
                        + DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(deadline))
                        + "\n"
                        + description)));
        pages.add(Filterable.passThrough(Component.literal(
                "To accept:\n/gov contract accept " + id + "\n\n"
                        + "To submit:\n/gov contract submit " + id + "\n\n"
                        + "To void:\n/gov contract void " + id + " <reason>")));
        final WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(title),
                "Horizons SMP",
                0,
                pages,
                true);
        final ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }
}