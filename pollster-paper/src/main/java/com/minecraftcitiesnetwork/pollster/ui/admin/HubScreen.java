package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.SurveyService.SurveyLock;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.command.ReportCommands;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Input;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.StaffText;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.Screens;
import com.minecraftcitiesnetwork.pollster.ui.common.ConfirmScreen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.minecraftcitiesnetwork.pollster.config.MessageKeys.*;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminSupport.t;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminSupport.v;

/** One survey's settings and actions, each button showing its current value. */
public final class HubScreen {

    private static final int NAME_MAX = 100;
    private static final int DESCRIPTION_MAX = 500;

    private HubScreen() {
    }

    /** Shows the loading notice, then a fresh hub built from the survey's current file and response count. */
    public static void open(@NotNull AdminUi ui, @NotNull ScreenContext ctx, @NotNull String surveyId) {
        ctx.open(AdminSupport.loading(ui));
        show(ui, ctx, surveyId);
    }

    private static void show(AdminUi ui, ScreenContext ctx, String id) {
        Optional<SurveyDefinition> survey = ui.service().survey(id);
        if (survey.isEmpty()) {
            ctx.open(AdminSupport.unavailable(ui, c -> AdminListScreen.open(ui, c)));
            return;
        }
        ui.service().responseCounts().whenComplete((counts, error) -> {
            if (error != null) {
                ctx.open(AdminSupport.failure(ui, error, c -> AdminListScreen.open(ui, c)));
                return;
            }
            ctx.open(create(ui, survey.get(), ui.service().lock(id), counts.getOrDefault(id, 0)));
        });
    }

    public static @NotNull Screen create(@NotNull AdminUi ui, @NotNull SurveyDefinition s, @NotNull SurveyLock lock,
                                         int responses) {
        String id = s.id();
        boolean draft = s.status() == SurveyStatus.DRAFT;
        // The template is plain text, but the survey name is staff-authored MiniMessage, so split
        // the template at a marker and render the name with the restricted renderer in between.
        String[] around = t(ui, EDITOR_HUB_TITLE, v("name", "\u0001")).split("\u0001", 2);
        Component title = Component.text(around[0])
                .append(StaffText.render(AdminSupport.truncate(s.name(), 40)))
                .append(Component.text(around.length > 1 ? around[1] : ""));
        Screen.Builder b = Screen.builder(ui.style().title(title));
        b.body(ui.style().label(t(ui, EDITOR_LABEL_ID), Component.text(id)));
        b.body(ui.style().label(t(ui, EDITOR_LABEL_STATUS), Component.text(s.status().name())));
        b.body(ui.style().label(t(ui, EDITOR_LABEL_RESPONSES), Component.text(responses)));
        b.body(ui.style().label(t(ui, EDITOR_LABEL_QUESTIONS), Component.text(s.questions().size())));
        b.body(ui.style().label(t(ui, EDITOR_LABEL_ANONYMOUS), Component.text(yesNo(ui, s.anonymous()))));
        b.body(ui.style().label(t(ui, EDITOR_LABEL_FEATURED), Component.text(yesNo(ui, s.featured()))));
        b.body(ui.style().label(t(ui, EDITOR_LABEL_REQUIRES_PERMISSION), Component.text(yesNo(ui, s.requiresPermission()))));
        b.body(ui.style().label(t(ui, EDITOR_LABEL_REWARDS), Component.text(s.rewards().size())));
        if (!draft && lock != SurveyLock.OK) {
            b.body(Component.text(t(ui, EDITOR_HUB_LOCKED_WARNING), NamedTextColor.RED));
        }

        // A closed survey cannot change at all, and anonymity only changes in a draft. Those fields
        // stay clickable (they explain why, or open read-only) but are drawn gray so they do not look active.
        boolean editable = s.status() != SurveyStatus.CLOSED;
        b.button(Button.of(AdminSupport.valued(ui, t(ui, EDITOR_LABEL_NAME), s.name(), editable), ctx -> editName(ui, ctx, id)));
        b.button(Button.of(AdminSupport.valued(ui, t(ui, EDITOR_LABEL_DESCRIPTION), s.description(), editable),
                ctx -> editDescription(ui, ctx, id)));
        b.button(Button.of(AdminSupport.valued(ui, t(ui, EDITOR_LABEL_QUESTIONS), "(" + s.questions().size() + ")", editable),
                ctx -> current(ui, ctx, id, fresh -> ctx.open(QuestionListScreen.create(ui, fresh)))));
        b.button(Button.of(AdminSupport.valued(ui, t(ui, EDITOR_LABEL_REWARDS), "(" + s.rewards().size() + ")", editable),
                ctx -> current(ui, ctx, id, fresh -> ctx.open(RewardsScreen.create(ui, fresh)))));
        b.button(Button.of(AdminSupport.valued(ui, t(ui, EDITOR_LABEL_ANONYMOUS), yesNo(ui, s.anonymous()), draft), ctx -> toggle(ui, ctx, id,
                true, fresh -> copy(fresh, fresh.name(), fresh.description(), !fresh.anonymous(),
                        fresh.featured(), fresh.requiresPermission()))));
        b.button(Button.of(AdminSupport.valued(ui, t(ui, EDITOR_LABEL_FEATURED), yesNo(ui, s.featured()), editable), ctx -> toggle(ui, ctx, id,
                false, fresh -> copy(fresh, fresh.name(), fresh.description(), fresh.anonymous(),
                        !fresh.featured(), fresh.requiresPermission()))));
        b.button(Button.of(AdminSupport.valued(ui, t(ui, EDITOR_LABEL_REQUIRES_PERMISSION), yesNo(ui, s.requiresPermission()), editable),
                ctx -> toggle(ui, ctx, id, false, fresh -> copy(fresh, fresh.name(), fresh.description(),
                        fresh.anonymous(), fresh.featured(), !fresh.requiresPermission()))));
        b.button(Button.of(ui.style().edit(t(ui, EDITOR_HUB_PREVIEW)), ctx -> preview(ui, ctx, id)));
        b.button(Button.of(ui.style().edit(t(ui, EDITOR_HUB_REPORT)), ctx -> openReport(ui, ctx, id)));
        b.button(switch (s.status()) {
            case DRAFT -> Button.of(ui.style().primary(t(ui, EDITOR_HUB_OPEN)), ctx -> openSurvey(ui, ctx, id));
            case OPEN -> Button.of(ui.style().danger(t(ui, EDITOR_HUB_CLOSE)), ctx -> closeSurvey(ui, ctx, id));
            case CLOSED -> Button.of(ui.style().primary(t(ui, EDITOR_HUB_REOPEN)), ctx -> reopenSurvey(ui, ctx, id));
        });
        b.button(Button.of(ui.style().edit(t(ui, EDITOR_HUB_DUPLICATE)), ctx -> ctx.open(duplicate(ui, id, null))));
        b.button(Button.of(ui.style().danger(t(ui, EDITOR_HUB_DELETE)), ctx -> deleteFlow(ui, ctx, id, responses)));
        b.button(AdminListScreen.back(ui));
        return b.columns(2).build();
    }

