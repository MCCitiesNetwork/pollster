package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Input;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.minecraftcitiesnetwork.pollster.config.MessageKeys.*;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminSupport.t;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminSupport.v;

/**
 * The options of one choice question. Changes apply to the pending question held by these screens;
 * "Save question" writes it, because a question with fewer than two options cannot be saved.
 */
public final class OptionListScreen {

    private static final int LABEL_MAX = 100;
    private static final int BULK_MAX = 4000;

    private OptionListScreen() {
    }

    public static @NotNull Screen create(@NotNull AdminUi ui, @NotNull SurveyDefinition s, @NotNull Question q) {
        String originalKey = s.question(q.key()).isPresent() ? q.key() : null;
        return create(ui, s, originalKey, q, null);
    }

    public static @NotNull Screen create(@NotNull AdminUi ui, @NotNull SurveyDefinition s,
                                         @Nullable String originalKey, @NotNull Question q,
                                         @Nullable Component error) {
        boolean draft = s.status() == SurveyStatus.DRAFT;
        Screen.Builder b = Screen.builder(ui.style().title(t(ui, EDITOR_OPTIONS_TITLE, v("text", AdminSupport.truncate(q.text(), 30)))));
        if (error != null) {
            b.body(error);
        }
        if (draft) {
            b.body(Component.text(t(ui, EDITOR_OPTIONS_HINT), NamedTextColor.GRAY));
        }
        List<Option> options = q.options();
        for (int i = 0; i < options.size(); i++) {
            int index = i;
            Option option = options.get(i);
            if (!draft) {
                b.body(Component.text(t(ui, EDITOR_COMMON_BULLET, v("key", option.key()), v("label", option.label())),
                        NamedTextColor.GRAY));
                continue;
            }
            b.button(Button.of(ui.style().edit(AdminSupport.truncate(option.key() + ": " + option.label(), 28)),
                    ctx -> ctx.open(editOption(ui, s, originalKey, q, index, null))));
            b.button(AdminSupport.narrow(ui.style().edit("▲"),
                    ctx -> ctx.open(create(ui, s, originalKey, swap(q, index, index - 1), null))));
            b.button(AdminSupport.narrow(ui.style().edit("▼"),
                    ctx -> ctx.open(create(ui, s, originalKey, swap(q, index, index + 1), null))));
            b.button(AdminSupport.narrow(ui.style().danger("✕"), ctx -> {
                List<Option> left = new ArrayList<>(options);
                left.remove(index);
                ctx.open(create(ui, s, originalKey, QuestionEdits.withOptions(q, left), null));
            }));
        }
        if (draft) {
            b.button(Button.of(ui.style().primary(t(ui, EDITOR_OPTIONS_ADD)), ctx -> ctx.open(editOption(ui, s, originalKey, q, -1, null))));
            b.button(Button.of(ui.style().primary(t(ui, EDITOR_OPTIONS_BULK)), ctx -> ctx.open(bulkAdd(ui, s, originalKey, q, null))));
            b.button(Button.of(ui.style().primary(t(ui, EDITOR_OPTIONS_SAVE_QUESTION)), ctx -> QuestionEditScreen.persist(ui, ctx, s,
                    originalKey, q, e -> ctx.open(create(ui, s, originalKey, q, e)),
                    fresh -> ctx.open(QuestionListScreen.create(ui, fresh)))));
        }
        b.button(Button.of(ui.style().secondary(t(ui, EDITOR_COMMON_BACK)),
                ctx -> ctx.open(QuestionEditScreen.create(ui, s, originalKey, q, null))));
        return b.columns(4).build();
    }

    private static Question swap(Question q, int from, int to) {
        List<Option> options = new ArrayList<>(q.options());
        if (to < 0 || to >= options.size()) {
            return q;
        }
        Option moved = options.remove(from);
        options.add(to, moved);
        return QuestionEdits.withOptions(q, options);
    }

