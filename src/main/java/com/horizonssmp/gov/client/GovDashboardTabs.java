package com.horizonssmp.gov.client;

final class GovDashboardTabs {
    private GovDashboardTabs() {}

    static void applyActivePanels(final GovDashboardScreen s) {
        s.lawPanel.setActive(s.activeTab == GovDashboardScreen.Tab.LAW);
        s.jobsPanel.setActive(s.activeTab == GovDashboardScreen.Tab.JOBS);
        s.peoplePanel.setActive(s.activeTab == GovDashboardScreen.Tab.PEOPLE);
        s.notesPanel.setActive(s.activeTab == GovDashboardScreen.Tab.NOTES);
        s.finesPanel.setActive(s.activeTab == GovDashboardScreen.Tab.FINES);
        s.vehiclesPanel.setActive(s.activeTab == GovDashboardScreen.Tab.VEHICLES);
        s.weaponsPanel.setActive(s.activeTab == GovDashboardScreen.Tab.WEAPONS);
        s.businessesPanel.setActive(s.activeTab == GovDashboardScreen.Tab.BUSINESSES);
        s.jailPanel.setActive(s.activeTab == GovDashboardScreen.Tab.JAIL);
        s.auditPanel.setActive(s.activeTab == GovDashboardScreen.Tab.AUDIT);
        s.treasuryPanel.setActive(s.activeTab == GovDashboardScreen.Tab.TREASURY
                || s.activeTab == GovDashboardScreen.Tab.STORE);
    }

    static void applyPanelSubtabs(final GovDashboardScreen s) {
        s.lawPanel.setSubtab(s.subtabs[GovDashboardScreen.Tab.LAW.ordinal()]);
        s.jobsPanel.setSubtab(s.subtabs[GovDashboardScreen.Tab.JOBS.ordinal()]);
        s.peoplePanel.setSubtab(s.subtabs[GovDashboardScreen.Tab.PEOPLE.ordinal()]);
        s.notesPanel.setSubtab(s.subtabs[GovDashboardScreen.Tab.NOTES.ordinal()]);
        s.finesPanel.setSubtab(s.subtabs[GovDashboardScreen.Tab.FINES.ordinal()]);
        s.vehiclesPanel.setSubtab(s.subtabs[GovDashboardScreen.Tab.VEHICLES.ordinal()]);
        s.weaponsPanel.setSubtab(s.subtabs[GovDashboardScreen.Tab.WEAPONS.ordinal()]);
        s.businessesPanel.setSubtab(s.subtabs[GovDashboardScreen.Tab.BUSINESSES.ordinal()]);
        s.jailPanel.setSubtab(s.subtabs[GovDashboardScreen.Tab.JAIL.ordinal()]);
        s.auditPanel.setSubtab(s.subtabs[GovDashboardScreen.Tab.AUDIT.ordinal()]);
        s.treasuryPanel.setSubtab(s.activeTab == GovDashboardScreen.Tab.STORE
                ? 1 : s.subtabs[GovDashboardScreen.Tab.TREASURY.ordinal()]);
    }

    static void onSubtab(final GovDashboardScreen s, final GovDashboardScreen.Tab tab, final int index) {
        if (tab == null) {
            return;
        }
        final java.util.List<Integer> visible = s.visibleSubtabs[tab.ordinal()];
        int resolved = index;
        if (!visible.contains(resolved)) {
            resolved = visible.isEmpty() ? 0 : visible.get(0);
        }
        s.subtabs[tab.ordinal()] = resolved;
        switch (tab) {
            case LAW -> s.lawPanel.setSubtab(resolved);
            case JOBS -> s.jobsPanel.setSubtab(resolved);
            case PEOPLE -> s.peoplePanel.setSubtab(resolved);
            case NOTES -> s.notesPanel.setSubtab(resolved);
            case FINES -> s.finesPanel.setSubtab(resolved);
            case VEHICLES -> s.vehiclesPanel.setSubtab(resolved);
            case WEAPONS -> s.weaponsPanel.setSubtab(resolved);
            case BUSINESSES -> s.businessesPanel.setSubtab(resolved);
            case JAIL -> s.jailPanel.setSubtab(resolved);
            case AUDIT -> s.auditPanel.setSubtab(resolved);
            case TREASURY -> s.treasuryPanel.setSubtab(resolved);
            case STORE -> s.treasuryPanel.setSubtab(1);
            default -> {
            }
        }
        if (tab == s.activeTab) {
            GovDashboardGeometry.rebuildSubtabRects(s);
        }
    }

    static void rebuildVisibleTabs(final GovDashboardScreen s) {
        final java.util.List<GovDashboardScreen.Tab> next = new java.util.ArrayList<>();
        for (final GovDashboardScreen.Tab tab : GovDashboardScreen.SECTIONS) {
            if (tabVisible(s, tab)) {
                next.add(tab);
            }
        }
        if (next.isEmpty()) {
            next.add(GovDashboardScreen.Tab.DASHBOARD);
        }
        s.visibleTabs = next;
        if (s.layout != null) {
            GovDashboardGeometry.buildRailGeometry(s);
        }
    }

    static void applyPanelSubtab(final GovDashboardScreen s, final GovDashboardScreen.Tab tab, final int index) {
        switch (tab) {
            case LAW -> s.lawPanel.setSubtab(index);
            case JOBS -> s.jobsPanel.setSubtab(index);
            case PEOPLE -> s.peoplePanel.setSubtab(index);
            case NOTES -> s.notesPanel.setSubtab(index);
            case FINES -> s.finesPanel.setSubtab(index);
            case VEHICLES -> s.vehiclesPanel.setSubtab(index);
            case WEAPONS -> s.weaponsPanel.setSubtab(index);
            case BUSINESSES -> s.businessesPanel.setSubtab(index);
            case JAIL -> s.jailPanel.setSubtab(index);
            case AUDIT -> s.auditPanel.setSubtab(index);
            case TREASURY -> s.treasuryPanel.setSubtab(index);
            case STORE -> s.treasuryPanel.setSubtab(1);
            default -> {
            }
        }
    }

    static boolean tabVisible(final GovDashboardScreen s, final GovDashboardScreen.Tab tab) {
        return switch (tab) {
            case PEOPLE -> s.canPeopleTab;
            case NOTES -> s.canNotesTab;
            case AUDIT -> s.canAuditTab;
            case JOBS -> s.canJobsManageTab;
            default -> true;
        };
    }

    static void setSubtabVisibility(final GovDashboardScreen s, final GovDashboardScreen.Tab tab, final boolean[] allowed) {
        if (tab == null || allowed == null) {
            return;
        }
        final int count = Math.min(allowed.length, GovDashboardScreen.SUBTAB_COUNTS[tab.ordinal()]);
        final java.util.List<Integer> visible = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (allowed[i]) {
                visible.add(i);
            }
        }
        if (visible.isEmpty() && count > 0) {
            visible.add(0);
        }
        s.visibleSubtabs[tab.ordinal()] = visible;
        int selected = s.subtabs[tab.ordinal()];
        if (!visible.contains(selected)) {
            selected = visible.isEmpty() ? 0 : visible.get(0);
            s.subtabs[tab.ordinal()] = selected;
            applyPanelSubtab(s, tab, selected);
        }
        if (s.activeTab == tab) {
            GovDashboardGeometry.rebuildSubtabRects(s);
        }
    }
}
