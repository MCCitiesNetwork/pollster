package com.minecraftcitiesnetwork.pollster.store;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.Reward;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/** Converts between {@link SurveyDefinition} and its YAML node tree. */
public final class SurveyFileCodec {

    public SurveyDefinition read(String id, ConfigurationNode root) throws SerializationException {
        if (!root.isMap()) {
            throw new SerializationException("The file must contain a map of survey settings.");
        }
        String name = root.node("name").getString(id);
        String description = root.node("description").getString("");
        SurveyStatus status = readStatus(root.node("status"));

        List<Reward> rewards = new ArrayList<>();
        for (ConfigurationNode node : root.node("rewards").childrenList()) {
            rewards.add(readReward(node));
        }
        List<Question> questions = new ArrayList<>();
        for (ConfigurationNode node : root.node("questions").childrenList()) {
            questions.add(readQuestion(node));
        }
        return new SurveyDefinition(id, name, description, status,
                root.node("anonymous").getBoolean(false),
                root.node("featured").getBoolean(false),
                root.node("requires-permission").getBoolean(false),
                rewards, questions);
    }

    public void write(SurveyDefinition s, ConfigurationNode root) throws SerializationException {
        root.node("name").set(s.name());
        root.node("description").set(s.description());
        root.node("status").set(s.status().name());
        root.node("anonymous").set(s.anonymous());
        root.node("featured").set(s.featured());
        root.node("requires-permission").set(s.requiresPermission());
        ConfigurationNode rewards = root.node("rewards");
        rewards.raw(null);
        rewards.set(List.of());
        for (Reward reward : s.rewards()) {
            writeReward(reward, rewards.appendListNode());
        }
        ConfigurationNode questions = root.node("questions");
        questions.raw(null);
        questions.set(List.of());
        for (Question question : s.questions()) {
            writeQuestion(question, questions.appendListNode());
        }
    }

    private static SurveyStatus readStatus(ConfigurationNode node) throws SerializationException {
        String raw = node.getString();
        if (raw == null) {
            return SurveyStatus.DRAFT;
        }
        try {
            return SurveyStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new SerializationException("Unknown status \"" + raw + "\" (expected one of "
                    + List.of(SurveyStatus.values()) + ").");
        }
    }

    private static Question readQuestion(ConfigurationNode node) throws SerializationException {
        String key = node.node("key").getString();
        if (key == null) {
            throw new SerializationException("A question is missing its key.");
        }
        String rawType = node.node("type").getString();
        if (rawType == null) {
            throw new SerializationException("Question \"" + key + "\" is missing its type.");
        }
        QuestionType type;
        try {
            type = QuestionType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new SerializationException("Question \"" + key + "\" has unknown type \"" + rawType
                    + "\" (expected one of " + List.of(QuestionType.values()) + ").");
        }
        List<Option> options = new ArrayList<>();
        for (ConfigurationNode optionNode : node.node("options").childrenList()) {
            String optionKey = optionNode.node("key").getString();
            if (optionKey == null) {
                throw new SerializationException("Question \"" + key + "\" has an option without a key.");
            }
            options.add(new Option(optionKey, optionNode.node("label").getString(optionKey)));
        }
        boolean multiple = type == QuestionType.MULTIPLE;
        return new Question(key, type, node.node("text").getString(""), options,
                node.node("required").getBoolean(true),
                multiple ? node.node("min").getInt(1) : 0,
                multiple ? node.node("max").getInt(0) : 0,
                node.node("show-if").getString());
    }

    private static void writeQuestion(Question q, ConfigurationNode node) throws SerializationException {
        node.node("key").set(q.key());
        node.node("type").set(q.type().name());
        node.node("text").set(q.text());
        if (!q.required()) {
            node.node("required").set(false);
        }
        if (q.type() == QuestionType.MULTIPLE) {
            node.node("min").set(q.min());
            if (q.max() != 0) {
                node.node("max").set(q.max());
            }
        }
        if (q.showIf() != null) {
            node.node("show-if").set(q.showIf());
        }
        // TEXT has none and YES_NO's are fixed, so only choice questions list options.
        if (q.type() == QuestionType.SINGLE || q.type() == QuestionType.MULTIPLE) {
            ConfigurationNode options = node.node("options");
            for (Option option : q.options()) {
                ConfigurationNode entry = options.appendListNode();
                entry.node("key").set(option.key());
                entry.node("label").set(option.label());
            }
        }
    }

    private static Reward readReward(ConfigurationNode node) throws SerializationException {
        var entries = node.childrenMap();
        if (entries.size() != 1) {
            throw new SerializationException("A reward must have exactly one key, found " + entries.keySet() + ".");
        }
        String kind = String.valueOf(entries.keySet().iterator().next());
        ConfigurationNode value = entries.values().iterator().next();
        return switch (kind) {
            case "command" -> new Reward.CommandReward(requireString(kind, value));
            case "player-command" -> new Reward.PlayerCommandReward(requireString(kind, value));
            case "message" -> new Reward.MessageReward(requireString(kind, value));
            case "broadcast" -> new Reward.BroadcastReward(requireString(kind, value));
            case "title" -> new Reward.TitleReward(value.node("title").getString(""),
                    value.node("subtitle").getString(""));
            default -> throw new SerializationException("Unknown reward type \"" + kind + "\".");
        };
    }

    private static String requireString(String kind, ConfigurationNode value) throws SerializationException {
        String text = value.getString();
        if (text == null) {
            throw new SerializationException("Reward \"" + kind + "\" needs a text value.");
        }
        return text;
    }

    private static void writeReward(Reward reward, ConfigurationNode node) throws SerializationException {
        switch (reward) {
            case Reward.CommandReward r -> node.node("command").set(r.command());
            case Reward.PlayerCommandReward r -> node.node("player-command").set(r.command());
            case Reward.MessageReward r -> node.node("message").set(r.miniMessage());
            case Reward.BroadcastReward r -> node.node("broadcast").set(r.miniMessage());
            case Reward.TitleReward r -> {
                node.node("title", "title").set(r.title());
                node.node("title", "subtitle").set(r.subtitle());
            }
        }
    }
}
