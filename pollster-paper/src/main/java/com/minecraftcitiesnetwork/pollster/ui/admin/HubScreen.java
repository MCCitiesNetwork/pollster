package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.SurveyService.SurveyLock;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Input;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.common.ConfirmScreen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

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
        Screen.Builder b = Screen.builder(ui.style().title("Survey: " + AdminSupport.truncate(s.name(), 40)));
        b.body(ui.style().label("Id", Component.text(id)));
        b.body(ui.style().label("Status", Component.text(s.status().name())));
        b.body(ui.style().label("Responses", Component.text(responses)));
        b.body(ui.style().label("Questions", Component.text(s.questions().size())));
        b.body(ui.style().label("Anonymous", Component.text(yesNo(s.anonymous()))));
        b.body(ui.style().label("Featured", Component.text(yesNo(s.featured()))));
        b.body(ui.style().label("Requires permission", Component.text(yesNo(s.requiresPermission()))));
        b.body(ui.style().label("Rewards", Component.text(s.rewards().size())));
        if (!draft && lock != SurveyLock.OK) {
            b.body(Component.text("[LOCKED] The questions in the file no longer match what was opened, "
                    + "so players cannot take this survey. Restore the file or duplicate the survey.",
                    NamedTextColor.RED));
        }

        b.button(Button.of(AdminSupport.valued(ui, "Name", s.name()), ctx -> editName(ui, ctx, id)));
        b.button(Button.of(AdminSupport.valued(ui, "Description", s.description()),
                ctx -> editDescription(ui, ctx, id)));
        b.button(Button.of(AdminSupport.valued(ui, "Questions", "(" + s.questions().size() + ")"),
                ctx -> current(ui, ctx, id, fresh -> ctx.open(QuestionListScreen.create(ui, fresh)))));
        b.button(Button.of(AdminSupport.valued(ui, "Rewards", "(" + s.rewards().size() + ")"),
                ctx -> current(ui, ctx, id, fresh -> ctx.open(RewardsScreen.create(ui, fresh)))));
        b.button(Button.of(AdminSupport.valued(ui, "Anonymous", yesNo(s.anonymous())), ctx -> toggle(ui, ctx, id,
                true, fresh -> copy(fresh, fresh.name(), fresh.description(), !fresh.anonymous(),
                        fresh.featured(), fresh.requiresPermission()))));
        b.button(Button.of(AdminSupport.valued(ui, "Featured", yesNo(s.featured())), ctx -> toggle(ui, ctx, id,
                false, fresh -> copy(fresh, fresh.name(), fresh.description(), fresh.anonymous(),
                        !fresh.featured(), fresh.requiresPermission()))));
        b.button(Button.of(AdminSupport.valued(ui, "Requires permission", yesNo(s.requiresPermission())),
                ctx -> toggle(ui, ctx, id, false, fresh -> copy(fresh, fresh.name(), fresh.description(),
                        fresh.anonymous(), fresh.featured(), !fresh.requiresPermission()))));
        b.button(Button.of(ui.style().edit("Preview"), ctx -> preview(ui, ctx, id)));
        b.button(Button.of(ui.style().edit("Report"), ctx -> ui.report().open(ctx.player(), id)));
        b.button(switch (s.status()) {
            case DRAFT -> Button.of(ui.style().primary("Open survey"), ctx -> openSurvey(ui, ctx, id));
            case OPEN -> Button.of(ui.style().danger("Close survey"), ctx -> closeSurvey(ui, ctx, id));
            case CLOSED -> Button.of(ui.style().primary("Reopen"), ctx -> reopenSurvey(ui, ctx, id));
        });
        b.button(Button.of(ui.style().edit("Duplicate"), ctx -> ctx.open(duplicate(ui, id, null))));
        b.button(Button.of(ui.style().danger("Delete"), ctx -> deleteFlow(ui, ctx, id, responses)));
        b.button(AdminListScreen.back(ui));
        return b.columns(2).build();
    }

    // --- actions ---------------------------------------------------------------------------

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
                ctx.open(AdminSupport.notice(ui, "Survey", ui.messages().messageFor(key), c -> open(ui, c, id)));
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
                ctx.open(AdminSupport.notice(ui, "Survey", ui.messages().messageFor(MessageKeys.EDITOR_READ_ONLY),
                        c -> open(ui, c, id)));
                return;
            }
            ctx.open(textEdit(ui, id, "Name", fresh.name(), false, NAME_MAX, null, true,
                    value -> copy(fresh, value, fresh.description(), fresh.anonymous(), fresh.featured(),
                            fresh.requiresPermission())));
        });
    }

    private static void editDescription(AdminUi ui, ScreenContext ctx, String id) {
        current(ui, ctx, id, fresh -> {
            if (fresh.status() == SurveyStatus.CLOSED) {
                ctx.open(AdminSupport.notice(ui, "Survey", ui.messages().messageFor(MessageKeys.EDITOR_READ_ONLY),
                        c -> open(ui, c, id)));
                return;
            }
            ctx.open(textEdit(ui, id, "Description", fresh.description(), true, DESCRIPTION_MAX, null, false,
                    value -> copy(fresh, fresh.name(), value, fresh.anonymous(), fresh.featured(),
                            fresh.requiresPermission())));
        });
    }

    /** A one-input screen. A refused save reopens it with the problem shown above the input. */
    private static Screen textEdit(AdminUi ui, String id, String field, String initial, boolean multiline, int max,
                                   @Nullable Component error, boolean required,
                                   Function<String, SurveyDefinition> apply) {
        Screen.Builder b = Screen.builder(ui.style().title("Edit " + field.toLowerCase()));
        if (error != null) {
            b.body(error);
        }
        return b.input(new Input.TextInput("value", Component.text(field), initial, max, multiline))
                .button(Button.of(ui.style().primary("Save"), ctx -> {
                    String value = ctx.text("value") == null ? "" : ctx.text("value").strip();
                    if (required && value.isEmpty()) {
                        ctx.open(textEdit(ui, id, field, initial, multiline, max,
                                ui.style().error(field + " cannot be empty."), required, apply));
                        return;
                    }
                    save(ui, ctx, id, apply.apply(value), problem -> ctx.open(textEdit(ui, id, field, value,
                            multiline, max, ui.style().error(reason(problem)), required, apply)));
                }))
                .button(Button.of(ui.style().secondary("Cancel"), ctx -> open(ui, ctx, id)))
                .columns(2)
                .build();
    }

    private static void preview(AdminUi ui, ScreenContext ctx, String id) {
        current(ui, ctx, id, fresh -> {
            var problems = AdminSupport.problems(fresh);
            if (!problems.isEmpty()) {
                ctx.open(AdminSupport.problemsScreen(ui, problems, c -> open(ui, c, id)));
                return;
            }
            ui.preview().preview(ctx.player(), fresh);
        });
    }

    private static void openSurvey(AdminUi ui, ScreenContext ctx, String id) {
        current(ui, ctx, id, fresh -> {
            var problems = AdminSupport.problems(fresh);
            if (!problems.isEmpty()) {
                ctx.open(AdminSupport.problemsScreen(ui, problems, c -> open(ui, c, id)));
                return;
            }
            ctx.open(ConfirmScreen.create(ui.style(), "Open survey?",
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
        Screen.Builder b = Screen.builder(ui.style().title("Duplicate survey"));
        b.body(Component.text("The copy is a draft with the same questions and rewards.", NamedTextColor.GRAY));
        if (error != null) {
            b.body(error);
        }
        return b.input(new Input.TextInput("id", Component.text("New id (a-z, 0-9, -)"), "",
                        SurveyIds.MAX_LENGTH, false))
                .button(Button.of(ui.style().primary("Create"), ctx -> {
                    String newId = ctx.text("id") == null ? "" : ctx.text("id").strip();
                    if (!SurveyIds.isValidSlug(newId)) {
                        ctx.open(duplicate(ui, sourceId, ui.style()
                                .error("Survey id must be 3 to 48 characters of a-z, 0-9 and '-'.")));
                        return;
                    }
                    if (ui.store().exists(newId)) {
                        ctx.open(duplicate(ui, sourceId,
                                ui.style().error("A survey with the id " + newId + " already exists.")));
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
                .button(Button.of(ui.style().secondary("Cancel"), ctx -> open(ui, ctx, sourceId)))
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
        Component first = Component.text("Delete " + id + "? This removes the survey file"
                + (responses > 0 ? " and its " + responses + " responses." : "."), NamedTextColor.GRAY);
        if (responses == 0) {
            ctx.open(ConfirmScreen.create(ui.style(), "Delete survey?", first, delete, cancel));
            return;
        }
        Component second = Component.text("Really delete " + id + "? Its " + responses
                + " responses will be deleted from the database and cannot be recovered.", NamedTextColor.RED);
        ctx.open(ConfirmScreen.create(ui.style(), "Delete survey?", first,
                c -> c.open(ConfirmScreen.create(ui.style(), "Really delete?", second, delete, cancel)), cancel));
    }

    // --- helpers ---------------------------------------------------------------------------

    static String reason(Throwable error) {
        Throwable t = AdminSupport.unwrap(error);
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static SurveyDefinition copy(SurveyDefinition s, String name, String description, boolean anonymous,
                                         boolean featured, boolean requiresPermission) {
        return new SurveyDefinition(s.id(), name, description, s.status(), anonymous, featured,
                requiresPermission, s.rewards(), s.questions());
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
