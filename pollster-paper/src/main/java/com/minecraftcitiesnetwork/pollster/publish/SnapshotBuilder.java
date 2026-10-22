package com.minecraftcitiesnetwork.pollster.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.report.OptionResult;
import com.minecraftcitiesnetwork.pollster.api.report.QuestionReport;
import com.minecraftcitiesnetwork.pollster.api.report.SurveyReport;
import com.minecraftcitiesnetwork.pollster.api.report.TextAnswer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

/** Turns a {@link SurveyReport} into the snapshot JSON that the web viewer reads ({@code format: 1}). */
public final class SnapshotBuilder {

    public static final int FORMAT = 1;

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final Clock clock;
    private final RandomGenerator random;
    private final ObjectMapper mapper = new ObjectMapper();

    public SnapshotBuilder(@NotNull Clock clock, @NotNull RandomGenerator random) {
        this.clock = clock;
        this.random = random;
    }

    public @NotNull ObjectNode build(@NotNull SurveyReport report, @Nullable Instant openedAt,
                                     @Nullable Instant closedAt) {
        ObjectNode root = NODES.objectNode();
        root.put("format", FORMAT);
        root.put("generatedAt", this.clock.instant().toString());

        ObjectNode survey = root.putObject("survey");
        survey.put("id", report.survey().id());
        survey.put("name", plain(report.survey().name()));
        survey.put("description", plain(report.survey().description()));
        survey.put("anonymous", report.survey().anonymous());
        putInstant(survey, "openedAt", openedAt);
        putInstant(survey, "closedAt", closedAt);

        ObjectNode totals = root.putObject("totals");
        totals.put("participants", report.participants());
        totals.put("averageSeconds", report.averageSeconds().isPresent()
                ? Math.round(report.averageSeconds().getAsDouble()) : 0L);

        ArrayNode questions = root.putArray("questions");
        for (QuestionReport questionReport : report.questions()) {
            questions.add(question(questionReport, report));
        }
        return root;
    }

    /** Compact UTF-8 JSON, for upload and export. */
    public byte[] toBytes(@NotNull ObjectNode snapshot) {
        try {
            return this.mapper.writeValueAsBytes(snapshot);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // An ObjectNode of plain values always serialises; anything else is a programming error.
            throw new IllegalStateException("Snapshot could not be serialised", e);
        }
    }

    private ObjectNode question(QuestionReport report, SurveyReport surveyReport) {
        Question question = report.question();
        ObjectNode node = NODES.objectNode();
        node.put("key", question.key());
        node.put("type", question.type().name());
        node.put("text", plain(question.text()));
        node.put("shownTo", report.shownTo());
        node.put("skipped", report.skipped());
        if (question.type() == QuestionType.TEXT) {
            node.set("answers", answers(report.texts(), surveyReport));
            return node;
        }
        // YES_NO serialises like SINGLE: the viewer reads the yes and no options as ordinary options.
        ArrayNode options = node.putArray("options");
        for (OptionResult option : report.options()) {
            ObjectNode optionNode = options.addObject();
            optionNode.put("key", option.key());
            optionNode.put("label", plain(option.label()));
            optionNode.put("votes", option.votes());
        }
        if (question.type() == QuestionType.MULTIPLE) {
            ObjectNode selections = node.putObject("selectionsPerRespondent");
            for (Map.Entry<Integer, Integer> entry : new TreeMap<>(report.selectionsPerRespondent()).entrySet()) {
                selections.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return node;
    }

    private ArrayNode answers(List<TextAnswer> texts, SurveyReport report) {
        boolean anonymous = report.survey().anonymous();
        List<TextAnswer> ordered = new ArrayList<>(texts);
        if (anonymous) {
            // Submission order would let a reader match answers to the order players were seen online.
            Collections.shuffle(ordered, this.random);
        }
        ArrayNode answers = NODES.arrayNode();
        for (TextAnswer answer : ordered) {
            ObjectNode node = answers.addObject();
            node.put("text", answer.text());
            String name = anonymous ? null : report.playerNames().get(answer.player());
            if (name == null) {
                node.putNull("player");
            } else {
                node.put("player", name);
            }
        }
        return answers;
    }

    private static void putInstant(ObjectNode node, String field, @Nullable Instant value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value.toString());
        }
    }

    /** Admin-written text is MiniMessage; the web page shows it without markup. */
    static String plain(String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
