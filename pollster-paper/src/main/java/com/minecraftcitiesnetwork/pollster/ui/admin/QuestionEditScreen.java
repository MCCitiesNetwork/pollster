package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.condition.ConditionParseException;
import com.minecraftcitiesnetwork.pollster.api.condition.ConditionParser;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Input;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.StaffText;
import com.minecraftcitiesnetwork.pollster.ui.common.ConfirmScreen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Edits one question. The form is held in memory until Save: a question that is not valid yet (a
 * choice question with no options) cannot be written to the file, so the option screens work on the
 * pending question too and Save persists it.
 */
public final class QuestionEditScreen {

    private static final int KEY_MAX = SurveyIds.MAX_LENGTH;
    private static final int TEXT_MAX = 300;
    private static final int CONDITION_MAX = 500;

    private QuestionEditScreen() {
    }

    public static @NotNull Screen create(@NotNull AdminUi ui, @NotNull SurveyDefinition s, @NotNull Question q,
                                         @Nullable Component error) {
        // A question that is already in the file is edited in place; anything else is a new question.
        String originalKey = s.question(q.key()).isPresent() ? q.key() : null;
        return create(ui, s, originalKey, q, error);
    }

    /**
     * @param originalKey the key the question has in the file, or null for a question not saved yet;
     *                    it differs from {@code q.key()} while the admin is renaming
     */
    public static @NotNull Screen create(@NotNull AdminUi ui, @NotNull SurveyDefinition s,
                                         @Nullable String originalKey, @NotNull Question q,
                                         @Nullable Component error) {
        if (s.status() != SurveyStatus.DRAFT) {
            return readOnly(ui, s, q);
        }
        Screen.Builder b = Screen.builder(ui.style().title("Question: " + AdminSupport.truncate(q.key(), 30)));
        if (error != null) {
            b.body(error);
        }
        b.input(new Input.TextInput("key", Component.text("Key (a-z, 0-9, -)"), q.key(), KEY_MAX, false));
        b.input(new Input.TextInput("text", Component.text("Question text"), q.text(), TEXT_MAX, false));
        b.input(new Input.ChoiceInput("type", Component.text("Type"), typeOptions(), q.type().name()));
        b.input(new Input.BoolInput("required", Component.text("Required"), q.required()));
        int count = q.options().size();
        if (q.type() == QuestionType.MULTIPLE && count > 0) {
            b.input(new Input.RangeInput("min", Component.text("Minimum selections"), 0, count, 1,
                    Math.min(q.min(), count)));
            b.input(new Input.RangeInput("max", Component.text("Maximum selections"), 0, count, 1,
                    q.effectiveMax()));
        }
        b.input(new Input.TextInput("condition", Component.text("Show only if (condition)"),
                q.showIf() == null ? "" : q.showIf(), CONDITION_MAX, false));

        if (q.type() == QuestionType.SINGLE || q.type() == QuestionType.MULTIPLE) {
            b.button(Button.of(AdminSupport.valued(ui, "Options", "(" + count + ")"),
                    ctx -> openOptions(ui, ctx, s, originalKey, q)));
        }
        b.button(Button.of(ui.style().primary("Save"), ctx -> save(ui, ctx, s, originalKey, q)));
        if (originalKey != null) {
            b.button(Button.of(ui.style().edit("Move ▲"), ctx -> move(ui, ctx, s, originalKey, -1)));
            b.button(Button.of(ui.style().edit("Move ▼"), ctx -> move(ui, ctx, s, originalKey, 1)));
            b.button(Button.of(ui.style().danger("Delete question"), ctx -> ctx.open(ConfirmScreen.create(
                    ui.style(), "Delete question?",
                    Component.text("Delete " + originalKey + "?", NamedTextColor.GRAY),
                    c -> delete(ui, c, s, originalKey, q), c -> c.open(create(ui, s, originalKey, q, null))))));
        }
        b.button(Button.of(ui.style().secondary("Back"), ctx -> ctx.open(QuestionListScreen.create(ui, s))));
        return b.columns(2).build();
    }

    private static Screen readOnly(AdminUi ui, SurveyDefinition s, Question q) {
        Screen.Builder b = Screen.builder(ui.style().title("Question: " + AdminSupport.truncate(q.key(), 30)));
        b.body(Component.text("Questions are locked once a survey has been opened.", NamedTextColor.GRAY));
        b.body(ui.style().label("Key", Component.text(q.key())));
        b.body(ui.style().label("Text", StaffText.render(q.text())));
        b.body(ui.style().label("Type", Component.text(q.type().name())));
        b.body(ui.style().label("Required", Component.text(q.required() ? "yes" : "no")));
        if (q.type() == QuestionType.MULTIPLE) {
            b.body(ui.style().label("Selections", Component.text(q.min() + " to " + q.effectiveMax())));
        }
        for (Option option : q.options()) {
            b.body(Component.text("• " + option.key() + ": ", NamedTextColor.GRAY)
                    .append(StaffText.render(option.label())));
        }
        b.body(ui.style().label("Condition", Component.text(q.showIf() == null ? "none" : q.showIf())));
        b.button(Button.of(ui.style().secondary("Back"), ctx -> ctx.open(QuestionListScreen.create(ui, s))));
        return b.build();
    }

    private static List<Map.Entry<String, Component>> typeOptions() {
        List<Map.Entry<String, Component>> options = new ArrayList<>();
        for (QuestionType type : QuestionType.values()) {
            options.add(Map.entry(type.name(), Component.text(type.name())));
        }
        return options;
    }

