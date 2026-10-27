package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.records.PlayerRecord;

/**
 * Helper for {@link PersonService} search logic.
 */
public final class PersonSearch {

    private PersonSearch() {
    }

    public static PersonService.Search search(
            final GovSavedData data, final Collection<PersonService.PersonRef> online, final String rawQuery) {
        final String query = Sanitizer.singleLine(rawQuery, Sanitizer.MAX_NAME).toLowerCase(Locale.ROOT);
        if (data == null || query.isEmpty()) {
            return new PersonService.Search(List.of(), 0);
        }

        final Map<UUID, PersonService.PersonRef> onlineByUuid = new LinkedHashMap<>();
        if (online != null) {
            for (final PersonService.PersonRef ref : online) {
                if (ref != null && ref.uuid() != null) {
                    onlineByUuid.put(ref.uuid(), ref);
                }
            }
        }

        final List<Candidate> matches = new ArrayList<>();
        final Set<UUID> seen = new HashSet<>();
        for (final PlayerRecord record : data.players().values()) {
            if (startsWith(record.lastName(), query)) {
                matches.add(new Candidate(record.uuid(), record.lastName(),
                        onlineByUuid.containsKey(record.uuid())));
                seen.add(record.uuid());
            }
        }
        for (final PersonService.PersonRef ref : onlineByUuid.values()) {
            if (!seen.contains(ref.uuid()) && startsWith(ref.name(), query)) {
                matches.add(new Candidate(ref.uuid(), Sanitizer.name(ref.name()), true));
            }
        }

        matches.sort(Comparator
                .comparing(Candidate::online).reversed()
                .thenComparing(candidate -> candidate.name().toLowerCase(Locale.ROOT))
                .thenComparing(candidate -> candidate.uuid().toString()));

        final int total = matches.size();
        final List<PersonService.Hit> hits = new ArrayList<>(Math.min(total, PersonKeys.MAX_SEARCH_RESULTS));
        for (final Candidate candidate : matches) {
            if (hits.size() >= PersonKeys.MAX_SEARCH_RESULTS) {
                break;
            }
            hits.add(hit(data, candidate));
        }
        return new PersonService.Search(hits, total);
    }

    private static boolean startsWith(final String name, final String lowerQuery) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(lowerQuery);
    }

    private record Candidate(UUID uuid, String name, boolean online) {
    }

    private static PersonService.Hit hit(final GovSavedData data, final Candidate candidate) {
        final PlayerRecord record = data.player(candidate.uuid());
        final String jobId = record == null ? "" : GovCapabilities.jobIdOf(record);
        final JobRecord job = jobId.isEmpty() ? null : data.job(jobId);
        return new PersonService.Hit(
                candidate.uuid(),
                candidate.name(),
                candidate.online(),
                jobId,
                job == null ? "" : job.displayName(),
                data.notesFor(candidate.uuid()).size(),
                SelfCardService.outstandingFines(data, candidate.uuid()));
    }
}