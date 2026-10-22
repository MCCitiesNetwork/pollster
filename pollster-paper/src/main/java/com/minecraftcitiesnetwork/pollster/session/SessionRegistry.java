package com.minecraftcitiesnetwork.pollster.session;

import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** The unfinished surveys of online and recently online players, one per player. */
public final class SessionRegistry {

    private final Clock clock;
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong tokens = new AtomicLong();

    public SessionRegistry(@NotNull Clock clock) {
        this.clock = clock;
    }

    public @NotNull Instant now() {
        return this.clock.instant();
    }

    /** Starts a session, replacing any existing one; the new token makes old dialog buttons stale. */
    public @NotNull PlayerSession start(@NotNull UUID player, @NotNull SurveyFlow flow, boolean preview) {
        PlayerSession session = new PlayerSession(player, flow, preview, this.tokens.incrementAndGet(), now());
        this.sessions.put(player, session);
        return session;
    }

    public @NotNull Optional<PlayerSession> get(@NotNull UUID player) {
        return Optional.ofNullable(this.sessions.get(player));
    }

    public boolean isCurrent(@NotNull UUID player, long token) {
        PlayerSession session = this.sessions.get(player);
        return session != null && session.token() == token;
    }

    public void touch(@NotNull UUID player, @NotNull Instant now) {
        this.sessions.computeIfPresent(player, (id, session) -> session.touched(now));
    }

    public void end(@NotNull UUID player) {
        this.sessions.remove(player);
    }

    /** Ends every session on the survey, for when it is closed, deleted or edited. */
    public void endAllFor(@NotNull String surveyId) {
        this.sessions.values().removeIf(session -> session.flow().survey().id().equals(surveyId));
    }

    public @NotNull Collection<PlayerSession> all() {
        return List.copyOf(this.sessions.values());
    }
}
