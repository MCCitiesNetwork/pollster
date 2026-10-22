package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Input;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Three steps: basics, settings, confirm. Each step passes a {@link Draft} on; nothing is written until Create. */
public final class CreateWizard {

    private static final int NAME_MAX = 100;
    private static final int DESCRIPTION_MAX = 500;

    public record Draft(@NotNull String id, @NotNull String name, @NotNull String description,
                        boolean anonymous, boolean featured, boolean requiresPermission) {
        public static final Draft EMPTY = new Draft("", "", "", false, false, false);
    }

    private CreateWizard() {
    }

    public static @NotNull Screen basics(@NotNull AdminUi ui, @NotNull Draft draft, @Nullable Component error) {
        Screen.Builder builder = Screen.builder(ui.style().title("Create survey: basics"));
        if (error != null) {
            builder.body(error);
        }
        return builder
                .input(new Input.TextInput("id", Component.text("Id (a-z, 0-9, -)"), draft.id(),
                        SurveyIds.MAX_LENGTH, false))
                .input(new Input.TextInput("name", Component.text("Name"), draft.name(), NAME_MAX, false))
                .input(new Input.TextInput("description", Component.text("Description"), draft.description(),
                        DESCRIPTION_MAX, true))
                .button(Button.of(ui.style().primary("Next"), ctx -> {
                    Draft entered = new Draft(trimmed(ctx.text("id")), trimmed(ctx.text("name")),
                            trimmed(ctx.text("description")), draft.anonymous(), draft.featured(),
                            draft.requiresPermission());
                    Component problem = problem(ui, entered);
                    ctx.open(problem == null ? settings(ui, entered) : basics(ui, entered, problem));
                }))
                .button(Button.of(ui.style().secondary("Cancel"), ctx -> AdminListScreen.open(ui, ctx)))
                .columns(2)
                .build();
    }

    public static @NotNull Screen settings(@NotNull AdminUi ui, @NotNull Draft draft) {
        return Screen.builder(ui.style().title("Create survey: settings"))
                .input(new Input.BoolInput("anonymous", Component.text("Anonymous (names are not stored)"),
                        draft.anonymous()))
                .input(new Input.BoolInput("featured", Component.text("Featured (suggested to players)"),
                        draft.featured()))
                .input(new Input.BoolInput("requires-permission", Component.text("Requires permission"),
                        draft.requiresPermission()))
                .button(Button.of(ui.style().primary("Next"), ctx -> ctx.open(confirm(ui, withSettings(draft, ctx)))))
                .button(Button.of(ui.style().secondary("Back"), ctx -> ctx.open(basics(ui, withSettings(draft, ctx), null))))
                .columns(2)
                .build();
    }

    public static @NotNull Screen confirm(@NotNull AdminUi ui, @NotNull Draft draft) {
        return Screen.builder(ui.style().title("Create survey: confirm"))
                .body(ui.style().label("Id", Component.text(draft.id())))
                .body(ui.style().label("Name", Component.text(draft.name())))
                .body(ui.style().label("Description", Component.text(AdminSupport.truncate(draft.description(), 80))))
                .body(ui.style().label("Anonymous", Component.text(yesNo(draft.anonymous()))))
                .body(ui.style().label("Featured", Component.text(yesNo(draft.featured()))))
                .body(ui.style().label("Requires permission", Component.text(yesNo(draft.requiresPermission()))))
                .button(Button.of(ui.style().primary("Create"), ctx -> create(ui, ctx, draft)))
                .button(Button.of(ui.style().secondary("Back"), ctx -> ctx.open(settings(ui, draft))))
                .button(Button.of(ui.style().secondary("Cancel"), ctx -> AdminListScreen.open(ui, ctx)))
                .columns(3)
                .build();
    }

    private static void create(AdminUi ui, ScreenContext ctx, Draft draft) {
        // Checked again here: the id may have been taken while the player sat on the confirm screen.
        Component problem = problem(ui, draft);
        if (problem != null) {
            ctx.open(basics(ui, draft, problem));
            return;
        }
        SurveyDefinition survey = new SurveyDefinition(draft.id(), draft.name(), draft.description(),
                SurveyStatus.DRAFT, draft.anonymous(), draft.featured(), draft.requiresPermission(),
                List.of(), List.of());
        ctx.open(AdminSupport.loading(ui));
        ui.service().save(survey).whenComplete((ignored, error) -> {
            if (error != null) {
                ctx.open(AdminSupport.failure(ui, error, c -> c.open(confirm(ui, draft))));
                return;
            }
            HubScreen.open(ui, ctx, draft.id());
        });
    }

    private static @Nullable Component problem(AdminUi ui, Draft draft) {
        if (!SurveyIds.isValidSlug(draft.id())) {
            return ui.style().error("Survey id must be 3 to 48 characters of a-z, 0-9 and '-'.");
        }
        if (ui.store().exists(draft.id())) {
            return ui.style().error("A survey with the id " + draft.id() + " already exists.");
        }
        if (draft.name().isEmpty()) {
            return ui.style().error("Give the survey a name.");
        }
        return null;
    }

    private static Draft withSettings(Draft draft, ScreenContext ctx) {
        return new Draft(draft.id(), draft.name(), draft.description(),
                Boolean.TRUE.equals(ctx.bool("anonymous")), Boolean.TRUE.equals(ctx.bool("featured")),
                Boolean.TRUE.equals(ctx.bool("requires-permission")));
    }

    private static String trimmed(@Nullable String s) {
        return s == null ? "" : s.strip();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
