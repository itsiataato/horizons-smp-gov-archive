package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.records.PlayerRecord;

/** Scope listing for scoped notes. Package-private. */
final class ScopedNoteScopes {

    private ScopedNoteScopes() {
    }

    static List<ScopedNoteService.ScopeView> scopes(final GovSavedData data, final ScopedNoteService.Actor actor) {
        if (data == null || actor == null || actor.uuid() == null) {
            return List.of();
        }
        final List<ScopedNoteService.ScopeView> result = new ArrayList<>();
        final Set<String> seenJobs = new LinkedHashSet<>();
        final PlayerRecord player = data.player(actor.uuid());

        if (player != null) {
            addJobScope(data, actor, GovCapabilities.jobIdOf(player), seenJobs, result);
        }

        if (ScopedNoteAuthorization.supervisesJobs(actor)) {
            final List<JobRecord> jobs = new ArrayList<>(data.jobs().values());
            jobs.sort(Comparator
                    .comparing((JobRecord job) -> job.displayName().toLowerCase(Locale.ROOT))
                    .thenComparing(JobRecord::id));
            for (final JobRecord job : jobs) {
                if (result.size() >= ScopedNoteService.MAX_LISTED_JOB_SCOPES) {
                    break;
                }
                if (seenJobs.contains(job.id())) {
                    continue;
                }
                seenJobs.add(job.id());
                result.add(jobScope(data, actor, job));
            }
        }

        final List<BusinessRecord> businesses = new ArrayList<>();
        for (final BusinessRecord business : data.businesses().values()) {
            if (ScopedNoteAuthorization.businessOwner(business, actor)
                    || ScopedNoteAuthorization.businessEmployee(business, actor)
                    || ScopedNoteAuthorization.businessManage(actor)) {
                businesses.add(business);
            }
        }
        businesses.sort(Comparator
                .comparing((BusinessRecord business) -> business.name().toLowerCase(Locale.ROOT))
                .thenComparing(BusinessRecord::id));
        for (int i = 0; i < businesses.size() && i < ScopedNoteService.MAX_LISTED_BUSINESS_SCOPES; i++) {
            result.add(businessScope(actor, businesses.get(i)));
        }
        return List.copyOf(result);
    }

    private static void addJobScope(
            final GovSavedData data,
            final ScopedNoteService.Actor actor,
            final String jobId,
            final Set<String> seen,
            final List<ScopedNoteService.ScopeView> result) {
        if (jobId == null || jobId.isBlank() || seen.contains(jobId)
                || result.size() >= ScopedNoteService.MAX_LISTED_JOB_SCOPES) {
            return;
        }
        final JobRecord job = data.job(jobId);
        if (job == null) {
            return;
        }
        seen.add(jobId);
        result.add(jobScope(data, actor, job));
    }

    private static ScopedNoteService.ScopeView jobScope(
            final GovSavedData data, final ScopedNoteService.Actor actor, final JobRecord job) {
        final boolean member = ScopedNoteAuthorization.jobMember(data, actor, job.id());
        final String role = member ? "member" : "supervisor";
        return new ScopedNoteService.ScopeView(
                NoteScope.JOB,
                job.id(),
                ScopedNoteSupport.label(job.displayName(), "Job " + job.id(), job.id()),
                role,
                ScopedNoteAuthorization.jobCanPost(data, actor, job.id()),
                ScopedNoteAuthorization.jobCanModerate(data, actor, job.id()));
    }

    private static ScopedNoteService.ScopeView businessScope(
            final ScopedNoteService.Actor actor, final BusinessRecord business) {
        final String role;
        if (ScopedNoteAuthorization.businessOwner(business, actor)) {
            role = "owner";
        } else if (ScopedNoteAuthorization.businessManager(business, actor)) {
            role = "manager";
        } else if (ScopedNoteAuthorization.businessEmployee(business, actor)) {
            role = "employee";
        } else {
            role = "manage";
        }
        return new ScopedNoteService.ScopeView(
                NoteScope.BUSINESS,
                business.id(),
                ScopedNoteSupport.label(business.name(), "Business " + business.id(), business.id()),
                role,
                ScopedNoteAuthorization.boardCanPost(business, actor),
                ScopedNoteAuthorization.businessOwner(business, actor)
                        || ScopedNoteAuthorization.businessManager(business, actor)
                        || ScopedNoteAuthorization.businessManage(actor));
    }
}