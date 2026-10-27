package com.horizonssmp.gov.client;


import com.horizonssmp.gov.client.GovDashboardScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.horizonssmp.gov.client.GovBusinessesTypes.*;
import static com.horizonssmp.gov.client.GovBusinessesTypes.*;

final class GovBusinessesCaches {

    static     void rebuildCaches(final GovBusinessesPanel p) {
        if (p.font == null) {
            return;
        }
        GovBusinessesCaches.rebuildRegistryViews(p);
        GovBusinessesCaches.rebuildDetail(p);
        GovBusinessesCaches2.rebuildStaffViews(p);
        GovBusinessesCaches2.rebuildFormValues(p);
        GovBusinessesCaches2.rebuildStatusCache(p);
        GovBusinessesCaches2.clampScrolls(p);
        GovBusinessesCaches2.rebuildStaffRemoveRects(p);
        GovBusinessesHits.rebuildHits(p);
    }

    static     void rebuildRegistryViews(final GovBusinessesPanel p) {
        p.rowViews.clear();
        if (p.registryList.w() <= 0 || p.registryRows.w() <= 0) {
            p.emptyTitle = "";
            p.emptyDetail = "";
            p.registryHeaderText = "";
            p.pageLabel = "";
            return;
        }
        final int rowWidth = Math.max(0, p.registryList.w() - 8);
        for (final BizRow row : p.rows) {
            final String status = GovBusinessesUtil.upper(GovBusinessesUtil.dash(row.status()));
            final int statusWidth = Math.min(Math.max(0, rowWidth / 3),
                    p.font.width(status) + 8);
            final int nameWidth = Math.max(0, rowWidth - statusWidth - 6);
            final String name = GovText.clip(p.font, GovBusinessesUtil.dash(row.name()), nameWidth);
            final String meta = GovBusinessesUtil.dash(row.owner())
                    + "  /  " + GovBusinessesStatus.typeLabel(p, row.type())
                    + "  /  STAFF " + row.employees();
            p.rowViews.add(new RowView(row.id(), name,
                    GovText.clip(p.font, meta, nameWidth), status, statusWidth));
        }

        p.registryHeaderText = GovText.clip(p.font,
                GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.registry_title")) + "  " + p.total,
                Math.max(0, p.registryHeader.w() - 4));
        p.pageLabel = GovText.clip(p.font,
                GovBusinessesUtil.tr("gui.gov.businesses.page_short", p.page + 1, p.pages),
                Math.max(0, Math.min(40, p.registryPage.w())));

        if (p.listState == LoadState.LOADING) {
            p.emptyTitle = p.loadingText;
            p.emptyDetail = "";
        } else if (p.listState == LoadState.ERROR) {
            p.emptyTitle = p.errorText;
            p.emptyDetail = GovText.clip(p.font, p.listError,
                    Math.max(0, p.registryRows.w() - 12));
        } else if (p.rows.isEmpty()) {
            p.emptyTitle = BusinessKeys.FILTER_ALL.equals(p.filter)
                    ? p.emptyAllText : p.emptyFilterText;
            p.emptyDetail = "";
        } else {
            p.emptyTitle = "";
            p.emptyDetail = "";
        }
    }