    // --- actions ---------------------------------------------------------------------------

    /** The editor needs pollster.admin, but reports have their own node, as the command does. */
    private static void openReport(AdminUi ui, ScreenContext ctx, String id) {
        if (!ctx.player().hasPermission(ReportCommands.PERMISSION)) {
            ctx.open(Screens.error(ui.style(), ui.messages().messageFor(MessageKeys.ERRORS_NO_PERMISSION),
                    List.of(), back -> open(ui, back, id)));
            return;
        }
        ui.report().open(ctx.player(), id);
    }

    private static void current(AdminUi ui, ScreenContext ctx, String id, Consumer<SurveyDefinition> action) {
        ui.service().survey(id).ifPresentOrElse(action,
                () -> ctx.open(AdminSupport.unavailable(ui, c -> AdminListScreen.open(ui, c))));
    }

    private static void toggle(AdminUi ui, ScreenContext ctx, String id, boolean draftOnly,
                               Function<SurveyDefinition, SurveyDefinition> change) {
        current(ui, ctx, id, fresh -> {
            boolean blocked = draftOnly ? fresh.status() != SurveyStatus.DRAFT : fresh.status() == SurveyStatus.CLOSED;
            if (blocked) {
                String key = draftOnly ? MessageKeys.EDITOR_ANONYMOUS_LOCKED : MessageKeys.EDITOR_READ_ONLY;
                ctx.open(AdminSupport.notice(ui, t(ui, EDITOR_COMMON_SURVEY), ui.messages().messageFor(key), c -> open(ui, c, id)));
                return;
            }
            save(ui, ctx, id, change.apply(fresh), error ->
                    ctx.open(AdminSupport.failure(ui, error, c -> open(ui, c, id))));
        });
    }

    /** Saves with the loading notice first, then reopens a fresh hub; {@code onFail} decides what a failure shows. */
    private static void save(AdminUi ui, ScreenContext ctx, String id, SurveyDefinition updated,
                             Consumer<Throwable> onFail) {
        ctx.open(AdminSupport.loading(ui));
        ui.service().save(updated).whenComplete((ignored, error) -> {
            if (error != null) {
                onFail.accept(error);
                return;
            }
            show(ui, ctx, id);
        });
    }

