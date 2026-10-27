package com.horizonssmp.gov.server.economy;

import java.util.UUID;

import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.enums.ContractState;
import com.horizonssmp.gov.server.GovSavedData;

import net.minecraft.server.level.ServerPlayer;

/**
 * The Government building-contract lifecycle.
 *
 * <p>Facade over package-private helper classes. Public API preserved.
 */
public final class ContractService {

    /** Number of contract rows returned on one list page. */
    public static final int PAGE_SIZE = 20;

    private final GovSavedData data;
    private final AccountService accounts;
    private final ContractPostHelper postHelper;
    private final ContractAcceptHelper acceptHelper;
    private final ContractApproveHelper approveHelper;
    private final ContractVoidHelper voidHelper;
    private final ContractExpireHelper expireHelper;
    private final ContractListHelper listHelper;

    public ContractService(final GovSavedData data, final AccountService accounts) {
        if (data == null) {
            throw new IllegalArgumentException("ContractService requires a GovSavedData");
        }
        if (accounts == null) {
            throw new IllegalArgumentException("ContractService requires an AccountService");
        }
        this.data = data;
        this.accounts = accounts;
        this.postHelper = new ContractPostHelper(data, accounts);
        this.acceptHelper = new ContractAcceptHelper(data, accounts);
        this.approveHelper = new ContractApproveHelper(data, accounts);
        this.voidHelper = new ContractVoidHelper(data, accounts);
        this.expireHelper = new ContractExpireHelper(data, accounts);
        this.listHelper = new ContractListHelper(data, accounts);
    }

    public EconomyResult post(
            final ServerPlayer poster,
            final UUID builder,
            final long materials,
            final long labor,
            final String description,
            final long now) {
        return postHelper.post(poster, builder, materials, labor, description, now);
    }

    public EconomyResult accept(
            final ServerPlayer builder, final String id, final long now) {
        return acceptHelper.accept(builder, id, now);
    }

    public EconomyResult submit(
            final ServerPlayer builder, final String id, final long now) {
        return acceptHelper.submit(builder, id, now);
    }

    public EconomyResult approve(
            final ServerPlayer actor, final String id, final long now) {
        return approveHelper.approve(actor, id, now);
    }

    public EconomyResult voidContract(
            final ServerPlayer actor, final String id, final String reason, final long now) {
        return voidHelper.voidContract(actor, id, reason, now);
    }

    public EconomyResult expire(final String id, final long now) {
        return expireHelper.expire(id, now);
    }

    public EconomyResult expireDue(final long now) {
        return expireHelper.expireDue(now);
    }

    public EconomyResult list(final ServerPlayer actor, final int page) {
        return listHelper.list(actor, page);
    }
}