package com.horizonssmp.gov.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class GovDashboardInit {
    private GovDashboardInit() {}

    static void init(final GovDashboardState s, final GovDashboardState.Tab tab) {
        s.activeTab = tab == null ? GovDashboardState.Tab.DASHBOARD : tab;

        s.systemLine = Component.translatable("gui.gov.shell.system");
        s.agencyLine = Component.translatable("gui.gov.shell.agency")
                .withStyle(ChatFormatting.BOLD);
        s.seasonText = GovDashboardState.tr("gui.gov.shell.season", 10);
        s.officialText = GovDashboardState.tr("gui.gov.shell.official");
        s.footerLeft = GovDashboardState.tr("gui.gov.shell.footer");
        s.footerOnly = GovDashboardState.tr("gui.gov.shell.only");
        s.footerRight = GovDashboardState.tr("gui.gov.shell.close") + "  /  " + GovDashboardState.tr("gui.gov.shell.sections");
        s.openText = GovDashboardState.tr("gui.gov.shell.open");
        s.appointmentWide = GovDashboardState.tr("gui.gov.shell.appointment");
        s.appointmentShort = GovDashboardState.tr("gui.gov.shell.appt");
        s.noAppointment = GovDashboardState.tr("gui.gov.shell.no_appointment");
        s.onDuty = GovDashboardState.tr("gui.gov.shell.on_duty");
        s.offDuty = GovDashboardState.tr("gui.gov.shell.off_duty");
        s.recordTitle = GovDashboardState.tr("gui.gov.card.record");
        s.restrictionsTitle = GovDashboardState.tr("gui.gov.card.restrictions");
        s.recordTitleBold = GovText.boldCached(s.recordTitle);
        s.restrictionsTitleBold = GovText.boldCached(s.restrictionsTitle);
        s.formText = GovDashboardState.tr("gui.gov.card.form");
        s.refreshText = GovDashboardState.tr("gui.gov.refresh").toUpperCase(Locale.ROOT);
        s.retryText = GovDashboardState.tr("gui.gov.card.retry");
        s.restrictedText = GovDashboardState.tr("gui.gov.shell.restricted");
        s.loadingText = GovDashboardState.tr("gui.gov.card.loading");
        s.errorTitle = GovDashboardState.tr("gui.gov.card.error");
        s.errorTitleBold = GovText.boldCached(s.errorTitle);
        s.receptionNote = GovDashboardState.tr("gui.gov.card.reception_note");
        s.noRestrictions = GovDashboardState.tr("gui.gov.card.no_restrictions");
        s.clearText = GovDashboardState.tr("gui.gov.card.clear");
        s.flaggedText = GovDashboardState.tr("gui.gov.card.flagged");
        s.heldText = GovDashboardState.tr("gui.gov.card.held");
        s.dueText = GovDashboardState.tr("gui.gov.card.due");
        s.clearStamp = GovText.boldCached(s.clearText);
        s.flaggedStamp = GovText.boldCached(s.flaggedText);
        s.heldStamp = GovText.boldCached(s.heldText);
        s.dueStamp = GovText.boldCached(s.dueText);

        s.cardLabels[0] = GovDashboardState.upper(GovDashboardState.tr("gui.gov.card.name"));
        s.cardLabels[1] = GovDashboardState.upper(GovDashboardState.tr("gui.gov.shell.appointment"));
        s.cardLabels[2] = GovDashboardState.upper(GovDashboardState.tr("gui.gov.card.rank"));
        s.cardLabels[3] = GovDashboardState.upper(GovDashboardState.tr("gui.gov.card.duty_status"));
        s.cardLabels[4] = GovDashboardState.upper(GovDashboardState.tr("gui.gov.card.standing"));
        s.cardLabels[5] = GovDashboardState.upper(GovDashboardState.tr("gui.gov.card.detention"));
        s.cardLabels[6] = GovDashboardState.upper(GovDashboardState.tr("gui.gov.card.fines"));

        for (final GovDashboardState.Tab section : GovDashboardState.Tab.values()) {
            int index = section.ordinal();
            String key = section == GovDashboardState.Tab.JAIL ? "gui.gov.shell.detention" : section.translationKey();
            Component label = Component.translatable(key);
            s.sectionLabels[index] = label;
            s.sectionNames[index] = label.getString();
            s.sectionBold[index] = GovText.boldCached(s.sectionNames[index]);
            int count = GovDashboardState.SUBTAB_COUNTS[index];
            s.subtabLabels[index] = new Component[count];
            s.subtabBold[index] = new Component[count];
            for (int slot = 0; slot < count; slot++) {
                String subtabKey = "gui.gov.subtab." + section.id() + "." + slot;
                s.subtabLabels[index][slot] = Component.translatable(subtabKey);
                s.subtabBold[index][slot] = Component.translatable(subtabKey)
                        .withStyle(ChatFormatting.BOLD);
            }
            List<Integer> initialVisible = new ArrayList<>();
            if (section == GovDashboardState.Tab.FINES || section == GovDashboardState.Tab.VEHICLES) {
                initialVisible.add(0);
            } else {
                for (int slot = 0; slot < count; slot++) {
                    initialVisible.add(slot);
                }
            }
            s.visibleSubtabs[index] = initialVisible;
        }
        Arrays.fill(s.railRects, GovDashboardState.EMPTY);
    }

    static void initScreen(final GovDashboardScreen s) {
        // One layout for the whole shell. It is recomputed only by this method (init/resize).
        s.layout = GovLayout.of(s.width, s.height);
        final GovRect content = s.layout.content();

        GovDashboardGeometry.layoutCardGeometry((GovDashboardScreen) s);
        GovDashboardGeometry.buildHeaderGeometry((GovDashboardScreen) s);
        GovDashboardGeometry.buildRailGeometry((GovDashboardScreen) s);

        // Create every widget first. The panels position (but never create) them in layout().
        for (final EditBox box : s.lawPanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }
        for (final EditBox box : s.jobsPanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }
        for (final EditBox box : s.peoplePanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }
        for (final EditBox box : s.notesPanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }
        for (final EditBox box : s.finesPanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }
        for (final EditBox box : s.vehiclesPanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }
        for (final EditBox box : s.weaponsPanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }
        for (final EditBox box : s.businessesPanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }
        for (final EditBox box : s.jailPanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }
        for (final EditBox box : s.auditPanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }
        for (final EditBox box : s.treasuryPanel.createWidgets(s.font())) {
            s.addGovWidget(box);
        }

        // Exactly one layout call per panel, after its widgets exist.
        s.lawPanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());
        s.jobsPanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());
        s.peoplePanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());
        s.notesPanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());
        s.finesPanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());
        s.vehiclesPanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());
        s.weaponsPanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());
        s.businessesPanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());
        s.jailPanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());
        s.auditPanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());
        s.treasuryPanel.layout(content.x(), content.y(), content.right(), content.bottom(), s.font());

        GovDashboardTabs.applyActivePanels((GovDashboardScreen) s);
        GovDashboardTabs.applyPanelSubtabs((GovDashboardScreen) s);
        GovDashboardGeometry.rebuildSubtabRects((GovDashboardScreen) s);
        GovDashboardCard.rebuildCardModel((GovDashboardScreen) s);

        // The rail appointment strip is populated from the self card even when the screen opens on
        // Law, so request it on open regardless of the initial tab.
        GovDashboardCard.requestCardIfNeeded((GovDashboardScreen) s);

    }
}