    private static void editName(AdminUi ui, ScreenContext ctx, String id) {
        current(ui, ctx, id, fresh -> {
            if (fresh.status() == SurveyStatus.CLOSED) {
                ctx.open(AdminSupport.notice(ui, t(ui, EDITOR_COMMON_SURVEY), ui.messages().messageFor(MessageKeys.EDITOR_READ_ONLY),
                        c -> open(ui, c, id)));
                return;
            }
            ctx.open(textEdit(ui, id, t(ui, EDITOR_LABEL_NAME), fresh.name(), false, NAME_MAX, null, true,
                    value -> copy(fresh, value, fresh.description(), fresh.anonymous(), fresh.featured(),
                            fresh.requiresPermission())));
        });
    }

    private static void editDescription(AdminUi ui, ScreenContext ctx, String id) {
        current(ui, ctx, id, fresh -> {
            if (fresh.status() == SurveyStatus.CLOSED) {
                ctx.open(AdminSupport.notice(ui, t(ui, EDITOR_COMMON_SURVEY), ui.messages().messageFor(MessageKeys.EDITOR_READ_ONLY),
                        c -> open(ui, c, id)));
                return;
            }
            ctx.open(textEdit(ui, id, t(ui, EDITOR_LABEL_DESCRIPTION), fresh.description(), true, DESCRIPTION_MAX, null, false,
                    value -> copy(fresh, fresh.name(), value, fresh.anonymous(), fresh.featured(),
                            fresh.requiresPermission())));
        });
    }

    /** A one-input screen. A refused save reopens it with the problem shown above the input. */
    private static Screen textEdit(AdminUi ui, String id, String field, String initial, boolean multiline, int max,
                                   @Nullable Component error, boolean required,
                                   Function<String, SurveyDefinition> apply) {
        Screen.Builder b = Screen.builder(ui.style().title(t(ui, EDITOR_HUB_EDIT_TITLE, v("field", field.toLowerCase()))));
        if (error != null) {
            b.body(error);
        }
        return b.input(new Input.TextInput("value", Component.text(field), initial, max, multiline))
                .button(Button.of(ui.style().primary(t(ui, EDITOR_COMMON_SAVE)), ctx -> {
                    String value = ctx.text("value") == null ? "" : ctx.text("value").strip();
                    if (required && value.isEmpty()) {
                        ctx.open(textEdit(ui, id, field, initial, multiline, max,
                                ui.style().error(t(ui, EDITOR_HUB_FIELD_EMPTY, v("field", field))), required, apply));
                        return;
                    }
                    save(ui, ctx, id, apply.apply(value), problem -> ctx.open(textEdit(ui, id, field, value,
                            multiline, max, AdminSupport.reason(ui, problem), required, apply)));
                }))
                .button(Button.of(ui.style().secondary(ui.style().word(UI_BUTTON_CANCEL)), ctx -> open(ui, ctx, id)))
                .columns(2)
                .build();
    }

    private static void preview(AdminUi ui, ScreenContext ctx, String id) {
        current(ui, ctx, id, fresh -> {
            var problems = AdminSupport.problems(ui, fresh);
            if (!problems.isEmpty()) {
                ctx.open(AdminSupport.problemsScreen(ui, problems, c -> open(ui, c, id)));
                return;
            }
            ui.preview().preview(ctx.player(), fresh);
        });
    }

    private static void openSurvey(AdminUi ui, ScreenContext ctx, String id) {
        current(ui, ctx, id, fresh -> {
            var problems = AdminSupport.problems(ui, fresh);
            if (!problems.isEmpty()) {
                ctx.open(AdminSupport.problemsScreen(ui, problems, c -> open(ui, c, id)));
                return;
            }
            ctx.open(ConfirmScreen.create(ui.style(), t(ui, EDITOR_HUB_OPEN_TITLE),
                    ui.messages().messageFor(MessageKeys.EDITOR_OPEN_WARNING), c -> {
                        c.open(AdminSupport.loading(ui));
                        ui.service().open(id).whenComplete((result, error) -> {
                            if (error != null) {
                                c.open(AdminSupport.failure(ui, error, d -> open(ui, d, id)));
                                return;
                            }
                            show(ui, c, id);
                        });
                    }, c -> open(ui, c, id)));
        });
    }

