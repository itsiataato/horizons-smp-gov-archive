package com.horizonssmp.gov.client;

import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.client.GovDashboardState.Tab;

/**
 * TASK-037 dashboard shell matrix: all 13 {@link Tab} values through the real
 * {@code GovDashboardScreen.selectTab} dispatch headless. DASHBOARD fires OPEN_TAB
 * (self-card); STORE reuses the treasury panel (subtab 1); every other tab is a
 * local-only switch; null stays put. Runs via {@code main()}.
 */
final class ButtonMatrixDashboardTest {
    private ButtonMatrixDashboardTest() {
    }

    private static GovDashboardScreen fresh() {
        final GovDashboardScreen s = new GovDashboardScreen(Tab.LAW);
        s.cardState = GovDashboardState.CardState.LOADING;
        s.pendingReqId = "";
        return s;
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            ButtonMatrix.fireNone("DASHBOARD", "TAB_COUNT", () -> {
            }, () -> ButtonMatrix.check(Tab.values().length == 13,
                    "expected 13 tabs, got " + Tab.values().length));

            final GovDashboardScreen pDash = fresh();
            ButtonMatrix.fire("DASHBOARD", "TAB_DASHBOARD",
                    () -> pDash.selectTab(Tab.DASHBOARD), OpCode.OPEN_TAB);

            final GovDashboardScreen pStore = fresh();
            ButtonMatrix.fire("DASHBOARD", "TAB_STORE",
                    () -> pStore.selectTab(Tab.STORE), OpCode.STORE_LIST);

            final GovDashboardScreen pJobs = fresh();
            ButtonMatrix.fire("DASHBOARD", "TAB_JOBS",
                    () -> pJobs.selectTab(Tab.JOBS), OpCode.JOB_LIST);

            final GovDashboardScreen pNotes = fresh();
            ButtonMatrix.fireSeam("DASHBOARD", "TAB_NOTES",
                    () -> pNotes.selectTab(Tab.NOTES), OpCode.NOTE_LIST);

            final GovDashboardScreen pFines = fresh();
            ButtonMatrix.fire("DASHBOARD", "TAB_FINES",
                    () -> pFines.selectTab(Tab.FINES), OpCode.FINE_LIST);

            final GovDashboardScreen pVehicles = fresh();
            ButtonMatrix.fire("DASHBOARD", "TAB_VEHICLES",
                    () -> pVehicles.selectTab(Tab.VEHICLES), OpCode.VEH_LIST);

            final GovDashboardScreen pWeapons = fresh();
            ButtonMatrix.fire("DASHBOARD", "TAB_WEAPONS",
                    () -> pWeapons.selectTab(Tab.WEAPONS), OpCode.WPN_LIST);

            final GovDashboardScreen pBiz = fresh();
            ButtonMatrix.fire("DASHBOARD", "TAB_BUSINESSES",
                    () -> pBiz.selectTab(Tab.BUSINESSES), OpCode.BIZ_LIST);

            final GovDashboardScreen pJail = fresh();
            ButtonMatrix.fire("DASHBOARD", "TAB_JAIL",
                    () -> pJail.selectTab(Tab.JAIL), OpCode.JAIL_LIST);

            final GovDashboardScreen pAudit = fresh();
            ButtonMatrix.fire("DASHBOARD", "TAB_AUDIT",
                    () -> pAudit.selectTab(Tab.AUDIT), OpCode.AUDIT_PAGE);

            final GovDashboardScreen pTreasury = fresh();
            ButtonMatrix.fire("DASHBOARD", "TAB_TREASURY",
                    () -> pTreasury.selectTab(Tab.TREASURY), OpCode.BANK_BALANCE);

            final GovDashboardScreen pLaw = fresh();
            ButtonMatrix.fireNone("DASHBOARD", "TAB_LAW",
                    () -> pLaw.selectTab(Tab.LAW),
                    () -> ButtonMatrix.check(pLaw.activeTab == Tab.LAW, "not on LAW"));

            final GovDashboardScreen pPeople = fresh();
            ButtonMatrix.fireNone("DASHBOARD", "TAB_PEOPLE",
                    () -> pPeople.selectTab(Tab.PEOPLE),
                    () -> ButtonMatrix.check(pPeople.activeTab == Tab.PEOPLE,
                            "not on PEOPLE"));

            final GovDashboardScreen pNull = fresh();
            pNull.selectTab(Tab.JOBS);
            ButtonMatrix.fireNone("DASHBOARD", "TAB_NULL",
                    () -> pNull.selectTab(null),
                    () -> ButtonMatrix.check(pNull.activeTab == Tab.JOBS, "null moved tab"));
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("DASHBOARD");
    }
}
