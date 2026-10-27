package com.horizonssmp.gov.server;

import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.NoteRecord;

final class GovStartupCheckScopedNotesContext {
    final List<String> failures;
    int checks;
    final long now = 1_900_000_000_000L;
    final GovSavedData scratch = new GovSavedData();

    final UUID alice = new UUID(0L, 9001L);
    final UUID bob = new UUID(0L, 9002L);
    final UUID carol = new UUID(0L, 9003L);
    final UUID dave = new UUID(0L, 9004L);
    final UUID owner = new UUID(0L, 9010L);
    final UUID manager = new UUID(0L, 9011L);
    final UUID worker = new UUID(0L, 9012L);
    final UUID outsider = new UUID(0L, 9013L);
    final UUID manageHolder = new UUID(0L, 9014L);
    final UUID unrelatedOwner = new UUID(0L, 9015L);
    final UUID orphanMember = new UUID(0L, 9016L);
    final UUID capWorker = new UUID(0L, 9017L);

    NoteRecord future;
    BusinessRecord bakery;
    BusinessRecord suspended;
    ScopedNoteService.Actor ownerActor;
    ScopedNoteService.Actor managerActor;
    ScopedNoteService.Actor workerActor;
    ScopedNoteService.Actor outsiderActor;
    ScopedNoteService.Actor manageActor;
    ScopedNoteService.Actor memberWriter;
    ScopedNoteService.Actor jobManager;

    GovStartupCheckScopedNotesContext(final List<String> failures) {
        this.failures = failures;
    }
}
