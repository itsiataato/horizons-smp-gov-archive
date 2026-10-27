package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovVehiclesTypes.Button;
import com.horizonssmp.gov.client.GovVehiclesTypes.Click;
import com.horizonssmp.gov.client.GovVehiclesTypes.VehicleRow;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.VehicleKeys;

/**
 * TASK-037 button matrix for the vehicles tab: every {@link Click} value through the real
 * {@code handleClick} dispatch (ROW via the real row-hit handler {@code selectVehicle}, which
 * is what {@code mouseClicked} calls). Armed actions are driven arm-then-fire with a
 * backdated stamp, plus an explicit cancel path. Runs via {@code main()}.
 */
final class ButtonMatrixVehiclesTest {
    private ButtonMatrixVehiclesTest() {
    }

    private static GovThemedEditBox box(final String value) {
        final GovThemedEditBox b = new GovThemedEditBox(null, 0, 0, 50, 9, Component.empty());
        b.setValue(value);
        return b;
    }

    private static Button button(final Click click, final String value) {
        return new Button(new GovRect(0, 0, 10, 10), "L", "", click, value, true, false);
    }

    private static VehicleRow row(final String id, final String plate, final String owner) {
        return new VehicleRow(id, plate, "car", owner, "uuid-" + owner.toLowerCase(),
                "ACTIVE", false, "", "NONE", 0, false);
    }

    /** Fully staged panel: selection, rows, every submit box, pager mid-range, all rights. */
    private static GovVehiclesPanel fresh() {
        final GovVehiclesPanel p = new GovVehiclesPanel(null);
        p.active = true;
        p.subtab = 0;
        p.rows.add(row("v-1", "AB12", "Alex"));
        p.rows.add(row("v-2", "XY99", "Sam"));
        p.selectedPlate = "AB12";
        p.listFilter = VehicleKeys.FILTER_ALL;
        p.page = 1;
        p.pages = 3;
        p.online.add("Alex");
        p.online.add("Sam");
        p.registerOwner = "Alex";
        p.registerClass = "car";
        p.licensePoints = "2";
        p.canRegister = true;
        p.canImpound = true;
        p.canRelease = true;
        p.canScan = true;
        p.canManageLicenses = true;
        p.scanBox = box("ab-12");
        p.plateBox = box("AB-12");
        p.registerOwnerBox = box("Alex");
        p.impoundPlateBox = box("AB12");
        p.impoundReasonBox = box("Blocking hydrant");
        p.licenseTargetBox = box("uuid-alex");
        p.licenseClassBox = box("car");
        p.licenseReasonBox = box("Speeding");
        p.pending.clear();
        return p;
    }

    /** First click arms, backdated stamp passes the 600ms floor, second click fires. */
    private static void armFire(final GovVehiclesPanel p, final Click click) {
        p.pending.clear();
        p.handleClick(button(click, ""));
        p.armedAt = System.currentTimeMillis() - 1000L;
        p.pending.clear();
        p.handleClick(button(click, ""));
    }

