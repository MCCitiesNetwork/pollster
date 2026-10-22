package com.minecraftcitiesnetwork.pollster.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.Reward;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

class SurveyStoreTest {

    @TempDir
    Path dir;

    private static SurveyDefinition everything() {
        List<Option> opts = List.of(new Option("a", "Alpha"), new Option("b", "Beta"), new Option("c", "Gamma"));
        return new SurveyDefinition("full-survey", "Full: <b>survey</b>", "Describes it.", SurveyStatus.OPEN, true,
                true, true,
                List.of(new Reward.CommandReward("eco give {player} 500"),
                        new Reward.PlayerCommandReward("spawn"),
                        new Reward.MessageReward("<green>Thanks"),
                        new Reward.TitleReward("Thanks", "for voting"),
                        new Reward.BroadcastReward("<gold>{player} voted")),
                List.of(new Question("pick", QuestionType.SINGLE, "Pick one", opts, true, 0, 0, null),
                        new Question("text", QuestionType.TEXT, "Why?", List.of(), false, 0, 0,
                                "answer(pick) != \"a\""),
                        new Question("many", QuestionType.MULTIPLE, "Pick some", opts, false, 0, 2, null),
                        new Question("many2", QuestionType.MULTIPLE, "Pick more", opts, true, 1, 0, null),
                        new Question("yn", QuestionType.YES_NO, "Sure?", List.of(), true, 0, 0, null)));
    }

    private static SurveyDefinition withId(SurveyDefinition s, String id) {
        return new SurveyDefinition(id, s.name(), s.description(), s.status(), s.anonymous(), s.featured(),
                s.requiresPermission(), s.rewards(), s.questions());
    }

    private SurveyStore store() {
        return new SurveyStore(this.dir, Logger.getAnonymousLogger());
    }

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(SurveyStoreTest.class.getResource("/surveys/" + name).toURI());
    }

    @Test
    void roundTrip_preservesEverything() throws Exception {
        SurveyDefinition original = everything();
        BasicConfigurationNode root = BasicConfigurationNode.root(ConfigurationOptions.defaults());
        SurveyFileCodec codec = new SurveyFileCodec();
        codec.write(original, root);
        assertEquals(original, codec.read("full-survey", root));
    }

    @Test
    void roundTrip_throughDisk() throws Exception {
        SurveyStore store = store();
        store.save(everything());
        store.loadAll();
        assertEquals(everything(), store.get("full-survey").orElseThrow());
    }

    @Test
    void loadAll_skipsBrokenFileAndLoadsOthers() throws Exception {
        for (String name : List.of("good.yml", "bad-yaml.yml", "unknown-type.yml", "dup-keys.yml")) {
            Files.copy(fixture(name), this.dir.resolve(name));
        }
        SurveyStore store = store();
        LoadReport report = store.loadAll();
        assertEquals(1, report.loaded());
        assertEquals(3, report.failures().size());
        assertTrue(report.failures().get("unknown-type.yml").contains("RANKED"), report.failures().toString());
        assertTrue(report.failures().get("dup-keys.yml").toLowerCase().contains("duplicate"),
                report.failures().toString());
        assertTrue(report.failures().containsKey("bad-yaml.yml"));
        assertTrue(store.exists("good"));
        assertEquals(1, store.all().size());
    }

    @Test
    void loadAll_rejectsFileNameNotMatchingSlug() throws Exception {
        Files.copy(fixture("Bad Name.yml"), this.dir.resolve("Bad Name.yml"));
        LoadReport report = store().loadAll();
        assertEquals(0, report.loaded());
        assertTrue(report.failures().containsKey("Bad Name.yml"));
    }

    @Test
    void save_rejectsInvalidSurvey() throws Exception {
        SurveyDefinition bad = everything().withQuestions(List.of(
                new Question("pick", QuestionType.SINGLE, "Pick", List.of(new Option("a", "A")), true, 0, 0, null)));
        assertThrows(IllegalArgumentException.class, () -> store().save(bad));
        try (Stream<Path> files = Files.list(this.dir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void save_isAtomic() throws IOException {
        store().save(everything());
        try (Stream<Path> files = Files.list(this.dir)) {
            List<String> names = files.map(p -> p.getFileName().toString()).toList();
            assertEquals(List.of("full-survey.yml"), names);
        }
    }

    @Test
    void delete_removesFile() throws IOException {
        SurveyStore store = store();
        store.save(everything());
        store.delete("full-survey");
        assertFalse(store.exists("full-survey"));
        assertFalse(Files.exists(this.dir.resolve("full-survey.yml")));
    }

    @Test
    void yesNo_optionsNotWritten() throws Exception {
        store().save(everything());
        String text = Files.readString(this.dir.resolve("full-survey.yml"));
        String yesNoBlock = text.substring(text.indexOf("key: yn"));
        assertFalse(yesNoBlock.contains("options"), yesNoBlock);
        String textBlock = text.substring(text.indexOf("key: text"), text.indexOf("key: many"));
        assertFalse(textBlock.contains("options"), textBlock);
    }

    @Test
    void save_concurrentWritesKeepEverySurvey() throws Exception {
        SurveyStore store = store();
        int count = 40;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                String id = "survey-" + i;
                futures.add(pool.submit(() -> {
                    go.await();
                    store.save(withId(everything(), id));
                    // Same id from several threads at once must not trip over a shared temp file.
                    store.save(withId(everything(), "shared-one"));
                    return null;
                }));
            }
            go.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(count + 1, store.all().size());
        try (Stream<Path> files = Files.list(this.dir)) {
            assertEquals(count + 1, files.count());
        }
    }

    @Test
    void loadAll_logsOneLinePerBrokenFile() throws Exception {
        for (String name : List.of("good.yml", "bad-yaml.yml", "unknown-type.yml", "dup-keys.yml")) {
            Files.copy(fixture(name), this.dir.resolve(name));
        }
        Logger log = Logger.getLogger("pollster-store-test-" + System.nanoTime());
        log.setUseParentHandlers(false);
        List<java.util.logging.LogRecord> records = new java.util.ArrayList<>();
        log.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        new SurveyStore(this.dir, log).loadAll();

        assertEquals(3, records.size(), records.stream().map(java.util.logging.LogRecord::getMessage).toList()
                .toString());
        for (String name : List.of("bad-yaml.yml", "unknown-type.yml", "dup-keys.yml")) {
            List<String> lines = records.stream().map(java.util.logging.LogRecord::getMessage)
                    .filter(m -> m.contains(name)).toList();
            assertEquals(1, lines.size(), name);
            String line = lines.get(0);
            assertTrue(line.startsWith("Skipping survey file " + name + ": "), line);
            assertFalse(line.contains("\n") || line.contains("\r"), "one line only: " + line);
            assertEquals(java.util.logging.Level.WARNING, records.stream()
                    .filter(r -> r.getMessage().contains(name)).findFirst().orElseThrow().getLevel());
        }
    }
}
