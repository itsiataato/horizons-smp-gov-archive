package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.BusinessPermission;
import com.horizonssmp.gov.common.enums.EmploymentStatus;
import com.horizonssmp.gov.common.records.BusinessComplaintRecord;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.network.GovHandshakePayload;
import com.horizonssmp.gov.network.GovNetwork;
import net.minecraft.nbt.CompoundTag;

final class GovStartupCheckBusinessEmploymentModelPart1 {

    private GovStartupCheckBusinessEmploymentModelPart1() {
    }

    static void run(final GovStartupCheckBusinessEmploymentModelContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final UUID owner = ctx.owner;
        final UUID worker = ctx.worker;

        ctx.checks++;
        if (BusinessKeys.MAX_POSITIONS != 64
                || BusinessKeys.MAX_CONTRACTS != 256
                || BusinessKeys.MAX_COMPLAINTS != 256
                || BusinessKeys.MAX_HISTORY_PER_PLAYER != 10
                || BusinessKeys.EMPLOYMENT_PAGE_SIZE != 6
                || BusinessKeys.MAX_WAGE_PER_DAY != 1_000_000L
                || BusinessKeys.COMPLAINT_COOLDOWN_MS != 24L * 60L * 60L * 1000L
                || BusinessRecord.MAX_POSITIONS != BusinessKeys.MAX_POSITIONS
                || BusinessRecord.MAX_CONTRACTS != BusinessKeys.MAX_CONTRACTS
                || BusinessRecord.MAX_COMPLAINTS != BusinessKeys.MAX_COMPLAINTS) {
            failures.add("a business employment limit is not the documented value");
        }

        ctx.checks++;
        if (GovNetwork.PROTOCOL_VERSION != 4
                || GovHandshakePayload.current().protocol() != 4) {
            failures.add("the business employment wire protocol is not 4");
        }

        // The CEO root and the permission vocabulary.
        ctx.checks++;
        final BusinessPositionRecord ceo = BusinessPositionRecord.ceo(now, owner);
        ctx.ceo = ceo;
        if (!BusinessPositionRecord.CEO_POSITION_ID.equals(ceo.id())
                || !ceo.system()
                || ceo.parentId() != null
                || !ceo.permissions().equals(BusinessPermission.all())
                || ceo.wagePerDay() != 0L) {
            failures.add("the CEO position factory is not the canonical root");
        }
        ctx.checks++;
        final Set<BusinessPermission> parsed = BusinessPermission.parseCsv(
                " hire, PROMOTE, notes, not.a.permission ");
        if (!parsed.equals(EnumSet.of(
                BusinessPermission.HIRE, BusinessPermission.PROMOTE,
                BusinessPermission.NOTES))
                || !BusinessPermission.parseCsv(BusinessPermission.format(parsed))
                        .equals(parsed)
                || BusinessPermission.byId("POSITIONS_MANAGE").orElse(null)
                        != BusinessPermission.POSITIONS_MANAGE) {
            failures.add("the business permission parser is not tolerant or does not round-trip");
        }

        ctx.checks++;
        if (GovCapabilities.requiredFor(OpCode.BIZ_CLOSE_APPROVE)
                        != GovCapability.BUSINESS_CLOSE_APPROVE
                || GovCapabilities.requiredFor(OpCode.BIZ_CLOSE_REJECT)
                        != GovCapability.BUSINESS_CLOSE_APPROVE
                || GovCapabilities.builtinJobCapabilities(GovCapabilities.GOVWORKER_JOB_ID)
                        .contains(GovCapability.BUSINESS_CLOSE_APPROVE)
                || !GovCapabilities.builtinJobCapabilities(GovCapabilities.GOVEXECUTIVE_JOB_ID)
                        .contains(GovCapability.BUSINESS_CLOSE_APPROVE)) {
            failures.add("the business close authority is not executive-only");
        }
        ctx.checks++;
        for (final OpCode op : new OpCode[] {
                OpCode.BIZ_RENAME, OpCode.BIZ_POSITION_LIST, OpCode.BIZ_POSITION_CREATE,
                OpCode.BIZ_POSITION_EDIT, OpCode.BIZ_POSITION_DELETE,
                OpCode.BIZ_POSITION_MOVE, OpCode.BIZ_OFFER_LIST, OpCode.BIZ_OFFER_SEND,
                OpCode.BIZ_OFFER_RESPOND, OpCode.BIZ_CONTRACT_LIST,
                OpCode.BIZ_CONTRACT_END, OpCode.BIZ_CONTRACT_PROMOTE,
                OpCode.BIZ_PAYROLL_RUN, OpCode.BIZ_COMPLAINT_LIST,
                OpCode.BIZ_COMPLAINT_FILE, OpCode.BIZ_CLOSE_REQUEST, OpCode.HISTORY_LIST}) {
            if (GovCapabilities.requiredFor(op) != GovCapability.PLAYER_LOOKUP_SELF) {
                failures.add("a business employment self-service op is not baseline: " + op);
            }
        }
        ctx.checks++;
        if (OpCode.BIZ_POSITION_LIST.audited() || OpCode.BIZ_OFFER_LIST.audited()
                || OpCode.BIZ_CONTRACT_LIST.audited() || OpCode.BIZ_COMPLAINT_LIST.audited()
                || OpCode.HISTORY_LIST.audited()) {
            failures.add("a business employment read is marked as audited");
        }

        // Position round trip, including the CSV permissions and a malformed row drop.
        final BusinessPositionRecord manager = new BusinessPositionRecord(
                "pos-manager", "Manager", BusinessPositionRecord.CEO_POSITION_ID,
                EnumSet.of(BusinessPermission.HIRE, BusinessPermission.PROMOTE),
                12L, 1, false, now, owner);
        ctx.manager = manager;
        ctx.checks++;
        if (!manager.equals(BusinessPositionRecord.read(manager.write()))) {
            failures.add("a business position did not round-trip through its own encoding");
        }
        ctx.checks++;
        final CompoundTag badPosition = new CompoundTag();
        badPosition.putString("id", "pos-bad");
        badPosition.putString("title", "");
        badPosition.putLong("wagePerDay", -1L);
        if (BusinessPositionRecord.read(badPosition) != null) {
            failures.add("a malformed business position was not dropped on read");
        }

        // Contract round trip and the fail-closed status / payroll helpers.
        final EmploymentContractRecord contract = new EmploymentContractRecord(
                "c-m12-worker", "pos-manager", worker, "StartupCheck M12 Worker", owner,
                12L, false, EmploymentStatus.ACTIVE, now, now, 0L, 0L, 0L, "");
        ctx.contract = contract;
        ctx.checks++;
        if (!contract.equals(EmploymentContractRecord.read(contract.write()))) {
            failures.add("an employment contract did not round-trip through its own encoding");
        }
        ctx.checks++;
        final EmploymentContractRecord paid = contract.withPayroll(now + 10L, 24L);
        final EmploymentContractRecord fired = paid.withStatus(
                EmploymentStatus.TERMINATED, now + 20L, "startup check");
        if (!paid.active() || paid.paidTotal() != 24L || paid.lastPaidAt() != now + 10L
                || !fired.terminated() || fired.endedAt() != now + 20L
                || !"startup check".equals(fired.endReason())
                || !"pos-lead".equals(paid.withPosition("pos-lead").positionId())) {
            failures.add("the employment contract helpers are wrong");
        }
        ctx.checks++;
        final CompoundTag unknownStatus = contract.write();
        unknownStatus.putString("status", "FROM_THE_FUTURE");
        if (EmploymentContractRecord.read(unknownStatus).status()
                != EmploymentStatus.TERMINATED) {
            failures.add("an unknown contract status did not fail closed to TERMINATED");
        }

        // Complaint round trip.
        final BusinessComplaintRecord complaint = new BusinessComplaintRecord(
                "cmp-m12", worker, owner, "StartupCheck repeated absence", now, true, "f-m12");
        ctx.complaint = complaint;
        ctx.checks++;
        if (!complaint.equals(BusinessComplaintRecord.read(complaint.write()))) {
            failures.add("a business complaint did not round-trip through its own encoding");
        }
    }
}
