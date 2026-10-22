package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.api.result.OpenResult;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.store.SurveyStore;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/** A real file store behind a mocked service, plus a context that records what a screen opens. */
final class AdminFixture {

    final SurveyStore store;
    final SurveyService service = Mockito.mock(SurveyService.class);
    final Messages messages = BundledMessages.load();
    final List<String> endedSessions = new ArrayList<>();
    final List<String> previews = new ArrayList<>();
    final List<String> reports = new ArrayList<>();
    final Map<String, Integer> counts = new HashMap<>();
    final AdminUi ui;
    final Player player = Mockito.mock(Player.class);

    AdminFixture(Path dir) {
        this.store = new SurveyStore(dir, Logger.getAnonymousLogger());
        this.messages.setMessage("editor.open-warning", "Opening locks the questions.");
        this.messages.setMessage("editor.locked", "Questions are locked.");
        this.messages.setMessage("editor.move-breaks-condition", "Move breaks <args>.");
        this.messages.setMessage("editor.key-referenced", "Key used by <args>.");
        this.messages.setMessage("editor.remove-referenced", "Referenced by <args>.");
        this.messages.setMessage("editor.duplicate-key", "Duplicate <args>.");
        this.messages.setMessage("editor.unknown-question", "Unknown <args>.");
        this.messages.setMessage("editor.read-only", "Read only.");
        this.messages.setMessage("editor.anonymous-locked", "Anonymous is draft only.");
        this.messages.setMessage("errors.survey-unavailable", "This survey is no longer available.");
        wireService();
        this.ui = new AdminUi(this.service, this.store, this.messages, new Style(this.messages),
                this.endedSessions::add, (p, s) -> this.previews.add(s.id()), (p, id) -> this.reports.add(id));
    }

    private void wireService() {
        Mockito.when(this.service.surveys()).thenAnswer(i -> List.copyOf(this.store.all()));
        Mockito.when(this.service.survey(anyString())).thenAnswer(i -> this.store.get(i.getArgument(0)));
        Mockito.when(this.service.lock(anyString())).thenReturn(SurveyService.SurveyLock.OK);
        Mockito.when(this.service.responseCounts())
                .thenAnswer(i -> CompletableFuture.completedFuture(new HashMap<>(this.counts)));
        Mockito.when(this.service.save(any())).thenAnswer(i -> {
            try {
                this.store.save(i.getArgument(0));
                return CompletableFuture.completedFuture(null);
            } catch (IllegalArgumentException e) {
                return CompletableFuture.failedFuture(e);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        Mockito.when(this.service.create(any())).thenAnswer(i -> {
            try {
                this.store.save(i.getArgument(0));
                return CompletableFuture.completedFuture(null);
            } catch (IllegalArgumentException e) {
                return CompletableFuture.failedFuture(e);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        Mockito.when(this.service.open(anyString())).thenAnswer(i -> {
            save(this.store.get(i.getArgument(0)).orElseThrow().withStatus(SurveyStatus.OPEN));
            return CompletableFuture.completedFuture(new OpenResult.Opened());
        });
        Mockito.when(this.service.close(anyString())).thenAnswer(i -> {
            save(this.store.get(i.getArgument(0)).orElseThrow().withStatus(SurveyStatus.CLOSED));
            return CompletableFuture.completedFuture(null);
        });
        Mockito.when(this.service.reopen(anyString())).thenAnswer(i -> {
            save(this.store.get(i.getArgument(0)).orElseThrow().withStatus(SurveyStatus.OPEN));
            return CompletableFuture.completedFuture(null);
        });
        Mockito.when(this.service.delete(anyString())).thenAnswer(i -> {
            try {
                this.store.delete(i.getArgument(0));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return CompletableFuture.completedFuture(null);
        });
        Mockito.when(this.service.duplicate(anyString(), anyString())).thenAnswer(i -> {
            SurveyDefinition src = this.store.get(i.getArgument(0)).orElseThrow();
            SurveyDefinition copy = new SurveyDefinition(i.getArgument(1), src.name(), src.description(),
                    SurveyStatus.DRAFT, src.anonymous(), src.featured(), src.requiresPermission(),
                    src.rewards(), src.questions());
            save(copy);
            return CompletableFuture.completedFuture(copy);
        });
    }

    void save(SurveyDefinition s) {
        try {
            this.store.save(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Question single(String key, String... optionKeys) {
        List<Option> options = new ArrayList<>();
        for (String k : optionKeys) {
            options.add(new Option(k, k.toUpperCase()));
        }
        return new Question(key, QuestionType.SINGLE, "Question " + key, options, true, 0, 0, null);
    }

    static Question text(String key, String showIf) {
        return new Question(key, QuestionType.TEXT, "Text " + key, List.of(), false, 0, 0, showIf);
    }

    SurveyDefinition survey(String id, SurveyStatus status, Question... questions) {
        SurveyDefinition s = new SurveyDefinition(id, "Name of " + id, "Desc", status, false, false, false,
                List.of(), List.of(questions));
        save(s);
        return s;
    }

    FakeContext context() {
        return new FakeContext(this.player);
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    static String plain(Screen s) {
        StringBuilder sb = new StringBuilder(plain(s.title()));
        s.body().forEach(b -> sb.append('\n').append(plain(b)));
        return sb.toString();
    }

    static Button button(Screen s, String labelContains) {
        return s.buttons().stream().filter(b -> plain(b.label()).contains(labelContains)).findFirst()
                .orElseThrow(() -> new AssertionError("no button containing '" + labelContains + "' in "
                        + s.buttons().stream().map(b -> plain(b.label())).toList()));
    }

    static boolean hasButton(Screen s, String labelContains) {
        return s.buttons().stream().anyMatch(b -> plain(b.label()).contains(labelContains));
    }

    static final class FakeContext implements ScreenContext {
        final Player player;
        final Map<String, String> texts = new HashMap<>();
        final Map<String, Boolean> bools = new HashMap<>();
        final Map<String, Float> numbers = new HashMap<>();
        final List<Screen> opened = new ArrayList<>();
        boolean closed;

        FakeContext(Player player) {
            this.player = player;
        }

        Screen last() {
            return this.opened.get(this.opened.size() - 1);
        }

        FakeContext text(String key, String value) {
            this.texts.put(key, value);
            return this;
        }

        @Override public Player player() { return this.player; }
        @Override public String text(String key) { return this.texts.get(key); }
        @Override public Boolean bool(String key) { return this.bools.get(key); }
        @Override public Float number(String key) { return this.numbers.get(key); }
        @Override public void open(Screen screen) { this.opened.add(screen); }
        @Override public void close() { this.closed = true; }
    }
}