    static     void rebuildDetail(final GovBusinessesPanel p) {
        p.detailLines.clear();
        p.detailStatusWidth = 0;
        p.detailNameText = "";
        p.detailNameComponent = Component.empty();
        p.detailStatusText = "";
        p.detailErrorText = GovText.clip(p.font, p.detailError,
                Math.max(0, p.detailBody.w() - 12));
        if (p.registryDetail.w() <= 0 || p.detailBody.w() <= 0) {
            return;
        }
        if (p.detail.isEmpty()) {
            return;
        }

        final int valueX = p.detailBody.x()
                + GovMetrics.clamp(p.detailBody.w() / 3, 54, 110);
        final int valueWidth = Math.max(0, p.detailBody.right() - valueX);

        GovBusinessesCaches.addDetail(p, p.labelId, GovBusinessesUtil.dash(p.detail.get(BusinessKeys.ID)), valueX, valueWidth);
        GovBusinessesCaches.addDetail(p, p.labelOwner, GovBusinessesUtil.dash(p.detail.get(BusinessKeys.OWNER)), valueX, valueWidth);
        GovBusinessesCaches.addDetail(p, p.labelType, GovBusinessesStatus.typeLabel(p, p.detail.get(BusinessKeys.TYPE)),
                valueX, valueWidth);
        GovBusinessesCaches.addDetail(p, p.labelStatus, GovBusinessesUtil.dash(GovBusinessesStatus.businessStatus(p)), valueX, valueWidth);
        GovBusinessesCaches.addDetail(p, p.labelRegistered,
                GovBusinessesUtil.when(GovBusinessesUtil.longField(p.detail, BusinessKeys.CREATED_AT))
                        + " / " + GovBusinessesUtil.dash(p.detail.get(BusinessKeys.CREATED_BY)),
                valueX, valueWidth);
        GovBusinessesCaches.addDetail(p, p.labelJob, GovBusinessesStatus.jobText(p), valueX, valueWidth);
        GovBusinessesCaches.addDetail(p, p.labelStaff,
                Integer.toString(GovBusinessesUtil.intField(p.detail, BusinessKeys.EMPLOYEE_COUNT)),
                valueX, valueWidth);
        final String suspension = p.detail.getOrDefault(BusinessKeys.SUSPEND_REASON, "");
        GovBusinessesCaches.addDetail(p, p.labelSuspension,
                suspension.isBlank() ? p.noSuspensionText : suspension,
                valueX, valueWidth);
        GovBusinessesCaches.addDetail(p, p.labelBalance, p.noBalanceText, valueX, valueWidth);

        GovBusinessesCaches.addHeading(p, p.labelNotes);
        GovBusinessesCaches.addWrapped(p, p.detail.getOrDefault(BusinessKeys.NOTES, ""), 3);

        final int staff = GovBusinessesUtil.intField(p.detail, BusinessKeys.EMPLOYEE_COUNT);
        final int listed = Math.min(staff, BusinessKeys.MAX_LISTED_EMPLOYEES);
        GovBusinessesCaches.addHeading(p, p.labelStaff + "  " + staff);
        if (listed == 0) {
            GovBusinessesCaches.addText(p, p.noStaffText);
        } else {
            for (int index = 0; index < listed; index++) {
                final String prefix = BusinessKeys.EMPLOYEE_PREFIX + index;
                final String name = p.detail.getOrDefault(
                        prefix + BusinessKeys.EMPLOYEE_NAME, "");
                final String role = p.detail.getOrDefault(
                        prefix + BusinessKeys.EMPLOYEE_ROLE, "");
                final boolean online = "true".equals(
                        p.detail.get(prefix + BusinessKeys.EMPLOYEE_ONLINE));
                GovBusinessesCaches.addText(p, GovBusinessesUtil.dash(name) + "  /  " + GovBusinessesStatus.roleLabel(p, role) + "  /  "
                        + (online ? GovBusinessesUtil.tr("gui.gov.businesses.online")
                                : GovBusinessesUtil.tr("gui.gov.businesses.offline")));
            }
            if (staff > listed) {
                GovBusinessesCaches.addText(p, GovBusinessesUtil.tr("gui.gov.businesses.staff.showing", listed, staff));
            }
        }

        GovBusinessesCaches.addHeading(p, p.labelPayroll);
        GovBusinessesCaches.addText(p, GovBusinessesUtil.tr("gui.gov.businesses.payroll_rate",
                GovBusinessesUtil.longField(p.detail, BusinessKeys.PAYROLL_RATE)));
        GovBusinessesCaches.addWrapped(p, p.detail.getOrDefault(BusinessKeys.PAYROLL_SCHEDULE, ""), 2);
        GovBusinessesCaches.addText(p, GovBusinessesUtil.tr("gui.gov.businesses.payroll_history",
                GovBusinessesUtil.intField(p.detail, BusinessKeys.PAYROLL_COUNT),
                GovBusinessesUtil.longField(p.detail, BusinessKeys.PAYROLL_LAST_AMOUNT),
                GovBusinessesUtil.when(GovBusinessesUtil.longField(p.detail, BusinessKeys.PAYROLL_LAST_AT)),
                GovBusinessesUtil.dash(p.detail.get(BusinessKeys.PAYROLL_LAST_BY))));
        GovBusinessesCaches.addText(p, p.displayOnlyText);

        final String status = GovBusinessesUtil.upper(GovBusinessesUtil.dash(GovBusinessesStatus.businessStatus(p)));
        p.detailStatusWidth = Math.min(Math.max(0, p.detailHeader.w() / 3),
                p.font.width(status) + 8);
        p.detailNameText = GovText.clip(p.font, GovBusinessesUtil.dash(p.detail.get(BusinessKeys.NAME)),
                Math.max(0, p.detailHeader.w() - p.detailStatusWidth - 6));
        p.detailNameComponent = GovText.boldCached(p.detailNameText);
        p.detailStatusText = GovText.clip(p.font, status,
                Math.max(0, p.detailStatusWidth - 4));
    }

    private static void addDetail(final GovBusinessesPanel p, final String label, final String value, final int valueX,
            final int valueWidth) {
        final String visibleLabel = GovText.clip(p.font, label,
                Math.max(0, valueX - p.detailBody.x() - 4));
        final String visibleValue = GovText.clip(p.font, GovBusinessesUtil.dash(value), valueWidth);
        p.detailLines.add(new DetailLine(visibleLabel, visibleValue, null, 12, false, null));
    }

    static     void addHeading(final GovBusinessesPanel p, final String text) {
        final String visible = GovText.clip(p.font, text,
                Math.max(0, p.detailBody.w() - 8));
        p.detailLines.add(new DetailLine(visible, null, null, 14, true,
                GovText.boldCached(visible)));
    }

    static     void addText(final GovBusinessesPanel p, final String text) {
        p.detailLines.add(new DetailLine(null,
                GovText.clip(p.font, text, Math.max(0, p.detailBody.w() - 8)),
                null, 11, false, null));
    }

    static     void addWrapped(final GovBusinessesPanel p, final String text, final int maxLines) {
        if (text == null || text.isBlank()) {
            GovBusinessesCaches.addText(p, "-");
            return;
        }
        final List<FormattedCharSequence> lines = GovText.wrapCached(p.font, text,
                Math.max(1, p.detailBody.w() - 8));
        final int count = Math.min(maxLines, lines.size());
        p.detailLines.add(new DetailLine(null, null, List.copyOf(lines.subList(0, count)),
                count * 10, false, null));
    }










}