    private static void closeSurvey(AdminUi ui, ScreenContext ctx, String id) {
        ctx.open(AdminSupport.loading(ui));
        ui.service().close(id).whenComplete((ignored, error) -> {
            if (error != null) {
                ctx.open(AdminSupport.failure(ui, error, c -> open(ui, c, id)));
                return;
            }
            // Players mid-survey must not be able to submit into a closed survey.
            ui.endSessions().accept(id);
            show(ui, ctx, id);
        });
    }

    private static void reopenSurvey(AdminUi ui, ScreenContext ctx, String id) {
        ctx.open(AdminSupport.loading(ui));
        ui.service().reopen(id).whenComplete((ignored, error) -> {
            if (error != null) {
                ctx.open(AdminSupport.failure(ui, error, c -> open(ui, c, id)));
                return;
            }
            show(ui, ctx, id);
        });
    }

    private static Screen duplicate(AdminUi ui, String sourceId, @Nullable Component error) {
        Screen.Builder b = Screen.builder(ui.style().title(t(ui, EDITOR_HUB_DUPLICATE_TITLE)));
        b.body(Component.text(t(ui, EDITOR_HUB_DUPLICATE_HINT), NamedTextColor.GRAY));
        if (error != null) {
            b.body(error);
        }
        return b.input(new Input.TextInput("id", Component.text(t(ui, EDITOR_COMMON_NEW_ID)), "",
                        SurveyIds.MAX_LENGTH, false))
                .button(Button.of(ui.style().primary(t(ui, EDITOR_COMMON_CREATE)), ctx -> {
                    String newId = ctx.text("id") == null ? "" : ctx.text("id").strip();
                    if (!SurveyIds.isValidSlug(newId)) {
                        ctx.open(duplicate(ui, sourceId, ui.style()
                                .error(t(ui, EDITOR_COMMON_ID_INVALID))));
                        return;
                    }
                    if (ui.store().exists(newId)) {
                        ctx.open(duplicate(ui, sourceId,
                                ui.style().error(t(ui, EDITOR_COMMON_ID_EXISTS, v("id", newId)))));
                        return;
                    }
                    ctx.open(AdminSupport.loading(ui));
                    ui.service().duplicate(sourceId, newId).whenComplete((copy, failure) -> {
                        if (failure != null) {
                            ctx.open(AdminSupport.failure(ui, failure, c -> open(ui, c, sourceId)));
                            return;
                        }
                        show(ui, ctx, newId);
                    });
                }))
                .button(Button.of(ui.style().secondary(ui.style().word(UI_BUTTON_CANCEL)), ctx -> open(ui, ctx, sourceId)))
                .columns(2)
                .build();
    }

    private static void deleteFlow(AdminUi ui, ScreenContext ctx, String id, int responses) {
        Consumer<ScreenContext> cancel = c -> open(ui, c, id);
        Consumer<ScreenContext> delete = c -> {
            c.open(AdminSupport.loading(ui));
            ui.service().delete(id).whenComplete((ignored, error) -> {
                if (error != null) {
                    c.open(AdminSupport.failure(ui, error, d -> open(ui, d, id)));
                    return;
                }
                ui.endSessions().accept(id);
                AdminListScreen.open(ui, c);
            });
        };
        Component first = Component.text(responses > 0
                ? t(ui, EDITOR_HUB_DELETE_BODY_RESPONSES, v("id", id), v("count", responses))
                : t(ui, EDITOR_HUB_DELETE_BODY, v("id", id)), NamedTextColor.GRAY);
        if (responses == 0) {
            ctx.open(ConfirmScreen.create(ui.style(), t(ui, EDITOR_HUB_DELETE_TITLE), first, delete, cancel));
            return;
        }
        Component second = Component.text(t(ui, EDITOR_HUB_REALLY_BODY, v("id", id), v("count", responses)),
                NamedTextColor.RED);
        ctx.open(ConfirmScreen.create(ui.style(), t(ui, EDITOR_HUB_DELETE_TITLE), first,
                c -> c.open(ConfirmScreen.create(ui.style(), t(ui, EDITOR_HUB_REALLY_TITLE), second, delete, cancel)),
                cancel));
    }

    // --- helpers ---------------------------------------------------------------------------

    private static SurveyDefinition copy(SurveyDefinition s, String name, String description, boolean anonymous,
                                         boolean featured, boolean requiresPermission) {
        return new SurveyDefinition(s.id(), name, description, s.status(), anonymous, featured,
                requiresPermission, s.rewards(), s.questions());
    }

    private static String yesNo(AdminUi ui, boolean value) {
        return t(ui, value ? EDITOR_COMMON_ANSWER_YES : EDITOR_COMMON_ANSWER_NO);
    }
}
