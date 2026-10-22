package com.minecraftcitiesnetwork.pollster.ui.player;

import com.minecraftcitiesnetwork.pollster.ui.StaffText;
import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.session.PlayerSession;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.Screens;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pieces shared by the player screens: titles, the stale-button guard and screen sequencing. */
final class PlayerScreens {

    private PlayerScreens() {
    }

    /** The survey name as title; previews carry a visible marker. Names are staff text (restricted markup). */
    static @NotNull Component title(@NotNull PlayerUi ui, @NotNull SurveyDefinition survey, boolean preview) {
        Component title = ui.style().title(StaffText.render(survey.name()));
        return preview ? title.append(ui.messages().messageFor(MessageKeys.SURVEY_PREVIEW_MARKER)) : title;
    }

    /** A configurable word from messages.yml as plain text, for the dialog styles to colour. */
    static @NotNull String word(@NotNull PlayerUi ui, @NotNull String key) {
        return ui.messages().plaintextMessageFor(key);
    }

    static @NotNull Screen unavailable(@NotNull PlayerUi ui) {
        return Screens.error(ui.style(), ui.messages().messageFor(MessageKeys.SURVEY_UNAVAILABLE), List.of(),
                ScreenContext::close);
    }

    /** A previewed survey only has to still exist; a real one must also be OPEN with an unchanged file. */
    static boolean available(@NotNull PlayerUi ui, @NotNull String surveyId, boolean preview) {
        Optional<SurveyDefinition> survey = ui.service().survey(surveyId);
        if (survey.isEmpty()) {
            return false;
        }
        return preview || (survey.get().status() == SurveyStatus.OPEN
                && ui.service().lock(surveyId) == SurveyService.SurveyLock.OK);
    }

    /**
     * Runs {@code action} only when the session is still the player's current one and its survey
     * is still takeable. Otherwise shows "no longer available" and changes nothing.
     */
    static void guarded(@NotNull PlayerUi ui, @NotNull PlayerSession session, @NotNull ScreenContext ctx,
                        @NotNull Runnable action) {
        UUID id = ctx.player().getUniqueId();
        if (!ui.sessions().isCurrent(id, session.token())
                || !available(ui, session.flow().survey().id(), session.preview())) {
            ui.sounds().error(ctx.player());
            ctx.open(unavailable(ui));
            return;
        }
        ui.sessions().touch(id, ui.sessions().now());
        action.run();
    }

    /**
     * Like {@link #guarded}, but the button also belongs to one step of the flow. A second click
     * on a screen the first click already moved past (a double click races the new dialog) just
     * shows where the player is now instead of answering the next question too.
     */
    static void guardedStep(@NotNull PlayerUi ui, @NotNull PlayerSession session, @NotNull ScreenContext ctx,
                            int step, @NotNull Runnable action) {
        guarded(ui, session, ctx, () -> {
            if (session.flow().step() != step) {
                ctx.open(current(ui, session));
                return;
            }
            action.run();
        });
    }

    /** The question the flow is on, or the review once every shown question is answered. */
    static @NotNull Screen current(@NotNull PlayerUi ui, @NotNull PlayerSession session) {
        return session.flow().current().isPresent()
                ? QuestionScreen.create(ui, session, null)
                : ReviewScreen.create(ui, session);
    }
}
