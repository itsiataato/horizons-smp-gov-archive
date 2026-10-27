package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.BusinessRole;
import com.horizonssmp.gov.common.records.BusinessEmployee;
import com.horizonssmp.gov.common.records.BusinessRecord;

/**
 * Helper for {@link BusinessService}: staff management.
 */
public final class BusinessStaff {

    private BusinessStaff() {
    }

    public static BusinessService.Outcome addEmployee(
            final GovSavedData data,
            final BusinessService.Actor actor,
            final String rawBusiness,
            final UUID employeeUuid,
            final String employeeName,
            final String rawRole,
            final long now) {
        if (data == null || actor == null || employeeUuid == null) {
            return BusinessService.Outcome.refused(ErrorCode.INVALID_STATE, "No employee to add.");
        }
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (!BusinessLookups.isOwner(business, actor)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "Only the owner of this business may staff it.");
        }
        if (business.closed()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed; it cannot take staff.");
        }
        final BusinessRole role = BusinessRole.parse(rawRole);
        if (role == null) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown role. Use one of: " + BusinessRole.ids() + ".");
        }
        if (business.employs(employeeUuid)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    BusinessLookups.ownerName(data, employeeUuid) + " is already on the staff of '"
                            + business.name() + "'.");
        }
        if (business.employees().size() >= BusinessService.MAX_EMPLOYEES) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' already has the maximum of "
                            + BusinessService.MAX_EMPLOYEES + " staff entries.");
        }
        data.playerOrCreate(employeeUuid, Sanitizer.name(employeeName), now);
        final List<BusinessEmployee> staff = new ArrayList<>(business.employees());
        staff.add(new BusinessEmployee(employeeUuid, role.id(), now));
        data.putBusiness(business.withEmployees(staff));
        return BusinessService.Outcome.ok(
                "added " + BusinessLookups.ownerName(data, employeeUuid) + " as " + role.id()
                        + " of '" + business.name() + "'.",
                business.id());
    }

    public static BusinessService.Outcome removeEmployee(
            final GovSavedData data,
            final BusinessService.Actor actor,
            final String rawBusiness,
            final UUID employeeUuid) {
        if (data == null || actor == null || employeeUuid == null) {
            return BusinessService.Outcome.refused(ErrorCode.INVALID_STATE, "No employee to remove.");
        }
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (!BusinessLookups.isOwner(business, actor)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "Only the owner of this business may change its staff.");
        }
        if (!business.employs(employeeUuid)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    BusinessLookups.ownerName(data, employeeUuid) + " is not on the staff of '"
                            + business.name() + "'.");
        }
        final List<BusinessEmployee> staff = new ArrayList<>(business.employees());
        staff.removeIf(entry -> employeeUuid.equals(entry.uuid()));
        data.putBusiness(business.withEmployees(staff));
        return BusinessService.Outcome.ok(
                "removed " + BusinessLookups.ownerName(data, employeeUuid) + " from '" + business.name() + "'.",
                business.id());
    }
}