package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

final class GovStartupCheckBusinessEmploymentServiceContext {
    final List<String> failures;
    int checks;
    final long now = 1_920_000_000_000L;
    final long day = BusinessEmploymentService.REAL_DAY_MS;

    final UUID owner = new UUID(0L, 9600L);
    final UUID manager = new UUID(0L, 9601L);
    final UUID worker = new UUID(0L, 9602L);
    final UUID other = new UUID(0L, 9603L);
    final UUID other2 = new UUID(0L, 9604L);
    final UUID exec = new UUID(0L, 9605L);
    final UUID transferTarget = new UUID(0L, 9606L);
    final UUID rejectOwner = new UUID(0L, 9607L);
    final UUID other3 = new UUID(0L, 9608L);
    final UUID registrarId = new UUID(0L, 9610L);
    final UUID officerId = new UUID(0L, 9611L);

    final String ownerName = "StartupCheck M12 Owner";
    final String managerName = "StartupCheck M12 Manager";
    final String workerName = "StartupCheck M12 Worker";
    final String otherName = "StartupCheck M12 Other";
    final String other2Name = "StartupCheck M12 Other Two";

    final GovSavedData data = new GovSavedData();

    final Set<GovCapability> registrarCaps = EnumSet.of(
            GovCapability.BUSINESS_REGISTER, GovCapability.BUSINESS_MANAGE,
            GovCapability.BUSINESS_CLOSE_APPROVE, GovCapability.SEASON_ADMIN);
    final BusinessService.Actor registrarBusiness;
    final BusinessEmploymentService.Actor registrarEmployment;
    final BusinessEmploymentService.Actor ownerActor;
    final BusinessEmploymentService.Actor managerActor;
    final BusinessEmploymentService.Actor workerActor;
    final BusinessEmploymentService.Actor otherActor;
    final BusinessEmploymentService.Actor execActor;
    final BusinessEmploymentService.Actor govworkerActor;

    BusinessRecord business;
    String businessId;
    BusinessPositionRecord managerPosition;
    EmploymentContractRecord managerPending;
    BusinessPositionRecord workerPosition;

    BusinessRecord otherShop;
    EmploymentContractRecord workerBefore;
    EmploymentContractRecord workerAfter4;

    FineRecord complaintFine;
    BusinessRecord pendingClose;
    long wagesBeforeThird;

    GovStartupCheckBusinessEmploymentServiceContext(final List<String> failures) {
        this.failures = failures;
        data.seedIfNeeded(now, false);
        for (final UUID id : List.of(owner, manager, worker, other, other2, exec,
                transferTarget, rejectOwner, other3, registrarId, officerId)) {
            data.putPlayer(PlayerRecord.blank(id, "StartupCheck M12 Player", now));
        }

        registrarBusiness = new BusinessService.Actor(
                registrarId, "StartupCheck M12 Registrar", registrarCaps);
        registrarEmployment = new BusinessEmploymentService.Actor(
                registrarId, "StartupCheck M12 Registrar", registrarCaps);
        ownerActor = new BusinessEmploymentService.Actor(owner, ownerName, Set.of());
        managerActor = new BusinessEmploymentService.Actor(manager, managerName, Set.of());
        workerActor = new BusinessEmploymentService.Actor(worker, workerName, Set.of());
        otherActor = new BusinessEmploymentService.Actor(other, otherName, Set.of());
        execActor = new BusinessEmploymentService.Actor(
                exec, "StartupCheck M12 Executive",
                EnumSet.of(GovCapability.BUSINESS_MANAGE,
                        GovCapability.BUSINESS_CLOSE_APPROVE));
        govworkerActor = new BusinessEmploymentService.Actor(
                new UUID(0L, 9612L), "StartupCheck M12 Govworker",
                EnumSet.of(GovCapability.BUSINESS_MANAGE));
    }
}
