package com.horizonssmp.gov.network;

import java.util.Locale;

import com.horizonssmp.gov.Gov;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A server instruction to open the Gov dashboard on a given tab: {@code gov:open_screen}.
 *
 * <p>This exists because {@code /gov dashboard} and {@code /gov law} run on the server, and a
 * server cannot open a screen on a client directly. Every connected client has this mod - the
 * non-optional handshake channel guarantees it - so the server may send this to any player
 * without a client-side capability check.
 *
 * <p>The tab is a stable lowercase id, decoded tolerantly: an id this build does not know opens
 * the dashboard tab, which is a useful screen on every build. Never send an enum ordinal: the
 * ids are part of the wire contract.
 *
 * @param tab the tab id, one of {@link #TAB_DASHBOARD}, {@link #TAB_LAW}, {@link #TAB_JOBS},
 *     {@link #TAB_PEOPLE}, {@link #TAB_NOTES}, {@link #TAB_FINES}, {@link #TAB_VEHICLES},
 *     {@link #TAB_WEAPONS}, {@link #TAB_BUSINESSES}, {@link #TAB_JAIL}, {@link #TAB_AUDIT},
 *     {@link #TAB_TREASURY} or {@link #TAB_STORE}
 */
public record GovOpenScreenPayload(String tab) implements CustomPacketPayload {

    /** The dashboard tab id. */
    public static final String TAB_DASHBOARD = "dashboard";
    /** The law tab id. */
    public static final String TAB_LAW = "law";
    /** The jobs tab id. */
    public static final String TAB_JOBS = "jobs";
    /** The people tab id. */
    public static final String TAB_PEOPLE = "people";
    /** The notes tab id. */
    public static final String TAB_NOTES = "notes";
    /** The fines tab id. */
    public static final String TAB_FINES = "fines";
    /** The vehicles tab id. */
    public static final String TAB_VEHICLES = "vehicles";
    /** The weapons tab id. */
    public static final String TAB_WEAPONS = "weapons";
    /** The businesses tab id. */
    public static final String TAB_BUSINESSES = "businesses";
    /** The jail tab id. */
    public static final String TAB_JAIL = "jail";
    /** The audit tab id. */
    public static final String TAB_AUDIT = "audit";
    /** The treasury tab id. */
    public static final String TAB_TREASURY = "treasury";
    /** The hidden store tab id, which opens the Treasury screen on its Store sub-category. */
    public static final String TAB_STORE = "store";

    /** The channel id. */
    public static final CustomPacketPayload.Type<GovOpenScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Gov.MODID, "open_screen"));

    /** The wire codec. */
    public static final StreamCodec<ByteBuf, GovOpenScreenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(32), GovOpenScreenPayload::tab,
                    GovOpenScreenPayload::new);

    public GovOpenScreenPayload {
        tab = canonicalTab(tab);
    }

    /** A payload for the dashboard tab. */
    public static GovOpenScreenPayload dashboard() {
        return new GovOpenScreenPayload(TAB_DASHBOARD);
    }

    /** A payload for the law tab. */
    public static GovOpenScreenPayload law() {
        return new GovOpenScreenPayload(TAB_LAW);
    }

    /** A payload for the jobs tab. */
    public static GovOpenScreenPayload jobs() {
        return new GovOpenScreenPayload(TAB_JOBS);
    }

    /** A payload for the people tab. */
    public static GovOpenScreenPayload people() {
        return new GovOpenScreenPayload(TAB_PEOPLE);
    }

    /** A payload for the notes tab. */
    public static GovOpenScreenPayload notes() {
        return new GovOpenScreenPayload(TAB_NOTES);
    }

    /** A payload for the fines tab. */
    public static GovOpenScreenPayload fines() {
        return new GovOpenScreenPayload(TAB_FINES);
    }

    /** A payload for the vehicles tab. */
    public static GovOpenScreenPayload vehicles() {
        return new GovOpenScreenPayload(TAB_VEHICLES);
    }

    /** A payload for the weapons tab. */
    public static GovOpenScreenPayload weapons() {
        return new GovOpenScreenPayload(TAB_WEAPONS);
    }

    /** A payload for the businesses tab. */
    public static GovOpenScreenPayload businesses() {
        return new GovOpenScreenPayload(TAB_BUSINESSES);
    }

    /** A payload for the jail tab. */
    public static GovOpenScreenPayload jail() {
        return new GovOpenScreenPayload(TAB_JAIL);
    }

    /** A payload for the audit tab. */
    public static GovOpenScreenPayload audit() {
        return new GovOpenScreenPayload(TAB_AUDIT);
    }

    /** A payload for the treasury tab. */
    public static GovOpenScreenPayload treasury() {
        return new GovOpenScreenPayload(TAB_TREASURY);
    }

    /** A payload for the hidden store tab, which opens the Treasury screen on Store. */
    public static GovOpenScreenPayload store() {
        return new GovOpenScreenPayload(TAB_STORE);
    }

    /**
     * The canonical id for a raw tab string: a known tab, or the dashboard tab for anything else.
     */
    public static String canonicalTab(final String raw) {
        if (raw != null) {
            final String needle = raw.strip().toLowerCase(Locale.ROOT);
            if (TAB_LAW.equals(needle)) {
                return TAB_LAW;
            }
            if (TAB_JOBS.equals(needle)) {
                return TAB_JOBS;
            }
            if (TAB_PEOPLE.equals(needle)) {
                return TAB_PEOPLE;
            }
            if (TAB_NOTES.equals(needle)) {
                return TAB_NOTES;
            }
            if (TAB_FINES.equals(needle)) {
                return TAB_FINES;
            }
            if (TAB_VEHICLES.equals(needle)) {
                return TAB_VEHICLES;
            }
            if (TAB_WEAPONS.equals(needle)) {
                return TAB_WEAPONS;
            }
            if (TAB_BUSINESSES.equals(needle)) {
                return TAB_BUSINESSES;
            }
            if (TAB_JAIL.equals(needle)) {
                return TAB_JAIL;
            }
            if (TAB_AUDIT.equals(needle)) {
                return TAB_AUDIT;
            }
            if (TAB_TREASURY.equals(needle)) {
                return TAB_TREASURY;
            }
            if (TAB_STORE.equals(needle)) {
                return TAB_STORE;
            }
        }
        return TAB_DASHBOARD;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
