package com.horizonssmp.gov.server;

import java.util.UUID;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessEmployee;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.enums.BusinessRole;

/** Authorization helpers for scoped notes. Package-private. */
final class ScopedNoteAuthorization {

    private ScopedNoteAuthorization() {
    }

    static boolean jobMember(final GovSavedData data, final ScopedNoteService.Actor actor, final String jobId) {
        return data != null && actor != null
                && GovCapabilities.holds(data.player(actor.uuid()), jobId);
    }

    static boolean jobCanRead(final GovSavedData data, final ScopedNoteService.Actor actor, final String jobId) {
        return jobMember(data, actor, jobId)
                || (actor != null && (actor.can(GovCapability.JOB_MANAGE)
                        || actor.can(GovCapability.NOTE_VIEW_ALL)));
    }

    static boolean jobCanPost(final GovSavedData data, final ScopedNoteService.Actor actor, final String jobId) {
        return jobMember(data, actor, jobId)
                && actor != null
                && actor.can(GovCapability.NOTE_WRITE);
    }

    static boolean jobCanModerate(final GovSavedData data, final ScopedNoteService.Actor actor, final String jobId) {
        return jobCanPost(data, actor, jobId)
                || (actor != null && actor.can(GovCapability.JOB_MANAGE));
    }

    static boolean businessOwner(final BusinessRecord business, final ScopedNoteService.Actor actor) {
        return business != null && actor != null && actor.uuid() != null
                && actor.uuid().equals(business.ownerUuid());
    }

    static boolean businessEmployee(final BusinessRecord business, final ScopedNoteService.Actor actor) {
        return business != null && actor != null && business.employee(actor.uuid()) != null;
    }

    static boolean businessManager(final BusinessRecord business, final ScopedNoteService.Actor actor) {
        if (business == null || actor == null) {
            return false;
        }
        final BusinessEmployee entry = business.employee(actor.uuid());
        return entry != null && BusinessRole.MANAGER.id().equalsIgnoreCase(entry.role());
    }

    static boolean businessManage(final ScopedNoteService.Actor actor) {
        return actor != null && actor.can(GovCapability.BUSINESS_MANAGE);
    }

    static String businessRole(final BusinessRecord business, final ScopedNoteService.Actor actor) {
        if (businessOwner(business, actor)) {
            return "owner";
        }
        if (businessManager(business, actor)) {
            return "manager";
        }
        if (businessEmployee(business, actor)) {
            return "employee";
        }
        return businessManage(actor) ? "manage" : "";
    }

    static boolean supervisesJobs(final ScopedNoteService.Actor actor) {
        return actor != null
                && (actor.can(GovCapability.JOB_MANAGE) || actor.can(GovCapability.NOTE_VIEW_ALL));
    }

    static boolean boardCanRead(final BusinessRecord business, final ScopedNoteService.Actor actor) {
        return businessOwner(business, actor)
                || businessEmployee(business, actor)
                || businessManage(actor);
    }

    static boolean boardCanPost(final BusinessRecord business, final ScopedNoteService.Actor actor) {
        return business != null && business.active() && boardCanRead(business, actor);
    }

    static boolean boardCanModerate(final BusinessRecord business, final ScopedNoteService.Actor actor) {
        return businessOwner(business, actor)
                || businessManager(business, actor)
                || businessManage(actor);
    }

    static boolean perPlayerCanRead(final BusinessRecord business, final ScopedNoteService.Actor actor) {
        return businessOwner(business, actor)
                || businessManager(business, actor)
                || businessManage(actor);
    }

    static boolean perPlayerCanPost(
            final BusinessRecord business, final ScopedNoteService.Actor actor, final UUID targetUuid) {
        if (business == null || targetUuid == null || !business.active()
                || !perPlayerCanRead(business, actor)) {
            return false;
        }
        return targetUuid.equals(business.ownerUuid()) || business.employs(targetUuid);
    }

    static boolean perPlayerCanModerate(final BusinessRecord business, final ScopedNoteService.Actor actor) {
        return businessOwner(business, actor) || businessManage(actor);
    }

    static boolean isAuthor(final NoteRecord note, final ScopedNoteService.Actor actor) {
        return note.authorUuid() != null && note.authorUuid().equals(actor.uuid());
    }
}