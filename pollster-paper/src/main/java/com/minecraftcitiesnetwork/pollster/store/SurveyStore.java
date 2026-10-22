package com.minecraftcitiesnetwork.pollster.store;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import com.minecraftcitiesnetwork.pollster.api.validation.SurveyValidator;
import com.minecraftcitiesnetwork.pollster.api.validation.ValidationProblem;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/** Survey definitions kept as one YAML file each; the files are the source of truth. */
public final class SurveyStore {

    private static final String EXTENSION = ".yml";

    private final Path surveysDir;
    private final Logger log;
    private final SurveyFileCodec codec = new SurveyFileCodec();
    // Replaced wholesale so readers never see a half-loaded set. Writers (save, delete, loadAll)
    // are synchronized: they run on a multi-thread pool, and two unsynchronized copy-and-swap
    // updates would drop one survey from memory.
    private volatile Map<String, SurveyDefinition> surveys = Map.of();

    public SurveyStore(Path surveysDir, Logger log) {
        this.surveysDir = surveysDir;
        this.log = log;
    }

    public synchronized LoadReport loadAll() {
        Map<String, SurveyDefinition> loaded = new LinkedHashMap<>();
        Map<String, String> failures = new LinkedHashMap<>();
        for (Path file : listFiles()) {
            String fileName = file.getFileName().toString();
            try {
                SurveyDefinition survey = readFile(file);
                loaded.put(survey.id(), survey);
            } catch (Exception e) {
                // One broken hand-edited file must never stop the others from loading.
                String reason = describe(e);
                failures.put(fileName, reason);
                this.log.warning("Skipping survey file " + fileName + ": " + reason);
            }
        }
        this.surveys = Map.copyOf(loaded);
        return new LoadReport(loaded.size(), failures);
    }

    private List<Path> listFiles() {
        if (!Files.isDirectory(this.surveysDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(this.surveysDir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(EXTENSION)).sorted().toList();
        } catch (IOException e) {
            this.log.warning("Could not list the surveys folder: " + e.getMessage());
            return List.of();
        }
    }

    private SurveyDefinition readFile(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String id = fileName.substring(0, fileName.length() - EXTENSION.length());
        if (!SurveyIds.isValidSlug(id)) {
            throw new IllegalArgumentException(
                    "the file name must be a survey id of 3 to 48 characters (a-z, 0-9 and '-')");
        }
        Optional<String> duplicate = DuplicateKeyScanner.findDuplicate(Files.readAllLines(file));
        if (duplicate.isPresent()) {
            throw new IllegalArgumentException(duplicate.get());
        }
        SurveyDefinition survey = this.codec.read(id, loader(file).load());
        List<ValidationProblem> problems = SurveyValidator.validate(survey);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(join(problems));
        }
        return survey;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        // Configurate wraps parser errors in a generic message; the cause says what is actually wrong.
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            message = "invalid YAML: " + withoutEmptyPath(e.getCause().getMessage());
        }
        message = withoutEmptyPath(message);
        // Configurate messages can span several lines (with a source excerpt); keep the log to one line.
        return message.replaceAll("\\s*\\R\\s*", " ").trim();
    }

    /** Configurate prefixes errors with the node path; at the root that is a meaningless "[]: ". */
    private static String withoutEmptyPath(String message) {
        return message.startsWith("[]: ") ? message.substring(4) : message;
    }

    private static YamlConfigurationLoader loader(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }

    private static String join(List<ValidationProblem> problems) {
        return problems.stream()
                .map(p -> p.questionKey() == null ? p.message()
                        : "Question \"" + p.questionKey() + "\": " + p.message())
                .collect(Collectors.joining("; "));
    }

    public Optional<SurveyDefinition> get(String id) {
        return Optional.ofNullable(this.surveys.get(id));
    }

    public Collection<SurveyDefinition> all() {
        return this.surveys.values().stream().sorted(Comparator.comparing(SurveyDefinition::id)).toList();
    }

    public boolean exists(String id) {
        return this.surveys.containsKey(id);
    }

    public synchronized void save(SurveyDefinition s) throws IOException {
        List<ValidationProblem> problems = SurveyValidator.validate(s);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(join(problems));
        }
        Files.createDirectories(this.surveysDir);
        Path target = this.surveysDir.resolve(s.id() + EXTENSION);
        // A unique temp name per write, so no two writes can ever share or delete each other's file.
        // It does not end in .yml, so loadAll never reads a leftover one. (Files.createTempFile is
        // avoided because it makes the file owner-only, which the moved survey file would keep.)
        Path temp = this.surveysDir.resolve(s.id() + EXTENSION + "." + UUID.randomUUID() + ".tmp");
        try {
            YamlConfigurationLoader loader = loader(temp);
            ConfigurationNode root = loader.createNode();
            this.codec.write(s, root);
            loader.save(root);
            // Write to a temp file then move, so a crash mid-write cannot leave a half-written survey.
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (ConfigurateException e) {
            throw new IOException("Could not write survey " + s.id() + ": " + e.getMessage(), e);
        } finally {
            Files.deleteIfExists(temp);
        }
        Map<String, SurveyDefinition> copy = new LinkedHashMap<>(this.surveys);
        copy.put(s.id(), s);
        this.surveys = Map.copyOf(copy);
    }

    public synchronized void delete(String id) throws IOException {
        Files.deleteIfExists(this.surveysDir.resolve(id + EXTENSION));
        Map<String, SurveyDefinition> copy = new LinkedHashMap<>(this.surveys);
        copy.remove(id);
        this.surveys = Map.copyOf(copy);
    }
}
