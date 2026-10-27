package com.horizonssmp.gov.command;

import java.util.ArrayList;
import java.util.List;

import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.server.GovSavedData;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * Auto-completion providers for every {@code /gov} argument that references a server-side record.
 *
 * <p>Each provider reads the live {@link GovSavedData} so the suggestions track what actually
 * exists. Multi-word business names are quoted so Brigadier parses them as a single argument, which
 * is what {@link StringArgumentType#string()} expects.
 */
public final class GovSuggestions {

    private GovSuggestions() {
    }

    /**
     * Suggests the ids of one record map (jobs, plates, fines, weapons, bounties, detentions,
     * area permits). Consolidates 7 identical bodies: null-server guard, live-data read,
     * best-effort collection, suggest. Behavior matches the old bodies exactly.
     *
     * @param records the record values to suggest from the live data
     * @param id the id text of one record
     * @param <T> the record type
     * @return a provider suggesting every id
     */
    private static <T> SuggestionProvider<CommandSourceStack> fromIds(
            final java.util.function.Function<GovSavedData, java.util.Collection<T>> records,
            final java.util.function.Function<T, String> id) {
        return (context, builder) -> {
            final CommandSourceStack source = context.getSource();
            if (source.getServer() == null) {
                return builder.buildFuture();
            }
            final GovSavedData data = GovSavedData.get(source.getServer());
            final List<String> ids = new ArrayList<>();
            try {
                for (final T record : records.apply(data)) {
                    ids.add(id.apply(record));
                }
            } catch (final Exception ignored) {
                // best effort
            }
            return SharedSuggestionProvider.suggest(ids, builder);
        };
    }

    /** Online player names (single words, no quoting needed). */
    public static final SuggestionProvider<CommandSourceStack> PLAYERS =
            (context, builder) -> {
                final CommandSourceStack source = context.getSource();
                final List<String> names = new ArrayList<>();
                if (source.getServer() != null) {
                    for (final ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
                        names.add(p.getName().getString());
                    }
                }
                return SharedSuggestionProvider.suggest(names, builder);
            };

    /** Stored people names (online + offline known to the government). */
    public static final SuggestionProvider<CommandSourceStack> PEOPLE =
            (context, builder) -> {
                final CommandSourceStack source = context.getSource();
                final List<String> names = new ArrayList<>();
                if (source.getServer() != null) {
                    final GovSavedData data = GovSavedData.get(source.getServer());
                    final java.util.Set<String> seen = new java.util.HashSet<>();
                    for (final ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
                        names.add(p.getName().getString());
                        seen.add(p.getName().getString());
                    }
                    // stored people
                    try {
                        for (final var rec : data.players().values()) {
                            if (rec.lastName() != null && !rec.lastName().isBlank()
                                    && seen.add(rec.lastName())) {
                                names.add(rec.lastName());
                            }
                        }
                    } catch (final Exception ignored) {
                        // best effort
                    }
                }
                return SharedSuggestionProvider.suggest(names, builder);
            };

    /** Business names (quoted when they contain spaces). */
    public static final SuggestionProvider<CommandSourceStack> BUSINESSES =
            (context, builder) -> {
                final CommandSourceStack source = context.getSource();
                if (source.getServer() == null) {
                    return builder.buildFuture();
                }
                final GovSavedData data = GovSavedData.get(source.getServer());
                final List<String> names = new ArrayList<>();
                for (final BusinessRecord b : data.businesses().values()) {
                    if (b.name() == null || b.name().isBlank()) {
                        continue;
                    }
                    final String n = b.name();
                    if (n.contains(" ")) {
                        names.add("\"" + n + "\"");
                    } else {
                        names.add(n);
                    }
                }
                return SharedSuggestionProvider.suggest(names, builder);
            };

    /** Job ids. */
    public static final SuggestionProvider<CommandSourceStack> JOBS =
            fromIds(data -> data.jobs().values(), j -> j.id());

    /** Vehicle plates. */
    public static final SuggestionProvider<CommandSourceStack> PLATES =
            fromIds(data -> data.vehicles().values(), v -> v.plate());

    /** License class ids. */
    public static final SuggestionProvider<CommandSourceStack> LICENSE_CLASSES =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"quad", "car", "truck", "helicopter", "plane"}, builder);

    /** Weapon permit class ids. */
    public static final SuggestionProvider<CommandSourceStack> PERMIT_CLASSES =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"l", "s", "r", "p", "h", "d", "g", "blasting", "mining"}, builder);

    /** Fine ids. */
    public static final SuggestionProvider<CommandSourceStack> FINES =
            fromIds(data -> data.fines().values(), f -> f.id());

    /** Weapon serials. */
    public static final SuggestionProvider<CommandSourceStack> WEAPONS =
            fromIds(data -> data.weapons().values(), w -> w.serial());

    /** Bounty ids. */
    public static final SuggestionProvider<CommandSourceStack> BOUNTIES =
            fromIds(data -> data.bounties().values(), b -> b.id());

    /** Jail detention ids. */
    public static final SuggestionProvider<CommandSourceStack> DETENTIONS =
            fromIds(data -> data.detentions().values(), d -> d.id());

    /** Note ids (scoped to a person when possible). */
    public static final SuggestionProvider<CommandSourceStack> NOTES =
            (context, builder) -> {
                // generic: suggest nothing specific; caller can override
                return builder.buildFuture();
            };

    /** Area permit ids. */
    public static final SuggestionProvider<CommandSourceStack> AREA_PERMITS =
            fromIds(data -> data.areaPermits().values(), a -> a.id());

    /** Legal basis values for jail. */
    public static final SuggestionProvider<CommandSourceStack> JAIL_BASIS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"consent", "violent", "warrant"}, builder);

    /** Vehicle classes (for registration). */
    public static final SuggestionProvider<CommandSourceStack> VEHICLE_CLASSES =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"car", "truck", "quad", "helicopter", "plane", "boat"}, builder);

    /** Business types. */
    public static final SuggestionProvider<CommandSourceStack> BUSINESS_TYPES =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"shop", "bank", "factory", "farm", "mine", "service", "other"}, builder);

    /** Fine bands. */
    public static final SuggestionProvider<CommandSourceStack> FINE_BANDS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"minor", "moderate", "major", "felony"}, builder);

    /** Scope values. */
    public static final SuggestionProvider<CommandSourceStack> SCOPES =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"all", "player"}, builder);

    /** Caps edit mode. */
    public static final SuggestionProvider<CommandSourceStack> CAP_MODES =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"add", "remove"}, builder);

    /** Bounty cancel target suggests bounty ids (alias). */
    public static final SuggestionProvider<CommandSourceStack> BOUNTY_IDS = BOUNTIES;
}