    /** The question as the form now shows it; options and unsubmitted values come from {@code base}. */
    private static Question readForm(ScreenContext ctx, Question base) {
        String key = strip(ctx.text("key"), base.key());
        String text = strip(ctx.text("text"), base.text());
        QuestionType type = base.type();
        String typeName = ctx.text("type");
        if (typeName != null) {
            try {
                type = QuestionType.valueOf(typeName);
            } catch (IllegalArgumentException ignored) {
                // Keep the current type: the client sent something this dialog never offered.
            }
        }
        Boolean required = ctx.bool("required");
        String condition = ctx.text("condition");
        String showIf = condition == null ? base.showIf() : (condition.isBlank() ? null : condition.strip());

        boolean choice = type == QuestionType.SINGLE || type == QuestionType.MULTIPLE;
        List<Option> options = choice ? base.options() : List.of();
        int min = 0;
        int max = 0;
        if (type == QuestionType.MULTIPLE) {
            min = ctx.number("min") != null ? Math.round(ctx.number("min")) : base.min();
            max = ctx.number("max") != null ? Math.round(ctx.number("max")) : base.max();
            max = Math.min(max, options.size());
        }
        return new Question(key, type, text, options, required == null ? base.required() : required,
                min, max, showIf);
    }

    private static String strip(@Nullable String value, String fallback) {
        return value == null ? fallback : value.strip();
    }

    private static void openOptions(AdminUi ui, ScreenContext ctx, SurveyDefinition s, @Nullable String originalKey,
                                    Question base) {
        Question pending = readForm(ctx, base);
        if (pending.type() == QuestionType.SINGLE || pending.type() == QuestionType.MULTIPLE) {
            ctx.open(OptionListScreen.create(ui, s, originalKey, pending, null));
        } else {
            ctx.open(create(ui, s, originalKey, pending, ui.style().error(
                    "Options only apply to single and multiple choice questions. Change the type and save first.")));
        }
    }

    private static void save(AdminUi ui, ScreenContext ctx, SurveyDefinition s, @Nullable String originalKey,
                             Question base) {
        Question q = readForm(ctx, base);
        Consumer<Component> back = error -> ctx.open(create(ui, s, originalKey, q, error));
        if (q.showIf() != null) {
            try {
                ConditionParser.parse(q.showIf());
            } catch (ConditionParseException e) {
                // The exception text carries the column, which is what lets the admin find the typo.
                back.accept(ui.style().error("Condition: " + e.getMessage()));
                return;
            }
        }
        boolean choice = q.type() == QuestionType.SINGLE || q.type() == QuestionType.MULTIPLE;
        if (choice && q.options().isEmpty()) {
            ctx.open(OptionListScreen.create(ui, s, originalKey, q, null));
            return;
        }
        persist(ui, ctx, s, originalKey, q, back, fresh -> ctx.open(QuestionListScreen.create(ui, fresh)));
    }

    /**
     * Applies the question to the survey as it is in the file now, saves it, and calls {@code onSaved}
     * with the saved survey. A refusal or a failed save goes to {@code onError} as an inline message.
     */
    static void persist(AdminUi ui, ScreenContext ctx, SurveyDefinition s, @Nullable String originalKey, Question q,
                        Consumer<Component> onError, Consumer<SurveyDefinition> onSaved) {
        SurveyDefinition base = ui.service().survey(s.id()).orElse(s);
        QuestionEdits.EditResult result = originalKey == null ? QuestionEdits.add(base, q)
                : QuestionEdits.replace(base, originalKey, q);
        saveResult(ui, ctx, result, onError, onSaved);
    }

    private static void saveResult(AdminUi ui, ScreenContext ctx, QuestionEdits.EditResult result,
                                   Consumer<Component> onError, Consumer<SurveyDefinition> onSaved) {
        switch (result) {
            case QuestionEdits.Rejected rejected -> onError.accept(AdminSupport.rejection(ui, rejected));
            case QuestionEdits.Ok ok -> {
                ctx.open(AdminSupport.loading(ui));
                ui.service().save(ok.s()).whenComplete((ignored, error) -> {
                    if (error != null) {
                        onError.accept(ui.style().error(HubScreen.reason(error)));
                        return;
                    }
                    onSaved.accept(ui.service().survey(ok.s().id()).orElse(ok.s()));
                });
            }
        }
    }

    private static void move(AdminUi ui, ScreenContext ctx, SurveyDefinition s, String key, int delta) {
        SurveyDefinition base = ui.service().survey(s.id()).orElse(s);
        Consumer<Component> back = error -> base.question(key)
                .ifPresent(q -> ctx.open(create(ui, base, key, q, error)));
        saveResult(ui, ctx, QuestionEdits.move(base, key, delta), back,
                fresh -> fresh.question(key).ifPresent(q -> ctx.open(create(ui, fresh, key, q, null))));
    }

    private static void delete(AdminUi ui, ScreenContext ctx, SurveyDefinition s, String key, Question q) {
        SurveyDefinition base = ui.service().survey(s.id()).orElse(s);
        saveResult(ui, ctx, QuestionEdits.remove(base, key),
                error -> ctx.open(create(ui, base, key, q, error)),
                fresh -> ctx.open(QuestionListScreen.create(ui, fresh)));
    }
}
