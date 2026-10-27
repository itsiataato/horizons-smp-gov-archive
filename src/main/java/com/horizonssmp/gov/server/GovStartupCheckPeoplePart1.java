package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.records.PlayerRecord;

final class GovStartupCheckPeoplePart1 {

    private GovStartupCheckPeoplePart1() {
    }

    static void run(final GovStartupCheckPeopleContext ctx) {
        try {
        ctx.checks = 0;
            ctx.now = System.currentTimeMillis();
            ctx.scratch = new GovSavedData();
            ctx.scratch.seedIfNeeded(ctx.now, false);

            ctx.writer = new PersonService.Actor(
                    new UUID(0L, 1002L), "StartupCheck Writer",
                    EnumSet.of(GovCapability.NOTE_WRITE, GovCapability.NOTE_VIEW_ALL,
                            GovCapability.PLAYER_LOOKUP));
            ctx.reader = new PersonService.Actor(
                    new UUID(0L, 1003L), "StartupCheck Reader",
                    EnumSet.of(GovCapability.NOTE_VIEW_ALL));
            ctx.alice = new UUID(0L, 3001L);
            ctx.bob = new UUID(0L, 3002L);
            ctx.carol = new UUID(0L, 3003L);
            ctx.dave = new UUID(0L, 3004L);

            ctx.scratch.putPlayer(PlayerRecord.blank(ctx.alice, "StartupCheck Alice", ctx.now)
                    .withJob(GovCapabilities.POLICE_JOB_ID, "Officer", ctx.now, ctx.writer.uuid(), true));
            ctx.scratch.putPlayer(PlayerRecord.blank(ctx.bob, "StartupCheck Bob", ctx.now));

            // Search: prefix, case-insensitive, ctx.capped, and never creating a file.
            ctx.checks++;
            ctx.all = 
                    PersonService.search(ctx.scratch, List.of(), "startupcheck");
            if (ctx.all.total() != 2 || ctx.all.hits().size() != 2) {
                ctx.failures.add("person search did not find the two synthetic records");
            }
            ctx.checks++;
            ctx.exact = 
                    PersonService.search(ctx.scratch, List.of(), "StartupCheck Alice");
            if (ctx.exact.total() != 1 || !ctx.alice.equals(ctx.exact.hits().get(0).uuid())) {
                ctx.failures.add("person search did not match an ctx.exact stored name");
            }
            ctx.checks++;
            if (!"Police".equals(ctx.exact.hits().get(0).jobName())) {
                ctx.failures.add("person search did not carry the primary job name");
            }
            ctx.checks++;
            if (PersonService.search(ctx.scratch, List.of(), "StartupCheck Ghost").total() != 0) {
                ctx.failures.add("person search matched a name that does not exist");
            }
            ctx.checks++;
            if (PersonService.search(ctx.scratch, List.of(), "  ").total() != 0) {
                ctx.failures.add("person search treated a blank query as a match");
            }

            // Online players are merged in and marked ctx.online, file or no file.
            ctx.online = List.of(
                    new PersonService.PersonRef(ctx.bob, "StartupCheck Bob"),
                    new PersonService.PersonRef(ctx.carol, "StartupCheck Carol"));
            ctx.checks++;
            ctx.withOnline = 
                    PersonService.search(ctx.scratch, ctx.online, "startupcheck");
            if (ctx.withOnline.total() != 3) {
                ctx.failures.add("person search did not merge the ctx.online players");
            }
            ctx.checks++;
            ctx.bobOnline = false;
            ctx.carolOnline = false;
            for (final PersonService.Hit hit : ctx.withOnline.hits()) {
                if (ctx.bob.equals(hit.uuid())) {
                    ctx.bobOnline = hit.online();
                }
                if (ctx.carol.equals(hit.uuid())) {
                    ctx.carolOnline = hit.online();
                }
            }
            if (!ctx.bobOnline || !ctx.carolOnline) {
                ctx.failures.add("an ctx.online person was not marked ctx.online in the search result");
            }
            ctx.checks++;
            if (ctx.scratch.player(ctx.carol) != null) {
                ctx.failures.add("person search created a file for an ctx.online player");
            }

            // The result cap: 25 more matches, 20 rows, and the true total still reported.
            for (int i = 1; i <= 25; i++) {
                ctx.scratch.putPlayer(PlayerRecord.blank(
                        new UUID(0L, 3100L + i), String.format("StartupCheck Row%02d", i), ctx.now));
            }
            ctx.checks++;
            ctx.capped = 
                    PersonService.search(ctx.scratch, List.of(), "startupcheck row");
            if (ctx.capped.total() != 25 || ctx.capped.hits().size() != PersonKeys.MAX_SEARCH_RESULTS) {
                ctx.failures.add("person search did not cap its rows while keeping the true total");
            }

            // The ctx.card: fields, the honest licence value, and the note-viewer gate.
            ctx.card = PersonService.detail(
                    ctx.scratch, ctx.alice, "StartupCheck Alice", false,
                    EnumSet.of(GovCapability.NOTE_VIEW_ALL, GovCapability.NOTE_WRITE));
            ctx.checks++;
            if (!"StartupCheck Alice".equals(ctx.card.get(PersonKeys.NAME))
                    || !ctx.alice.toString().equals(ctx.card.get(PersonKeys.UUID))
                    || !"false".equals(ctx.card.get(PersonKeys.ONLINE))
                    || !"Police".equals(ctx.card.get(PersonKeys.JOB))
                    || !GovCapabilities.POLICE_JOB_ID.equals(ctx.card.get(PersonKeys.JOB_ID))
                    || !"Officer".equals(ctx.card.get(PersonKeys.RANK))) {
                ctx.failures.add("the person ctx.card does not carry the record's own fields");
            }
            ctx.checks++;
            if (!"none".equals(ctx.card.get(PersonKeys.LICENSE))) {
                ctx.failures.add("the person ctx.card does not report an empty licence list as none");
            }
            ctx.checks++;
            if (!"true".equals(ctx.card.get(PersonKeys.CAN_VIEW_NOTES))
                    || !"true".equals(ctx.card.get(PersonKeys.CAN_MANAGE_NOTES))) {
                ctx.failures.add("the person ctx.card lost its viewer flags for a note ctx.reader");
            }
            ctx.blind = PersonService.detail(
                    ctx.scratch, ctx.alice, "StartupCheck Alice", false, EnumSet.noneOf(GovCapability.class));
            ctx.checks++;
            if (!"false".equals(ctx.blind.get(PersonKeys.CAN_VIEW_NOTES))
                    || !"0".equals(ctx.blind.get(PersonKeys.NOTES))) {
                ctx.failures.add("the person ctx.card exposed notes to a viewer without note.view.all");
            }
            ctx.unseen = PersonService.detail(
                    ctx.scratch, ctx.carol, "StartupCheck Carol", true, EnumSet.allOf(GovCapability.class));
            ctx.checks++;
            if (!"true".equals(ctx.unseen.get(PersonKeys.ONLINE))
                    || !"".equals(ctx.unseen.get(PersonKeys.JOB))) {
                ctx.failures.add("the person ctx.card of an ctx.online player without a file is wrong");
            }
            ctx.checks++;
            if (ctx.scratch.player(ctx.carol) != null) {
                ctx.failures.add("reading a person ctx.card created a file");
            }

        } catch (final RuntimeException e) {
            ctx.checks++;
            ctx.failures.add("person and note rule check threw: " + e);
        }
    }
}
