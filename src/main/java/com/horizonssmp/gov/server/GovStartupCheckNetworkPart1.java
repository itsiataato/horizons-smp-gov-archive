package com.horizonssmp.gov.server;

import java.util.LinkedHashMap;
import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.network.GovActionRequestPayload;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovHandshakePayload;
import com.horizonssmp.gov.network.GovNetwork;
import com.horizonssmp.gov.network.GovOpenScreenPayload;
import net.minecraft.network.protocol.PacketFlow;

final class GovStartupCheckNetworkPart1 {

    private GovStartupCheckNetworkPart1() {
    }

    static void run(final GovStartupCheckNetworkContext ctx) {
        try {
        ctx.checks = 0;

        ctx.checks += GovStartupCheckNetworkSupport.expectChannel(GovHandshakePayload.TYPE.id(), PacketFlow.CLIENTBOUND, ctx.failures);
        ctx.checks += GovStartupCheckNetworkSupport.expectChannel(GovHandshakePayload.TYPE.id(), PacketFlow.SERVERBOUND, ctx.failures);
        ctx.checks += GovStartupCheckNetworkSupport.expectChannel(GovActionRequestPayload.TYPE.id(), PacketFlow.SERVERBOUND, ctx.failures);
        ctx.checks += GovStartupCheckNetworkSupport.expectChannel(GovActionResultPayload.TYPE.id(), PacketFlow.CLIENTBOUND, ctx.failures);
        ctx.checks += GovStartupCheckNetworkSupport.expectChannel(GovOpenScreenPayload.TYPE.id(), PacketFlow.CLIENTBOUND, ctx.failures);

        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovHandshakePayload.STREAM_CODEC,
                new GovHandshakePayload(GovNetwork.PROTOCOL_VERSION, Gov.version()))) {
            ctx.failures.add("handshake payload did not round-trip through its codec");
        }

        ctx.samplePayload = new LinkedHashMap<>();
        ctx.samplePayload.put("target", "Steve");
        ctx.samplePayload.put("reason", "test = with \\ and a newline\n");
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovActionRequestPayload.STREAM_CODEC,
                new GovActionRequestPayload("OPEN_TAB", ctx.samplePayload, "req-1"))) {
            ctx.failures.add("action request payload did not round-trip through its codec");
        }

        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovActionResultPayload.STREAM_CODEC,
                new GovActionResultPayload("req-1", true, "", "", "name=Steve"))) {
            ctx.failures.add("accepted action result payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovActionResultPayload.STREAM_CODEC,
                GovActionResultPayload.of(ActionResult.failure("req-2", ErrorCode.FORBIDDEN)))) {
            ctx.failures.add("refused action result payload did not round-trip through its codec");
        }
        ctx.checks++;
        ctx.refusal = 
                GovActionResultPayload.of(ActionResult.failure("req-3", ErrorCode.BAD_ARTICLE));
        if (ctx.refusal.errorCodeValue().orElse(null) != ErrorCode.BAD_ARTICLE) {
            ctx.failures.add("ctx.refusal payload lost its stable error code");
        }
        ctx.checks++;
        if (new GovActionResultPayload("req-4", false, "CODE_FROM_A_NEWER_BUILD", "x", "")
                .errorCodeValue().isPresent()) {
            ctx.failures.add("ctx.refusal payload did not tolerate an unknown error code");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovOpenScreenPayload.STREAM_CODEC, GovOpenScreenPayload.law())) {
            ctx.failures.add("open screen payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovOpenScreenPayload.STREAM_CODEC, GovOpenScreenPayload.jobs())) {
            ctx.failures.add("jobs open screen payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovOpenScreenPayload.TAB_JOBS.equals(GovOpenScreenPayload.canonicalTab("jobs"))) {
            ctx.failures.add("the jobs tab id does not round-trip through the tolerant tab parser");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovOpenScreenPayload.STREAM_CODEC, GovOpenScreenPayload.people())) {
            ctx.failures.add("people open screen payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovOpenScreenPayload.TAB_PEOPLE.equals(GovOpenScreenPayload.canonicalTab("people"))) {
            ctx.failures.add("the people tab id does not round-trip through the tolerant tab parser");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovOpenScreenPayload.STREAM_CODEC, GovOpenScreenPayload.notes())) {
            ctx.failures.add("notes open screen payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovOpenScreenPayload.TAB_NOTES.equals(GovOpenScreenPayload.canonicalTab("notes"))) {
            ctx.failures.add("the notes tab id does not round-trip through the tolerant tab parser");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovOpenScreenPayload.STREAM_CODEC, GovOpenScreenPayload.fines())) {
            ctx.failures.add("fines open screen payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovOpenScreenPayload.TAB_FINES.equals(GovOpenScreenPayload.canonicalTab("fines"))) {
            ctx.failures.add("the fines tab id does not round-trip through the tolerant tab parser");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovOpenScreenPayload.STREAM_CODEC, GovOpenScreenPayload.vehicles())) {
            ctx.failures.add("vehicles open screen payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovOpenScreenPayload.TAB_VEHICLES.equals(
                GovOpenScreenPayload.canonicalTab("vehicles"))) {
            ctx.failures.add("the vehicles tab id does not round-trip through the tolerant tab parser");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovOpenScreenPayload.STREAM_CODEC, GovOpenScreenPayload.weapons())) {
            ctx.failures.add("weapons open screen payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovOpenScreenPayload.TAB_WEAPONS.equals(
                GovOpenScreenPayload.canonicalTab("weapons"))) {
            ctx.failures.add("the weapons tab id does not round-trip through the tolerant tab parser");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovOpenScreenPayload.STREAM_CODEC, GovOpenScreenPayload.businesses())) {
            ctx.failures.add("businesses open screen payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovOpenScreenPayload.TAB_BUSINESSES.equals(
                GovOpenScreenPayload.canonicalTab(" BUSINESSES "))) {
            ctx.failures.add("the businesses tab id does not round-trip through the tolerant tab parser");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovOpenScreenPayload.STREAM_CODEC, GovOpenScreenPayload.jail())) {
            ctx.failures.add("jail open screen payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovOpenScreenPayload.TAB_JAIL.equals(GovOpenScreenPayload.canonicalTab(" JAIL "))) {
            ctx.failures.add("the jail tab id does not round-trip through the tolerant tab parser");
        }
        ctx.checks++;
        if (!GovStartupCheckNetworkSupport.roundTrips(GovOpenScreenPayload.STREAM_CODEC, GovOpenScreenPayload.audit())) {
            ctx.failures.add("audit open screen payload did not round-trip through its codec");
        }
        ctx.checks++;
        if (!GovOpenScreenPayload.TAB_AUDIT.equals(
                GovOpenScreenPayload.canonicalTab("audit"))) {
            ctx.failures.add("the audit tab id does not round-trip through the tolerant tab parser");
        }
        ctx.checks++;
        if (!GovOpenScreenPayload.TAB_DASHBOARD.equals(GovOpenScreenPayload.canonicalTab("nonsense"))) {
            ctx.failures.add("an unknown tab id did not fall back to the dashboard tab");
        }

        } catch (final RuntimeException e) {
            ctx.checks++;
            ctx.failures.add("network rule check threw: " + e);
        }
    }
}
