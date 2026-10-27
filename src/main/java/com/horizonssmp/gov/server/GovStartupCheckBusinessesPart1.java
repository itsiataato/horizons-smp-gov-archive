package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import com.horizonssmp.gov.common.enums.BusinessType;
import com.horizonssmp.gov.common.enums.BusinessRole;
import com.horizonssmp.gov.config.GovConfig;
import java.util.UUID;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.records.BusinessRecord;

final class GovStartupCheckBusinessesPart1 {

    private GovStartupCheckBusinessesPart1() {
    }

    static void run(final GovStartupCheckBusinessesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final UUID carol = ctx.carol;
        final UUID dave = ctx.dave;
        final BusinessService.Actor registrar = ctx.registrar;
        final BusinessService.Actor clerk = ctx.clerk;
        final BusinessService.Actor aliceOwner = ctx.aliceOwner;

            // now from ctx
            final boolean banking = GovConfig.bankEnabled();

            ctx.checks++;
            if (!"b-".equals(BusinessService.BUSINESS_ID_PREFIX)
                    || BusinessService.MAX_EMPLOYEES != 24
                    || BusinessKeys.PAGE_SIZE != 8
                    || BusinessKeys.MAX_LISTED_EMPLOYEES != 12
                    || BusinessKeys.MAX_PAYROLL_GOLD != 64L
                    || BusinessService.MAX_PAYROLL_GOLD != BusinessKeys.MAX_PAYROLL_GOLD) {
                failures.add("a business registry constant is not the documented value");
            }

            final OpCode[] businessOps = {
                OpCode.BIZ_LIST, OpCode.BIZ_INFO, OpCode.BIZ_REGISTER, OpCode.BIZ_TRANSFER,
                OpCode.BIZ_SUSPEND, OpCode.BIZ_REVOKE, OpCode.BIZ_EMPLOYEE_ADD,
                OpCode.BIZ_EMPLOYEE_REMOVE, OpCode.BIZ_LINK, OpCode.BIZ_PAYROLL_SET,
                OpCode.BIZ_PAYROLL_RECORD, OpCode.PAYROLL_INFO};
            ctx.checks++;
            for (final OpCode op : businessOps) {
                if (!GovActionDispatcher.isRegistered(op)) {
                    failures.add("M7 action has no handler registered: " + op);
                }
            }

            // The action matrix: the baseline read capability opens the four owner-or-manager
            // actions, and the service decides which of the two the actor is.
            ctx.checks++;
            if (GovCapabilities.requiredFor(OpCode.BIZ_LIST) != GovCapability.PLAYER_LOOKUP_SELF
                    || GovCapabilities.requiredFor(OpCode.BIZ_INFO)
                            != GovCapability.PLAYER_LOOKUP_SELF
                    || GovCapabilities.requiredFor(OpCode.BIZ_REGISTER)
                            != GovCapability.BUSINESS_REGISTER
                    || GovCapabilities.requiredFor(OpCode.BIZ_TRANSFER)
                            != GovCapability.BUSINESS_MANAGE
                    || GovCapabilities.requiredFor(OpCode.BIZ_SUSPEND)
                            != GovCapability.BUSINESS_MANAGE
                    || GovCapabilities.requiredFor(OpCode.BIZ_REVOKE)
                            != GovCapability.BUSINESS_MANAGE
                    || GovCapabilities.requiredFor(OpCode.BIZ_PAYROLL_RECORD)
                            != GovCapability.BUSINESS_MANAGE
                    || GovCapabilities.requiredFor(OpCode.BIZ_EMPLOYEE_ADD)
                            != GovCapability.PLAYER_LOOKUP_SELF
                    || GovCapabilities.requiredFor(OpCode.BIZ_EMPLOYEE_REMOVE)
                            != GovCapability.PLAYER_LOOKUP_SELF
                    || GovCapabilities.requiredFor(OpCode.BIZ_LINK)
                            != GovCapability.PLAYER_LOOKUP_SELF
                    || GovCapabilities.requiredFor(OpCode.BIZ_PAYROLL_SET)
                            != GovCapability.PLAYER_LOOKUP_SELF
                    || GovCapabilities.requiredFor(OpCode.PAYROLL_INFO)
                            != GovCapability.PAYROLL_RUN) {
                failures.add("an M7 action does not require the documented capability");
            }

            ctx.checks++;
            if (OpCode.BIZ_LIST.audited() || OpCode.BIZ_INFO.audited()
                    || OpCode.PAYROLL_INFO.audited()) {
                failures.add("a business read op is marked as audited");
            }
            ctx.checks++;
            for (final OpCode op : new OpCode[] {
                OpCode.BIZ_REGISTER, OpCode.BIZ_TRANSFER, OpCode.BIZ_SUSPEND,
                OpCode.BIZ_REVOKE, OpCode.BIZ_EMPLOYEE_ADD, OpCode.BIZ_EMPLOYEE_REMOVE,
                OpCode.BIZ_LINK, OpCode.BIZ_PAYROLL_SET, OpCode.BIZ_PAYROLL_RECORD}) {
                if (!op.audited()) {
                    failures.add("a business mutation op is not marked as audited: " + op);
                }
            }

            ctx.checks++;
            if (BusinessService.canViewAll(EnumSet.noneOf(GovCapability.class))
                    || !BusinessService.canViewAll(EnumSet.of(GovCapability.BUSINESS_MANAGE))
                    || !BusinessService.canViewAll(EnumSet.of(GovCapability.BUSINESS_REGISTER))
                    || !BusinessService.canViewAll(EnumSet.of(GovCapability.PAYROLL_RUN))
                    || BusinessService.canViewAll(EnumSet.of(GovCapability.FINE_ISSUE))) {
                failures.add("the business read scope does not follow the registry capabilities");
            }

            ctx.checks++;
            if (!BusinessKeys.FILTER_SUSPENDED.equals(
                            BusinessService.canonicalFilter("SUSPENDED"))
                    || !BusinessKeys.FILTER_CLOSED.equals(
                            BusinessService.canonicalFilter(" closed "))
                    || !BusinessKeys.FILTER_ALL.equals(
                            BusinessService.canonicalFilter("nonsense"))
                    || !BusinessKeys.FILTER_ALL.equals(BusinessService.canonicalFilter(null))) {
                failures.add("the business filter parser is not tolerant and canonical");
            }

            ctx.checks++;
            if (BusinessType.parse("SHOP") != BusinessType.SHOP
                    || BusinessType.parse("spaceship") != null
                    || BusinessRole.parse("Manager") != BusinessRole.MANAGER
                    || BusinessRole.parse("pilot") != null
                    || !BusinessType.ids().contains("shop")
                    || !BusinessRole.ids().contains("manager")) {
                failures.add("the business type or role vocabulary is not strict and complete");
            }

            final BusinessService.Actor bobOwner = new BusinessService.Actor(
                    bob, "StartupCheck Business Bob",
                    EnumSet.of(GovCapability.BUSINESS_MANAGE,
                            GovCapability.BUSINESS_CLOSE_APPROVE));
            final BusinessService.Actor citizen = new BusinessService.Actor(
                    dave, "StartupCheck Business Dave", EnumSet.noneOf(GovCapability.class));

            // Registration: the id is stable and short, the owner defaults to the named owner, and
            // the card starts empty with no balance of any kind.
            final BusinessService.Outcome aceOutcome = BusinessService.register(
                    scratch, registrar, "StartupCheck Ace Shop", "shop", alice,
                    "StartupCheck Business Alice", now);
            final BusinessRecord ace = BusinessService.find(scratch, "StartupCheck Ace Shop");
            ctx.checks++;
            if (!aceOutcome.ok() || ace == null
                    || !ace.id().startsWith(BusinessService.BUSINESS_ID_PREFIX)
                    || !"StartupCheck Ace Shop".equals(ace.name())
                    || !alice.equals(ace.ownerUuid())
                    || !"shop".equals(ace.type())
                    || !BusinessRecord.STATUS_ACTIVE.equals(ace.status())
                    || ace.createdAt() != now
                    || !registrar.uuid().equals(ace.createdBy())
                    || !ace.employees().isEmpty()
                    || !ace.payroll().empty()
                    || !ace.linkedJobId().isEmpty()) {
                failures.add("the business registration round-trip did not produce a clean card");
            }

            ctx.checks++;
            if (BusinessService.register(scratch, registrar, "startupcheck ace SHOP", "shop",
                    alice, "StartupCheck Business Alice", now).code() != ErrorCode.NAME_TAKEN) {
                failures.add("a duplicate business name was not refused case-insensitively");
            }

            // Sanitisation: formatting codes and control characters never reach the stored name.
            final BusinessService.Outcome cleanOutcome = BusinessService.register(
                    scratch, registrar, "StartupCheck \u00a7cClean\tShop", "service", carol,
                    "StartupCheck Business Carol", now);
            final BusinessRecord cleanRecord =
                    BusinessService.find(scratch, "startupcheck clean shop");
            ctx.checks++;
            if (!cleanOutcome.ok()
                    || cleanRecord == null
                    || !"StartupCheck Clean Shop".equals(cleanRecord.name())
                    || BusinessService.register(scratch, registrar, "startupcheck clean shop",
                            "service", alice, "StartupCheck Business Alice", now).code()
                            != ErrorCode.NAME_TAKEN) {
                failures.add("the business name was not sanitised, or the cleaned name is not unique");
            }

            ctx.checks++;
            if (BusinessService.register(scratch, registrar, "   ", "shop", alice,
                    "StartupCheck Business Alice", now).code() != ErrorCode.BAD_PAYLOAD
                    || BusinessService.register(scratch, registrar, "StartupCheck Bad Type",
                            "spaceship", alice, "StartupCheck Business Alice", now).code()
                            != ErrorCode.BAD_PAYLOAD) {
                failures.add("a blank name or an unknown business type was accepted");
            }

            ctx.checks++;
            if (BusinessService.register(scratch, citizen, "StartupCheck Citizen Shop", "shop",
                    dave, "StartupCheck Business Dave", now).code() != ErrorCode.FORBIDDEN
                    || BusinessService.register(scratch, clerk, "StartupCheck Clerk Shop", "shop",
                            dave, "StartupCheck Business Dave", now).code()
                            != ErrorCode.FORBIDDEN) {
                failures.add("business registration did not refuse an actor without business.register");
            }


        ctx.ace = ace;
    }
}
