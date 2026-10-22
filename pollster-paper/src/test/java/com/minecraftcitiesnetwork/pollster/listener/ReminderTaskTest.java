package com.minecraftcitiesnetwork.pollster.listener;

import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.config.ConfigLoader;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.session.SessionRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ReminderTaskTest {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(T0);
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ReminderTaskTest.this.now.get(); }
    };
    private final SessionRegistry sessions = new SessionRegistry(this.clock);
    private final Player player = mock(Player.class);
    private final UUID id = UUID.randomUUID();
    private final List<Component> sent = new ArrayList<>();
    private boolean online = true;
    private Settings settings = settings(true, Duration.ofSeconds(60), Duration.ofSeconds(300));
    private ReminderTask task;

    private static Settings settings(boolean enabled, Duration idle, Duration repeat) {
        return new Settings(null, new Settings.Reminders(enabled, idle, repeat), 0, null, null);
    }

    @BeforeEach
    void setUp() throws IOException {
        Messages messages = new ConfigLoader(Files.createTempDirectory("pollster-test"),
                ConfigLoader.class.getClassLoader(), Logger.getLogger("test")).loadMessages();
        this.task = new ReminderTask(this.sessions, this.clock, () -> this.settings,
                uuid -> this.online ? Optional.of(this.player) : Optional.empty(),
                (p, message) -> this.sent.add(message), () -> messages);
        Question q = new Question("q", QuestionType.YES_NO, "Q", List.of(new Option("yes", "Yes")), true, 0, 0, null);
        SurveyDefinition survey = new SurveyDefinition("rem-one", "N", "", SurveyStatus.OPEN, true, false, false,
                List.of(), List.of(q));
        this.sessions.start(this.id, new SurveyFlow(survey, p -> false, T0), false);
    }

    private void at(long seconds) {
        this.now.set(T0.plusSeconds(seconds));
        this.task.run();
    }

    @Test
    void reminder_notBeforeIdle() {
        at(59);
        assertTrue(this.sent.isEmpty());
    }

    @Test
    void reminder_sendsAfterIdle() {
        at(60);
        assertEquals(1, this.sent.size());
        assertTrue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(this.sent.get(0)).contains("unfinished survey"));
    }

    @Test
    void reminder_repeatsAtInterval() {
        at(60);
        at(300);
        assertEquals(1, this.sent.size());
        at(360);
        assertEquals(2, this.sent.size());
    }

    @Test
    void reminder_onceWhenRepeatZero() {
        this.settings = settings(true, Duration.ofSeconds(60), Duration.ZERO);
        at(60);
        at(10_000);
        assertEquals(1, this.sent.size());
    }

    @Test
    void reminder_skipsOfflinePlayers() {
        this.online = false;
        at(120);
        assertTrue(this.sent.isEmpty());
        this.online = true;
        at(121);
        assertEquals(1, this.sent.size());
    }

    @Test
    void reminder_disabledSendsNothing() {
        this.settings = settings(false, Duration.ofSeconds(60), Duration.ofSeconds(300));
        at(1000);
        assertTrue(this.sent.isEmpty());
    }

    @Test
    void reminder_activityRestartsIdleWindow() {
        at(60);
        this.now.set(T0.plusSeconds(100));
        this.sessions.touch(this.id, this.clock.instant());
        at(159);
        assertEquals(1, this.sent.size());
        at(160);
        assertEquals(2, this.sent.size());
    }

    @Test
    void reminder_skipsPreviews() {
        this.sessions.end(this.id);
        Question q = new Question("q", QuestionType.YES_NO, "Q", List.of(new Option("yes", "Yes")), true, 0, 0, null);
        SurveyDefinition survey = new SurveyDefinition("rem-two", "N", "", SurveyStatus.DRAFT, true, false, false,
                List.of(), List.of(q));
        this.sessions.start(this.id, new SurveyFlow(survey, p -> false, T0), true);
        at(1000);
        assertTrue(this.sent.isEmpty());
    }
}
