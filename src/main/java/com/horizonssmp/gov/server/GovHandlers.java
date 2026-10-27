package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovExportSection;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.JobKeys;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.common.enums.AuditOutcome;
import com.horizonssmp.gov.common.enums.FineBand;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.PermitStatus;
import com.horizonssmp.gov.common.enums.WeaponCategory;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.AuditEntry;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.law.LawIndex;
import com.horizonssmp.gov.server.economy.EconomyHandlers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The action handlers this build ships.
 *
 * <p>M1 implemented the two read actions the dashboard shell needs: {@code OPEN_TAB} (the self
 * card) and {@code LAW_ARTICLE} (one article, server-validated). M2 adds the Jobs tab: the
 * {@code JOB_LIST} and {@code JOB_INFO} reads, and the {@code JOB_CREATE}, {@code JOB_EDIT},
 * {@code JOB_DELETE} and {@code JOB_ASSIGN} mutations. M3 adds the People and Notes tabs:
 * {@code PERSON_SEARCH} and {@code LOOKUP_PERSON} for people, {@code NOTE_LIST} for notes, and
 * {@code NOTE_ADD}, {@code NOTE_EDIT} and {@code NOTE_DELETE} for the note mutations. M4 adds the
 * Fines tab: {@code FINE_LIST} and {@code FINE_INFO} for reads, and {@code FINE_ISSUE},
 * {@code FINE_PAY}, {@code FINE_COLLECT} and {@code FINE_WAIVE} for the lifecycle. M5 adds the
 * Vehicles tab: {@code VEH_LIST} and {@code VEH_INFO} for reads, {@code VEH_REGISTER},
 * {@code VEH_EDIT}, {@code VEH_IMPOUND} and {@code VEH_RELEASE} for the vehicle lifecycle,
 * {@code PLATE_SCAN} for a registry lookup, and {@code LICENSE_ISSUE}, {@code LICENSE_REVOKE} and
 * {@code LICENSE_POINTS} for licences. M9 adds the Audit tab: {@code AUDIT_PAGE} and
 * {@code AUDIT_INFO} reads, the service-audited {@code AUDIT_EXPORT} and {@code ADMIN_EXPORT}
 * writes, the {@code SEASON_STATUS} read, and the two-step {@code SEASON_BEGIN} /
 * {@code SEASON_COMMIT} reset. M11 adds the scoped-note reads and mutations: {@code NOTE_SCOPES}
 * and {@code NOTE_SCOPED_LIST} for reads, and {@code NOTE_SCOPED_ADD}, {@code NOTE_SCOPED_EDIT}
 * and {@code NOTE_SCOPED_DELETE} for the job and business scopes. Everything else stays
 * {@code UNSUPPORTED} until its own milestone, which is a real refusal produced by the dispatcher
 * pipeline, not a stub.
 *
 * <p>Every handler here is a function of its {@link GovActionDispatcher.ActionContext}. The read
 * handlers only read records. The mutation handlers call {@link JobService}, which decides whether
 * the change is legal for the data and performs it; the handler turns the outcome into an
 * {@link ActionResult} and notifies the affected player. Authorization is not decided here - the
 * dispatcher has already checked the one capability {@link
 * com.horizonssmp.gov.common.GovCapabilities#requiredFor(OpCode)} names, and no handler looks at
 * a job name, a rank or an operator flag. Accepted mutations are audited by the dispatcher, except
 * for the four service-audited actions (the two exports and the two season steps), which write
 * their own richer line with the path, hash or counts.
 */
public final class GovHandlers {

    /** The payload key naming the article a {@code LAW_ARTICLE} request asks for. */
    public static final String KEY_ARTICLE = "article";

    private static boolean registered;

    private GovHandlers() {
    }

    /**
     * Registers the handlers. Idempotent and safe to call from the mod constructor.
     *
     * <p>Called on both dists; a client never dispatches, so registering there is inert.
     */
    public static synchronized void registerAll() {
        if (registered) {
            return;
        }
        registered = true;
        GovActionDispatcher.register(OpCode.OPEN_TAB, GovHandlers::openTab);
        GovActionDispatcher.register(OpCode.LAW_ARTICLE, GovHandlers::lawArticle);
        GovActionDispatcher.register(OpCode.LOOKUP_PERSON, GovPeopleHandlers::lookupPerson);
        GovActionDispatcher.register(OpCode.PERSON_SEARCH, GovPeopleHandlers::personSearch);
        GovActionDispatcher.register(OpCode.NOTE_LIST, GovPeopleHandlers::noteList);
        GovActionDispatcher.register(OpCode.NOTE_ADD, GovPeopleHandlers::noteAdd);
        GovActionDispatcher.register(OpCode.NOTE_EDIT, GovPeopleHandlers::noteEdit);
        GovActionDispatcher.register(OpCode.NOTE_DELETE, GovPeopleHandlers::noteDelete);
        GovActionDispatcher.register(OpCode.NOTE_SCOPES, GovPeopleHandlers::noteScopes);
        GovActionDispatcher.register(OpCode.NOTE_SCOPED_LIST, GovPeopleHandlers::noteScopedList);
        GovActionDispatcher.register(OpCode.NOTE_SCOPED_ADD, GovPeopleHandlers::noteScopedAdd);
        GovActionDispatcher.register(OpCode.NOTE_SCOPED_EDIT, GovPeopleHandlers::noteScopedEdit);
        GovActionDispatcher.register(OpCode.NOTE_SCOPED_DELETE, GovPeopleHandlers::noteScopedDelete);
        GovActionDispatcher.register(OpCode.FINE_LIST, GovFineHandlers::fineList);
        GovActionDispatcher.register(OpCode.FINE_INFO, GovFineHandlers::fineInfo);
        GovActionDispatcher.register(OpCode.FINE_ISSUE, GovFineHandlers::fineIssue);
        GovActionDispatcher.register(OpCode.FINE_PAY, GovFineHandlers::finePay);
        GovActionDispatcher.register(OpCode.FINE_COLLECT, GovFineHandlers::fineCollect);
        GovActionDispatcher.register(OpCode.FINE_WAIVE, GovFineHandlers::fineWaive);
        GovActionDispatcher.register(OpCode.JOB_LIST, GovJobHandlers::jobList);
        GovActionDispatcher.register(OpCode.JOB_INFO, GovJobHandlers::jobInfo);
        GovActionDispatcher.register(OpCode.JOB_CREATE, GovJobHandlers::jobCreate);
        GovActionDispatcher.register(OpCode.JOB_EDIT, GovJobHandlers::jobEdit);
        GovActionDispatcher.register(OpCode.JOB_DELETE, GovJobHandlers::jobDelete);
        GovActionDispatcher.register(OpCode.JOB_ASSIGN, GovJobHandlers::jobAssign);
        GovActionDispatcher.register(OpCode.BIZ_LIST, GovBusinessHandlers::businessList);
        GovActionDispatcher.register(OpCode.BIZ_INFO, GovBusinessHandlers::businessInfo);
        GovActionDispatcher.register(OpCode.BIZ_REGISTER, GovBusinessHandlers::businessRegister);
        GovActionDispatcher.register(OpCode.BIZ_TRANSFER, GovBusinessHandlers::businessTransfer);
        GovActionDispatcher.register(OpCode.BIZ_SUSPEND, GovBusinessHandlers::businessSuspend);
        GovActionDispatcher.register(OpCode.BIZ_REVOKE, GovBusinessHandlers::businessClose);
        GovActionDispatcher.register(OpCode.BIZ_DELETE, GovBusinessHandlers::businessDelete);
        GovActionDispatcher.register(OpCode.BIZ_EMPLOYEE_ADD, GovBusinessHandlers::businessEmployeeAdd);
        GovActionDispatcher.register(OpCode.BIZ_EMPLOYEE_REMOVE,
                GovBusinessHandlers::businessEmployeeRemove);
        GovActionDispatcher.register(OpCode.BIZ_LINK, GovBusinessHandlers::businessLink);
        GovActionDispatcher.register(OpCode.BIZ_PAYROLL_SET, GovBusinessHandlers::businessPayrollSet);
        GovActionDispatcher.register(OpCode.BIZ_PAYROLL_RECORD,
                GovBusinessHandlers::businessPayrollRecord);
        GovActionDispatcher.register(OpCode.PAYROLL_INFO, GovBusinessHandlers::payrollInfo);
        GovActionDispatcher.register(OpCode.BIZ_RENAME, GovBusinessEmploymentHandlers::businessRename);
        GovActionDispatcher.register(OpCode.BIZ_POSITION_LIST, GovBusinessEmploymentHandlers::businessPositionList);
        GovActionDispatcher.register(OpCode.BIZ_POSITION_CREATE,
                GovBusinessEmploymentHandlers::businessPositionCreate);
        GovActionDispatcher.register(OpCode.BIZ_POSITION_EDIT, GovBusinessEmploymentHandlers::businessPositionEdit);
        GovActionDispatcher.register(OpCode.BIZ_POSITION_DELETE,
                GovBusinessEmploymentHandlers::businessPositionDelete);
        GovActionDispatcher.register(OpCode.BIZ_POSITION_MOVE, GovBusinessEmploymentHandlers::businessPositionMove);
        GovActionDispatcher.register(OpCode.BIZ_OFFER_LIST, GovBusinessEmploymentHandlers::businessOfferList);
        GovActionDispatcher.register(OpCode.BIZ_OFFER_SEND, GovBusinessEmploymentHandlers::businessOfferSend);
        GovActionDispatcher.register(OpCode.BIZ_OFFER_RESPOND, GovBusinessEmploymentHandlers::businessOfferRespond);
        GovActionDispatcher.register(OpCode.BIZ_CONTRACT_LIST, GovBusinessEmploymentHandlers::businessContractList);
        GovActionDispatcher.register(OpCode.BIZ_CONTRACT_END, GovBusinessEmploymentHandlers::businessContractEnd);
        GovActionDispatcher.register(OpCode.BIZ_CONTRACT_PROMOTE,
                GovBusinessEmploymentHandlers::businessContractPromote);
        GovActionDispatcher.register(OpCode.BIZ_PAYROLL_RUN, GovBusinessEmploymentHandlers::businessPayrollRun);
        GovActionDispatcher.register(OpCode.BIZ_COMPLAINT_LIST,
                GovBusinessGovernanceHandlers::businessComplaintList);
        GovActionDispatcher.register(OpCode.BIZ_COMPLAINT_FILE,
                GovBusinessGovernanceHandlers::businessComplaintFile);
        GovActionDispatcher.register(OpCode.BIZ_CLOSE_REQUEST, GovBusinessGovernanceHandlers::businessCloseRequest);
        GovActionDispatcher.register(OpCode.HISTORY_LIST, GovBusinessGovernanceHandlers::historyList);
        GovActionDispatcher.register(OpCode.BIZ_CLOSE_APPROVE, GovBusinessGovernanceHandlers::businessCloseApprove);
        GovActionDispatcher.register(OpCode.BIZ_CLOSE_REJECT, GovBusinessGovernanceHandlers::businessCloseReject);
        GovActionDispatcher.register(OpCode.VEH_LIST, GovVehicleHandlers::vehicleList);
        GovActionDispatcher.register(OpCode.VEH_INFO, GovVehicleHandlers::vehicleInfo);
        GovActionDispatcher.register(OpCode.VEH_REGISTER, GovVehicleHandlers::vehicleRegister);
        GovActionDispatcher.register(OpCode.VEH_EDIT, GovVehicleHandlers::vehicleEdit);
        GovActionDispatcher.register(OpCode.VEH_IMPOUND, GovVehicleHandlers::vehicleImpound);
        GovActionDispatcher.register(OpCode.VEH_RELEASE, GovVehicleHandlers::vehicleRelease);
        GovActionDispatcher.register(OpCode.PLATE_SCAN, GovVehicleHandlers::plateScan);
        GovActionDispatcher.register(OpCode.LICENSE_ISSUE, GovVehicleHandlers::licenseIssue);
        GovActionDispatcher.register(OpCode.LICENSE_REVOKE, GovVehicleHandlers::licenseRevoke);
        GovActionDispatcher.register(OpCode.LICENSE_POINTS, GovVehicleHandlers::licensePoints);
        GovActionDispatcher.register(OpCode.WPN_LIST, GovWeaponHandlers::weaponList);
        GovActionDispatcher.register(OpCode.WPN_INFO, GovWeaponHandlers::weaponInfo);
        GovActionDispatcher.register(OpCode.WPN_REGISTER, GovWeaponHandlers::weaponRegister);
        GovActionDispatcher.register(OpCode.WPN_TRANSFER, GovWeaponHandlers::weaponTransfer);
        GovActionDispatcher.register(OpCode.WPN_REVOKE, GovWeaponHandlers::weaponRevoke);
        GovActionDispatcher.register(OpCode.WPN_REPORT_LOSS, GovWeaponHandlers::weaponReportLoss);
        GovActionDispatcher.register(OpCode.WPN_CLASSIFY, GovWeaponHandlers::weaponClassify);
        GovActionDispatcher.register(OpCode.WPN_PERMIT_LIST, GovWeaponHandlers::weaponPermitList);
        GovActionDispatcher.register(OpCode.WPN_PERMIT_ISSUE, GovWeaponHandlers::weaponPermitIssue);
        GovActionDispatcher.register(OpCode.WPN_PERMIT_REVOKE, GovWeaponHandlers::weaponPermitRevoke);
        GovActionDispatcher.register(OpCode.WPN_PERMIT_SUSPEND, GovWeaponHandlers::weaponPermitSuspend);
        GovActionDispatcher.register(OpCode.AREA_PERMIT_LIST, GovAreaHandlers::areaPermitList);
        GovActionDispatcher.register(OpCode.AREA_PERMIT_CREATE, GovAreaHandlers::areaPermitCreate);
        GovActionDispatcher.register(OpCode.AREA_PERMIT_REVOKE, GovAreaHandlers::areaPermitRevoke);
        GovActionDispatcher.register(OpCode.JAIL_LIST, GovJailHandlers::jailList);
        GovActionDispatcher.register(OpCode.JAIL_INFO, GovJailHandlers::jailInfo);
        GovActionDispatcher.register(OpCode.JAIL_COMMIT, GovJailHandlers::jailDetain);
        GovActionDispatcher.register(OpCode.JAIL_RELEASE, GovJailHandlers::jailRelease);
        GovActionDispatcher.register(OpCode.JAIL_EXTEND, GovJailHandlers::jailExtend);
        GovActionDispatcher.register(OpCode.JAIL_CELL, GovJailHandlers::jailCell);
        GovActionDispatcher.register(OpCode.JAIL_CELL_CREATE, GovJailHandlers::jailCellCreate);
        GovActionDispatcher.register(OpCode.JAIL_CELL_DELETE, GovJailHandlers::jailCellDelete);
        GovActionDispatcher.register(OpCode.CUFF, GovJailHandlers::cuff);
        GovActionDispatcher.register(OpCode.UNCUFF, GovJailHandlers::uncuff);
        GovActionDispatcher.register(OpCode.AUDIT_PAGE, GovAuditHandlers::auditPage);
        GovActionDispatcher.register(OpCode.AUDIT_INFO, GovAuditHandlers::auditInfo);
        GovActionDispatcher.register(OpCode.AUDIT_EXPORT, GovAuditHandlers::auditExport);
        GovActionDispatcher.register(OpCode.ADMIN_EXPORT, GovAuditHandlers::adminExport);
        GovActionDispatcher.register(OpCode.SEASON_STATUS, GovAuditHandlers::seasonStatus);
        GovActionDispatcher.register(OpCode.SEASON_BEGIN, GovAuditHandlers::seasonBegin);
        GovActionDispatcher.register(OpCode.SEASON_COMMIT, GovAuditHandlers::seasonCommit);
        GovActionDispatcher.register(OpCode.BOUNTY_PLACE, GovBountyHandlers::bountyPlace);
        GovActionDispatcher.register(OpCode.BOUNTY_LIST, GovBountyHandlers::bountyList);
        GovActionDispatcher.register(OpCode.BOUNTY_CANCEL, GovBountyHandlers::bountyCancel);
        EconomyHandlers.register();
        Gov.LOGGER.info(
                "[gov] registered {} action handlers: {}",
                GovActionDispatcher.registeredOps().size(),
                GovActionDispatcher.registeredOps());
    }

    /** {@code OPEN_TAB}: the actor's own card, as a field block. */
    private static ActionResult openTab(final GovActionDispatcher.ActionContext context) {
        final Map<String, String> fields =
                SelfCardService.fields(context.actor(), context.data(), context.now());
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /**
     * {@code LAW_ARTICLE}: one article of the bundled law.
     *
     * <p>The article id is validated against {@link LawIndex}, the same index the book and the
     * reader use, so a fine that records an article can never record one this build cannot show.
     * The client renders the bundled law directly and does not need this round trip yet; it exists
     * so the server-validated path is real before M2 starts storing article ids.
     */
    private static ActionResult lawArticle(final GovActionDispatcher.ActionContext context) {
        final String id = context.payload(KEY_ARTICLE).strip();
        if (id.isEmpty()) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_PAYLOAD, "No article id was given.");
        }
        return LawIndex.get().article(id)
                .map(article -> ActionResult.success(
                        context.request().reqId(),
                        GovFields.encode(Map.of(
                                "id", article.id(),
                                "title", article.title(),
                                "body", article.body(),
                                "penalty", article.penalty()))))
                .orElseGet(() -> ActionResult.failure(
                        context.request().reqId(),
                        ErrorCode.BAD_ARTICLE,
                        "There is no law article " + Sanitizer.singleLine(id, 32) + " in this build."));
    }

}