    private static void click(final GovVehiclesPanel p, final Click click, final String value) {
        p.pending.clear();
        p.handleClick(button(click, value));
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            final GovVehiclesPanel pFilter = fresh();
            ButtonMatrix.fire("VEHICLES", "FILTER",
                    () -> click(pFilter, Click.FILTER, VehicleKeys.FILTER_VALID),
                    OpCode.VEH_LIST, VehicleKeys.KEY_FILTER, VehicleKeys.KEY_PAGE);

            final GovVehiclesPanel pRow = fresh();
            ButtonMatrix.fire("VEHICLES", "ROW", () -> {
                pRow.pending.clear();
                // Real row-hit handler: mouseClicked calls selectVehicle(hit.value()).
                pRow.selectVehicle("XY99");
            }, OpCode.VEH_INFO, VehicleKeys.KEY_PLATE);

            final GovVehiclesPanel pPage = fresh();
            ButtonMatrix.fire("VEHICLES", "PAGE",
                    () -> click(pPage, Click.PAGE, "-1"),
                    OpCode.VEH_LIST, VehicleKeys.KEY_PAGE);

            final GovVehiclesPanel pRefresh = fresh();
            ButtonMatrix.fire("VEHICLES", "REFRESH",
                    () -> click(pRefresh, Click.REFRESH, ""),
                    OpCode.VEH_LIST, VehicleKeys.KEY_FILTER);

            final GovVehiclesPanel pRetryList = fresh();
            ButtonMatrix.fire("VEHICLES", "RETRY_LIST",
                    () -> click(pRetryList, Click.RETRY_LIST, ""),
                    OpCode.VEH_LIST, VehicleKeys.KEY_FILTER);

            final GovVehiclesPanel pRetryDetail = fresh();
            ButtonMatrix.fire("VEHICLES", "RETRY_DETAIL",
                    () -> click(pRetryDetail, Click.RETRY_DETAIL, ""),
                    OpCode.VEH_INFO, VehicleKeys.KEY_PLATE);

            final GovVehiclesPanel pScan = fresh();
            ButtonMatrix.fire("VEHICLES", "SCAN",
                    () -> click(pScan, Click.SCAN, ""),
                    OpCode.PLATE_SCAN, VehicleKeys.KEY_PLATE);

            final GovVehiclesPanel pOwnerPrev = fresh();
            ButtonMatrix.fireNone("VEHICLES", "OWNER_PREV",
                    () -> click(pOwnerPrev, Click.OWNER_PREV, ""),
                    () -> ButtonMatrix.check("Sam".equals(pOwnerPrev.registerOwner),
                            "owner did not step back, got " + pOwnerPrev.registerOwner));

            final GovVehiclesPanel pOwnerNext = fresh();
            ButtonMatrix.fireNone("VEHICLES", "OWNER_NEXT",
                    () -> click(pOwnerNext, Click.OWNER_NEXT, ""),
                    () -> ButtonMatrix.check("Sam".equals(pOwnerNext.registerOwner),
                            "owner did not step forward, got " + pOwnerNext.registerOwner));

            final GovVehiclesPanel pClass = fresh();
            ButtonMatrix.fireNone("VEHICLES", "REGISTER_CLASS",
                    () -> click(pClass, Click.REGISTER_CLASS, "truck"),
                    () -> ButtonMatrix.check("truck".equals(pClass.registerClass),
                            "register class did not change"));

            final GovVehiclesPanel pRegister = fresh();
            ButtonMatrix.fire("VEHICLES", "REGISTER_SUBMIT",
                    () -> click(pRegister, Click.REGISTER_SUBMIT, ""),
                    OpCode.VEH_REGISTER, VehicleKeys.KEY_PLATE, VehicleKeys.KEY_CLASS,
                    "target");

            final GovVehiclesPanel pImpound = fresh();
            ButtonMatrix.fire("VEHICLES", "IMPOUND_SUBMIT", () -> {
                pImpound.pending.clear();
                pImpound.handleClick(button(Click.IMPOUND_SUBMIT, ""));
                ButtonMatrix.check(pImpound.armedClick == Click.IMPOUND_SUBMIT,
                        "impound did not arm");
                pImpound.armedAt = System.currentTimeMillis() - 1000L;
                pImpound.pending.clear();
                pImpound.handleClick(button(Click.IMPOUND_SUBMIT, ""));
            }, OpCode.VEH_IMPOUND, VehicleKeys.KEY_PLATE, VehicleKeys.KEY_REASON);

            // Cancel path: arming RELEASE then FILTER drops the release arm; the list
            // request fires, never the cancelled release.
            final GovVehiclesPanel pCancel = fresh();
            ButtonMatrix.fire("VEHICLES", "FILTER_CANCELS_RELEASE", () -> {
                pCancel.pending.clear();
                pCancel.handleClick(button(Click.RELEASE, ""));
                ButtonMatrix.check(pCancel.armedClick == Click.RELEASE,
                        "release did not arm");
                pCancel.pending.clear();
                pCancel.handleClick(button(Click.FILTER, VehicleKeys.FILTER_VALID));
                ButtonMatrix.check(pCancel.armedClick == null,
                        "filter did not cancel release arm");
            }, OpCode.VEH_LIST, VehicleKeys.KEY_FILTER);

            final GovVehiclesPanel pRelease = fresh();
            ButtonMatrix.fire("VEHICLES", "RELEASE", () -> armFire(pRelease, Click.RELEASE),
                    OpCode.VEH_RELEASE, VehicleKeys.KEY_PLATE);

            final GovVehiclesPanel pLicPrev = fresh();
            ButtonMatrix.fire("VEHICLES", "LICENSE_PREV",
                    () -> click(pLicPrev, Click.LICENSE_PREV, ""),
                    OpCode.VEH_INFO, VehicleKeys.KEY_PLATE);

            final GovVehiclesPanel pLicNext = fresh();
            ButtonMatrix.fire("VEHICLES", "LICENSE_NEXT",
                    () -> click(pLicNext, Click.LICENSE_NEXT, ""),
                    OpCode.VEH_INFO, VehicleKeys.KEY_PLATE);

            final GovVehiclesPanel pPoints = fresh();
            ButtonMatrix.fireNone("VEHICLES", "LICENSE_POINTS",
                    () -> click(pPoints, Click.LICENSE_POINTS, "4"),
                    () -> ButtonMatrix.check("4".equals(pPoints.licensePoints),
                            "license points did not change"));

            final GovVehiclesPanel pIssue = fresh();
            ButtonMatrix.fire("VEHICLES", "LICENSE_ISSUE",
                    () -> click(pIssue, Click.LICENSE_ISSUE, ""),
                    OpCode.LICENSE_ISSUE, "target", LicenseKeys.KEY_CLASS);

            final GovVehiclesPanel pAssess = fresh();
            ButtonMatrix.fire("VEHICLES", "LICENSE_ASSESS",
                    () -> click(pAssess, Click.LICENSE_ASSESS, ""),
                    OpCode.LICENSE_POINTS, "target", LicenseKeys.KEY_CLASS,
                    LicenseKeys.KEY_POINTS, LicenseKeys.KEY_REASON);

            final GovVehiclesPanel pRevoke = fresh();
            ButtonMatrix.fire("VEHICLES", "LICENSE_REVOKE", () -> {
                pRevoke.pending.clear();
                pRevoke.handleClick(button(Click.LICENSE_REVOKE, ""));
                ButtonMatrix.check(pRevoke.armedClick == Click.LICENSE_REVOKE,
                        "revoke did not arm");
                pRevoke.armedAt = System.currentTimeMillis() - 1000L;
                pRevoke.pending.clear();
                pRevoke.handleClick(button(Click.LICENSE_REVOKE, ""));
            }, OpCode.LICENSE_REVOKE, "target", LicenseKeys.KEY_CLASS,
                    LicenseKeys.KEY_REASON);
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("VEHICLES");
    }
}
