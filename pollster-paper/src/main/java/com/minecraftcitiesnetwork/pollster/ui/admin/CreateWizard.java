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

import static com.minecraftcitiesnetwork.pollster.config.MessageKeys.*;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminSupport.t;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminSupport.v;

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
        Screen.Builder builder = Screen.builder(ui.style().title(t(ui, EDITOR_WIZARD_BASICS_TITLE)));
        if (error != null) {
            builder.body(error);
        }
        return builder
                .input(new Input.TextInput("id", Component.text(t(ui, EDITOR_WIZARD_ID_INPUT)), draft.id(),
                        SurveyIds.MAX_LENGTH, false))
                .input(new Input.TextInput("name", Component.text(t(ui, EDITOR_LABEL_NAME)), draft.name(), NAME_MAX, false))
                .input(new Input.TextInput("description", Component.text(t(ui, EDITOR_LABEL_DESCRIPTION)), draft.description(),
                        DESCRIPTION_MAX, true))
                .button(Button.of(ui.style().primary(t(ui, EDITOR_COMMON_NEXT)), ctx -> {
                    Draft entered = new Draft(trimmed(ctx.text("id")), trimmed(ctx.text("name")),
                            trimmed(ctx.text("description")), draft.anonymous(), draft.featured(),
                            draft.requiresPermission());
                    Component problem = problem(ui, entered);
                    ctx.open(problem == null ? settings(ui, entered) : basics(ui, entered, problem));
                }))
                .button(Button.of(ui.style().secondary(ui.style().word(UI_BUTTON_CANCEL)), ctx -> AdminListScreen.open(ui, ctx)))
                .columns(2)
                .build();
    }

    public static @NotNull Screen settings(@NotNull AdminUi ui, @NotNull Draft draft) {
        return Screen.builder(ui.style().title(t(ui, EDITOR_WIZARD_SETTINGS_TITLE)))
                .input(new Input.BoolInput("anonymous", Component.text(t(ui, EDITOR_WIZARD_ANONYMOUS_INPUT)),
                        draft.anonymous()))
                .input(new Input.BoolInput("featured", Component.text(t(ui, EDITOR_WIZARD_FEATURED_INPUT)),
                        draft.featured()))
                .input(new Input.BoolInput("requires-permission", Component.text(t(ui, EDITOR_LABEL_REQUIRES_PERMISSION)),
                        draft.requiresPermission()))
                .button(Button.of(ui.style().primary(t(ui, EDITOR_COMMON_NEXT)), ctx -> ctx.open(confirm(ui, withSettings(draft, ctx)))))
                .button(Button.of(ui.style().secondary(t(ui, EDITOR_COMMON_BACK)), ctx -> ctx.open(basics(ui, withSettings(draft, ctx), null))))
                .columns(2)
                .build();
    }

    public static @NotNull Screen confirm(@NotNull AdminUi ui, @NotNull Draft draft) {
        return Screen.builder(ui.style().title(t(ui, EDITOR_WIZARD_CONFIRM_TITLE)))
                .body(ui.style().label(t(ui, EDITOR_LABEL_ID), Component.text(draft.id())))
                .body(ui.style().label(t(ui, EDITOR_LABEL_NAME), Component.text(draft.name())))
                .body(ui.style().label(t(ui, EDITOR_LABEL_DESCRIPTION), Component.text(AdminSupport.truncate(draft.description(), 80))))
                .body(ui.style().label(t(ui, EDITOR_LABEL_ANONYMOUS), Component.text(yesNo(ui, draft.anonymous()))))
                .body(ui.style().label(t(ui, EDITOR_LABEL_FEATURED), Component.text(yesNo(ui, draft.featured()))))
                .body(ui.style().label(t(ui, EDITOR_LABEL_REQUIRES_PERMISSION), Component.text(yesNo(ui, draft.requiresPermission()))))
                .button(Button.of(ui.style().primary(t(ui, EDITOR_COMMON_CREATE)), ctx -> create(ui, ctx, draft)))
                .button(Button.of(ui.style().secondary(t(ui, EDITOR_COMMON_BACK)), ctx -> ctx.open(settings(ui, draft))))
                .button(Button.of(ui.style().secondary(ui.style().word(UI_BUTTON_CANCEL)), ctx -> AdminListScreen.open(ui, ctx)))
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
            return ui.style().error(t(ui, EDITOR_COMMON_ID_INVALID));
        }
        if (ui.store().exists(draft.id())) {
            return ui.style().error(t(ui, EDITOR_COMMON_ID_EXISTS, v("id", draft.id())));
        }
        if (draft.name().isEmpty()) {
            return ui.style().error(t(ui, EDITOR_WIZARD_NAME_REQUIRED));
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

    private static String yesNo(AdminUi ui, boolean value) {
        return t(ui, value ? EDITOR_COMMON_ANSWER_YES : EDITOR_COMMON_ANSWER_NO);
    }
}