    /** One option's key and label; {@code index} -1 adds a new option. */
    private static Screen editOption(AdminUi ui, SurveyDefinition s, @Nullable String originalKey, Question q,
                                     int index, @Nullable Component error) {
        Option current = index < 0 ? new Option("", "") : q.options().get(index);
        Screen.Builder b = Screen.builder(ui.style().title(t(ui, index < 0 ? EDITOR_OPTIONS_ADD : EDITOR_OPTIONS_EDIT_TITLE)));
        if (error != null) {
            b.body(error);
        }
        return b.input(new Input.TextInput("key", Component.text(t(ui, EDITOR_OPTIONS_KEY_INPUT)), current.key(),
                        SurveyIds.MAX_LENGTH, false))
                .input(new Input.TextInput("label", Component.text(t(ui, EDITOR_OPTIONS_LABEL_INPUT)), current.label(), LABEL_MAX, false))
                .button(Button.of(ui.style().primary(t(ui, EDITOR_COMMON_SAVE)), ctx -> {
                    String label = ctx.text("label") == null ? "" : ctx.text("label").strip();
                    String key = ctx.text("key") == null ? "" : ctx.text("key").strip();
                    if (label.isEmpty()) {
                        ctx.open(editOption(ui, s, originalKey, q, index, ui.style().error(t(ui, EDITOR_OPTIONS_LABEL_REQUIRED))));
                        return;
                    }
                    if (key.isEmpty()) {
                        key = SurveyIds.slugify(label);
                    }
                    if (!SurveyIds.isValidKey(key)) {
                        ctx.open(editOption(ui, s, originalKey, q, index, ui.style()
                                .error(t(ui, EDITOR_OPTIONS_KEY_INVALID))));
                        return;
                    }
                    for (int i = 0; i < q.options().size(); i++) {
                        if (i != index && q.options().get(i).key().equals(key)) {
                            ctx.open(editOption(ui, s, originalKey, q, index,
                                    ui.style().error(t(ui, EDITOR_OPTIONS_KEY_TAKEN, v("key", key)))));
                            return;
                        }
                    }
                    List<Option> options = new ArrayList<>(q.options());
                    Option saved = new Option(key, label);
                    if (index < 0) {
                        options.add(saved);
                    } else {
                        options.set(index, saved);
                    }
                    ctx.open(create(ui, s, originalKey, QuestionEdits.withOptions(q, options), null));
                }))
                .button(Button.of(ui.style().secondary(ui.style().word(UI_BUTTON_CANCEL)), ctx -> ctx.open(create(ui, s, originalKey, q, null))))
                .columns(2)
                .build();
    }

    private static Screen bulkAdd(AdminUi ui, SurveyDefinition s, @Nullable String originalKey, Question q,
                                  @Nullable Component error) {
        Screen.Builder b = Screen.builder(ui.style().title(t(ui, EDITOR_OPTIONS_BULK_TITLE)));
        b.body(Component.text(t(ui, EDITOR_OPTIONS_BULK_HINT), NamedTextColor.GRAY));
        if (error != null) {
            b.body(error);
        }
        return b.input(new Input.TextInput("options", Component.text(t(ui, EDITOR_LABEL_OPTIONS)), "", BULK_MAX, true))
                .button(Button.of(ui.style().primary(t(ui, EDITOR_OPTIONS_BULK_SUBMIT)), ctx -> {
                    List<Option> parsed = QuestionEdits.bulkParse(ctx.text("options") == null ? "" : ctx.text("options"));
                    if (parsed.isEmpty()) {
                        ctx.open(bulkAdd(ui, s, originalKey, q, ui.style().error(t(ui, EDITOR_OPTIONS_BULK_EMPTY))));
                        return;
                    }
                    // Keys are made unique against the options already on the question as well.
                    Set<String> used = new HashSet<>();
                    q.options().forEach(o -> used.add(o.key()));
                    List<Option> options = new ArrayList<>(q.options());
                    for (Option option : parsed) {
                        options.add(new Option(QuestionEdits.uniqueKey(option.key(), used), option.label()));
                    }
                    ctx.open(create(ui, s, originalKey, QuestionEdits.withOptions(q, options), null));
                }))
                .button(Button.of(ui.style().secondary(ui.style().word(UI_BUTTON_CANCEL)), ctx -> ctx.open(create(ui, s, originalKey, q, null))))
                .columns(2)
                .build();
    }
}
