package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import net.minecraft.nbt.CompoundTag;

final class GovStartupCheckPeopleContext {

        public UUID alice;
    public List<NoteRecord> aliceNotes;
    public PersonService.Search all;
    public Map<String, String> blind;
    public UUID bob;
    public List<NoteRecord> bobNotes;
    public boolean bobOnline;
    public PersonService.Search capped;
    public Map<String, String> card;
    public UUID carol;
    public boolean carolOnline;
    public int checks;
    public UUID dave;
    public PersonService.NotePage davePage;
    public NoteRecord edited;
    public PersonService.Search exact;
    public List<String> failures;
    public PersonService.Outcome filled;
    public NoteRecord first;
    public PersonService.NotePage globalPage;
    public PersonService.NotePage lastPage;
    public NoteRecord legacy;
    public CompoundTag legacyNote;
    public long now;
    public List<PersonService.PersonRef> online;
    public Map<String, String> pageFields;
    public PersonService.Actor reader;
    public NoteRecord roundTrip;
    public GovSavedData scratch;
    public Map<String, String> unseen;
    public PersonService.Search withOnline;
    public PersonService.Actor writer;

    GovStartupCheckPeopleContext(final List<String> failures) {
        this.failures = failures;
        this.checks = 0;
    }
}
