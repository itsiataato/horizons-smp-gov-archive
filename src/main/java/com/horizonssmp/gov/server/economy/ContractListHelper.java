package com.horizonssmp.gov.server.economy;

import java.util.ArrayList;
import java.util.List;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.ContractRecord;
import com.horizonssmp.gov.server.GovSavedData;

import net.minecraft.server.level.ServerPlayer;

final class ContractListHelper {

    private final GovSavedData data;
    private final AccountService accounts;

    ContractListHelper(final GovSavedData data, final AccountService accounts) {
        this.data = data;
        this.accounts = accounts;
    }

    EconomyResult list(final ServerPlayer actor, final int page) {
        if (!ContractSupport.enabled()) {
            return EconomyResult.failure(ErrorCode.ECONOMY_DISABLED);
        }
        if (actor == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ContractSupport.ACTOR_REQUIRED);
        }
        final boolean manage = ContractSupport.holdsManage(data, actor);
        final java.util.UUID viewer = actor.getUUID();
        final List<ContractRecord> visible = new ArrayList<>();
        if (manage) {
            for (final String contractId : data.contractIdOrder()) {
                final ContractRecord contract = data.contract(contractId);
                if (contract != null) {
                    visible.add(contract);
                }
            }
        } else {
            for (final String contractId : data.contractIdsForParty(viewer)) {
                final ContractRecord contract = data.contract(contractId);
                if (contract != null) {
                    visible.add(contract);
                }
            }
        }

        final int total = visible.size();
        final int pages = Math.max(1, (total + ContractService.PAGE_SIZE - 1) / ContractService.PAGE_SIZE);
        final int served = Math.max(0, Math.min(page, pages - 1));
        EconomyResult result = EconomyResult.success()
                .with("contract.page", Integer.toString(served))
                .with("contract.pages", Integer.toString(pages))
                .with("contract.total", Integer.toString(total));

        final int start = Math.min(total, served * ContractService.PAGE_SIZE);
        final int end = Math.min(total, start + ContractService.PAGE_SIZE);
        for (int index = 0; index < end - start; index++) {
            final ContractRecord contract = visible.get(start + index);
            final String prefix = "contract." + index + ".";
            result = result
                    .with(prefix + "id", contract.id())
                    .with(prefix + "poster", contract.poster().toString())
                    .with(prefix + "builder", contract.builder().toString())
                    .with(prefix + "materials", Long.toString(contract.materials()))
                    .with(prefix + "labor", Long.toString(contract.labor()))
                    .with(prefix + "total", Long.toString(contract.total()))
                    .with(prefix + "state", contract.state().name())
                    .with(prefix + "description", contract.description())
                    .with(prefix + "expiresAt", Long.toString(contract.expiresAt()));
        }
        return result;
    }
}