package com.minecraftcitiesnetwork.pollster.ui.player;

import com.minecraftcitiesnetwork.pollster.SoundPlayer;
import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.Reward;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.config.ConfigLoader;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.rewards.RewardRunner;
import com.minecraftcitiesnetwork.pollster.session.PlayerSession;
import com.minecraftcitiesnetwork.pollster.session.SessionRegistry;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Builds a {@link PlayerUi} over mocks so screens can be driven without a server. */
final class PlayerTestSupport {

    static final Instant START = Instant.parse("2026-10-22T12:00:00Z");

    final SurveyService service = mock(SurveyService.class);
    final RewardRunner rewards = mock(RewardRunner.class);
    final Player player = mock(Player.class);
    final UUID playerId = UUID.randomUUID();
    final List<Event> events = new ArrayList<>();
    final Map<String, SurveyDefinition> surveys = new HashMap<>();
    final SessionRegistry sessions = new SessionRegistry(Clock.fixed(START, ZoneOffset.UTC));
    final Messages messages;
    /** Who is online, by UUID, as the server would answer; the test player starts online. */
    final Map<UUID, Player> online = new HashMap<>();
    /** Set to cancel every cancellable event the screens fire. */
    boolean cancelEvents;
    final PlayerUi ui;

    PlayerTestSupport() {
        try {
            this.messages = new ConfigLoader(Files.createTempDirectory("pollster-test"),
                    ConfigLoader.class.getClassLoader(), Logger.getLogger("test")).loadMessages();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        when(this.player.getUniqueId()).thenReturn(this.playerId);
        when(this.player.getName()).thenReturn("Alex");
        when(this.player.hasPermission(anyString())).thenReturn(false);
        this.online.put(this.playerId, this.player);
        when(this.service.survey(anyString()))
                .thenAnswer(call -> Optional.ofNullable(this.surveys.get(call.<String>getArgument(0))));
        when(this.service.lock(anyString())).thenReturn(SurveyService.SurveyLock.OK);
        Consumer<Event> events = event -> {
            this.events.add(event);
            if (this.cancelEvents && event instanceof org.bukkit.event.Cancellable c) {
                c.setCancelled(true);
            }
        };
        this.ui = new PlayerUi(this.service, this.sessions, this.rewards, this.messages, Style.defaults(),
                new Settings(null, null, 0, null, null), new SoundPlayer(new Settings.Sounds(null, null, null, null)),
                events, id -> Optional.ofNullable(this.online.get(id)));
    }

    SurveyDefinition add(SurveyDefinition survey) {
        this.surveys.put(survey.id(), survey);
        return survey;
    }

    PlayerSession start(SurveyDefinition survey, boolean preview) {
        SurveyFlow flow = new SurveyFlow(survey, p -> false, START, 255);
        return this.sessions.start(this.playerId, flow, preview);
    }

    FakeContext context() {
        return new FakeContext(this.player);
    }

    static SurveyDefinition survey(String id, SurveyStatus status, List<Reward> rewards, Question... questions) {
        return new SurveyDefinition(id, "Survey " + id, "About it", status, true, false, false,
                rewards, List.of(questions));
    }

    static Question single(String key, int options, boolean required) {
        List<Option> list = new ArrayList<>();
        for (int i = 0; i < options; i++) {
            list.add(new Option("o" + i, "Option " + i));
        }
        return new Question(key, QuestionType.SINGLE, "Pick " + key, list, required, 0, 0, null);
    }

    static Question multiple(String key, int options, int min, int max) {
        List<Option> list = new ArrayList<>();
        for (int i = 0; i < options; i++) {
            list.add(new Option("m" + i, "Choice " + i));
        }
        return new Question(key, QuestionType.MULTIPLE, "Pick some " + key, list, true, min, max, null);
    }

    static Question yesNo(String key) {
        return new Question(key, QuestionType.YES_NO, "Agree " + key,
                List.of(new Option("yes", "Yes"), new Option("no", "No")), true, 0, 0, null);
    }

    static Question text(String key, boolean required) {
        return new Question(key, QuestionType.TEXT, "Say " + key, List.of(), required, 0, 0, null);
    }

    static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    static String body(Screen screen) {
        StringBuilder out = new StringBuilder();
        for (Component line : screen.body()) {
            out.append(plain(line)).append('\n');
        }
        return out.toString();
    }

    static Button button(Screen screen, String label) {
        return screen.buttons().stream().filter(b -> plain(b.label()).equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("no button '" + label + "' on " + labels(screen)));
    }

    static List<String> labels(Screen screen) {
        return screen.buttons().stream().map(b -> plain(b.label())).toList();
    }

    /** Captures the screens a click opens and serves input values by key. */
    static final class FakeContext implements ScreenContext {
        final Player player;
        final List<Screen> opened = new ArrayList<>();
        final Map<String, String> texts = new HashMap<>();
        final Map<String, Boolean> bools = new HashMap<>();
        boolean closed;

        FakeContext(Player player) {
            this.player = player;
        }

        @Override public Player player() { return this.player; }
        @Override public String text(String key) { return this.texts.get(key); }
        @Override public Boolean bool(String key) { return this.bools.get(key); }
        @Override public Float number(String key) { return null; }
        @Override public void open(Screen screen) { this.opened.add(screen); }
        @Override public void close() { this.closed = true; }

        Screen last() {
            return this.opened.get(this.opened.size() - 1);
        }
    }
}
