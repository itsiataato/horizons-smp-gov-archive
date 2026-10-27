package com.horizonssmp.gov.client;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.client.GovBusinessesTypes.*;
import java.util.LinkedHashMap;
import java.util.Map;

final class GovBusinessesResponsesApply {

    static void applyList(final GovBusinessesPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.canRegister = "true".equals(fields.get(BusinessKeys.CAN_REGISTER));
        p.canManage = "true".equals(fields.get(BusinessKeys.CAN_MANAGE));
        p.canPayroll = "true".equals(fields.get(BusinessKeys.CAN_PAYROLL));
        ((GovDashboardScreen) p.host).setSubtabVisibility(GovDashboardScreen.Tab.BUSINESSES,
                new boolean[] {true, p.canRegister, p.canManage, p.canManage, p.canPayroll || p.canManage, p.canManage});
        p.viewerName = fields.getOrDefault(BusinessKeys.VIEWER_NAME, "");
        p.payrollCap = GovBusinessesUtil.longField(fields, BusinessKeys.PAYROLL_CAP_GOLD);
        if (p.payrollCap <= 0L) {
            p.payrollCap = BusinessKeys.MAX_PAYROLL_GOLD;
        }
        p.total = GovBusinessesUtil.intField(fields, BusinessKeys.TOTAL);
        p.pages = Math.max(1, GovBusinessesUtil.intField(fields, BusinessKeys.PAGES));
        p.page = GovMetrics.clamp(GovBusinessesUtil.intField(fields, BusinessKeys.PAGE), 0, p.pages - 1);
        p.filter = fields.getOrDefault(BusinessKeys.FILTER, BusinessKeys.FILTER_ALL);

        p.rows.clear();
        for (int index = 0; index < BusinessKeys.PAGE_SIZE; index++) {
            final String prefix = BusinessKeys.ROW_PREFIX + index;
            final String id = fields.get(prefix + BusinessKeys.ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            p.rows.add(new BizRow(
                    id,
                    fields.getOrDefault(prefix + BusinessKeys.ROW_NAME, ""),
                    fields.getOrDefault(prefix + BusinessKeys.ROW_OWNER, ""),
                    fields.getOrDefault(prefix + BusinessKeys.ROW_OWNER_UUID, ""),
                    fields.getOrDefault(prefix + BusinessKeys.ROW_TYPE, ""),
                    fields.getOrDefault(prefix + BusinessKeys.ROW_STATUS, ""),
                    GovBusinessesUtil.intField(fields, prefix + BusinessKeys.ROW_EMPLOYEES),
                    fields.getOrDefault(prefix + BusinessKeys.ROW_JOB, ""),
                    fields.getOrDefault(prefix + BusinessKeys.ROW_JOB_NAME, "")));
        }
        p.listState = LoadState.READY;
        p.listError = "";
        p.registryScroll = 0;

        boolean selectedVisible = false;
        for (final BizRow row : p.rows) {
            if (row.id().equals(p.selectedId)) {
                selectedVisible = true;
                break;
            }
        }
        if (!p.selectedId.isEmpty() && !selectedVisible) {
            p.selectedId = "";
            p.latestDetailReqId = "";
            p.detail.clear();
            p.detailState = LoadState.NONE;
            p.detailError = "";
            if (GovBusinessesArm.anyArmed(p)) {
                p.status = "";
                p.statusIsError = false;
            }
            GovBusinessesArm.disarmAll(p);
        }
        if (p.rows.isEmpty()) {
            p.latestDetailReqId = "";
            p.selectedId = "";
            p.detail.clear();
            p.detailState = LoadState.NONE;
            p.detailError = "";
        } else if (p.selectedId.isEmpty()) {
            GovBusinessesResponses.selectBusiness(p, p.rows.get(0).id());
        } else if (selectedVisible) {
            GovBusinessesRequests.requestDetail(p, p.selectedId);
        }
        GovBusinessesCaches.rebuildCaches(p);
    }

    static void applyDetail(final GovBusinessesPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        final String id = fields.get(BusinessKeys.ID);
        if (id == null || id.isBlank()) {
            p.detailState = LoadState.ERROR;
            p.detailError = p.detailErrorTitle;
            GovBusinessesCaches.rebuildCaches(p);
            return;
        }
        p.selectedId = id;
        p.detail.clear();
        p.detail.putAll(fields);
        p.detailState = LoadState.READY;
        p.detailError = "";
        p.detailScroll = 0;
        if (GovBusinessesArm.anyArmed(p)) {
            p.status = "";
            p.statusIsError = false;
        }
        GovBusinessesArm.disarmAll(p);
        GovBusinessesCaches.rebuildCaches(p);
    }

    static void applyPositionList(final GovBusinessesPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.positionTotal = GovBusinessesUtil.intField(fields, BusinessKeys.POSITION_COUNT);
        p.positionPages = Math.max(1, GovBusinessesUtil.intField(fields, BusinessKeys.PAGES));
        p.positionPage = GovMetrics.clamp(GovBusinessesUtil.intField(fields, BusinessKeys.PAGE),
                0, p.positionPages - 1);
        p.positionRows.clear();
        for (int index = 0; index < BusinessKeys.MAX_POSITIONS; index++) {
            final String prefix = BusinessKeys.POSITION_PREFIX + index;
            final String id = fields.get(prefix + BusinessKeys.POSITION_ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            final Map<String, String> row = new LinkedHashMap<>();
            row.put(BusinessKeys.POSITION_ROW_ID, id);
            row.put(BusinessKeys.POSITION_ROW_TITLE,
                    fields.getOrDefault(prefix + BusinessKeys.POSITION_ROW_TITLE, ""));
            row.put(BusinessKeys.POSITION_ROW_PARENT,
                    fields.getOrDefault(prefix + BusinessKeys.POSITION_ROW_PARENT, ""));
            row.put(BusinessKeys.POSITION_ROW_WAGE,
                    fields.getOrDefault(prefix + BusinessKeys.POSITION_ROW_WAGE, ""));
            row.put(BusinessKeys.POSITION_ROW_ORDER,
                    fields.getOrDefault(prefix + BusinessKeys.POSITION_ROW_ORDER, ""));
            row.put(BusinessKeys.POSITION_ROW_PERMISSIONS,
                    fields.getOrDefault(prefix + BusinessKeys.POSITION_ROW_PERMISSIONS, ""));
            row.put(BusinessKeys.POSITION_ROW_SYSTEM,
                    fields.getOrDefault(prefix + BusinessKeys.POSITION_ROW_SYSTEM, ""));
            row.put(BusinessKeys.POSITION_ROW_DEPTH,
                    fields.getOrDefault(prefix + BusinessKeys.POSITION_ROW_DEPTH, ""));
            row.put(BusinessKeys.POSITION_ROW_CAN_EDIT,
                    fields.getOrDefault(prefix + BusinessKeys.POSITION_ROW_CAN_EDIT, ""));
            p.positionRows.add(row);
        }
        p.positionState = LoadState.READY;
        p.positionError = "";
        GovBusinessesCaches.rebuildCaches(p);
    }

    static void applyOfferList(final GovBusinessesPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.offerTotal = GovBusinessesUtil.intField(fields, BusinessKeys.OFFER_COUNT);
        p.offerPages = Math.max(1, GovBusinessesUtil.intField(fields, BusinessKeys.PAGES));
        p.offerPage = GovMetrics.clamp(GovBusinessesUtil.intField(fields, BusinessKeys.PAGE),
                0, p.offerPages - 1);
        p.offerRows.clear();
        for (int index = 0; index < BusinessKeys.MAX_CONTRACTS; index++) {
            final String prefix = BusinessKeys.OFFER_PREFIX + index;
            final String id = fields.get(prefix + BusinessKeys.OFFER_ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            final Map<String, String> row = new LinkedHashMap<>();
            row.put(BusinessKeys.OFFER_ROW_ID, id);
            row.put(BusinessKeys.OFFER_ROW_POSITION,
                    fields.getOrDefault(prefix + BusinessKeys.OFFER_ROW_POSITION, ""));
            row.put(BusinessKeys.OFFER_ROW_EMPLOYEE,
                    fields.getOrDefault(prefix + BusinessKeys.OFFER_ROW_EMPLOYEE, ""));
            row.put(BusinessKeys.OFFER_ROW_EMPLOYEE_UUID,
                    fields.getOrDefault(prefix + BusinessKeys.OFFER_ROW_EMPLOYEE_UUID, ""));
            row.put(BusinessKeys.OFFER_ROW_WAGE,
                    fields.getOrDefault(prefix + BusinessKeys.OFFER_ROW_WAGE, ""));
            row.put(BusinessKeys.OFFER_ROW_ONE_TIME,
                    fields.getOrDefault(prefix + BusinessKeys.OFFER_ROW_ONE_TIME, ""));
            row.put(BusinessKeys.OFFER_ROW_STATUS,
                    fields.getOrDefault(prefix + BusinessKeys.OFFER_ROW_STATUS, ""));
            row.put(BusinessKeys.OFFER_ROW_OFFERED_AT,
                    fields.getOrDefault(prefix + BusinessKeys.OFFER_ROW_OFFERED_AT, ""));
            p.offerRows.add(row);
        }
        p.offerState = LoadState.READY;
        p.offerError = "";
        GovBusinessesCaches.rebuildCaches(p);
    }

    static void applyContractList(final GovBusinessesPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.contractTotal = GovBusinessesUtil.intField(fields, BusinessKeys.CONTRACT_COUNT);
        p.contractPages = Math.max(1, GovBusinessesUtil.intField(fields, BusinessKeys.PAGES));
        p.contractPage = GovMetrics.clamp(GovBusinessesUtil.intField(fields, BusinessKeys.PAGE),
                0, p.contractPages - 1);
        p.contractRows.clear();
        for (int index = 0; index < BusinessKeys.MAX_CONTRACTS; index++) {
            final String prefix = BusinessKeys.CONTRACT_PREFIX + index;
            final String id = fields.get(prefix + BusinessKeys.CONTRACT_ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            final Map<String, String> row = new LinkedHashMap<>();
            row.put(BusinessKeys.CONTRACT_ROW_ID, id);
            row.put(BusinessKeys.CONTRACT_ROW_POSITION,
                    fields.getOrDefault(prefix + BusinessKeys.CONTRACT_ROW_POSITION, ""));
            row.put(BusinessKeys.CONTRACT_ROW_EMPLOYEE,
                    fields.getOrDefault(prefix + BusinessKeys.CONTRACT_ROW_EMPLOYEE, ""));
            row.put(BusinessKeys.CONTRACT_ROW_EMPLOYEE_UUID,
                    fields.getOrDefault(prefix + BusinessKeys.CONTRACT_ROW_EMPLOYEE_UUID, ""));
            row.put(BusinessKeys.CONTRACT_ROW_WAGE,
                    fields.getOrDefault(prefix + BusinessKeys.CONTRACT_ROW_WAGE, ""));
            row.put(BusinessKeys.CONTRACT_ROW_ONE_TIME,
                    fields.getOrDefault(prefix + BusinessKeys.CONTRACT_ROW_ONE_TIME, ""));
            row.put(BusinessKeys.CONTRACT_ROW_STATUS,
                    fields.getOrDefault(prefix + BusinessKeys.CONTRACT_ROW_STATUS, ""));
            row.put(BusinessKeys.CONTRACT_ROW_STARTED_AT,
                    fields.getOrDefault(prefix + BusinessKeys.CONTRACT_ROW_STARTED_AT, ""));
            row.put(BusinessKeys.CONTRACT_ROW_PAID_TOTAL,
                    fields.getOrDefault(prefix + BusinessKeys.CONTRACT_ROW_PAID_TOTAL, ""));
            p.contractRows.add(row);
        }
        p.contractState = LoadState.READY;
        p.contractError = "";
        GovBusinessesCaches.rebuildCaches(p);
    }

    static void applyComplaintList(final GovBusinessesPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.complaintTotal = GovBusinessesUtil.intField(fields, BusinessKeys.COMPLAINT_COUNT);
        p.complaintPages = Math.max(1, GovBusinessesUtil.intField(fields, BusinessKeys.PAGES));
        p.complaintPage = GovMetrics.clamp(GovBusinessesUtil.intField(fields, BusinessKeys.PAGE),
                0, p.complaintPages - 1);
        p.complaintRows.clear();
        for (int index = 0; index < BusinessKeys.MAX_COMPLAINTS; index++) {
            final String prefix = BusinessKeys.COMPLAINT_PREFIX + index;
            final String id = fields.get(prefix + BusinessKeys.COMPLAINT_ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            final Map<String, String> row = new LinkedHashMap<>();
            row.put(BusinessKeys.COMPLAINT_ROW_ID, id);
            row.put(BusinessKeys.COMPLAINT_ROW_EMPLOYEE,
                    fields.getOrDefault(prefix + BusinessKeys.COMPLAINT_ROW_EMPLOYEE, ""));
            row.put(BusinessKeys.COMPLAINT_ROW_EMPLOYEE_UUID,
                    fields.getOrDefault(prefix + BusinessKeys.COMPLAINT_ROW_EMPLOYEE_UUID, ""));
            row.put(BusinessKeys.COMPLAINT_ROW_REASON,
                    fields.getOrDefault(prefix + BusinessKeys.COMPLAINT_ROW_REASON, ""));
            row.put(BusinessKeys.COMPLAINT_ROW_AT,
                    fields.getOrDefault(prefix + BusinessKeys.COMPLAINT_ROW_AT, ""));
            row.put(BusinessKeys.COMPLAINT_ROW_UPHELD,
                    fields.getOrDefault(prefix + BusinessKeys.COMPLAINT_ROW_UPHELD, ""));
            row.put(BusinessKeys.COMPLAINT_ROW_FINE,
                    fields.getOrDefault(prefix + BusinessKeys.COMPLAINT_ROW_FINE, ""));
            p.complaintRows.add(row);
        }
        p.complaintState = LoadState.READY;
        p.complaintError = "";
        GovBusinessesCaches.rebuildCaches(p);
    }
}
