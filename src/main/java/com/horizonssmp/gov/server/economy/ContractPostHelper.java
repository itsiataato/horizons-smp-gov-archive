package com.horizonssmp.gov.server.economy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.enums.ContractState;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.ContractRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;
import com.horizonssmp.gov.server.Sanitizer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

final class ContractPostHelper {

    private final GovSavedData data;
    private final AccountService accounts;

    ContractPostHelper(final GovSavedData data, final AccountService accounts) {
        this.data = data;
        this.accounts = accounts;
    }

    EconomyResult post(
            final ServerPlayer poster,
            final UUID builder,
            final long materials,
            final long labor,
            final String description,
            final long now) {
        if (!ContractSupport.enabled()) {
            return EconomyResult.failure(ErrorCode.ECONOMY_DISABLED);
        }
        if (poster == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.ACTOR_REQUIRED);
        }
        if (now < 0L) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (!ContractSupport.holdsManage(data, poster)) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }
        if (!BankRegions.current().isBank(poster)) {
            return EconomyResult.failure(ErrorCode.REGION_REQUIRED);
        }
        if (builder == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.BUILDER_OFFLINE);
        }
        if (poster.getUUID().equals(builder)) {
            return EconomyResult.failure(
                    ErrorCode.CONTRACT_LIMIT, "You cannot post a contract to yourself.");
        }

        final MinecraftServer server = poster.getServer();
        final GovTargets.Resolved target =
                GovTargets.resolve(server, data, builder.toString());
        if (target == null || !target.online()) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET, ContractSupport.BUILDER_OFFLINE);
        }
        final ServerPlayer builderPlayer = target.player();
        if (!ContractSupport.builderEligible(data, builder)) {
            return EconomyResult.failure(ErrorCode.CONTRACT_LIMIT, ContractSupport.BUILDER_REQUIRED);
        }
        if (data.hasOpenContract(builder)) {
            return EconomyResult.failure(
                    ErrorCode.CONTRACT_LIMIT,
                    "That builder already has an open contract.");
        }
        if (ContractSupport.openContractCount(data) >= GovConfig.contractMaxConcurrent()) {
            return EconomyResult.failure(
                    ErrorCode.CONTRACT_LIMIT,
                    "The server has reached its open contract limit.");
        }
        if (materials < ContractRecord.MIN_COMPONENT
                || materials > ContractRecord.MAX_COMPONENT
                || labor < ContractRecord.MIN_COMPONENT
                || labor > ContractRecord.MAX_COMPONENT) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.COMPONENT_INVALID);
        }
        final long total = materials + labor;
        if (total > ContractRecord.MAX_TOTAL || total > GovConfig.contractMaxTotal()) {
            return EconomyResult.failure(ErrorCode.CONTRACT_LIMIT, ContractSupport.TOTAL_INVALID);
        }

        final String cleanDescription =
                Sanitizer.singleLine(description, Sanitizer.MAX_TEXT);
        if (cleanDescription.length() < ContractSupport.MIN_TEXT
                || cleanDescription.length() > ContractSupport.MAX_DESCRIPTION) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.DESCRIPTION_INVALID);
        }

        final Inventory posterInventory = poster.getInventory();
        final int writableSlot = ContractSupport.writableBookSlot(posterInventory);
        if (writableSlot < 0) {
            return EconomyResult.failure(ErrorCode.INSUFFICIENT_ITEMS, ContractSupport.BOOK_REQUIRED);
        }
        if (!ContractSupport.hasFreeSlot(builderPlayer)) {
            return EconomyResult.failure(ErrorCode.INVENTORY_FULL, ContractSupport.BUILDER_INVENTORY_FULL);
        }

        final EconomyMeta meta = data.economyMeta();
        final long contractSeq = meta.nextContractSeq();
        if (contractSeq == Long.MAX_VALUE) {
            throw new IllegalStateException("contract sequence space exhausted");
        }
        final String id = "c" + contractSeq;
        if (data.contract(id) != null) {
            return EconomyResult.failure(
                    ErrorCode.INVALID_STATE, "That contract id is already in use.");
        }
        final AccountRecord government = accounts.government();
        if (!government.acceptsDebit()) {
            return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
        }
        if (government.balance() < total) {
            return EconomyResult.failure(
                    ErrorCode.INSUFFICIENT_FUNDS,
                    "The government cannot cover that contract.");
        }

        final long deadline;
        try {
            deadline = ContractSupport.deadline(now);
        } catch (final ArithmeticException overflow) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }

        final String title = "Contract " + id;
        final String posterName = Sanitizer.name(poster.getName().getString());
        final ItemStack book = ContractSupport.contractBook(
                id,
                title,
                posterName,
                target.name(),
                materials,
                labor,
                total,
                cleanDescription,
                deadline);

        final ItemStack writable = posterInventory.getItem(writableSlot);
        writable.shrink(1);
        posterInventory.setChanged();
        if (!ContractSupport.give(builderPlayer, book)) {
            writable.grow(1);
            posterInventory.setChanged();
            return EconomyResult.failure(ErrorCode.INVENTORY_FULL, ContractSupport.BUILDER_INVENTORY_FULL);
        }

        final long ledgerSeq = accounts.nextSeq();
        final long newGovernmentBalance;
        try {
            newGovernmentBalance = Math.subtractExact(government.balance(), total);
        } catch (final ArithmeticException overflow) {
            throw new IllegalStateException("government balance underflow", overflow);
        }
        final AccountRecord updatedGovernment =
                ContractSupport.withBalance(government, newGovernmentBalance, ledgerSeq);
        final long newEscrow;
        try {
            newEscrow = Math.addExact(meta.contractEscrow(), total);
        } catch (final ArithmeticException overflow) {
            throw new IllegalStateException("contract escrow overflow", overflow);
        }
        final EconomyMeta updatedMeta = ContractSupport.withMeta(
                meta, newEscrow, ledgerSeq + 1L, contractSeq + 1L);
        final ContractRecord contract = new ContractRecord(
                id,
                poster.getUUID(),
                builder,
                materials,
                labor,
                total,
                cleanDescription,
                ContractState.POSTED,
                now,
                0L,
                0L,
                0L,
                deadline,
                title);
        final LedgerEntry entry = new LedgerEntry(
                ledgerSeq,
                now,
                ContractSupport.TXN_POST,
                com.horizonssmp.gov.common.enums.LedgerKind.CONTRACT,
                ContractSupport.GOVERNMENT,
                ContractSupport.ESCROW,
                total,
                0L,
                poster.getUUID(),
                "contract " + id + " posted");

        data.putAccount(updatedGovernment);
        data.setEconomyMeta(updatedMeta);
        data.putContract(contract);
        data.appendLedger(entry);
        data.markDirty();

        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(EconomyKeys.KEY_CONTRACT, id);
        payload.put("materials", Long.toString(materials));
        payload.put("labor", Long.toString(labor));
        payload.put("total", Long.toString(total));
        payload.put("description", cleanDescription);
        AuditService.append(
                data,
                poster,
                OpCode.CONTRACT_POST.name(),
                target,
                payload,
                cleanDescription);

        return EconomyResult.success("Contract posted.")
                .with(EconomyKeys.KEY_CONTRACT, id)
                .with(EconomyKeys.KEY_STATE, ContractState.POSTED.name())
                .with(EconomyKeys.KEY_AMOUNT, Long.toString(total));
    }
}